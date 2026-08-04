package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryAlertLog;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.dto.query.AlertQueryDTO;
import com.jjx.inventory.dto.vo.AlertVO;
import com.jjx.inventory.mapper.InventoryAlertLogMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.service.InventoryAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.jjx.system.annotation.Event;

/**
 * 库存预警服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAlertServiceImpl extends ServiceImpl<InventoryAlertLogMapper, InventoryAlertLog>
        implements InventoryAlertService {

    private final InventoryAlertLogMapper alertLogMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final EventPublisher eventPublisher;

    @Override
    public IPage<AlertVO> page(AlertQueryDTO query) {
        LambdaQueryWrapper<InventoryAlertLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getAlertType() != null && !query.getAlertType().isEmpty()) wrapper.eq(InventoryAlertLog::getAlertType, query.getAlertType());
        if (query.getAlertLevel() != null && !query.getAlertLevel().isEmpty()) wrapper.eq(InventoryAlertLog::getAlertLevel, query.getAlertLevel());
        if (query.getStatus() != null && !query.getStatus().isEmpty()) wrapper.eq(InventoryAlertLog::getStatus, query.getStatus());
        if (query.getAlertTimeStart() != null) wrapper.ge(InventoryAlertLog::getAlertTime, query.getAlertTimeStart());
        if (query.getAlertTimeEnd() != null) wrapper.le(InventoryAlertLog::getAlertTime, query.getAlertTimeEnd());
        if (query.getMaterialId() != null) wrapper.eq(InventoryAlertLog::getMaterialId, query.getMaterialId());
        wrapper.orderByDesc(InventoryAlertLog::getAlertTime);

        Page<InventoryAlertLog> logPage = new Page<>(query.getCurrent(), query.getSize());
        IPage<InventoryAlertLog> logResult = alertLogMapper.selectPage(logPage, wrapper);
        Page<AlertVO> voPage = new Page<>(query.getCurrent(), query.getSize());
        voPage.setTotal(logResult.getTotal());
        voPage.setPages(logResult.getPages());
        voPage.setRecords(convertToVOList(logResult.getRecords()));
        return voPage;
    }

    @Override
    public void executeAlertCheck() {
        log.info("开始执行库存预警检查");
        checkSafeStockAlert();
        checkMaxStockAlert();
        checkExpiryAlert();
        checkObsoleteAlert();
        log.info("库存预警检查完成");
    }

    @Override
    public void checkSafeStockAlert() {
        log.info("检查安全库存预警");
        List<InventoryStock> lowStock = stockMapper.selectLowStock();
        for (InventoryStock stock : lowStock) {
            String msg = "物料[" + stock.getMaterialCode() + "] " + stock.getMaterialName()
                    + " 当前库存: " + stock.getTotalQuantity() + ", 低于安全库存";
            log.warn(msg);

            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("safe_stock");
            alert.setAlertLevel("warning");
            alert.setMaterialId(stock.getMaterialId());
            alert.setMaterialCode(stock.getMaterialCode());
            alert.setMaterialName(stock.getMaterialName());
            alert.setCurrentStock(stock.getTotalQuantity());
            alert.setSafeStock(stock.getSafeStock() != null ? stock.getSafeStock() : stock.getTotalQuantity()); // 真实安全库存从物料表取
            alert.setAlertMessage(msg);
            alert.setAlertTime(java.time.LocalDateTime.now());
            alertLogMapper.insert(alert);
        }
        try { if (!lowStock.isEmpty()) eventPublisher.fire("stock.low", Map.of("count", String.valueOf(lowStock.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("安全库存预警检查完成，发现 {} 条", lowStock.size());
    }


    @Override
    public void checkSafeStockAlert(Long materialId) {
        log.info("检查单物料安全库存预警: materialId={}", materialId);
        InventoryStock stock = stockMapper.selectByMaterialId(materialId);
        if (stock == null) return;

        java.math.BigDecimal safe = java.math.BigDecimal.ZERO;
        try {
            String sql = "SELECT safe_stock FROM inventory_material WHERE material_id = " + materialId;
            java.util.List<java.util.Map<String,Object>> rows = java.util.Collections.emptyList();
            // 使用 MyBatis-Plus 的 selectMaps 搭配 QueryWrapper 需要指定类型
            // 通过 stockMapper 的现有方法查询
            if (stockMapper.selectByMaterialId(materialId) != null) {
                var qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<InventoryStock>();
                qw.select("safe_stock").eq("material_id", materialId);
                rows = stockMapper.selectMaps(qw);
            }
            if (!rows.isEmpty() && rows.get(0).get("safe_stock") != null)
                safe = new java.math.BigDecimal(rows.get(0).get("safe_stock").toString());
        } catch (Exception e) {
            log.warn("查询安全库存失败: {}", e.getMessage());
        }

        if (safe.compareTo(java.math.BigDecimal.ZERO) <= 0) return;
        if (stock.getTotalQuantity() != null && stock.getTotalQuantity().compareTo(safe) >= 0) return;

        String msg = "物料[" + stock.getMaterialCode() + "] " + stock.getMaterialName()
                + " 当前库存: " + stock.getTotalQuantity() + ", 安全库存: " + safe + ", 低于安全库存";
        log.warn(msg);

        InventoryAlertLog alert = new InventoryAlertLog();
        alert.setAlertType("safe_stock");
        alert.setAlertLevel("warning");
        alert.setMaterialId(stock.getMaterialId());
        alert.setMaterialCode(stock.getMaterialCode());
        alert.setMaterialName(stock.getMaterialName());
        alert.setCurrentStock(stock.getTotalQuantity());
        alert.setSafeStock(safe);
        alert.setAlertMessage(msg);
        alert.setAlertTime(java.time.LocalDateTime.now());
        alertLogMapper.insert(alert);

        try { eventPublisher.fire("stock.low", java.util.Map.of("materialId", String.valueOf(materialId), "currentStock", String.valueOf(stock.getTotalQuantity()), "safeStock", String.valueOf(safe))); }
        catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("单物料安全库存预警检查完成: materialId={}", materialId);
    }
    @Override
    public void checkMaxStockAlert() {
        log.info("检查最高库存预警");
        // 检查库存超过最高库存的物料
        LambdaQueryWrapper<InventoryStockItem> wrapper = new LambdaQueryWrapper<InventoryStockItem>()
                .gt(InventoryStockItem::getQuantity, 10000); // 简单阈值检查
        List<InventoryStockItem> overStock = stockItemMapper.selectList(wrapper);
        for (InventoryStockItem item : overStock) {
            String msg = "物料[" + item.getMaterialCode() + "] 库存: " + item.getQuantity() + ", 可能过高";
            log.warn(msg);

            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("max_stock");
            alert.setAlertLevel("info");
            alert.setMaterialId(item.getMaterialId());
            alert.setMaterialCode(item.getMaterialCode());
            alert.setMaterialName(item.getMaterialName());
            alert.setCurrentStock(item.getQuantity());
            alert.setAlertMessage(msg);
            alert.setAlertTime(java.time.LocalDateTime.now());
            alertLogMapper.insert(alert);
        }
        try { if (!overStock.isEmpty()) eventPublisher.fire("stock.max", Map.of("count", String.valueOf(overStock.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("最高库存预警检查完成，发现 {} 条", overStock.size());
    }

    @Override
    public void checkExpiryAlert() {
        log.info("检查保质期预警");
        List<InventoryStock> expiring = stockMapper.selectExpiring();
        for (InventoryStock stock : expiring) {
            String msg = "物料[" + stock.getMaterialCode() + "] " + stock.getMaterialName()
                    + " 最早有效期: " + stock.getEarliestExpiry() + ", 即将过期";
            log.warn(msg);

            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("expiry");
            alert.setAlertLevel("warning");
            alert.setMaterialId(stock.getMaterialId());
            alert.setMaterialCode(stock.getMaterialCode());
            alert.setMaterialName(stock.getMaterialName());
            alert.setCurrentStock(stock.getTotalQuantity());
            alert.setExpiryDate(stock.getEarliestExpiry());
            alert.setAlertMessage(msg);
            alert.setAlertTime(java.time.LocalDateTime.now());
            alertLogMapper.insert(alert);
        }
        try { if (!expiring.isEmpty()) eventPublisher.fire("stock.expiry", Map.of("count", String.valueOf(expiring.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("保质期预警检查完成，发现 {} 条", expiring.size());
    }

    @Override
    public void checkObsoleteAlert() {
        log.info("检查呆滞料预警");
        List<InventoryStock> obsolete = stockMapper.selectObsolete();
        for (InventoryStock stock : obsolete) {
            String msg = "物料[" + stock.getMaterialCode() + "] " + stock.getMaterialName()
                    + " 库存: " + stock.getTotalQuantity() + ", 超过180天未出库";
            log.warn(msg);

            InventoryAlertLog alert = new InventoryAlertLog();
            alert.setAlertType("obsolete");
            alert.setAlertLevel("warning");
            alert.setMaterialId(stock.getMaterialId());
            alert.setMaterialCode(stock.getMaterialCode());
            alert.setMaterialName(stock.getMaterialName());
            alert.setCurrentStock(stock.getTotalQuantity());
            alert.setAlertMessage(msg);
            alert.setAlertTime(java.time.LocalDateTime.now());
            alertLogMapper.insert(alert);
        }
        try { if (!obsolete.isEmpty()) eventPublisher.fire("stock.obsolete", Map.of("count", String.valueOf(obsolete.size()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("呆滞料预警检查完成，发现 {} 条", obsolete.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(Long alertId) {
        InventoryAlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null) {
            log.error("预警不存在: alertId={}", alertId);
            return false;
        }

        alert.setStatus(1);
        return alertLogMapper.updateById(alert) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchMarkRead(List<Long> alertIds) {
        if (alertIds == null || alertIds.isEmpty()) {
            return false;
        }

        List<InventoryAlertLog> alerts = alertLogMapper.selectBatchIds(alertIds);
        for (InventoryAlertLog alert : alerts) {
            alert.setStatus(1);
        }

        return updateBatchById(alerts);
    }

    @Override
    @Event(value = "inventory.alert.processed", bizId = "#alertId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean processAlert(Long alertId, String processedBy, String remark) {
        InventoryAlertLog alert = alertLogMapper.selectById(alertId);
        if (alert == null) {
            log.error("预警不存在: alertId={}", alertId);
            return false;
        }

        alert.setStatus(2);
        alert.setProcessedBy(processedBy);
        alert.setProcessedTime(LocalDateTime.now());
        alert.setProcessRemark(remark);
        return alertLogMapper.updateById(alert) > 0;
    }

    @Override
    public List<Map<String, Object>> generatePurchaseSuggestions() {
        log.info("生成采购建议");
        List<InventoryStock> lowStock = stockMapper.selectLowStock();
        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (InventoryStock stock : lowStock) {
            // 建议采购量 = 安全库存 * 2 - 当前库存（简单算法）
            BigDecimal suggestQty = BigDecimal.valueOf(100).subtract(stock.getTotalQuantity() != null
                    ? stock.getTotalQuantity() : BigDecimal.ZERO);
            if (suggestQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            suggestions.add(Map.of(
                    "materialCode", stock.getMaterialCode(),
                    "materialName", stock.getMaterialName(),
                    "currentStock", stock.getTotalQuantity() != null ? stock.getTotalQuantity().doubleValue() : 0,
                    "suggestQuantity", suggestQty.doubleValue(),
                    "reason", "低于安全库存，建议补货",
                    "priority", "normal"
            ));
        }

        log.info("生成采购建议完成，共 {} 条", suggestions.size());
        return suggestions;
    }

    @Override
    public List<AlertVO> getUnprocessed() {
        List<InventoryAlertLog> alerts = alertLogMapper.selectList(
                new LambdaQueryWrapper<InventoryAlertLog>()
                        .eq(InventoryAlertLog::getStatus, 0)
                        .orderByDesc(InventoryAlertLog::getAlertTime)
        );
        return convertToVOList(alerts);
    }

    @Override
    public boolean existsUnprocessed(String alertType, Long materialId) {
        Long count = alertLogMapper.selectCount(
                new LambdaQueryWrapper<InventoryAlertLog>()
                        .eq(InventoryAlertLog::getAlertType, alertType)
                        .eq(InventoryAlertLog::getMaterialId, materialId)
                        .eq(InventoryAlertLog::getStatus, 0)
        );
        return count != null && count > 0;
    }

    @Override
    public IPage<InventoryAlertLog> pageQuery(Map<String, Object> params) {
        String alertType = (String) params.get("alertType");
        String alertLevel = (String) params.get("alertLevel");
        String status = (String) params.get("status");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryAlertLog> wrapper = new LambdaQueryWrapper<>();
        if (alertType != null && !alertType.isEmpty()) {
            wrapper.eq(InventoryAlertLog::getAlertType, alertType);
        }
        if (alertLevel != null && !alertLevel.isEmpty()) {
            wrapper.eq(InventoryAlertLog::getAlertLevel, alertLevel);
        }
        if (status != null) {
            wrapper.eq(InventoryAlertLog::getStatus, Integer.valueOf(status));
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryAlertLog::getAlertTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryAlertLog::getAlertTime, endDate);
        }
        wrapper.orderByDesc(InventoryAlertLog::getAlertTime);

        Page<InventoryAlertLog> page = new Page<>(pageNum, pageSize);
        return alertLogMapper.selectPage(page, wrapper);
    }

    private List<AlertVO> convertToVOList(List<InventoryAlertLog> alerts) {
        List<AlertVO> result = new ArrayList<>();
        for (InventoryAlertLog alert : alerts) {
            result.add(convertToVO(alert));
        }
        return result;
    }

    private AlertVO convertToVO(InventoryAlertLog alert) {
        if (alert == null) {
            return null;
        }

        AlertVO vo = new AlertVO();
        vo.setAlertId(alert.getAlertId());
        vo.setAlertType(alert.getAlertType());
        vo.setAlertLevel(alert.getAlertLevel());
        vo.setMaterialId(alert.getMaterialId());
        vo.setMaterialCode(alert.getMaterialCode());
        vo.setMaterialName(alert.getMaterialName());
        vo.setCurrentStock(alert.getCurrentStock());
        vo.setSafeStock(alert.getSafeStock());
        vo.setMaxStock(alert.getMaxStock());
        vo.setExpiryDate(alert.getExpiryDate());
        vo.setLastOutboundDate(alert.getLastOutboundDate());
        vo.setAlertMessage(alert.getAlertMessage());
        vo.setAlertTime(alert.getAlertTime());
        vo.setStatus(alert.getStatus());
        vo.setProcessedBy(alert.getProcessedBy());
        vo.setProcessedTime(alert.getProcessedTime());
        vo.setProcessRemark(alert.getProcessRemark());
        vo.setSuggestion(alert.getSuggestion());
        vo.setCreateTime(alert.getCreateTime());
        vo.setUpdateTime(alert.getUpdateTime());
        vo.setCreateBy(alert.getCreateBy());
        vo.setUpdateBy(alert.getUpdateBy());
        // 设置类型名称

        return vo;
    }

}
