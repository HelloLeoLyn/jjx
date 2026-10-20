package com.jjx.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.*;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.*;
import com.jjx.sales.controller.SampleOrderController;
import com.jjx.sales.domain.dto.*;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.domain.vo.*;
import com.jjx.sales.enums.*;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.*;
import com.jjx.sales.service.impl.SampleOrderServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SampleReturnSourceOrderTest {
    static <T> T construct(Class<T> type, Map<Class<?>, Object> deps) {
        try {
            var ctor = Arrays.stream(type.getDeclaredConstructors()).max(Comparator.comparingInt(java.lang.reflect.Constructor::getParameterCount)).orElseThrow();
            return type.cast(ctor.newInstance(Arrays.stream(ctor.getParameterTypes()).map(t -> deps.containsKey(t) ? deps.get(t) : mock(t)).toArray()));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    static Map<Class<?>, Object> dependencies() {
        Map<Class<?>, Object> deps = new HashMap<>();
        for (var t : SampleOrderServiceImpl.class.getDeclaredConstructors()[0].getParameterTypes()) deps.put(t, mock(t));
        deps.put(ObjectMapper.class, new ObjectMapper().findAndRegisterModules()); return deps;
    }
    static void ready(Map<Class<?>, Object> deps, long productId) {
        var product = new Product(); product.setProductId(productId); product.setProductCode("P37"); product.setProductStatus(6);
        when(((ProductMapper)deps.get(ProductMapper.class)).selectById(productId)).thenReturn(product);
        var bom = new EngineeringBom(); bom.setApproveStatus(3);
        when(((IEngineeringBomService)deps.get(IEngineeringBomService.class)).getOne(any(Wrapper.class))).thenReturn(bom);
        var route = new EngineeringRouting(); route.setApproveStatus(3);
        when(((IEngineeringRoutingService)deps.get(IEngineeringRoutingService.class)).getOne(any(Wrapper.class))).thenReturn(route);
    }
    Map<Class<?>, Object> deps; OrderMapper orders; SalesSampleOrderMapper profiles; SalesOrderProductMapper lines;
    IOrderService orderService; SampleOrderServiceImpl service; SalesOrder sample, source; SalesSampleOrder profile; SalesOrderProduct line;
    @BeforeEach void setup() {
        var config = new MybatisConfiguration();
        for (var t : List.of(SalesOrder.class, SalesSampleOrder.class, SalesOrderProduct.class, EngineeringBom.class, EngineeringRouting.class, Product.class, EngineeringFilm.class, SalesSampleTransfer.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(config, "return37"), t);
        deps = dependencies(); orders = (OrderMapper)deps.get(OrderMapper.class); profiles = (SalesSampleOrderMapper)deps.get(SalesSampleOrderMapper.class);
        lines = (SalesOrderProductMapper)deps.get(SalesOrderProductMapper.class); orderService = (IOrderService)deps.get(IOrderService.class);
        service = construct(SampleOrderServiceImpl.class, deps);
        source = new SalesOrder(); source.setOrderId(100L); source.setOrderNo("SO37"); source.setCustomerId(7L);
        source.setOrderType(SalesOrderTypeEnum.STANDARD.getCode()); source.setOrderStatus(SalesOrderStatusEnum.IN_PRODUCTION.getValue()); source.setTotalQuantity(100);
        sample = new SalesOrder(); sample.setOrderId(200L); sample.setOrderNo("SP37"); sample.setCustomerId(7L);
        sample.setOrderType(SalesOrderTypeEnum.SAMPLE.getCode()); sample.setSampleStatus(SampleOrderStatusEnum.CONFIRMED.getValue());
        sample.setSourceType("SALES_ORDER"); sample.setSourceNo("SO37"); sample.setSampleProductId(8L); sample.setSampleProductCode("P37");
        profile = new SalesSampleOrder(); profile.setOrderId(200L); profile.setSampleStatus(sample.getSampleStatus());
        profile.setSourceType(sample.getSourceType()); profile.setSourceNo(sample.getSourceNo()); profile.setProductId(8L); profile.setProductCode("P37");
        line = new SalesOrderProduct(); line.setId(300L); line.setOrderId(100L); line.setProductId(8L); line.setProductCode("P37"); line.setQuantity(100); line.setUnitPrice(BigDecimal.TEN);
        when(orders.selectByIdForUpdate(200L)).thenReturn(sample); when(orders.selectById(200L)).thenReturn(sample);
        when(profiles.selectOne(any())).thenReturn(profile); when(orders.selectList(any())).thenReturn(List.of(source)); when(lines.selectList(any())).thenReturn(List.of(line));
        when(orders.updateSampleStatus(eq(200L), anyInt(), anyInt())).thenReturn(1); when(orders.updateById(any(SalesOrder.class))).thenReturn(1);
        var sampleLine = new SalesOrderProductVO(); BeanUtils.copyProperties(line, sampleLine); sampleLine.setOrderId(200L); sampleLine.setQuantity(3);
        when(((ISalesOrderProductService)deps.get(ISalesOrderProductService.class)).getListByOrderId(200L)).thenReturn(List.of(sampleLine)); ready(deps, 8L);
    }
    @Test void putAndPostReturnOriginalWithoutCreatingOrChangingIt() {
        var controller = construct(SampleOrderController.class, Map.of(ISampleOrderService.class, service));
        assertEquals(100L, controller.convertToProduction(200L, List.of(new SampleConvertItemDTO()), "changed", null, null, null, null).getData().getOrderId());
        assertEquals(100L, controller.convertSample(200L, new SalesOrderAddDTO()).getData().getOrderId());
        assertEquals(100, source.getTotalQuantity()); assertEquals(100, line.getQuantity()); assertEquals(BigDecimal.TEN, line.getUnitPrice());
        verifyNoInteractions(orderService); verify(orders, never()).insert(any(SalesOrder.class)); verify(lines, never()).updateById(any(SalesOrderProduct.class));
        verify(orders, times(2)).updateById(argThat((SalesOrder o) -> o.getOrderId().equals(200L) && o.getConvertedOrderId().equals(100L) && o.getConvertOrderTime() != null && o.getOrderStatus() == null));
    }
    @Test void checkExposesOriginalOrderAndProductWithoutWriting() {
        var check = service.checkConvertReady(200L); assertTrue(check.getAllPass()); assertTrue(check.getReturnToSourceOrder());
        assertEquals(100L, check.getSourceOrderId()); assertEquals(300L, check.getSourceOrderProductId()); verify(orders, never()).updateById(any(SalesOrder.class));
    }
    @Test void invalidOriginalOrderRejectedBeforeWrite() {
        source.setOrderStatus(SalesOrderStatusEnum.DRAFT.getValue()); source.setCustomerId(9L);
        assertThrows(BusinessException.class, () -> service.convertSampleToProduction(200L, null)); source.setCustomerId(7L);
        when(orders.selectList(any())).thenReturn(List.of()); assertThrows(BusinessException.class, () -> service.convertToProduction(200L));
        when(orders.selectList(any())).thenReturn(List.of(source, source)); assertThrows(BusinessException.class, () -> service.convertSampleToProduction(200L, null));
        verify(orders, never()).updateById(any(SalesOrder.class)); verifyNoInteractions(orderService);
    }
    @Test void removedAmbiguousOrChangedProductCannotBeReplacedByRequest() {
        for (var matches : List.of(List.<SalesOrderProduct>of(), List.of(line, line))) {
            when(lines.selectList(any())).thenReturn(matches); assertThrows(BusinessException.class, () -> service.convertToProduction(200L, List.of(new SampleConvertItemDTO())));
        }
        line.setProductId(9L); when(lines.selectList(any())).thenReturn(List.of(line)); assertThrows(BusinessException.class, () -> service.convertSampleToProduction(200L, new SalesOrderAddDTO()));
        verify(orders, never()).updateById(any(SalesOrder.class));
    }
    @Test void sourceLinkNeedsNoEngineeringButConfirmationAndRetryGuardRemainRequired() {
        reset((IEngineeringBomService)deps.get(IEngineeringBomService.class));
        assertEquals(100L, service.convertSampleToProduction(200L, null).getOrderId());
        assertTrue(service.checkConvertReady(200L).getAllPass());
        verifyNoInteractions(deps.get(IEngineeringBomService.class), deps.get(IEngineeringRoutingService.class));
        clearInvocations(orders);
        profile.setSampleStatus(SampleOrderStatusEnum.SAMPLE_SENT.getValue());
        assertThrows(BusinessException.class, () -> service.convertToProduction(200L));
        profile.setSampleStatus(SampleOrderStatusEnum.TRANSFERRED.getValue()); profile.setConvertedOrderId(100L);
        assertThrows(BusinessException.class, () -> service.convertSampleToProduction(200L, null));
        verify(orders, never()).updateById(any(SalesOrder.class));
    }
    @Test void newProductionOrderStillRequiresBom() {
        sample.setSourceType(null); profile.setSourceType(null);
        when(((IEngineeringBomService)deps.get(IEngineeringBomService.class)).getOne(any(Wrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.convertSampleToProduction(200L, new SalesOrderAddDTO()));
        verifyNoInteractions(orderService);
    }
    @Test void confirmedSalesSampleAutoLinksWithoutChangingOriginal() {
        sample.setSampleStatus(SampleOrderStatusEnum.SAMPLE_SENT.getValue());
        doAnswer(i -> { sample.setSampleStatus(i.getArgument(2)); return 1; }).when(orders).updateSampleStatus(eq(200L), anyInt(), anyInt());
        service.confirmSample(200L, "客户");
        verify(orders).updateById(argThat((SalesOrder o) -> Long.valueOf(100L).equals(o.getConvertedOrderId())
                && SampleOrderStatusEnum.TRANSFERRED.getValue().equals(o.getSampleStatus())));
        verifyNoInteractions(orderService, deps.get(IEngineeringBomService.class), deps.get(IEngineeringRoutingService.class));
        assertEquals(100, source.getTotalQuantity()); assertEquals(BigDecimal.TEN, line.getUnitPrice());
    }
    @Test void failedAutoLinkKeepsConfirmationAndReturnsRetryHint() {
        sample.setSampleStatus(SampleOrderStatusEnum.SAMPLE_SENT.getValue());
        when(orders.selectList(any())).thenReturn(List.of());
        var result = service.confirmSample(200L, "客户");
        verify(orders).updateSampleStatus(200L, SampleOrderStatusEnum.SAMPLE_SENT.getValue(), SampleOrderStatusEnum.CONFIRMED.getValue());
        assertTrue(result.getRemark().contains("关联回原订单"));
        verify(orders, never()).updateById(argThat((SalesOrder o) -> o.getConvertedOrderId() != null));
    }
    @Test void repeatedConfirmationDoesNotWriteLinkedSampleAgain() {
        sample.setSampleStatus(SampleOrderStatusEnum.TRANSFERRED.getValue()); sample.setConvertedOrderId(100L);
        assertSame(sample, service.confirmSample(200L, "客户"));
        verify(orders, never()).updateById(any(SalesOrder.class)); verify(orders, never()).updateSampleStatus(anyLong(), anyInt(), anyInt());
    }
    @Test void standaloneAndQuotationStillCreateOrderWithTrace() {
        for (String origin : Arrays.asList(null, "QUOTATION")) {
            sample.setSourceType(origin); profile.setSourceType(origin); sample.setTraceId("trace37");
            var quote = new SalesQuotation(); quote.setQuotationId(50L); quote.setQuotationNo("QT37");
            when(((QuotationMapper)deps.get(QuotationMapper.class)).selectOne(any())).thenReturn(quote);
            var dto = new SalesOrderAddDTO(); dto.setOrderNo("NEW");
            var attachment = new SalesOrderAddDTO.OrderAttachmentDTO(); attachment.setAttachmentId(123L); attachment.setDocumentType("CUSTOMER_DRAWING");
            dto.setAttachments(List.of(attachment)); when(orderService.insertOrder(dto)).thenReturn(400L);
            var created = new SalesOrder(); created.setOrderId(400L); when(orders.selectById(400L)).thenReturn(created);
            assertEquals(400L, service.convertSampleToProduction(200L, dto).getOrderId()); assertEquals("trace37", dto.getTraceId());
            assertEquals(SalesOrderTypeEnum.STANDARD.getCode(), dto.getOrderType());
            assertEquals(origin == null ? null : 50L, dto.getQuotationId()); assertSame(attachment, dto.getAttachments().getFirst());
            verify(orderService, atLeastOnce()).insertOrder(same(dto)); clearInvocations(orderService);
        }
    }
}
