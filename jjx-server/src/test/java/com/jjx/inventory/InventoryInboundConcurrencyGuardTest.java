package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.InventoryInboundItem;
import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.mapper.InventoryInboundItemMapper;
import com.jjx.inventory.mapper.InventoryInboundOrderMapper;
import com.jjx.inventory.mapper.InventoryIqcBatchMapper;
import com.jjx.inventory.mapper.InventoryIqcDispositionOrderMapper;
import com.jjx.inventory.mapper.InventoryIqcQuarantineMapper;
import com.jjx.inventory.mapper.InventoryIqcReturnOrderMapper;
import com.jjx.inventory.mapper.InventoryIqcReworkOrderMapper;
import com.jjx.inventory.mapper.InventoryIqcScrapOrderMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.mapper.InventoryWarehouseMapper;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.inventory.service.InventoryItemService;
import com.jjx.inventory.service.InventoryStockMutationService;
import com.jjx.inventory.service.impl.InventoryInboundServiceImpl;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.service.QualityLotService;
import com.jjx.quality.service.QualityNcrService;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesDeliveryItemMapper;
import com.jjx.sales.mapper.SalesDeliveryMapper;
import com.jjx.system.utils.SecurityUtils;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 并发回归（dev-20260923-013）：同一张待确认入库单并发确认（含冲减）。
 *
 * 守卫口径（DEV-651 方案A + dev-20260922-020 口径 B）：
 *   confirm 先 selectByIdForUpdate（SELECT ... FOR UPDATE 行锁），并发第二个请求阻塞后重读，
 *   状态已不是 草稿/待审批/已批准 → 直接返回 false，不重复过账；
 *   生产来源「应入 &lt; 已入」走 reducePostedStock 按负差额冲减，冲减后为负则整单拒绝。
 */
class InventoryInboundConcurrencyGuardTest {

    private static final Long INBOUND_ID = 1L;

    private final InventoryInboundOrderMapper inboundOrderMapper = mock(InventoryInboundOrderMapper.class);
    private final InventoryInboundItemMapper inboundItemMapper = mock(InventoryInboundItemMapper.class);
    private final InventoryStockItemMapper stockItemMapper = mock(InventoryStockItemMapper.class);
    private final InventoryStockMapper stockMapper = mock(InventoryStockMapper.class);
    private final InventoryTransactionMapper transactionMapper = mock(InventoryTransactionMapper.class);
    private final InventoryStockMutationService stockMutationService = mock(InventoryStockMutationService.class);
    private final InventoryMaterialMapper inventoryMaterialMapper = mock(InventoryMaterialMapper.class);
    private final InventoryWarehouseMapper warehouseMapper = mock(InventoryWarehouseMapper.class);
    private final ProductionOrderMapper productionOrderMapper = mock(ProductionOrderMapper.class);
    private final PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
    private final PurchaseOrderItemMapper purchaseOrderItemMapper = mock(PurchaseOrderItemMapper.class);
    private final EventPublisher eventPublisher = mock(EventPublisher.class);
    private final InventoryAlertService alertService = mock(InventoryAlertService.class);
    private final InventoryItemService inventoryItemService = mock(InventoryItemService.class);
    private final OrderMapper salesOrderMapper = mock(OrderMapper.class);
    private final SalesDeliveryMapper salesDeliveryMapper = mock(SalesDeliveryMapper.class);
    private final SalesDeliveryItemMapper salesDeliveryItemMapper = mock(SalesDeliveryItemMapper.class);
    private final InventoryIqcQuarantineMapper iqcQuarantineMapper = mock(InventoryIqcQuarantineMapper.class);
    private final InventoryIqcDispositionOrderMapper iqcDispositionOrderMapper =
            mock(InventoryIqcDispositionOrderMapper.class);
    private final InventoryIqcReturnOrderMapper iqcReturnOrderMapper = mock(InventoryIqcReturnOrderMapper.class);
    private final InventoryIqcReworkOrderMapper iqcReworkOrderMapper = mock(InventoryIqcReworkOrderMapper.class);
    private final InventoryIqcBatchMapper iqcBatchMapper = mock(InventoryIqcBatchMapper.class);
    private final InventoryIqcScrapOrderMapper iqcScrapOrderMapper = mock(InventoryIqcScrapOrderMapper.class);
    private final RedisSequenceService redisSequenceService = mock(RedisSequenceService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<QualityLotService> qualityLotServiceProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<QualityNcrService> qualityNcrServiceProvider = mock(ObjectProvider.class);
    private final QualityLotMapper qualityLotMapper = mock(QualityLotMapper.class);
    /** dev-20260923-043：返工退料读补料出库明细（并发护栏测试不涉及退料） */
    private final com.jjx.inventory.mapper.InventoryOutboundOrderMapper outboundOrderMapper =
            mock(com.jjx.inventory.mapper.InventoryOutboundOrderMapper.class);
    private final com.jjx.inventory.mapper.InventoryOutboundItemMapper outboundItemMapper =
            mock(com.jjx.inventory.mapper.InventoryOutboundItemMapper.class);
    private final com.jjx.quality.mapper.QualityNcrMapper qualityNcrMapper =
            mock(com.jjx.quality.mapper.QualityNcrMapper.class);

    private final InventoryInboundServiceImpl service = new InventoryInboundServiceImpl(
            inboundOrderMapper, inboundItemMapper, stockItemMapper, stockMapper, transactionMapper,
            stockMutationService, inventoryMaterialMapper, warehouseMapper, productionOrderMapper,
            purchaseOrderMapper, purchaseOrderItemMapper, eventPublisher, alertService, inventoryItemService,
            salesOrderMapper, salesDeliveryMapper, salesDeliveryItemMapper, iqcQuarantineMapper,
            iqcDispositionOrderMapper, iqcReturnOrderMapper, iqcReworkOrderMapper, iqcBatchMapper,
            iqcScrapOrderMapper, redisSequenceService, qualityLotServiceProvider, qualityNcrServiceProvider,
            qualityLotMapper, outboundOrderMapper, outboundItemMapper, qualityNcrMapper);

    // ==================== 并发确认（正常过账） ====================

    @Test
    void confirmPostsPendingProductionInboundExactlyOnce() {
        InventoryInboundOrder order = productionOrder(InventoryOrderStatusEnum.APPROVED);
        InventoryInboundItem item = item("98", "0");
        when(inboundOrderMapper.selectByIdForUpdate(INBOUND_ID)).thenReturn(order);
        when(inboundItemMapper.selectByInboundId(INBOUND_ID)).thenReturn(Collections.singletonList(item));
        when(stockItemMapper.selectOne(any())).thenReturn(stock("0"));
        when(inboundOrderMapper.updateById(order)).thenReturn(1);

        assertTrue(confirmWithOperatorContext(1L, "仓库员"));

        ArgumentCaptor<BigDecimal> delta = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<InventoryTransaction> tx = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(stockMutationService, times(1)).applyDelta(any(InventoryStockItem.class),
                delta.capture(), tx.capture());
        assertEquals(0, new BigDecimal("98").compareTo(delta.getValue()));
        assertEquals("INBOUND", tx.getValue().getTransactionType());
        assertEquals(0, new BigDecimal("98").compareTo(item.getPostedQuantity()));
        assertEquals(InventoryOrderStatusEnum.COMPLETED.getValue(), order.getOrderStatus());
        // 过账前必须走行锁读取，否则并发会重复入库
        verify(inboundOrderMapper).selectByIdForUpdate(INBOUND_ID);
    }

    // ==================== 并发确认（含冲减） ====================

    @Test
    void confirmReflectedShortfallReducesBatchStockWithAdjustTransaction() {
        // 复检判少：应入 0，已入 98 → 本次净差额 −98，由 reducePostedStock 冲减
        InventoryInboundOrder order = productionOrder(InventoryOrderStatusEnum.APPROVED);
        InventoryInboundItem item = item("0", "98");
        when(inboundOrderMapper.selectByIdForUpdate(INBOUND_ID)).thenReturn(order);
        when(inboundItemMapper.selectByInboundId(INBOUND_ID)).thenReturn(Collections.singletonList(item));
        when(stockItemMapper.selectOne(any())).thenReturn(stock("98"));
        when(inboundOrderMapper.updateById(order)).thenReturn(1);

        assertTrue(confirmWithOperatorContext(1L, "仓库员"));

        ArgumentCaptor<BigDecimal> delta = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<InventoryTransaction> tx = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(stockMutationService, times(1)).applyDelta(any(InventoryStockItem.class),
                delta.capture(), tx.capture());
        assertEquals(0, new BigDecimal("-98").compareTo(delta.getValue()), "冲减必须是负差额");
        assertEquals("ADJUST", tx.getValue().getTransactionType(), "红冲/冲减走 ADJUST，不是 INBOUND");
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getPostedQuantity()), "冲减后明细已过账量回到目标值");
    }

    @Test
    void confirmRefusesShortfallThatWouldGoNegative() {
        InventoryInboundOrder order = productionOrder(InventoryOrderStatusEnum.APPROVED);
        InventoryInboundItem item = item("0", "98");
        when(inboundOrderMapper.selectByIdForUpdate(INBOUND_ID)).thenReturn(order);
        when(inboundItemMapper.selectByInboundId(INBOUND_ID)).thenReturn(Collections.singletonList(item));
        when(stockItemMapper.selectOne(any())).thenReturn(stock("50")); // 只剩 50，冲 98 → 负数

        BusinessException ex = assertThrows(BusinessException.class,
                () -> confirmWithOperatorContext(1L, "仓库员"));

        assertTrue(ex.getMessage().contains("冲减后库存将为负"), ex.getMessage());
        verify(stockMutationService, never()).applyDelta(any(), any(), any());
        // 未过账成功 → 单据状态不被改写
        verify(inboundOrderMapper, never()).updateById(any(InventoryInboundOrder.class));
    }

    // ==================== 并发确认（第二个请求被守卫拒绝） ====================

    @Test
    void secondConfirmOnAlreadyPostedOrderIsRejectedWithoutSecondPosting() {
        InventoryInboundOrder pending = productionOrder(InventoryOrderStatusEnum.APPROVED);
        InventoryInboundItem item = item("98", "0");
        when(inboundOrderMapper.selectByIdForUpdate(INBOUND_ID)).thenReturn(pending);
        when(inboundItemMapper.selectByInboundId(INBOUND_ID)).thenReturn(Collections.singletonList(item));
        when(stockItemMapper.selectOne(any())).thenReturn(stock("0"));
        when(inboundOrderMapper.updateById(pending)).thenReturn(1);
        assertTrue(confirmWithOperatorContext(1L, "仓库员"));

        // 第二个并发请求在行锁释放后重读：该单已「已完成」，不再允许过账
        when(inboundOrderMapper.selectByIdForUpdate(INBOUND_ID))
                .thenReturn(productionOrder(InventoryOrderStatusEnum.COMPLETED));

        assertFalse(confirmWithOperatorContext(2L, "另一个仓库员"));

        verify(stockMutationService, times(1)).applyDelta(any(), any(), any());
        verify(inboundOrderMapper, times(1)).updateById(any(InventoryInboundOrder.class));
        verify(inboundOrderMapper, times(2)).selectByIdForUpdate(INBOUND_ID);
    }

    @Test
    void confirmOnCancelledReverseOrderIsRejected() {
        // 复检换代已作废的原单（状态 9）不允许再过账
        when(inboundOrderMapper.selectByIdForUpdate(INBOUND_ID))
                .thenReturn(productionOrder(InventoryOrderStatusEnum.CANCELLED));

        assertFalse(confirmWithOperatorContext(1L, "仓库员"));

        verify(stockMutationService, never()).applyDelta(any(), any(), any());
    }

    // ==================== 行锁与事务契约 ====================

    @Test
    void confirmKeepsRowLockAndRollbackContract() throws Exception {
        Select lock = InventoryInboundOrderMapper.class.getMethod("selectByIdForUpdate", Long.class)
                .getAnnotation(Select.class);
        assertNotNull(lock, "入库单行锁查询不得删除");
        assertTrue(String.join(" ", Arrays.asList(lock.value())).toUpperCase().contains("FOR UPDATE"),
                "并发确认依赖 FOR UPDATE 行锁；去掉即重复入库");

        Method confirm = InventoryInboundServiceImpl.class.getMethod("confirm", Long.class, Long.class, String.class);
        Transactional transactional = confirm.getAnnotation(Transactional.class);
        assertNotNull(transactional, "confirm 必须在事务内执行");
        assertTrue(Arrays.asList(transactional.rollbackFor()).contains(Exception.class),
                "冲减失败必须整单回滚");
    }

    // ==================== 夹具 ====================

    /**
     * confirm 过账成功后会写事件 payload，payload 取 SecurityUtils（Sa-Token 登录态）。
     * 单测无 Web 上下文，这里把登录态静态替换掉，只让并发守卫语义参与断言。
     */
    private boolean confirmWithOperatorContext(Long operatorId, String operatorName) {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(operatorId);
            security.when(SecurityUtils::getUsername).thenReturn(operatorName);
            security.when(SecurityUtils::getDisplayName).thenReturn(operatorName);
            return service.confirm(INBOUND_ID, operatorId, operatorName);
        }
    }

    private static InventoryInboundOrder productionOrder(InventoryOrderStatusEnum status) {
        InventoryInboundOrder order = new InventoryInboundOrder();
        order.setInboundId(INBOUND_ID);
        order.setInboundNo("WO-PL260923001-01-FI03");
        order.setInboundType("PRODUCTION_FINISH");
        order.setSourceType("PRODUCTION");
        order.setSourceId(2L);
        order.setSourceNo("WO-PL260923001-01");
        order.setWarehouseId(2L);
        order.setOrderStatus(status.getValue());
        return order;
    }

    private static InventoryInboundItem item(String quantity, String postedQuantity) {
        InventoryInboundItem item = new InventoryInboundItem();
        item.setItemId(7L);
        item.setInboundId(INBOUND_ID);
        item.setInventoryItemId(2067L);
        item.setMaterialCode("JST270MEOO");
        item.setMaterialName("成品");
        item.setQuantity(new BigDecimal(quantity));
        item.setPostedQuantity(new BigDecimal(postedQuantity));
        item.setBatchNo("BATCH-QL260923007");
        item.setLotId(7L);
        return item;
    }

    private static InventoryStockItem stock(String quantity) {
        InventoryStockItem stock = new InventoryStockItem();
        stock.setItemId(6L);
        stock.setInventoryItemId(2067L);
        stock.setMaterialCode("JST270MEOO");
        stock.setMaterialName("成品");
        stock.setWarehouseId(2L);
        stock.setBatchNo("BATCH-QL260923007");
        stock.setQuantity(new BigDecimal(quantity));
        stock.setReservedQuantity(BigDecimal.ZERO);
        stock.setStatus(1);
        return stock;
    }
}
