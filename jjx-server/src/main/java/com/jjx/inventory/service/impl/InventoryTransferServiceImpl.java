package com.jjx.inventory.service.impl;

import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryStorageLocation;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.domain.InventoryTransferItem;
import com.jjx.inventory.domain.InventoryTransferOrder;
import com.jjx.inventory.domain.InventoryWarehouse;
import com.jjx.inventory.dto.query.TransferQueryDTO;
import com.jjx.inventory.dto.vo.TransferItemVO;
import com.jjx.inventory.dto.vo.TransferVO;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStorageLocationMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.mapper.InventoryTransferItemMapper;
import com.jjx.inventory.mapper.InventoryTransferOrderMapper;
import com.jjx.inventory.mapper.InventoryWarehouseMapper;
import com.jjx.inventory.service.InventoryTransferService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.jjx.system.annotation.Event;

/**
 * 调拨服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryTransferServiceImpl extends ServiceImpl<InventoryTransferOrderMapper, InventoryTransferOrder>
        implements InventoryTransferService {

    private final InventoryTransferOrderMapper transferOrderMapper;
    private final InventoryTransferItemMapper transferItemMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final InventoryWarehouseMapper transferWarehouseMapper;
    private final InventoryStorageLocationMapper transferLocationMapper;

    @Override
    public IPage<TransferVO> page(TransferQueryDTO query) {
        // DEV-694：实现真实分页查询（原为空实现，导致调拨列表永远空白）
        LambdaQueryWrapper<InventoryTransferOrder> wrapper = new LambdaQueryWrapper<>();

        if (query.getTransferNo() != null && !query.getTransferNo().isEmpty()) {
            wrapper.like(InventoryTransferOrder::getTransferNo, query.getTransferNo());
        }
        if (query.getTransferType() != null && !query.getTransferType().isEmpty()) {
            wrapper.eq(InventoryTransferOrder::getTransferType, query.getTransferType());
        }
        if (query.getFromWarehouseId() != null) {
            wrapper.eq(InventoryTransferOrder::getFromWarehouseId, query.getFromWarehouseId());
        }
        if (query.getToWarehouseId() != null) {
            wrapper.eq(InventoryTransferOrder::getToWarehouseId, query.getToWarehouseId());
        }
        if (query.getOrderStatus() != null && !query.getOrderStatus().isEmpty()) {
            wrapper.eq(InventoryTransferOrder::getOrderStatus, query.getOrderStatus());
        }
        if (query.getApproveStatus() != null && !query.getApproveStatus().isEmpty()) {
            wrapper.eq(InventoryTransferOrder::getApproveStatus, query.getApproveStatus());
        }
        if (query.getTransferDateStart() != null) {
            wrapper.ge(InventoryTransferOrder::getTransferDate, query.getTransferDateStart());
        }
        if (query.getTransferDateEnd() != null) {
            wrapper.le(InventoryTransferOrder::getTransferDate, query.getTransferDateEnd());
        }

        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            boolean isAsc = "asc".equalsIgnoreCase(query.getOrderDirection());
            if ("transferNo".equals(query.getOrderBy())) {
                wrapper.orderBy(true, isAsc, InventoryTransferOrder::getTransferNo);
            } else {
                wrapper.orderBy(true, isAsc, InventoryTransferOrder::getCreateTime);
            }
        } else {
            wrapper.orderByDesc(InventoryTransferOrder::getCreateTime).orderByDesc(InventoryTransferOrder::getTransferId);
        }

        Page<InventoryTransferOrder> orderPage = new Page<>(query.getCurrent(), query.getSize());
        IPage<InventoryTransferOrder> orderResult = transferOrderMapper.selectPage(orderPage, wrapper);

        Page<TransferVO> voPage = new Page<>(query.getCurrent(), query.getSize());
        voPage.setTotal(orderResult.getTotal());
        voPage.setPages(orderResult.getPages());
        voPage.setRecords(convertToVOList(orderResult.getRecords()));
        return voPage;
    }

    @Override
    public TransferVO getDetail(Long transferId) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return null;
        }
        TransferVO vo = convertToVO(order);
        List<InventoryTransferItem> items = transferItemMapper.selectByTransferId(transferId);
        if (items != null && !items.isEmpty()) {
            vo.setItems(convertToItemVOList(items));
        }
        return vo;
    }

    @Override
    @Event(value = "inventory.transfer.created", bizId = "#params", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> params) {
        log.info("创建调拨单: {}", params);

        // 1. 校验必有字段
        if (params.get("fromWarehouseId") == null) {
            throw new BusinessException("调出仓库不能为空");
        }
        if (params.get("toWarehouseId") == null) {
            throw new BusinessException("调入仓库不能为空");
        }
        if (params.get("items") == null || ((List<?>) params.get("items")).isEmpty()) {
            throw new BusinessException("调拨明细不能为空");
        }

        // 2. 解析基础字段
        InventoryTransferOrder order = new InventoryTransferOrder();
        String transferNo = (String) params.get("transferNo");
        if (transferNo == null || transferNo.isEmpty()) {
            // 自动生成调拨单号：TR + yyyyMMdd + 4位流水
            transferNo = "TR" + LocalDate.now().toString().replace("-", "")
                    + String.format("%04d", System.currentTimeMillis() % 10000);
        }
        order.setTransferNo(transferNo);
        order.setTransferType((String) params.getOrDefault("transferType", "normal"));
        order.setFromWarehouseId(Long.valueOf(params.get("fromWarehouseId").toString()));
        order.setToWarehouseId(Long.valueOf(params.get("toWarehouseId").toString()));
        if (params.get("fromLocationId") != null) {
            order.setFromLocationId(Long.valueOf(params.get("fromLocationId").toString()));
        }
        if (params.get("toLocationId") != null) {
            order.setToLocationId(Long.valueOf(params.get("toLocationId").toString()));
        }
        // 处理 transferDate
        if (params.get("transferDate") != null) {
            String dateStr = params.get("transferDate").toString();
            order.setTransferDate(LocalDate.parse(dateStr));
        } else {
            order.setTransferDate(LocalDate.now());
        }
        // 处理 expectedDate
        if (params.get("expectedDate") != null) {
            String dateStr = params.get("expectedDate").toString();
            order.setExpectedDate(LocalDate.parse(dateStr));
        }
        order.setOrderStatus(InventoryOrderStatusEnum.DRAFT.getValue());
        order.setRemark((String) params.get("remark"));

        // 3. 解析明细列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) params.get("items");
        List<InventoryTransferItem> items = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;

        for (Map<String, Object> itemMap : itemMaps) {
            InventoryTransferItem item = new InventoryTransferItem();
            if (itemMap.get("materialId") != null) {
                item.setMaterialId(Long.valueOf(itemMap.get("materialId").toString()));
            }
            item.setMaterialCode((String) itemMap.get("materialCode"));
            item.setMaterialName((String) itemMap.get("materialName"));
            item.setSpecification((String) itemMap.get("specification"));
            item.setUnit((String) itemMap.get("unit"));
            if (itemMap.get("quantity") != null) {
                item.setQuantity(new BigDecimal(itemMap.get("quantity").toString()));
                totalQty = totalQty.add(item.getQuantity());
            }
            if (itemMap.get("unitCost") != null) {
                item.setUnitCost(new BigDecimal(itemMap.get("unitCost").toString()));
            }
            if (itemMap.get("amount") != null) {
                item.setAmount(new BigDecimal(itemMap.get("amount").toString()));
                totalAmt = totalAmt.add(item.getAmount());
            } else if (item.getQuantity() != null && item.getUnitCost() != null) {
                item.setAmount(item.getQuantity().multiply(item.getUnitCost()));
                totalAmt = totalAmt.add(item.getAmount());
            }
            item.setBatchNo((String) itemMap.get("batchNo"));
            if (itemMap.get("fromLocationId") != null) {
                item.setFromLocationId(Long.valueOf(itemMap.get("fromLocationId").toString()));
            }
            if (itemMap.get("toLocationId") != null) {
                item.setToLocationId(Long.valueOf(itemMap.get("toLocationId").toString()));
            }
            item.setStatus(0);
            items.add(item);
        }

        if (items.isEmpty()) {
            throw new BusinessException("调拨明细不能为空");
        }

        order.setTotalQuantity(totalQty);
        order.setTotalAmount(totalAmt);

        // 4. 插入主记录
        transferOrderMapper.insert(order);
        Long transferId = order.getTransferId();

        // 5. 插入明细
        for (InventoryTransferItem item : items) {
            item.setTransferId(transferId);
            transferItemMapper.insert(item);
        }

        log.info("调拨单创建成功: transferId={}, transferNo={}", transferId, transferNo);
        return transferId;
    }

    @Override
    @Event(value = "inventory.transfer.submitted", bizId = "#transferId", bizType = "'inventory'")
    public boolean submitApprove(Long transferId) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        order.setApproveStatus(InventoryOrderStatusEnum.PENDING.getValue());
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    @Event(value = "inventory.transfer.approved", bizId = "#transferId", bizType = "'inventory'")
    public boolean approve(Long transferId, Long approverId, String approverName, String remark) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        if (!InventoryOrderStatusEnum.PENDING.getValue().equals(order.getApproveStatus())) {
            log.error("调拨单审批状态不正确，无法审批: transferId={}, status={}", transferId, order.getApproveStatus());
            return false;
        }

        order.setApproveStatus(InventoryOrderStatusEnum.APPROVED.getValue());
        order.setApproverId(approverId);
        order.setApproverName(approverName);
        order.setApproveRemark(remark);
        order.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    @Event(value = "inventory.transfer.rejected", bizId = "#transferId", bizType = "'inventory'")
    public boolean reject(Long transferId, Long approverId, String approverName, String remark) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        if (!InventoryOrderStatusEnum.PENDING.getValue().equals(order.getApproveStatus())) {
            log.error("调拨单审批状态不正确，无法驳回: transferId={}, status={}", transferId, order.getApproveStatus());
            return false;
        }

        order.setApproveStatus(InventoryOrderStatusEnum.REJECTED.getValue());
        order.setApproverId(approverId);
        order.setApproverName(approverName);
        order.setApproveRemark(remark);
        order.setOrderStatus(InventoryOrderStatusEnum.CANCELLED.getValue());
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    @Event(value = "inventory.transfer.confirmed_out", bizId = "#transferId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmOut(Long transferId, Long operatorId, String operatorName) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        // 校验状态：必须已审核
        if (!InventoryOrderStatusEnum.APPROVED.getValue().equals(order.getApproveStatus())) {
            log.error("调拨单审批状态不正确，无法调出: transferId={}, approveStatus={}", transferId, order.getApproveStatus());
            return false;
        }
        if (!InventoryOrderStatusEnum.APPROVED.getValue().equals(order.getOrderStatus())) {
            log.error("调拨单状态不正确，无法调出: transferId={}, orderStatus={}", transferId, order.getOrderStatus());
            return false;
        }

        // 查询调拨明细
        List<InventoryTransferItem> items = transferItemMapper.selectByTransferId(transferId);
        if (items == null || items.isEmpty()) {
            log.error("调拨单明细为空: transferId={}", transferId);
            return false;
        }

        // 遍历明细，执行调出仓库出库（FIFO扣减）
        for (InventoryTransferItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal remaining = item.getQuantity();

            // 按FIFO顺序从调出仓库扣减库存
            List<InventoryStockItem> fifoItems = stockItemMapper.selectActiveByMaterialAndWarehouse(
                    item.getMaterialId(), order.getFromWarehouseId());

            for (InventoryStockItem si : fifoItems) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal available = si.getQuantity().subtract(si.getReservedQuantity());
                if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal deductQty = remaining.min(available);
                stockItemMapper.deductStock(si.getItemId(), deductQty);
                remaining = remaining.subtract(deductQty);
            }

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("物料[" + item.getMaterialCode() + "]在调出仓库库存不足，缺少: " + remaining);
            }

            // 刷新库存汇总
            stockMapper.refreshSummary(item.getMaterialId());

            // 更新明细的出库数量
            transferItemMapper.updateOutQuantity(item.getItemId(), item.getQuantity(), order.getFromLocationId());

            // 记录库存流水（调拨出库）
            InventoryTransaction tx = new InventoryTransaction();
            tx.setMaterialId(item.getMaterialId());
            tx.setMaterialCode(item.getMaterialCode());
            tx.setMaterialName(item.getMaterialName());
            tx.setWarehouseId(order.getFromWarehouseId());
            tx.setLocationId(order.getFromLocationId());
            tx.setTransactionType("TRANSFER_OUT");
            tx.setSourceType("INVENTORY_TRANSFER");
            tx.setSourceId(transferId);
            tx.setSourceNo(order.getTransferNo());
            tx.setBatchNo(item.getBatchNo());
            tx.setQuantity(item.getQuantity().negate());
            tx.setUnitCost(item.getUnitCost());
            tx.setAmount(item.getAmount() != null ? item.getAmount().negate() : null);
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(operatorId != null ? operatorId : SecurityUtils.getUserId());
            tx.setOperatorName(operatorName != null ? operatorName : SecurityUtils.getUsername());
            tx.setRemark("调拨出库确认");
            transactionMapper.insert(tx);
        }

        // 更新调拨单状态
        order.setOrderStatus(InventoryOrderStatusEnum.OUT_CONFIRM.getValue());
        order.setOutOperator(operatorName);
        order.setOutTime(LocalDateTime.now());
        transferOrderMapper.updateById(order);

        // 使用Mapper中的confirmOut方法确保乐观锁
        transferOrderMapper.confirmOut(transferId, operatorName);

        log.info("调拨出库确认完成: transferId={}, operator={}", transferId, operatorName);
        return true;
    }

    @Override
    @Event(value = "inventory.transfer.confirmed_in", bizId = "#transferId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmIn(Long transferId, Long operatorId, String operatorName) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        // 校验状态：必须已调出
        if (!InventoryOrderStatusEnum.OUT_CONFIRM.getValue().equals(order.getOrderStatus())) {
            log.error("调拨单状态不正确，无法调入: transferId={}, orderStatus={}", transferId, order.getOrderStatus());
            return false;
        }
        if (order.getOutOperator() == null || order.getOutOperator().isEmpty()) {
            log.error("调拨单尚未调出，无法调入: transferId={}", transferId);
            return false;
        }

        // 查询调拨明细
        List<InventoryTransferItem> items = transferItemMapper.selectByTransferId(transferId);
        if (items == null || items.isEmpty()) {
            log.error("调拨单明细为空: transferId={}", transferId);
            return false;
        }

        // 遍历明细，执行调入仓库入库
        for (InventoryTransferItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // 在调入仓库创建库存明细记录
            InventoryStockItem newStock = new InventoryStockItem();
            newStock.setMaterialId(item.getMaterialId());
            newStock.setMaterialCode(item.getMaterialCode());
            newStock.setMaterialName(item.getMaterialName());
            newStock.setWarehouseId(order.getToWarehouseId());
            newStock.setLocationId(item.getToLocationId() != null ? item.getToLocationId() : order.getToLocationId());
            newStock.setBatchNo(item.getBatchNo() != null ? item.getBatchNo() : LocalDate.now().toString());
            newStock.setQuantity(item.getQuantity());
            newStock.setReservedQuantity(BigDecimal.ZERO);
            newStock.setUnitCost(item.getUnitCost());
            newStock.setStatus(1);
            newStock.setLastInboundTime(LocalDateTime.now());
            stockItemMapper.insert(newStock);

            // 刷新库存汇总
            stockMapper.refreshSummary(item.getMaterialId());

            // 更新明细的入库数量
            transferItemMapper.updateInQuantity(item.getItemId(), item.getQuantity(), order.getToLocationId());

            // 记录库存流水（调拨入库）
            InventoryTransaction tx = new InventoryTransaction();
            tx.setMaterialId(item.getMaterialId());
            tx.setMaterialCode(item.getMaterialCode());
            tx.setMaterialName(item.getMaterialName());
            tx.setWarehouseId(order.getToWarehouseId());
            tx.setLocationId(item.getToLocationId() != null ? item.getToLocationId() : order.getToLocationId());
            tx.setTransactionType("TRANSFER_IN");
            tx.setSourceType("INVENTORY_TRANSFER");
            tx.setSourceId(transferId);
            tx.setSourceNo(order.getTransferNo());
            tx.setBatchNo(item.getBatchNo());
            tx.setQuantity(item.getQuantity());
            tx.setUnitCost(item.getUnitCost());
            tx.setAmount(item.getAmount());
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(operatorId != null ? operatorId : SecurityUtils.getUserId());
            tx.setOperatorName(operatorName != null ? operatorName : SecurityUtils.getUsername());
            tx.setRemark("调拨入库确认");
            transactionMapper.insert(tx);
        }

        // 更新调拨单状态为 completed
        order.setOrderStatus(InventoryOrderStatusEnum.COMPLETED.getValue());
        order.setInOperator(operatorName);
        order.setInTime(LocalDateTime.now());
        order.setActualDate(LocalDate.now());
        transferOrderMapper.updateById(order);

        // 使用Mapper中的confirmIn方法确保乐观锁
        transferOrderMapper.confirmIn(transferId, operatorName);

        log.info("调拨入库确认完成: transferId={}, operator={}", transferId, operatorName);
        return true;
    }

    @Override
    @Event(value = "inventory.transfer.cancelled", bizId = "#transferId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long transferId, String reason) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        if (InventoryOrderStatusEnum.IN_CONFIRM.getValue().equals(order.getOrderStatus()) || InventoryOrderStatusEnum.CLOSED.getValue().equals(order.getOrderStatus())) {
            log.error("已完成的调拨单无法取消: transferId={}", transferId);
            return false;
        }

        // 方案A（2026-08-06）：OUT_CONFIRM（已调出未调入）取消 → 回补源仓库存，避免库存凭空消失
        if (InventoryOrderStatusEnum.OUT_CONFIRM.getValue().equals(order.getOrderStatus())) {
            List<InventoryTransferItem> items = transferItemMapper.selectByTransferId(transferId);
            if (items != null) {
                for (InventoryTransferItem item : items) {
                    if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    // 回补源仓库存（新增一条库存明细）
                    InventoryStockItem newStock = new InventoryStockItem();
                    newStock.setMaterialId(item.getMaterialId());
                    newStock.setMaterialCode(item.getMaterialCode());
                    newStock.setMaterialName(item.getMaterialName());
                    newStock.setWarehouseId(order.getFromWarehouseId());
                    newStock.setLocationId(item.getFromLocationId() != null ? item.getFromLocationId() : order.getFromLocationId());
                    newStock.setBatchNo(item.getBatchNo() != null ? item.getBatchNo() : LocalDate.now().toString());
                    newStock.setQuantity(item.getQuantity());
                    newStock.setReservedQuantity(BigDecimal.ZERO);
                    newStock.setUnitCost(item.getUnitCost());
                    newStock.setStatus(1);
                    newStock.setLastInboundTime(LocalDateTime.now());
                    stockItemMapper.insert(newStock);

                    // 刷新库存汇总
                    stockMapper.refreshSummary(item.getMaterialId());

                    // 记录库存流水（调拨取消回补）
                    InventoryTransaction tx = new InventoryTransaction();
                    tx.setMaterialId(item.getMaterialId());
                    tx.setMaterialCode(item.getMaterialCode());
                    tx.setMaterialName(item.getMaterialName());
                    tx.setWarehouseId(order.getFromWarehouseId());
                    tx.setLocationId(item.getFromLocationId() != null ? item.getFromLocationId() : order.getFromLocationId());
                    tx.setTransactionType("TRANSFER_OUT");
                    tx.setSourceType("INVENTORY_TRANSFER");
                    tx.setSourceId(transferId);
                    tx.setSourceNo(order.getTransferNo());
                    tx.setBatchNo(item.getBatchNo());
                    tx.setQuantity(item.getQuantity());
                    tx.setUnitCost(item.getUnitCost());
                    tx.setAmount(item.getAmount() != null ? item.getAmount() : null);
                    tx.setTransactionTime(LocalDateTime.now());
                    tx.setOperatorId(SecurityUtils.getUserId());
                    tx.setOperatorName(SecurityUtils.getUsername());
                    tx.setRemark("调拨取消回补源仓");
                    transactionMapper.insert(tx);
                }
            }
        }

        order.setOrderStatus(InventoryOrderStatusEnum.CANCELLED.getValue());
        order.setRemark(reason);
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    public List<TransferVO> getPendingApproval() {
        List<InventoryTransferOrder> orders = transferOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryTransferOrder>()
                        .eq(InventoryTransferOrder::getApproveStatus, InventoryOrderStatusEnum.PENDING.getValue())
                        .orderByAsc(InventoryTransferOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public List<TransferVO> getProcessing() {
        List<InventoryTransferOrder> orders = transferOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryTransferOrder>()
                        .in(InventoryTransferOrder::getOrderStatus, InventoryOrderStatusEnum.APPROVED.getValue(), InventoryOrderStatusEnum.OUT_CONFIRM.getValue())
                        .orderByAsc(InventoryTransferOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public boolean updateStatus(Long transferId, Integer status) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        order.setOrderStatus(status);
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    public IPage<InventoryTransferOrder> pageQuery(Map<String, Object> params) {
        String transferNo = (String) params.get("transferNo");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryTransferOrder> wrapper = new LambdaQueryWrapper<>();
        if (transferNo != null && !transferNo.isEmpty()) {
            wrapper.like(InventoryTransferOrder::getTransferNo, transferNo);
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryTransferOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryTransferOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryTransferOrder::getCreateTime).orderByDesc(InventoryTransferOrder::getTransferId);

        Page<InventoryTransferOrder> page = new Page<>(pageNum, pageSize);
        return transferOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Map<String, Object> params) {
        if (params != null && params.get("transferId") != null) {
            Long transferId = Long.valueOf(params.get("transferId").toString());
            TransferVO detail = getDetail(transferId);
            if (detail != null) {
                return Map.of("code", 200, "data", detail);
            }
        }
        return Map.of("code", 404, "message", "调拨单不存在");
    }

    private List<TransferVO> convertToVOList(List<InventoryTransferOrder> orders) {
        List<TransferVO> result = new ArrayList<>();
        for (InventoryTransferOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private TransferVO convertToVO(InventoryTransferOrder order){
        if (order == null) {
            return null;
        }
        TransferVO vo = new TransferVO();
        BeanUtils.copyProperties(order, vo);

        // DEV-694：回填仓库名与库位名（实体只有ID，前端需要名称显示）
        try {
            if (order.getFromWarehouseId() != null) {
                InventoryWarehouse fromWh = transferWarehouseMapper.selectById(order.getFromWarehouseId());
                if (fromWh != null) vo.setFromWarehouseName(fromWh.getWarehouseName());
            }
            if (order.getToWarehouseId() != null) {
                InventoryWarehouse toWh = transferWarehouseMapper.selectById(order.getToWarehouseId());
                if (toWh != null) vo.setToWarehouseName(toWh.getWarehouseName());
            }
            if (order.getFromLocationId() != null) {
                InventoryStorageLocation fromLoc = transferLocationMapper.selectById(order.getFromLocationId());
                if (fromLoc != null) vo.setFromLocationName(fromLoc.getLocationName());
            }
            if (order.getToLocationId() != null) {
                InventoryStorageLocation toLoc = transferLocationMapper.selectById(order.getToLocationId());
                if (toLoc != null) vo.setToLocationName(toLoc.getLocationName());
            }
        } catch (Exception e) {
            log.warn("回填调拨仓库/库位名称失败: transferId={}, err={}", order.getTransferId(), e.getMessage());
        }

        return vo;
    }

    /**
     * 将 InventoryTransferItem 列表转换为 TransferItemVO 列表
     */
    private static List<TransferItemVO> convertToItemVOList(List<InventoryTransferItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<TransferItemVO> result = new ArrayList<>();
        for (InventoryTransferItem item : items) {
            result.add(convertToItemVO(item));
        }
        return result;
    }

    /**
     * 将 InventoryTransferItem 转换为 TransferItemVO
     */
    private static TransferItemVO convertToItemVO(InventoryTransferItem item) {
        if (item == null) {
            return null;
        }
        TransferItemVO vo = new TransferItemVO();
        vo.setTransferItemId(item.getItemId());
        vo.setTransferId(item.getTransferId());
        vo.setMaterialId(item.getMaterialId());
        vo.setMaterialCode(item.getMaterialCode());
        vo.setMaterialName(item.getMaterialName());
        vo.setSpecification(item.getSpecification());
        vo.setUnit(item.getUnit());
        vo.setQuantity(item.getQuantity());
        vo.setUnitCost(item.getUnitCost());
        vo.setAmount(item.getAmount());
        vo.setBatchNo(item.getBatchNo());
        vo.setRemark(item.getRemark());
        return vo;
    }

}
