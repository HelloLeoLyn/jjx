package com.jjx.production;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.domain.vo.DispatchNodeVO;
import com.jjx.production.enums.DispatchNodeStatusEnum;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.production.service.impl.DispatchNodeReadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * P1-E 回归测试：Read Cutover（legacy fallback 已关闭）
 * - Node 存在 → Node 为唯一读取 Source of Truth
 * - 无 Node → 不再 fallback operators（migration/data integrity anomaly），返回空/无当前责任人
 * - isCurrentAssignee / hasUserParticipated 只读 Node
 * 说明：jdbcTemplate 注入 null（cutover 后无 legacy SQL 路径）。
 */
@ExtendWith(MockitoExtension.class)
class DispatchNodeReadCutoverTest {

    @Mock ProductionDispatchNodeMapper nodeMapper;
    @Mock ProductionDispatchMapper dispatchMapper;

    private DispatchNodeReadServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        var ctor = DispatchNodeReadServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (DispatchNodeReadServiceImpl) ctor.newInstance(nodeMapper, dispatchMapper, null);
    }

    private ProductionDispatchNode node(Long id, Long dispatchId, Long parentId, Long assigneeId,
                                        String name, String status) {
        ProductionDispatchNode n = new ProductionDispatchNode();
        n.setNodeId(id);
        n.setDispatchId(dispatchId);
        n.setParentNodeId(parentId);
        n.setAssigneeId(assigneeId);
        n.setAssigneeName(name);
        n.setNodeStatus(status);
        n.setAssignedAt(LocalDateTime.of(2026, 8, 19, 12, 0));
        return n;
    }

    @Test
    void noNodeReturnsEmptyChainNotLegacyFallback() {
        // cutover：无 Node → 空责任链（不再 fallback operators）
        when(nodeMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.getResponsibilityChain(3L).isEmpty());
    }

    @Test
    void noNodeReturnsNullCurrentActive() {
        // cutover：无 Node → 无当前责任人（不再取 legacy 末位 operator）
        when(nodeMapper.selectOne(any())).thenReturn(null);
        when(nodeMapper.selectCount(any())).thenReturn(0L); // hasNodes=false → anomaly 分支
        assertNull(service.getCurrentActiveNode(3L));
    }

    @Test
    void nodePresentIsSourceOfTruth() {
        when(nodeMapper.selectList(any())).thenReturn(List.of(
                node(1L, 3L, null, 96L, "车间主任", "DELEGATED"),
                node(2L, 3L, 1L, 104L, "工人", "ACTIVE")));
        List<DispatchNodeVO> chain = service.getResponsibilityChain(3L);
        assertEquals(2, chain.size());
        assertEquals("NODE", chain.get(0).getSource());
    }

    @Test
    void isCurrentAssigneeOnlyReadsNode() {
        when(nodeMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.isCurrentAssignee(3L, 104L));
    }

    @Test
    void isCurrentAssigneeFalseWhenNotActiveInNode() {
        when(nodeMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.isCurrentAssignee(3L, 96L));
    }

    @Test
    void hasUserParticipatedOnlyReadsNode() {
        when(nodeMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.hasUserParticipated(96L));
    }
}
