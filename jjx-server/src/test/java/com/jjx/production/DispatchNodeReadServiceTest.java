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
 * P1-B 回归测试：Node-first Read Model（Node 存在路径）
 * 说明：JdbcTemplate 在 JDK25 下无法被 Mockito mock（P0 已证实），
 * 故反射构造注入 null jdbcTemplate（本测试路径不触碰 legacy SQL）；
 * legacy fallback 的 SQL 路径用真实 MySQL 事务回滚验证（见 DispatchNodeReadFallbackDbTest/实施报告）。
 */
@ExtendWith(MockitoExtension.class)
class DispatchNodeReadServiceTest {

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
                                        String assigneeName, String status, LocalDateTime assignedAt) {
        ProductionDispatchNode n = new ProductionDispatchNode();
        n.setNodeId(id);
        n.setDispatchId(dispatchId);
        n.setParentNodeId(parentId);
        n.setAssigneeType("USER");
        n.setAssigneeId(assigneeId);
        n.setAssigneeName(assigneeName);
        n.setNodeStatus(status);
        n.setAssignedAt(assignedAt);
        n.setOrgId(7L);
        n.setOrgName("印刷一组");
        return n;
    }

    @Test
    void currentNodeReturnsActiveNodeWhenNodesExist() {
        ProductionDispatchNode active = node(2L, 3L, 1L, 104L, "印刷一组工人",
                DispatchNodeStatusEnum.ACTIVE.getCode(), LocalDateTime.of(2026, 8, 19, 12, 0));
        when(nodeMapper.selectOne(any())).thenReturn(active);

        DispatchNodeVO vo = service.getCurrentActiveNode(3L);
        assertNotNull(vo);
        assertEquals(2L, vo.getNodeId());
        assertEquals(104L, vo.getAssigneeId());
        assertEquals("印刷一组工人", vo.getAssigneeName());
        assertEquals("印刷一组", vo.getOrgName());
        assertEquals(DispatchNodeStatusEnum.ACTIVE.getCode(), vo.getNodeStatus());
        assertEquals("NODE", vo.getSource());
    }

    @Test
    void currentNodeReturnsNullWhenNoActiveNode() {
        when(nodeMapper.selectOne(any())).thenReturn(null);
        when(nodeMapper.selectCount(any())).thenReturn(1L); // hasNodes check

        DispatchNodeVO vo = service.getCurrentActiveNode(3L);
        assertNull(vo);
    }

    @Test
    void responsibilityChainSortedByAssignedAtWhenNodesExist() {
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 19, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 19, 10, 0);
        LocalDateTime t3 = LocalDateTime.of(2026, 8, 19, 11, 0);
        // 故意乱序返回，验证稳定排序（Responsibility History：按 assignedAt 时间先后）
        ProductionDispatchNode n3 = node(3L, 3L, 2L, 104L, "工人", "ACTIVE", t3);
        ProductionDispatchNode n1 = node(1L, 3L, null, 96L, "车间主任", "DELEGATED", t1);
        ProductionDispatchNode n2 = node(2L, 3L, 1L, 98L, "组长", "DELEGATED", t2);
        when(nodeMapper.selectList(any())).thenReturn(List.of(n3, n1, n2));

        List<DispatchNodeVO> chain = service.getResponsibilityChain(3L);
        assertEquals(3, chain.size());
        assertEquals(96L, chain.get(0).getAssigneeId()); // 车间主任
        assertEquals(98L, chain.get(1).getAssigneeId()); // 组长
        assertEquals(104L, chain.get(2).getAssigneeId()); // 工人（ACTIVE 末位）
        assertEquals("NODE", chain.get(0).getSource());
    }

    @Test
    void isCurrentAssigneeTrueWhenActiveNodeMatchesUser() {
        when(nodeMapper.selectCount(any())).thenReturn(1L);

        assertTrue(service.isCurrentAssignee(3L, 104L));
    }

    @Test
    void isCurrentAssigneeFalseWhenNotActiveAssignee() {
        when(nodeMapper.selectCount(any())).thenReturn(0L);

        assertFalse(service.isCurrentAssignee(3L, 96L));
    }

    @Test
    void hasUserParticipatedTrueWhenNodeExists() {
        when(nodeMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.hasUserParticipated(96L));
    }
}
