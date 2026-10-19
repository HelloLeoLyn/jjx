package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.validation.QuotationItemsValidator;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IProductService;
import com.jjx.product.service.ProductCustomerValidator;
import com.jjx.sales.domain.converter.SalesQuotationConverter;
import com.jjx.sales.domain.dto.SalesQuotationAddDTO;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesQuotationItem;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.IOrderService;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 服务层回归，模拟持久层；不代表真实业务库或浏览器验证。 */
class QuotationProductEntryTest {
    private QuotationMapper quotations;
    private SalesQuotationItemMapper items;
    private ProductMapper products;
    private IProductService productService;
    private ProductCustomerValidator customers;
    private SalesQuotationConverter converter;
    private QuotationServiceImpl service;
    private SalesQuotation quotation;
    private SalesQuotationItem item;
    private Product product;

    @BeforeEach
    void setup() {
        quotations = mock(QuotationMapper.class);
        items = mock(SalesQuotationItemMapper.class);
        products = mock(ProductMapper.class);
        productService = mock(IProductService.class);
        customers = mock(ProductCustomerValidator.class);
        converter = mock(SalesQuotationConverter.class);
        service = spy(new QuotationServiceImpl(quotations, mock(QuotationFlowMapper.class), items,
                mock(SalesInquiryMapper.class), products, customers, productService,
                mock(IOrderService.class), mock(OrderMapper.class), mock(ISysAttachmentService.class),
                mock(RedisSequenceService.class), mock(OperLogChangeRecorder.class),
                mock(SysAttachmentMapper.class), mock(SysAttachmentRefMapper.class),
                mock(AttachmentRefService.class), mock(AttachmentDocumentTypeService.class),
                converter, mock(EventPublisher.class)));
        doNothing().when(service).recalcQuotationAmounts(anyLong());
        quotation = new SalesQuotation();
        quotation.setCustomerId(1L);
        quotation.setQuotationNo("QT-PRODUCT-TEST");
        quotation.setSalesPersonId(1L);
        quotation.setSalesPersonName("tester");
        item = new SalesQuotationItem();
        item.setProductCode("P-NEW");
        item.setProductName("测试产品");
        item.setQuantity(1);
        item.setUnit("PCS");
        item.setUnitPrice(BigDecimal.TEN);
        quotation.setItems(List.of(item));
        product = new Product();
        product.setProductId(8L);
        product.setProductCode("P-NEW");
        product.setCustomerId(1L);
        when(customers.validateSelectable(8L, 1L)).thenReturn(product);
        when(customers.validateBelongsToCustomer(8L, 1L)).thenReturn(product);
        when(converter.toEntity(any())).thenReturn(quotation);
        when(quotations.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(quotations.insert(any(SalesQuotation.class))).thenAnswer(call -> {
            quotation.setQuotationId(10L);
            return 1;
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void bothTypesSaveSelectedExistingProduct(int type) {
        quotation.setQuotationType(type);
        item.setProductId(8L);
        assertEquals(10L, service.insertQuotation(new SalesQuotationAddDTO()));
        verify(customers).validateSelectable(8L, 1L);
        verifyNoInteractions(productService);
        verify(items).insert(item);
        assertEquals(8L, item.getProductId());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void bothTypesCreateAndLinkNewProduct(int type) {
        quotation.setQuotationType(type);
        when(productService.ensureDraftProduct(eq("P-NEW"), eq("测试产品"), eq("PCS"), eq("quotation"),
                eq(1L), any(), any(), any(), any(), any())).thenReturn(8L);
        assertTrue(new QuotationItemsValidator().isValid(quotation, null));
        assertEquals(10L, service.insertQuotation(new SalesQuotationAddDTO()));
        verify(customers).validateBelongsToCustomer(8L, 1L);
        verify(customers, never()).validateSelectable(anyLong(), anyLong());
        verify(items).insert(item);
        assertEquals(8L, item.getProductId());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void existingProductCannotBypassCustomerCheckBySubmittingOnlyCode(int type) {
        quotation.setQuotationType(type);
        when(products.selectOne(any(Wrapper.class))).thenReturn(product);
        when(customers.validateSelectable(8L, 1L)).thenThrow(new BusinessException("不属于当前客户"));
        assertThrows(BusinessException.class, () -> service.insertQuotation(new SalesQuotationAddDTO()));
        verify(quotations, never()).insert(any(SalesQuotation.class));
        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void failedCreationNeverWritesUnlinkedDetails(int type) {
        quotation.setQuotationType(type);
        when(productService.ensureDraftProduct(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException("建档失败"));
        assertThrows(BusinessException.class, () -> service.insertQuotation(new SalesQuotationAddDTO()));
        verify(items, never()).insert(any(SalesQuotationItem.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void editingKeepsPreviouslyLinkedDraftButStillChecksCustomer(int type) {
        quotation.setQuotationType(type);
        item.setProductId(8L);
        SalesQuotation original = new SalesQuotation();
        original.setItems(List.of(item));
        ReflectionTestUtils.invokeMethod(service, "validateCustomerProducts", quotation, original);
        verify(customers).validateBelongsToCustomer(8L, 1L);
        verify(customers, never()).validateSelectable(anyLong(), anyLong());
    }

    @Test
    void mismatchedProductIdAndCodeAreRejected() {
        item.setProductId(8L);
        item.setProductCode("OTHER");
        assertThrows(BusinessException.class, () -> service.insertQuotation(new SalesQuotationAddDTO()));
        verify(quotations, never()).insert(any(SalesQuotation.class));
    }
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void selectedProductNameAlwaysComesFromItsArchive(int type) {
        quotation.setQuotationType(type);
        item.setProductId(8L);
        item.setProductName("另一个产品的旧名称");
        product.setProductName("所选产品名称");
        service.insertQuotation(new SalesQuotationAddDTO());
        assertEquals("所选产品名称", item.getProductName());
        verify(items).insert(item);
    }

}
