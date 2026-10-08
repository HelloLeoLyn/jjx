package com.jjx.production.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductionBomResolverTest {
    private final EngineeringBomMapper mapper = mock(EngineeringBomMapper.class);
    private final ProductionBomResolver resolver = new ProductionBomResolver(mapper);
    private final ProductionOrder order = new ProductionOrder();

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), EngineeringBom.class);
        order.setOrderNo("WO-TEST"); order.setProductId(2L); order.setBomId(5L);
    }

    @Test void pinnedApprovedVersionSurvivesCurrentVersionSwitch() {
        EngineeringBom old = approvedBom(); old.setIsCurrent(false);
        when(mapper.selectById(5L)).thenReturn(old);
        assertSame(old, resolver.resolve(order));
        verify(mapper, never()).selectOne(any());
    }

    @Test void missingPinnedVersionDoesNotFallBackToCurrent() {
        assertThrows(BusinessException.class, () -> resolver.resolve(order));
        verify(mapper, never()).selectOne(any());
    }

    @Test void wrongProductVersionIsRejected() {
        EngineeringBom wrong = approvedBom(); wrong.setProductId(3L);
        when(mapper.selectById(5L)).thenReturn(wrong);
        assertThrows(BusinessException.class, () -> resolver.resolve(order));
        verify(mapper, never()).selectOne(any());
    }

    @Test void unapprovedAndObsoleteVersionsAreRejected() {
        for (ProductEnums.BomStatus status : ProductEnums.BomStatus.values()) {
            if (status == ProductEnums.BomStatus.APPROVED) continue;
            EngineeringBom bom = approvedBom(); bom.setApproveStatus(status.getValue());
            when(mapper.selectById(5L)).thenReturn(bom);
            assertThrows(BusinessException.class, () -> resolver.resolve(order));
        }
        verify(mapper, never()).selectOne(any());
    }

    @Test void unboundLegacyOrderKeepsCurrentApprovedLookupWithoutWritingBack() {
        order.setBomId(null);
        EngineeringBom current = approvedBom(); current.setBomId(8L);
        when(mapper.selectOne(any())).thenAnswer(call -> {
            LambdaQueryWrapper<EngineeringBom> query = call.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("product_id"));
            assertTrue(sql.contains("is_current"));
            assertTrue(sql.contains("approve_status"));
            assertTrue(query.getParamNameValuePairs().containsValue(order.getProductId()));
            assertTrue(query.getParamNameValuePairs().containsValue(ProductEnums.BomStatus.APPROVED.getValue()));
            return current;
        });
        assertSame(current, resolver.resolve(order));
        assertNull(order.getBomId());
        verify(mapper).selectOne(any());
        verify(mapper, never()).selectById(any());
        verifyNoMoreInteractions(mapper);
    }

    @Test void legacyOrderWithoutCurrentBomRemainsUnbound() {
        order.setBomId(null);
        assertNull(resolver.resolve(order));
        assertNull(order.getBomId());
    }

    private EngineeringBom approvedBom() {
        EngineeringBom bom = new EngineeringBom(); bom.setBomId(5L); bom.setProductId(2L);
        bom.setApproveStatus(ProductEnums.BomStatus.APPROVED.getValue());
        return bom;
    }
}
