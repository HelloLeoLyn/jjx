package com.jjx.sales.service.impl;

import com.jjx.sales.domain.vo.SalesOrderVO;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.enums.SalesOrderTypeEnum;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSampleProgressTest {
    @Mock SalesSampleOrderMapper mapper;
    @Mock com.jjx.sales.mapper.OrderMapper orders;
    @Mock com.jjx.sales.domain.converter.SalesOrderConverter converter;
    @InjectMocks OrderServiceImpl service;

    private SalesOrderVO row(long id, Integer active, Integer remaining) {
        var row = new SalesOrderVO(); row.setOrderId(id);
        row.setActiveSampleCount(active); row.setSampleableProductCount(remaining); return row;
    }

    @Test void batchesWholePageWithExactlyTheActiveStatusSet() {
        var page = List.of(row(1, null, null), row(2, null, null), row(3, null, null));
        var statuses = List.of(SampleOrderStatusEnum.CREATED, SampleOrderStatusEnum.REQUEST,
                SampleOrderStatusEnum.ENGINEERING, SampleOrderStatusEnum.SAMPLE_READY,
                SampleOrderStatusEnum.SAMPLE_SENT, SampleOrderStatusEnum.REJECTED,
                SampleOrderStatusEnum.CONFIRMED, SampleOrderStatusEnum.TRANSFERRED).stream()
                .map(SampleOrderStatusEnum::getValue).toList();
        when(mapper.selectOrderSampleProgress(anyList(), eq("SALES_ORDER"), eq(SalesOrderTypeEnum.SAMPLE.getCode()), eq(statuses)))
                .thenReturn(List.of(row(1, 1, 2), row(2, 3, 0)));
        service.fillSampleProgress(page);
        assertEquals(1, page.get(0).getActiveSampleCount());
        assertEquals(2, page.get(0).getSampleableProductCount());
        assertEquals(0, page.get(1).getSampleableProductCount());
        assertEquals(0, page.get(2).getActiveSampleCount());
        assertEquals(0, page.get(2).getSampleableProductCount());
        verify(mapper, times(1)).selectOrderSampleProgress(argThat(ids -> ids.size() == 3 && ids.containsAll(List.of(1L,2L,3L))),
                eq("SALES_ORDER"), eq(SalesOrderTypeEnum.SAMPLE.getCode()), eq(statuses));
    }

    @Test void pageQueryReturnsProgressWithoutChangingMainDataOrPagination() {
        var entity = new com.jjx.sales.domain.entity.SalesOrder(); entity.setOrderId(1L);
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.jjx.sales.domain.entity.SalesOrder>(2, 10, 21);
        page.setRecords(List.of(entity));
        when(orders.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class), any())).thenReturn(page);
        var vo = row(1, null, null); vo.setOrderNo("SO1");
        when(converter.toVOList(List.of(entity))).thenReturn(List.of(vo));
        when(mapper.selectOrderSampleProgress(anyList(), anyString(), anyInt(), anyList())).thenReturn(List.of(row(1, 2, 1)));
        var query = new com.jjx.sales.domain.dto.SalesOrderQueryDTO(); query.setPageNum(2); query.setPageSize(10);
        var result = service.pageQuery(query);
        assertEquals(21, result.getTotal()); assertEquals(2, result.getPageNum());
        assertEquals("SO1", result.getRecords().get(0).getOrderNo());
        assertEquals(2, result.getRecords().get(0).getActiveSampleCount());
        assertEquals(1, result.getRecords().get(0).getSampleableProductCount());
    }

    @Test void failureAndNullCountsFallBackToZeroAndEmptyPageDoesNotQuery() {
        service.fillSampleProgress(List.of()); verifyNoInteractions(mapper);
        var row = row(1, 9, 9);
        when(mapper.selectOrderSampleProgress(anyList(), anyString(), anyInt(), anyList()))
                .thenThrow(new IllegalStateException("query failed"));
        assertDoesNotThrow(() -> service.fillSampleProgress(List.of(row)));
        assertEquals(0, row.getActiveSampleCount()); assertEquals(0, row.getSampleableProductCount());
        doReturn(List.of(row(1, null, null))).when(mapper)
                .selectOrderSampleProgress(anyList(), anyString(), anyInt(), anyList());
        service.fillSampleProgress(List.of(row));
        assertEquals(0, row.getActiveSampleCount()); assertEquals(0, row.getSampleableProductCount());
    }
}
