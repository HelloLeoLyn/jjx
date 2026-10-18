package com.jjx.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.service.ProductCustomerValidator;
import com.jjx.sales.domain.dto.SalesOrderBatchSampleDTO;
import com.jjx.sales.domain.dto.SampleOrderCreateDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.sales.enums.SalesOrderStatusEnum;
import com.jjx.sales.enums.SalesOrderTypeEnum;
import com.jjx.sales.mapper.CustomerMapper;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import com.jjx.sales.service.ISampleOrderService;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.sales.service.impl.SampleOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderBatchSampleTest {
    static SampleOrderServiceImpl service(Map<Class<?>, Object> dependencies) {
        try {
            var constructor = Arrays.stream(SampleOrderServiceImpl.class.getDeclaredConstructors())
                    .max(Comparator.comparingInt(java.lang.reflect.Constructor::getParameterCount)).orElseThrow();
            return (SampleOrderServiceImpl) constructor.newInstance(Arrays.stream(constructor.getParameterTypes())
                    .map(t -> dependencies.containsKey(t) ? dependencies.get(t) : mock(t)).toArray());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private final OrderMapper orders = mock(OrderMapper.class);
    private final SalesSampleOrderMapper profiles = mock(SalesSampleOrderMapper.class);
    private final ISalesOrderProductService products = mock(ISalesOrderProductService.class);
    private final ProductCustomerValidator validator = mock(ProductCustomerValidator.class);
    private final ISampleOrderService self = mock(ISampleOrderService.class);
    private final CustomerMapper customers = mock(CustomerMapper.class);
    private SampleOrderServiceImpl service;
    private SalesOrder source;
    private final List<SalesOrderProductVO> sourceItems = new ArrayList<>();
    private final List<SampleOrderCreateDTO> submitted = new ArrayList<>();

    @BeforeEach void setup() {
        service = service(Map.of(OrderMapper.class, orders, SalesSampleOrderMapper.class, profiles,
                ISalesOrderProductService.class, products, ProductCustomerValidator.class, validator,
                CustomerMapper.class, customers,
                ObjectMapper.class, new ObjectMapper().findAndRegisterModules()));
        ReflectionTestUtils.setField(service, "self", self);
        source = new SalesOrder(); source.setOrderId(100L); source.setOrderNo("SO-TEST");
        source.setOrderType(SalesOrderTypeEnum.STANDARD.getCode()); source.setCustomerId(7L);
        source.setOrderStatus(SalesOrderStatusEnum.DRAFT.getValue());
        source.setCustomerName("来源客户"); source.setContactPerson("来源联系人"); source.setContactPhone("1234");
        SalesCustomer customer = new SalesCustomer(); customer.setCustomerId(7L); customer.setCustomerName("来源客户");
        when(customers.selectById(7L)).thenReturn(customer);
        when(orders.selectOne(any())).thenReturn(source);
        when(orders.selectById(100L)).thenReturn(source);
        when(orders.selectByIdForUpdate(100L)).thenReturn(source);
        for (long id = 1; id <= 3; id++) {
            SalesOrderProductVO item = new SalesOrderProductVO(); item.setId(id); item.setProductId(id + 10);
            item.setProductCode("P" + id); item.setProductName("产品" + id); item.setQuantity(100); item.setUnit("PCS");
            item.setSpecification("规格" + id); sourceItems.add(item);
        }
        when(products.getListByOrderId(100L)).thenReturn(sourceItems);
        AtomicLong next = new AtomicLong(1000);
        when(self.createSample(any())).thenAnswer(i -> {
            SampleOrderCreateDTO dto = i.getArgument(0); submitted.add(dto);
            SalesOrder result = new SalesOrder(); result.setOrderId(next.incrementAndGet()); result.setOrderNo("SP" + next);
            result.setTotalQuantity(dto.getItems().getFirst().getQuantity()); result.setSampleQty(result.getTotalQuantity());
            when(orders.selectById(result.getOrderId())).thenReturn(result); return result;
        });
    }

    private SalesOrderBatchSampleDTO request(Long... ids) {
        SalesOrderBatchSampleDTO dto = new SalesOrderBatchSampleDTO();
        dto.setSourceVersion(service.getSalesOrderSampleOptions(100L).getSourceVersion());
        dto.setTechRequirement("共同要求");
        dto.setItems(Arrays.stream(ids).map(id -> {
            var item = new SalesOrderBatchSampleDTO.Item(); item.setItemId(id); item.setQuantity(BigDecimal.valueOf(id == 1 ? 3 : 5));
            item.setRemark("备注" + id); return item;
        }).toList()); return dto;
    }

    @Test void selectsTwoOfThreeAndCopiesCanonicalSource() {
        assertEquals(2, service.createBatchFromSalesOrder(100L, request(1L, 2L)).size());
        assertEquals(List.of(3, 5), submitted.stream().map(d -> d.getItems().getFirst().getQuantity()).toList());
        assertEquals(List.of("P1", "P2"), submitted.stream().map(d -> d.getItems().getFirst().getProductCode()).toList());
        assertEquals("来源联系人", submitted.getFirst().getContactPerson());
        assertEquals("共同要求", submitted.getLast().getTechRequirement());
        assertEquals("备注2", submitted.getLast().getRemark());
        assertEquals("规格1", submitted.getFirst().getItems().getFirst().getSpecification());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "2147483648"})
    void rejectsInvalidQuantityBeforeAnyCreation(String quantity) {
        var dto = request(1L, 2L); dto.getItems().getLast().setQuantity(new BigDecimal(quantity));
        assertThrows(BusinessException.class, () -> service.createBatchFromSalesOrder(100L, dto));
        assertTrue(submitted.isEmpty());
    }

    @Test void rejectsEmptyDuplicateForeignAndChangedSource() {
        for (var dto : List.of(request(), request(1L, 1L), request(999L)))
            assertThrows(BusinessException.class, () -> service.createBatchFromSalesOrder(100L, dto));
        var stale = request(1L); sourceItems.getFirst().setProductName("已修改");
        assertThrows(BusinessException.class, () -> service.createBatchFromSalesOrder(100L, stale));
        assertTrue(submitted.isEmpty());
    }

    @Test void rejectsCrossCustomerProductBeforeWritingFirstItem() {
        var dto = request(1L, 2L);
        doThrow(new BusinessException("跨客户产品")).when(validator).validateBelongsToCustomer(12L, 7L);
        assertThrows(BusinessException.class, () -> service.createBatchFromSalesOrder(100L, dto));
        assertTrue(submitted.isEmpty());
    }

    @Test void failureIdentifiesProductAndDoesNotReturnPartialSuccess() {
        var dto = request(1L, 2L);
        doThrow(new IllegalStateException("模拟失败")).when(self)
                .createSample(argThat(d -> d != null && d.getItems().getFirst().getProductId().equals(12L)));
        var error = assertThrows(BusinessException.class, () -> service.createBatchFromSalesOrder(100L, dto));
        assertTrue(error.getMessage().contains("P2")); assertTrue(error.getMessage().contains("整批未保存"));
    }

    /** dev-20261018-039：非终态订单（含生产中）预填与建单均放行。 */
    @ParameterizedTest @EnumSource(value = SalesOrderStatusEnum.class, names = {
            "DRAFT", "PENDING_REVIEW", "REVIEWING", "APPROVED", "REJECTED", "CONFIRMED", "IN_PRODUCTION"})
    void allowsEveryNonTerminalStatus(SalesOrderStatusEnum status) {
        source.setOrderStatus(status.getValue());
        assertEquals(3, service.getSalesOrderSampleOptions(100L).getItems().size());
        assertEquals(1, service.createBatchFromSalesOrder(100L, request(1L)).size());
        assertEquals(1, submitted.size());
    }

    /** dev-20261018-039：已发货/已完成/已取消终态在预填和正式建单都拒绝，且整批不落单。 */
    @ParameterizedTest @EnumSource(value = SalesOrderStatusEnum.class, names = {"SHIPPED", "COMPLETED", "CANCELLED"})
    void rejectsTerminalStatusForPrefillAndBatch(SalesOrderStatusEnum status) {
        var dto = request(1L, 2L);
        source.setOrderStatus(status.getValue());
        var optionsError = assertThrows(BusinessException.class, () -> service.getSalesOrderSampleOptions(100L));
        assertTrue(optionsError.getMessage().contains(status.getLabel()));
        assertTrue(optionsError.getMessage().contains("不可发起打样"));
        var batchError = assertThrows(BusinessException.class, () -> service.createBatchFromSalesOrder(100L, dto));
        assertTrue(batchError.getMessage().contains(status.getLabel()));
        assertTrue(submitted.isEmpty());
    }

    /** dev-20261018-039：未知/空状态不得猜作允许。 */
    @Test void rejectsUnknownOrEmptyStatus() {
        var dto = request(1L);
        for (Integer status : Arrays.asList(null, 0, 99)) {
            source.setOrderStatus(status);
            assertThrows(BusinessException.class, () -> service.getSalesOrderSampleOptions(100L));
        }
        source.setOrderStatus(null);
        var error = assertThrows(BusinessException.class, () -> service.createBatchFromSalesOrder(100L, dto));
        assertTrue(error.getMessage().contains("未知"));
        assertTrue(error.getMessage().contains("核查"));
        assertTrue(submitted.isEmpty());
    }

    /** dev-20261018-039：旧单产品来源（createSample + SALES_ORDER）同样拒绝终态订单。 */
    @ParameterizedTest @EnumSource(value = SalesOrderStatusEnum.class, names = {"SHIPPED", "COMPLETED", "CANCELLED"})
    void rejectsTerminalStatusOnLegacyProductSourcePath(SalesOrderStatusEnum status) {
        source.setOrderStatus(status.getValue());
        var dto = new SampleOrderCreateDTO();
        dto.setCustomerId(7L); dto.setSourceType("SALES_ORDER"); dto.setSourceNo("SO-TEST");
        var item = new SampleOrderCreateDTO.Item();
        item.setProductId(11L); item.setProductCode("P1"); item.setQuantity(3);
        dto.setItems(List.of(item));
        var error = assertThrows(BusinessException.class, () -> service.createSample(dto));
        assertTrue(error.getMessage().contains(status.getLabel()));
        assertTrue(error.getMessage().contains("不可发起打样"));
    }
}
