package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesSampleBom;
import com.jjx.sales.dto.save.SampleProcessPlanDTO;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesSampleBomMapper;
import com.jjx.sales.mapper.SalesSampleProcessMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打样独立 BOM 材料（sales_sample_bom）的落库与回读（dev-20261015-025）。
 * 覆盖：saveSampleBom 归一口径/轮次作用域、saveProcessPlan 携带 standaloneMaterials、
 * 空列表清空、名称/数量/项目非法值拦截。
 */
@ExtendWith(MockitoExtension.class)
class SampleStandaloneMaterialPersistenceTest {

    @Mock OrderMapper orderMapper;
    @Mock SalesSampleBomMapper sampleBomMapper;
    @Mock SalesSampleProcessMapper sampleProcessMapper;
    @Mock ProductStandardProcessMapper standardProcessMapper;
    @InjectMocks SampleOrderServiceImpl service;

    private SalesOrder order(Integer status) {
        var o = new SalesOrder();
        o.setOrderId(10L);
        o.setSampleStatus(status);
        o.setSampleRound(1);
        o.setDeleted(0);
        return o;
    }

    private SalesSampleBom bom(String name, BigDecimal qty, BigDecimal base, BigDecimal module, String projectIds) {
        var b = new SalesSampleBom();
        b.setMaterialName(name);
        b.setQuantity(qty);
        b.setBaseQty(base);
        b.setModuleQty(module);
        b.setProjectIds(projectIds);
        b.setUnit("PCS");
        return b;
    }

    private static void assertValue(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void saveSampleBomNormalizesAndScopesToRound() {
        when(orderMapper.selectById(10L)).thenReturn(order(SampleOrderStatusEnum.ENGINEERING.getValue()));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            service.saveSampleBom(10L, 1, List.of(
                    bom("基材", new BigDecimal("1"), new BigDecimal("3"), new BigDecimal("4"), "[8,9]"),
                    bom("胶", new BigDecimal("0.25"), null, null, null)));
        }
        verify(sampleBomMapper).delete(any());
        var captor = ArgumentCaptor.forClass(SalesSampleBom.class);
        verify(sampleBomMapper, times(2)).insert(captor.capture());
        var rows = captor.getAllValues();
        // 显式基数/模数优先，单位用量=基数÷模数（四位）
        assertValue("3", rows.get(0).getBaseQty());
        assertValue("4", rows.get(0).getModuleQty());
        assertValue("0.7500", rows.get(0).getQuantity());
        assertEquals("tester", rows.get(0).getCreateBy());
        assertEquals(1, rows.get(0).getRoundNo());
        assertEquals(10L, rows.get(0).getOrderId());
        // 历史小数用量约分为整数基数/模数
        assertValue("1", rows.get(1).getBaseQty());
        assertValue("4", rows.get(1).getModuleQty());
        assertValue("0.2500", rows.get(1).getQuantity());
    }

    @Test
    void saveSampleBomEmptyListClearsRoundWithoutInsert() {
        when(orderMapper.selectById(10L)).thenReturn(order(SampleOrderStatusEnum.ENGINEERING.getValue()));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            service.saveSampleBom(10L, 2, new ArrayList<>());
        }
        verify(sampleBomMapper).delete(any());
        verify(sampleBomMapper, never()).insert(any(SalesSampleBom.class));
    }

    @Test
    void saveSampleBomRejectsBlankNameInvalidQuantityAndBadProjects() {
        when(orderMapper.selectById(10L)).thenReturn(order(SampleOrderStatusEnum.ENGINEERING.getValue()));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            assertThrows(BusinessException.class,
                    () -> service.saveSampleBom(10L, 1, List.of(bom("  ", BigDecimal.ONE, null, null, null))));
            assertThrows(BusinessException.class,
                    () -> service.saveSampleBom(10L, 1, List.of(bom("x", BigDecimal.ZERO, null, null, null))));
            assertThrows(BusinessException.class,
                    () -> service.saveSampleBom(10L, 1, List.of(bom("x", new BigDecimal("0.12345"), null, null, null))));
            assertThrows(BusinessException.class,
                    () -> service.saveSampleBom(10L, 1, List.of(bom("x", BigDecimal.ONE, null, null, "not-json"))));
            assertThrows(BusinessException.class,
                    () -> service.saveSampleBom(10L, 1, List.of(bom("x", BigDecimal.ONE, null, null, "[0]"))));
        }
    }

    @Test
    void saveProcessPlanPersistsStandaloneMaterials() {
        when(orderMapper.selectById(10L)).thenReturn(order(SampleOrderStatusEnum.ENGINEERING.getValue()));
        when(sampleProcessMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(sampleProcessMapper.selectByOrderAndRound(10L, 1)).thenReturn(new ArrayList<>());
        when(sampleBomMapper.selectByOrderId(10L)).thenReturn(List.of());

        var dto = new SampleProcessPlanDTO();
        dto.setItems(new ArrayList<>());
        dto.setStandaloneMaterials(List.of(bom("独立料", new BigDecimal("2"), new BigDecimal("5"), new BigDecimal("2"), "[7]")));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            service.saveProcessPlan(10L, dto);
        }

        var captor = ArgumentCaptor.forClass(SalesSampleBom.class);
        verify(sampleBomMapper).insert(captor.capture());
        assertValue("5", captor.getValue().getBaseQty());
        assertValue("2", captor.getValue().getModuleQty());
        assertValue("2.5000", captor.getValue().getQuantity());
        assertEquals("独立料", captor.getValue().getMaterialName());
    }

    @Test
    void saveProcessPlanWithoutStandaloneFieldKeepsExistingBomUntouched() {
        when(orderMapper.selectById(10L)).thenReturn(order(SampleOrderStatusEnum.ENGINEERING.getValue()));
        when(sampleProcessMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(sampleProcessMapper.selectByOrderAndRound(10L, 1)).thenReturn(new ArrayList<>());

        var dto = new SampleProcessPlanDTO();
        dto.setItems(new ArrayList<>());
        dto.setStandaloneMaterials(null);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            service.saveProcessPlan(10L, dto);
        }
        verify(sampleBomMapper, never()).delete(any());
        verify(sampleBomMapper, never()).insert(any(SalesSampleBom.class));
    }
}
