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
import com.jjx.inventory.domain.InventoryIqcBatch;
import com.jjx.inventory.dto.query.InboundQueryDTO;
import com.jjx.inventory.dto.query.IqcPendingQueryDTO;
import com.jjx.inventory.dto.query.IqcQuarantineLedgerQueryDTO;
import com.jjx.inventory.dto.save.InboundInspectionSubmitDTO;
import com.jjx.inventory.dto.vo.InboundItemVO;
import com.jjx.inventory.dto.vo.InboundVO;
import com.jjx.inventory.dto.vo.IqcPendingVO;
import com.jjx.inventory.dto.vo.IqcQuarantineLedgerPageVO;
import com.jjx.inventory.dto.vo.IqcQuarantineLedgerRowVO;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final com.jjx.inventory.service.InventoryStockMutationService stockMutationService;
    private final InventoryMaterialMapper inventoryMaterialMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final EventPublisher eventPublisher;
    private final InventoryAlertService alertService;
    private final InventoryItemService inventoryItemService;
    private final com.jjx.sales.mapper.OrderMapper salesOrderMapper;
    /** 2026-09-21 dev-20260921-039：拒收回库按发货单明细回冲。 */
    private final com.jjx.sales.mapper.SalesDeliveryMapper salesDeliveryMapper;
    private final com.jjx.sales.mapper.SalesDeliveryItemMapper salesDeliveryItemMapper;
    private final com.jjx.inventory.mapper.InventoryIqcQuarantineMapper iqcQuarantineMapper;
    private final com.jjx.inventory.mapper.InventoryIqcDispositionOrderMapper iqcDispositionOrderMapper;
    private final com.jjx.inventory.mapper.InventoryIqcReturnOrderMapper iqcReturnOrderMapper;
    private final com.jjx.inventory.mapper.InventoryIqcReworkOrderMapper iqcReworkOrderMapper;
    private final com.jjx.inventory.mapper.InventoryIqcBatchMapper iqcBatchMapper;
    private final com.jjx.inventory.mapper.InventoryIqcScrapOrderMapper iqcScrapOrderMapper;
    private final RedisSequenceService redisSequenceService;
    /** 完工入库过账后回写 quality_lot.stored_quantity（dev-20260918-022/023）；
     *  用 ObjectProvider 懒取，避免 inventory → quality 的强依赖/循环。 */
    private final org.springframework.beans.factory.ObjectProvider<com.jjx.quality.service.QualityLotService> qualityLotServiceProvider;
    /** IQC 不合格统一写 NCR；延迟获取避免 inventory/quality 构造器循环。 */
    private final org.springframework.beans.factory.ObjectProvider<com.jjx.quality.service.QualityNcrService> qualityNcrServiceProvider;
    private final com.jjx.quality.mapper.QualityLotMapper qualityLotMapper;
    /** dev-20260923-043：返工退料 —— 读补料出库明细（原发料批次）与不良单号 */
    private final com.jjx.inventory.mapper.InventoryOutboundOrderMapper outboundOrderMapper;
    private final com.jjx.inventory.mapper.InventoryOutboundItemMapper outboundItemMapper;
    private final com.jjx.quality.mapper.QualityNcrMapper qualityNcrMapper;

    /**
     * 入库类事件统一发布（2026-09-21 dev-20260921-013 库存批）：
     * 手写 payload，bizNo 取入库单号（原注解 bizId 只能带内部 inboundId）。
     */
    private void publishInboundEvent(String eventCode, Long inboundId) {
        InventoryInboundOrder order = inboundId == null ? null : inboundOrderMapper.selectById(inboundId);
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "inventory", inboundId, order == null ? null : order.getInboundNo());
        if (order != null) {
            payload.put("inboundNo", order.getInboundNo());
            payload.put("bizNo", order.getInboundNo());
            payload.put("sourceNo", order.getSourceNo());
            payload.put("supplierName", order.getSupplierName());
            payload.put("sourceDesc", buildSourceDesc(order));
            payload.put("warehouseId", order.getWarehouseId());
        }
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, eventCode, payload);
    }

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
    public Long create(Map<String, Object> params) {
        log.info("创建入库单: {}", params);
        InventoryInboundOrder order = new InventoryInboundOrder();
        // dev-20260923-029（单号第 5 批）：兜底不再用时间戳拼号，改走号段 IN+yyMMdd+3
        Object inboundNoParam = params.get("inboundNo");
        order.setInboundNo(inboundNoParam == null || String.valueOf(inboundNoParam).isBlank()
                ? nextInboundNo()
                : String.valueOf(inboundNoParam));
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
        publishInboundEvent("inventory.inbound.created", order.getInboundId());
        return order.getInboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        // dev-20260922-020（口径 B：一切以「确认入库」为准）：这里算的是「本次应过账的净差额」，
        // 可为负（复检判少 → 仓库确认时冲减）。正数由 addStock 加库存；负数由 reducePostedStock 冲减。
        BigDecimal netDeltaThisTime = BigDecimal.ZERO;
        if ("PRODUCTION".equals(order.getSourceType())) {
            netDeltaThisTime = inboundItemMapper.selectByInboundId(inboundId).stream()
                    .map(item -> {
                        BigDecimal targetQuantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                        BigDecimal postedQuantity = Objects.requireNonNullElse(item.getPostedQuantity(), BigDecimal.ZERO);
                        return targetQuantity.subtract(postedQuantity);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        addStock(order, operatorId, operatorName, "确认入库");
        if ("PRODUCTION".equals(order.getSourceType())) {
            reducePostedStock(order, operatorId, operatorName, netDeltaThisTime);
            writebackProducedQuantity(order, netDeltaThisTime);
            syncQualityLotStored(order);
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
        boolean confirmed = inboundOrderMapper.updateById(order) > 0;
        if (confirmed) {
            // 2026-09-18：事件改为手写 payload 后置发布，把入库单号/采购单号/供应商/操作人带进通知模板
            //（原 @Event 注解只能带内部 inboundId，通知标题只能显示「内部编号」）
            Map<String, Object> payload = iqcPayload(order, null, null, null);
            payload.put("bizType", "inventory");
            payload.put("operatorName", operatorName);
            publishIqcEventAfterCommit("inventory.inbound.confirmed", payload);
        }
        return confirmed;
    }

    private void validateAllIqcApproved(Long inboundId) {
        for (InventoryInboundItem item : inboundItemMapper.selectByInboundId(inboundId)) {
            com.jjx.quality.domain.entity.QualityLot quality = item.getLotId() == null
                    ? null : qualityLotMapper.selectById(item.getLotId());
            if (quality == null || !"APPROVED".equals(quality.getReviewStatus())) {
                throw new BusinessException("物料" + item.getMaterialCode() + " IQC 尚未审核通过");
            }
        }
    }

    private int createIqcQuarantine(InventoryInboundOrder order, Long operatorId, String operatorName) {
        int createdCount = 0;
        for (InventoryInboundItem item : inboundItemMapper.selectByInboundId(order.getInboundId())) {
            // 2026-09-21（dev-20260921-004）：隔离数量口径 = 该明细当前检验记录的净不良量。
            // 原公式「收货数量 − 允收数量」在复检/返工后必然算错：明细行被改写成子批次、允收量
            // 随之变成子批次数量 → 100−5=95 件良品被当成隔离（PO202609210001-3 实例）。
            BigDecimal quarantineQty = BigDecimal.ZERO;
            if (item.getLotId() != null) {
                com.jjx.quality.domain.entity.QualityLot lot = qualityLotMapper.selectById(item.getLotId());
                if (lot != null && lot.getFailQuantity() != null) {
                    quarantineQty = lot.getFailQuantity();
                }
            }
            if (quarantineQty.signum() <= 0) continue;
            Long count = iqcQuarantineMapper.selectCount(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcQuarantine>()
                    .eq(com.jjx.inventory.domain.InventoryIqcQuarantine::getInboundItemId, item.getItemId())
                    .eq(com.jjx.inventory.domain.InventoryIqcQuarantine::getLotId, item.getLotId()));
            if (count != null && count > 0) continue;
            com.jjx.inventory.domain.InventoryIqcQuarantine quarantine = new com.jjx.inventory.domain.InventoryIqcQuarantine();
            quarantine.setInboundId(order.getInboundId());
            quarantine.setInboundItemId(item.getItemId());
            quarantine.setInspectionId(null);
            quarantine.setLotId(item.getLotId());
            quarantine.setMaterialId(item.getMaterialId());
            quarantine.setMaterialCode(item.getMaterialCode());
            quarantine.setMaterialName(item.getMaterialName());
            quarantine.setBatchNo(item.getBatchNo());
            InventoryIqcBatch batch = ensureOriginalBatch(item);
            quarantine.setIqcBatchId(batch.getBatchId());
            quarantine.setQuantity(quarantineQty);
            quarantine.setRemainingQuantity(quarantineQty);
            quarantine.setDisposition(item.getDisposition());
            quarantine.setStatus(com.jjx.inventory.enums.IqcQuarantineStatusEnum.PENDING.getCode());
            quarantine.setOperatorId(operatorId != null ? operatorId : SecurityUtils.getUserId());
            quarantine.setOperatorName(operatorName != null ? operatorName : SecurityUtils.getDisplayName());
            iqcQuarantineMapper.insert(quarantine);
            createdCount++;

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
            tx.setIqcBatchId(batch.getBatchId());
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
        return createdCount;
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

        com.jjx.quality.service.QualityNcrService ncrService = qualityNcrServiceProvider.getIfAvailable();
        if (ncrService == null) {
            throw new BusinessException("质量不良台账服务不可用，已取消 IQC 处置");
        }
        ncrService.syncIqcDisposition(quarantine.getLotId(), actionCode, quantity,
                action.getOperatorName() != null ? action.getOperatorName() : SecurityUtils.getDisplayName(),
                action.getRemark());

        if (release) addReleasedQuarantineStock(quarantine, quantity, action);
        var dispositionOrder = new com.jjx.inventory.domain.InventoryIqcDispositionOrder();
        dispositionOrder.setDispositionNo(redisSequenceService.generateBusinessNumberByType(
                "iqc_disposition", "IQD", "yyMMdd", 3));
        dispositionOrder.setQuarantineId(quarantine.getQuarantineId());
        dispositionOrder.setInboundId(quarantine.getInboundId());
        dispositionOrder.setInboundItemId(quarantine.getInboundItemId());
        dispositionOrder.setInspectionId(quarantine.getInspectionId());
        dispositionOrder.setLotId(quarantine.getLotId());
        dispositionOrder.setAction(actionCode);
        dispositionOrder.setQuantity(quantity);
        dispositionOrder.setMaterialCode(quarantine.getMaterialCode());
        dispositionOrder.setMaterialName(quarantine.getMaterialName());
        dispositionOrder.setBatchNo(quarantine.getBatchNo());
        dispositionOrder.setIqcBatchId(quarantine.getIqcBatchId());
        dispositionOrder.setRemark(action.getRemark());
        dispositionOrder.setStatus("COMPLETED");
        dispositionOrder.setOperatorId(action.getOperatorId() != null ? action.getOperatorId() : SecurityUtils.getUserId());
        dispositionOrder.setOperatorName(action.getOperatorName() != null ? action.getOperatorName() : SecurityUtils.getDisplayName());
        iqcDispositionOrderMapper.insert(dispositionOrder);
        if ("RETURN".equals(actionCode)) createIqcReturnOrder(quarantine, dispositionOrder, action);
        if ("REWORK".equals(actionCode)) createIqcReworkOrder(quarantine, dispositionOrder, action);
        if ("SCRAP".equals(actionCode)) createIqcScrapOrder(quarantine, dispositionOrder, action);
        quarantine.setRemainingQuantity(quarantine.getRemainingQuantity().subtract(quantity));
        if (quarantine.getRemainingQuantity().signum() == 0) quarantine.setStatus(status);
        iqcQuarantineMapper.updateById(quarantine);

        if (!release) {
            InventoryTransaction tx = new InventoryTransaction();
            InventoryInboundItem inboundItem = inboundItemMapper.selectById(quarantine.getInboundItemId());
            InventoryInboundOrder inboundOrder = inboundOrderMapper.selectById(quarantine.getInboundId());
            tx.setMaterialId(quarantine.getMaterialId()); tx.setMaterialCode(quarantine.getMaterialCode());
            tx.setMaterialName(quarantine.getMaterialName()); tx.setTransactionType("IQC_" + actionCode);
            tx.setWarehouseId(inboundOrder == null ? null : inboundOrder.getWarehouseId());
            tx.setLocationId(inboundItem == null ? null : inboundItem.getLocationId());
            tx.setSourceType("INBOUND_IQC"); tx.setSourceId(quarantine.getInboundId());
            tx.setBatchNo(quarantine.getBatchNo()); tx.setIqcBatchId(quarantine.getIqcBatchId()); tx.setQuantity(quantity);
            tx.setBeforeQuantity(quarantine.getRemainingQuantity().add(quantity));
            tx.setAfterQuantity(quarantine.getRemainingQuantity());
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(action.getOperatorId() != null ? action.getOperatorId() : SecurityUtils.getUserId());
            tx.setOperatorName(action.getOperatorName() != null ? action.getOperatorName() : SecurityUtils.getDisplayName());
            tx.setRemark(action.getRemark()); transactionMapper.insert(tx);
        }
        updateBatchAfterDisposition(quarantine.getIqcBatchId(), actionCode, quantity);
        return true;
    }

    private void updateBatchAfterDisposition(Long batchId, String actionCode, BigDecimal quantity) {
        if (batchId == null || quantity == null) return;
        InventoryIqcBatch batch = iqcBatchMapper.selectById(batchId);
        if (batch == null) return;
        // 2026-09-21：处置量写单独的 disposed_quantity，不再复用 processed_quantity
        //（后者今后只表示「检验/处理量」，由 updateIqcBatchResult 写入，避免一列两义）
        batch.setDisposedQuantity(nvl(batch.getDisposedQuantity()).add(quantity));
        BigDecimal remaining = nvl(batch.getRemainingQuantity()).subtract(quantity).max(BigDecimal.ZERO);
        batch.setRemainingQuantity(remaining);
        if ("SCRAP".equals(actionCode)) {
            batch.setScrappedQuantity(nvl(batch.getScrappedQuantity()).add(quantity));
            batch.setStatus("SCRAPPED");
        } else if ("RETURN".equals(actionCode)) {
            batch.setStatus("RETURNED");
        } else if ("REWORK".equals(actionCode)) {
            batch.setStatus("REWORKED");
        } else if ("RELEASE".equals(actionCode)) {
            batch.setAcceptedQuantity(nvl(batch.getAcceptedQuantity()).add(quantity));
            batch.setStatus("RELEASED");
        }
        iqcBatchMapper.updateById(batch);
    }

    /** 单号是否已被占用（dev-20260923-032 抽成方法，供号段防重循环用）。 */
    private boolean existsInboundNo(String inboundNo) {
        return inboundOrderMapper.selectCount(new LambdaQueryWrapper<InventoryInboundOrder>()
                .eq(InventoryInboundOrder::getInboundNo, inboundNo)) > 0;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void createIqcReturnOrder(com.jjx.inventory.domain.InventoryIqcQuarantine quarantine,
                                      com.jjx.inventory.domain.InventoryIqcDispositionOrder disposition,
                                      com.jjx.inventory.dto.save.IqcQuarantineActionDTO action) {
        var item = inboundItemMapper.selectById(quarantine.getInboundItemId());
        var inbound = inboundOrderMapper.selectById(quarantine.getInboundId());
        var order = new com.jjx.inventory.domain.InventoryIqcReturnOrder();
        order.setReturnNo(redisSequenceService.generateBusinessNumberByType(
                "iqc_return", "IQR", "yyMMdd", 3));
        order.setDispositionId(disposition.getDispositionId()); order.setQuarantineId(quarantine.getQuarantineId());
        order.setInboundId(quarantine.getInboundId()); order.setInboundItemId(quarantine.getInboundItemId());
        order.setInspectionId(quarantine.getInspectionId()); order.setLotId(quarantine.getLotId()); order.setMaterialId(quarantine.getMaterialId());
        order.setMaterialCode(quarantine.getMaterialCode()); order.setMaterialName(quarantine.getMaterialName());
        order.setBatchNo(quarantine.getBatchNo()); order.setIqcBatchId(quarantine.getIqcBatchId()); order.setQuantity(disposition.getQuantity());
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
        order.setReworkNo(redisSequenceService.generateBusinessNumberByType(
                "iqc_rework", "IQW", "yyMMdd", 3));
        order.setDispositionId(disposition.getDispositionId()); order.setQuarantineId(quarantine.getQuarantineId());
        order.setInboundId(quarantine.getInboundId()); order.setInboundItemId(quarantine.getInboundItemId());
        order.setInspectionId(quarantine.getInspectionId()); order.setLotId(quarantine.getLotId()); order.setMaterialId(quarantine.getMaterialId());
        order.setMaterialCode(quarantine.getMaterialCode()); order.setMaterialName(quarantine.getMaterialName());
        order.setBatchNo(quarantine.getBatchNo()); order.setIqcBatchId(quarantine.getIqcBatchId()); order.setQuantity(disposition.getQuantity());
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
        order.setScrapNo(redisSequenceService.generateBusinessNumberByType(
                "iqc_scrap", "IQS", "yyMMdd", 3));
        order.setDispositionId(disposition.getDispositionId()); order.setQuarantineId(quarantine.getQuarantineId());
        order.setInboundId(quarantine.getInboundId()); order.setInboundItemId(quarantine.getInboundItemId());
        order.setInspectionId(quarantine.getInspectionId()); order.setLotId(quarantine.getLotId()); order.setMaterialId(quarantine.getMaterialId());
        order.setMaterialCode(quarantine.getMaterialCode()); order.setMaterialName(quarantine.getMaterialName());
        order.setBatchNo(quarantine.getBatchNo()); order.setIqcBatchId(quarantine.getIqcBatchId()); order.setQuantity(disposition.getQuantity());
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
    public IqcQuarantineLedgerPageVO pageIqcQuarantineLedger(IqcQuarantineLedgerQueryDTO query) {
        Page<IqcQuarantineLedgerRowVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<IqcQuarantineLedgerRowVO> result = iqcQuarantineMapper.selectLedgerPage(page, query);
        List<Long> inboundIds = result.getRecords().stream()
                .map(IqcQuarantineLedgerRowVO::getInboundId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        IqcQuarantineLedgerPageVO response = new IqcQuarantineLedgerPageVO();
        response.setPage(PageResult.of(result, result.getRecords()));
        if (inboundIds.isEmpty()) {
            response.setDispositionOrders(List.of());
            response.setReworkOrders(List.of());
            response.setBatches(List.of());
            return response;
        }
        response.setDispositionOrders(iqcDispositionOrderMapper.selectList(
                new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcDispositionOrder>()
                        .in(com.jjx.inventory.domain.InventoryIqcDispositionOrder::getInboundId, inboundIds)
                        .orderByDesc(com.jjx.inventory.domain.InventoryIqcDispositionOrder::getDispositionId)));
        response.setReworkOrders(iqcReworkOrderMapper.selectList(
                new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcReworkOrder>()
                        .in(com.jjx.inventory.domain.InventoryIqcReworkOrder::getInboundId, inboundIds)
                        .orderByDesc(com.jjx.inventory.domain.InventoryIqcReworkOrder::getReworkId)));
        response.setBatches(iqcBatchMapper.selectList(new LambdaQueryWrapper<InventoryIqcBatch>()
                .in(InventoryIqcBatch::getSourceInboundId, inboundIds)
                .orderByAsc(InventoryIqcBatch::getBatchId)));
        return response;
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
    public List<InventoryIqcBatch> listIqcBatches(Long inboundId) {
        if (inboundId == null) return List.of();
        // 2026-09-21 根治：改按「所属入库单」过滤。原实现按 source_inbound_item_id ∈ 该单明细 id，
        // 而明细 id 会被复用（旧数据被清理后新明细又拿到 id 1/2）→ 历史批次被错误挂到新单上。
        return iqcBatchMapper.selectList(new LambdaQueryWrapper<InventoryIqcBatch>()
                .eq(InventoryIqcBatch::getSourceInboundId, inboundId)
                .orderByAsc(InventoryIqcBatch::getBatchId));
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
        scrap.setApproverName(approval == null ? SecurityUtils.getDisplayName() : approval.getApproverName());
        iqcScrapOrderMapper.updateById(scrap);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long completeIqcRework(Long reworkId) {
        var rework = iqcReworkOrderMapper.selectById(reworkId);
        if (rework == null) throw new BusinessException("IQC 返工单不存在");
        if (!"CREATED".equals(rework.getStatus())) throw new BusinessException("该返工单已完成或已发起复检");
        if (rework.getQuantity() == null || rework.getQuantity().signum() <= 0) {
            throw new BusinessException("返工单数量必须大于 0");
        }
        if (rework.getInboundItemId() == null) throw new BusinessException("返工单缺少来料明细");
        var item = inboundItemMapper.selectById(rework.getInboundItemId());
        if (item == null || item.getLotId() == null) throw new BusinessException("找不到原 IQC 检验批");
        if (!rework.getInboundItemId().equals(item.getItemId())
                || (rework.getInboundId() != null && !rework.getInboundId().equals(item.getInboundId()))) {
            throw new BusinessException("返工单与来料明细不匹配");
        }
        Long pendingCount = iqcReworkOrderMapper.selectCount(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcReworkOrder>()
                .eq(com.jjx.inventory.domain.InventoryIqcReworkOrder::getInboundItemId, item.getItemId())
                .eq(com.jjx.inventory.domain.InventoryIqcReworkOrder::getStatus, "PENDING_REINSPECTION"));
        if (pendingCount != null && pendingCount > 0) throw new BusinessException("该材料已有待复检返工单");
        var old = qualityLotMapper.selectById(item.getLotId());
        if (old == null || !"APPROVED".equals(old.getReviewStatus()) || !"fail".equals(old.getResult())) {
            throw new BusinessException("只有已审核的不合格检验记录可以发起返工复检");
        }
        BigDecimal failQty = old.getFailQuantity() == null ? BigDecimal.ZERO : old.getFailQuantity();
        if (rework.getQuantity().compareTo(failQty) > 0) {
            throw new BusinessException("返工数量不能超过原不良数量（" + failQty.stripTrailingZeros().toPlainString() + "）");
        }
        String childBatchNo = createIqcChildBatch(rework, item, null, "REWORK");
        Long newLotId = createIqcReinspectionLot(item, old, rework.getQuantity(), childBatchNo);
        item.setInspectionId(null);
        item.setLotId(newLotId);
        item.setBatchNo(childBatchNo);
        item.setSampledQuantity(rework.getQuantity());
        item.setInspectionResult(null);
        item.setDisposition(null);
        item.setQualifiedQuantity(BigDecimal.ZERO);
        item.setRejectedQuantity(BigDecimal.ZERO);
        item.setRejectReason(null);
        inboundItemMapper.updateById(item);
        rework.setStatus("PENDING_REINSPECTION");
        rework.setChildBatchNo(childBatchNo);
        iqcReworkOrderMapper.updateById(rework);
        var inbound = inboundOrderMapper.selectById(item.getInboundId());
        if (inbound != null) {
            inbound.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
            inboundOrderMapper.updateById(inbound);
        }
        return newLotId;
    }

    private Long createIqcReinspectionLot(InventoryInboundItem item,
            com.jjx.quality.domain.entity.QualityLot old, BigDecimal quantity, String childBatchNo) {
        com.jjx.quality.service.QualityLotService lotService = qualityLotServiceProvider.getIfAvailable();
        if (lotService == null) throw new BusinessException("统一质量检验服务不可用");
        com.jjx.quality.dto.QualityLotCreateDTO dto = new com.jjx.quality.dto.QualityLotCreateDTO();
        dto.setLotType("IQC"); dto.setSourceType("INBOUND"); dto.setSourceId(item.getInboundId());
        dto.setSourceItemId(item.getItemId()); dto.setMaterialId(item.getMaterialId());
        dto.setMaterialCode(item.getMaterialCode()); dto.setMaterialName(item.getMaterialName());
        dto.setBatchNo(childBatchNo); dto.setLotQuantity(quantity); dto.setParentLotId(old.getLotId());
        dto.setVersion((old.getVersion() == null ? 1 : old.getVersion()) + 1);
        dto.setRemark("IQC 返工复检，源检验批 " + old.getLotNo());
        com.jjx.quality.domain.entity.QualityLot fresh = lotService.createLot(dto);
        fresh.setReviewStatus("DRAFT");
        qualityLotMapper.updateById(fresh);
        return fresh.getLotId();
    }

    /** 创建返工/复检子批次，并显式保留父批次关系。 */
    private InventoryIqcBatch ensureOriginalBatch(InventoryInboundItem item) {
        // 2026-09-21 根治：身份优先 —— 明细已绑定批次时沿 parent 上溯到 ORIGINAL，
        // 不再依赖 (material_id, batch_no) 查询。原实现按名字查，而返工后明细的 batch_no 会被
        // 改写成子批次号（RW-…）→ 查不到原批 → 重复插新行，这就是「幽灵 ORIGINAL 批次 / 重复子批次」的根因。
        if (item.getIqcBatchId() != null) {
            InventoryIqcBatch current = iqcBatchMapper.selectById(item.getIqcBatchId());
            if (current != null) {
                InventoryIqcBatch root = current;
                int guard = 0;
                while (root.getParentBatchId() != null && guard++ < 20) {
                    InventoryIqcBatch parent = iqcBatchMapper.selectById(root.getParentBatchId());
                    if (parent == null) break;
                    root = parent;
                }
                if (!Objects.equals(item.getIqcBatchId(), root.getBatchId())) {
                    item.setIqcBatchId(root.getBatchId());
                    inboundItemMapper.updateById(item);
                }
                return root;
            }
        }
        String parentBatchNo = item.getBatchNo();
        if (parentBatchNo == null || parentBatchNo.isBlank()) {
            throw new BusinessException("原批次号为空，无法建立批次身份");
        }
        InventoryIqcBatch parent = iqcBatchMapper.selectOne(new LambdaQueryWrapper<InventoryIqcBatch>()
                .eq(InventoryIqcBatch::getMaterialId, item.getMaterialId())
                .eq(InventoryIqcBatch::getBatchNo, parentBatchNo)
                .last("LIMIT 1"));
        if (parent == null) {
            parent = new InventoryIqcBatch();
            parent.setMaterialId(item.getMaterialId());
            parent.setBatchNo(parentBatchNo);
            parent.setRootBatchNo(parentBatchNo);
            parent.setBatchType("ORIGINAL");
            parent.setSourceInboundItemId(item.getItemId());
            parent.setSourceInboundId(item.getInboundId());
            parent.setSourceInspectionId(null);
            parent.setQuantity(item.getQuantity());
            parent.setRemainingQuantity(item.getQuantity());
            parent.setStatus("SOURCE");
            iqcBatchMapper.insert(parent);
        }
        if (!Objects.equals(item.getIqcBatchId(), parent.getBatchId())) {
            item.setIqcBatchId(parent.getBatchId());
            inboundItemMapper.updateById(item);
        }
        return parent;
    }

    private String createIqcChildBatch(com.jjx.inventory.domain.InventoryIqcReworkOrder rework,
                                       InventoryInboundItem item,
                                       Long inspectionId,
                                       String batchType) {
        if (rework.getChildBatchId() != null && rework.getChildBatchNo() != null) {
            return rework.getChildBatchNo();
        }
        InventoryIqcBatch parent = ensureOriginalBatch(item);
        String parentBatchNo = parent.getBatchNo();
        String childBatchNo = "RW-" + rework.getReworkNo();
        InventoryIqcBatch child = iqcBatchMapper.selectOne(new LambdaQueryWrapper<InventoryIqcBatch>()
                .eq(InventoryIqcBatch::getMaterialId, item.getMaterialId())
                .eq(InventoryIqcBatch::getBatchNo, childBatchNo)
                .last("LIMIT 1"));
        if (child == null) {
            child = new InventoryIqcBatch();
            child.setBatchNo(childBatchNo);
            // 2026-09-21：补写物料与所属入库单 —— 原先不写 material_id，导致「按 material+batch_no」
            // 幂等判定失效（NULL≠NULL）而重复插子批次（如 batch 11/12），且批次与物料脱钩。
            child.setMaterialId(item.getMaterialId());
            child.setSourceInboundId(parent.getSourceInboundId() != null
                    ? parent.getSourceInboundId() : item.getInboundId());
            child.setParentBatchId(parent.getBatchId());
            child.setParentBatchNo(parentBatchNo);
            child.setRootBatchNo(parent.getRootBatchNo());
            child.setBatchType(batchType);
            child.setSourceReworkId(rework.getReworkId() != null && rework.getReworkId() > 0
                    ? rework.getReworkId() : null);
            child.setSourceInboundItemId(item.getItemId());
            child.setSourceInspectionId(inspectionId);
            child.setQuantity(rework.getQuantity());
            child.setRemainingQuantity(rework.getQuantity());
            child.setStatus("PENDING_REINSPECTION");
            iqcBatchMapper.insert(child);
        }
        rework.setChildBatchId(child.getBatchId());
        item.setIqcBatchId(child.getBatchId());
        inboundItemMapper.updateById(item);
        return child.getBatchNo();
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
        InventoryTransaction tx = new InventoryTransaction();
        tx.setTransactionType("IQC_RELEASE");
        tx.setSourceType("INBOUND_IQC");
        tx.setSourceId(quarantine.getInboundId());
        tx.setLotId(quarantine.getLotId());
        tx.setBatchNo(quarantine.getBatchNo());
        tx.setOperatorId(action.getOperatorId() != null ? action.getOperatorId() : SecurityUtils.getUserId());
        tx.setOperatorName(action.getOperatorName() != null ? action.getOperatorName() : SecurityUtils.getDisplayName());
        tx.setRemark(action.getRemark() == null ? "IQC 隔离品释放" : action.getRemark());
        stockMutationService.applyDelta(stock, quantity, tx);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        boolean updated = inboundOrderMapper.updateById(order) > 0;
        if (updated) {
            publishInboundEvent("inventory.inbound.cancelled", inboundId);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
                    if (item.getLotId() == null) return false;
                    com.jjx.quality.domain.entity.QualityLot current = qualityLotMapper.selectById(item.getLotId());
                    return current != null
                            && "pending".equals(current.getResult())
                            && "DRAFT".equals(current.getReviewStatus());
                });
        if (!pendingPurchaseInspection
                && !pendingReinspection
                && !InventoryOrderStatusEnum.DRAFT.getValue().equals(status)
                && !InventoryOrderStatusEnum.REJECTED.getValue().equals(status)
                && !InventoryOrderStatusEnum.CANCELLED.getValue().equals(status)) {
            log.error("入库单状态不允许提交审批: inboundId={}, status={}", inboundId, status);
            return false;
        }

        // dev-20260922-009：待复核期间禁止重复提交。
        // 起因：提交动作本身把单据置回 PENDING（下方 order.setOrderStatus(PENDING)），所以"已提交待复核"
        // 与"待检验"同为 PENDING，状态守卫拦不住重复提交；重复提交会复用同一张检验批覆盖已录的合格/不良/
        // 检测项，并重发 quality.iqc.submitted（事件侧按 eventCode+毫秒 建待办，不去重）→ 多一条待办+通知。
        // 判定：采购来源 + 全部明细都已生成检验批且都处于待审核（PENDING）→ 已在待复核，直接拒绝。
        // 不拦的场景：明细还存在 REJECTED（被驳回待重录）/DRAFT（复检草稿）时 allowed=false，可正常重提。
        if (isPurchaseInbound(order)
                && InventoryOrderStatusEnum.PENDING.getValue().equals(status)
                && allItemsAwaitingReview(inboundId)) {
            throw new BusinessException("该入库单已提交来料检验、正在等待品质主管审核，请勿重复提交；如需修改请让品质主管先驳回对应明细");
        }

        if (isPurchaseInbound(order)) {
            saveInspection(order, inspection);
        }
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        boolean updated = inboundOrderMapper.updateById(order) > 0;
        // 2026-09-21（dev-20260921-003）：本事件改为手写 payload 发布，不再挂 @Event 注解。
        // 注解只能带 bizId=#inboundId，payload 里没有 inboundNo/sourceNo/supplierName，
        // 而 sys_event_config 的模板用的是 {inboundNo} 等占位符 → 通知与看板任务标题会原样显示
        // 「入库单【{inboundNo}】待来料检验」（sys_notification 33/34、sys_task 1997/1998 实测）。
        // iqcPayload 已含 inboundId/inboundNo/sourceNo/supplierName/触发人，模板可直接解析。
        if (updated) {
            publishIqcEventAfterCommit("quality.iqc.submitted", iqcPayload(order, null, null, null));
        }
        return updated;
    }

    /**
     * 全部明细都已生成检验批、且都停在待审核（PENDING）——即"已提交、在等品质主管复核"。
     * dev-20260922-009：给「待复核期间禁止重复提交」用。明细还没检验批、或存在 REJECTED（已驳回待重录）
     * /DRAFT（复检草稿）时返回 false —— 这些情况允许正常重新提交。
     */
    private boolean allItemsAwaitingReview(Long inboundId) {
        List<InventoryInboundItem> items = inboundItemMapper.selectByInboundId(inboundId);
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (InventoryInboundItem item : items) {
            if (item.getLotId() == null) {
                return false;
            }
            com.jjx.quality.domain.entity.QualityLot lot = qualityLotMapper.selectById(item.getLotId());
            if (lot == null || !"PENDING".equals(lot.getReviewStatus())) {
                return false;
            }
        }
        return true;
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
                com.jjx.quality.domain.entity.QualityLot previous = item.getLotId() == null
                        ? null : qualityLotMapper.selectById(item.getLotId());
                if (previous != null && "APPROVED".equals(previous.getReviewStatus())) {
                    // 整单重提时，已审核项目以数据库事实为准，既不要求客户端重复填写，也禁止覆盖。
                    allPass = allPass && "PASS".equalsIgnoreCase(item.getInspectionResult());
                    continue;
                }
                boolean reinspection = previous != null && previous.getParentLotId() != null
                        && "pending".equals(previous.getResult()) && previous.getLotQuantity() != null;
                BigDecimal reinspectionQuantity = reinspection ? previous.getLotQuantity() : null;
                String itemResult = submitted.getInspectionResult() == null ? "" : submitted.getInspectionResult().toUpperCase();
                if (!List.of("PASS", "FAIL").contains(itemResult)) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "检验判定仅支持 PASS、FAIL");
                }
                BigDecimal qualified = submitted.getQualifiedQuantity() == null ? BigDecimal.ZERO : submitted.getQualifiedQuantity();
                BigDecimal rejected = submitted.getRejectedQuantity() == null ? BigDecimal.ZERO : submitted.getRejectedQuantity();
                BigDecimal checked = qualified.add(rejected);
                if (qualified.signum() < 0 || rejected.signum() < 0) throw new BusinessException("合格数量和不良数量不能为负数");
                BigDecimal inspectionQuantity = reinspection ? reinspectionQuantity : item.getQuantity();
                if (checked.compareTo(inspectionQuantity) != 0) {
                    if (reinspection) {
                        throw new BusinessException("复检的合格数量与不良数量之和必须等于本次返工数量（"
                            + reinspectionQuantity.stripTrailingZeros().toPlainString() + "）");
                    }
                    throw new BusinessException("物料" + item.getMaterialCode() + "：合格数量与不良数量之和必须等于收货数量");
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
                // 2026-09-21（dev-20260921-004）：复检/返工场景下明细行仍代表「整批」，
                // 允收量 = 收货数量 − 本轮净不良。原实现写 qualified（=本次子批次良品数），
                // 等于把整行允收量改写成子批次数量 → 隔离公式与入库过账都按子批次数算，
                // PO202609210001-3 因此少入 95 件、并生成 95 件的假隔离。
                BigDecimal accepted = reinspection
                        ? item.getQuantity().subtract(rejected)
                        : qualified;
                if (accepted.signum() < 0 || accepted.compareTo(item.getQuantity()) > 0) {
                    throw new BusinessException("物料" + item.getMaterialCode() + "允收入库数量必须在收货数量范围内");
                }
                if (!reinspection && "PASS".equals(itemResult) && accepted.compareTo(item.getQuantity()) != 0) {
                    throw new BusinessException("RM001599：整批判定合格时接收数量须等于收货数量（物料"
                            + item.getMaterialCode() + "）");
                }
                // 2026-09-16 dev-20260916-009：不良品不得计入允收入库数量。
                // 隔离数量口径 = 收货数量 - 接收数量，若不良品被接收，隔离数量会算成 0，
                // 不良品就会被当良品入库（PO202609160003 / RM001563 实例）。
                // 因此接收数量上限 = 收货数量 - 不良数量 = 良品数量。
                BigDecimal maxAccepted = item.getQuantity().subtract(rejected);
                if ("FAIL".equals(itemResult) && accepted.compareTo(maxAccepted) > 0) {
                    throw new BusinessException("物料" + item.getMaterialCode()
                            + "允收入库数量不能超过良品数量（收货数量 - 不良数量 = " + maxAccepted.stripTrailingZeros().toPlainString()
                            + "），不良品请走隔离处置");
                }

                item.setSampledQuantity(inspectionQuantity);
                item.setInspectionId(null);
                item.setInspectionResult(itemResult);
                item.setDisposition(disposition == null ? null : disposition.getCode());
                item.setQualifiedQuantity(qualified);
                item.setRejectedQuantity(rejected);
                item.setAcceptedQuantity(accepted);
                item.setRejectReason(submitted.getRejectReason());
                // 026：IQC 检验事实同步落 quality_lot（expand：新模型落库，旧路径暂留）
                Long iqcLotId = syncIqcLot(order.getInboundId(), item, itemResult, qualified, rejected,
                        submitted.getRejectReason(), submitted.getInspectionItems());
                if (iqcLotId != null) {
                    item.setLotId(iqcLotId);
                }
                inboundItemMapper.updateById(item);
                allPass = allPass && "PASS".equals(itemResult);
        }
        order.setInspectionResult(allPass ? "PASS" : "OTHER");
        order.setInspectionRemark(inspection.getInspectionRemark());
        order.setInspectorId(SecurityUtils.getUserId());
        order.setInspectorName(SecurityUtils.getDisplayName());
        order.setInspectionTime(LocalDateTime.now());
    }

    /**
     * IQC 归一（dev-20260918-026 · expand）：把 IQC 检验事实写入 quality_lot（lot_type=IQC）。
     * 幂等：按 sourceType=INBOUND + sourceId=入库单 + sourceItemId=入库明细 复用同一批。
     * IQC 检验事实只写 quality_lot，不再写旧 production_quality_inspection。
     * 失败不阻断旧路径（expand 过渡期容错）。
     */
    private Long syncIqcLot(Long inboundId, InventoryInboundItem item, String itemResult,
                            BigDecimal qualified, BigDecimal rejected, String defectReason,
                            List<com.jjx.production.domain.dto.InspectionItemDTO> inspectionItems) {
            com.jjx.quality.service.QualityLotService lotService = qualityLotServiceProvider.getIfAvailable();
            if (lotService == null) {
                throw new BusinessException("统一质量检验服务不可用，不能提交 IQC");
            }
            java.util.List<com.jjx.quality.domain.entity.QualityLot> lots = lotService.listBySource("INBOUND", inboundId);
            // 2026-09-21（dev-20260921-004）：复检/返工子批次必须独立成检验批，不能复用原批 lot。
            // 原实现只按 sourceItemId 复用 → 子批复检结果覆写原批的「检验/合格/不良」事实
            // （RM001585/PO202609210001-3-1 实测：原批 100 检 95 合格被写成 5 检 5 合格），
            // 且复检批没有父子关系、无法追溯。现在按批次号匹配，子批挂 parent_lot_id 指向原批。
            com.jjx.quality.domain.entity.QualityLot lot = lots.stream()
                    .filter(l -> item.getBatchNo() != null && item.getBatchNo().equals(l.getBatchNo()))
                    .findFirst().orElse(null);
            com.jjx.quality.domain.entity.QualityLot originalLot = lot == null ? lots.stream()
                    .filter(l -> item.getItemId() != null && item.getItemId().equals(l.getSourceItemId()))
                    .findFirst().orElse(null) : null;
            if (lot == null) {
                com.jjx.quality.dto.QualityLotCreateDTO dto = new com.jjx.quality.dto.QualityLotCreateDTO();
                dto.setLotType("IQC");
                dto.setSourceType("INBOUND");
                dto.setSourceId(inboundId);
                dto.setSourceItemId(item.getItemId());
                dto.setMaterialId(item.getMaterialId());
                dto.setMaterialCode(item.getMaterialCode());
                dto.setMaterialName(item.getMaterialName());
                dto.setBatchNo(item.getBatchNo());
                BigDecimal lotQty = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
                if (originalLot != null) {
                    // 子批次（复检/返工产物）：批数量取本次复检数量，挂原批 lot 下并升版
                    // （createLot 的防重复守卫：同来源+行+版本唯一，复检必须新版本）
                    lotQty = qualified.add(rejected);
                    dto.setParentLotId(originalLot.getLotId());
                    int nextVersion = lots.stream()
                            .filter(l -> item.getItemId() != null && item.getItemId().equals(l.getSourceItemId()))
                            .map(l -> l.getVersion() == null ? 1 : l.getVersion())
                            .max(Integer::compareTo).orElse(1) + 1;
                    dto.setVersion(nextVersion);
                }
                if (lotQty.signum() <= 0) {
                    lotQty = qualified.add(rejected);
                }
                if (lotQty.signum() <= 0) {
                    return null;
                }
                dto.setLotQuantity(lotQty);
                dto.setRemark(originalLot == null
                        ? "IQC 检验批（来源入库单，批次 " + item.getBatchNo() + "）"
                        : "IQC 复检批（来源入库单，原批次 " + originalLot.getBatchNo()
                          + "，批次 " + item.getBatchNo() + "）");
                lot = lotService.createLot(dto);
            }
            java.util.List<com.jjx.quality.dto.QualityLotItemDTO> lotItems = new java.util.ArrayList<>();
            int sort = 0;
            for (com.jjx.production.domain.dto.InspectionItemDTO source : inspectionItems) {
                com.jjx.quality.dto.QualityLotItemDTO target = new com.jjx.quality.dto.QualityLotItemDTO();
                target.setCheckItem(source.getCheckItem());
                target.setStandard(source.getStandard());
                target.setActualValue(source.getActualValue());
                target.setResult(source.getResult());
                target.setRemark(source.getRemark());
                target.setSortOrder(++sort);
                lotItems.add(target);
            }
            lotService.saveItems(lot.getLotId(), lotItems);
            lot.setReviewStatus("PENDING");
            lot.setInspector(SecurityUtils.getDisplayName());
            lot.setDefectReason(defectReason);
            lot.setRemark(itemResult + "：合格 " + qualified + "，不良 " + rejected);
            qualityLotMapper.updateById(lot);
            // 判定不在提交时做：IQC 有独立审核环节，审核通过时再判。
            return lot.getLotId();
    }

    /** IQC 审核通过后，把最终判定写入 quality_lot（dev-20260918-026 expand 阶段，容错） */
    private void judgeIqcLot(InventoryInboundItem item, String itemResult, BigDecimal qualified, BigDecimal rejected) {
            if (item == null || item.getLotId() == null) {
                throw new BusinessException("IQC 检验批不存在，不能审核");
            }
            com.jjx.quality.service.QualityLotService lotService = qualityLotServiceProvider.getIfAvailable();
            if (lotService == null) {
                throw new BusinessException("统一质量检验服务不可用，不能审核 IQC");
            }
            BigDecimal q = qualified == null ? BigDecimal.ZERO : qualified;
            BigDecimal f = rejected == null ? BigDecimal.ZERO : rejected;
            BigDecimal inspected = q.add(f);
            if (inspected.signum() <= 0) {
                return;
            }
            lotService.applyJudgement(item.getLotId(), inspected, q, f,
                    "PASS".equals(itemResult) ? "pass" : "fail", SecurityUtils.getDisplayName());
            if (f.signum() > 0) {
                com.jjx.quality.service.QualityNcrService ncrService = qualityNcrServiceProvider.getIfAvailable();
                if (ncrService == null) throw new BusinessException("不良台账服务不可用，IQC 审核已回滚");
                com.jjx.quality.domain.entity.QualityLot lot = qualityLotMapper.selectById(item.getLotId());
                ncrService.syncFromLot(lot, f, BigDecimal.ZERO, f, BigDecimal.ZERO,
                        item.getRejectReason(), SecurityUtils.getDisplayName());
            }
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
                // 2026-09-16 dev-20260916-008：实测记录允许留空（配合"整批合格"批量口径），
                // 只要求逐项给出合格/不合格结论；需要证据时可让检验员补填。
                log.debug("IQC 检测项目[{}]实测记录留空（物料{}），按当前口径放行",
                        check.getCheckItem(), inboundItem.getMaterialCode());
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
        // 2026-09-21（dev-20260921-003）并发复核防护：
        // 原实现先做一致性读（明细/检验记录）、随后才由 updateInboundReviewStatus 锁单，
        // 同一单据两笔复核并发时各自读自己事务开始时的快照，互相看不到对方刚提交的 APPROVED，
        // 「全部明细已审核」判定两边都落空 → 单据状态卡在待审批（PO202609210001 实测）。
        // 现在改为：先锁单、再当前读（FOR UPDATE）明细与检验记录。加锁顺序与 submitApprove 一致
        // （单 → 明细），避免与提交检验互相死锁。
        Long inboundId = inboundItemMapper.selectInboundIdByItemId(itemId);
        if (inboundId == null) throw new BusinessException("入库明细尚未提交 IQC 检验");
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) throw new BusinessException("入库单不存在");
        InventoryInboundItem item = inboundItemMapper.selectByIdForUpdate(itemId);
        if (item == null || item.getLotId() == null) throw new BusinessException("入库明细尚未提交 IQC 检验");
        com.jjx.quality.domain.entity.QualityLot quality = qualityLotMapper.selectForUpdate(item.getLotId());
        if (quality == null) throw new BusinessException("IQC 检验批不存在");
        // 幂等：已审核的记录重复提交（重复点击/并发重放）不再重复判级、不重复发事件，
        // 但仍重跑一次单据状态判定，让历史卡在待审批、明细却已全部审核的单据能收尾。
        if ("APPROVED".equals(quality.getReviewStatus())) {
            updateInboundReviewStatus(inboundId);
            return true;
        }
        if (!"PENDING".equals(quality.getReviewStatus())) {
            throw new BusinessException("仅待审核的 IQC 记录可以审核");
        }
        judgeIqcLot(item, item.getInspectionResult(), item.getQualifiedQuantity(), item.getRejectedQuantity());
        quality = qualityLotMapper.selectById(quality.getLotId());
        quality.setReviewStatus("APPROVED");
        quality.setReviewRemark(review == null ? null : review.getRemark());
        quality.setReviewerId(review == null ? SecurityUtils.getUserId() : review.getApproverId());
        quality.setReviewerName(review == null || review.getApproverName() == null
                ? SecurityUtils.getDisplayName() : review.getApproverName());
        quality.setReviewTime(LocalDateTime.now());
        qualityLotMapper.updateById(quality);
        if (quality.getParentLotId() != null) {
            var rework = iqcReworkOrderMapper.selectOne(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcReworkOrder>()
                    .eq(com.jjx.inventory.domain.InventoryIqcReworkOrder::getInboundItemId, item.getItemId())
                    .eq(com.jjx.inventory.domain.InventoryIqcReworkOrder::getStatus, "PENDING_REINSPECTION")
                    .orderByDesc(com.jjx.inventory.domain.InventoryIqcReworkOrder::getReworkId)
                    .last("LIMIT 1"));
            if (rework != null) {
                rework.setStatus("COMPLETED");
                iqcReworkOrderMapper.updateById(rework);
            }
        }
        updateIqcBatchResult(item.getIqcBatchId(), quality);
        // order 就是方法开头锁住的那一行（同一事务内内容未变），直接复用，不再重复查
        publishIqcEventAfterCommit("quality.iqc.item.approved", iqcPayload(order, item,
                order.getInspectorId(), order.getInspectorName()));
        updateInboundReviewStatus(inboundId);
        return true;
    }

    private void updateIqcBatchResult(Long batchId, com.jjx.quality.domain.entity.QualityLot quality) {
        if (batchId == null || quality == null) return;
        InventoryIqcBatch batch = iqcBatchMapper.selectById(batchId);
        if (batch == null) return;
        BigDecimal pass = nvl(quality.getPassQuantity());
        BigDecimal fail = nvl(quality.getFailQuantity());
        batch.setProcessedQuantity(pass.add(fail));
        batch.setAcceptedQuantity(pass);
        batch.setRejectedQuantity(fail);
        batch.setRemainingQuantity(nvl(batch.getQuantity()).subtract(pass.add(fail)).max(BigDecimal.ZERO));
        batch.setStatus(fail.signum() > 0 ? "FAILED" : "QUALIFIED");
        iqcBatchMapper.updateById(batch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rejectInspectionItem(Long itemId,
            com.jjx.inventory.dto.save.InboundInspectionReviewDTO review) {
        if (review == null || org.apache.commons.lang3.StringUtils.isBlank(review.getRemark())) {
            throw new BusinessException("驳回时必须填写审核意见");
        }
        InventoryInboundItem item = inboundItemMapper.selectById(itemId);
        if (item == null || item.getLotId() == null) throw new BusinessException("入库明细尚未提交 IQC 检验");
        com.jjx.quality.domain.entity.QualityLot quality = qualityLotMapper.selectById(item.getLotId());
        if (quality == null || !"PENDING".equals(quality.getReviewStatus())) {
            throw new BusinessException("仅待审核的 IQC 记录可以驳回");
        }
        quality.setReviewStatus("REJECTED");
        quality.setReviewRemark(review.getRemark());
        quality.setReviewerId(review.getApproverId());
        quality.setReviewerName(review.getApproverName());
        quality.setReviewTime(LocalDateTime.now());
        qualityLotMapper.updateById(quality);
        InventoryInboundOrder order = inboundOrderMapper.selectById(item.getInboundId());
        publishIqcEventAfterCommit("quality.iqc.item.rejected", iqcPayload(order, item,
                order == null ? null : order.getInspectorId(), order == null ? null : order.getInspectorName()));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reinspectItem(Long itemId) {
        InventoryInboundItem item = inboundItemMapper.selectById(itemId);
        if (item == null || item.getLotId() == null) throw new BusinessException("入库明细尚无可复检的 IQC 记录");
        com.jjx.quality.domain.entity.QualityLot old = qualityLotMapper.selectById(item.getLotId());
        if (old == null || !"APPROVED".equals(old.getReviewStatus())) {
            throw new BusinessException("只有已审核的 IQC 记录可以发起复检");
        }
        if (!"fail".equals(old.getResult()) || old.getFailQuantity() == null || old.getFailQuantity().signum() <= 0) {
            throw new BusinessException("只有存在不良数量的 IQC 记录可以发起复检");
        }
        Long pendingRework = iqcReworkOrderMapper.selectCount(new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryIqcReworkOrder>()
                .eq(com.jjx.inventory.domain.InventoryIqcReworkOrder::getInboundItemId, item.getItemId())
                .eq(com.jjx.inventory.domain.InventoryIqcReworkOrder::getStatus, "PENDING_REINSPECTION"));
        if (pendingRework != null && pendingRework > 0) throw new BusinessException("该材料已有待复检返工单");
        var directReinspection = new com.jjx.inventory.domain.InventoryIqcReworkOrder();
        directReinspection.setReworkId(-old.getLotId());
        directReinspection.setReworkNo("REINSPECT-" + old.getLotId());
        directReinspection.setBatchNo(item.getBatchNo());
        directReinspection.setInboundItemId(item.getItemId());
        directReinspection.setQuantity(old.getFailQuantity());
        String childBatchNo = createIqcChildBatch(directReinspection, item, null, "REINSPECTION");
        Long newId = createIqcReinspectionLot(item, old, old.getFailQuantity(), childBatchNo);
        item.setInspectionId(null);
        item.setLotId(newId);
        item.setBatchNo(childBatchNo);
        item.setSampledQuantity(old.getFailQuantity());
        item.setQualifiedQuantity(BigDecimal.ZERO);
        item.setRejectedQuantity(BigDecimal.ZERO);
        item.setInspectionResult(null);
        item.setDisposition(null);
        item.setRejectReason(null);
        inboundItemMapper.updateById(item);
        InventoryInboundOrder order = inboundOrderMapper.selectById(item.getInboundId());
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.updateById(order);
        publishIqcEventAfterCommit("quality.iqc.reinspection.created", iqcPayload(order, item,
                order.getInspectorId(), order.getInspectorName()));
        return newId;
    }

    /**
     * 全部行审核通过即建隔离台账，使不合格品处置与确认入库动作解耦。
     * 2026-09-21（dev-20260921-003）：判定前先锁单、并用当前读（FOR UPDATE）重取明细与检验记录。
     * 原实现读一致性快照 → 并发复核时两笔事务互相看不到对方刚提交的 APPROVED，判定永远不成立
     * （PO202609210001 实测：两条明细都已审核，单据却停在待审批）。
     */
    private void updateInboundReviewStatus(Long inboundId) {
        InventoryInboundOrder order = inboundOrderMapper.selectByIdForUpdate(inboundId);
        if (order == null) return;
        List<InventoryInboundItem> items = inboundItemMapper.selectByInboundIdForUpdate(inboundId);
        boolean allApproved = !items.isEmpty() && items.stream().allMatch(item -> {
            if (item.getLotId() == null) return false;
            com.jjx.quality.domain.entity.QualityLot quality = qualityLotMapper.selectForUpdate(item.getLotId());
            return quality != null && "APPROVED".equals(quality.getReviewStatus());
        });
        if (allApproved) {
            order.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());
            inboundOrderMapper.updateById(order);
            int quarantineCount = createIqcQuarantine(order, SecurityUtils.getUserId(), SecurityUtils.getDisplayName());
            Map<String, Object> approvedPayload = iqcPayload(order, null, null, null);
            publishIqcEventAfterCommit("quality.iqc.approved", approvedPayload);
            if (quarantineCount > 0) {
                approvedPayload.put("quarantineCount", quarantineCount);
                publishIqcEventAfterCommit("quality.iqc.quarantine.created", approvedPayload);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean syncReviewStatus(Long inboundId) {
        // 手工重算单据审核状态（2026-09-21 dev-20260921-003）：
        // 给「明细已全部审核、单据状态却没推进」的历史单据收尾用。走同一条判定逻辑，
        // 幂等（createIqcQuarantine 按 明细+检验记录 去重），可重复调用。
        updateInboundReviewStatus(inboundId);
        InventoryInboundOrder order = inboundOrderMapper.selectById(inboundId);
        return order != null && InventoryOrderStatusEnum.APPROVED.getValue().equals(order.getOrderStatus());
    }

    /**
     * 入库来源一句话描述（事件模板用）：
     * · 采购入库 → 「采购单 PRxxx，供应商 某某」（无供应商时只留采购单号）
     * · 生产完工入库 → 「生产工单 WO-xxx」
     * 2026-09-21 dev-20260921-040：避免模板写死“采购单+供应商”导致生产来源渲染空值。
     */
    private String buildSourceDesc(InventoryInboundOrder order) {
        if (order == null) {
            return null;
        }
        String sourceNo = order.getSourceNo();
        boolean hasSourceNo = sourceNo != null && !sourceNo.isBlank();
        if ("PRODUCTION".equals(order.getSourceType())) {
            return "生产工单 " + (hasSourceNo ? sourceNo : "-");
        }
        if ("SALES_RETURN".equals(order.getSourceType())) {
            return "客户拒收回库（发货单 " + (hasSourceNo ? sourceNo : "-") + "）";
        }
        StringBuilder desc = new StringBuilder("采购单 ").append(hasSourceNo ? sourceNo : "-");
        if (order.getSupplierName() != null && !order.getSupplierName().isBlank()) {
            desc.append("，供应商 ").append(order.getSupplierName());
        }
        return desc.toString();
    }

    private Map<String, Object> iqcPayload(InventoryInboundOrder order, InventoryInboundItem item,
            Long receiverId, String receiverName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bizType", "quality");
        payload.put("triggerUserId", SecurityUtils.getUserId());
        payload.put("triggerUserName", SecurityUtils.getUsername());
        payload.put("triggerRealName", SecurityUtils.getDisplayName());
        if (order != null) {
            payload.put("bizId", order.getInboundId());
            payload.put("inboundId", order.getInboundId());
            payload.put("inboundNo", order.getInboundNo());
            payload.put("bizNo", order.getInboundNo());
            payload.put("sourceId", order.getSourceId());
            payload.put("sourceNo", order.getSourceNo());
            payload.put("supplierId", order.getSupplierId());
            payload.put("supplierName", order.getSupplierName());
            // 2026-09-21 dev-20260921-040：入库来源一句话描述（采购单+供应商 / 生产工单）。
            // 模板里原来写死「采购单 {sourceNo}，供应商 {supplierName}」——生产完工入库没有供应商，
            // 会渲染成空值并刷 WARN；改用 {sourceDesc} 后两种来源都能带齐信息。
            payload.put("sourceDesc", buildSourceDesc(order));
        }
        if (item != null) {
            payload.put("itemId", item.getItemId());
            payload.put("materialId", item.getMaterialId());
            payload.put("materialCode", item.getMaterialCode());
            payload.put("materialName", item.getMaterialName());
        }
        if (receiverId != null) {
            payload.put("receiverId", receiverId);
        }
        if (receiverName != null && !receiverName.isBlank()) {
            payload.put("receiverName", receiverName);
        }
        return payload;
    }

    private void publishIqcEventAfterCommit(String eventCode, Map<String, Object> payload) {
        Map<String, Object> payloadSnapshot = new HashMap<>(payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.fire(eventCode, payloadSnapshot);
                }
            });
        } else {
            eventPublisher.fire(eventCode, payloadSnapshot);
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

        // 审批只记录审批结果，不过账；库存过账的唯一入口是 confirm()。
        order.setOrderStatus(InventoryOrderStatusEnum.APPROVED.getValue());
        order.setApproverId(approverId);
        order.setApproverName(approverName);
        order.setApproveTime(LocalDateTime.now());
        order.setApproveRemark(remark);
        boolean updated = inboundOrderMapper.updateById(order) > 0;
        if (updated) {
            publishInboundEvent("inventory.inbound.approved", inboundId);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        boolean updated = inboundOrderMapper.updateById(order) > 0;
        if (updated) {
            publishInboundEvent("inventory.inbound.rejected", inboundId);
        }
        return updated;
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
                if (existing.getIqcBatchId() == null) existing.setIqcBatchId(item.getIqcBatchId());
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
                newItem.setIqcBatchId(item.getIqcBatchId());
                newItem.setProductionDate(item.getProductionDate());
                newItem.setExpiryDate(item.getExpiryDate());
                newItem.setQuantity(BigDecimal.ZERO);
                newItem.setReservedQuantity(BigDecimal.ZERO);
                newItem.setUnitCost(item.getUnitPrice());
                newItem.setStatus(1);
                existing = newItem;
            }

            // 刷新库存汇总

            // 记录流水（DEV-651 补：before/after 为 NOT NULL，入库=加库存，before=当前汇总-本次数量）
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
            tx.setIqcBatchId(item.getIqcBatchId());
            tx.setLotId(item.getLotId());
            tx.setUnitCost(item.getUnitPrice());
            tx.setTransactionTime(LocalDateTime.now());
            tx.setOperatorId(operatorId != null ? operatorId : SecurityUtils.getUserId());
            tx.setOperatorName(operatorName != null ? operatorName : SecurityUtils.getDisplayName());
            tx.setRemark(remark != null ? remark : "入库确认");
            stockMutationService.applyDelta(existing, quantityToPost, tx);
            item.setPostedQuantity(postedQuantity.add(quantityToPost));
            inboundItemMapper.updateById(item);
        }
    }

    /**
     * 完工入库过账后回写 quality_lot.stored_quantity / CLOSED（dev-20260918-022/023）。
     * 失败不影响入库主流程（但会记 warn）。
     */
    private void syncQualityLotStored(InventoryInboundOrder order) {
        try {
            com.jjx.quality.service.QualityLotService lotService = qualityLotServiceProvider.getIfAvailable();
            if (lotService == null) {
                return;
            }
            int lots = 0;
            // dev-20260923（022 收尾缺陷修复）：按「该检验批在**未取消**单据里的已过账净额」回写 stored_quantity。
            // 原实现把「当前单据这一行的 posted_quantity」当目标值：红冲单（负数量）确认时 delta 被算成
            // −98−98=−196 → stored 变成 −98（异常值，业务上已入库数不可能为负）。改为按批聚合：
            //   target = SUM(该 lot 在未取消单据里的 posted_quantity) → 正常单 98 + 红冲单 −98 = 0 ✓
            java.util.Set<Long> affectedLots = new java.util.HashSet<>();
            for (InventoryInboundItem item : inboundItemMapper.selectByInboundId(order.getInboundId())) {
                if (item.getLotId() != null) {
                    affectedLots.add(item.getLotId());
                }
            }
            for (Long lotId : affectedLots) {
                BigDecimal net = BigDecimal.ZERO;
                List<InventoryInboundItem> lotItems = inboundItemMapper.selectList(
                        new LambdaQueryWrapper<InventoryInboundItem>().eq(InventoryInboundItem::getLotId, lotId));
                for (InventoryInboundItem li : lotItems) {
                    InventoryInboundOrder liOrder = inboundOrderMapper.selectById(li.getInboundId());
                    if (liOrder == null
                            || InventoryOrderStatusEnum.CANCELLED.getValue().equals(liOrder.getOrderStatus())) {
                        continue;
                    }
                    net = net.add(Objects.requireNonNullElse(li.getPostedQuantity(), BigDecimal.ZERO));
                }
                com.jjx.quality.domain.entity.QualityLot lot = lotService.getLot(lotId);
                BigDecimal delta = net.subtract(Objects.requireNonNullElse(lot.getStoredQuantity(), BigDecimal.ZERO));
                if (delta.signum() != 0) {
                    lotService.addStoredQuantity(lotId, delta);
                }
                lots++;
            }
            if (lots == 0) {
                lots = lotService.markOrderFinishedStored(order.getSourceId());
            }
            log.info("完工入库回写检验批已入库数量: orderId={} 批数={}", order.getSourceId(), lots);
        } catch (Exception e) {
            log.warn("回写 quality_lot.stored_quantity 失败（不影响入库）: orderId={} err={}", order.getSourceId(), e.getMessage());
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
            // dev-20260922-020（口径 B）：冲减时 postedQty 为负 → 这里会把订单已产数减回去，下限 0
            salesOrder.setProducedQuantity(Math.max(0, produced + postedQty.intValue()));
            salesOrderMapper.updateById(salesOrder);
            log.info("完工入库回写订单 produced_quantity: orderId={}, 本次+{}，累计={}",
                    productionOrder.getSalesOrderId(), postedQty, salesOrder.getProducedQuantity());
        } catch (Exception e) {
            log.warn("完工入库回写订单 produced_quantity 失败（不影响入库主流程）: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromPurchase(Long purchaseOrderId) {
        log.info("从采购订单创建入库单: purchaseOrderId={}", purchaseOrderId);

        // 1. 查询采购订单
        PurchaseOrder po = purchaseOrderMapper.selectById(purchaseOrderId);
        if (po == null) {
            throw new BusinessException("采购订单不存在: " + purchaseOrderId);
        }

        // 2. 幂等去重（dev-20260923-029 第 5 批）：原来是「单号 == 采购单号」判重；拆号后单号不再含采购单号，
        // 改为语义判重：该采购单已有**未取消**的入库单就不再整单生成（不再依赖单号形态）。
        Long existingAny = inboundOrderMapper.selectCount(new LambdaQueryWrapper<InventoryInboundOrder>()
                .eq(InventoryInboundOrder::getSourceType, "PURCHASE")
                .eq(InventoryInboundOrder::getSourceId, purchaseOrderId)
                .ne(InventoryInboundOrder::getOrderStatus, InventoryOrderStatusEnum.CANCELLED.getValue()));
        if (existingAny != null && existingAny > 0) {
            log.warn("采购订单{}已有入库单，跳过整单生成", purchaseOrderId);
            return null;
        }
        String inboundNo = nextInboundNo();

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
        // 2026-09-18：事件改为手写 payload 后置发布，把采购单号/入库单号/供应商带进通知模板
        //（原 @Event 注解 payload 只有 purchaseOrderId，通知标题只能显示「内部编号」；
        //  bizId 同步改为入库单 id，使通知「去处理」跳到 /inventory/inbound?bizId= 能落到本单）
        Map<String, Object> createdPayload = new HashMap<>();
        createdPayload.put("bizType", "inventory");
        createdPayload.put("triggerUserId", SecurityUtils.getUserId());
        createdPayload.put("triggerUserName", SecurityUtils.getUsername());
        createdPayload.put("triggerRealName", SecurityUtils.getDisplayName());
        createdPayload.put("bizId", order.getInboundId());
        createdPayload.put("inboundId", order.getInboundId());
        createdPayload.put("inboundNo", order.getInboundNo());
        createdPayload.put("bizNo", order.getInboundNo());
        createdPayload.put("purchaseOrderId", purchaseOrderId);
        createdPayload.put("purchaseOrderNo", po.getOrderNo());
        createdPayload.put("sourceId", purchaseOrderId);
        createdPayload.put("sourceNo", po.getOrderNo());
        createdPayload.put("supplierId", po.getSupplierId());
        createdPayload.put("supplierName", po.getSupplierName());
        publishIqcEventAfterCommit("inventory.inbound.created_from_purchase", createdPayload);
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
        // dev-20260923-029（单号第 5 批）：号改号段 IN+yyMMdd+3（不再复用采购单号）；
        // 「已生成入库单明细量」改按 source_type/source_id 查 —— 原来按单号前缀 likeRight(采购单号)，
        // 拆号后必然查空 → alreadyIn=0 → 每次收货会把整额再入一遍（静默重复入库！）。
        List<InventoryInboundOrder> existingList = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryInboundOrder>()
                        .eq(InventoryInboundOrder::getSourceType, "PURCHASE")
                        .eq(InventoryInboundOrder::getSourceId, purchaseOrderId));
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
            log.info("采购订单{} 无待入库数量，跳过", purchaseOrderId);
            return null;
        }

        // dev-20260923-029（第 5 批）：每次收货一张**独立入库单**（各占新流水号，不再用 -2/-3 后缀）；
        // 状态=待审批，仓库确认后才加库存（2026-08-11 业务定稿：收货≠入库）
        final InventoryInboundOrder order;
        String inboundNo = nextInboundNo();
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

    /**
     * 采购/退货等入库单号（dev-20260923-029 单号第 5 批）：一律走号段 `biz_no_rule.inbound`
     * （实测前缀 IN + yyMMdd + 3 位日流水，形如 {@code IN260923001}），不再复用采购单号、也不再用时间戳拼号。
     */
    private String nextInboundNo() {
        return redisSequenceService.generateBusinessNumberByType("inbound", "IN", "yyMMdd", 3);
    }

    /**
     * 采购入库默认仓解析（2026-09-23 dev-20260923-007 重写，A+C）
     *
     * <p>口径（优先级由高到低）：
     * <ol>
     *   <li>物料主数据 {@code inventory_material.default_warehouse_id}（需为启用仓）；同一单内多物料默认仓不一致时，
     *       按“入库数量最大”的物料决定（其余物料会进同一张单，需人工拆分/改单）；</li>
     *   <li>默认仓缺失时回退“按物料类型聚合”：R 原料 / I 油墨 / A 辅料 → 原料仓（名称含“原料”，仓库类型 normal），
     *       F 成品 → 成品仓（warehouse_type=finished），两侧按数量多者胜、相同偏原料；</li>
     *   <li>都定不了才用兑底仓，并打 WARN（静默入错仓比报错危险得多）。</li>
     * </ol>
     *
     * <p>2026-09-23 修复背景（看板任务 2192）：原实现调 {@code selectAllEnabled()}，而它按 status='0' 过滤，
     * 与前端开关/存量数据（1=正常）不一致 → 永远返回空列表 → 静默落新建汇“兑底仓”1L（成品仓）；成品入库恰好对所以未暴露，
     * 原料入库（PO260923001 RM001585/RM001599）全部错落成品仓。
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
                log.warn("采购入库默认仓解析：无物料明细，回退仓库{}", fallbackWarehouseId);
                return fallbackWarehouseId;
            }

            Map<Long, InventoryMaterial> materialMap = inventoryMaterialMapper.selectBatchIds(materialIds).stream()
                    .collect(Collectors.toMap(InventoryMaterial::getMaterialId, m -> m, (a, b) -> a));
            List<InventoryWarehouse> enabledWarehouses = warehouseMapper.selectAllEnabled();
            Set<Long> enabledIds = enabledWarehouses.stream()
                    .map(InventoryWarehouse::getWarehouseId)
                    .collect(Collectors.toSet());

            // 1) 物料默认仓优先（C：接上已有字段）
            Map<Long, BigDecimal> qtyByDefaultWarehouse = new LinkedHashMap<>();
            BigDecimal rawQuantity = BigDecimal.ZERO;
            BigDecimal finishedQuantity = BigDecimal.ZERO;
            for (int i = 0; i < items.size(); i++) {
                BigDecimal quantity = i < quantities.size() ? quantities.get(i) : null;
                if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) continue;
                InventoryMaterial material = materialMap.get(items.get(i).getMaterialId());
                if (material == null) continue;
                Long defaultWarehouseId = material.getDefaultWarehouseId();
                if (defaultWarehouseId != null) {
                    if (enabledIds.contains(defaultWarehouseId)) {
                        qtyByDefaultWarehouse.merge(defaultWarehouseId, quantity, BigDecimal::add);
                    } else {
                        log.warn("物料{} 默认仓库{}不在启用仓列表，已忽略该默认仓",
                                material.getMaterialCode(), defaultWarehouseId);
                    }
                }
                String materialType = material.getMaterialType();
                if (MaterialEnums.Type.RAW.getValue().equals(materialType)
                        || MaterialEnums.Type.INK.getValue().equals(materialType)
                        || MaterialEnums.Type.AUXILIARY.getValue().equals(materialType)) {
                    rawQuantity = rawQuantity.add(quantity);
                } else if (MaterialEnums.Type.FINISHED.getValue().equals(materialType)) {
                    finishedQuantity = finishedQuantity.add(quantity);
                }
            }
            if (!qtyByDefaultWarehouse.isEmpty()) {
                Long matchedId = qtyByDefaultWarehouse.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
                if (matchedId != null) {
                    return matchedId;
                }
            }

            // 2) 类型聚合回退
            if (rawQuantity.signum() == 0 && finishedQuantity.signum() == 0) {
                log.warn("采购入库默认仓解析：物料既无默认仓也无匹配类型，回退仓库{}（materialIds={}）",
                        fallbackWarehouseId, materialIds);
                return fallbackWarehouseId;
            }
            boolean useRawWarehouse = rawQuantity.compareTo(finishedQuantity) >= 0;
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
            if (matched == null) {
                log.warn("采购入库默认仓解析：未匹配到可用仓库（raw={} / finished={} / 启用仓={}），回退仓库{}",
                        rawQuantity, finishedQuantity, enabledWarehouses.size(), fallbackWarehouseId);
                return fallbackWarehouseId;
            }
            return matched.getWarehouseId();
        } catch (Exception e) {
            log.warn("采购入库默认仓映射失败，回退仓库{}: {}", fallbackWarehouseId, e.getMessage());
            return fallbackWarehouseId;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromProduction(Long workOrderId) {
        com.jjx.quality.service.QualityLotService lotService = qualityLotServiceProvider.getIfAvailable();
        if (lotService != null) {
            List<com.jjx.quality.domain.entity.QualityLot> lots = lotService.listByOrder(workOrderId, null).stream()
                    .filter(lot -> "FQC".equals(lot.getLotType()))
                    .toList();
            if (!lots.isEmpty()) {
                java.util.Set<Long> superseded = lots.stream()
                        .map(com.jjx.quality.domain.entity.QualityLot::getParentLotId)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());
                Long lastInboundId = null;
                for (com.jjx.quality.domain.entity.QualityLot lot : lots) {
                    if (!superseded.contains(lot.getLotId()) && lot.getInspectedQuantity() != null
                            && lot.getInspectedQuantity().signum() > 0
                            && Objects.requireNonNullElse(lot.getPassQuantity(), BigDecimal.ZERO).signum() > 0) {
                        lastInboundId = createFromProduction(workOrderId, lot.getLotId(), lot.getPassQuantity());
                    }
                }
                return lastInboundId;
            }
        }
        Long createdId = createFromProduction(workOrderId, null, null);
        // dev-20260923（022 收尾核查）：以上为空说明该工单还没有「已判定的有效检验批」（如完工早于检验），
        // 此时才退回「工单级兜底单」FINISH-<工单号> / 批次 BATCH-<工单号>（历史兼容路径，仅在建单时兜底，
        // 与判定路径的按批单不会并存——完工路径已有防重：该工单存在任何未取消生产入库单就不再建单）。
        log.info("工单{}无有效检验批，走工单级兜底入库单: inboundId={}", workOrderId, createdId);
        return createdId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromProduction(Long workOrderId, Long lotId, BigDecimal inspectedPassQty) {
        log.info("从生产工单创建入库单: workOrderId={}", workOrderId);

        // 1. 查询生产工单
        ProductionOrder prodOrder = productionOrderMapper.selectById(workOrderId);
        if (prodOrder == null) {
            throw new BusinessException("生产工单不存在: " + workOrderId);
        }

        // DEV-936（2026-08-12）：工单未完工禁止生成完工入库单（与 DEV-053 完工质检门一致），
        // 否则 finishedQuantity=0 导致入库数量记 0、库存不入账
        if (lotId == null && !com.jjx.production.enums.ProductionOrderStatusEnum.COMPLETED.getValue().equals(prodOrder.getOrderStatus())) {
            String statusName = "状态码" + prodOrder.getOrderStatus();
            try {
                var pe = com.jjx.production.enums.ProductionOrderStatusEnum.getByValue(prodOrder.getOrderStatus());
                statusName = pe.getLabel();
            } catch (Exception ignored) {}
            throw new BusinessException("工单未完工，不能生成完工入库单（当前状态：" + statusName + "）");
        }

        // 2. 创建入库单
        if (lotId == null) {
            // dev-20260923（022 收尾 · 防重复入库）：旧防重只查 "FINISH-<工单号>-FQC-" 前缀，而按检验批出单
            // 后的新单名是 "<工单号>-FI<NN>" → 匹配不到，导致完工/重试路径照样再建一张工单级单，
            // 与批单叠加后仓库都确认即双倍入库。现改为：该工单只要已有任何未取消的生产入库单就不新建。
            Long existingAny = inboundOrderMapper.selectCount(
                    new LambdaQueryWrapper<InventoryInboundOrder>()
                            .eq(InventoryInboundOrder::getSourceType, "PRODUCTION")
                            .eq(InventoryInboundOrder::getSourceId, workOrderId)
                            .ne(InventoryInboundOrder::getOrderStatus, InventoryOrderStatusEnum.CANCELLED.getValue()));
            if (existingAny != null && existingAny > 0) {
                log.info("工单{}已存在生产入库单（按批或历史），完工/重试路径不再重复建单", workOrderId);
                return null;
            }
        }
        if (lotId != null) {
            InventoryInboundItem existingLotItem = inboundItemMapper.selectOne(
                    new LambdaQueryWrapper<InventoryInboundItem>()
                            .eq(InventoryInboundItem::getLotId, lotId)
                            .orderByAsc(InventoryInboundItem::getItemId)
                            .last("LIMIT 1"));
            if (existingLotItem != null) {
                // dev-20260923（022 收尾）：同一批「再次判定/改判」→ 更新该批单的应入量；
                // 已过账则退回「待确认」，由仓库点确认入库按差额过账（加/减都走确认，见 dev-20260922-020）。
                InventoryInboundOrder existOrder = inboundOrderMapper.selectById(existingLotItem.getInboundId());
                BigDecimal oldQty = existingLotItem.getQuantity() == null ? BigDecimal.ZERO : existingLotItem.getQuantity();
                BigDecimal newQty = inspectedPassQty == null ? oldQty : inspectedPassQty;
                if (existOrder != null && newQty.compareTo(oldQty) != 0) {
                    existingLotItem.setQuantity(newQty);
                    inboundItemMapper.updateById(existingLotItem);
                    existOrder.setTotalQuantity(newQty);
                    if (isPostedInboundStatus(existOrder.getOrderStatus())) {
                        existOrder.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
                    }
                    existOrder.setRemark(appendRemark(existOrder.getRemark(),
                            "成品检验改判：" + oldQty.toPlainString() + " → " + newQty.toPlainString() + "，待确认入库"));
                    inboundOrderMapper.updateById(existOrder);
                    log.info("成品检验批改判已更新批单: lotId={} order={} {} -> {}",
                            lotId, existOrder.getInboundNo(), oldQty.toPlainString(), newQty.toPlainString());
                }
                return existingLotItem.getInboundId();
            }
        }
        String inboundNo;
        if (lotId == null) {
            inboundNo = "FINISH-" + prodOrder.getOrderNo();
        } else {
            List<InventoryInboundOrder> finishOrders = inboundOrderMapper.selectList(new LambdaQueryWrapper<InventoryInboundOrder>()
                    .eq(InventoryInboundOrder::getSourceType, "PRODUCTION")
                    .eq(InventoryInboundOrder::getSourceId, workOrderId)
                    .likeRight(InventoryInboundOrder::getInboundNo, prodOrder.getOrderNo() + "-FI"));
            // dev-20260923-032（看板 2250）：序号改为按「-FI 之后的全部数字」解析。
            // 原实现 = 正则 -FI\d{2} 只认 2 位 + substring(len-2) 取后两位 + %02d 生成：
            // 第 100 张写出 -FI100 后，正则匹配不到它 → max 回退到 99 → 目标恒为 100 → 与已有单撞号
            // → 静默 return null，该工单**每一张新批单都建不出来**（无异常、无上层感知）。
            final String fiPrefix = prodOrder.getOrderNo() + "-FI";
            final java.util.regex.Pattern fiPattern =
                    java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(fiPrefix) + "(\\d+)$");
            int sequence = finishOrders.stream()
                    .map(InventoryInboundOrder::getInboundNo)
                    .filter(Objects::nonNull)
                    .map(fiPattern::matcher)
                    .filter(java.util.regex.Matcher::find)
                    .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                    .max().orElse(0) + 1;
            if (sequence > 99) {
                log.warn("工单{}完工入库单 FI 序号已到 {}（超过 99，自动进位到 3 位）", workOrderId, sequence);
            }
            inboundNo = fiPrefix + String.format("%02d", sequence);
            // 兵底防重：不再因撞号静默丢单——向后找第一个空号（上限 100 次，通常一次即中）
            int guard = 0;
            while (existsInboundNo(inboundNo)) {
                if (++guard > 100) {
                    log.error("工单{}完工入库单号生成失败：连续 100 个号已被占用，最后尝试 {}", workOrderId, inboundNo);
                    return null;
                }
                sequence++;
                inboundNo = fiPrefix + String.format("%02d", sequence);
            }
        }
        if (lotId == null) {
            // 工单级兜底单仍按单号防重（历史兼容路径的正规去重：撞上说明该工单已有兜底单）
            if (existsInboundNo(inboundNo)) {
                log.warn("生产工单{}的完工入库单已存在", workOrderId);
                return null;
            }
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

        // 3. 创建入库明细
        // 2026-09-21 订正注释（原写「物料=成品物料档案 F类型，产品ID→物料ID映射」与实现不符）：
        // 成品入库身份走 inventory_item(item_type=PRODUCT)，material_id 故意留空 ——
        // 全库没有 F 类成品物料档案（inventory_material.material_type='F' 为 0 行），
        // 所以成品不参与「按 material_id」的库存/流水关联与安全库存预警（设计口径，非漏写）。
        InventoryInboundItem inboundItem = new InventoryInboundItem();
        inboundItem.setInboundId(order.getInboundId());
        String materialCode = prodOrder.getProductCode();
        String materialName = prodOrder.getProductName();
        inboundItem.setInventoryItemId(inventoryItemService.ensure(
                InventoryItemTypeEnum.PRODUCT, prodOrder.getProductId(), materialCode,
                materialName, null, "PCS").getInventoryItemId());
        inboundItem.setMaterialCode(materialCode);
        inboundItem.setMaterialName(materialName);
        inboundItem.setLotId(lotId);
        // 068定稿：入库产品数量=最后一道工序/完工检验合格数（052口径 finishedQuantity，非工序汇总 completedQuantity）
        BigDecimal inboundQty = inspectedPassQty != null ? inspectedPassQty
                : (prodOrder.getFinishedQuantity() != null && prodOrder.getFinishedQuantity().compareTo(BigDecimal.ZERO) > 0)
                ? prodOrder.getFinishedQuantity()
                : (prodOrder.getCompletedQuantity() != null ? prodOrder.getCompletedQuantity() : prodOrder.getPlannedQuantity());
        inboundItem.setQuantity(inboundQty);
        // dev-20260923（022 收尾）：批次号按用户口径改为 BATCH-<检验批号>（批号全局唯一、短且可读）；
        // 取不到批号时回退 lotId，保证仍有唯一值。老批次 BATCH-<工单号> 保留共存。
        String lotNoForBatch = null;
        if (lotId != null) {
            com.jjx.quality.domain.entity.QualityLot lotForBatch = qualityLotMapper.selectById(lotId);
            lotNoForBatch = lotForBatch == null ? null : lotForBatch.getLotNo();
        }
        inboundItem.setBatchNo(lotId == null ? "BATCH-" + prodOrder.getOrderNo()
                : "BATCH-" + (lotNoForBatch != null ? lotNoForBatch : lotId));
        inboundItem.setSortOrder(1);
        inboundItemMapper.insert(inboundItem);

        // 2026-09-21 dev-20260921-031：补齐表头汇总。此前完工入库单 total_quantity 恒为 0
        // （列表/详情「总数量」显示 0，实单 FINISH-WO-PL2609210001-01 明细 2 表头 0）；
        // 手动建单路径在 :306-311 有写，完工入库两条路径都漏了。
        order.setTotalQuantity(inboundQty);
        if (inboundItem.getAmount() != null) {
            order.setTotalAmount(inboundItem.getAmount());
        }

        // 4. 提交审批，待人工确认后过账
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.updateById(order);

        log.info("生产完工入库完成: workOrderId={}, inboundId={}", workOrderId, order.getInboundId());
        publishInboundEvent("inventory.inbound.created_from_production", order.getInboundId());
        return order.getInboundId();
    }

    // ==================== dev-20260923-043：返工退料（净耗 = 补料 − 退料） ====================

    @Override
    public List<java.util.Map<String, Object>> returnPreview(Long ncrId) {
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        if (ncrId == null) {
            return rows;
        }
        java.util.Map<String, java.util.Map<String, Object>> byKey = new java.util.LinkedHashMap<>();
        // ① 补料明细（该不良单的补料出库单：supplement_ncr_id = ncrId，未取消）
        try {
            List<com.jjx.inventory.domain.InventoryOutboundOrder> suppOrders = outboundOrderMapper.selectList(
                    new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryOutboundOrder>()
                            .eq(com.jjx.inventory.domain.InventoryOutboundOrder::getSupplementNcrId, ncrId)
                            .ne(com.jjx.inventory.domain.InventoryOutboundOrder::getOrderStatus,
                                    InventoryOrderStatusEnum.CANCELLED.getValue()));
            for (com.jjx.inventory.domain.InventoryOutboundOrder so : suppOrders) {
                for (com.jjx.inventory.domain.InventoryOutboundItem it : outboundItemMapper.selectList(
                        new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryOutboundItem>()
                                .eq(com.jjx.inventory.domain.InventoryOutboundItem::getOutboundId, so.getOutboundId()))) {
                    String key = (it.getMaterialCode() == null ? "" : it.getMaterialCode())
                            + "|" + (it.getBatchNo() == null ? "" : it.getBatchNo());
                    java.util.Map<String, Object> row = byKey.computeIfAbsent(key, k -> {
                        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("materialId", it.getMaterialId());
                        m.put("inventoryItemId", it.getInventoryItemId());
                        m.put("materialCode", it.getMaterialCode());
                        m.put("materialName", it.getMaterialName());
                        m.put("unit", it.getUnit());
                        m.put("batchNo", it.getBatchNo());
                        m.put("supplementQty", BigDecimal.ZERO);
                        m.put("returnedQty", BigDecimal.ZERO);
                        m.put("returnableQty", BigDecimal.ZERO);
                        return m;
                    });
                    row.put("supplementQty", ((BigDecimal) row.get("supplementQty")).add(nzQty(it.getQuantity())));
                }
            }
        } catch (Exception e) {
            log.warn("返工退料预览：读补料明细失败 ncrId={} err={}", ncrId, e.getMessage());
        }
        // ② 已退（本不良单的退料入库单：source_type=QUALITY_NCR）
        try {
            List<InventoryInboundOrder> rtnOrders = inboundOrderMapper.selectList(
                    new LambdaQueryWrapper<InventoryInboundOrder>()
                            .eq(InventoryInboundOrder::getSourceType, "QUALITY_NCR")
                            .eq(InventoryInboundOrder::getSourceId, ncrId)
                            .ne(InventoryInboundOrder::getOrderStatus, InventoryOrderStatusEnum.CANCELLED.getValue()));
            for (InventoryInboundOrder ro : rtnOrders) {
                for (InventoryInboundItem it : inboundItemMapper.selectList(
                        new LambdaQueryWrapper<InventoryInboundItem>()
                                .eq(InventoryInboundItem::getInboundId, ro.getInboundId()))) {
                    for (java.util.Map<String, Object> row : byKey.values()) {
                        if (java.util.Objects.equals(row.get("materialCode"), it.getMaterialCode())) {
                            row.put("returnedQty", ((BigDecimal) row.get("returnedQty")).add(nzQty(it.getQuantity())));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("返工退料预览：读已退量失败 ncrId={} err={}", ncrId, e.getMessage());
        }
        for (java.util.Map<String, Object> row : byKey.values()) {
            BigDecimal returnable = ((BigDecimal) row.get("supplementQty"))
                    .subtract((BigDecimal) row.get("returnedQty")).max(BigDecimal.ZERO);
            row.put("returnableQty", returnable);
            rows.add(row);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProductionReturnInbound(Long orderId, Long ncrId,
                                              List<java.util.Map<String, Object>> items, String reason) {
        if (orderId == null || ncrId == null) {
            throw new BusinessException("返工退料必须关联生产工单与质量不良单");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("退料明细不能为空");
        }
        com.jjx.quality.domain.entity.QualityNcr ncr = qualityNcrMapper.selectById(ncrId);
        if (ncr == null) {
            throw new BusinessException("不良台账不存在: " + ncrId);
        }
        ProductionOrder order = productionOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }
        // 净耗口径：可退 = 补料 − 已退（逐物料）
        java.util.Map<String, BigDecimal> canReturn = new java.util.HashMap<>();
        for (java.util.Map<String, Object> r : returnPreview(ncrId)) {
            canReturn.put(String.valueOf(r.get("materialCode")), (BigDecimal) r.get("returnableQty"));
        }
        String prefix = "RTN-" + order.getOrderNo() + "-" + ncr.getNcrNo() + "-";
        Long existed = inboundOrderMapper.selectCount(
                new LambdaQueryWrapper<InventoryInboundOrder>().likeRight(InventoryInboundOrder::getInboundNo, prefix));
        String inboundNo = prefix + ((existed == null ? 0 : existed) + 1);

        InventoryInboundOrder io = new InventoryInboundOrder();
        io.setInboundNo(inboundNo);
        io.setInboundType("PRODUCTION_RETURN");
        io.setSourceType("QUALITY_NCR");
        io.setSourceId(ncrId);
        io.setSourceNo(ncr.getNcrNo());
        io.setInboundDate(LocalDate.now());
        try {
            InventoryWarehouse wh = warehouseMapper.selectOne(new LambdaQueryWrapper<InventoryWarehouse>()
                    .eq(InventoryWarehouse::getStatus, 1).orderByAsc(InventoryWarehouse::getWarehouseId).last("LIMIT 1"));
            if (wh != null) {
                io.setWarehouseId(wh.getWarehouseId());
            }
        } catch (Exception e) {
            log.warn("返工退料：获取默认仓库失败 {}", e.getMessage());
        }
        io.setOrderStatus(InventoryOrderStatusEnum.DRAFT.getValue());
        io.setRemark("返工退料（工单 " + order.getOrderNo() + " / 不良单 " + ncr.getNcrNo()
                + (reason == null || reason.isBlank() ? "" : "，原因：" + reason.trim()) + "）");
        inboundOrderMapper.insert(io);

        BigDecimal totalQty = BigDecimal.ZERO;
        int sort = 1;
        for (java.util.Map<String, Object> row : items) {
            BigDecimal qty = row.get("quantity") == null ? BigDecimal.ZERO
                    : new BigDecimal(String.valueOf(row.get("quantity")));
            if (qty.signum() <= 0) {
                continue;
            }
            String materialCode = row.get("materialCode") == null ? null : String.valueOf(row.get("materialCode"));
            BigDecimal allow = canReturn.getOrDefault(materialCode, BigDecimal.ZERO);
            if (allow.signum() <= 0) {
                throw new BusinessException("物料[" + materialCode + "]没有补料记录（或已全部退回），不能退料");
            }
            if (qty.compareTo(allow) > 0) {
                throw new BusinessException("物料[" + materialCode + "]退料量 " + qty.toPlainString()
                        + " 超过可退量 " + allow.toPlainString() + "（净耗 = 补料 − 已退）");
            }
            Long materialId = row.get("materialId") == null ? null : Long.valueOf(String.valueOf(row.get("materialId")));
            Long inventoryItemId = row.get("inventoryItemId") == null ? null
                    : Long.valueOf(String.valueOf(row.get("inventoryItemId")));
            if (inventoryItemId == null) {
                if (materialId == null) {
                    throw new BusinessException("物料[" + materialCode + "]缺少物料ID，无法退料");
                }
                com.jjx.inventory.domain.InventoryItem inventoryItem = inventoryItemService.ensure(
                        InventoryItemTypeEnum.MATERIAL,
                        materialId, materialCode, String.valueOf(row.get("materialName")),
                        null, row.get("unit") == null ? "PCS" : String.valueOf(row.get("unit")));
                inventoryItemId = inventoryItem.getInventoryItemId();
            }
            InventoryInboundItem item = new InventoryInboundItem();
            item.setInboundId(io.getInboundId());
            item.setInventoryItemId(inventoryItemId);
            item.setMaterialId(materialId);
            item.setMaterialCode(materialCode);
            item.setMaterialName(row.get("materialName") == null ? null : String.valueOf(row.get("materialName")));
            item.setUnit(row.get("unit") == null ? null : String.valueOf(row.get("unit")));
            item.setQuantity(qty);
            // A 方案：批次回**原发料批次**（可追溯；由 returnPreview 带回）
            item.setBatchNo(row.get("batchNo") == null ? null : String.valueOf(row.get("batchNo")));
            item.setSortOrder(sort++);
            inboundItemMapper.insert(item);
            totalQty = totalQty.add(qty);
        }
        if (sort == 1) {
            throw new BusinessException("没有可退的退料明细（数量都为 0）");
        }
        io.setTotalQuantity(totalQty);
        io.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.updateById(io);
        approve(io.getInboundId(), null, null, "返工退料");
        confirm(io.getInboundId(), null, "返工退料");
        log.info("返工退料完成: inboundNo={} 工单={} 不良单={} 数量={}", inboundNo,
                order.getOrderNo(), ncr.getNcrNo(), totalQty.toPlainString());
        return io.getInboundId();
    }

    private static BigDecimal nzQty(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 客户拒收回库（2026-09-21 dev-20260921-039，拒收回流）。
     *
     * <p>客户拒收后货在客户端，但账上库存已被销售发货出库扣走 —— 本方法按发货单明细生成「拒收回库单」
     * （source_type=SALES_RETURN，单号 REJECT-{发货单号}）并自动审批 + 过账，把数量回冲到成品库存，
     * 仓库无需手工建单入库。拆封/损坏的货请走退货/报废流程（本方法按“可再售”口径回库）。</p>
     *
     * @return 拒收回库单ID；发货单无明细（历史数据）时返回 null（不回冲，仅告警）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSalesRejectInbound(Long deliveryId) {
        com.jjx.sales.domain.entity.SalesDelivery delivery = salesDeliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException("发货单不存在: " + deliveryId);
        }
        String inboundNo = "REJECT-" + delivery.getDeliveryNo();
        List<InventoryInboundOrder> exists = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryInboundOrder>().eq(InventoryInboundOrder::getInboundNo, inboundNo));
        if (!exists.isEmpty()) {
            log.info("拒收回库单已存在，跳过重复创建: {}", inboundNo);
            return exists.get(0).getInboundId();
        }
        List<com.jjx.sales.domain.entity.SalesDeliveryItem> deliveryItems = salesDeliveryItemMapper.selectList(
                new LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesDeliveryItem>()
                        .eq(com.jjx.sales.domain.entity.SalesDeliveryItem::getDeliveryId, deliveryId));
        if (deliveryItems.isEmpty()) {
            log.warn("发货单{}无明细，无法按明细回冲库存（历史数据，请人工处理）", delivery.getDeliveryNo());
            return null;
        }

        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundNo(inboundNo);
        order.setInboundType("SALES_RETURN");
        order.setSourceType("SALES_RETURN");
        order.setSourceId(deliveryId);
        order.setSourceNo(delivery.getDeliveryNo());
        order.setInboundDate(LocalDate.now());
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
        String reason = delivery.getRejectReason();
        order.setRemark("客户拒收回库（发货单 " + delivery.getDeliveryNo()
                + (reason == null || reason.isBlank() ? "" : "，原因：" + reason) + "）");
        inboundOrderMapper.insert(order);

        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        int sort = 1;
        for (com.jjx.sales.domain.entity.SalesDeliveryItem item : deliveryItems) {
            BigDecimal quantity = BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            com.jjx.inventory.domain.InventoryItem inventoryItem = inventoryItemService.ensure(
                    InventoryItemTypeEnum.PRODUCT, item.getProductId(), item.getProductCode(),
                    item.getProductName(), null,
                    item.getUnit() == null || item.getUnit().isBlank() ? "PCS" : item.getUnit());
            InventoryInboundItem inboundItem = new InventoryInboundItem();
            inboundItem.setInboundId(order.getInboundId());
            inboundItem.setInventoryItemId(inventoryItem.getInventoryItemId());
            inboundItem.setMaterialCode(item.getProductCode());
            inboundItem.setMaterialName(item.getProductName());
            inboundItem.setQuantity(quantity);
            inboundItem.setUnitPrice(item.getUnitPrice());
            inboundItem.setSortOrder(sort++);
            inboundItemMapper.insert(inboundItem);
            totalQty = totalQty.add(quantity);
            if (item.getAmount() != null) {
                totalAmount = totalAmount.add(item.getAmount());
            }
        }
        if (sort == 1) {
            log.warn("发货单{}明细数量全为 0，无回冲内容", delivery.getDeliveryNo());
            return null;
        }
        order.setTotalQuantity(totalQty);
        order.setTotalAmount(totalAmount);
        order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        inboundOrderMapper.updateById(order);
        approve(order.getInboundId(), null, null, "客户拒收回库");
        confirm(order.getInboundId(), null, "客户拒收回库");
        log.info("客户拒收回库完成: deliveryNo={}, inboundNo={}, 数量={}",
                delivery.getDeliveryNo(), inboundNo, totalQty);
        return order.getInboundId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal syncFinishInbound(Long orderId, Long lotId, BigDecimal targetQuantity, String reason) {
        if (orderId == null || targetQuantity == null) {
            return BigDecimal.ZERO;
        }
        ProductionOrder prodOrder = productionOrderMapper.selectById(orderId);
        if (prodOrder == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }
        List<InventoryInboundOrder> exists = inboundOrderMapper.selectList(new LambdaQueryWrapper<InventoryInboundOrder>()
                .eq(InventoryInboundOrder::getSourceType, "PRODUCTION")
                .eq(InventoryInboundOrder::getSourceId, orderId)
                .ne(InventoryInboundOrder::getOrderStatus, InventoryOrderStatusEnum.CANCELLED.getValue())
                .orderByAsc(InventoryInboundOrder::getInboundId));
        InventoryInboundOrder order = exists.isEmpty() ? null : exists.get(0);
        InventoryInboundItem item = null;
        if (lotId != null) {
            item = inboundItemMapper.selectOne(new LambdaQueryWrapper<InventoryInboundItem>()
                    .eq(InventoryInboundItem::getLotId, lotId)
                    .orderByAsc(InventoryInboundItem::getItemId)
                    .last("LIMIT 1"));
            order = item == null ? null : inboundOrderMapper.selectById(item.getInboundId());
        }
        BigDecimal current = BigDecimal.ZERO;
        if (order != null) {
            if (item == null) {
                item = inboundItemMapper.selectOne(new LambdaQueryWrapper<InventoryInboundItem>()
                        .eq(InventoryInboundItem::getInboundId, order.getInboundId())
                        .orderByAsc(InventoryInboundItem::getItemId)
                        .last("LIMIT 1"));
            }
            current = (item == null || item.getQuantity() == null) ? BigDecimal.ZERO : item.getQuantity();
        }
        BigDecimal delta = targetQuantity.subtract(current);
        if (delta.signum() == 0) {
            return BigDecimal.ZERO;
        }
        boolean posted = order != null && isPostedInboundStatus(order.getOrderStatus());

        if (order == null) {
            if (delta.signum() < 0) {
                return BigDecimal.ZERO;
            }
            order = new InventoryInboundOrder();
            order.setInboundNo("FINISH-" + prodOrder.getOrderNo());
            order.setInboundType("PRODUCTION_FINISH");
            order.setSourceType("PRODUCTION");
            order.setSourceId(orderId);
            order.setSourceNo(prodOrder.getOrderNo());
            order.setTraceId(prodOrder.getTraceId());
            order.setInboundDate(java.time.LocalDate.now());
            try {
                InventoryWarehouse defaultWh = warehouseMapper.selectOne(new LambdaQueryWrapper<InventoryWarehouse>()
                        .eq(InventoryWarehouse::getStatus, 1)
                        .orderByAsc(InventoryWarehouse::getWarehouseId)
                        .last("LIMIT 1"));
                if (defaultWh != null) {
                    order.setWarehouseId(defaultWh.getWarehouseId());
                }
            } catch (Exception e) {
                log.warn("获取默认仓库失败: {}", e.getMessage());
            }
            order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
            inboundOrderMapper.insert(order);

            item = new InventoryInboundItem();
            item.setInboundId(order.getInboundId());
            item.setInventoryItemId(inventoryItemService.ensure(InventoryItemTypeEnum.PRODUCT, prodOrder.getProductId(),
                    prodOrder.getProductCode(), prodOrder.getProductName(), null, "PCS").getInventoryItemId());
            item.setMaterialCode(prodOrder.getProductCode());
            item.setMaterialName(prodOrder.getProductName());
            item.setQuantity(targetQuantity);
            item.setBatchNo("BATCH-" + prodOrder.getOrderNo());
            item.setSortOrder(1);
            inboundItemMapper.insert(item);
            // 2026-09-21 dev-20260921-031：表头汇总（此前只写明细、表头恒为 0）
            order.setTotalQuantity(targetQuantity);
            inboundOrderMapper.updateById(order);
            log.info("完工入库单已建（差额同步）: order={} 数量={} lotId={}",
                    prodOrder.getOrderNo(), targetQuantity.toPlainString(), lotId);
            // dev-20260922-020（口径 B）：新建即通知/待办仓库去「确认入库」
            publishInboundEvent("inventory.inbound.created_from_production", order.getInboundId());
            return delta;
        }

        if (item != null) {
            item.setQuantity(targetQuantity);
            inboundItemMapper.updateById(item);
            // 2026-09-21 dev-20260921-031：明细改了、表头也要跟着（此前表头恒为 0）
            order.setTotalQuantity(targetQuantity);
            inboundOrderMapper.updateById(order);
        }
        // dev-20260922-020（口径 B：一切以「确认入库」为准，用户 2026-09-22 明确要求）：
        // 判定/复检只维护入库单的「应入数量」，**一律不动库存、不写库存流水**。
        // · 未过账 → 数量已更正，本就在等仓库确认
        // · 已过账且应入 ≠ 已入 → 把单据退回「待处理」，仓库点「确认入库」时按差额过账
        //   （补入走 addStock；应入 < 已入 的冲减走 confirm → reducePostedStock）
        if (item != null && delta.signum() != 0) {
            if (posted) {
                Integer originStatus = order.getOrderStatus();
                order.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
                order.setRemark(appendRemark(order.getRemark(),
                        "成品检验更正：" + (delta.signum() > 0 ? "补入 " : "冲减 ") + delta.abs().toPlainString()
                                + "（" + (reason == null ? "-" : reason) + "），待确认入库"));
                inboundOrderMapper.updateById(order);
                log.info("成品入库单已退回待确认（原状态={}）: order={} {} -> {} delta={} lotId={} reason={}",
                        originStatus, prodOrder.getOrderNo(), current.toPlainString(),
                        targetQuantity.toPlainString(), delta.toPlainString(), lotId, reason);
            } else {
                log.info("完工入库数量已更正（未过账）: order={} {} -> {} delta={} lotId={} reason={}",
                        prodOrder.getOrderNo(), current.toPlainString(), targetQuantity.toPlainString(),
                        delta.toPlainString(), lotId, reason);
            }
            // 通知/待办仓库：有成品入库单待确认（数量已变更）
            publishInboundEvent("inventory.inbound.created_from_production", order.getInboundId());
        }
        return delta;
    }

    /** 备注追加（列宽 500，超长截断） */
    private String appendRemark(String origin, String addition) {
        String base = origin == null ? "" : origin.trim();
        String next = base.isEmpty() ? addition : base + " ｜ " + addition;
        return next.length() > 500 ? next.substring(0, 500) : next;
    }

    /**
     * dev-20260923（022 收尾 · 复检换代）：处理「原检验批」那张入库单。
     * · 未过账 → 直接作废（备注原因），不再占着待确认位；
     * · 已过账 → 生成一张**红冲单**（负数量、待仓库确认入库；确认时走 dev-20260922-020 的冲减分支）。
     * 取代原先「按工单 target=0 冲销」的写法——那会把整张工单级单冲掉，与按批单叠加导致重复入库。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleSupersededLotInbound(Long lotId, String reason) {
        if (lotId == null) {
            return;
        }
        InventoryInboundItem lotItem = inboundItemMapper.selectOne(new LambdaQueryWrapper<InventoryInboundItem>()
                .eq(InventoryInboundItem::getLotId, lotId)
                .orderByAsc(InventoryInboundItem::getItemId)
                .last("LIMIT 1"));
        if (lotItem == null) {
            return;
        }
        InventoryInboundOrder order = inboundOrderMapper.selectById(lotItem.getInboundId());
        if (order == null) {
            return;
        }
        String tag = reason == null ? "复检换代" : reason;
        if (!isPostedInboundStatus(order.getOrderStatus())) {
            order.setOrderStatus(InventoryOrderStatusEnum.CANCELLED.getValue());
            order.setRemark(appendRemark(order.getRemark(), "作废（" + tag + "）"));
            inboundOrderMapper.updateById(order);
            log.info("原批入库单已作废: order={} lotId={} reason={}", order.getInboundNo(), lotId, tag);
            return;
        }
        BigDecimal posted = Objects.requireNonNullElse(lotItem.getPostedQuantity(), BigDecimal.ZERO);
        if (posted.signum() <= 0) {
            return;
        }
        String reverseNo = order.getInboundNo() + "-R";
        Long dup = inboundOrderMapper.selectCount(new LambdaQueryWrapper<InventoryInboundOrder>()
                .eq(InventoryInboundOrder::getInboundNo, reverseNo));
        if (dup != null && dup > 0) {
            log.info("红冲单已存在，跳过: {}", reverseNo);
            return;
        }
        InventoryInboundOrder reverse = new InventoryInboundOrder();
        reverse.setInboundNo(reverseNo);
        reverse.setInboundType(order.getInboundType());
        reverse.setSourceType(order.getSourceType());
        reverse.setSourceId(order.getSourceId());
        reverse.setSourceNo(order.getSourceNo());
        reverse.setTraceId(order.getTraceId());
        reverse.setWarehouseId(order.getWarehouseId());
        reverse.setLocationId(order.getLocationId());
        reverse.setInboundDate(java.time.LocalDate.now());
        reverse.setOrderStatus(InventoryOrderStatusEnum.PENDING.getValue());
        reverse.setTotalQuantity(posted.negate());
        reverse.setRemark("红冲 " + order.getInboundNo() + "（" + tag + "），待确认入库");
        inboundOrderMapper.insert(reverse);

        InventoryInboundItem reverseItem = new InventoryInboundItem();
        reverseItem.setInboundId(reverse.getInboundId());
        reverseItem.setInventoryItemId(lotItem.getInventoryItemId());
        reverseItem.setMaterialId(lotItem.getMaterialId());
        reverseItem.setMaterialCode(lotItem.getMaterialCode());
        reverseItem.setMaterialName(lotItem.getMaterialName());
        reverseItem.setQuantity(posted.negate());
        reverseItem.setBatchNo(lotItem.getBatchNo());
        reverseItem.setLotId(lotId);
        reverseItem.setSortOrder(1);
        reverseItem.setRemark("红冲 " + order.getInboundNo());
        inboundItemMapper.insert(reverseItem);

        publishInboundEvent("inventory.inbound.created_from_production", reverse.getInboundId());
        // 2026-09-23（用户建议①）：原单补反向标记（写在备注里），从原单也能查到冲销链
        order.setRemark(appendRemark(order.getRemark(), "已被 " + reverseNo + " 冲销（" + tag + "）"));
        inboundOrderMapper.updateById(order);
        log.info("已生成红冲单: {} 冲减 {}（原单 {}，lotId={}）", reverseNo, posted.toPlainString(),
                order.getInboundNo(), lotId);
    }

    /**
     * dev-20260922-020（口径 B）：仓库点「确认入库」时处理「应入 < 已入」的冲减（复检判少/更正）。
     * 与 addStock 对称：减批次库存 + 写 ADJUST 负流水 + 回写明细 posted_quantity；库存不足直接报错（不做负库存）。
     */
    private void reducePostedStock(InventoryInboundOrder order, Long operatorId, String operatorName, BigDecimal netDelta) {
        if (netDelta == null || netDelta.signum() >= 0) {
            return;
        }
        for (InventoryInboundItem item : inboundItemMapper.selectByInboundId(order.getInboundId())) {
            BigDecimal target = item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity();
            BigDecimal postedQty = Objects.requireNonNullElse(item.getPostedQuantity(), BigDecimal.ZERO);
            BigDecimal reduce = postedQty.subtract(target);
            if (reduce.signum() <= 0) {
                continue;
            }
            InventoryStockItem stock = stockItemMapper.selectOne(new LambdaQueryWrapper<InventoryStockItem>()
                    .eq(InventoryStockItem::getInventoryItemId, item.getInventoryItemId())
                    .eq(InventoryStockItem::getBatchNo, item.getBatchNo())
                    .eq(InventoryStockItem::getStatus, 1)
                    .orderByAsc(InventoryStockItem::getItemId)
                    .last("LIMIT 1"));
            if (stock == null) {
                throw new BusinessException("成品库存批次不存在，无法冲减：" + item.getBatchNo());
            }
            BigDecimal before = Objects.requireNonNullElse(stock.getQuantity(), BigDecimal.ZERO);
            BigDecimal after = before.subtract(reduce);
            if (after.signum() < 0) {
                throw new BusinessException("冲减后库存将为负（批次 " + item.getBatchNo() + " 当前 "
                        + before.toPlainString() + "，需冲减 " + reduce.toPlainString()
                        + "），可能已发货，请人工核对后再处理");
            }
            InventoryTransaction tx = new InventoryTransaction();
            tx.setInventoryItemId(stock.getInventoryItemId());
            tx.setMaterialId(stock.getMaterialId());
            tx.setMaterialCode(stock.getMaterialCode());
            tx.setMaterialName(stock.getMaterialName());
            tx.setWarehouseId(stock.getWarehouseId());
            tx.setLocationId(stock.getLocationId());
            tx.setTransactionType("ADJUST");
            tx.setSourceType("PRODUCTION");
            tx.setSourceId(order.getSourceId());
            tx.setSourceNo(order.getSourceNo());
            tx.setBatchNo(item.getBatchNo());
            tx.setLotId(item.getLotId());
            tx.setUnitCost(stock.getUnitCost());
            tx.setAmount(stock.getUnitCost() == null ? null : stock.getUnitCost().multiply(reduce.negate()));
            tx.setTransactionTime(java.time.LocalDateTime.now());
            tx.setRemark("成品检验更正冲减（仓库确认入库）");
            tx.setOperatorId(operatorId);
            tx.setOperatorName(operatorName);
            stockMutationService.applyDelta(stock, reduce.negate(), tx);

            item.setPostedQuantity(target);
            inboundItemMapper.updateById(item);
            log.info("成品入库冲减: order={} batch={} 冲减={} 库存 {} -> {}",
                    order.getInboundNo(), item.getBatchNo(), reduce.toPlainString(),
                    before.toPlainString(), after.toPlainString());
        }
    }

    private static boolean isPostedInboundStatus(Integer status) {
        if (status == null) {
            return false;
        }
        return status == InventoryOrderStatusEnum.CONFIRMED.getValue()
                || status == InventoryOrderStatusEnum.OUT_CONFIRM.getValue()
                || status == InventoryOrderStatusEnum.IN_CONFIRM.getValue()
                || status == InventoryOrderStatusEnum.COMPLETED.getValue()
                || status == InventoryOrderStatusEnum.PROCESSED.getValue();
    }

    /**
     * dev-20260923（022 收尾）：解析成品库存批次号。
     * 口径随 022 变化：022 之前成品批次 = `BATCH-<工单号>`；022 之后 = `BATCH-<检验批号>`。
     * 以**该检验批入库明细上的批次号**为准（最权威），拿不到再按批号拼、最后回退老命名。
     */
    private String resolveProductBatchNo(ProductionOrder prodOrder, Long lotId) {
        if (lotId != null) {
            InventoryInboundItem lotItem = inboundItemMapper.selectOne(new LambdaQueryWrapper<InventoryInboundItem>()
                    .eq(InventoryInboundItem::getLotId, lotId)
                    .orderByAsc(InventoryInboundItem::getItemId)
                    .last("LIMIT 1"));
            if (lotItem != null && lotItem.getBatchNo() != null && !lotItem.getBatchNo().isBlank()) {
                return lotItem.getBatchNo();
            }
            com.jjx.quality.domain.entity.QualityLot lot = qualityLotMapper.selectById(lotId);
            if (lot != null && lot.getLotNo() != null && !lot.getLotNo().isBlank()) {
                return "BATCH-" + lot.getLotNo();
            }
        }
        return "BATCH-" + prodOrder.getOrderNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal adjustFinishStock(Long orderId, Long lotId, Long ncrId, BigDecimal deltaQuantity, String remark) {
        if (orderId == null || deltaQuantity == null || deltaQuantity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        ProductionOrder prodOrder = productionOrderMapper.selectById(orderId);
        if (prodOrder == null) {
            throw new BusinessException("生产工单不存在: " + orderId);
        }
        String batchNo = resolveProductBatchNo(prodOrder, lotId);
        InventoryStockItem stock = stockItemMapper.selectOne(new LambdaQueryWrapper<InventoryStockItem>()
                .eq(InventoryStockItem::getMaterialCode, prodOrder.getProductCode())
                .eq(InventoryStockItem::getBatchNo, batchNo)
                .orderByAsc(InventoryStockItem::getItemId)
                .last("LIMIT 1"));
        if (stock == null) {
            // dev-20260923（022 收尾）：兼容历史批次命名 —— 022 之前成品批次是 BATCH-<工单号>，之后是 BATCH-<检验批号>
            String legacyBatchNo = "BATCH-" + prodOrder.getOrderNo();
            if (!legacyBatchNo.equals(batchNo)) {
                stock = stockItemMapper.selectOne(new LambdaQueryWrapper<InventoryStockItem>()
                        .eq(InventoryStockItem::getMaterialCode, prodOrder.getProductCode())
                        .eq(InventoryStockItem::getBatchNo, legacyBatchNo)
                        .orderByAsc(InventoryStockItem::getItemId)
                        .last("LIMIT 1"));
                if (stock != null) {
                    batchNo = legacyBatchNo;
                }
            }
        }
        if (stock == null) {
            if (deltaQuantity.signum() > 0) {
                throw new BusinessException("成品库存批次不存在，无法调整（批次 " + batchNo + "）。请先完成成品入库。");
            }
            return BigDecimal.ZERO;
        }
        BigDecimal before = stock.getQuantity() == null ? BigDecimal.ZERO : stock.getQuantity();
        BigDecimal after = before.add(deltaQuantity);
        if (after.signum() < 0) {
            throw new BusinessException("库存调整后为负（当前 " + before.toPlainString() + "，调整 "
                    + deltaQuantity.toPlainString() + "），请人工核对");
        }
        InventoryTransaction tx = new InventoryTransaction();
        tx.setInventoryItemId(stock.getInventoryItemId());
        tx.setMaterialId(stock.getMaterialId());
        tx.setMaterialCode(stock.getMaterialCode());
        tx.setMaterialName(stock.getMaterialName());
        tx.setWarehouseId(stock.getWarehouseId());
        tx.setLocationId(stock.getLocationId());
        tx.setTransactionType("ADJUST");
        tx.setSourceType("PRODUCTION_QC");
        tx.setSourceId(orderId);
        tx.setSourceNo(prodOrder.getOrderNo());
        tx.setLotId(lotId);
        tx.setNcrId(ncrId);
        tx.setBatchNo(batchNo);
        tx.setUnitCost(stock.getUnitCost());
        tx.setAmount(stock.getUnitCost() == null ? null : stock.getUnitCost().multiply(deltaQuantity));
        tx.setTransactionTime(java.time.LocalDateTime.now());
        tx.setRemark(remark == null ? "不良处置引起的库存调整" : remark);
        try {
            tx.setOperatorId(SecurityUtils.getUserId());
            tx.setOperatorName(SecurityUtils.getDisplayName());
        } catch (Exception ignored) {
        }
        stockMutationService.applyDelta(stock, deltaQuantity, tx);
        log.info("成品库存调整完成(不良处置): order={} delta={} lotId={} ncrId={} remark={}",
                prodOrder.getOrderNo(), deltaQuantity.toPlainString(), lotId, ncrId, remark);
        return deltaQuantity;
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
        // dev-20260922-009：新模型下检验批存 lot_id（inspection_id 已置空），必须同时吐给前端
        vo.setLotId(item.getLotId());
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
