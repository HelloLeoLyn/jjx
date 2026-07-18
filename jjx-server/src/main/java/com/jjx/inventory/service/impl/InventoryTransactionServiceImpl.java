package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.dto.query.TransactionQueryDTO;
import com.jjx.inventory.dto.vo.TransactionVO;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.service.InventoryTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存流水服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryTransactionServiceImpl extends ServiceImpl<InventoryTransactionMapper, InventoryTransaction>
        implements InventoryTransactionService {

    private final InventoryTransactionMapper transactionMapper;

    @Override
    public IPage<TransactionVO> page(TransactionQueryDTO query) {
        // 这里需要实现分页查询，返回TransactionVO
        // 暂时返回空分页，实际需要实现查询逻辑
        Page<TransactionVO> page = new Page<>(query.getCurrent(), query.getSize());
        return page;
    }

    @Override
    public void recordTransaction(InventoryTransaction transaction) {
        if (transaction == null) {
            log.error("库存流水记录为空");
            return;
        }

        if (transaction.getTransactionTime() == null) {
            transaction.setTransactionTime(LocalDateTime.now());
        }

        try {
            transactionMapper.insert(transaction);
            log.info("记录库存流水成功: transactionId={}, materialId={}, type={}, quantity={}",
                    transaction.getTransactionId(), transaction.getMaterialId(),
                    transaction.getTransactionType(), transaction.getQuantity());
        } catch (Exception e) {
            log.error("记录库存流水失败: {}", e.getMessage(), e);
            throw new RuntimeException("记录库存流水失败", e);
        }
    }

    @Override
    public List<TransactionVO> getBySource(String sourceType, Long sourceId) {
        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryTransaction::getSourceType, sourceType)
                .eq(InventoryTransaction::getSourceId, sourceId)
                .orderByDesc(InventoryTransaction::getTransactionTime);

        List<InventoryTransaction> transactions = transactionMapper.selectList(wrapper);
        return convertToVOList(transactions);
    }

    @Override
    public List<TransactionVO> getByMaterial(Long materialId, int limit) {
        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryTransaction::getMaterialId, materialId)
                .orderByDesc(InventoryTransaction::getTransactionTime)
                .last("LIMIT " + limit);

        List<InventoryTransaction> transactions = transactionMapper.selectList(wrapper);
        return convertToVOList(transactions);
    }

    @Override
    public List<TransactionVO> getByTimeRange(String startTime, String endTime) {
        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<>();

        if (startTime != null && !startTime.isEmpty()) {
            LocalDateTime start = parseDateTime(startTime);
            wrapper.ge(InventoryTransaction::getTransactionTime, start);
        }

        if (endTime != null && !endTime.isEmpty()) {
            LocalDateTime end = parseDateTime(endTime);
            wrapper.le(InventoryTransaction::getTransactionTime, end);
        }

        wrapper.orderByDesc(InventoryTransaction::getTransactionTime);

        List<InventoryTransaction> transactions = transactionMapper.selectList(wrapper);
        return convertToVOList(transactions);
    }

    @Override
    public Map<String, Object> statByMaterial(Long materialId, String startTime) {
        Map<String, Object> result = new HashMap<>();

        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryTransaction::getMaterialId, materialId);

        if (startTime != null && !startTime.isEmpty()) {
            LocalDateTime start = parseDateTime(startTime);
            wrapper.ge(InventoryTransaction::getTransactionTime, start);
        }

        List<InventoryTransaction> transactions = transactionMapper.selectList(wrapper);

        double inboundQuantity = 0;
        double outboundQuantity = 0;
        double inboundAmount = 0;
        double outboundAmount = 0;

        for (InventoryTransaction transaction : transactions) {
            double quantity = transaction.getQuantity() != null ? transaction.getQuantity().doubleValue() : 0;
            double amount = transaction.getAmount() != null ? transaction.getAmount().doubleValue() : 0;

            if ("inbound".equals(transaction.getTransactionType()) || "transfer_in".equals(transaction.getTransactionType())) {
                inboundQuantity += Math.abs(quantity);
                inboundAmount += Math.abs(amount);
            } else if ("outbound".equals(transaction.getTransactionType()) || "transfer_out".equals(transaction.getTransactionType())) {
                outboundQuantity += Math.abs(quantity);
                outboundAmount += Math.abs(amount);
            }
        }

        result.put("materialId", materialId);
        result.put("inboundQuantity", inboundQuantity);
        result.put("outboundQuantity", outboundQuantity);
        result.put("inboundAmount", inboundAmount);
        result.put("outboundAmount", outboundAmount);
        result.put("netQuantity", inboundQuantity - outboundQuantity);
        result.put("netAmount", inboundAmount - outboundAmount);
        result.put("transactionCount", transactions.size());

        return result;
    }

    @Override
    public TransactionVO getById(Long transactionId) {
        InventoryTransaction transaction = transactionMapper.selectById(transactionId);
        if (transaction == null) {
            return null;
        }
        return convertToVO(transaction);
    }

    @Override
    public IPage<InventoryTransaction> pageQuery(Map<String, Object> params) {
        Long materialId = (Long) params.get("materialId");
        Long warehouseId = (Long) params.get("warehouseId");
        String transactionType = (String) params.get("transactionType");
        String sourceType = (String) params.get("sourceType");
        String startTime = (String) params.get("startTime");
        String endTime = (String) params.get("endTime");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<>();
        if (materialId != null) {
            wrapper.eq(InventoryTransaction::getMaterialId, materialId);
        }
        if (warehouseId != null) {
            wrapper.eq(InventoryTransaction::getWarehouseId, warehouseId);
        }
        if (transactionType != null && !transactionType.isEmpty()) {
            wrapper.eq(InventoryTransaction::getTransactionType, transactionType);
        }
        if (sourceType != null && !sourceType.isEmpty()) {
            wrapper.eq(InventoryTransaction::getSourceType, sourceType);
        }
        if (startTime != null && !startTime.isEmpty()) {
            LocalDateTime start = parseDateTime(startTime);
            wrapper.ge(InventoryTransaction::getTransactionTime, start);
        }
        if (endTime != null && !endTime.isEmpty()) {
            LocalDateTime end = parseDateTime(endTime);
            wrapper.le(InventoryTransaction::getTransactionTime, end);
        }
        wrapper.orderByDesc(InventoryTransaction::getTransactionTime);

        Page<InventoryTransaction> page = new Page<>(pageNum, pageSize);
        return transactionMapper.selectPage(page, wrapper);
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            if (dateTimeStr.length() == 10) {
                // yyyy-MM-dd
                return LocalDateTime.parse(dateTimeStr + "T00:00:00");
            } else {
                // yyyy-MM-dd HH:mm:ss
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(dateTimeStr, formatter);
            }
        } catch (Exception e) {
            log.error("解析时间字符串失败: {}", dateTimeStr, e);
            return LocalDateTime.now();
        }
    }

    private List<TransactionVO> convertToVOList(List<InventoryTransaction> transactions) {
        List<TransactionVO> result = new ArrayList<>();
        for (InventoryTransaction transaction : transactions) {
            result.add(convertToVO(transaction));
        }
        return result;
    }

    private TransactionVO convertToVO(InventoryTransaction transaction) {
        if (transaction == null) {
            return null;
        }

        TransactionVO vo = new TransactionVO();
        vo.setTransactionId(transaction.getTransactionId());
        vo.setMaterialId(transaction.getMaterialId());
        vo.setMaterialCode(transaction.getMaterialCode());
        vo.setMaterialName(transaction.getMaterialName());
        vo.setWarehouseId(transaction.getWarehouseId());
        vo.setLocationId(transaction.getLocationId());
        vo.setTransactionType(transaction.getTransactionType());
        vo.setSourceType(transaction.getSourceType());
        vo.setSourceId(transaction.getSourceId());
        vo.setSourceNo(transaction.getSourceNo());
        vo.setBatchNo(transaction.getBatchNo());
        vo.setQuantity(transaction.getQuantity());
        vo.setBeforeQuantity(transaction.getBeforeQuantity());
        vo.setAfterQuantity(transaction.getAfterQuantity());
        vo.setUnitCost(transaction.getUnitCost());
        vo.setAmount(transaction.getAmount());
        vo.setTransactionTime(transaction.getTransactionTime());
        vo.setOperatorId(transaction.getOperatorId());
        vo.setOperatorName(transaction.getOperatorName());
        vo.setCreateTime(transaction.getCreateTime());
        vo.setRemark(transaction.getRemark());

        // 设置类型名称
        vo.setTransactionTypeName(getTransactionTypeName(transaction.getTransactionType()));
        vo.setSourceTypeName(getSourceTypeName(transaction.getSourceType()));

        return vo;
    }

    private String getTransactionTypeName(String transactionType) {
        if (transactionType == null) {
            return "";
        }
        switch (transactionType) {
            case "inbound": return "入库";
            case "outbound": return "出库";
            case "transfer_in": return "调拨入库";
            case "transfer_out": return "调拨出库";
            case "adjust": return "盘盈盘亏";
            default: return transactionType;
        }
    }

    private String getSourceTypeName(String sourceType) {
        if (sourceType == null) {
            return "";
        }
        switch (sourceType) {
            case "purchase_order": return "采购订单";
            case "work_order": return "工单";
            case "sales_order": return "销售订单";
            case "stocktake": return "盘点单";
            default: return sourceType;
        }
    }
}
