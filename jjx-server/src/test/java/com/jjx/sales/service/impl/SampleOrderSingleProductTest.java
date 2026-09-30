package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.service.ProductCustomerValidator;
import com.jjx.sales.domain.dto.SampleOrderCreateDTO;
import com.jjx.sales.domain.dto.SampleOrderUpdateDTO;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.system.service.OperLogChangeRecorder;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SampleOrderSingleProductTest {
    @Mock OrderMapper orderMapper;
    @Mock CustomerMapper customerMapper;
    @Mock SalesSampleOrderMapper sampleOrderMapper;
    @Mock SalesOrderProductMapper orderProductMapper;
    @Mock ISalesOrderProductService orderProductService;
    @Mock ProductCustomerValidator productCustomerValidator;
    @Mock OperLogChangeRecorder changeRecorder;
    @InjectMocks SampleOrderServiceImpl service;

    private SampleOrderCreateDTO createDto(Integer quantity) {
        var dto = new SampleOrderCreateDTO();
        dto.setCustomerId(7L);
        var item = new SampleOrderCreateDTO.Item();
        item.setProductId(20L);
        item.setProductCode("P20");
        item.setProductName("成品20");
        item.setQuantity(quantity);
        dto.setItems(List.of(item));
        return dto;
    }

    private SampleOrderUpdateDTO updateDto(Integer quantity) {
        var dto = new SampleOrderUpdateDTO();
        dto.setCustomerId(7L);
        var item = new SampleOrderUpdateDTO.Item();
        item.setProductId(20L);
        item.setProductCode("P20");
        item.setProductName("成品20");
        item.setQuantity(quantity);
        item.setUnit("PCS");
        dto.setItems(List.of(item));
        return dto;
    }

    private void existingDraft() {
        var order = new SalesOrder();
        order.setOrderId(10L);
        order.setCustomerId(7L);
        order.setSampleStatus(SampleOrderStatusEnum.CREATED.getValue());
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(customerMapper.selectById(7L)).thenReturn(new SalesCustomer());
    }

    private void verifyNoOrderWrites() {
        verify(orderMapper, never()).insert(any(SalesOrder.class));
        verify(orderMapper, never()).updateById(any(SalesOrder.class));
        verify(orderProductService, never()).deleteByOrderId(anyLong());
        verify(orderProductService, never()).batchAdd(anyList());
        verifyNoInteractions(sampleOrderMapper);
    }

    @Test
    void createRejectsMultipleProductsBeforeWriting() {
        var dto = createDto(2);
        dto.setItems(List.of(dto.getItems().getFirst(), dto.getItems().getFirst()));
        assertThrows(BusinessException.class, () -> service.createSample(dto));
        verifyNoOrderWrites();
    }

    @Test
    void createWithoutQuotationRejectsMissingProduct() {
        var dto = createDto(2);
        dto.setItems(List.of());
        assertThrows(BusinessException.class, () -> service.createSample(dto));
        dto.setItems(null);
        assertThrows(BusinessException.class, () -> service.createSample(dto));
        verifyNoOrderWrites();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void createRejectsInvalidQuantity(Integer quantity) {
        assertThrows(BusinessException.class, () -> service.createSample(createDto(quantity)));
        verifyNoOrderWrites();
    }

    @Test
    void updateRejectsMultipleProductsBeforeReplacingOriginal() {
        existingDraft();
        var dto = updateDto(3);
        dto.setItems(List.of(dto.getItems().getFirst(), dto.getItems().getFirst()));
        assertThrows(BusinessException.class, () -> service.updateSampleOrder(10L, dto));
        verifyNoOrderWrites();
    }

    @Test
    void updateRejectsMissingProductBeforeReplacingOriginal() {
        existingDraft();
        var dto = updateDto(3);
        dto.setItems(List.of());
        assertThrows(BusinessException.class, () -> service.updateSampleOrder(10L, dto));
        verifyNoOrderWrites();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void updateRejectsInvalidQuantity(Integer quantity) {
        existingDraft();
        assertThrows(BusinessException.class, () -> service.updateSampleOrder(10L, updateDto(quantity)));
        verifyNoOrderWrites();
    }

    @Test
    void editingOneProductUpdatesSnapshotAndAllowsMultiplePieces() {
        existingDraft();
        var profile = new SalesSampleOrder();
        profile.setProductId(19L);
        when(sampleOrderMapper.selectByOrderId(10L)).thenReturn(profile);
        var product = new SalesOrderProduct();
        product.setProductId(20L);
        product.setProductCode("P20");
        product.setProductName("成品20");
        product.setQuantity(5);
        product.setUnit("PCS");
        when(orderProductMapper.selectList(any())).thenReturn(List.of(product));
        var savedProduct = new SalesOrderProductVO();
        savedProduct.setProductId(20L);
        savedProduct.setQuantity(5);
        when(orderProductService.getListByOrderId(10L)).thenReturn(List.of(savedProduct));

        service.updateSampleOrder(10L, updateDto(5));

        verify(productCustomerValidator).validateBelongsToCustomer(20L, 7L);
        verify(orderProductService).batchAdd(argThat(items -> items.size() == 1
                && items.getFirst().getProductId().equals(20L) && items.getFirst().getQuantity() == 5));
        verify(sampleOrderMapper).updateById(argThat((SalesSampleOrder p) -> p.getProductId().equals(20L)
                && "P20".equals(p.getProductCode()) && "成品20".equals(p.getProductName())));
        verify(orderMapper).updateById(argThat((SalesOrder o) -> Integer.valueOf(5).equals(o.getSampleQty())));
    }

    @Test
    void httpValidationEnforcesSingleProductAndPositiveQuantity() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(createDto(5)).isEmpty());
            assertTrue(validator.validate(updateDto(5)).isEmpty());
            assertFalse(validator.validate(createDto(0)).isEmpty());
            assertFalse(validator.validate(updateDto(-1)).isEmpty());
            var create = createDto(5);
            create.setItems(List.of(create.getItems().getFirst(), create.getItems().getFirst()));
            assertFalse(validator.validate(create).isEmpty());
            var update = updateDto(5);
            update.setItems(List.of(update.getItems().getFirst(), update.getItems().getFirst()));
            assertFalse(validator.validate(update).isEmpty());
            update.setItems(List.of());
            assertFalse(validator.validate(update).isEmpty());
        }
    }
}
