package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.*;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.impl.*;
import com.jjx.product.mapper.*;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.mapper.*;
import com.jjx.system.utils.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实公开Service入口，仓储替身；断言不完整需求在任何写入前停止。 */
class LegacyBomConsumerFlowTest {
    InventoryMaterialMapper materials=mock(InventoryMaterialMapper.class);
    EngineeringBomMapper boms=mock(EngineeringBomMapper.class);
    EngineeringBomItemMapper items=mock(EngineeringBomItemMapper.class);
    OrderMapper orders=mock(OrderMapper.class);
    SalesOrderProductMapper products=mock(SalesOrderProductMapper.class);
    OrderMaterialReserveMapper reservations=mock(OrderMaterialReserveMapper.class);
    MaterialStockAllocationService allocations=mock(MaterialStockAllocationService.class);
    InventoryAlertLogMapper alerts=mock(InventoryAlertLogMapper.class);
    EngineeringBomItem row;
    InventoryMaterial archive;
    SalesOrderProduct product;

    @BeforeEach void setup() {
        for(var type:List.of(EngineeringBom.class,EngineeringBomItem.class,SalesOrder.class,SalesOrderProduct.class,
                InventoryAlertLog.class)) TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),type);
        var order=new SalesOrder();order.setOrderId(7L);order.setOrderNo("SO7");
        when(orders.selectById(7L)).thenReturn(order);when(orders.selectByIdForUpdate(7L)).thenReturn(order);
        product=new SalesOrderProduct();product.setProductId(8L);product.setQuantity(300);when(products.selectList(any())).thenReturn(List.of(product));
        var bom=new EngineeringBom();bom.setBomId(9L);when(boms.selectOne(any())).thenReturn(bom);
        row=new EngineeringBomItem();row.setItemId(10L);row.setMaterialId(11L);row.setMaterialCode("MAT11");row.setMaterialName("材料");
        row.setUnit("PCS");row.setBaseQty(BigDecimal.ONE);row.setModuleQty(new BigDecimal("6"));row.setActualIssueQty(new BigDecimal("0.1667"));
        when(items.selectList(any())).thenReturn(List.of(row));
        archive=new InventoryMaterial();archive.setMaterialId(11L);archive.setMaterialCode("MAT11");archive.setUnit("PCS");
        when(materials.selectById(11L)).thenReturn(archive);
    }
    InventoryAlertServiceImpl alertService() {
        var service=mock(InventoryAlertServiceImpl.class,CALLS_REAL_METHODS);
        Map<String,Object> deps=new HashMap<>();deps.put("materialMapper",materials);deps.put("bomMapper",boms);deps.put("bomItemMapper",items);
        deps.put("orderMapper",orders);deps.put("orderProductMapper",products);deps.put("alertLogMapper",alerts);
        deps.put("inventoryItemMapper",mock(InventoryItemMapper.class));deps.put("stockMapper",mock(InventoryStockMapper.class));
        deps.put("purchaseOrderItemMapper",mock(com.jjx.purchase.mapper.PurchaseOrderItemMapper.class));
        deps.put("salesOrderStockReserveMapper",mock(SalesOrderStockReserveMapper.class));
        deps.put("productionOrderMapper",mock(com.jjx.production.mapper.ProductionOrderMapper.class));
        deps.put("orderShortageLedgerMapper",mock(OrderShortageLedgerMapper.class));
        deps.forEach((k,v)->ReflectionTestUtils.setField(service,k,v));return service;
    }
    @Test void previewUsesExactDemandAndCrossOrderAllocation() {
        var result=alertService().previewOrderShortage(7L);assertEquals(true,result.get("calculationComplete"));
        var rows=(List<Map<String,Object>>)result.get("materialRows");
        assertEquals(0,new BigDecimal("50").compareTo((BigDecimal)rows.get(0).get("demand")));
        assertEquals(0,new BigDecimal("50").compareTo((BigDecimal)rows.get(0).get("actualGap")));
    }
    @Test void incompletePreviewReturnsIssuesAndMutationNeverResolvesOldAlerts() {
        archive.setUnit("M");var service=alertService();var result=assertDoesNotThrow(()->service.previewOrderShortage(7L));
        assertEquals(false,result.get("calculationComplete"));assertFalse(((List<?>)result.get("calculationIssues")).isEmpty());
        assertThrows(BusinessException.class,()->service.checkOrderShortageWithDetail(7L));verifyNoInteractions(alerts);
    }
    @Test void validRowsRemainVisibleButGapIsUnknownWhenAnotherContributionIsInvalid() {
        var good=new EngineeringBomItem();good.setMaterialId(12L);good.setUnit("PCS");good.setBaseQty(BigDecimal.ONE);good.setModuleQty(BigDecimal.ONE);
        var m=new InventoryMaterial();m.setUnit("PCS");when(materials.selectById(12L)).thenReturn(m);
        archive.setUnit("M");when(items.selectList(any())).thenReturn(List.of(row,good));
        var result=alertService().previewOrderShortage(7L);var rows=(List<Map<String,Object>>)result.get("materialRows");
        assertEquals(1,rows.size());assertEquals(12L,rows.get(0).get("materialId"));assertNull(rows.get(0).get("actualGap"));
    }
    @Test void reserveValidatesBeforeReleasingOrAllocatingAnything() {
        archive.setUnit("M");var service=new OrderMaterialReserveServiceImpl(reservations,allocations,orders,products,boms,items,materials);
        assertThrows(BusinessException.class,()->service.reserveForOrder(7L,3));verifyNoInteractions(reservations,allocations);
        verify(orders,never()).updateById(any(SalesOrder.class));
    }
    @Test void reserveAllocatesFiftyNotFiftyOne() {
        var service=new OrderMaterialReserveServiceImpl(reservations,allocations,orders,products,boms,items,materials);
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("test");service.reserveForOrder(7L,3);
        }
        var amount=org.mockito.ArgumentCaptor.forClass(BigDecimal.class);verify(allocations).allocate(any(),amount.capture());
        assertEquals(0,new BigDecimal("50").compareTo(amount.getValue()));
    }
    @Test void globalSumUsesTheSamePerOrderRoundingAsAllocation() {
        product.setQuantity(3);
        var second=new SalesOrder();second.setOrderId(8L);second.setOrderNo("SO8");
        var first=orders.selectById(7L);
        when(orders.selectList(any())).thenReturn(List.of(first,second));
        when(orders.selectById(8L)).thenReturn(second);
        alertService().checkGlobalShortage();
        var captured=org.mockito.ArgumentCaptor.forClass(InventoryAlertLog.class);verify(alerts).insert(captured.capture());
        assertTrue(captured.getValue().getAlertMessage().contains("总需求2"));
    }
    @Test void globalIncompleteDemandNeverDeletesOrCreatesDefinitePurchaseAlerts() {
        archive.setUnit("M");var first=orders.selectById(7L);when(orders.selectList(any())).thenReturn(List.of(first));
        assertThrows(BusinessException.class,()->alertService().checkGlobalShortage());verifyNoInteractions(alerts);
    }
    @Test void continuousReserveSendsFractionToTheExistingWholeRollAllocator() {
        product.setQuantity(3);row.setUnit("M");row.setBaseQty(new BigDecimal("0.08"));row.setModuleQty(BigDecimal.ONE);archive.setUnit("M");
        var service=new OrderMaterialReserveServiceImpl(reservations,allocations,orders,products,boms,items,materials);
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("test");service.reserveForOrder(7L,3);
        }
        var amount=org.mockito.ArgumentCaptor.forClass(BigDecimal.class);verify(allocations).allocate(any(),amount.capture());
        assertEquals(0,new BigDecimal("0.24").compareTo(amount.getValue()));
    }
}
