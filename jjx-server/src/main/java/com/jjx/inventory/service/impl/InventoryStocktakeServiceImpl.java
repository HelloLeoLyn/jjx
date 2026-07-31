package com.jjx.inventory.service.impl;

import com.jjx.inventory.enums.OrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.dto.query.StocktakeQueryDTO;
import com.jjx.inventory.dto.vo.StocktakeItemVO;
import com.jjx.inventory.dto.vo.StocktakeVO;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryStocktakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 盘点服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryStocktakeServiceImpl extends ServiceImpl<InventoryStocktakeOrderMapper, InventoryStocktakeOrder>
        implements InventoryStocktakeService {

    private final InventoryStocktakeOrderMapper stocktakeOrderMapper;
    private final InventoryStocktakeItemMapper stocktakeItemMapper;
    private final InventoryMaterialMapper materialMapper;
    private final InventoryStockMapper stockMapper;
    private final RedisSequenceService redisSequenceService;
    private final InventoryInboundOrderMapper inboundOrderMapper;
    private final InventoryInboundItemMapper inboundItemMapper;
    private final InventoryOutboundOrderMapper outboundOrderMapper;
    private final InventoryOutboundItemMapper outboundItemMapper;

    @Override
    public IPage<StocktakeVO> page(StocktakeQueryDTO query) {
        LambdaQueryWrapper<InventoryStocktakeOrder> wrapper = new LambdaQueryWrapper<>();

        if (query.getStocktakeId() != null) {
            wrapper.eq(InventoryStocktakeOrder::getStocktakeId, query.getStocktakeId());
        }
        if (query.getStocktakeNo() != null && !query.getStocktakeNo().isEmpty()) {
            wrapper.like(InventoryStocktakeOrder::getStocktakeNo, query.getStocktakeNo());
        }
        if (query.getStocktakeType() != null && !query.getStocktakeType().isEmpty()) {
            wrapper.eq(InventoryStocktakeOrder::getStocktakeType, query.getStocktakeType());
        }
        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryStocktakeOrder::getWarehouseId, query.getWarehouseId());
        }
        if (query.getOrderStatus() != null && !query.getOrderStatus().isEmpty()) {
            wrapper.eq(InventoryStocktakeOrder::getOrderStatus, query.getOrderStatus());
        }
        if (query.getApproveStatus() != null && !query.getApproveStatus().isEmpty()) {
            wrapper.eq(InventoryStocktakeOrder::getApproveStatus, query.getApproveStatus());
        }
        if (query.getCreateTimeStart() != null && !query.getCreateTimeStart().isEmpty()) {
            wrapper.ge(InventoryStocktakeOrder::getCreateTime, query.getCreateTimeStart());
        }
        if (query.getCreateTimeEnd() != null && !query.getCreateTimeEnd().isEmpty()) {
            wrapper.le(InventoryStocktakeOrder::getCreateTime, query.getCreateTimeEnd());
        }

        // 排序
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            boolean isAsc = "asc".equalsIgnoreCase(query.getOrderDirection());
            switch (query.getOrderBy()) {
                case "stocktakeNo":
                    wrapper.orderBy(true, isAsc, InventoryStocktakeOrder::getStocktakeNo);
                    break;
                case "createTime":
                    wrapper.orderBy(true, isAsc, InventoryStocktakeOrder::getCreateTime);
                    break;
                case "totalDiffQuantity":
                    wrapper.orderBy(true, isAsc, InventoryStocktakeOrder::getTotalDiffQuantity);
                    break;
                default:
                    wrapper.orderByDesc(InventoryStocktakeOrder::getCreateTime);
            }
        } else {
            wrapper.orderByDesc(InventoryStocktakeOrder::getCreateTime);
        }

        Page<InventoryStocktakeOrder> orderPage = new Page<>(query.getCurrent(), query.getSize());
        IPage<InventoryStocktakeOrder> orderResult = stocktakeOrderMapper.selectPage(orderPage, wrapper);

        Page<StocktakeVO> voPage = new Page<>(query.getCurrent(), query.getSize());
        voPage.setTotal(orderResult.getTotal());
        voPage.setPages(orderResult.getPages());
        voPage.setRecords(convertToVOList(orderResult.getRecords()));

        return voPage;
    }

    @Override
    public StocktakeVO getDetail(Long stocktakeId) {
        // 查询盘点单主记录
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return null;
        }

        // 转换为VO
        StocktakeVO vo = convertToVO(order);

        // 查询盘点明细
        List<InventoryStocktakeItem> items = stocktakeItemMapper.selectByStocktakeId(stocktakeId);
        if (items != null && !items.isEmpty()) {
            vo.setItems(convertToItemVOList(items));
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> params) {
        log.info("创建盘点单: {}", params);

        // 1. 解析参数并构建盘点单主记录
        InventoryStocktakeOrder order = new InventoryStocktakeOrder();

        // 生成盘点单号
        String stocktakeNo = redisSequenceService.generateBusinessNumber("ST", "盘点单");
        order.setStocktakeNo(stocktakeNo);

        // 基础字段
        order.setStocktakeType((String) params.getOrDefault("stocktakeType", "full"));
        if (params.get("warehouseId") != null) {
            order.setWarehouseId(Long.valueOf(params.get("warehouseId").toString()));
        }
        order.setLocationIds((String) params.get("locationIds"));
        order.setMaterialIds((String) params.get("materialIds"));

        // 时间字段
        String planStartStr = (String) params.get("planStartTime");
        String planEndStr = (String) params.get("planEndTime");
        if (planStartStr != null) {
            order.setPlanStartTime(LocalDateTime.parse(planStartStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (planEndStr != null) {
            order.setPlanEndTime(LocalDateTime.parse(planEndStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        // 人员字段
        if (params.get("stocktakerId") != null) {
            order.setStocktakerId(Long.valueOf(params.get("stocktakerId").toString()));
        }
        order.setStocktakerName((String) params.get("stocktakerName"));
        if (params.get("supervisorId") != null) {
            order.setSupervisorId(Long.valueOf(params.get("supervisorId").toString()));
        }
        order.setSupervisorName((String) params.get("supervisorName"));

        // 备注
        order.setRemark((String) params.get("remark"));

        // 初始状态：草稿
        order.setOrderStatus(OrderStatusEnum.DRAFT.getCode());

        // 插入主记录
        stocktakeOrderMapper.insert(order);
        Long stocktakeId = order.getStocktakeId();
        log.info("盘点单主记录已创建: stocktakeId={}, stocktakeNo={}", stocktakeId, stocktakeNo);

        // 2. 如果指定了预载库存快照，则生成盘点明细
        boolean preloadItems = params.get("preloadItems") == null || Boolean.TRUE.equals(params.get("preloadItems"));
        if (preloadItems) {
            createStocktakeItems(order);
        }

        return stocktakeId;
    }

    /**
     * 预载盘点明细：根据盘点范围将当前库存快照写入 inventory_stocktake_item
     */
    private void createStocktakeItems(InventoryStocktakeOrder order) {
        List<InventoryMaterial> materials;

        // 判断盘点范围：all=全盘，指定的materials
        String materialIdsStr = order.getMaterialIds();
        if (materialIdsStr != null && !materialIdsStr.isEmpty() && !"all".equals(materialIdsStr)) {
            // 解析物料ID列表
            List<Long> ids = parseIdList(materialIdsStr);
            if (!ids.isEmpty()) {
                materials = materialMapper.selectBatchIds(ids);
            } else {
                // 查询所有启用的物料
                materials = materialMapper.selectList(
                        new LambdaQueryWrapper<InventoryMaterial>().eq(InventoryMaterial::getStatus, 1));
            }
        } else {
            // 全盘：查询所有启用物料
            materials = materialMapper.selectList(
                    new LambdaQueryWrapper<InventoryMaterial>().eq(InventoryMaterial::getStatus, 1));
        }

        if (materials == null || materials.isEmpty()) {
            log.warn("盘点单{}没有物料可盘点", order.getStocktakeNo());
            return;
        }

        int sortOrder = 1;
        for (InventoryMaterial material : materials) {
            // 查询当前系统库存数量
            BigDecimal systemQty = BigDecimal.ZERO;
            BigDecimal unitCost = BigDecimal.ZERO;
            InventoryStock stock = stockMapper.selectByMaterialId(material.getMaterialId());
            if (stock != null) {
                systemQty = stock.getTotalQuantity() != null ? stock.getTotalQuantity() : BigDecimal.ZERO;
            }
            // 从物料表获取标准单价作为单位成本
            if (material.getStandardPrice() != null) {
                unitCost = material.getStandardPrice();
            }

            InventoryStocktakeItem item = new InventoryStocktakeItem();
            item.setStocktakeId(order.getStocktakeId());
            item.setMaterialId(material.getMaterialId());
            item.setMaterialCode(material.getMaterialCode());
            item.setMaterialName(material.getMaterialName());
            item.setSystemQuantity(systemQty);
            item.setActualQuantity(null); // 待盘点录入
            item.setUnitCost(unitCost);
            item.setAdjustStatus(0);
            item.setRemark(String.valueOf(sortOrder)); // 用remark暂存排序

            stocktakeItemMapper.insert(item);
            sortOrder++;
        }

        log.info("盘点单{}预载了{}个物料库存快照", order.getStocktakeNo(), materials.size());
    }

    /**
     * 解析逗号分隔的ID列表
     */
    private List<Long> parseIdList(String idStr) {
        List<Long> ids = new ArrayList<>();
        if (idStr == null || idStr.isEmpty()) return ids;
        try {
            // 尝试JSON数组格式 [1,2,3]
            if (idStr.startsWith("[")) {
                String trimmed = idStr.substring(1, idStr.length() - 1);
                for (String s : trimmed.split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) {
                        ids.add(Long.parseLong(s));
                    }
                }
            } else {
                // 逗号分隔
                for (String s : idStr.split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) {
                        ids.add(Long.parseLong(s));
                    }
                }
            }
        } catch (NumberFormatException e) {
            log.warn("解析物料ID列表失败: {}", idStr);
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startStocktake(Long stocktakeId) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!OrderStatusEnum.DRAFT.getCode().equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法开始盘点: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.PROCESSING.getCode());
        order.setActualStartTime(LocalDateTime.now());
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean inputStocktakeData(Long stocktakeId, List<Map<String, Object>> items) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!OrderStatusEnum.PROCESSING.getCode().equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法录入数据: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        if (items == null || items.isEmpty()) {
            log.warn("盘点数据为空: stocktakeId={}", stocktakeId);
            return true;
        }

        // 遍历items，更新每条明细的实盘数量
        for (Map<String, Object> itemMap : items) {
            if (itemMap.get("itemId") == null) {
                log.warn("盘点数据缺少itemId: {}", itemMap);
                continue;
            }

            Long itemId = Long.valueOf(itemMap.get("itemId").toString());
            InventoryStocktakeItem existingItem = stocktakeItemMapper.selectById(itemId);
            if (existingItem == null) {
                log.warn("盘点明细不存在: itemId={}", itemId);
                continue;
            }
            if (!stocktakeId.equals(existingItem.getStocktakeId())) {
                log.warn("盘点明细不属于当前盘点单: itemId={}, stocktakeId={}", itemId, stocktakeId);
                continue;
            }

            // 更新实盘数量
            if (itemMap.get("actualQuantity") != null) {
                BigDecimal actualQty = new BigDecimal(itemMap.get("actualQuantity").toString());
                existingItem.setActualQuantity(actualQty);
            }

            // 记录盘点时间和盘点人
            existingItem.setStocktakeTime(LocalDateTime.now());
            if (itemMap.get("stocktakeBy") != null) {
                existingItem.setStocktakeBy(itemMap.get("stocktakeBy").toString());
            } else if (order.getStocktakerName() != null) {
                existingItem.setStocktakeBy(order.getStocktakerName());
            }

            // 备注
            if (itemMap.get("remark") != null) {
                existingItem.setRemark(itemMap.get("remark").toString());
            }

            stocktakeItemMapper.updateById(existingItem);
        }

        log.info("盘点数据录入完成: stocktakeId={}, 录入{}条", stocktakeId, items.size());
        return true;
    }

    @Override
    public Map<String, Object> calculateDiff(Long stocktakeId) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return Map.of("success", false, "message", "盘点单不存在");
        }

        // 查询所有明细
        List<InventoryStocktakeItem> items = stocktakeItemMapper.selectByStocktakeId(stocktakeId);
        if (items == null || items.isEmpty()) {
            log.warn("盘点单{}没有明细数据", stocktakeId);
            return Map.of("success", false, "message", "没有盘点明细数据");
        }

        BigDecimal totalDiffQty = BigDecimal.ZERO;
        BigDecimal totalDiffAmt = BigDecimal.ZERO;
        List<Map<String, Object>> diffList = new ArrayList<>();
        int surplusCount = 0;  // 盘盈
        int lossCount = 0;     // 盘亏

        for (InventoryStocktakeItem item : items) {
            // 系统数量默认为0
            BigDecimal systemQty = item.getSystemQuantity() != null ? item.getSystemQuantity() : BigDecimal.ZERO;
            // 实盘数量默认为0
            BigDecimal actualQty = item.getActualQuantity() != null ? item.getActualQuantity() : BigDecimal.ZERO;

            // 差异 = 实盘 - 系统
            BigDecimal diffQty = actualQty.subtract(systemQty);
            BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;
            BigDecimal diffAmt = diffQty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);

            // 更新到明细记录
            item.setDiffQuantity(diffQty);
            item.setDiffAmount(diffAmt);
            stocktakeItemMapper.updateById(item);

            // 累加总额
            totalDiffQty = totalDiffQty.add(diffQty);
            totalDiffAmt = totalDiffAmt.add(diffAmt);

            // 统计盘盈/盘亏
            String diffType;
            if (diffQty.compareTo(BigDecimal.ZERO) > 0) {
                diffType = "surplus";
                surplusCount++;
            } else if (diffQty.compareTo(BigDecimal.ZERO) < 0) {
                diffType = "loss";
                lossCount++;
            } else {
                diffType = "match";
            }

            Map<String, Object> diffEntry = Map.of(
                    "itemId", item.getItemId(),
                    "materialId", item.getMaterialId(),
                    "materialCode", item.getMaterialCode(),
                    "materialName", item.getMaterialName(),
                    "systemQuantity", systemQty,
                    "actualQuantity", actualQty,
                    "diffQuantity", diffQty,
                    "unitCost", unitCost,
                    "diffAmount", diffAmt,
                    "diffType", diffType
            );
            diffList.add(diffEntry);
        }

        // 更新盘点单主记录的汇总数据
        order.setTotalSystemQuantity(
                items.stream()
                        .map(i -> i.getSystemQuantity() != null ? i.getSystemQuantity() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        order.setTotalActualQuantity(
                items.stream()
                        .map(i -> i.getActualQuantity() != null ? i.getActualQuantity() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        order.setTotalDiffQuantity(totalDiffQty);
        order.setTotalDiffAmount(totalDiffAmt);
        stocktakeOrderMapper.updateById(order);

        log.info("盘点差异计算完成: stocktakeId={}, 盘盈{}个, 盘亏{}个, 差异数量={}, 差异金额={}",
                stocktakeId, surplusCount, lossCount, totalDiffQty, totalDiffAmt);

        return Map.of(
                "success", true,
                "stocktakeId", stocktakeId,
                "totalSystemQuantity", order.getTotalSystemQuantity(),
                "totalActualQuantity", order.getTotalActualQuantity(),
                "totalDiffQuantity", totalDiffQty,
                "totalDiffAmount", totalDiffAmt,
                "surplusCount", surplusCount,
                "lossCount", lossCount,
                "items", diffList
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmResult(Long stocktakeId) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!OrderStatusEnum.PROCESSING.getCode().equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法确认结果: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.CONFIRMED.getCode());
        order.setActualEndTime(LocalDateTime.now());
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean processDiff(Long stocktakeId, Long operatorId, String operatorName) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!OrderStatusEnum.CONFIRMED.getCode().equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法处理盈亏: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        // 查询所有明细
        List<InventoryStocktakeItem> items = stocktakeItemMapper.selectByStocktakeId(stocktakeId);
        if (items == null || items.isEmpty()) {
            log.warn("盘点单{}没有明细数据，直接标记为已处理", stocktakeId);
            order.setOrderStatus(OrderStatusEnum.PROCESSED.getCode());
            return stocktakeOrderMapper.updateById(order) > 0;
        }

        // 确保差异已经计算
        boolean diffCalculated = items.stream().anyMatch(i -> i.getDiffQuantity() != null);
        if (!diffCalculated) {
            log.warn("盘点单{}尚未计算差异，先执行计算", stocktakeId);
            // 手动计算差异
            BigDecimal totalDiffQty = BigDecimal.ZERO;
            BigDecimal totalDiffAmt = BigDecimal.ZERO;
            for (InventoryStocktakeItem item : items) {
                BigDecimal systemQty = item.getSystemQuantity() != null ? item.getSystemQuantity() : BigDecimal.ZERO;
                BigDecimal actualQty = item.getActualQuantity() != null ? item.getActualQuantity() : BigDecimal.ZERO;
                BigDecimal diffQty = actualQty.subtract(systemQty);
                BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;
                BigDecimal diffAmt = diffQty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);

                item.setDiffQuantity(diffQty);
                item.setDiffAmount(diffAmt);

                totalDiffQty = totalDiffQty.add(diffQty);
                totalDiffAmt = totalDiffAmt.add(diffAmt);
            }
            order.setTotalDiffQuantity(totalDiffQty);
            order.setTotalDiffAmount(totalDiffAmt);
            stocktakeOrderMapper.updateById(order);
        }

        // 分组：盘盈(diff>0)和盘亏(diff<0)
        List<InventoryStocktakeItem> surplusItems = items.stream()
                .filter(i -> i.getDiffQuantity() != null && i.getDiffQuantity().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        List<InventoryStocktakeItem> lossItems = items.stream()
                .filter(i -> i.getDiffQuantity() != null && i.getDiffQuantity().compareTo(BigDecimal.ZERO) < 0)
                .collect(Collectors.toList());

        Long warehouseId = order.getWarehouseId() != null ? order.getWarehouseId() : 1L;

        // 1. 盘盈 → 创建入库单
        if (!surplusItems.isEmpty()) {
            String inboundNo = redisSequenceService.generateBusinessNumber("SI", "盘盈入库");
            InventoryInboundOrder inboundOrder = new InventoryInboundOrder();
            inboundOrder.setInboundNo(inboundNo);
            inboundOrder.setInboundType("adjust");
            inboundOrder.setSourceType("STOCKTAKE");
            inboundOrder.setSourceId(stocktakeId);
            inboundOrder.setSourceNo(order.getStocktakeNo());
            inboundOrder.setWarehouseId(warehouseId);
            inboundOrder.setOrderStatus(OrderStatusEnum.DRAFT.getCode()); // 创建为草稿状态，等待后续确认
            inboundOrder.setInboundDate(LocalDate.now());
            inboundOrderMapper.insert(inboundOrder);
            Long inboundId = inboundOrder.getInboundId();

            BigDecimal totalInQty = BigDecimal.ZERO;
            BigDecimal totalInAmt = BigDecimal.ZERO;
            int sort = 1;

            for (InventoryStocktakeItem item : surplusItems) {
                BigDecimal diffQty = item.getDiffQuantity(); // 正数

                InventoryInboundItem inboundItem = new InventoryInboundItem();
                inboundItem.setInboundId(inboundId);
                inboundItem.setMaterialId(item.getMaterialId());
                inboundItem.setMaterialCode(item.getMaterialCode());
                inboundItem.setMaterialName(item.getMaterialName());
                inboundItem.setQuantity(diffQty);
                inboundItem.setUnitPrice(item.getUnitCost());
                inboundItem.setAmount(item.getDiffAmount() != null ? item.getDiffAmount() : diffQty.multiply(
                        item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO));
                inboundItem.setSortOrder(sort++);
                inboundItemMapper.insert(inboundItem);

                totalInQty = totalInQty.add(diffQty);
                totalInAmt = totalInAmt.add(inboundItem.getAmount() != null ? inboundItem.getAmount() : BigDecimal.ZERO);

                // 更新盘点明细的处理状态
                item.setAdjustStatus(1);
                item.setAdjustOrderId(inboundId);
                item.setReason("盘盈入库");
                stocktakeItemMapper.updateById(item);
            }

            // 更新入库单汇总
            inboundOrder.setTotalQuantity(totalInQty);
            inboundOrder.setTotalAmount(totalInAmt);
            inboundOrderMapper.updateById(inboundOrder);

            log.info("盘盈入库单已创建: inboundId={}, inboundNo={}, 物料数={}", inboundId, inboundNo, surplusItems.size());
        }

        // 2. 盘亏 → 创建出库单
        if (!lossItems.isEmpty()) {
            String outboundNo = redisSequenceService.generateBusinessNumber("SO", "盘亏出库");
            InventoryOutboundOrder outboundOrder = new InventoryOutboundOrder();
            outboundOrder.setOutboundNo(outboundNo);
            outboundOrder.setOutboundType("adjust");
            outboundOrder.setSourceType("STOCKTAKE");
            outboundOrder.setSourceId(stocktakeId);
            outboundOrder.setSourceNo(order.getStocktakeNo());
            outboundOrder.setWarehouseId(warehouseId);
            outboundOrder.setOrderStatus(OrderStatusEnum.DRAFT.getCode()); // 创建为草稿状态
            outboundOrder.setOutboundDate(LocalDate.now());
            outboundOrderMapper.insert(outboundOrder);
            Long outboundId = outboundOrder.getOutboundId();

            BigDecimal totalOutQty = BigDecimal.ZERO;
            BigDecimal totalOutAmt = BigDecimal.ZERO;
            int sort = 1;

            for (InventoryStocktakeItem item : lossItems) {
                BigDecimal diffQty = item.getDiffQuantity().abs(); // 取绝对值

                InventoryOutboundItem outboundItem = new InventoryOutboundItem();
                outboundItem.setOutboundId(outboundId);
                outboundItem.setMaterialId(item.getMaterialId());
                outboundItem.setMaterialCode(item.getMaterialCode());
                outboundItem.setMaterialName(item.getMaterialName());
                outboundItem.setQuantity(diffQty);
                outboundItem.setUnitPrice(item.getUnitCost());
                outboundItem.setAmount(item.getDiffAmount() != null ? item.getDiffAmount().abs()
                        : diffQty.multiply(item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO));
                outboundItem.setSortOrder(sort++);
                outboundItemMapper.insert(outboundItem);

                totalOutQty = totalOutQty.add(diffQty);
                totalOutAmt = totalOutAmt.add(outboundItem.getAmount() != null ? outboundItem.getAmount() : BigDecimal.ZERO);

                // 更新盘点明细的处理状态
                item.setAdjustStatus(1);
                item.setAdjustOrderId(outboundId);
                item.setReason("盘亏出库");
                stocktakeItemMapper.updateById(item);
            }

            // 更新出库单汇总
            outboundOrder.setTotalQuantity(totalOutQty);
            outboundOrder.setTotalAmount(totalOutAmt);
            outboundOrderMapper.updateById(outboundOrder);

            log.info("盘亏出库单已创建: outboundId={}, outboundNo={}, 物料数={}", outboundId, outboundNo, lossItems.size());
        }

        // 3. 更新盘点单状态为 completed (已处理完成)
        order.setOrderStatus(OrderStatusEnum.PROCESSED.getCode());
        int updated = stocktakeOrderMapper.updateById(order);

        log.info("盘点盈亏处理完成: stocktakeId={}, 盘盈{}个, 盘亏{}个, operatorId={}, operatorName={}",
                stocktakeId, surplusItems.size(), lossItems.size(), operatorId, operatorName);

        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeStocktake(Long stocktakeId) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!OrderStatusEnum.PROCESSED.getCode().equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法关闭: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.CLOSED.getCode());
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean submitApprove(Long stocktakeId) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        order.setApproveStatus(OrderStatusEnum.PENDING.getCode());
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean approve(Long stocktakeId, Long approverId, String approverName, String remark) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!OrderStatusEnum.PENDING.getCode().equals(order.getApproveStatus())) {
            log.error("盘点单审批状态不正确，无法审批: stocktakeId={}, status={}", stocktakeId, order.getApproveStatus());
            return false;
        }

        order.setApproveStatus(OrderStatusEnum.APPROVED.getCode());
        order.setApproverId(approverId);
        order.setApproverName(approverName);
        order.setApproveRemark(remark);
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    public List<StocktakeVO> getProcessing() {
        List<InventoryStocktakeOrder> orders = stocktakeOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryStocktakeOrder>()
                        .eq(InventoryStocktakeOrder::getOrderStatus, OrderStatusEnum.PROCESSING.getCode())
                        .orderByAsc(InventoryStocktakeOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public List<StocktakeVO> getPendingApproval() {
        List<InventoryStocktakeOrder> orders = stocktakeOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryStocktakeOrder>()
                        .eq(InventoryStocktakeOrder::getApproveStatus, OrderStatusEnum.PENDING.getCode())
                        .orderByAsc(InventoryStocktakeOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public boolean updateStatus(Long stocktakeId, Integer status) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        order.setOrderStatus(status);
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    public IPage<InventoryStocktakeOrder> pageQuery(Map<String, Object> params) {
        String stocktakeNo = (String) params.get("stocktakeNo");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryStocktakeOrder> wrapper = new LambdaQueryWrapper<>();
        if (stocktakeNo != null && !stocktakeNo.isEmpty()) {
            wrapper.like(InventoryStocktakeOrder::getStocktakeNo, stocktakeNo);
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryStocktakeOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryStocktakeOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryStocktakeOrder::getCreateTime);

        Page<InventoryStocktakeOrder> page = new Page<>(pageNum, pageSize);
        return stocktakeOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Map<String, Object> params) {
        if (params != null && params.get("stocktakeId") != null) {
            Long stocktakeId = Long.valueOf(params.get("stocktakeId").toString());
            StocktakeVO detail = getDetail(stocktakeId);
            if (detail != null) {
                return Map.of("code", 200, "data", detail);
            }
        }
        return Map.of("code", 404, "message", "盘点单不存在");
    }

    // ==================== 转换方法 ====================

    private static List<StocktakeVO> convertToVOList(List<InventoryStocktakeOrder> orders) {
        List<StocktakeVO> result = new ArrayList<>();
        for (InventoryStocktakeOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private static StocktakeVO convertToVO(InventoryStocktakeOrder order) {
        if (order == null) {
            return null;
        }

        StocktakeVO vo = new StocktakeVO();
        BeanUtils.copyProperties(order, vo);

        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());
        vo.setCreateBy(order.getCreateBy());
        vo.setCreateByName(order.getCreateBy());
        vo.setUpdateBy(order.getUpdateBy());
        vo.setUpdateByName(order.getUpdateBy());
        vo.setRemark(order.getRemark());

        return vo;
    }

    private static List<StocktakeItemVO> convertToItemVOList(List<InventoryStocktakeItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<StocktakeItemVO> result = new ArrayList<>();
        for (InventoryStocktakeItem item : items) {
            result.add(convertToItemVO(item));
        }
        return result;
    }

    private static StocktakeItemVO convertToItemVO(InventoryStocktakeItem item) {
        if (item == null) {
            return null;
        }

        StocktakeItemVO vo = new StocktakeItemVO();
        vo.setStocktakeItemId(item.getItemId());
        vo.setStocktakeId(item.getStocktakeId());
        vo.setMaterialId(item.getMaterialId());
        vo.setMaterialCode(item.getMaterialCode());
        vo.setMaterialName(item.getMaterialName());
        vo.setBatchNo(item.getBatchNo());
        vo.setLocationId(item.getLocationId());

        // 计算数量
        BigDecimal systemQty = item.getSystemQuantity() != null ? item.getSystemQuantity() : BigDecimal.ZERO;
        BigDecimal actualQty = item.getActualQuantity() != null ? item.getActualQuantity() : BigDecimal.ZERO;
        vo.setSystemQuantity(systemQty);
        vo.setActualQuantity(actualQty);

        // 差异
        BigDecimal diffQty = item.getDiffQuantity() != null ? item.getDiffQuantity() : BigDecimal.ZERO;
        BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;
        BigDecimal diffAmt = item.getDiffAmount() != null ? item.getDiffAmount() : BigDecimal.ZERO;
        vo.setDiffQuantity(diffQty);
        vo.setUnitCost(unitCost);
        vo.setDiffAmount(diffAmt);

        // 差异类型
        if (diffQty.compareTo(BigDecimal.ZERO) > 0) {
            vo.setDiffType("surplus");
        } else if (diffQty.compareTo(BigDecimal.ZERO) < 0) {
            vo.setDiffType("loss");
        } else {
            vo.setDiffType("match");
        }

        // 由 remark 暂存的排序值，尝试解析
        String remark = item.getRemark();
        if (remark != null) {
            vo.setRemark(remark);
            try {
                vo.setSortOrder(Integer.parseInt(remark));
            } catch (NumberFormatException ignored) {
            }
        }

        vo.setStocktakeTime(item.getStocktakeTime());
        vo.setStocktakeBy(item.getStocktakeBy());

        return vo;
    }

}
