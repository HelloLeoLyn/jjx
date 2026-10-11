package com.jjx.sales;

import com.jjx.product.service.WorkSpecBindingService;
import com.jjx.sales.domain.converter.SalesOrderProductConverter;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import com.jjx.sales.service.impl.SalesOrderProductServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderWorkSpecBindingTest {
    @Mock SalesOrderProductMapper mapper;
    @Mock SalesOrderProductConverter converter;
    @Mock WorkSpecBindingService binding;
    @Spy @InjectMocks SalesOrderProductServiceImpl service;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), SalesOrderProduct.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }
    SalesOrderProduct line(long id, Long version) {
        var line = new SalesOrderProduct(); line.setId(id); line.setProductId(1L); line.setOrderId(5L); line.setWorkSpecVersionId(version); return line;
    }
    SalesOrderProductDTO item(Long id) {
        var dto = new SalesOrderProductDTO(); dto.setId(id); dto.setProductId(1L); dto.setOrderId(5L); return dto;
    }
    @Test void newLineAdoptsCurrentPublishedVersion() {
        var dto = item(null); var entity = line(1, null);
        when(converter.toEntity(dto)).thenReturn(entity); when(binding.latestPublishedId(1L)).thenReturn(11L);
        doReturn(true).when(service).save(entity);
        assertTrue(service.add(dto)); assertEquals(11L, entity.getWorkSpecVersionId());
    }
    @Test void newLineMayHaveNoPublishedVersion() {
        var dto = item(null); var entity = line(1, null);
        when(binding.latestPublishedId(1L)).thenReturn(null);
        when(converter.toEntity(dto)).thenReturn(entity); doReturn(true).when(service).save(entity);
        assertTrue(service.add(dto)); assertNull(entity.getWorkSpecVersionId());
    }
    @Test void editPreservesExactLineVersionsIncludingUnknownWhenReordered() {
        when(mapper.selectList(any())).thenReturn(List.of(line(10, 11L), line(20, null)));
        var first = item(20L); var second = item(10L);
        var firstEntity = line(0, null); var secondEntity = line(0, null);
        when(converter.toEntity(first)).thenReturn(firstEntity); when(converter.toEntity(second)).thenReturn(secondEntity);
        doReturn(true).when(service).deleteByOrderId(5L); doReturn(true).when(service).saveBatch(anyCollection());
        assertTrue(service.replaceItems(5L, List.of(first, second)));
        assertNull(firstEntity.getWorkSpecVersionId()); assertEquals(11L, secondEntity.getWorkSpecVersionId());
        verifyNoInteractions(binding);
    }
    @Test void confirmHistoricalUnknownDoesNotLookupLatest() {
        when(mapper.selectList(any())).thenReturn(List.of(line(10, null)));
        service.validateAdoptedVersions(5L); verifyNoInteractions(binding);
    }
    @Test void confirmChecksStoredVersionOnly() {
        when(mapper.selectList(any())).thenReturn(List.of(line(10, 11L)));
        service.validateAdoptedVersions(5L); verify(binding).requirePublished(1L, 11L);
        verify(binding, never()).latestPublishedId(any());
    }
}
