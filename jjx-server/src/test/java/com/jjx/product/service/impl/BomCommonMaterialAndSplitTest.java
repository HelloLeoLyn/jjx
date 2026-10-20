package com.jjx.product.service.impl;

import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.domain.dto.EngineeringBomItemDTO;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BomCommonMaterialAndSplitTest {
    @InjectMocks EngineeringBomServiceImpl service;
    @Mock EngineeringBomMapper productBomMapper;
    @Mock EngineeringBomItemMapper productBomItemMapper;

    private EngineeringBomItem convert(EngineeringBomItemDTO dto) throws Exception {
        Method method = EngineeringBomServiceImpl.class.getDeclaredMethod("toBomItemEntity", EngineeringBomItemDTO.class);
        method.setAccessible(true);
        return (EngineeringBomItem) method.invoke(service, dto);
    }

    @Test
    void emptyProjectOverridesStaleFlagWhileNamedProjectRequiresMatching() throws Exception {
        var dto = new EngineeringBomItemDTO();
        dto.setIsCommon(false);
        assertTrue(convert(dto).getIsCommon());
        dto.setProcessName("未匹配项目");
        assertFalse(convert(dto).getIsCommon());
        dto.setProcessId(7L);
        dto.setIsCommon(true);
        assertFalse(convert(dto).getIsCommon());
    }

    @Test
    void splitSurvivesSaveConversionAndCanBeClearedWithoutChangingDemand() throws Exception {
        var dto = new EngineeringBomItemDTO();
        dto.setSplit(" 50mm ");
        dto.setBaseQty(BigDecimal.ONE);
        dto.setModuleQty(new BigDecimal("5"));
        dto.setLossRate(0);
        var item = convert(dto);
        assertEquals("50mm", item.getSplit());
        assertEquals(0, new BigDecimal("0.2").compareTo(item.getQuantity()));
        dto.setSplit(null);
        assertNull(convert(dto).getSplit());
        dto.setSplit("");
        assertEquals("", convert(dto).getSplit());
    }

    @Test
    void submitAllowsEmptyProjectButRejectsUnmatchedProjectName() {
        var bom = new EngineeringBom();
        bom.setBomId(1L);
        var item = new EngineeringBomItem();
        item.setItemId(2L);
        item.setBomId(1L);
        item.setIsCommon(false); // 历史标记不能阻止公共物料提交。
        when(productBomMapper.selectById(1L)).thenReturn(bom);
        when(productBomItemMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(item));
        assertTrue(service.checkBomForSubmit(1L).stream().noneMatch(issue -> "processId".equals(issue.getField())));
        item.setProcessName("未匹配项目");
        assertTrue(service.checkBomForSubmit(1L).stream().anyMatch(issue -> "processId".equals(issue.getField())));
    }
}
