package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.sales.domain.dto.SampleOrderCreateDTO;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.service.AttachmentRefService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 样品单随单附件归属一致性（dev-20261018-023，口径对齐订单侧 dev-20261018-018 卡面）。
 * 断言：非法归属时抛业务异常且不产生任何 AttachmentRef 写入；未知编码降为整单附件；一致时按入参落库。
 * 注：Mockito 只能证明「无 ref 落库」；真实事务回滚需 Spring/H2，未在本类覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SampleAttachmentOwnershipTest {

    @Mock ISalesOrderProductService orderProductService;
    @Mock ProductMapper productMapper;
    @Mock AttachmentRefService attachmentRefService;
    @InjectMocks SampleOrderServiceImpl service;

    private static final Long ORDER_ID = 1L;
    private static final Long CUSTOMER_ID = 7L;
    private static final Long PRODUCT_ID = 100L;
    private static final String PRODUCT_CODE = "CODE-A";

    private void stubOrderItems() {
        SalesOrderProductVO row = new SalesOrderProductVO();
        row.setProductId(PRODUCT_ID);
        row.setProductCode(PRODUCT_CODE);
        when(orderProductService.getListByOrderId(ORDER_ID)).thenReturn(List.of(row));
    }

    private void stubProduct(Long productId, Long customerId) {
        Product p = new Product();
        p.setProductId(productId);
        p.setCustomerId(customerId);
        when(productMapper.selectById(productId)).thenReturn(p);
    }

    private SampleOrderCreateDTO.SampleAttachmentDTO attachment(Long productId, String productCode) {
        SampleOrderCreateDTO.SampleAttachmentDTO dto = new SampleOrderCreateDTO.SampleAttachmentDTO();
        dto.setAttachmentId(11L);
        dto.setDocumentType("SAMPLE_REVIEW_EVIDENCE");
        dto.setProductId(productId);
        dto.setProductCode(productCode);
        return dto;
    }

    private void bind(Long customerId, List<SampleOrderCreateDTO.SampleAttachmentDTO> attachments) throws Throwable {
        Method m = SampleOrderServiceImpl.class.getDeclaredMethod(
                "bindSampleAttachments", Long.class, Long.class, List.class);
        m.setAccessible(true);
        try {
            m.invoke(service, ORDER_ID, customerId, attachments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void mismatchedIdAndCodeRejectedWithoutAnyWrite() {
        stubOrderItems();
        stubProduct(PRODUCT_ID, CUSTOMER_ID);
        List<SampleOrderCreateDTO.SampleAttachmentDTO> list = List.of(
                attachment(PRODUCT_ID, PRODUCT_CODE),      // 第一条合法
                attachment(PRODUCT_ID, "CODE-B"));          // 第二条 ID/编码冲突
        assertThrows(BusinessException.class, () -> bind(CUSTOMER_ID, list));
        verify(attachmentRefService, never()).create(any());
    }

    @Test
    void productNotInOrderRejectedWithoutAnyWrite() {
        stubOrderItems();
        assertThrows(BusinessException.class,
                () -> bind(CUSTOMER_ID, List.of(attachment(999L, null))));
        verify(attachmentRefService, never()).create(any());
    }

    @Test
    void productOfOtherCustomerRejectedWithoutAnyWrite() {
        stubOrderItems();
        stubProduct(PRODUCT_ID, 9L);
        assertThrows(BusinessException.class,
                () -> bind(CUSTOMER_ID, List.of(attachment(PRODUCT_ID, null))));
        verify(attachmentRefService, never()).create(any());
    }

    @Test
    void unknownCodeFallsBackToWholeOrderAttachment() throws Throwable {
        stubOrderItems();
        assertDoesNotThrow(() -> bind(CUSTOMER_ID, List.of(attachment(null, "UNKNOWN-CODE"))));

        ArgumentCaptor<AttachmentRefDTO> captor = ArgumentCaptor.forClass(AttachmentRefDTO.class);
        verify(attachmentRefService, times(1)).create(captor.capture());
        assertNull(captor.getValue().getProductId());
        assertNull(captor.getValue().getProductCode());
    }

    @Test
    void consistentProductPersistsAsGiven() throws Throwable {
        stubOrderItems();
        stubProduct(PRODUCT_ID, CUSTOMER_ID);
        assertDoesNotThrow(() -> bind(CUSTOMER_ID, List.of(attachment(PRODUCT_ID, PRODUCT_CODE))));

        ArgumentCaptor<AttachmentRefDTO> captor = ArgumentCaptor.forClass(AttachmentRefDTO.class);
        verify(attachmentRefService).create(captor.capture());
        assertEquals(PRODUCT_ID, captor.getValue().getProductId());
        assertEquals(PRODUCT_CODE, captor.getValue().getProductCode());
        assertEquals("sample", captor.getValue().getSourceType());
        assertEquals(ORDER_ID, captor.getValue().getSourceId());
    }

    @Test
    void wholeOrderAttachmentWithoutProductKeepsBothNull() throws Throwable {
        stubOrderItems();
        assertDoesNotThrow(() -> bind(CUSTOMER_ID, List.of(attachment(null, null))));

        ArgumentCaptor<AttachmentRefDTO> captor = ArgumentCaptor.forClass(AttachmentRefDTO.class);
        verify(attachmentRefService).create(captor.capture());
        assertNull(captor.getValue().getProductId());
        assertNull(captor.getValue().getProductCode());
    }
}
