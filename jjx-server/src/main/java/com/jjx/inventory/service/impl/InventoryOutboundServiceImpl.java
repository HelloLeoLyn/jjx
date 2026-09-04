package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.domain.InventoryOutboundItem;
import com.jjx.inventory.domain.InventoryWarehouse;
import com.jjx.inventory.dto.vo.OutboundItemVO;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryStorageLocation;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.dto.query.OutboundQueryDTO;
import com.jjx.inventory.dto.vo.OutboundVO;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.enums.OutboundTypeEnum;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryOutboundItemMapper;
import com.jjx.inventory.mapper.InventoryOutboundOrderMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStorageLocationMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.mapper.InventoryWarehouseMapper;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.service.InventoryOutboundService;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jjx.common.exception.BusinessException;
import com.jjx.system.annotation.Event;

/**
 * 出库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryOutboundServiceImpl extends ServiceImpl<InventoryOutboundOrderMapper, InventoryOutboundOrder>
        implements InventoryOutboundService {

    private final InventoryOutboundOrderMapper outboundOrderMapper;
    private final InventoryOutboundItemMapper outboundItemMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final InventoryStorageLocationMapper storageLocationMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final InventoryWarehouseMapper outboundWarehouseMapper;
    private final InventoryMaterialMapper materialMapper;
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    private final com.jjx.product.mapper.EngineeringBomMapper productBomMapper;
    private final com.jjx.product.mapper.EngineeringBomItemMapper productBomItemMapper;
    private final com.jjx.sales.mapper.OrderMapper salesOrderMapper;
    private final InventoryAlertService alertService;
    private final com.jjx.sales.mapper.SalesOrderProductMapper salesOrderProductMapper;
    private final com.jjx.inventory.service.OrderStockReserveService orderStockReserveService;
    private final com.jjx.inventory.service.ProductStockService productStockService;

    @Override
    public IPage<OutboundVO> page(OutboundQueryDTO query) {
        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (query.getOutboundId() != null) wrapper.eq(InventoryOutboundOrder::getOutboundId, query.getOutboundId());
        if (query.getOutboundNo() != null && !query.getOutboundNo().isEmpty()) wrapper.like(InventoryOutboundOrder::getOutboundNo, query.getOutboundNo());
        if (query.getOutboundType() != null && !query.getOutboundType().isEmpty()) wrapper.eq(InventoryOutboundOrder::getOutboundType, query.getOutboundType());
        if (query.getWarehouseId() != null) wrapper.eq(InventoryOutboundOrder::getWarehouseId, query.getWarehouseId());
        if (query.getSourceType() != null && !query.getSourceType().isEmpty()) wrapper.eq(InventoryOutboundOrder::getSourceType, query.getSourceType());
        if (query.getSourceTypeNe() != null && !query.getSourceTypeNe().isEmpty()) wrapper.ne(InventoryOutboundOrder::getSourceType, query.getSourceTypeNe());
        if (query.getSourceNo() != null && !query.getSourceNo().isEmpty()) wrapper.like(InventoryOutboundOrder::getSourceNo, query.getSourceNo());
        if (query.getOrderStatus() != null && !query.getOrderStatus().isEmpty()) wrapper.eq(InventoryOutboundOrder::getOrderStatus, query.getOrderStatus());
        if (query.getApproveStatus() != null && !query.getApproveStatus().isEmpty()) wrapper.eq(InventoryOutboundOrder::getApproveStatus, query.getApproveStatus());
        if (query.getOutboundDateStart() != null) wrapper.ge(InventoryOutboundOrder::getOutboundDate, query.getOutboundDateStart());
        if (query.getOutboundDateEnd() != null) wrapper.le(InventoryOutboundOrder::getOutboundDate, query.getOutboundDateEnd());
        if (query.getCreateTimeStart() != null && !query.getCreateTimeStart().isEmpty()) wrapper.ge(InventoryOutboundOrder::getCreateTime, query.getCreateTimeStart());
        if (query.getCreateTimeEnd() != null && !query.getCreateTimeEnd().isEmpty()) wrapper.le(InventoryOutboundOrder::getCreateTime, query.getCreateTimeEnd());
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            boolean isAsc = "asc".equalsIgnoreCase(query.getOrderDirection());
            switch (query.getOrderBy()) {
                case "outboundNo": wrapper.orderBy(true, isAsc, InventoryOutboundOrder::getOutboundNo); break;
                case "outboundDate": wrapper.orderBy(true, isAsc, InventoryOutboundOrder::getOutboundDate); break;
                case "createTime": wrapper.orderBy(true, isAsc, InventoryOutboundOrder::getCreateTime); break;
                case "totalAmount": wrapper.orderBy(true, isAsc, InventoryOutboundOrder::getTotalAmount); break;
                default: wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime).orderByDesc(InventoryOutboundOrder::getOutboundId);
            }
        } else wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime).orderByDesc(InventoryOutboundOrder::getOutboundId);

        Page<InventoryOutboundOrder> orderPage = new Page<>(query.getCurrent(), query.getSize());
        IPage<InventoryOutboundOrder> orderResult = outboundOrderMapper.selectPage(orderPage, wrapper);
        Page<OutboundVO> voPage = new Page<>(query.getCurrent(), query.getSize());
        voPage.setTotal(orderResult.getTotal());
        voPage.setPages(orderResult.getPages());
        voPage.setRecords(convertToVOList(orderResult.getRecords()));
        return voPage;
    }

/**
     * 2026-08-18：获取默认启用仓库，查不到时抛明确业务异常（原静默跳过导致 warehouse_id 为 NULL）
     */
    private InventoryWarehouse getDefaultWarehouseOrThrow() {
        try {
            InventoryWarehouse wh = outboundWarehouseMapper.selectOne(
                    new LambdaQueryWrapper<InventoryWarehouse>()
                            .eq(InventoryWarehouse::getStatus, 1)
                            .orderByAsc(InventoryWarehouse::getWarehouseId)
                            .last("LIMIT 1"));
            if (wh == null) {
                throw new BusinessException("未配置启用状态的仓库，请先在【库存→仓库管理】中启用仓库");
            }
            return wh;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("获取默认仓库失败: {}", e.getMessage());
            throw new BusinessException("获取默认仓库失败，请检查仓库配置");
        }
    }

    @Override
    public OutboundVO getDetail(Long outboundId) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return null;
        }
        OutboundVO vo = convertToVO(order);
        List<InventoryOutboundItem> items = outboundItemMapper.selectByOutboundId(outboundId);
        if (items != null && !items.isEmpty()) {
            vo.setItems(convertToItemVOList(items, storageLocationMapper));
        }
        return vo;
    }

    @Override
    @Event(value = "inventory.outbound.created", bizId = "#params", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    
    public Long create(Map<String, Object> params) {
        log.info("创建出库单: {}", params);
        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundNo((String) params.getOrDefault("outboundNo", "OUT-" + System.currentTimeMillis()));
        order.setOutboundType((String) params.getOrDefault("outboundType", "sales"));
        order.setSourceType((String) params.get("sourceType"));
        if (params.get("sourceId") != null) order.setSourceId(Long.valueOf(params.get("sourceId").toString()));
        order.setSourceNo((String) params.get("sourceNo"));
        // 2026-08-18：手动出库 warehouseId 必填（原不校验 → NULL → SQL 裸错）
        if (params.get("warehouseId") == null) {
            throw new BusinessException("请选择出库仓库");
        }
        order.setWarehouseId(Long.valueOf(params.get("warehouseId").toString()));
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        outboundOrderMapper.insert(order);

        // 保存明细（DEV-695：原 create 不落 items，导致手动出库单无明细无法确认）
        Object itemsObj = params.get("items");
        if (itemsObj instanceof List<?> itemList && !itemList.isEmpty()) {
            List<InventoryOutboundItem> items = new ArrayList<>();
            int sort = 1;
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmt = BigDecimal.ZERO;
            for (Object obj : itemList) {
                if (!(obj instanceof Map<?, ?> m)) continue;
                InventoryOutboundItem item = new InventoryOutboundItem();
                item.setOutboundId(order.getOutboundId());
                if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
                item.setMaterialCode((String) m.get("materialCode"));
                item.setMaterialName((String) m.get("materialName"));
                item.setSpecification((String) m.get("specification"));
                item.setUnit((String) m.get("unit"));
                if (m.get("quantity") != null) item.setQuantity(new BigDecimal(m.get("quantity").toString()));
                if (m.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(m.get("unitPrice").toString()));
                BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                item.setAmount(qty.multiply(price));
                item.setBatchNo((String) m.get("batchNo"));
                if (m.get("locationId") != null) item.setLocationId(Long.valueOf(m.get("locationId").toString()));
                if (m.get("remark") != null) item.setRemark(m.get("remark").toString());
                item.setSortOrder(sort++);
                items.add(item);
                totalQty = totalQty.add(qty);
                totalAmt = totalAmt.add(item.getAmount());
            }
            if (!items.isEmpty()) {
                outboundItemMapper.batchInsert(items);
                order.setTotalQuantity(totalQty);
                order.setTotalAmount(totalAmt);
                outboundOrderMapper.updateById(order);
            }
        }
        return order.getOutboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Map<String, Object> params) {
        log.info("更新出库单: {}", params);
        Object idObj = params.get("outboundId");
        if (idObj == null) {
            throw new com.jjx.common.exception.BusinessException("outboundId 不能为空");
        }
        Long outboundId = Long.valueOf(idObj.toString());
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            throw new com.jjx.common.exception.BusinessException("出库单不存在: " + outboundId);
        }
        if (!InventoryOrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())) {
            throw new com.jjx.common.exception.BusinessException("仅待处理状态的出库单可编辑");
        }

        // 更新单头
        if (params.get("outboundType") != null) order.setOutboundType((String) params.get("outboundType"));
        if (params.get("sourceType") != null) order.setSourceType((String) params.get("sourceType"));
        if (params.get("sourceNo") != null) order.setSourceNo((String) params.get("sourceNo"));
        if (params.get("warehouseId") != null) order.setWarehouseId(Long.valueOf(params.get("warehouseId").toString()));
        if (params.get("remark") != null) order.setRemark((String) params.get("remark"));
        outboundOrderMapper.updateById(order);

        // 更新明细：先删旧明细再插新明细
        if (params.get("items") instanceof List<?> itemList) {
            outboundItemMapper.delete(new LambdaQueryWrapper<InventoryOutboundItem>()
                    .eq(InventoryOutboundItem::getOutboundId, outboundId));
            if (!itemList.isEmpty()) {
                List<InventoryOutboundItem> items = new ArrayList<>();
                int sort = 1;
                BigDecimal totalQty = BigDecimal.ZERO;
                BigDecimal totalAmt = BigDecimal.ZERO;
                for (Object obj : itemList) {
                    if (!(obj instanceof Map<?, ?> m)) continue;
                    InventoryOutboundItem item = new InventoryOutboundItem();
                    item.setOutboundId(outboundId);
                    if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
                    item.setMaterialCode((String) m.get("materialCode"));
                    item.setMaterialName((String) m.get("materialName"));
                    item.setSpecification((String) m.get("specification"));
                    item.setUnit((String) m.get("unit"));
                    if (m.get("quantity") != null) item.setQuantity(new BigDecimal(m.get("quantity").toString()));
                    if (m.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(m.get("unitPrice").toString()));
                    BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                    BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                    item.setAmount(qty.multiply(price));
                    item.setBatchNo((String) m.get("batchNo"));
                    if (m.get("locationId") != null) item.setLocationId(Long.valueOf(m.get("locationId").toString()));
                    if (m.get("remark") != null) item.setRemark(m.get("remark").toString());
                    item.setSortOrder(sort++);
                    items.add(item);
                    totalQty = totalQty.add(qty);
                    totalAmt = totalAmt.add(item.getAmount());
                }
                if (!items.isEmpty()) {
                    outboundItemMapper.batchInsert(items);
                    order.setTotalQuantity(totalQty);
                    order.setTotalAmount(totalAmt);
                    outboundOrderMapper.updateById(order);
                } else {
                    order.setTotalQuantity(BigDecimal.ZERO);
                    order.setTotalAmount(BigDecimal.ZERO);
                    outboundOrderMapper.updateById(order);
                }
            }
        }
        return true;
    }

    @Override
    @Event(value = "inventory.outbound.confirmed", bizId = "#outboundId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(Long outboundId, Long operatorId, String operatorName) {
        // DEV-651 方案A：行锁查询，锁住单据行直到事务提交，并发下第二个请求阻塞后状态校验失败，杜绝重复出入库
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!InventoryOrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())
                && !InventoryOrderStatusEnum.APPROVED.getValue().equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法确认: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        // 库存操作统一发生在 confirm（DEV-651：confirm=审批+完成 单路径，approve 不再动库存）
        // 直接执行库存扣减（不经过 approve，避免状态不匹配）
        List<InventoryOutboundItem> outItems = outboundItemMapper.selectByOutboundId(outboundId);
        // DEV-580：销售发货出库时，先同步释放该订单的成品预留（扣减前释放，FIFO才能扣到预留部分）
        if ("SALES".equals(order.getSourceType()) && order.getSourceId() != null) {
            for (InventoryOutboundItem item : outItems) {
                if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
                try {
                    orderStockReserveService.releaseForOutbound(order.getSourceId(), item.getMaterialId(), item.getQuantity());
                } catch (Exception e) {
                    log.warn("出库联动释放成品预留失败（不影响扣减）: {}", e.getMessage());
                }
            }
        }
        for (InventoryOutboundItem item : outItems) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal remaining = item.getQuantity();
            // DEV-693：明细指定了库位 → 先按该库位 FIFO 扣减，不足再从全局 FIFO 补齐
            if (item.getLocationId() != null) {
                List<InventoryStockItem> locItems = stockItemMapper.selectFIFOAvailableByLocation(item.getMaterialId(), item.getLocationId());
                for (InventoryStockItem si : locItems) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal deductQty = remaining.min(si.getQuantity().subtract(si.getReservedQuantity()));
                    if (deductQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                    stockItemMapper.deductStock(si.getItemId(), deductQty);
                    remaining = remaining.subtract(deductQty);
                }
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                List<InventoryStockItem> fifoItems = stockItemMapper.selectFIFOAvailable(item.getMaterialId());
                for (InventoryStockItem si : fifoItems) {
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal deductQty = remaining.min(si.getQuantity().subtract(si.getReservedQuantity()));
                    if (deductQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                    stockItemMapper.deductStock(si.getItemId(), deductQty);
                    remaining = remaining.subtract(deductQty);
                }
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("物料[" + item.getMaterialCode() + "]库存不足，缺少: " + remaining);
            }
            stockMapper.refreshSummary(item.getMaterialId());
            // DEV-20260810-096：销售出库=产品出库（产品维度独立记账）
            // 产品库存与物料库存各自独立记账；仅销售出库同步扣产品库存（物料是成品F且有专用产品时）
            if ("SALES".equals(order.getSourceType())) {
                try {
                    com.jjx.inventory.domain.InventoryMaterial mat = materialMapper.selectById(item.getMaterialId());
                    if (mat != null && mat.getProductId() != null) {
                        productStockService.decrease(mat.getProductId(), item.getQuantity());
                        log.info("销售出库同步产品库存-: productId={}, qty={}", mat.getProductId(), item.getQuantity());
                    }
                } catch (Exception e) {
                    log.warn("销售出库同步产品库存失败（不影响出库主流程）: {}", e.getMessage());
                }
            }
            // 获取扣减前的库存汇总
            java.math.BigDecimal beforeQty = java.math.BigDecimal.ZERO;
            InventoryStock currentStock = stockMapper.selectByMaterialId(item.getMaterialId());
            if (currentStock != null && currentStock.getTotalQuantity() != null) {
                beforeQty = currentStock.getTotalQuantity().add(item.getQuantity());
            }
            InventoryTransaction tx = new InventoryTransaction();
            tx.setMaterialId(item.getMaterialId());
            tx.setMaterialCode(item.getMaterialCode());
            tx.setMaterialName(item.getMaterialName());
            tx.setWarehouseId(order.getWarehouseId());
            tx.setLocationId(item.getLocationId());
            tx.setTransactionType("OUTBOUND");
            tx.setSourceType(order.getSourceType());
            tx.setSourceId(outboundId);
            tx.setSourceNo(order.getOutboundNo());
            tx.setBatchNo(item.getBatchNo());
            tx.setQuantity(item.getQuantity().negate());
            tx.setBeforeQuantity(beforeQty);
            tx.setAfterQuantity(beforeQty.subtract(item.getQuantity()));
            tx.setUnitCost(item.getUnitPrice());
            tx.setAmount(item.getAmount());
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(operatorId != null ? operatorId : SecurityUtils.getUserId());
            tx.setOperatorName(operatorName != null ? operatorName : SecurityUtils.getUsername());
            tx.setRemark("出库确认完成");
            transactionMapper.insert(tx);
        }
        // 安全库存检查（移到循环外只调一次，原实现在循环内全量重复执行）
        try {
            alertService.checkSafeStockAlert();
        } catch (Exception e) {
            log.warn("安全库存检查失败: {}", e.getMessage());
        }
        order.setOrderStatus(InventoryOrderStatusEnum.COMPLETED.getValue());
        boolean updated = outboundOrderMapper.updateById(order) > 0;

        // 生产领料单确认发料后，同步更新工单领料状态（2026-08-18 多次领料修正：
        // 还有未完成发料的领料单 → 保持领料中(1)；全部确认发料 → 已领料(2)，不再确认一张就置 2）
        try {
            if ("work_order".equals(order.getSourceType()) && order.getSourceId() != null) {
                com.jjx.production.domain.entity.ProductionOrder prodOrder =
                        productionOrderMapper.selectById(order.getSourceId());
                if (prodOrder != null && prodOrder.getMaterialStatus() != null
                        && prodOrder.getMaterialStatus() < 2) {
                    Long remaining = outboundOrderMapper.selectCount(
                            new LambdaQueryWrapper<InventoryOutboundOrder>()
                                    .eq(InventoryOutboundOrder::getSourceType, "work_order")
                                    .eq(InventoryOutboundOrder::getSourceId, order.getSourceId())
                                    .in(InventoryOutboundOrder::getOrderStatus,
                                            InventoryOrderStatusEnum.PENDING.getValue(),
                                            InventoryOrderStatusEnum.APPROVED.getValue(),
                                            InventoryOrderStatusEnum.CONFIRMED.getValue()));
                    if (remaining != null && remaining > 0) {
                        prodOrder.setMaterialStatus(1); // 仍有待发料领料单：领料中
                        log.info("工单{}仍有{}张领料单待发料，状态保持领料中", order.getSourceId(), remaining);
                    } else {
                        prodOrder.setMaterialStatus(2); // 全部发完：已领料
                    }
                    productionOrderMapper.updateById(prodOrder);
                }
            }
        } catch (Exception e) {
            log.warn("确认发料后更新工单领料状态失败: {}", e.getMessage());
        }

        // 058定稿：工单材料成本自动核算——确认发料后，按本次领料金额（数量×批次实际单价）累加到工单 materialCost
        try {
            if ("work_order".equals(order.getSourceType()) && order.getSourceId() != null) {
                com.jjx.production.domain.entity.ProductionOrder prodOrder =
                        productionOrderMapper.selectById(order.getSourceId());
                if (prodOrder != null) {
                    java.math.BigDecimal pickCost = java.math.BigDecimal.ZERO;
                    for (InventoryOutboundItem item : outItems) {
                        if (item.getQuantity() == null) continue;
                        // 取物料当前成本（标准单价），扣减时若批次带 unit_cost 则用批次价
                        java.math.BigDecimal unitCost = item.getUnitPrice() != null
                                ? item.getUnitPrice() : java.math.BigDecimal.ZERO;
                        if (unitCost.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                            try {
                                com.jjx.inventory.domain.InventoryMaterial mat = materialMapper.selectById(item.getMaterialId());
                                if (mat != null && mat.getStandardPrice() != null) {
                                    unitCost = mat.getStandardPrice();
                                }
                            } catch (Exception ex) {
                                log.warn("查询物料标准单价失败: {}", ex.getMessage());
                            }
                        }
                        pickCost = pickCost.add(item.getQuantity().multiply(unitCost));
                    }
                    if (pickCost.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        java.math.BigDecimal totalCost = prodOrder.getMaterialCost() != null
                                ? prodOrder.getMaterialCost() : java.math.BigDecimal.ZERO;
                        prodOrder.setMaterialCost(totalCost.add(pickCost));
                        productionOrderMapper.updateById(prodOrder);
                        log.info("工单{}材料成本累加{}（本次领料），累计{}",
                                order.getSourceId(), pickCost, prodOrder.getMaterialCost());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("工单材料成本核算失败（不影响出库）: {}", e.getMessage());
        }

        // 073定稿：销售发货出库确认后，回写订单 shipped_quantity（Σ发货量，不超订单量）
        try {
            if ("SALES".equals(order.getSourceType()) && order.getSourceId() != null) {
                com.jjx.sales.domain.entity.SalesOrder salesOrder = salesOrderMapper.selectById(order.getSourceId());
                if (salesOrder != null) {
                    java.math.BigDecimal shipped = java.math.BigDecimal.ZERO;
                    for (InventoryOutboundItem item : outItems) {
                        if (item.getQuantity() != null) {
                            shipped = shipped.add(item.getQuantity());
                        }
                    }
                    int shippedInt = shipped.intValue();
                    if (salesOrder.getShippedQuantity() == null) {
                        salesOrder.setShippedQuantity(0);
                    }
                    salesOrder.setShippedQuantity(salesOrder.getShippedQuantity() + shippedInt);
                    salesOrderMapper.updateById(salesOrder);
                    log.info("销售出库确认回写订单 shipped_quantity: orderId={}, 本次+{}，累计={}",
                            order.getSourceId(), shippedInt, salesOrder.getShippedQuantity());
                }
            }
        } catch (Exception e) {
            log.warn("销售出库回写订单 shipped_quantity 失败（不影响出库）: {}", e.getMessage());
        }

        return updated;
    }

    @Override
    @Event(value = "inventory.outbound.cancelled", bizId = "#outboundId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long outboundId, String reason) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (InventoryOrderStatusEnum.COMPLETED.getValue().equals(order.getOrderStatus())) {
            log.error("已完成的出库单无法取消: outboundId={}", outboundId);
            return false;
        }

        order.setOrderStatus(InventoryOrderStatusEnum.CANCELLED.getValue());
        order.setRemark(reason);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.outbound.submitted", bizId = "#outboundId", bizType = "'inventory'")
    public boolean submitApprove(Long outboundId) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        // DEV-651：只有草稿/已驳回/已取消状态的单才能提交审批，防止把已完成/进行中的单打回待审批
        Integer status = order.getOrderStatus();
        if (!InventoryOrderStatusEnum.DRAFT.getValue().equals(status)
                && !InventoryOrderStatusEnum.REJECTED.getValue().equals(status)
                && !InventoryOrderStatusEnum.CANCELLED.getValue().equals(status)) {
            log.error("出库单状态不允许提交审批: outboundId={}, status={}", outboundId, status);
            return false;
        }

        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Event(value = "inventory.outbound.approved", bizId = "#outboundId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(Long outboundId, Long approverId, String approverName, String remark) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!InventoryOrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法审批: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        // DEV-651：审批只做状态流转，库存扣减统一由 confirm 执行（confirm=审批+完成 单路径）
        // 原实现在这里扣库存，导致：①与 confirm 重复维护扣减逻辑；②approve 后无出口到 COMPLETED，单子卡死
        order.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());
        if (remark != null) {
            order.setApproveRemark(remark);
        }
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.outbound.rejected", bizId = "#outboundId", bizType = "'inventory'")
    public boolean reject(Long outboundId, Long approverId, String approverName, String remark) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!InventoryOrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法驳回: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(InventoryOrderStatusEnum.REJECTED.getValue());
        order.setRemark(remark);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Event(value = "inventory.outbound.created_from_production", bizId = "#workOrderId", bizType = "'inventory'")
    // REQUIRES_NEW（2026-08-11）：自动领料独立事务，失败只回滚自身，不污染工单开工主事务
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long createFromProduction(Long workOrderId) {
        return createFromProduction(workOrderId, null);
    }

    /**
     * 2026-08-18：领料预览（BOM展开+可用量+替代料），供生成前确认弹窗展示
     *
     * @return 领料方案行：主料行 + 替代料行
     */
    @Override
    public java.util.List<java.util.Map<String, Object>> previewPick(Long workOrderId) {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        // 1. 查工单
        com.jjx.production.domain.entity.ProductionOrder prodOrder = productionOrderMapper.selectById(workOrderId);
        if (prodOrder == null) {
            throw new BusinessException("生产工单不存在: " + workOrderId);
        }
        // 2. 查生效BOM
        com.jjx.engineering.domain.entity.EngineeringBom bom = productBomMapper.selectOne(
                new LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom>()
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getProductId, prodOrder.getProductId())
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getIsCurrent, 1)
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getApproveStatus, 3)
                        .orderByDesc(com.jjx.engineering.domain.entity.EngineeringBom::getCreateTime)
                        .last("LIMIT 1"));
        if (bom == null) {
            throw new BusinessException("工单产品[" + prodOrder.getProductCode() + "]无已审批的当前BOM，无法领料");
        }
        // 3. 查BOM明细
        List<com.jjx.engineering.domain.entity.EngineeringBomItem> bomItems = productBomItemMapper.selectList(
                new LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBomItem>()
                        .eq(com.jjx.engineering.domain.entity.EngineeringBomItem::getBomId, bom.getBomId()));
        if (bomItems.isEmpty()) {
            throw new BusinessException("BOM[" + bom.getBomCode() + "]无明细，无法领料");
        }
        // 4. 可用量预计算
        java.util.Map<Long, BigDecimal> availableMap = new java.util.HashMap<>();
        for (com.jjx.engineering.domain.entity.EngineeringBomItem bomItem : bomItems) {
            if (!"buy".equals(bomItem.getSourceType())) continue;
            BigDecimal available = BigDecimal.ZERO;
            try {
                List<InventoryStockItem> fifoItems = stockItemMapper.selectFIFOAvailable(bomItem.getMaterialId());
                for (InventoryStockItem si : fifoItems) {
                    available = available.add(si.getQuantity().subtract(si.getReservedQuantity()));
                }
            } catch (Exception e) {
                log.warn("领料预检查询库存失败: materialId={}, err={}", bomItem.getMaterialId(), e.getMessage());
            }
            availableMap.put(bomItem.getMaterialId(), available);
        }
        // 5. 逐物料生成预览行（主料 + 替代料）
        for (com.jjx.engineering.domain.entity.EngineeringBomItem bomItem : bomItems) {
            if (!"buy".equals(bomItem.getSourceType())) continue;
            BigDecimal baseQty = bomItem.getBaseQty() != null && bomItem.getBaseQty().compareTo(BigDecimal.ZERO) > 0
                    ? bomItem.getBaseQty() : BigDecimal.ONE;
            BigDecimal qtyNeeded = bomItem.getQuantity()
                    .multiply(prodOrder.getPlannedQuantity())
                    .divide(baseQty, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(bomItem.getLossRate() != null
                            ? BigDecimal.valueOf(bomItem.getLossRate()).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP)
                            : BigDecimal.ZERO))
                    .setScale(0, java.math.RoundingMode.UP);
            BigDecimal available = availableMap.getOrDefault(bomItem.getMaterialId(), BigDecimal.ZERO);
            BigDecimal qtyPick = qtyNeeded.min(available);
            boolean insufficient = qtyPick.compareTo(qtyNeeded) < 0;
            // 主料行
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("materialId", bomItem.getMaterialId());
            row.put("materialCode", bomItem.getMaterialCode());
            row.put("materialName", bomItem.getMaterialName());
            row.put("specification", bomItem.getSpecification() != null ? bomItem.getSpecification() : "");
            row.put("unit", bomItem.getUnit() != null ? bomItem.getUnit() : "PCS");
            row.put("qtyNeeded", qtyNeeded);
            row.put("available", available);
            row.put("qtyPick", qtyPick);
            row.put("substitute", false);
            row.put("substituteOf", "");
            row.put("insufficient", insufficient);
            rows.add(row);
            // 首选不足 → 替代料行
            if (insufficient && bomItem.getSubstituteJson() != null && !bomItem.getSubstituteJson().isEmpty()
                    && !"[]".equals(bomItem.getSubstituteJson())) {
                try {
                    java.util.List<java.util.Map<String, Object>> subs = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(bomItem.getSubstituteJson(),
                                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                    BigDecimal shortage = qtyNeeded.subtract(qtyPick);
                    for (java.util.Map<String, Object> sub : subs) {
                        Object subId = sub.get("materialId");
                        if (subId == null) continue;
                        Long subMaterialId = Long.valueOf(subId.toString());
                        BigDecimal ratio = sub.get("ratio") != null ? new BigDecimal(sub.get("ratio").toString()) : BigDecimal.ONE;
                        BigDecimal subNeed = shortage.multiply(ratio).setScale(0, java.math.RoundingMode.UP);
                        BigDecimal subAvail = BigDecimal.ZERO;
                        try {
                            List<InventoryStockItem> subFifo = stockItemMapper.selectFIFOAvailable(subMaterialId);
                            for (InventoryStockItem si : subFifo) {
                                subAvail = subAvail.add(si.getQuantity().subtract(si.getReservedQuantity()));
                            }
                        } catch (Exception e) {
                            log.warn("替代料库存查询失败: materialId={}", subMaterialId);
                        }
                        if (subAvail.compareTo(BigDecimal.ZERO) > 0) {
                            com.jjx.inventory.domain.InventoryMaterial subMat = materialMapper.selectById(subMaterialId);
                            java.util.Map<String, Object> subRow = new java.util.LinkedHashMap<>();
                            subRow.put("materialId", subMaterialId);
                            subRow.put("materialCode", subMat != null ? subMat.getMaterialCode() : String.valueOf(subMaterialId));
                            subRow.put("materialName", subMat != null ? subMat.getMaterialName() : (sub.get("materialName") != null ? sub.get("materialName") : ""));
                            subRow.put("specification", subMat != null && subMat.getSpecification() != null ? subMat.getSpecification() : "");
                            subRow.put("unit", subMat != null && subMat.getUnit() != null ? subMat.getUnit() : "PCS");
                            subRow.put("qtyNeeded", subNeed);
                            subRow.put("available", subAvail);
                            subRow.put("qtyPick", subNeed.min(subAvail));
                            subRow.put("substitute", true);
                            subRow.put("substituteOf", bomItem.getMaterialCode());
                            subRow.put("insufficient", subNeed.compareTo(subAvail) > 0);
                            rows.add(subRow);
                            break; // 只取第一个可用替代料
                        }
                    }
                } catch (Exception e) {
                    log.warn("替代料预览解析失败: {}", e.getMessage());
                }
            }
        }
        return rows;
    }

    /**
     * 2026-08-18：支持领料预览调整（adjustedItems：materialId→quantity，仅可少领，替代料自动按新短缺补）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long createFromProduction(Long workOrderId, java.util.List<java.util.Map<String, Object>> adjustedItems) {
        log.info("从生产工单创建出库单: workOrderId={}", workOrderId);

        // 1. 查询生产工单
        com.jjx.production.domain.entity.ProductionOrder prodOrder =
                productionOrderMapper.selectById(workOrderId);
        if (prodOrder == null) {
            throw new BusinessException("生产工单不存在: " + workOrderId);
        }

        // 2. 查询当前生效BOM
        LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom> bomWrapper =
                new LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom>()
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getProductId, prodOrder.getProductId())
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getIsCurrent, 1)
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getApproveStatus, 3)
                        .orderByDesc(com.jjx.engineering.domain.entity.EngineeringBom::getCreateTime)
                        .last("LIMIT 1");
        com.jjx.engineering.domain.entity.EngineeringBom bom = productBomMapper.selectOne(bomWrapper);
        if (bom == null) {
            log.warn("生产工单{}的产品{}无生效BOM，跳过自动领料", workOrderId, prodOrder.getProductCode());
            return null;
        }

        // 3. 查询BOM明细
        LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBomItem> itemWrapper =
                new LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBomItem>()
                        .eq(com.jjx.engineering.domain.entity.EngineeringBomItem::getBomId, bom.getBomId());
        List<com.jjx.engineering.domain.entity.EngineeringBomItem> bomItems = productBomItemMapper.selectList(itemWrapper);
        if (bomItems.isEmpty()) {
            log.warn("BOM{}无明细，无法自动领料", bom.getBomCode());
            return null;
        }

        // 4. 创建出库单
        String outboundNo = "PICK-" + prodOrder.getOrderNo();
        // 检查是否已生成
        LambdaQueryWrapper<InventoryOutboundOrder> existCheck = new LambdaQueryWrapper<InventoryOutboundOrder>()
                .eq(InventoryOutboundOrder::getOutboundNo, outboundNo);
        if (outboundOrderMapper.selectCount(existCheck) > 0) {
            log.warn("生产工单{}的领料单已存在", workOrderId);
            return null;
        }

        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundNo(outboundNo);
        order.setOutboundType(OutboundTypeEnum.PRODUCTION.getCode());
        order.setSourceType("work_order");
        order.setSourceId(workOrderId);
        order.setSourceNo(prodOrder.getOrderNo());
        order.setTraceId(prodOrder.getTraceId()); // 链路追踪（DEV-568）：工单→领料出库单继承
        order.setOutboundDate(LocalDate.now());
        // 默认取第一个启用仓库（工单无仓库字段）
        // 2026-08-18：查不到启用仓库时明确报错（原静默跳过导致 warehouse_id NULL → SQL 裸错）
        order.setWarehouseId(getDefaultWarehouseOrThrow().getWarehouseId());
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        outboundOrderMapper.insert(order);

        // 5. 创建出库单明细
        int sort = 1;
        BigDecimal totalQty = BigDecimal.ZERO;
        // 045定稿：领料预检按本次领料数量查可用量，不足允许部分领料开工（不再全量拦截）
        // 首领单按可用量部分领，剩余靠追加领料(033 createProductionPick)补
        java.util.Map<Long, BigDecimal> availableMap = new java.util.HashMap<>();
        for (com.jjx.engineering.domain.entity.EngineeringBomItem bomItem : bomItems) {
            if (!"buy".equals(bomItem.getSourceType())) continue;
            BigDecimal available = BigDecimal.ZERO;
            try {
                List<InventoryStockItem> fifoItems = stockItemMapper.selectFIFOAvailable(bomItem.getMaterialId());
                for (InventoryStockItem si : fifoItems) {
                    available = available.add(si.getQuantity().subtract(si.getReservedQuantity()));
                }
            } catch (Exception e) {
                log.warn("领料预检查询库存失败: materialId={}, err={}", bomItem.getMaterialId(), e.getMessage());
            }
            availableMap.put(bomItem.getMaterialId(), available);
        }

        for (com.jjx.engineering.domain.entity.EngineeringBomItem bomItem : bomItems) {
            if (!"buy".equals(bomItem.getSourceType())) continue;

            BigDecimal baseQty = bomItem.getBaseQty() != null && bomItem.getBaseQty().compareTo(BigDecimal.ZERO) > 0
                    ? bomItem.getBaseQty() : BigDecimal.ONE;
            BigDecimal qtyNeeded = bomItem.getQuantity()
                    .multiply(prodOrder.getPlannedQuantity())
                    .divide(baseQty, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(
                            bomItem.getLossRate() != null ? BigDecimal.valueOf(bomItem.getLossRate()).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO
                    ))
                    .setScale(0, java.math.RoundingMode.UP);

            // 034/048定稿：首选料可用不足时，按 substitute_json 优先级尝试替代料（模数换算：替代需求量=原需求量×ratio）
            BigDecimal available = availableMap.getOrDefault(bomItem.getMaterialId(), BigDecimal.ZERO);
            Long pickMaterialId = bomItem.getMaterialId();
            String pickCode = bomItem.getMaterialCode();
            String pickName = bomItem.getMaterialName();
            String pickSpec = bomItem.getSpecification();
            String pickUnit = bomItem.getUnit();
            BigDecimal qtyPick = qtyNeeded.min(available);
            // 2026-08-18：预览调整（仅可少领：0 ≤ 调整 ≤ min(需求,可用)），替代料按新短缺自动补
            if (adjustedItems != null) {
                for (java.util.Map<String, Object> adj : adjustedItems) {
                    if (adj.get("materialId") == null || adj.get("quantity") == null) continue;
                    if (bomItem.getMaterialId().longValue() == ((Number) adj.get("materialId")).longValue()) {
                        BigDecimal adjQty = new BigDecimal(String.valueOf(adj.get("quantity")));
                        qtyPick = adjQty.min(qtyPick).max(BigDecimal.ZERO);
                        break;
                    }
                }
            }
            if (qtyPick.compareTo(qtyNeeded) < 0) {
                // 首选料不足 → 尝试替代料（034：A料缺货用B料替代，按模数换算）
                BigDecimal shortage = qtyNeeded.subtract(qtyPick);
                try {
                    if (bomItem.getSubstituteJson() != null && !bomItem.getSubstituteJson().isEmpty()
                            && !"[]".equals(bomItem.getSubstituteJson())) {
                        java.util.List<java.util.Map<String, Object>> subs =
                                com.fasterxml.jackson.databind.ObjectMapper.class.cast(
                                        new com.fasterxml.jackson.databind.ObjectMapper())
                                        .readValue(bomItem.getSubstituteJson(),
                                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                        if (subs != null) {
                            for (java.util.Map<String, Object> sub : subs) {
                                Object subId = sub.get("materialId");
                                if (subId == null) continue;
                                Long subMaterialId = Long.valueOf(subId.toString());
                                BigDecimal ratio = sub.get("ratio") != null
                                        ? new BigDecimal(sub.get("ratio").toString()) : BigDecimal.ONE;
                                BigDecimal subNeed = shortage.multiply(ratio).setScale(0, java.math.RoundingMode.UP);
                                BigDecimal subAvail = BigDecimal.ZERO;
                                try {
                                    List<InventoryStockItem> subFifo = stockItemMapper.selectFIFOAvailable(subMaterialId);
                                    for (InventoryStockItem si : subFifo) {
                                        subAvail = subAvail.add(si.getQuantity().subtract(si.getReservedQuantity()));
                                    }
                                } catch (Exception e) {
                                    log.warn("替代料库存查询失败: materialId={}", subMaterialId);
                                }
                                if (subAvail.compareTo(BigDecimal.ZERO) > 0) {
                                    BigDecimal subPick = subNeed.min(subAvail);
                                    com.jjx.inventory.domain.InventoryMaterial subMat = materialMapper.selectById(subMaterialId);
                                    // 替代料明细（替换留痕：remark 标原物料）
                                    InventoryOutboundItem subItem = new InventoryOutboundItem();
                                    subItem.setOutboundId(order.getOutboundId());
                                    subItem.setMaterialId(subMaterialId);
                                    subItem.setMaterialCode(subMat != null ? subMat.getMaterialCode() : String.valueOf(subMaterialId));
                                    subItem.setMaterialName(subMat != null ? subMat.getMaterialName() : (String) sub.get("materialName"));
                                    subItem.setQuantity(subPick);
                                    subItem.setSortOrder(sort++);
                                    subItem.setRemark("替代料替换[" + pickCode + "]（模数" + ratio + "）");
                                    try {
                                        List<InventoryStockItem> subFifo2 = stockItemMapper.selectFIFOAvailable(subMaterialId);
                                        if (!subFifo2.isEmpty() && subFifo2.get(0).getLocationId() != null) {
                                            subItem.setLocationId(subFifo2.get(0).getLocationId());
                                        }
                                    } catch (Exception e) {
                                        log.warn("替代料推荐库位失败: {}", e.getMessage());
                                    }
                                    outboundItemMapper.insert(subItem);
                                    totalQty = totalQty.add(subPick);
                                    log.info("034替代料替换：{}缺{}，用替代料{}领{}（模数{}）",
                                            pickCode, shortage, subItem.getMaterialCode(), subPick, ratio);
                                    break; // 替代成功即停
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("替代料解析/领料失败（不影响主料领料）: {}", e.getMessage());
                }
            }
            // 首选料仍领可用部分（可为0：全部由替代料覆盖则跳过）
            if (qtyPick.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (qtyPick.compareTo(qtyNeeded) < 0) {
                log.warn("物料[{}]库存不足（需{}可用{}），本次按可用量领{}，剩余可追加领料",
                        pickCode, qtyNeeded, available, qtyPick);
            }

            InventoryOutboundItem outItem = new InventoryOutboundItem();
            outItem.setOutboundId(order.getOutboundId());
            outItem.setMaterialId(pickMaterialId);
            outItem.setMaterialCode(pickCode);
            outItem.setMaterialName(pickName);
            outItem.setSpecification(pickSpec);
            outItem.setUnit(pickUnit);
            outItem.setQuantity(qtyPick);
            outItem.setSortOrder(sort++);
            // DEV-693：预填 FIFO 推荐库位（最早批次所在库位），供仓管按位拣货；确认时优先从该库位扣减
            try {
                List<InventoryStockItem> fifoItems = stockItemMapper.selectFIFOAvailable(bomItem.getMaterialId());
                if (!fifoItems.isEmpty() && fifoItems.get(0).getLocationId() != null) {
                    outItem.setLocationId(fifoItems.get(0).getLocationId());
                }
            } catch (Exception e) {
                log.warn("领料单推荐库位失败(跳过): materialId={}, err={}", bomItem.getMaterialId(), e.getMessage());
            }
            totalQty = totalQty.add(qtyPick);
            outboundItemMapper.insert(outItem);
        }

        // 6. 2026-08-18：明细为空（所有物料可用量 0，库存不足）→ 不建空壳单，回滚并明确提示
        if (sort == 1) {
            throw new BusinessException("库存不足，无法自动领料（请先入库或到生产领料页手工领料）");
        }

        // 7. 汇总并更新工单领料状态（待发料）
        order.setTotalQuantity(totalQty);
        outboundOrderMapper.updateById(order);
        prodOrder.setMaterialStatus(1);
        productionOrderMapper.updateById(prodOrder);

        log.info("生产领料单已生成(待发料): workOrderId={}, outboundId={}", workOrderId, order.getOutboundId());
        return order.getOutboundId();
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> getPickRemaining(Long workOrderId) {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        com.jjx.production.domain.entity.ProductionOrder prodOrder = productionOrderMapper.selectById(workOrderId);
        if (prodOrder == null) {
            return result;
        }
        // BOM 需求量
        LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom> bomWrapper =
                new LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom>()
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getProductId, prodOrder.getProductId())
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getIsCurrent, 1)
                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getApproveStatus, 3)
                        .orderByDesc(com.jjx.engineering.domain.entity.EngineeringBom::getCreateTime)
                        .last("LIMIT 1");
        com.jjx.engineering.domain.entity.EngineeringBom bom = productBomMapper.selectOne(bomWrapper);
        if (bom == null) {
            return result;
        }
        List<com.jjx.engineering.domain.entity.EngineeringBomItem> bomItems = productBomItemMapper.selectList(
                new LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBomItem>()
                        .eq(com.jjx.engineering.domain.entity.EngineeringBomItem::getBomId, bom.getBomId()));
        // 已领料量（该工单所有领料出库单明细合计）
        java.util.Map<Long, BigDecimal> pickedMap = new java.util.HashMap<>();
        try {
            List<InventoryOutboundOrder> pickOrders = outboundOrderMapper.selectList(
                    new LambdaQueryWrapper<InventoryOutboundOrder>()
                            .eq(InventoryOutboundOrder::getSourceType, "work_order")
                            .eq(InventoryOutboundOrder::getSourceId, workOrderId));
            for (InventoryOutboundOrder po : pickOrders) {
                List<InventoryOutboundItem> items = outboundItemMapper.selectByOutboundId(po.getOutboundId());
                for (InventoryOutboundItem it : items) {
                    if (it.getQuantity() != null) {
                        pickedMap.merge(it.getMaterialId(), it.getQuantity(), BigDecimal::add);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询已领料量失败: {}", e.getMessage());
        }
        for (com.jjx.engineering.domain.entity.EngineeringBomItem bomItem : bomItems) {
            if (!"buy".equals(bomItem.getSourceType())) continue;
            BigDecimal baseQty = bomItem.getBaseQty() != null && bomItem.getBaseQty().compareTo(BigDecimal.ZERO) > 0
                    ? bomItem.getBaseQty() : BigDecimal.ONE;
            BigDecimal demand = bomItem.getQuantity()
                    .multiply(prodOrder.getPlannedQuantity())
                    .divide(baseQty, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.ONE.add(
                            bomItem.getLossRate() != null ? BigDecimal.valueOf(bomItem.getLossRate()).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO
                    ))
                    .setScale(0, java.math.RoundingMode.UP);
            BigDecimal picked = pickedMap.getOrDefault(bomItem.getMaterialId(), BigDecimal.ZERO);
            BigDecimal remaining = demand.subtract(picked);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("materialId", bomItem.getMaterialId());
            row.put("materialCode", bomItem.getMaterialCode());
            row.put("materialName", bomItem.getMaterialName());
            row.put("demand", demand);
            row.put("picked", picked);
            row.put("remaining", remaining);
            result.add(row);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProductionPick(Long workOrderId, java.util.List<java.util.Map<String, Object>> items) {
        log.info("追加领料: workOrderId={}, items={}", workOrderId, items);
        com.jjx.production.domain.entity.ProductionOrder prodOrder = productionOrderMapper.selectById(workOrderId);
        if (prodOrder == null) {
            throw new BusinessException("生产工单不存在: " + workOrderId);
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("本次领料明细不能为空");
        }
        // 剩余量校验：Σ本次领料 ≤ 剩余需求量（033定稿：可改小不可改大）
        java.util.Map<Long, BigDecimal> remainingMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> rem : getPickRemaining(workOrderId)) {
            remainingMap.put(((Number) rem.get("materialId")).longValue(), (BigDecimal) rem.get("remaining"));
        }
        java.util.List<java.util.Map<String, Object>> validItems = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> item : items) {
            Long materialId = ((Number) item.get("materialId")).longValue();
            BigDecimal qty = new BigDecimal(String.valueOf(item.get("quantity")));
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal remaining = remainingMap.getOrDefault(materialId, BigDecimal.ZERO);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("物料[" + item.get("materialCode") + "]剩余可领量为0，不能追加领料");
            }
            if (qty.compareTo(remaining) > 0) {
                throw new BusinessException("物料[" + item.get("materialCode") + "]本次领料" + qty + "超过剩余可领量" + remaining);
            }
            validItems.add(item);
        }
        if (validItems.isEmpty()) {
            throw new BusinessException("没有可领的物料明细");
        }
        // 出库单号 PICK-{工单号}-{序号}
        long seq = outboundOrderMapper.selectCount(
                new LambdaQueryWrapper<InventoryOutboundOrder>()
                        .eq(InventoryOutboundOrder::getSourceType, "work_order")
                        .eq(InventoryOutboundOrder::getSourceId, workOrderId)) + 1;
        String outboundNo = "PICK-" + prodOrder.getOrderNo() + "-" + seq;
        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundNo(outboundNo);
        order.setOutboundType(OutboundTypeEnum.PRODUCTION.getCode());
        order.setSourceType("work_order");
        order.setSourceId(workOrderId);
        order.setSourceNo(prodOrder.getOrderNo());
        order.setTraceId(prodOrder.getTraceId());
        order.setOutboundDate(LocalDate.now());
        // 2026-08-18：查不到启用仓库时明确报错（原静默跳过导致 warehouse_id NULL → SQL 裸错）
        order.setWarehouseId(getDefaultWarehouseOrThrow().getWarehouseId());
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        outboundOrderMapper.insert(order);

        int sort = 1;
        BigDecimal totalQty = BigDecimal.ZERO;
        for (java.util.Map<String, Object> item : validItems) {
            Long materialId = ((Number) item.get("materialId")).longValue();
            BigDecimal qty = new BigDecimal(String.valueOf(item.get("quantity")));
            InventoryOutboundItem outItem = new InventoryOutboundItem();
            outItem.setOutboundId(order.getOutboundId());
            outItem.setMaterialId(materialId);
            outItem.setMaterialCode((String) item.get("materialCode"));
            outItem.setMaterialName((String) item.get("materialName"));
            outItem.setQuantity(qty);
            outItem.setSortOrder(sort++);
            try {
                List<InventoryStockItem> fifoItems = stockItemMapper.selectFIFOAvailable(materialId);
                if (!fifoItems.isEmpty() && fifoItems.get(0).getLocationId() != null) {
                    outItem.setLocationId(fifoItems.get(0).getLocationId());
                }
            } catch (Exception e) {
                log.warn("追加领料推荐库位失败(跳过): materialId={}", materialId);
            }
            totalQty = totalQty.add(qty);
            outboundItemMapper.insert(outItem);
        }
        order.setTotalQuantity(totalQty);
        outboundOrderMapper.updateById(order);
        prodOrder.setMaterialStatus(1);
        productionOrderMapper.updateById(prodOrder);
        log.info("追加领料单已生成(待发料): workOrderId={}, outboundId={}, no={}", workOrderId, order.getOutboundId(), outboundNo);
        return order.getOutboundId();
    }

    @Override
    @Event(value = "inventory.outbound.created_from_sales", bizId = "#salesOrderId", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public Long createFromSales(Long salesOrderId) {
        log.info("从销售订单创建出库单: salesOrderId={}", salesOrderId);

        // 1. 查询销售订单
        com.jjx.sales.domain.entity.SalesOrder salesOrder = salesOrderMapper.selectById(salesOrderId);
        if (salesOrder == null) {
            throw new BusinessException("销售订单不存在: " + salesOrderId);
        }

        // 2. 查询订单产品
        LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesOrderProduct> itemWrapper =
                new LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesOrderProduct>()
                        .eq(com.jjx.sales.domain.entity.SalesOrderProduct::getOrderId, salesOrderId);
        List<com.jjx.sales.domain.entity.SalesOrderProduct> products = salesOrderProductMapper.selectList(itemWrapper);
        if (products.isEmpty()) {
            throw new BusinessException("销售订单无产品明细，无法出库");
        }

        // 3. 创建出库单（073分批：去掉 SHIP-{orderNo} 唯一限制，改为 SHIP-{orderNo}-{序号}，支持多张出库单累计不超订单量）
        long shipSeq = outboundOrderMapper.selectCount(
                new LambdaQueryWrapper<InventoryOutboundOrder>()
                        .eq(InventoryOutboundOrder::getSourceType, "SALES")
                        .eq(InventoryOutboundOrder::getSourceId, salesOrderId)) + 1;
        String outboundNo = "SHIP-" + salesOrder.getOrderNo() + "-" + shipSeq;

        InventoryOutboundOrder order = new InventoryOutboundOrder();
        order.setOutboundNo(outboundNo);
        order.setOutboundType("SALES_SHIP");
        order.setSourceType("SALES");
        order.setSourceId(salesOrderId);
        order.setSourceNo(salesOrder.getOrderNo());
        order.setTraceId(salesOrder.getTraceId()); // 链路追踪（DEV-568）：销售订单→发货出库单继承
        order.setOutboundDate(LocalDate.now());
        // DEV-932修复：销售发货出库单必须带仓库，参考 createFromProduction 取默认启用仓库（warehouse_id NOT NULL 无默认值）
        // 2026-08-18：查不到启用仓库时明确报错（原静默跳过导致 warehouse_id NULL → SQL 裸错）
        order.setWarehouseId(getDefaultWarehouseOrThrow().getWarehouseId());
        order.setOrderStatus(InventoryOrderStatusEnum.DRAFT.getValue());
        outboundOrderMapper.insert(order);

        // 4. 创建出库单明细（成品物料映射：产品→inventory_material.product_id）
        // 073分批：本次发货量 = min(产品订单量 - Σ已发货量, 产品库存可用量)，不足提示
        int sort = 1;
        for (com.jjx.sales.domain.entity.SalesOrderProduct product : products) {
            InventoryOutboundItem outItem = new InventoryOutboundItem();
            // 查成品物料档案（发布时自动创建，material_type='F'）
            com.jjx.inventory.domain.InventoryMaterial finishMat = null;
            try {
                finishMat = materialMapper.selectOne(
                        new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                                .eq(com.jjx.inventory.domain.InventoryMaterial::getProductId, product.getProductId())
                                .last("LIMIT 1"));
            } catch (Exception e) {
                log.warn("查询成品物料失败: {}", e.getMessage());
            }
            if (finishMat == null) {
                throw new BusinessException("产品[" + product.getProductCode() + "]无成品物料档案，请先发布产品");
            }
            // 已发货量（该订单所有销售出库单明细合计）
            BigDecimal shipped = BigDecimal.ZERO;
            try {
                List<InventoryOutboundOrder> shipOrders = outboundOrderMapper.selectList(
                        new LambdaQueryWrapper<InventoryOutboundOrder>()
                                .eq(InventoryOutboundOrder::getSourceType, "SALES")
                                .eq(InventoryOutboundOrder::getSourceId, salesOrderId));
                for (InventoryOutboundOrder so : shipOrders) {
                    List<InventoryOutboundItem> soItems = outboundItemMapper.selectByOutboundId(so.getOutboundId());
                    for (InventoryOutboundItem si : soItems) {
                        if (si.getMaterialId() != null && si.getMaterialId().equals(finishMat.getMaterialId()) && si.getQuantity() != null) {
                            shipped = shipped.add(si.getQuantity());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("查询已发货量失败: {}", e.getMessage());
            }
            BigDecimal orderQty = BigDecimal.valueOf(product.getQuantity() == null ? 0 : product.getQuantity());
            BigDecimal remaining = orderQty.subtract(shipped);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // 该产品已发完，本单跳过
            }
            // 产品库存可用量（096产品维度）
            BigDecimal productAvailable = BigDecimal.ZERO;
            try {
                com.jjx.inventory.domain.ProductStock ps = productStockService.getByProductId(product.getProductId());
                if (ps != null && ps.getAvailableQuantity() != null) {
                    productAvailable = ps.getAvailableQuantity();
                }
            } catch (Exception e) {
                log.warn("查询产品库存失败(按0处理): productId={}", product.getProductId());
            }
            BigDecimal shipQty = remaining.min(productAvailable);
            if (shipQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("产品[" + product.getProductCode() + "]库存不足，无法发货（还需" + remaining.stripTrailingZeros().toPlainString() + "，可用" + productAvailable.stripTrailingZeros().toPlainString() + "）");
            }
            outItem.setOutboundId(order.getOutboundId());
            outItem.setMaterialId(finishMat.getMaterialId());
            outItem.setMaterialCode(finishMat.getMaterialCode());
            outItem.setMaterialName(finishMat.getMaterialName());
            outItem.setQuantity(shipQty);
            outItem.setUnitPrice(product.getUnitPrice());
            outItem.setSortOrder(sort++);
            // DEV-693：预填 FIFO 推荐库位（最早批次所在库位）
            try {
                List<InventoryStockItem> fifoItems = stockItemMapper.selectFIFOAvailable(finishMat.getMaterialId());
                if (!fifoItems.isEmpty() && fifoItems.get(0).getLocationId() != null) {
                    outItem.setLocationId(fifoItems.get(0).getLocationId());
                }
            } catch (Exception e) {
                log.warn("销售出库推荐库位失败(跳过): materialId={}, err={}", finishMat.getMaterialId(), e.getMessage());
            }
            outboundItemMapper.insert(outItem);
        }

        // 5. 提交审批并自动审批
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        outboundOrderMapper.updateById(order);
        approve(order.getOutboundId(), null, null, "销售发货出库");

        // 021/073定稿：销售发货出库必须扣库存——approve 后立即 confirm（confirm=审批+完成 单路径，库存扣减在 confirm）
        // 原实现只 approve 不 confirm → 库存永远不扣（最严重 bug）
        try {
            confirm(order.getOutboundId(), null, "销售发货出库");
            log.info("销售发货出库已自动确认并扣库存: outboundId={}", order.getOutboundId());
        } catch (Exception e) {
            log.error("销售发货出库自动确认失败（需人工处理）: outboundId={}, err={}", order.getOutboundId(), e.getMessage());
            throw new BusinessException("销售发货出库确认失败：" + e.getMessage());
        }

        log.info("销售发货出库完成: salesOrderId={}, outboundId={}", salesOrderId, order.getOutboundId());
        return order.getOutboundId();
    }

    @Override
    public List<OutboundVO> getPendingApproval() {
        List<InventoryOutboundOrder> orders = outboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryOutboundOrder>()
                        .eq(InventoryOutboundOrder::getOrderStatus, InventoryOrderStatusEnum.PENDING.getValue())
                        .orderByAsc(InventoryOutboundOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public List<OutboundVO> getByDateRange(String startDate, String endDate) {
        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryOutboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryOutboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime);

        List<InventoryOutboundOrder> orders = outboundOrderMapper.selectList(wrapper);
        return convertToVOList(orders);
    }

    @Override
    public OutboundVO getBySource(String sourceType, Long sourceId) {
        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryOutboundOrder::getSourceType, sourceType)
                .eq(InventoryOutboundOrder::getSourceId, sourceId);
        InventoryOutboundOrder order = outboundOrderMapper.selectOne(wrapper);
        return convertToVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long outboundId, Integer status) {
        // DEV-651 方案A：行锁
        InventoryOutboundOrder order = outboundOrderMapper.selectByIdForUpdate(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        order.setOrderStatus(status);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public IPage<InventoryOutboundOrder> pageQuery(Map<String, Object> params) {
        String outboundNo = (String) params.get("outboundNo");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (outboundNo != null && !outboundNo.isEmpty()) {
            wrapper.like(InventoryOutboundOrder::getOutboundNo, outboundNo);
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryOutboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryOutboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime);

        Page<InventoryOutboundOrder> page = new Page<>(pageNum, pageSize);
        return outboundOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Map<String, Object> params) {
        if (params != null && params.get("outboundId") != null) {
            Long outboundId = Long.valueOf(params.get("outboundId").toString());
            OutboundVO detail = getDetail(outboundId);
            if (detail != null) {
                return Map.of("code", 200, "data", detail);
            }
        }
        return Map.of("code", 404, "message", "出库单不存在");
    }

    private static List<OutboundVO> convertToVOList(List<InventoryOutboundOrder> orders) {
        List<OutboundVO> result = new ArrayList<>();
        for (InventoryOutboundOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private static OutboundVO convertToVO(InventoryOutboundOrder order) {
        if (order == null) {
            return null;
        }

        OutboundVO vo = new OutboundVO();
        BeanUtils.copyProperties(order, vo);

        // 设置类型名称与状态名称
        OutboundTypeEnum typeEnum = OutboundTypeEnum.getByCode(order.getOutboundType());
        vo.setOutboundTypeName(typeEnum != null ? typeEnum.getLabel() : order.getOutboundType());
        InventoryOrderStatusEnum statusEnum = InventoryOrderStatusEnum.getByValue(order.getOrderStatus());
        vo.setStatus(order.getOrderStatus());
        if (statusEnum != null) {
            // 生产领料单：待审批显示"待发料"，已完成显示"已发料"
            if (OutboundTypeEnum.PRODUCTION.getCode().equals(order.getOutboundType())) {
                if (InventoryOrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())) {
                    vo.setStatusName("待发料");
                } else if (InventoryOrderStatusEnum.COMPLETED.getValue().equals(order.getOrderStatus())) {
                    vo.setStatusName("已发料");
                } else {
                    vo.setStatusName(statusEnum.getLabel());
                }
            } else {
                vo.setStatusName(statusEnum.getLabel());
            }
        }

        return vo;
    }

    /**
     * DEV-660：判断是否领料单（production 类型或 PICK- 单号前缀）
     */
    private static boolean isPickOrder(OutboundVO vo) {
        if (vo == null) return false;
        if (OutboundTypeEnum.PRODUCTION.getCode().equals(vo.getOutboundType())) return true;
        return vo.getOutboundNo() != null && vo.getOutboundNo().startsWith("PICK-");
    }

    private static List<OutboundItemVO> convertToItemVOList(List<InventoryOutboundItem> items, com.jjx.inventory.mapper.InventoryStorageLocationMapper locMapper) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        List<OutboundItemVO> result = new ArrayList<>();
        for (InventoryOutboundItem item : items) result.add(convertToItemVO(item, locMapper));
        return result;
    }

    private static OutboundItemVO convertToItemVO(InventoryOutboundItem item, com.jjx.inventory.mapper.InventoryStorageLocationMapper locMapper) {
        if (item == null) return null;
        OutboundItemVO vo = new OutboundItemVO();
        vo.setOutboundItemId(item.getItemId());
        vo.setOutboundId(item.getOutboundId());
        vo.setMaterialId(item.getMaterialId());
        vo.setMaterialCode(item.getMaterialCode());
        vo.setMaterialName(item.getMaterialName());
        vo.setSpecification(item.getSpecification());
        vo.setUnit(item.getUnit());
        vo.setQuantity(item.getQuantity());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setAmount(item.getAmount());
        vo.setBatchNo(item.getBatchNo());
        vo.setLocationId(item.getLocationId());
        // DEV-693：回填库位名称（拣货指导）
        if (item.getLocationId() != null && locMapper != null) {
            try {
                InventoryStorageLocation loc = locMapper.selectById(item.getLocationId());
                if (loc != null) vo.setLocationName(loc.getLocationName());
            } catch (Exception e) {
                // 忽略库位名称填充失败
            }
        }
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }

}
