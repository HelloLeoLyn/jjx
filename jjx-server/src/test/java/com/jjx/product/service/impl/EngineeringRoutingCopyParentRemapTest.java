package com.jjx.product.service.impl;

import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * dev-20261013-032：路线复制时 parent_id 必须由旧 itemId 重映射为新 itemId，
 * 否则组合工序子行挂不上父行（新路线显示缺失/错位）。
 */
@ExtendWith(MockitoExtension.class)
class EngineeringRoutingCopyParentRemapTest {

    @Mock EngineeringRoutingItemMapper mapper;

    private EngineeringRoutingItem item(Long id, Long parentId) {
        EngineeringRoutingItem it = new EngineeringRoutingItem();
        it.setItemId(id);
        it.setParentId(parentId);
        return it;
    }

    private void assignIds(AtomicLong seq) {
        lenient().when(mapper.insert(any(EngineeringRoutingItem.class))).thenAnswer(inv -> {
            EngineeringRoutingItem it = inv.getArgument(0);
            if (it.getItemId() == null) it.setItemId(seq.getAndIncrement());
            return 1;
        });
    }

    @Test
    void remapsChildParentIdToNewIds() {
        assignIds(new AtomicLong(200));
        when(mapper.selectByRoutingId(5L)).thenReturn(List.of(item(100L, null), item(101L, 100L)));

        Map<Long, Long> idMap = EngineeringRoutingServiceImpl.copyRoutingDetails(mapper, 5L, 9L);

        assertEquals(200L, idMap.get(100L));
        assertEquals(201L, idMap.get(101L));
        // 插入阶段先落根（parent_id=null），且都指向新路线
        ArgumentCaptor<EngineeringRoutingItem> ins = ArgumentCaptor.forClass(EngineeringRoutingItem.class);
        verify(mapper, times(2)).insert(ins.capture());
        assertTrue(ins.getAllValues().stream().allMatch(i -> i.getParentId() == null && i.getRoutingId() == 9L));
        // 回填：子行(新201)的 parent_id = 新父(200)
        ArgumentCaptor<EngineeringRoutingItem> up = ArgumentCaptor.forClass(EngineeringRoutingItem.class);
        verify(mapper, times(1)).updateById(up.capture());
        assertEquals(201L, up.getValue().getItemId());
        assertEquals(200L, up.getValue().getParentId());
    }

    @Test
    void flatRouteHasNoParentRemap() {
        assignIds(new AtomicLong(300));
        when(mapper.selectByRoutingId(5L)).thenReturn(List.of(item(100L, null), item(101L, null)));

        EngineeringRoutingServiceImpl.copyRoutingDetails(mapper, 5L, 9L);

        verify(mapper, times(2)).insert(any(EngineeringRoutingItem.class));
        verify(mapper, never()).updateById(any(EngineeringRoutingItem.class));
    }

    @Test
    void multipleChildrenRemappedAndMissingParentDefaultsToRoot() {
        assignIds(new AtomicLong(400));
        when(mapper.selectByRoutingId(5L)).thenReturn(List.of(
                item(100L, null), item(101L, 100L), item(102L, 100L), item(103L, 999L)));

        Map<Long, Long> idMap = EngineeringRoutingServiceImpl.copyRoutingDetails(mapper, 5L, 9L);

        assertEquals(400L, idMap.get(100L));
        // 只有 101、102 回填；103 的旧父 999 不存在 → 保持为根，跳过
        verify(mapper, times(2)).updateById(any(EngineeringRoutingItem.class));
    }
}
