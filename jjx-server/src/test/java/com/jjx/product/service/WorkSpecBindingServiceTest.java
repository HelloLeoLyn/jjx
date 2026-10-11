package com.jjx.product.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.mapper.ProductWorkSpecVersionMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.ProductionOrderStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkSpecBindingServiceTest {
    @Mock ProductWorkSpecVersionMapper versions;
    @InjectMocks WorkSpecBindingService service;

    ProductWorkSpecVersion version(Long product, String status) {
        ProductWorkSpecVersion v = new ProductWorkSpecVersion();
        v.setId(11L); v.setProductId(product); v.setStatus(status); return v;
    }
    ProductionOrder order(ProductionOrderStatusEnum status, Long version) {
        ProductionOrder order = new ProductionOrder(); order.setOrderType("WORK_ORDER");
        order.setProductId(1L); order.setOrderStatus(status.getValue()); order.setWorkSpecVersionId(version); return order;
    }
    @Test void issueUsesLatestPublishedAndPersistsItsId() {
        var v = version(1L, "PUBLISHED");
        when(versions.selectLatestPublished(1L)).thenReturn(v);
        when(versions.selectById(11L)).thenReturn(v);
        var changed = order(ProductionOrderStatusEnum.APPROVED, null);
        service.prepareUpdate(order(ProductionOrderStatusEnum.DRAFT, null), changed);
        assertEquals(11L, changed.getWorkSpecVersionId());
    }
    @Test void explicitWrongProductIsRejectedWithoutFallback() {
        when(versions.selectById(11L)).thenReturn(version(2L, "PUBLISHED"));
        assertThrows(BusinessException.class, () -> service.bindOnIssue(1L, 11L));
        verify(versions, never()).selectLatestPublished(any());
    }
    @Test void retiredAndMissingVersionsAreRejected() {
        when(versions.selectById(11L)).thenReturn(version(1L, "RETIRED"));
        assertThrows(BusinessException.class, () -> service.requirePublished(1L, 11L));
        assertThrows(BusinessException.class, () -> service.requirePublished(1L, 99L));
        assertThrows(BusinessException.class, () -> service.bindOnIssue(1L, null));
    }
    @Test void issuedVersionAndProductCannotBeReplaced() {
        var before = order(ProductionOrderStatusEnum.PLANNED, 11L);
        assertThrows(BusinessException.class, () -> service.prepareUpdate(before, order(ProductionOrderStatusEnum.PLANNED, 12L)));
        var changed = order(ProductionOrderStatusEnum.PLANNED, 11L); changed.setProductId(2L);
        assertThrows(BusinessException.class, () -> service.prepareUpdate(before, changed));
        verifyNoInteractions(versions);
    }
    @Test void historicalNullRemainsUnknownAndCannotStartOrRebind() {
        var before = order(ProductionOrderStatusEnum.PLANNED, null);
        var unchanged = order(ProductionOrderStatusEnum.PLANNED, null);
        service.prepareUpdate(before, unchanged); assertNull(unchanged.getWorkSpecVersionId());
        assertThrows(BusinessException.class, () -> service.prepareUpdate(before, order(ProductionOrderStatusEnum.IN_PROGRESS, null)));
        assertThrows(BusinessException.class, () -> service.prepareUpdate(before, order(ProductionOrderStatusEnum.PLANNED, 11L)));
        assertThrows(BusinessException.class, () -> service.prepareUpdate(before, order(ProductionOrderStatusEnum.DRAFT, null)));
        verifyNoInteractions(versions);
    }
    @Test void existingBindingIsValidatedWithoutSelectingLatestOnStart() {
        when(versions.selectById(11L)).thenReturn(version(1L, "PUBLISHED"));
        service.prepareUpdate(order(ProductionOrderStatusEnum.PLANNED, 11L), order(ProductionOrderStatusEnum.IN_PROGRESS, 11L));
        verify(versions, never()).selectLatestPublished(any());
    }
}
