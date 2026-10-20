package com.jjx.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.EngineeringRoutingMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.ProductWorkSpecVersionMapper;
import com.jjx.product.service.WorkSpecBindingService;
import com.jjx.production.domain.dto.ProductionOrderCreateDTO;
import com.jjx.production.service.ProductionOrderService;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.enums.SalesOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.sales.service.impl.OrderStatusServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 直接调用生成计划入口，验证全部前置检查早于任何计划或订单状态写入。 */
@ExtendWith(MockitoExtension.class)
class OrderProductionPlanWorkSpecTest {
    @Mock OrderMapper salesOrderMapper;
    @Mock ISalesOrderProductService orderProductService;
    @Mock SalesOrderProductMapper salesOrderProductMapper;
    @Mock ProductionOrderService productionOrderService;
    @Mock EngineeringBomMapper productBomMapper;
    @Mock EngineeringRoutingMapper productRoutingMapper;
    @Mock ProductMapper productMapper;
    @Mock ProductWorkSpecVersionMapper versions;
    @Mock RedisSequenceService redisSequenceService;
    @InjectMocks OrderStatusServiceImpl service;
    SalesOrder order;

    @BeforeEach void setup() {
        for (Class<?> entity : List.of(SalesOrderProduct.class, EngineeringBom.class, EngineeringRouting.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "plan-test"), entity);
        }
        ReflectionTestUtils.setField(service, "workSpecBindingService", new WorkSpecBindingService(versions));
        order = new SalesOrder();
        order.setOrderId(10L);
        order.setOrderNo("SO-10");
        order.setOrderStatus(SalesOrderStatusEnum.APPROVED.getValue());
        when(salesOrderMapper.selectById(10L)).thenReturn(order);
        when(orderProductService.isExists(10L)).thenReturn(true);
    }

    private SalesOrderProduct product(Long id) {
        var product = new SalesOrderProduct();
        product.setProductId(id);
        product.setProductCode("P-" + id);
        product.setProductName("产品名称" + id);
        product.setQuantity(2);
        return product;
    }

    private void ready(List<SalesOrderProduct> products) {
        when(salesOrderProductMapper.selectList(any())).thenReturn(products);
        when(productBomMapper.selectCount(any())).thenReturn(1L);
        when(productRoutingMapper.selectCount(any())).thenReturn(1L);
    }

    private void published(Long id) {
        var version = new ProductWorkSpecVersion();
        version.setId(id + 100L);
        when(versions.selectLatestPublished(id)).thenReturn(version);
    }

    private void rejected(String message) {
        BusinessException error = assertThrows(BusinessException.class, () -> service.createProductionPlan(10L));
        assertEquals(message, error.getMessage());
        assertEquals(SalesOrderStatusEnum.APPROVED.getValue(), order.getOrderStatus());
        verify(productionOrderService, never()).createOrder(any());
        verify(productionOrderService, never()).updateOrderStatus(anyLong(), anyInt(), anyString());
        verify(salesOrderMapper, never()).updateStatusWithCheck(anyLong(), anyInt(), anyInt());
        verify(salesOrderMapper, never()).updateById(any(SalesOrder.class));
        verifyNoInteractions(redisSequenceService);
    }

    @Test void noVersionRejectsBeforeAnyWrite() {
        ready(List.of(product(1L)));
        when(versions.selectByProductId(1L)).thenReturn(List.of());
        rejected("产品[P-1] 产品名称1 尚未制定作业规范，请先在产品作业规范页制定并发布");
    }

    @Test void draftOnlyRejectsWithPublishInstruction() {
        ready(List.of(product(1L)));
        var draft = new ProductWorkSpecVersion();
        draft.setStatus("DRAFT");
        when(versions.selectByProductId(1L)).thenReturn(List.of(draft));
        rejected("产品[P-1] 产品名称1 作业规范已制定但未发布，请先发布版本");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void secondProductFailureNeverCreatesPartialPlan(boolean draftExists) {
        ready(List.of(product(1L), product(2L)));
        published(1L);
        when(versions.selectByProductId(2L)).thenReturn(draftExists
                ? List.of(new ProductWorkSpecVersion()) : List.of());
        rejected("产品[P-2] 产品名称2 " + (draftExists
                ? "作业规范已制定但未发布，请先发布版本"
                : "尚未制定作业规范，请先在产品作业规范页制定并发布"));
    }

    @Test void publishedProductsCreatePlansAndConfirmWithoutRebindingSalesLines() {
        var first = product(1L); // 历史行未绑定也按当前是否存在发布版本判断
        var second = product(2L);
        second.setWorkSpecVersionId(999L);
        ready(List.of(product(null), first, second));
        published(1L);
        published(2L);
        when(productionOrderService.createOrder(any())).thenReturn(101L, 102L);
        when(salesOrderMapper.updateStatusWithCheck(10L, SalesOrderStatusEnum.CONFIRMED.getValue(),
                SalesOrderStatusEnum.APPROVED.getValue())).thenReturn(1);
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            service.createProductionPlan(10L);
        }
        var plans = ArgumentCaptor.forClass(ProductionOrderCreateDTO.class);
        verify(productionOrderService, times(2)).createOrder(plans.capture());
        assertEquals(List.of(1L, 2L), plans.getAllValues().stream().map(ProductionOrderCreateDTO::getProductId).toList());
        assertTrue(plans.getAllValues().stream().allMatch(plan -> "PLAN".equals(plan.getOrderType())
                && Long.valueOf(10L).equals(plan.getSalesOrderId())));
        verify(productionOrderService).updateOrderStatus(101L, 1, "销售订单生成计划，自动提交审批");
        verify(productionOrderService).updateOrderStatus(102L, 1, "销售订单生成计划，自动提交审批");
        verify(salesOrderMapper).updateStatusWithCheck(10L, SalesOrderStatusEnum.CONFIRMED.getValue(),
                SalesOrderStatusEnum.APPROVED.getValue());
        verify(salesOrderMapper).updateById(any(SalesOrder.class));
        verify(versions, never()).selectByProductId(any());
        verify(versions, never()).selectLatestPublished(isNull());
        assertNull(first.getWorkSpecVersionId());
        assertEquals(999L, second.getWorkSpecVersionId());
    }

    @Test void missingBomKeepsOriginalMessage() {
        when(salesOrderProductMapper.selectList(any())).thenReturn(List.of(product(1L)));
        when(productBomMapper.selectCount(any())).thenReturn(0L);
        rejected("产品[P-1] 产品名称1 没有当前生效的BOM，请先配置并审批BOM");
        verifyNoInteractions(versions, productRoutingMapper);
    }

    @Test void unapprovedBomKeepsOriginalMessage() {
        when(salesOrderProductMapper.selectList(any())).thenReturn(List.of(product(1L)));
        when(productBomMapper.selectCount(any())).thenReturn(0L, 1L);
        rejected("产品[P-1] 产品名称1 当前BOM尚未审批通过，请先完成BOM审批");
        verifyNoInteractions(versions, productRoutingMapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidRoutingKeepsOriginalMessage(boolean draftExists) {
        when(salesOrderProductMapper.selectList(any())).thenReturn(List.of(product(1L)));
        when(productBomMapper.selectCount(any())).thenReturn(1L);
        when(productRoutingMapper.selectCount(any())).thenReturn(0L, draftExists ? 1L : 0L);
        rejected("产品[P-1] 产品名称1 " + (draftExists
                ? "当前工艺路线尚未审批通过，请先完成路线审批"
                : "没有当前工艺路线，请先配置并审批工艺路线"));
        verifyNoInteractions(versions);
    }

    @Test void allSampleLinesKeepExistingFailureAndSkipSpecQueries() {
        when(salesOrderProductMapper.selectList(any())).thenReturn(List.of(product(null)));
        rejected("订单无可生成计划的产品（均无productId）");
        verifyNoInteractions(versions, productBomMapper, productRoutingMapper);
    }
}
