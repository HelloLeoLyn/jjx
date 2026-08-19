package com.jjx.production;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.enums.DispatchNodeStatusEnum;
import com.jjx.production.mapper.ProductionDispatchLogMapper;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.service.DispatchNodeReadService;
import com.jjx.production.service.impl.DispatchActionServiceImpl;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P1-C 回归测试：RETURN 核心规则（评审定稿模型）
 * - RETURN 关闭当前 ACTIVE + 创建新的上级责任实例
 * - 禁止重新激活旧 parent（N2 保持 DELEGATED）
 * - N4.parentNodeId = N2.parentNodeId（不是 N3）
 * - 历史节点 assignee 不可覆盖
 * 说明：jdbcTemplate 注入 null（RETURN 路径不触碰 legacy SQL）；真实事务/并发/投影用 DB 验证（报告 §19）。
 */
class DispatchReturnActionTest {

    private DispatchActionServiceImpl service;
    private ProductionDispatchMapper dispatchMapper;
    private ProductionDispatchNodeMapper nodeMapper;
    private ProductionDispatchLogMapper logMapper;
    private ProductionOperationExecutionMapper executionMapper;
    private SysUserMapper sysUserMapper;
    private SysDeptMapper sysDeptMapper;
    private DispatchNodeReadService nodeReadService;

    @BeforeEach
    void setUp() throws Exception {
        dispatchMapper = mock(ProductionDispatchMapper.class);
        nodeMapper = mock(ProductionDispatchNodeMapper.class);
        logMapper = mock(ProductionDispatchLogMapper.class);
        executionMapper = mock(ProductionOperationExecutionMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        sysDeptMapper = mock(SysDeptMapper.class);
        nodeReadService = mock(DispatchNodeReadService.class);
        var ctor = DispatchActionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (DispatchActionServiceImpl) ctor.newInstance(dispatchMapper, nodeMapper, logMapper,
                executionMapper, sysUserMapper, sysDeptMapper, nodeReadService, null);
    }

    private ProductionDispatch dispatch() {
        ProductionDispatch d = new ProductionDispatch();
        d.setDispatchId(10L);
        d.setOrderId(2L);
        d.setStatus(2);
        return d;
    }

    private ProductionDispatchNode node(Long id, Long dispatchId, Long parentId, Long assigneeId,
                                        String assigneeName, String status) {
        ProductionDispatchNode n = new ProductionDispatchNode();
        n.setNodeId(id);
        n.setDispatchId(dispatchId);
        n.setParentNodeId(parentId);
        n.setAssigneeType("USER");
        n.setAssigneeId(assigneeId);
        n.setAssigneeName(assigneeName);
        n.setNodeStatus(status);
        return n;
    }

    @Test
    void returnClosesActiveAndCreatesNewUpperInstanceNotReactivatingParent() {
        // N1 车间主任(96) → N2 班组长(98,DELEGATED) → N3 张三(104,ACTIVE)
        ProductionDispatchNode n1 = node(1L, 10L, null, 96L, "车间主任", "DELEGATED");
        ProductionDispatchNode n2 = node(2L, 10L, 1L, 98L, "班组长", "DELEGATED");
        ProductionDispatchNode n3 = node(3L, 10L, 2L, 104L, "张三", "ACTIVE");

        when(dispatchMapper.selectById(10L)).thenReturn(dispatch());
        when(dispatchMapper.selectOne(any())).thenReturn(dispatch()); // FOR UPDATE 锁
        when(nodeReadService.hasNodes(10L)).thenReturn(true);          // 已有 Node，不 adopt
        when(nodeMapper.selectOne(any())).thenReturn(n3);              // 当前 ACTIVE
        when(nodeMapper.selectById(2L)).thenReturn(n2);                // parent = N2
        when(nodeMapper.update(any(), any())).thenReturn(1);           // 条件关闭成功
        when(nodeMapper.insert(any(ProductionDispatchNode.class))).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(List.of(n1, n2, n3)); // projection
        when(dispatchMapper.updateById(any(ProductionDispatch.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(104L); // 张三本人退回
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            service.returnTask(10L, "干不了", "张三", 104L);
        }

        // N3 被关闭为 RETURNED（条件更新 node_status 校验）
        var cap = ArgumentCaptor.forClass(ProductionDispatchNode.class);
        verify(nodeMapper).update(cap.capture(), any());
        assertEquals(DispatchNodeStatusEnum.RETURNED.getCode(), cap.getValue().getNodeStatus());
        assertNotNull(cap.getValue().getClosedAt());

        // 新节点：assignee = N2.assignee(班组长98)，parent = N2.parentNodeId(1L) —— 不是 N3.nodeId(3L)
        var insertCap = ArgumentCaptor.forClass(ProductionDispatchNode.class);
        verify(nodeMapper).insert(insertCap.capture());
        ProductionDispatchNode n4 = insertCap.getValue();
        assertEquals(98L, n4.getAssigneeId());          // N4 是班组长第二次持责
        assertEquals("班组长", n4.getAssigneeName());
        assertEquals(1L, n4.getParentNodeId());        // 同层：parent=N2.parent=N1
        assertNotEquals(3L, n4.getParentNodeId());     // 禁止 N4.parent = N3
        assertEquals(DispatchNodeStatusEnum.ACTIVE.getCode(), n4.getNodeStatus());
        assertNotNull(n4.getAssignedAt());

        // 关键断言：N2 从未被 UPDATE 重新激活（update 只调用了一次，且目标是 N3）
        verify(nodeMapper, times(1)).update(any(), any());
        // N2 的历史 assignee/状态不被修改
        assertEquals(98L, n2.getAssigneeId());
        assertEquals(DispatchNodeStatusEnum.DELEGATED.getCode(), n2.getNodeStatus());
    }

    @Test
    void returnRejectsWhenRootNode() {
        // N1 是 root（parent=null），不能再退
        ProductionDispatchNode n1 = node(1L, 10L, null, 96L, "车间主任", "ACTIVE");
        when(dispatchMapper.selectById(10L)).thenReturn(dispatch());
        when(dispatchMapper.selectOne(any())).thenReturn(dispatch());
        when(nodeReadService.hasNodes(10L)).thenReturn(true);
        when(nodeMapper.selectOne(any())).thenReturn(n1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(96L);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.returnTask(10L, "退", "车间主任", 96L));
            assertTrue(ex.getMessage().contains("最上级"));
        }
    }

    @Test
    void returnDeniedForNonActiveAssignee() {
        ProductionDispatchNode n3 = node(3L, 10L, 2L, 104L, "张三", "ACTIVE");
        when(dispatchMapper.selectById(10L)).thenReturn(dispatch());
        when(dispatchMapper.selectOne(any())).thenReturn(dispatch());
        when(nodeReadService.hasNodes(10L)).thenReturn(true);
        when(nodeMapper.selectOne(any())).thenReturn(n3);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(99L); // 非张三
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            assertThrows(BusinessException.class,
                    () -> service.returnTask(10L, "退", "路人", 99L));
        }
    }
}
