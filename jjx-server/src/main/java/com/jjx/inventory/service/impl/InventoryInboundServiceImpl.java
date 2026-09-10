package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryInboundItem;
import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.domain.InventoryWarehouse;
import com.jjx.inventory.dto.query.InboundQueryDTO;
import com.jjx.inventory.dto.query.IqcPendingQueryDTO;
import com.jjx.inventory.dto.save.InboundInspectionSubmitDTO;
import com.jjx.inventory.dto.vo.InboundItemVO;
import com.jjx.inventory.dto.vo.InboundVO;
import com.jjx.inventory.dto.vo.IqcPendingVO;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.enums.QualityDispositionEnum;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.enums.MaterialEnums;
import com.jjx.inventory.mapper.InventoryInboundItemMapper;
import com.jjx.inventory.mapper.InventoryInboundOrderMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.mapper.InventoryWarehouseMapper;
import com.jjx.inventory.service.InventoryInboundService;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.inventory.service.InventoryItemService;
import com.jjx.inventory.enums.InventoryItemTypeEnum;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import com.jjx.system.annotation.Event;

/**
 * 入库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryInboundServiceImpl extends ServiceImpl<InventoryInboundOrderMapper, InventoryInboundOrder>
        implements InventoryInboundService {

    private final InventoryInboundOrderMapper inboundOrderMapper;
    private final InventoryInboundItemMapper inboundItemMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final InventoryMaterialMapper inventoryMaterialMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final EventPublisher eventPublisher;
    private final InventoryAlertService alertService;
    private final InventoryItemService inventoryItemService;
    private final com.jjx.sales.mapper.OrderMapper salesOrderMapper;
    private final com.jjx.production.service.QualityInspectionService qualityInspectionService;
    private final com.jjx.production.service.QualityActionService qualityActionService;
    private final com.jjx.production.mapper.ProductionQualityInspectionMapper qualityInspectionMapper;
    private final com.jjx.inventory.mapper.InventoryIqcQuarantineMapper iqcQuarantineMapper;
    private final com.jjx.inventory.mapper.InventoryIqcDispositionOrderMapper iqcDispositionOrderMapper;
    private final com.jjx.inventory.mapper.InventoryIqcReturnOrderMapper iqcReturnOrderMapper;
    private final com.jjx.inventory.mapper.InventoryIqcReworkOrderMapper iqcReworkOrderMapper;
    private final com.jjx.inventory.mapper.InventoryIqcScrapOrderMapper iqcScrapOrderMapper;

    @Override
    public IPage<IqcPendingVO> pageIqcPending(IqcPendingQueryDTO query) {
        Page<IqcPendingVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        return inboundOrderMapper.selectIqcPendingPage(
                page,
                "PURCHASE",
                InventoryOrderStatusEnum.PENDING.getValue(),
                InventoryOrderStatusEnum.APPROVED.getValue(),
                InventoryOrderStatusEnum.COMPLETED.getValue(),
                query.getOrderStatus(),
                query.getFillInspection(),
                query.getInboundNo());
    }

    @Override
    public IPage<InboundVO> page(InboundQueryDTO query) {
        // 构建查询条件
        LambdaQueryWrapper<InventoryInboundOrder> wrapper = new LambdaQueryWrapper<>();

        // 根据查询参数添加条件
        if (query.getInboundId() != null) {
            wrapper.eq(InventoryInboundOrder::getInboundId, query.getInboundId());
        }

        if (query.getInboundNo() != null && !query.getInboundNo().isEmpty()) {
            wrapper.like(InventoryInboundOrder::getInboundNo, query.getInboundNo());
        }

        if (query.getInboundType() != null && !query.getInboundType().isEmpty()) {
            wrapper.eq(InventoryInboundOrder::getInboundType, query.getInboundType());
        }

        if (query.getWarehouseId() != null) {
            wrapper.eq(InventoryInboundOrder::getWarehouseId, query.getWarehouseId());
        }

        if (query.getSourceType() != null && !query.getSourceType().isEmpty()) {
            wrapper.eq(InventoryInboundOrder::getSourceType, query.getSourceType());
        }

        if (query.getSourceNo() != null && !query.getSourceNo().isEmpty()) {
            wrapper.like(InventoryInboundOrder::getSourceNo, query.getSourceNo());
        }

        if (query.getOrderStatus() != null && !query.getOrderStatus().isEmpty()) {
            wrapper.eq(InventoryInboundOrder::getOrderStatus, query.getOrderStatus());
        }

        if (query.getApproveStatus() != null && !query.getApproveStatus().isEmpty()) {
            wrapper.eq(InventoryInboundOrder::getApproveStatus, query.getApproveStatus());
        }

        // 入库日期范围查询
        if (query.getInboundDateStart() != null) {
            wrapper.ge(InventoryInboundOrder::getInboundDate, query.getInboundDateStart());
        }

        if (query.getInboundDateEnd() != null) {
            wrapper.le(InventoryInboundOrder::getInboundDate, query.getInboundDateEnd());
        }

        // 创建时间范围查询
        if (query.getCreateTimeStart() != null && !query.getCreateTimeStart().isEmpty()) {
            wrapper.ge(InventoryInboundOrder::getCreateTime, query.getCreateTimeStart());
        }

        if (query.getCreateTimeEnd() != null && !query.getCreateTimeEnd().isEmpty()) {
            wrapper.le(InventoryInboundOrder::getCreateTime, query.getCreateTimeEnd());
        }

        // 排序处理
        if (query.getOrderBy() != null && !query.getOrderBy().isEmpty()) {
            String orderBy = query.getOrderBy();
            boolean isAsc = "asc".equalsIgnoreCase(query.getOrderDirection());

            // 根据orderBy字段映射到实体字段
            switch (orderBy) {
                case "inboundNo":
                    wrapper.orderBy(true, isAsc, InventoryInboundOrder::getInboundNo);
                    break;
                case "inboundDate":
                    wrapper.orderBy(true, isAsc, InventoryInboundOrder::getInboundDate);
                    break;
                case "createTime":
                    wrapper.orderBy(true, isAsc, InventoryInboundOrder::getCreateTime);
                    break;
                case "totalAmount":
                    wrapper.orderBy(true, isAsc, InventoryInboundOrder::getTotalAmount);
                    break;
                default:
                    // 默认按创建时间倒序
                    wrapper.orderByDesc(InventoryInboundOrder::getCreateTime);
            }
        } else {
            // 默认按创建时间倒序
            wrapper.orderByDesc(InventoryInboundOrder::getCreateTime);
        }

        // 执行分页查询
        Page<InventoryInboundOrder> orderPage = new Page<>(query.getCurrent(), query.getSize());
        IPage<InventoryInboundOrder> orderResult = inboundOrderMapper.selectPage(orderPage, wrapper);

        // 转换为VO分页
        Page<InboundVO> voPage = new Page<>(query.getCurrent(), query.getSize());
        voPage.setTotal(orderResult.getTotal());
        voPage.setPages(orderResult.getPages());

        // 转换数据
        List<InboundVO> voList = convertToVOList(orderResult.getRecords());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public InboundVO getDetail(Long inboundId) {
        InventoryInboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return null;
        }

        InboundVO vo = convertToVO(order);
        // 获取入库单明细项并设置到VO对象
        List<InventoryInboundItem> items = inboundItemMapper.selectByInboundId(inboundId);
        if (items != null && !items.isEmpty()) {
            vo.setItems(convertToItemVOList(items));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.created", bizId = "#params", bizType = "'inventory'")
    public Long create(Map<String, Object> params) {
        log.info("创建入库单: {}", params);
        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundNo((String) params.getOrDefault("inboundNo", "IN-" + System.currentTimeMillis()));
        order.setInboundType((String) params.getOrDefault("inboundType", "purchase"));
        order.setSourceType((String) params.get("sourceType"));
        if (params.get("sourceId") != null) order.setSourceId(Long.valueOf(params.get("sourceId").toString()));
        order.setSourceNo((String) params.get("sourceNo"));
        if (params.get("warehouseId") != null) order.setWarehouseId(Long.valueOf(params.get("warehouseId").toString()));
        if (params.get("supplierId") != null) order.setSupplierId(Long.valueOf(params.get("supplierId").toString()));
        order.setSupplierName((String) params.get("supplierName"));
        // 入库日期：缺省今天（DEV-436 修复：inbound_date NOT NULL 无默认值）
        if (params.get("inboundDate") != null && !params.get("inboundDate").toString().isEmpty()) {
            order.setInboundDate(LocalDate.parse(params.get("inboundDate").toString()));
        } else {
            order.setInboundDate(LocalDate.now());
        }
        order.setRemark((String) params.get("remark"));
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.insert(order);

        // 保存明细（DEV-436 修复：原 create 不落 items，导致入库单无明细无法确认）
        Object itemsObj = params.get("items");
        if (itemsObj instanceof List<?> itemList && !itemList.isEmpty()) {
            List<InventoryInboundItem> items = new ArrayList<>();
            int sort = 1;
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmt = BigDecimal.ZERO;
            for (Object obj : itemList) {
                if (!(obj instanceof Map<?, ?> m)) continue;
                InventoryInboundItem item = new InventoryInboundItem();
                item.setInboundId(order.getInboundId());
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
                if (m.get("productionDate") != null && !m.get("productionDate").toString().isEmpty()) {
                    item.setProductionDate(LocalDate.parse(m.get("productionDate").toString()));
                }
                if (m.get("expiryDate") != null && !m.get("expiryDate").toString().isEmpty()) {
                    item.setExpiryDate(LocalDate.parse(m.get("expiryDate").toString()));
                }
                if (m.get("locationId") != null) item.setLocationId(Long.valueOf(m.get("locationId").toString()));
                item.setRemark((String) m.get("remark"));
                item.setSortOrder(sort++);
                items.add(item);
                totalQty = totalQty.add(qty);
                totalAmt = totalAmt.add(item.getAmount());
            }
            if (!items.isEmpty()) {
                inboundItemMapper.batchInsert(items);
                order.setTotalQuantity(totalQty);
                order.setTotalAmount(totalAmt);
                inboundOrderMapper.updateById(order);
            }
        }
        return order.getInboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.confirmed", bizId = "#inboundId", bizType = "'inventory'")
    public boolean confirm(Long inboundId, Long operatorId, String operatorName) {
        // DEV-651 方案A：行锁查询，锁住单据行直到事务提交，并发下第二个请求阻塞后状态校验失败，杜绝重复入库
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        // 手工入库保持 confirm 直接过账；采购来源必须走检验提交 + 品质主管 approve。
        boolean purchaseConfirmable = isPurchaseInbound(order)
                && InventoryOrderStatusEnum.APPROVED.getValue().equals(order.getOrderStatus());
        boolean manualConfirmable = !isPurchaseInbound(order)
                && (InventoryOrderStatusEnum.DRAFT.getValue().equals(order.getOrderStatus())
                || InventoryOrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())
                || InventoryOrderStatusEnum.APPROVED.getValue().equals(order.getOrderStatus()));
        if (!manualConfirmable && !purchaseConfirmable) {
            log.error("入库单状态或来源不允许直接确认: inboundId={}, status={}", inboundId, order.getOrderStatus());
            return false;
        }

        // 采购单 confirm 仅负责合格/允收数量过账：加库存+流水+置完成；不合格品隔离台账已在全部行审核通过时创建
        if (purchaseConfirmable) validateAllIqcApproved(inboundId);
        BigDecimal postedQtyThisTime = BigDecimal.ZERO;
        if ("PRODUCTION".equals(order.getSourceType())) {
            postedQtyThisTime = inboundItemMapper.selectByInboundId(inboundId).stream()
                    .map(item -> {
                        BigDecimal targetQuantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                        BigDecimal postedQuantity = Objects.requireNonNullElse(item.getPostedQuantity(), BigDecimal.ZERO);
                        BigDecimal quantityToPost = targetQuantity.subtract(postedQuantity);
                        return quantityToPost.compareTo(BigDecimal.ZERO) > 0 ? quantityToPost : BigDecimal.ZERO;
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        addStock(order, operatorId, operatorName, "确认入库");
        if ("PRODUCTION".equals(order.getSourceType())) {
            writebackProducedQuantity(order, postedQtyThisTime);
        }
        // 幂等兜底：审核通过时已建，此处跳过重复，兼容历史数据及边界场景
        if (purchaseConfirmable) createIqcQuarantine(order, operatorId, operatorName);
        order.setOrderStatus(InventoryOrderStatusEnum.COMPLETED.getValue());
        // 安全库存检查
        try {
            List<InventoryInboundItem> items = inboundItemMapper.selectByInboundId(inboundId);
            for (InventoryInboundItem item : items) {
                alertService.checkSafeStockAlert(item.getMaterialId());
            }
        } catch (Exception e) {
            log.warn("安全库存检查失败: {}", e.getMessage());
        }
        return inboundOrderMapper.updateById(order) > 0;
    }

    private void validateAllIqcApproved(Long inboundId) {
        for (InventoryInboundItem item : inboundItemMapper.selectByInboundId(inboundId)) {
            com.jjx.production.domain.entity.ProductionQualityInspection quality = item.getInspectionId() == null
                    ? null : qualityInspectionMapper.selectById(item.getInspectionId());
            if (quality == null || !com.jjx.production.enums.QualityReviewStatusEnum.APPROVED.getCode()
                    .equals(quality.getReviewStatus())) {
                throw new BusinessException("物料" + item.getMaterialCode() + " IQC 尚未审核通过");
            }
        }
    }

    private void createIqcQuarantine(InventoryInboundOrder order, Long operatorId, String operatorName) {
        for (InventoryInboundItem item : inboundItemMapper.selectByInboundId(order.getInboundId())) {
            BigDecimal accepted = Objects.requireNonNullElse(item.getAcceptedQuantity(), BigDecimal.ZERO);
            BigDecimal quarantineQty = item.getQuantity().subtract(accepted);
            if (quarantineQty.signum() <= 0) continue;
            Long count = iqcQuarantineMapper.selectCount(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcQuarantine>()
                    .eq(com.jjx.inventory.domain.InventoryIqcQuarantine::getInboundItemId, item.getItemId())
                    .eq(com.jjx.inventory.domain.InventoryIqcQuarantine::getInspectionId, item.getInspectionId()));
            if (count != null && count > 0) continue;
            com.jjx.inventory.domain.InventoryIqcQuarantine quarantine = new com.jjx.inventory.domain.InventoryIqcQuarantine();
            quarantine.setInboundId(order.getInboundId());
            quarantine.setInboundItemId(item.getItemId());
            quarantine.setInspectionId(item.getInspectionId());
            quarantine.setMaterialId(item.getMaterialId());
            quarantine.setMaterialCode(item.getMaterialCode());
            quarantine.setMaterialName(item.getMaterialName());
            quarantine.setBatchNo(item.getBatchNo());
            quarantine.setQuantity(quarantineQty);
            quarantine.setRemainingQuantity(quarantineQty);
            quarantine.setDisposition(item.getDisposition());
            quarantine.setStatus(com.jjx.inventory.enums.IqcQuarantineStatusEnum.PENDING.getCode());
            quarantine.setOperatorId(operatorId != null ? operatorId : SecurityUtils.getUserId());
            quarantine.setOperatorName(operatorName != null ? operatorName : SecurityUtils.getUsername());
            iqcQuarantineMapper.insert(quarantine);

            InventoryTransaction tx = new InventoryTransaction();
            tx.setMaterialId(item.getMaterialId());
            tx.setMaterialCode(item.getMaterialCode());
            tx.setMaterialName(item.getMaterialName());
            tx.setWarehouseId(order.getWarehouseId());
            tx.setLocationId(item.getLocationId());
            tx.setTransactionType("IQC_QUARANTINE");
            tx.setSourceType("INBOUND_IQC");
            tx.setSourceId(order.getInboundId());
            tx.setSourceNo(order.getInboundNo());
            tx.setBatchNo(item.getBatchNo());
            tx.setQuantity(quarantineQty);
            tx.setBeforeQuantity(BigDecimal.ZERO);
            tx.setAfterQuantity(quarantineQty);
            tx.setUnitCost(item.getUnitPrice());
            tx.setAmount(item.getUnitPrice() == null ? null : item.getUnitPrice().multiply(quarantineQty));
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(quarantine.getOperatorId());
            tx.setOperatorName(quarantine.getOperatorName());
            tx.setRemark("IQC 不合格品隔离");
            transactionMapper.insert(tx);
        }
    }

    @Override
    public List<com.jjx.inventory.domain.InventoryIqcQuarantine> listQuarantine(Long inboundId) {
        return iqcQuarantineMapper.selectList(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcQuarantine>()
                .eq(com.jjx.inventory.domain.InventoryIqcQuarantine::getInboundId, inboundId)
                .orderByAsc(com.jjx.inventory.domain.InventoryIqcQuarantine::getQuarantineId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleQuarantine(Long quarantineId, com.jjx.inventory.dto.save.IqcQuarantineActionDTO action) {
        var quarantine = iqcQuarantineMapper.selectById(quarantineId);
        if (quarantine == null) throw new BusinessException("隔离记录不存在");
        if (!com.jjx.inventory.enums.IqcQuarantineStatusEnum.PENDING.getCode().equals(quarantine.getStatus())) {
            throw new BusinessException("该隔离记录已完成处置");
        }
        BigDecimal quantity = action == null || action.getQuantity() == null ? BigDecimal.ZERO : action.getQuantity();
        if (quantity.signum() <= 0 || quantity.compareTo(quarantine.getRemainingQuantity()) > 0) {
            throw new BusinessException("处置数量必须大于 0 且不能超过隔离剩余数量");
        }
        String actionCode = action.getAction();
        String status;
        boolean release = "RELEASE".equals(actionCode);
        if (release) status = com.jjx.inventory.enums.IqcQuarantineStatusEnum.RELEASED.getCode();
        else if ("RETURN".equals(actionCode)) status = com.jjx.inventory.enums.IqcQuarantineStatusEnum.RETURNED.getCode();
        else if ("REWORK".equals(actionCode)) status = com.jjx.inventory.enums.IqcQuarantineStatusEnum.REWORKED.getCode();
        else if ("SCRAP".equals(actionCode)) status = com.jjx.inventory.enums.IqcQuarantineStatusEnum.SCRAPPED.getCode();
        else throw new BusinessException("不支持的隔离处置方式");

        if (release) addReleasedQuarantineStock(quarantine, quantity, action);
        var dispositionOrder = new com.jjx.inventory.domain.InventoryIqcDispositionOrder();
        dispositionOrder.setDispositionNo("IQD" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%03d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000)));
        dispositionOrder.setQuarantineId(quarantine.getQuarantineId());
        dispositionOrder.setInboundId(quarantine.getInboundId());
        dispositionOrder.setInboundItemId(quarantine.getInboundItemId());
        dispositionOrder.setInspectionId(quarantine.getInspectionId());
        dispositionOrder.setAction(actionCode);
        dispositionOrder.setQuantity(quantity);
        dispositionOrder.setMaterialCode(quarantine.getMaterialCode());
        dispositionOrder.setMaterialName(quarantine.getMaterialName());
        dispositionOrder.setBatchNo(quarantine.getBatchNo());
        dispositionOrder.setRemark(action.getRemark());
        dispositionOrder.setStatus("COMPLETED");
        dispositionOrder.setOperatorId(action.getOperatorId() != null ? action.getOperatorId() : SecurityUtils.getUserId());
        dispositionOrder.setOperatorName(action.getOperatorName() != null ? action.getOperatorName() : SecurityUtils.getUsername());
        iqcDispositionOrderMapper.insert(dispositionOrder);
        if ("RETURN".equals(actionCode)) createIqcReturnOrder(quarantine, dispositionOrder, action);
        if ("REWORK".equals(actionCode)) createIqcReworkOrder(quarantine, dispositionOrder, action);
        if ("SCRAP".equals(actionCode)) createIqcScrapOrder(quarantine, dispositionOrder, action);
        quarantine.setRemainingQuantity(quarantine.getRemainingQuantity().subtract(quantity));
        if (quarantine.getRemainingQuantity().signum() == 0) quarantine.setStatus(status);
        iqcQuarantineMapper.updateById(quarantine);

        InventoryTransaction tx = new InventoryTransaction();
        InventoryInboundItem inboundItem = inboundItemMapper.selectById(quarantine.getInboundItemId());
        InventoryInboundOrder inboundOrder = inboundOrderMapper.selectById(quarantine.getInboundId());
        tx.setMaterialId(quarantine.getMaterialId()); tx.setMaterialCode(quarantine.getMaterialCode());
        tx.setMaterialName(quarantine.getMaterialName()); tx.setTransactionType("IQC_" + actionCode);
        tx.setWarehouseId(inboundOrder == null ? null : inboundOrder.getWarehouseId());
        tx.setLocationId(inboundItem == null ? null : inboundItem.getLocationId());
        tx.setSourceType("INBOUND_IQC"); tx.setSourceId(quarantine.getInboundId());
        tx.setBatchNo(quarantine.getBatchNo()); tx.setQuantity(quantity);
        tx.setBeforeQuantity(quarantine.getRemainingQuantity().add(quantity));
        tx.setAfterQuantity(quarantine.getRemainingQuantity());
        tx.setTransactionTime(LocalDateTime.now());
        tx.setOperatorId(action.getOperatorId() != null ? action.getOperatorId() : SecurityUtils.getUserId());
        tx.setOperatorName(action.getOperatorName() != null ? action.getOperatorName() : SecurityUtils.getUsername());
        tx.setRemark(action.getRemark()); transactionMapper.insert(tx);
        return true;
    }

    private void createIqcReturnOrder(com.jjx.inventory.domain.InventoryIqcQuarantine quarantine,
                                      com.jjx.inventory.domain.InventoryIqcDispositionOrder disposition,
                                      com.jjx.inventory.dto.save.IqcQuarantineActionDTO action) {
        var item = inboundItemMapper.selectById(quarantine.getInboundItemId());
        var inbound = inboundOrderMapper.selectById(quarantine.getInboundId());
        var order = new com.jjx.inventory.domain.InventoryIqcReturnOrder();
        order.setReturnNo("IQR" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%03d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000)));
        order.setDispositionId(disposition.getDispositionId()); order.setQuarantineId(quarantine.getQuarantineId());
        order.setInboundId(quarantine.getInboundId()); order.setInboundItemId(quarantine.getInboundItemId());
        order.setInspectionId(quarantine.getInspectionId()); order.setMaterialId(quarantine.getMaterialId());
        order.setMaterialCode(quarantine.getMaterialCode()); order.setMaterialName(quarantine.getMaterialName());
        order.setBatchNo(quarantine.getBatchNo()); order.setQuantity(disposition.getQuantity());
        order.setSupplierId(inbound == null ? null : inbound.getSupplierId());
        order.setSupplierName(inbound == null ? null : inbound.getSupplierName());
        order.setReason(action.getRemark()); order.setStatus("CREATED");
        order.setOperatorId(disposition.getOperatorId()); order.setOperatorName(disposition.getOperatorName());
        iqcReturnOrderMapper.insert(order);
    }

    private void createIqcReworkOrder(com.jjx.inventory.domain.InventoryIqcQuarantine quarantine,
                                      com.jjx.inventory.domain.InventoryIqcDispositionOrder disposition,
                                      com.jjx.inventory.dto.save.IqcQuarantineActionDTO action) {
        var inbound = inboundOrderMapper.selectById(quarantine.getInboundId());
        var order = new com.jjx.inventory.domain.InventoryIqcReworkOrder();
        order.setReworkNo("IQW" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%03d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000)));
        order.setDispositionId(disposition.getDispositionId()); order.setQuarantineId(quarantine.getQuarantineId());
        order.setInboundId(quarantine.getInboundId()); order.setInboundItemId(quarantine.getInboundItemId());
        order.setInspectionId(quarantine.getInspectionId()); order.setMaterialId(quarantine.getMaterialId());
        order.setMaterialCode(quarantine.getMaterialCode()); order.setMaterialName(quarantine.getMaterialName());
        order.setBatchNo(quarantine.getBatchNo()); order.setQuantity(disposition.getQuantity());
        order.setSupplierId(inbound == null ? null : inbound.getSupplierId());
        order.setSupplierName(inbound == null ? null : inbound.getSupplierName());
        order.setReason(action.getRemark()); order.setStatus("CREATED");
        order.setOperatorId(disposition.getOperatorId()); order.setOperatorName(disposition.getOperatorName());
        iqcReworkOrderMapper.insert(order);
    }

    private void createIqcScrapOrder(com.jjx.inventory.domain.InventoryIqcQuarantine quarantine,
                                     com.jjx.inventory.domain.InventoryIqcDispositionOrder disposition,
                                     com.jjx.inventory.dto.save.IqcQuarantineActionDTO action) {
        var order = new com.jjx.inventory.domain.InventoryIqcScrapOrder();
        order.setScrapNo("IQS" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + String.format("%03d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000)));
        order.setDispositionId(disposition.getDispositionId()); order.setQuarantineId(quarantine.getQuarantineId());
        order.setInboundId(quarantine.getInboundId()); order.setInboundItemId(quarantine.getInboundItemId());
        order.setInspectionId(quarantine.getInspectionId()); order.setMaterialId(quarantine.getMaterialId());
        order.setMaterialCode(quarantine.getMaterialCode()); order.setMaterialName(quarantine.getMaterialName());
        order.setBatchNo(quarantine.getBatchNo()); order.setQuantity(disposition.getQuantity());
        order.setReason(action.getRemark()); order.setStatus("PENDING_APPROVAL");
        order.setApplicantId(disposition.getOperatorId()); order.setApplicantName(disposition.getOperatorName());
        iqcScrapOrderMapper.insert(order);
    }

    @Override
    public List<com.jjx.inventory.domain.InventoryIqcDispositionOrder> listDispositionOrders(Long inboundId) {
        return iqcDispositionOrderMapper.selectList(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcDispositionOrder>()
                .eq(com.jjx.inventory.domain.InventoryIqcDispositionOrder::getInboundId, inboundId)
                .orderByDesc(com.jjx.inventory.domain.InventoryIqcDispositionOrder::getDispositionId));
    }

    @Override
    public List<com.jjx.inventory.domain.InventoryIqcQuarantine> listAllQuarantine(String status) {
        var wrapper = new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcQuarantine>();
        if (status != null && !status.isBlank()) wrapper.eq(com.jjx.inventory.domain.InventoryIqcQuarantine::getStatus, status);
        return iqcQuarantineMapper.selectList(wrapper.orderByDesc(com.jjx.inventory.domain.InventoryIqcQuarantine::getQuarantineId));
    }

    @Override
    public List<com.jjx.inventory.domain.InventoryIqcDispositionOrder> listAllDispositionOrders(String action) {
        var wrapper = new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcDispositionOrder>();
        if (action != null && !action.isBlank()) wrapper.eq(com.jjx.inventory.domain.InventoryIqcDispositionOrder::getAction, action);
        return iqcDispositionOrderMapper.selectList(wrapper.orderByDesc(com.jjx.inventory.domain.InventoryIqcDispositionOrder::getDispositionId));
    }

    @Override
    public com.jjx.inventory.domain.InventoryIqcDispositionOrder getDispositionOrder(Long dispositionId) {
        var order = iqcDispositionOrderMapper.selectById(dispositionId);
        if (order == null) throw new BusinessException("IQC 处置单不存在");
        return order;
    }

    @Override
    public List<com.jjx.inventory.domain.InventoryIqcReturnOrder> listIqcReturnOrders(Long inboundId) {
        return iqcReturnOrderMapper.selectList(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcReturnOrder>()
                .eq(com.jjx.inventory.domain.InventoryIqcReturnOrder::getInboundId, inboundId)
                .orderByDesc(com.jjx.inventory.domain.InventoryIqcReturnOrder::getReturnId));
    }

    @Override
    public List<com.jjx.inventory.domain.InventoryIqcReworkOrder> listIqcReworkOrders(Long inboundId) {
        return iqcReworkOrderMapper.selectList(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcReworkOrder>()
                .eq(com.jjx.inventory.domain.InventoryIqcReworkOrder::getInboundId, inboundId)
                .orderByDesc(com.jjx.inventory.domain.InventoryIqcReworkOrder::getReworkId));
    }

    @Override
    public List<com.jjx.inventory.domain.InventoryIqcScrapOrder> listIqcScrapOrders(Long inboundId) {
        return iqcScrapOrderMapper.selectList(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcScrapOrder>()
                .eq(com.jjx.inventory.domain.InventoryIqcScrapOrder::getInboundId, inboundId)
                .orderByDesc(com.jjx.inventory.domain.InventoryIqcScrapOrder::getScrapId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approveIqcScrap(Long scrapId, com.jjx.inventory.dto.save.IqcScrapApproveDTO approval) {
        var scrap = iqcScrapOrderMapper.selectById(scrapId);
        if (scrap == null) throw new BusinessException("IQC 报废单不存在");
        if (!"PENDING_APPROVAL".equals(scrap.getStatus())) throw new BusinessException("该报废单已处理");
        if (approval == null || !approval.isApproved()) {
            scrap.setStatus("REJECTED");
        } else {
            scrap.setStatus("APPROVED");
        }
        scrap.setApproverId(approval == null ? SecurityUtils.getUserId() : approval.getApproverId());
        scrap.setApproverName(approval == null ? SecurityUtils.getUsername() : approval.getApproverName());
        iqcScrapOrderMapper.updateById(scrap);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long completeIqcRework(Long reworkId) {
        var rework = iqcReworkOrderMapper.selectById(reworkId);
        if (rework == null) throw new BusinessException("IQC 返工单不存在");
        if (!"CREATED".equals(rework.getStatus())) throw new BusinessException("该返工单已完成或已发起复检");
        var item = inboundItemMapper.selectById(rework.getInboundItemId());
        if (item == null || item.getInspectionId() == null) throw new BusinessException("找不到原 IQC 检验记录");
        Long newInspectionId = qualityActionService.reinspect(item.getInspectionId());
        var fresh = qualityInspectionMapper.selectById(newInspectionId);
        fresh.setReviewStatus(com.jjx.production.enums.QualityReviewStatusEnum.DRAFT.getCode());
        qualityInspectionMapper.updateById(fresh);
        item.setInspectionId(newInspectionId);
        item.setInspectionResult(null);
        item.setDisposition(null);
        item.setAcceptedQuantity(BigDecimal.ZERO);
        inboundItemMapper.updateById(item);
        rework.setStatus("PENDING_REINSPECTION");
        iqcReworkOrderMapper.updateById(rework);
        return newInspectionId;
    }

    private void addReleasedQuarantineStock(com.jjx.inventory.domain.InventoryIqcQuarantine quarantine,
                                             BigDecimal quantity,
                                             com.jjx.inventory.dto.save.IqcQuarantineActionDTO action) {
        LambdaQueryWrapper<InventoryStockItem> wrapper = new LambdaQueryWrapper<InventoryStockItem>()
                .eq(InventoryStockItem::getMaterialId, quarantine.getMaterialId())
                .eq(InventoryStockItem::getBatchNo, quarantine.getBatchNo())
                .eq(InventoryStockItem::getStatus, 1);
        InventoryStockItem stock = stockItemMapper.selectOne(wrapper);
        if (stock == null) throw new BusinessException("找不到原批次可用库存记录，无法释放隔离数量");
        stock.setQuantity(stock.getQuantity().add(quantity));
        stockItemMapper.updateById(stock);
        stockMapper.refreshSummary(quarantine.getMaterialId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.cancelled", bizId = "#inboundId", bizType = "'inventory'")
    public boolean cancel(Long inboundId, String reason) {
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        if (InventoryOrderStatusEnum.COMPLETED.getValue().equals(order.getOrderStatus())) {
            log.error("已完成的入库单无法取消: inboundId={}", inboundId);
            return false;
        }

        order.setOrderStatus(InventoryOrderStatusEnum.CANCELLED.getValue());
        order.setRemark(reason);
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.submitted", bizId = "#inboundId", bizType = "'inventory'")
    public boolean submitApprove(Long inboundId, InboundInspectionSubmitDTO inspection) {
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        // 采购收货单创建即处于 PENDING；inspection_result 为空表示“待检验”，有值表示“待主管复核”。
        Integer status = order.getOrderStatus();
        boolean pendingPurchaseInspection = isPurchaseInbound(order)
                && InventoryOrderStatusEnum.PENDING.getValue().equals(status);
        // 已完成入库单发生供应商返工后，需要允许提交新生成的 IQC 复检单；
        // 仅当明细当前指向一张待检草稿时放行，避免普通已完成入库单被重复提交。
        boolean pendingReinspection = isPurchaseInbound(order)
                && InventoryOrderStatusEnum.COMPLETED.getValue().equals(status)
                && inboundItemMapper.selectByInboundId(inboundId).stream().anyMatch(item -> {
                    if (item.getInspectionId() == null) return false;
                    com.jjx.production.domain.entity.ProductionQualityInspection current =
                            qualityInspectionMapper.selectById(item.getInspectionId());
                    return current != null
                            && com.jjx.production.enums.QualityInspectionResultEnum.PENDING.getCode()
                            .equals(current.getResult())
                            && com.jjx.production.enums.QualityReviewStatusEnum.DRAFT.getCode()
                            .equals(current.getReviewStatus());
                });
        if (!pendingPurchaseInspection
                && !pendingReinspection
                && !InventoryOrderStatusEnum.DRAFT.getValue().equals(status)
                && !InventoryOrderStatusEnum.REJECTED.getValue().equals(status)
                && !InventoryOrderStatusEnum.CANCELLED.getValue().equals(status)) {
            log.error("入库单状态不允许提交审批: inboundId={}, status={}", inboundId, status);
            return false;
        }

        if (isPurchaseInbound(order)) {
            saveInspection(order, inspection);
        }
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        return inboundOrderMapper.updateById(order) > 0;
    }

    private void saveInspection(InventoryInboundOrder order, InboundInspectionSubmitDTO inspection) {
        if (inspection == null) {
            throw new BusinessException("采购入库单必须填写来料检验结果");
        }
        Map<Long, InventoryInboundItem> existing = inboundItemMapper.selectByInboundId(order.getInboundId()).stream()
                .collect(Collectors.toMap(InventoryInboundItem::getItemId, item -> item));
        if (inspection.getItems() == null || inspection.getItems().size() != existing.size()) {
            throw new BusinessException("必须逐项完成本次入库单的来料检验");
        }
        boolean allPass = true;
        for (InboundInspectionSubmitDTO.Item submitted : inspection.getItems()) {
                InventoryInboundItem item = existing.get(submitted.getItemId());
                if (item == null) throw new BusinessException("入库明细不存在: " + submitted.getItemId());
                com.jjx.production.domain.entity.ProductionQualityInspection previous = item.getInspectionId() == null
                        ? null : qualityInspectionMapper.selectById(item.getInspectionId());
                if (previous != null
                        && com.jjx.production.enums.QualityReviewStatusEnum.APPROVED.getCode()
                        .equals(previous.getReviewStatus())) {
                    // 整单重提时，已审核项目以数据库事实为准，既不要求客户端重复填写，也禁止覆盖。
                    allPass = allPass && "PASS".equalsIgnoreCase(item.getInspectionResult());
                    continue;
                }
                String itemResult = submitted.getInspectionResult() == null ? "" : submitted.getInspectionResult().toUpperCase();
                if (!List.of("PASS", "FAIL").contains(itemResult)) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "检验判定仅支持 PASS、FAIL");
                }
                BigDecimal qualified = submitted.getQualifiedQuantity() == null ? BigDecimal.ZERO : submitted.getQualifiedQuantity();
                BigDecimal rejected = submitted.getRejectedQuantity() == null ? BigDecimal.ZERO : submitted.getRejectedQuantity();
                BigDecimal checked = qualified.add(rejected);
                if (qualified.signum() < 0 || rejected.signum() < 0) throw new BusinessException("合格数量和不良数量不能为负数");
                if (checked.compareTo(item.getQuantity()) > 0) throw new BusinessException("物料" + item.getMaterialCode() + "检验数量不能超过收货数量");
                if (submitted.getSampledQuantity() != null
                        && (submitted.getSampledQuantity().signum() < 0 || submitted.getSampledQuantity().compareTo(item.getQuantity()) > 0
                        || submitted.getSampledQuantity().compareTo(checked) != 0)) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "抽检数量须等于合格与不良数量之和，且不能超过收货数量");
                }
                QualityDispositionEnum disposition = QualityDispositionEnum.fromCode(submitted.getDisposition());
                if ("FAIL".equals(itemResult) && disposition == null) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "不合格时必须选择处置方式");
                }
                // dev-20260908-019（合并 1568+1591）：可删除本批不检项目；保留项目仍校验，物料判定不合格须说明原因。
                if ("FAIL".equals(itemResult)
                        && org.apache.commons.lang3.StringUtils.isBlank(submitted.getRejectReason())) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "不合格必须填写不合格原因");
                }
                if ("FAIL".equals(itemResult) && rejected.signum() <= 0) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "判定不合格时不良数量必须大于0");
                }
                if ("PASS".equals(itemResult) && rejected.signum() > 0) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "存在不良数量时不能判定合格");
                }
                validateIqcInspectionItems(item, submitted.getInspectionItems());
                BigDecimal accepted = submitted.getAcceptedQuantity() == null ? BigDecimal.ZERO : submitted.getAcceptedQuantity();
                if (accepted.signum() < 0 || accepted.compareTo(item.getQuantity()) > 0) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "允收入库数量必须在收货数量范围内");
                }
                if ("FAIL".equals(itemResult)
                        && List.of(QualityDispositionEnum.RETURN, QualityDispositionEnum.SCRAP,
                        QualityDispositionEnum.REINSPECT, QualityDispositionEnum.HOLD,
                        QualityDispositionEnum.SUPPLIER_REWORK).contains(disposition)
                        && accepted.signum() != 0) {
                    throw new BusinessException("退货/报废/返工等处置整批不接收，接收数量须为 0");
                }

                boolean editablePending = previous != null
                        && com.jjx.production.enums.QualityInspectionResultEnum.PENDING.getCode()
                        .equals(previous.getResult());
                Long inspectionId;
                if (editablePending) {
                    inspectionId = previous.getInspectionId();
                    previous.setDisposition(disposition == null ? null : disposition.getCode());
                    previous.setRemark(inspection.getInspectionRemark());
                    previous.setInspector(SecurityUtils.getUsername());
                    previous.setReviewStatus(com.jjx.production.enums.QualityReviewStatusEnum.PENDING.getCode());
                    previous.setReviewRemark(null);
                    previous.setReviewerId(null);
                    previous.setReviewerName(null);
                    previous.setReviewTime(null);
                    qualityInspectionMapper.updateById(previous);
                } else {
                    com.jjx.production.domain.dto.QualityInspectionCreateDTO create =
                            new com.jjx.production.domain.dto.QualityInspectionCreateDTO();
                    create.setInspectionType(com.jjx.production.enums.QualityInspectionTypeEnum.IQC.getCode());
                    create.setSourceType(com.jjx.production.enums.QualitySourceTypeEnum.INBOUND.getCode());
                    create.setSourceId(order.getInboundId());
                    create.setSourceItemId(item.getItemId());
                    create.setMaterialId(item.getMaterialId());
                    create.setBatchNo(item.getBatchNo());
                    if (previous != null) {
                        create.setPreviousInspectionId(previous.getInspectionId());
                        create.setInspectionVersion(previous.getInspectionVersion() == null
                                ? 2 : previous.getInspectionVersion() + 1);
                    }
                    create.setDisposition(disposition == null ? null : disposition.getCode());
                    create.setInspector(SecurityUtils.getUsername());
                    create.setRemark(inspection.getInspectionRemark());
                    create.setItems(submitted.getInspectionItems());
                    inspectionId = qualityInspectionService.create(create);
                }
                com.jjx.production.domain.dto.QualityInspectionUpdateDTO update =
                        new com.jjx.production.domain.dto.QualityInspectionUpdateDTO();
                update.setInspectionId(inspectionId);
                update.setTotalQty(submitted.getSampledQuantity());
                update.setPassQty(qualified);
                update.setFailQty(rejected);
                update.setDefectDesc(submitted.getRejectReason());
                if (editablePending) update.setItems(submitted.getInspectionItems());
                qualityInspectionService.update(update);

                com.jjx.production.domain.entity.ProductionQualityInspection submittedInspection =
                        qualityInspectionMapper.selectById(inspectionId);
                submittedInspection.setReviewStatus(com.jjx.production.enums.QualityReviewStatusEnum.PENDING.getCode());
                qualityInspectionMapper.updateById(submittedInspection);

                item.setSampledQuantity(submitted.getSampledQuantity());
                item.setInspectionId(inspectionId);
                item.setInspectionResult(itemResult);
                item.setDisposition(disposition == null ? null : disposition.getCode());
                item.setQualifiedQuantity(qualified);
                item.setRejectedQuantity(rejected);
                item.setAcceptedQuantity(accepted);
                item.setRejectReason(submitted.getRejectReason());
                inboundItemMapper.updateById(item);
                allPass = allPass && "PASS".equals(itemResult);
        }
        order.setInspectionResult(allPass ? "PASS" : "OTHER");
        order.setInspectionRemark(inspection.getInspectionRemark());
        order.setInspectorId(SecurityUtils.getUserId());
        order.setInspectorName(SecurityUtils.getUsername());
        order.setInspectionTime(LocalDateTime.now());
    }

    private static void validateIqcInspectionItems(InventoryInboundItem inboundItem,
            List<com.jjx.production.domain.dto.InspectionItemDTO> inspectionItems) {
        if (inspectionItems == null || inspectionItems.isEmpty()) {
            throw new BusinessException("物料" + inboundItem.getMaterialCode() + "必须录入检测项目");
        }
        for (com.jjx.production.domain.dto.InspectionItemDTO check : inspectionItems) {
            if (check == null || org.apache.commons.lang3.StringUtils.isBlank(check.getCheckItem())) {
                throw new BusinessException("物料" + inboundItem.getMaterialCode() + "存在未命名的检测项目");
            }
            if (org.apache.commons.lang3.StringUtils.isBlank(check.getActualValue())) {
                throw new BusinessException("物料" + inboundItem.getMaterialCode() + "的检测项目“"
                        + check.getCheckItem() + "”必须填写实测记录");
            }
            String result = check.getResult() == null ? "" : check.getResult().toLowerCase();
            if (!List.of(
                    com.jjx.production.enums.QualityInspectionResultEnum.PASS.getCode(),
                    com.jjx.production.enums.QualityInspectionResultEnum.FAIL.getCode()).contains(result)) {
                throw new BusinessException("物料" + inboundItem.getMaterialCode() + "的检测项目“"
                        + check.getCheckItem() + "”必须判定合格或不合格");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approveInspectionItem(Long itemId,
            com.jjx.inventory.dto.save.InboundInspectionReviewDTO review) {
        InventoryInboundItem item = inboundItemMapper.selectById(itemId);
        if (item == null || item.getInspectionId() == null) throw new BusinessException("入库明细尚未提交 IQC 检验");
        com.jjx.production.domain.entity.ProductionQualityInspection quality = qualityInspectionMapper.selectById(item.getInspectionId());
        if (quality == null) throw new BusinessException("IQC 检验记录不存在");
        if (!com.jjx.production.enums.QualityReviewStatusEnum.PENDING.getCode().equals(quality.getReviewStatus())) {
            throw new BusinessException("仅待审核的 IQC 记录可以审核");
        }
        com.jjx.production.domain.dto.QualityJudgeDTO judge = new com.jjx.production.domain.dto.QualityJudgeDTO();
        judge.setResult("PASS".equalsIgnoreCase(item.getInspectionResult())
                ? com.jjx.production.enums.QualityInspectionResultEnum.PASS.getCode()
                : com.jjx.production.enums.QualityInspectionResultEnum.FAIL.getCode());
        judge.setTotalQty(item.getSampledQuantity());
        judge.setPassQty(item.getQualifiedQuantity());
        judge.setFailQty(item.getRejectedQuantity());
        judge.setDefectDesc(item.getRejectReason());
        judge.setRemark(quality.getRemark());
        qualityActionService.judge(quality.getInspectionId(), judge);
        quality = qualityInspectionMapper.selectById(quality.getInspectionId());
        quality.setReviewStatus(com.jjx.production.enums.QualityReviewStatusEnum.APPROVED.getCode());
        quality.setReviewRemark(review == null ? null : review.getRemark());
        quality.setReviewerId(review == null ? SecurityUtils.getUserId() : review.getApproverId());
        quality.setReviewerName(review == null || review.getApproverName() == null
                ? SecurityUtils.getUsername() : review.getApproverName());
        quality.setReviewTime(LocalDateTime.now());
        qualityInspectionMapper.updateById(quality);
        updateInboundReviewStatus(item.getInboundId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rejectInspectionItem(Long itemId,
            com.jjx.inventory.dto.save.InboundInspectionReviewDTO review) {
        if (review == null || org.apache.commons.lang3.StringUtils.isBlank(review.getRemark())) {
            throw new BusinessException("驳回时必须填写审核意见");
        }
        InventoryInboundItem item = inboundItemMapper.selectById(itemId);
        if (item == null || item.getInspectionId() == null) throw new BusinessException("入库明细尚未提交 IQC 检验");
        com.jjx.production.domain.entity.ProductionQualityInspection quality = qualityInspectionMapper.selectById(item.getInspectionId());
        if (quality == null || !com.jjx.production.enums.QualityReviewStatusEnum.PENDING.getCode().equals(quality.getReviewStatus())) {
            throw new BusinessException("仅待审核的 IQC 记录可以驳回");
        }
        quality.setReviewStatus(com.jjx.production.enums.QualityReviewStatusEnum.REJECTED.getCode());
        quality.setReviewRemark(review.getRemark());
        quality.setReviewerId(review.getApproverId());
        quality.setReviewerName(review.getApproverName());
        quality.setReviewTime(LocalDateTime.now());
        qualityInspectionMapper.updateById(quality);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reinspectItem(Long itemId) {
        InventoryInboundItem item = inboundItemMapper.selectById(itemId);
        if (item == null || item.getInspectionId() == null) throw new BusinessException("入库明细尚无可复检的 IQC 记录");
        com.jjx.production.domain.entity.ProductionQualityInspection old = qualityInspectionMapper.selectById(item.getInspectionId());
        if (old == null || !com.jjx.production.enums.QualityReviewStatusEnum.APPROVED.getCode().equals(old.getReviewStatus())) {
            throw new BusinessException("只有已审核的 IQC 记录可以发起复检");
        }
        Long newId = qualityActionService.reinspect(old.getInspectionId());
        com.jjx.production.domain.entity.ProductionQualityInspection fresh = qualityInspectionMapper.selectById(newId);
        fresh.setReviewStatus(com.jjx.production.enums.QualityReviewStatusEnum.DRAFT.getCode());
        qualityInspectionMapper.updateById(fresh);
        item.setInspectionId(newId);
        inboundItemMapper.updateById(item);
        InventoryInboundOrder order = inboundOrderMapper.selectById(item.getInboundId());
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.updateById(order);
        return newId;
    }

    /**
     * 全部行审核通过即建隔离台账，使不合格品处置与确认入库动作解耦。
     */
    private void updateInboundReviewStatus(Long inboundId) {
        List<InventoryInboundItem> items = inboundItemMapper.selectByInboundId(inboundId);
        boolean allApproved = !items.isEmpty() && items.stream().allMatch(item -> {
            if (item.getInspectionId() == null) return false;
            com.jjx.production.domain.entity.ProductionQualityInspection quality = qualityInspectionMapper.selectById(item.getInspectionId());
            return quality != null && com.jjx.production.enums.QualityReviewStatusEnum.APPROVED.getCode().equals(quality.getReviewStatus());
        });
        if (allApproved) {
            InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
            order.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());
            inboundOrderMapper.updateById(order);
            createIqcQuarantine(order, SecurityUtils.getUserId(), SecurityUtils.getUsername());
        }
    }

    private boolean isPurchaseInbound(InventoryInboundOrder order) {
        return order.getSourceId() != null
                && ("PURCHASE".equalsIgnoreCase(order.getSourceType())
                || "PURCHASE_ORDER".equalsIgnoreCase(order.getSourceType())
                || "PURCHASE".equalsIgnoreCase(order.getInboundType()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.approved", bizId = "#inboundId", bizType = "'inventory'")
    public boolean approve(Long inboundId, Long approverId, String approverName, String remark) {
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        if (!InventoryOrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())) {
            log.error("入库单状态不正确，无法审批: inboundId={}, status={}", inboundId, order.getOrderStatus());
            return false;
        }

        if (isPurchaseInbound(order) && order.getInspectionResult() == null) {
            throw new BusinessException("采购入库单尚未完成来料检验，不能审批");
        }

        if (isPurchaseInbound(order)) {
            throw new BusinessException("采购入库请使用单项 IQC 审核；库存过账将在确认入库阶段执行");
        }

        if (isPurchaseInbound(order)) {
            List<InventoryInboundItem> inspectionItems = inboundItemMapper.selectByInboundId(inboundId);
            for (InventoryInboundItem item : inspectionItems) {
                if (item.getInspectionId() == null || item.getInspectionResult() == null) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "尚未完成来料检验");
                }
                com.jjx.production.domain.dto.QualityJudgeDTO judge =
                        new com.jjx.production.domain.dto.QualityJudgeDTO();
                judge.setResult("PASS".equalsIgnoreCase(item.getInspectionResult())
                        ? com.jjx.production.enums.QualityInspectionResultEnum.PASS.getCode()
                        : com.jjx.production.enums.QualityInspectionResultEnum.FAIL.getCode());
                judge.setTotalQty(item.getSampledQuantity());
                judge.setPassQty(item.getQualifiedQuantity());
                judge.setFailQty(item.getRejectedQuantity());
                judge.setDefectDesc(item.getRejectReason());
                judge.setRemark(remark);
                qualityActionService.judge(item.getInspectionId(), judge);
                com.jjx.production.domain.entity.ProductionQualityInspection quality =
                        qualityInspectionMapper.selectById(item.getInspectionId());
                quality.setReviewerId(approverId);
                quality.setReviewerName(approverName);
                quality.setReviewTime(LocalDateTime.now());
                qualityInspectionMapper.updateById(quality);
            }
        }

        // 审批只记录审批结果，不过账；库存过账的唯一入口是 confirm()。
        order.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());
        order.setApproverId(approverId);
        order.setApproverName(approverName);
        order.setApproveTime(LocalDateTime.now());
        order.setApproveRemark(remark);
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.rejected", bizId = "#inboundId", bizType = "'inventory'")
    public boolean reject(Long inboundId, Long approverId, String approverName, String remark) {
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }


        if (isPurchaseInbound(order)) {
            throw new BusinessException("采购入库请在 IQC 审核窗口逐项驳回");
        }

        if (!InventoryOrderStatusEnum.PENDING.getValue().equals(order.getOrderStatus())) {
            log.error("入库单状态不正确，无法驳回: inboundId={}, status={}", inboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(InventoryOrderStatusEnum.REJECTED.getValue());
        order.setRemark(remark);
        return inboundOrderMapper.updateById(order) > 0;
    }

    /**
     * 执行入库加库存（DEV-651：库存操作统一由 confirm 调用）
     * 原 approve 中的加库存逻辑抽取，供 confirm 直接使用
     */
    private void addStock(InventoryInboundOrder order, Long operatorId, String operatorName, String remark) {
        List<InventoryInboundItem> items = inboundItemMapper.selectByInboundId(order.getInboundId());
        for (InventoryInboundItem item : items) {
            if (item.getInventoryItemId() == null) {
                InventoryMaterial material = inventoryMaterialMapper.selectById(item.getMaterialId());
                if (material == null) {
                    throw new BusinessException("入库明细缺少有效的库存物品身份");
                }
                item.setInventoryItemId(inventoryItemService.ensure(
                        InventoryItemTypeEnum.MATERIAL, material.getMaterialId(), material.getMaterialCode(),
                        material.getMaterialName(), material.getSpecification(), material.getUnit()).getInventoryItemId());
            }
            BigDecimal targetQuantity = isPurchaseInbound(order)
                    ? Objects.requireNonNullElse(item.getAcceptedQuantity(), BigDecimal.ZERO)
                    : item.getQuantity();
            BigDecimal postedQuantity = Objects.requireNonNullElse(item.getPostedQuantity(), BigDecimal.ZERO);
            BigDecimal quantityToPost = targetQuantity == null ? BigDecimal.ZERO : targetQuantity.subtract(postedQuantity);
            if (quantityToPost.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 查找现有批次库存
            LambdaQueryWrapper<InventoryStockItem> wrapper = new LambdaQueryWrapper<InventoryStockItem>()
                    .eq(InventoryStockItem::getInventoryItemId, item.getInventoryItemId())
                    .eq(InventoryStockItem::getBatchNo, item.getBatchNo())
                    .eq(InventoryStockItem::getStatus, 1);
            if (item.getLocationId() != null) {
                wrapper.eq(InventoryStockItem::getLocationId, item.getLocationId());
            }
            InventoryStockItem existing = stockItemMapper.selectOne(wrapper);

            if (existing != null) {
                // 已有批次，增加数量
                existing.setQuantity(existing.getQuantity().add(quantityToPost));
                existing.setLastInboundTime(LocalDateTime.now());
                stockItemMapper.updateById(existing);
            } else {
                // 新建批次记录
                InventoryStockItem newItem = new InventoryStockItem();
                newItem.setInventoryItemId(item.getInventoryItemId());
                newItem.setMaterialId(item.getMaterialId());
                newItem.setMaterialCode(item.getMaterialCode());
                newItem.setMaterialName(item.getMaterialName());
                newItem.setWarehouseId(order.getWarehouseId());
                newItem.setLocationId(item.getLocationId());
                newItem.setBatchNo(item.getBatchNo());
                newItem.setProductionDate(item.getProductionDate());
                newItem.setExpiryDate(item.getExpiryDate());
                newItem.setQuantity(quantityToPost);
                newItem.setReservedQuantity(BigDecimal.ZERO);
                newItem.setUnitCost(item.getUnitPrice());
                newItem.setStatus(1);
                newItem.setLastInboundTime(LocalDateTime.now());
                stockItemMapper.insert(newItem);
            }
            item.setPostedQuantity(postedQuantity.add(quantityToPost));
            inboundItemMapper.updateById(item);

            // 刷新库存汇总
            stockMapper.refreshSummaryByInventoryItemId(item.getInventoryItemId());

            // 记录流水（DEV-651 补：before/after 为 NOT NULL，入库=加库存，before=当前汇总-本次数量）
            java.math.BigDecimal currentTotal = java.math.BigDecimal.ZERO;
            InventoryStock cur = stockMapper.selectByInventoryItemId(item.getInventoryItemId());
            if (cur != null && cur.getTotalQuantity() != null) {
                currentTotal = cur.getTotalQuantity();
            }
            InventoryTransaction tx = new InventoryTransaction();
            tx.setInventoryItemId(item.getInventoryItemId());
            tx.setMaterialId(item.getMaterialId());
            tx.setMaterialCode(item.getMaterialCode());
            tx.setMaterialName(item.getMaterialName());
            tx.setWarehouseId(order.getWarehouseId());
            tx.setLocationId(item.getLocationId());
            tx.setTransactionType("INBOUND");
            tx.setSourceType(order.getSourceType() != null ? order.getSourceType() : "PURCHASE");
            tx.setSourceId(order.getInboundId());
            tx.setSourceNo(order.getInboundNo());
            tx.setBatchNo(item.getBatchNo());
            tx.setQuantity(quantityToPost);
            tx.setBeforeQuantity(currentTotal.subtract(quantityToPost));
            tx.setAfterQuantity(currentTotal);
            tx.setUnitCost(item.getUnitPrice());
            tx.setAmount(item.getUnitPrice() == null ? null : item.getUnitPrice().multiply(quantityToPost));
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(operatorId != null ? operatorId : SecurityUtils.getUserId());
            tx.setOperatorName(operatorName != null ? operatorName : SecurityUtils.getUsername());
            tx.setRemark(remark != null ? remark : "入库确认");
            transactionMapper.insert(tx);
        }
    }

    private void writebackProducedQuantity(InventoryInboundOrder order, BigDecimal postedQty) {
        try {
            ProductionOrder productionOrder = productionOrderMapper.selectById(order.getSourceId());
            if (productionOrder == null || productionOrder.getSalesOrderId() == null) {
                return;
            }
            com.jjx.sales.domain.entity.SalesOrder salesOrder =
                    salesOrderMapper.selectById(productionOrder.getSalesOrderId());
            if (salesOrder == null) {
                return;
            }
            int produced = salesOrder.getProducedQuantity() != null ? salesOrder.getProducedQuantity() : 0;
            salesOrder.setProducedQuantity(produced + postedQty.intValue());
            salesOrderMapper.updateById(salesOrder);
            log.info("完工入库回写订单 produced_quantity: orderId={}, 本次+{}，累计={}",
                    productionOrder.getSalesOrderId(), postedQty, salesOrder.getProducedQuantity());
        } catch (Exception e) {
            log.warn("完工入库回写订单 produced_quantity 失败（不影响入库主流程）: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.created_from_purchase", bizId = "#purchaseOrderId", bizType = "'inventory'")
    public Long createFromPurchase(Long purchaseOrderId) {
        log.info("从采购订单创建入库单: purchaseOrderId={}", purchaseOrderId);

        // 1. 查询采购订单
        PurchaseOrder po = purchaseOrderMapper.selectById(purchaseOrderId);
        if (po == null) {
            throw new BusinessException("采购订单不存在: " + purchaseOrderId);
        }

        // 2. 检查是否已生成入库单
        // 保留采购单/收货次数与批次号的可读追踪关系；这里只去重 PO 前缀，不改为无业务含义的随机序列。
        String inboundNo = buildPurchaseInboundNo(po.getOrderNo());
        LambdaQueryWrapper<InventoryInboundOrder> existCheck = new LambdaQueryWrapper<InventoryInboundOrder>()
                .eq(InventoryInboundOrder::getInboundNo, inboundNo);
        if (inboundOrderMapper.selectCount(existCheck) > 0) {
            log.warn("采购订单{}的入库单已存在", purchaseOrderId);
            return null;
        }

        // 3. 查询采购订单明细
        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectItemsByOrderId(purchaseOrderId);
        if (items.isEmpty()) {
            throw new BusinessException("采购订单无物料明细");
        }

        // 4. 创建入库单
        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundNo(inboundNo);
        order.setInboundType("PURCHASE");
        order.setSourceType("PURCHASE");
        order.setSourceId(purchaseOrderId);
        order.setSourceNo(po.getOrderNo());
        order.setTraceId(po.getTraceId()); // 链路追踪（DEV-568）：采购到货→入库单继承
        List<BigDecimal> receiveQtys = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            BigDecimal receiveQty = item.getQuantity();
            if (receiveQty != null && item.getReceivedQuantity() != null) {
                receiveQty = receiveQty.subtract(item.getReceivedQuantity());
            }
            receiveQtys.add(receiveQty);
        }
        order.setWarehouseId(resolvePurchaseWarehouseId(items, receiveQtys));
        order.setInboundDate(LocalDate.now());
        order.setOrderStatus(InventoryOrderStatusEnum.DRAFT.getValue());
        // 供应商/创建人从采购单带过来，避免列表页数据空白
        order.setSupplierId(po.getSupplierId());
        order.setSupplierName(po.getSupplierName());
        try {
            order.setCreateBy(com.jjx.system.utils.SecurityUtils.getUsername());
        } catch (Exception ignore) { }
        inboundOrderMapper.insert(order);

        // 5. 创建入库单明细
        int sort = 1;
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (PurchaseOrderItem item : items) {
            if (item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal receiveQty = item.getQuantity(); // 按采购数量入库
            if (item.getReceivedQuantity() != null) {
                receiveQty = receiveQty.subtract(item.getReceivedQuantity());
            }
            if (receiveQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            InventoryInboundItem inboundItem = new InventoryInboundItem();
            inboundItem.setInboundId(order.getInboundId());
            inboundItem.setMaterialId(item.getMaterialId());
            inboundItem.setMaterialCode(item.getMaterialCode());
            inboundItem.setMaterialName(item.getMaterialName());
            inboundItem.setQuantity(receiveQty);
            inboundItem.setUnitPrice(item.getUnitPrice());
            inboundItem.setAmount(item.getAmount());
            inboundItem.setBatchNo(order.getInboundNo() + "-" + sort); // 批次号=入库单号-行序号（2026-08-11 修复：原 PO-单号-行序号 在多凭证时重复，凭证↔批次断链）
            inboundItem.setSortOrder(sort++);
            inboundItemMapper.insert(inboundItem);
            totalQty = totalQty.add(receiveQty);
            if (item.getAmount() != null) {
                totalAmt = totalAmt.add(item.getAmount());
            }

            // 更新采购订单已收数量
            purchaseOrderItemMapper.updateReceivedQuantity(item.getItemId(), receiveQty);
        }

        // 主表汇总字段补全
        order.setTotalQuantity(totalQty);
        order.setTotalAmount(totalAmt);
        inboundOrderMapper.updateById(order);

        // 6. 创建后进入待检验（复用 PENDING，inspection_result 为空）；不自动审批、不入库存。
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.updateById(order);

        try { eventPublisher.fire("purchase.arrived", Map.of("sourceNo", order.getSourceNo(), "inboundId", String.valueOf(order.getInboundId()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("采购入库完成: purchaseOrderId={}, inboundId={}", purchaseOrderId, order.getInboundId());
        return order.getInboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInboundRecordFromPurchase(Long purchaseOrderId) {
        log.info("采购收货自动生成入库单: purchaseOrderId={}", purchaseOrderId);
        PurchaseOrder po = purchaseOrderMapper.selectById(purchaseOrderId);
        if (po == null) {
            log.warn("采购订单不存在，跳过自动入库: {}", purchaseOrderId);
            return null;
        }
        // 2026-08-18：每次收货生成独立入库单（批次），不再删除重建——
        // 采购单详情「入库凭证」按时间线区分每次收货（第1次收500、第2次收500各自一张单）
        // 入库单号同时是批次号前缀，且需关联采购单及收货次数；不改随机序列，仅避免 PO-PO- 双前缀。
        String baseInboundNo = buildPurchaseInboundNo(po.getOrderNo());
        List<InventoryInboundOrder> existingList = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryInboundOrder>().likeRight(InventoryInboundOrder::getInboundNo, baseInboundNo));
        // 已生成入库单明细量（含待确认——待确认单也占用了收货量，防下一张重复入；驳回/删除后自动重新计入）
        Map<Long, BigDecimal> alreadyInByMaterial = new HashMap<>();
        for (InventoryInboundOrder done : existingList) {
            List<InventoryInboundItem> doneItems = inboundItemMapper.selectByInboundId(done.getInboundId());
            for (InventoryInboundItem di : doneItems) {
                if (di.getMaterialId() == null || di.getQuantity() == null) continue;
                alreadyInByMaterial.merge(di.getMaterialId(), di.getQuantity(), BigDecimal::add);
            }
        }

        List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectItemsByOrderId(purchaseOrderId);
        if (items.isEmpty()) {
            log.warn("采购订单无明细，跳过自动入库: {}", purchaseOrderId);
            return null;
        }

        // 计算各明细未入库数量（本次已收 - 已完成已入）
        List<PurchaseOrderItem> toInItems = new ArrayList<>();
        List<BigDecimal> toInQtys = new ArrayList<>();
        for (PurchaseOrderItem item : items) {
            if (item.getReceivedQuantity() == null || item.getReceivedQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal alreadyIn = alreadyInByMaterial.getOrDefault(item.getMaterialId(), BigDecimal.ZERO);
            BigDecimal toIn = item.getReceivedQuantity().subtract(alreadyIn);
            if (toIn.compareTo(BigDecimal.ZERO) <= 0) continue;
            toInItems.add(item);
            toInQtys.add(toIn);
        }
        if (toInItems.isEmpty()) {
            log.info("采购订单{} 无待入库数量，跳过: {}", purchaseOrderId, baseInboundNo);
            return null;
        }

        // 每次收货新建一张入库单（序号递增 PO-xxx、PO-xxx-2、PO-xxx-3…）；状态=待审批，仓库确认后才加库存（2026-08-11 业务定稿：收货≠入库）
        final InventoryInboundOrder order;
        String inboundNo = existingList.isEmpty() ? baseInboundNo : baseInboundNo + "-" + (existingList.size() + 1);
        order = new InventoryInboundOrder();
        order.setInboundNo(inboundNo);
        order.setInboundType("PURCHASE");
        order.setSourceType("PURCHASE");
        order.setSourceId(purchaseOrderId);
        order.setSourceNo(po.getOrderNo());
        order.setTraceId(po.getTraceId());
        order.setWarehouseId(resolvePurchaseWarehouseId(toInItems, toInQtys));
        order.setInboundDate(LocalDate.now());
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue()); // 同一待审批态：inspection_result 为空时表示待检验
        order.setRemark("采购收货自动入库（DEV-624）批次" + existingList.size());
        // 供应商/创建人从采购单带过来，避免列表页数据空白
        order.setSupplierId(po.getSupplierId());
        order.setSupplierName(po.getSupplierName());
        try {
            order.setCreateBy(com.jjx.system.utils.SecurityUtils.getUsername());
        } catch (Exception ignore) { }
        inboundOrderMapper.insert(order);
        log.info("采购订单{} 新建收货入库单 {}", purchaseOrderId, inboundNo);

        // 重建明细=未入库数量
        int sort = 1;
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (int i = 0; i < toInItems.size(); i++) {
            PurchaseOrderItem item = toInItems.get(i);
            BigDecimal toIn = toInQtys.get(i);
            InventoryInboundItem inboundItem = new InventoryInboundItem();
            inboundItem.setInboundId(order.getInboundId());
            inboundItem.setMaterialId(item.getMaterialId());
            inboundItem.setMaterialCode(item.getMaterialCode());
            inboundItem.setMaterialName(item.getMaterialName());
            inboundItem.setQuantity(toIn);
            inboundItem.setUnitPrice(item.getUnitPrice());
            BigDecimal itemAmt = (item.getAmount() == null || item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) == 0)
                    ? null
                    : item.getAmount().multiply(toIn.divide(item.getQuantity(), 4, java.math.RoundingMode.HALF_UP));
            inboundItem.setAmount(itemAmt);
            inboundItem.setBatchNo(order.getInboundNo() + "-" + sort); // 批次号=入库单号-行序号（2026-08-11 修复：原 PO-单号-行序号 在多凭证时重复，凭证↔批次断链）
            inboundItem.setSortOrder(sort++);
            inboundItemMapper.insert(inboundItem);
            totalQty = totalQty.add(toIn);
            if (itemAmt != null) {
                totalAmt = totalAmt.add(itemAmt);
            }
        }
        // 主表汇总字段补全（列表页/详情页展示用）
        order.setTotalQuantity(totalQty);
        order.setTotalAmount(totalAmt);
        inboundOrderMapper.updateById(order);
        try { eventPublisher.fire("purchase.arrived", Map.of("sourceNo", order.getSourceNo(), "inboundId", String.valueOf(order.getInboundId()))); } catch (Exception e) { log.warn("联动失败: {}", e.getMessage()); }
        log.info("采购收货自动入库单生成/更新: purchaseOrderId={}, inboundId={}, qty={}", purchaseOrderId, order.getInboundId(), totalQty);
        return order.getInboundId();
    }

    private String buildPurchaseInboundNo(String purchaseOrderNo) {
        return purchaseOrderNo != null && purchaseOrderNo.startsWith("PO")
                ? purchaseOrderNo
                : "PO-" + purchaseOrderNo;
    }

    /**
     * 按待入库数量决定采购入库单默认仓：R 原料与 F 成品分别汇总，数量相同按 R 优先。
     * 仓库表没有原料专用 warehouse_type，因此 R 按启用仓名称“原料”匹配；F 优先按 finished 类型，
     * 再按名称“成品”匹配。查询异常、物料类型无法判定或目标仓不存在时均回退历史默认仓 1L。
     */
    private Long resolvePurchaseWarehouseId(List<PurchaseOrderItem> items, List<BigDecimal> quantities) {
        final Long fallbackWarehouseId = 1L;
        try {
            List<Long> materialIds = items.stream()
                    .map(PurchaseOrderItem::getMaterialId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (materialIds.isEmpty()) {
                return fallbackWarehouseId;
            }

            Map<Long, String> materialTypes = inventoryMaterialMapper.selectBatchIds(materialIds).stream()
                    .collect(Collectors.toMap(InventoryMaterial::getMaterialId, InventoryMaterial::getMaterialType));
            BigDecimal rawQuantity = BigDecimal.ZERO;
            BigDecimal finishedQuantity = BigDecimal.ZERO;
            for (int i = 0; i < items.size(); i++) {
                BigDecimal quantity = i < quantities.size() ? quantities.get(i) : null;
                if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) continue;
                String materialType = materialTypes.get(items.get(i).getMaterialId());
                if (MaterialEnums.Type.RAW.getValue().equals(materialType)) {
                    rawQuantity = rawQuantity.add(quantity);
                } else if (MaterialEnums.Type.FINISHED.getValue().equals(materialType)) {
                    finishedQuantity = finishedQuantity.add(quantity);
                }
            }
            if (rawQuantity.signum() == 0 && finishedQuantity.signum() == 0) {
                return fallbackWarehouseId;
            }

            boolean useRawWarehouse = rawQuantity.compareTo(finishedQuantity) >= 0;
            List<InventoryWarehouse> enabledWarehouses = warehouseMapper.selectAllEnabled();
            InventoryWarehouse matched = enabledWarehouses.stream()
                    .filter(warehouse -> useRawWarehouse
                            ? warehouse.getWarehouseName() != null && warehouse.getWarehouseName().contains("原料")
                            : "finished".equalsIgnoreCase(warehouse.getWarehouseType()))
                    .findFirst()
                    .orElse(null);
            if (!useRawWarehouse && matched == null) {
                matched = enabledWarehouses.stream()
                        .filter(warehouse -> warehouse.getWarehouseName() != null
                                && warehouse.getWarehouseName().contains("成品"))
                        .findFirst()
                        .orElse(null);
            }
            return matched != null ? matched.getWarehouseId() : fallbackWarehouseId;
        } catch (Exception e) {
            log.warn("采购入库默认仓映射失败，回退仓库{}: {}", fallbackWarehouseId, e.getMessage());
            return fallbackWarehouseId;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "inventory.inbound.created_from_production", bizId = "#workOrderId", bizType = "'inventory'")
    public Long createFromProduction(Long workOrderId) {
        return createFromProduction(workOrderId, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromProduction(Long workOrderId, Long inspectionId, BigDecimal inspectedPassQty) {
        log.info("从生产工单创建入库单: workOrderId={}", workOrderId);

        // 1. 查询生产工单
        ProductionOrder prodOrder = productionOrderMapper.selectById(workOrderId);
        if (prodOrder == null) {
            throw new BusinessException("生产工单不存在: " + workOrderId);
        }

        // DEV-936（2026-08-12）：工单未完工禁止生成完工入库单（与 DEV-053 完工质检门一致），
        // 否则 finishedQuantity=0 导致入库数量记 0、库存不入账
        if (inspectionId == null && !com.jjx.production.enums.ProductionOrderStatusEnum.COMPLETED.getValue().equals(prodOrder.getOrderStatus())) {
            String statusName = "状态码" + prodOrder.getOrderStatus();
            try {
                var pe = com.jjx.production.enums.ProductionOrderStatusEnum.getByValue(prodOrder.getOrderStatus());
                statusName = pe.getLabel();
            } catch (Exception ignored) {}
            throw new BusinessException("工单未完工，不能生成完工入库单（当前状态：" + statusName + "）");
        }

        // 2. 创建入库单
        if (inspectionId == null) {
            Long partialCount = inboundOrderMapper.selectCount(
                    new LambdaQueryWrapper<InventoryInboundOrder>()
                            .eq(InventoryInboundOrder::getSourceType, "PRODUCTION")
                            .eq(InventoryInboundOrder::getSourceId, workOrderId)
                            .likeRight(InventoryInboundOrder::getInboundNo, "FINISH-" + prodOrder.getOrderNo() + "-FQC-"));
            if (partialCount != null && partialCount > 0) {
                log.info("工单{}已按FQC分批入库，完工时不再重复入库", workOrderId);
                return null;
            }
        }
        String inboundNo = inspectionId == null
                ? "FINISH-" + prodOrder.getOrderNo()
                : "FINISH-" + prodOrder.getOrderNo() + "-FQC-" + inspectionId;
        LambdaQueryWrapper<InventoryInboundOrder> existCheck = new LambdaQueryWrapper<InventoryInboundOrder>()
                .eq(InventoryInboundOrder::getInboundNo, inboundNo);
        if (inboundOrderMapper.selectCount(existCheck) > 0) {
            log.warn("生产工单{}的完工入库单已存在", workOrderId);
            return null;
        }

        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundNo(inboundNo);
        order.setInboundType("PRODUCTION_FINISH");
        order.setSourceType("PRODUCTION");
        order.setSourceId(workOrderId);
        order.setSourceNo(prodOrder.getOrderNo());
        order.setTraceId(prodOrder.getTraceId()); // 链路追踪（DEV-568）：工单→完工入库单继承
        order.setInboundDate(LocalDate.now());
        // DEV-679：工单无仓库字段，默认取第一个启用仓库（与出库侧 createFromProduction 一致）
        try {
            InventoryWarehouse defaultWh = warehouseMapper.selectOne(
                    new LambdaQueryWrapper<InventoryWarehouse>()
                            .eq(InventoryWarehouse::getStatus, 1)
                            .orderByAsc(InventoryWarehouse::getWarehouseId)
                            .last("LIMIT 1"));
            if (defaultWh != null) {
                order.setWarehouseId(defaultWh.getWarehouseId());
            }
        } catch (Exception e) {
            log.warn("获取默认仓库失败: {}", e.getMessage());
        }
        order.setOrderStatus(InventoryOrderStatusEnum.DRAFT.getValue());
        inboundOrderMapper.insert(order);

        // 3. 创建入库明细（DEV-579：物料=成品物料档案 F类型，产品ID→物料ID映射）
        InventoryInboundItem inboundItem = new InventoryInboundItem();
        inboundItem.setInboundId(order.getInboundId());
        // 通过产品ID查成品物料档案（material_type=F），无档案则回退用产品ID（兼容旧数据）
        String materialCode = prodOrder.getProductCode();
        String materialName = prodOrder.getProductName();
        inboundItem.setInventoryItemId(inventoryItemService.ensure(
                InventoryItemTypeEnum.PRODUCT, prodOrder.getProductId(), materialCode,
                materialName, null, "PCS").getInventoryItemId());
        inboundItem.setMaterialCode(materialCode);
        inboundItem.setMaterialName(materialName);
        // 068定稿：入库产品数量=最后一道工序/完工检验合格数（052口径 finishedQuantity，非工序汇总 completedQuantity）
        BigDecimal inboundQty = inspectedPassQty != null ? inspectedPassQty
                : (prodOrder.getFinishedQuantity() != null && prodOrder.getFinishedQuantity().compareTo(BigDecimal.ZERO) > 0)
                ? prodOrder.getFinishedQuantity()
                : (prodOrder.getCompletedQuantity() != null ? prodOrder.getCompletedQuantity() : prodOrder.getPlannedQuantity());
        inboundItem.setQuantity(inboundQty);
        inboundItem.setBatchNo("BATCH-" + prodOrder.getOrderNo());
        inboundItem.setSortOrder(1);
        inboundItemMapper.insert(inboundItem);

        // 4. 提交审批，待人工确认后过账
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.updateById(order);

        log.info("生产完工入库完成: workOrderId={}, inboundId={}", workOrderId, order.getInboundId());
        return order.getInboundId();
    }

    @Override
    public List<InboundVO> getPendingApproval() {
        List<InventoryInboundOrder> orders = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryInboundOrder>()
                        .eq(InventoryInboundOrder::getOrderStatus, InventoryOrderStatusEnum.PENDING.getValue())
                        .orderByAsc(InventoryInboundOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public List<InboundVO> getByDateRange(String startDate, String endDate) {
        LambdaQueryWrapper<InventoryInboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryInboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryInboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryInboundOrder::getCreateTime);

        List<InventoryInboundOrder> orders = inboundOrderMapper.selectList(wrapper);
        return convertToVOList(orders);
    }

    @Override
    public InboundVO getBySource(String sourceType, Long sourceId) {
        LambdaQueryWrapper<InventoryInboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryInboundOrder::getSourceType, sourceType)
                .eq(InventoryInboundOrder::getSourceId, sourceId);
        InventoryInboundOrder order = inboundOrderMapper.selectOne(wrapper);
        return convertToVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long inboundId, Integer status) {
        // DEV-651 方案A：行锁
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) {
            log.error("入库单不存在: inboundId={}", inboundId);
            return false;
        }

        order.setOrderStatus(status);
        return inboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public IPage<InventoryInboundOrder> pageQuery(Map<String, Object> params) {
        String inboundNo = (String) params.get("inboundNo");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryInboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (inboundNo != null && !inboundNo.isEmpty()) {
            wrapper.like(InventoryInboundOrder::getInboundNo, inboundNo);
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryInboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryInboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryInboundOrder::getCreateTime);

        Page<InventoryInboundOrder> page = new Page<>(pageNum, pageSize);
        return inboundOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Map<String, Object> params) {
        if (params != null && params.get("inboundId") != null) {
            Long inboundId = Long.valueOf(params.get("inboundId").toString());
            InboundVO detail = getDetail(inboundId);
            if (detail != null) {
                return Map.of("code", 200, "data", detail);
            }
        }
        return Map.of("code", 404, "message", "入库单不存在");
    }

    private List<InboundVO> convertToVOList(List<InventoryInboundOrder> orders) {
        List<InboundVO> result = new ArrayList<>();
        for (InventoryInboundOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private InboundVO convertToVO(InventoryInboundOrder order) {
        if (order == null) {
            return null;
        }

        InboundVO vo = new InboundVO();
        BeanUtils.copyProperties(order, vo);
        // 状态码/名称（前端展示用，与实体 Integer 字段对齐）
        vo.setStatus(order.getOrderStatus());
        vo.setStatusName(com.jjx.inventory.enums.InventoryOrderStatusEnum.getByValue(order.getOrderStatus()) != null
                ? com.jjx.inventory.enums.InventoryOrderStatusEnum.getByValue(order.getOrderStatus()).getLabel() : null);
        // 入库类型名称
        vo.setInboundTypeName(inboundTypeName(order.getInboundType()));
        // 仓库名称
        if (order.getWarehouseId() != null) {
            try {
                InventoryWarehouse wh = warehouseMapper.selectById(order.getWarehouseId());
                if (wh != null) {
                    vo.setWarehouseName(wh.getWarehouseName());
                }
            } catch (Exception ignore) { }
        }
        // 审核状态名称（approve_status 为未维护的死字段，2026-08-11 起不再使用，统一以 order_status 为准）
        return vo;
    }

    /** 入库类型显示名 */
    private static String inboundTypeName(String inboundType) {
        if (inboundType == null) return null;
        return switch (inboundType) {
            case "PURCHASE" -> "采购入库";
            case "PRODUCTION_FINISH" -> "生产入库";
            case "RETURN" -> "退货入库";
            case "TRANSFER" -> "调拨入库";
            case "OTHER" -> "其他入库";
            default -> inboundType;
        };
    }

    private static List<InboundItemVO> convertToItemVOList(List<InventoryInboundItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        List<InboundItemVO> result = new ArrayList<>();
        for (InventoryInboundItem item : items) {
            result.add(convertToItemVO(item));
        }
        return result;
    }

    private static InboundItemVO convertToItemVO(InventoryInboundItem item) {
        if (item == null) {
            return null;
        }

        InboundItemVO vo = new InboundItemVO();
        vo.setInboundItemId(item.getItemId());
        vo.setInboundId(item.getInboundId());
        vo.setMaterialId(item.getMaterialId());
        vo.setMaterialCode(item.getMaterialCode());
        vo.setMaterialName(item.getMaterialName());
        vo.setSpecification(item.getSpecification());
        vo.setUnit(item.getUnit());
        vo.setQuantity(item.getQuantity());
        vo.setSampledQuantity(item.getSampledQuantity());
        vo.setInspectionId(item.getInspectionId());
        vo.setInspectionResult(item.getInspectionResult());
        vo.setDisposition(item.getDisposition());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setAmount(item.getAmount());
        vo.setBatchNo(item.getBatchNo());
        vo.setProductionDate(item.getProductionDate());
        vo.setExpiryDate(item.getExpiryDate());
        vo.setLocationId(item.getLocationId());
        vo.setQualifiedQuantity(item.getQualifiedQuantity());
        vo.setRejectedQuantity(item.getRejectedQuantity());
        vo.setAcceptedQuantity(item.getAcceptedQuantity());
        vo.setPostedQuantity(item.getPostedQuantity());
        vo.setRejectReason(item.getRejectReason());
        vo.setSortOrder(item.getSortOrder());
        vo.setRemark(item.getRemark());

        return vo;
    }

}
