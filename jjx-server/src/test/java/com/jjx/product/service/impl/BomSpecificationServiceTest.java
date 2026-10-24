package com.jjx.product.service.impl;

import cn.hutool.json.JSONUtil;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.MaterialMasterService;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BomSpecificationServiceTest {
    final MaterialMasterService master = mock(MaterialMasterService.class);
    final InventoryMaterialMapper materials = mock(InventoryMaterialMapper.class);
    final SysOperLogMapper logs = mock(SysOperLogMapper.class);
    final BomSpecificationService service = new BomSpecificationService(master, materials, logs);
    final InventoryMaterial material = new InventoryMaterial();

    @BeforeEach
    void setup() {
        material.setMaterialId(8L);
        material.setSpecification(""); // 库存规格为空，仍必须通过属性生成器。
        when(materials.selectById(8L)).thenReturn(material);
        when(master.specificationPreview(material)).thenReturn(
                new MaterialMasterService.SpecificationPreview("厚度:0.2mm", true, "属性生成"));
    }

    EngineeringBomItem row(long id, String spec) {
        var item = new EngineeringBomItem();
        item.setItemId(id);
        item.setMaterialId(8L);
        item.setSpecification(spec);
        return item;
    }

    SysOperLog record(List<EngineeringBomItem> rows) {
        when(logs.insert(any(SysOperLog.class))).thenReturn(1);
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("engineer");
            security.when(SecurityUtils::getDisplayName).thenReturn("工程员");
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            service.record(1L, rows);
        }
        var saved = ArgumentCaptor.forClass(SysOperLog.class);
        verify(logs).insert(saved.capture());
        return saved.getValue();
    }

    @Test
    void queryGeneratesFromAttributesAndDoesNotRewriteHistoricalSpecifications() {
        var old = row(10, "历史原文");
        var empty = row(11, "");
        service.enrich(1L, List.of(old, empty));
        assertEquals("厚度:0.2mm", old.getMaterialSpecification());
        assertEquals("历史原文", old.getSpecification());
        assertEquals("", empty.getSpecification());
        assertEquals("厚度:0.2mm", old.getSpecificationBaseline());
        verify(master, times(1)).specificationPreview(material);
        verify(logs, never()).insert(any(SysOperLog.class));
    }

    @Test
    void savedCheckpointSurvivesReopenAndDistinguishesAutoManualAndManuallyClearedValues() {
        var auto = row(10, "厚度:0.2mm");
        var manual = row(11, "工程手填");
        var cleared = row(12, "");
        var saved = record(List.of(auto, manual, cleared));
        assertEquals("engineer", saved.getUsername());
        assertEquals("工程员", saved.getRealName());
        assertNotNull(saved.getCreateTime());
        var snapshots = JSONUtil.parseObj(saved.getDetail()).getJSONArray("specifications");
        assertEquals(3, snapshots.size());
        assertEquals(10L, snapshots.getJSONObject(0).getLong("itemId"));
        when(logs.selectOne(any())).thenReturn(saved);
        when(master.specificationPreview(material)).thenReturn(
                new MaterialMasterService.SpecificationPreview("厚度:0.3mm", true, "属性生成"));
        service.enrich(1L, List.of(auto, manual, cleared));
        assertEquals("厚度:0.2mm", auto.getSpecificationBaseline());
        assertFalse(auto.getSpecificationManual());
        assertTrue(manual.getSpecificationManual());
        assertTrue(cleared.getSpecificationManual());
        assertEquals("厚度:0.3mm", auto.getMaterialSpecification());
        assertEquals("工程手填", manual.getSpecification());
        assertEquals("", cleared.getSpecification());
    }

    @Test
    void checkpointDoesNotApplyAfterAnExternalEditOrMaterialRelink() {
        var saved = record(List.of(row(10, "厚度:0.2mm")));
        when(logs.selectOne(any())).thenReturn(saved);
        var changed = row(10, "外部改写");
        service.enrich(1L, List.of(changed));
        assertEquals(changed.getMaterialSpecification(), changed.getSpecificationBaseline());
        changed.setMaterialId(9L);
        var other = new InventoryMaterial();
        when(materials.selectById(9L)).thenReturn(other);
        when(master.specificationPreview(other)).thenReturn(
                new MaterialMasterService.SpecificationPreview("另一物料规格", true, "属性生成"));
        service.enrich(1L, List.of(changed));
        assertEquals("另一物料规格", changed.getSpecificationBaseline());
        assertEquals("外部改写", changed.getSpecification());
    }

    @Test
    void missingMaterialDoesNotGenerateOrOverwriteAnOldValue() {
        var missing = row(10, "原手填值");
        missing.setMaterialId(99L);
        service.enrich(1L, List.of(missing));
        assertEquals("", missing.getMaterialSpecification());
        assertEquals("原手填值", missing.getSpecification());
    }
}
