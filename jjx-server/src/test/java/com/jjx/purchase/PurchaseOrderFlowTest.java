package com.jjx.purchase;

import com.jjx.purchase.domain.dto.PurchaseOrderDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderApprovalDTO;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.service.impl.PurchaseOrderServiceImpl;
import com.jjx.purchase.converter.PurchaseConverter;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 采购订单流程测试
 * 创建采购单 → 审核
 */
class PurchaseOrderFlowTest {

    @Mock private PurchaseOrderMapper orderMapper;
    @Mock private PurchaseOrderItemMapper orderItemMapper;
    @Mock private PurchaseConverter purchaseConverter;

    private PurchaseOrderServiceImpl orderService;

    @Mock private com.jjx.inventory.mapper.InventoryStockItemMapper stockItemMapper;
    @Mock private com.jjx.inventory.mapper.InventoryStockMapper stockMapper;
    @Mock private com.jjx.inventory.mapper.InventoryTransactionMapper transactionMapper;
    @Mock private com.jjx.inventory.mapper.InventoryMaterialMapper materialMapper;
    @Mock private com.jjx.inventory.mapper.InventoryWarehouseMapper warehouseMapper;
    @Mock private com.jjx.inventory.service.InventoryInboundService inboundService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        orderService = new PurchaseOrderServiceImpl(orderMapper, orderItemMapper, purchaseConverter,
            stockItemMapper, stockMapper, transactionMapper, materialMapper, warehouseMapper, inboundService);
    }

    @Test
    @DisplayName("1. 创建采购单 - 成功")
    void testCreateOrder() {
        // Mock: 订单号唯一
        when(orderMapper.checkOrderNoUnique(anyString())).thenReturn(0);

        PurchaseOrderDTO dto = new PurchaseOrderDTO();
        dto.setOrderNo("PO202607200001");
        dto.setSupplierId(1L);
        dto.setSupplierName("供应商A");

        // 添加订单明细（必填）
        PurchaseOrderItemDTO item = new PurchaseOrderItemDTO();
        item.setMaterialId(1L);
        item.setQuantity(BigDecimal.TEN);
        dto.setItems(java.util.List.of(item));

        when(orderMapper.checkOrderNoUnique(anyString())).thenReturn(0);

        // Mock converter to return entity
        PurchaseOrder order = new PurchaseOrder();
        when(purchaseConverter.toEntity(any(PurchaseOrderDTO.class))).thenReturn(order);
        // Mock item converter - service iterates dto items and converts each
        PurchaseOrderItem mockItem = new PurchaseOrderItem();
        when(purchaseConverter.toEntity(any(PurchaseOrderItemDTO.class))).thenReturn(mockItem);
        when(orderMapper.insert(any(PurchaseOrder.class))).thenReturn(1);
        when(orderItemMapper.insert(any(com.jjx.purchase.domain.entity.PurchaseOrderItem.class))).thenReturn(1);

        int result = orderService.insertOrder(dto);
        assert result > 0 : "创建失败";
        System.out.println("  ✅ 创建采购单: " + dto.getOrderNo());
    }

    @Test
    @DisplayName("2. 采购单审核 - 不存在订单应报错")
    void testApproveOrder_notFound() {
        when(orderMapper.selectById(999L)).thenReturn(null);

        PurchaseOrderApprovalDTO dto = new PurchaseOrderApprovalDTO();
        dto.setOrderId(999L);

        try {
            orderService.approveOrder(dto);
            assert false : "应该抛出异常";
        } catch (com.jjx.common.exception.BusinessException e) {
            System.out.println("  ✅ 正确拦截: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("3. 订单号唯一性校验")
    void testCheckOrderNo() {
        when(orderMapper.checkOrderNoUnique(anyString())).thenReturn(0);
        boolean unique = orderService.checkOrderNoUnique("PO202607200001");
        assert !unique : "订单号应唯一";
        System.out.println("  ✅ 订单号唯一性校验通过");
    }
}
