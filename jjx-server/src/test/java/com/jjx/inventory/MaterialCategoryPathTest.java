package com.jjx.inventory;

import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.service.impl.InventoryMaterialCategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** dev-20261024-006：分类维护时同步维护 category_path / name_path（父级变化递归刷新子节点）。 */
@ExtendWith(MockitoExtension.class)
class MaterialCategoryPathTest {

    @Mock InventoryMaterialCategoryMapper mapper;
    @Mock com.jjx.event.EventPublisher eventPublisher;
    @Mock InventoryMaterialMapper materialMapper;
    @InjectMocks InventoryMaterialCategoryServiceImpl service;

    private void bindBaseMapper() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void saveChildComposesChineseNamePathFromParent() {
        bindBaseMapper();
        InventoryMaterialCategory root = new InventoryMaterialCategory();
        root.setCategoryId(13L); root.setCategoryCode("RAW"); root.setCategoryName("原材料");
        root.setParentId(0L); root.setCategoryLevel(1); root.setCategoryPath("RAW"); root.setNamePath("原材料");
        when(mapper.selectById(13L)).thenReturn(root);
        when(mapper.insert(any(InventoryMaterialCategory.class))).thenReturn(1);

        InventoryMaterialCategory child = new InventoryMaterialCategory();
        child.setCategoryCode("RAW_AUX"); child.setCategoryName("辅材"); child.setParentId(13L);
        assertTrue(service.save(child));

        assertEquals(2, child.getCategoryLevel());
        assertEquals("RAW/RAW_AUX", child.getCategoryPath());
        assertEquals("原材料/辅材", child.getNamePath());
        ArgumentCaptor<InventoryMaterialCategory> inserted = ArgumentCaptor.forClass(InventoryMaterialCategory.class);
        verify(mapper).insert(inserted.capture());
        assertEquals("原材料/辅材", inserted.getValue().getNamePath());
    }

    @Test
    void renameParentRefreshesDescendantNamePath() {
        bindBaseMapper();
        InventoryMaterialCategory oldRoot = new InventoryMaterialCategory();
        oldRoot.setCategoryId(13L); oldRoot.setCategoryCode("RAW"); oldRoot.setCategoryName("原料");
        oldRoot.setParentId(0L); oldRoot.setCategoryLevel(1); oldRoot.setCategoryPath("RAW"); oldRoot.setNamePath("原料");
        InventoryMaterialCategory renamed = new InventoryMaterialCategory();
        renamed.setCategoryId(13L); renamed.setCategoryName("原材料"); renamed.setParentId(0L); renamed.setCategoryCode("RAW");
        // 第一次取旧值用于比较，刷新子节点时父节点已更新为新值
        when(mapper.selectById(13L)).thenReturn(oldRoot, renamed);
        when(mapper.updateById(any(InventoryMaterialCategory.class))).thenReturn(1);

        InventoryMaterialCategory child = new InventoryMaterialCategory();
        child.setCategoryId(17L); child.setCategoryCode("RAW_AUX"); child.setCategoryName("辅材");
        child.setParentId(13L); child.setCategoryLevel(2); child.setCategoryPath("RAW/RAW_AUX"); child.setNamePath("原料/辅材");
        when(mapper.selectList(any())).thenReturn(List.of(child));

        assertTrue(service.updateById(renamed));
        assertEquals("原材料", renamed.getNamePath());
        ArgumentCaptor<InventoryMaterialCategory> updated = ArgumentCaptor.forClass(InventoryMaterialCategory.class);
        verify(mapper, times(2)).updateById(updated.capture());
        assertEquals("原材料/辅材", updated.getAllValues().get(1).getNamePath());
    }
}
