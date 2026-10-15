package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.dto.transfer.SampleTransferConfirmDTO;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 资料转移：材料项目「逐条单选 / 整单通用」的校验语义（dev-20261015-025）。
 * 只覆盖校验段——确认前前置校验必须先拦截非法输入，避免按项目复制多条材料或默认第一道。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SampleTransferMaterialProjectValidationTest {

    @Mock OrderMapper orderMapper;
    @Mock ProductStandardProcessMapper standardProcessMapper;
    @Mock RedisSequenceService redisSequenceService;
    @InjectMocks SampleOrderServiceImpl service;

    private SampleTransferConfirmDTO dto(List<SampleTransferConfirmDTO.MaterialMapping> materials,
                                         List<SampleTransferConfirmDTO.MaterialProjectSelection> selections) {
        var d = new SampleTransferConfirmDTO();
        d.setOrderId(10L);
        var pm = new SampleTransferConfirmDTO.ProcessMapping();
        pm.setStdProcessId(1L);
        pm.setProcessName("工序P");
        pm.setProcessOrder(1);
        d.setProcessMappings(List.of(pm));
        d.setMaterialMappings(materials);
        d.setMaterialProjects(selections);
        return d;
    }

    private SampleTransferConfirmDTO.MaterialMapping material(String rowKey, List<Long> projectIds) {
        var mm = new SampleTransferConfirmDTO.MaterialMapping();
        mm.setRowKey(rowKey);
        mm.setMaterialName("材料M");
        mm.setProjectIds(projectIds);
        return mm;
    }

    private SampleTransferConfirmDTO.MaterialProjectSelection selection(String rowKey, Long processId, boolean common) {
        var s = new SampleTransferConfirmDTO.MaterialProjectSelection();
        s.setRowKey(rowKey);
        s.setProcessId(processId);
        s.setIsCommon(common);
        return s;
    }

    private void confirmedOrder() {
        var o = new SalesOrder();
        o.setOrderId(10L);
        o.setSampleStatus(SampleOrderStatusEnum.CONFIRMED.getValue());
        o.setDeleted(0);
        when(orderMapper.selectById(10L)).thenReturn(o);
    }

    private void enabledProcess(Long id) {
        var p = new ProductStandardProcess();
        p.setProcessId(id);
        p.setProcessName("项目" + id);
        p.setIsEnabled(1);
        when(standardProcessMapper.selectById(id)).thenReturn(p);
    }

    @Test
    void rejectsCommonFlagOnCombinationMaterial() {
        confirmedOrder();
        var ex = assertThrows(BusinessException.class, () -> service.confirmTransfer(
                dto(List.of(material("r1", List.of(1L, 2L))), List.of(selection("r1", null, true)))));
        assertTrue(ex.getMessage().contains("组合项目须选择其中一个工序"));
    }

    @Test
    void rejectsMaterialWithoutAnyChoice() {
        confirmedOrder();
        var ex = assertThrows(BusinessException.class, () -> service.confirmTransfer(
                dto(List.of(material("r1", null)), new ArrayList<>())));
        assertTrue(ex.getMessage().contains("请为材料选择单一项目或整单通用"));
    }

    @Test
    void rejectsProcessOutsideMaterialProjectScope() {
        confirmedOrder();
        var ex = assertThrows(BusinessException.class, () -> service.confirmTransfer(
                dto(List.of(material("r1", List.of(5L, 6L))), List.of(selection("r1", 7L, false)))));
        assertTrue(ex.getMessage().contains("所选项目不在材料项目范围内"));
    }

    @Test
    void rejectsMissingOrDisabledProcess() {
        confirmedOrder();
        // 未建/不存在
        assertThrows(BusinessException.class, () -> service.confirmTransfer(
                dto(List.of(material("r1", null)), List.of(selection("r1", 5L, false)))));
        // 已停用
        var disabled = new ProductStandardProcess();
        disabled.setProcessId(6L);
        disabled.setIsEnabled(0);
        when(standardProcessMapper.selectById(6L)).thenReturn(disabled);
        assertThrows(BusinessException.class, () -> service.confirmTransfer(
                dto(List.of(material("r1", null)), List.of(selection("r1", 6L, false)))));
    }

    @Test
    void rejectsStaleMaterialSelection() {
        confirmedOrder();
        enabledProcess(5L);
        var ex = assertThrows(BusinessException.class, () -> service.confirmTransfer(
                dto(List.of(material("r1", null)),
                        List.of(selection("r1", 5L, false), selection("r-stale", 5L, false)))));
        assertTrue(ex.getMessage().contains("材料已变化"));
    }

    @Test
    void rejectsDuplicatedSelectionKey() {
        confirmedOrder();
        var ex = assertThrows(BusinessException.class, () -> service.confirmTransfer(
                dto(List.of(material("r1", null)),
                        List.of(selection("r1", 5L, false), selection("r1", 6L, false)))));
        assertTrue(ex.getMessage().contains("材料项目确认重复"));
    }

    @Test
    void validSelectionPassesValidationAndReachesWrites() {
        confirmedOrder();
        enabledProcess(5L);
        when(redisSequenceService.generateBusinessNumberByType(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("REACHED_WRITES"));
        var thrown = assertThrows(RuntimeException.class, () -> service.confirmTransfer(
                dto(List.of(material("r1", null)), List.of(selection("r1", 5L, false)))));
        assertEquals("REACHED_WRITES", thrown.getMessage());
    }
}
