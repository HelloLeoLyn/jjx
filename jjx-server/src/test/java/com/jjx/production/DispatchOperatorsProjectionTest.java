package com.jjx.production;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P1-C 回归测试：legacy operators projection 算法
 * 规则：operators 表达"当前有效责任路径"（从 ACTIVE 沿 parent 向上追溯反转），
 * 不是完整责任历史。RETURN/REASSIGN 后的历史节点不进入 projection。
 * 反射调用私有 syncOperatorsProjection 验证。
 */
class DispatchOperatorsProjectionTest {

    private ProductionDispatchMapper dispatchMapper;
    private ProductionDispatchNodeMapper nodeMapper;
    private DispatchActionServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        dispatchMapper = mock(ProductionDispatchMapper.class);
        nodeMapper = mock(ProductionDispatchNodeMapper.class);
        var ctor = DispatchActionServiceImpl.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        service = (DispatchActionServiceImpl) ctor.newInstance(dispatchMapper, nodeMapper,
                mock(ProductionDispatchLogMapper.class), mock(ProductionOperationExecutionMapper.class),
                mock(SysUserMapper.class), mock(SysDeptMapper.class),
                mock(DispatchNodeReadService.class), null);
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
        return n;
    }

    private String invokeProjection(List<ProductionDispatchNode> nodes) throws Exception {
        when(nodeMapper.selectList(any())).thenReturn(nodes);
        Method m = DispatchActionServiceImpl.class.getDeclaredMethod("syncOperatorsProjection", Long.class);
        m.setAccessible(true);
        m.invoke(service, 30L);
        // 捕获 projection 写回
        var cap = org.mockito.ArgumentCaptor.forClass(ProductionDispatch.class);
        verify(dispatchMapper).updateById(cap.capture());
        return cap.getValue().getOperators();
    }

    @Test
    void returnHistoryProducesCurrentPathOnly() throws Exception {
        // 场景：N1(96) → N2(98) → N3(104) RETURNED，N4(98 重新持责) ACTIVE parent=N1
        // 完整历史 4 个节点，但 projection 应只输出 N1 → N4（当前有效路径）
        List<ProductionDispatchNode> nodes = List.of(
                node(1L, 30L, null, 96L, "车间主任", "DELEGATED"),
                node(2L, 30L, 1L, 98L, "班组长", "DELEGATED"),
                node(3L, 30L, 2L, 104L, "工人", "RETURNED"),
                node(4L, 30L, 1L, 98L, "班组长", "ACTIVE"));
        String ops = invokeProjection(nodes);
        // 输出 [{"userId":96,..."level":1},{"userId":98,..."level":2}]
        assertTrue(ops.contains("\"userId\":96"));
        assertTrue(ops.contains("\"userId\":98"));
        assertFalse(ops.contains("\"userId\":104"), "RETURNED 历史节点不应进入当前 projection");
        // level 按路径顺序 1,2（Legacy Projection only）
        assertTrue(ops.contains("\"level\":1"));
        assertTrue(ops.contains("\"level\":2"));
        assertFalse(ops.contains("\"level\":3"));
    }

    @Test
    void reassignHistoryProducesCurrentPathOnly() throws Exception {
        // N1(96) → N2(98) REASSIGNED，N3(100 同级) ACTIVE parent=N1
        List<ProductionDispatchNode> nodes = List.of(
                node(1L, 30L, null, 96L, "车间主任", "DELEGATED"),
                node(2L, 30L, 1L, 98L, "组长A", "REASSIGNED"),
                node(3L, 30L, 1L, 100L, "组长B", "ACTIVE"));
        String ops = invokeProjection(nodes);
        assertTrue(ops.contains("\"userId\":96"));
        assertTrue(ops.contains("\"userId\":100"));
        assertFalse(ops.contains("\"userId\":98"), "REASSIGNED 历史节点不应进入当前 projection");
        assertEquals(2, ops.split("\"level\"").length - 1);
    }

    @Test
    void simpleChainFullPath() throws Exception {
        // N1(96) → N2(98) → N3(104) ACTIVE：正常链全部输出
        List<ProductionDispatchNode> nodes = List.of(
                node(1L, 30L, null, 96L, "车间主任", "DELEGATED"),
                node(2L, 30L, 1L, 98L, "班组长", "DELEGATED"),
                node(3L, 30L, 2L, 104L, "工人", "ACTIVE"));
        String ops = invokeProjection(nodes);
        assertTrue(ops.contains("\"userId\":96"));
        assertTrue(ops.contains("\"userId\":98"));
        assertTrue(ops.contains("\"userId\":104"));
        assertEquals(3, ops.split("\"level\"").length - 1);
    }

    @Test
    void noActiveKeepsProjectionUnchanged() throws Exception {
        // 无 ACTIVE（如整单退回后）：不更新 projection（保留最后链供旧页面展示）
        List<ProductionDispatchNode> nodes = List.of(
                node(1L, 30L, null, 96L, "车间主任", "DELEGATED"),
                node(2L, 30L, 1L, 98L, "班组长", "RETURNED"));
        when(nodeMapper.selectList(any())).thenReturn(nodes);
        Method m = DispatchActionServiceImpl.class.getDeclaredMethod("syncOperatorsProjection", Long.class);
        m.setAccessible(true);
        m.invoke(service, 30L);
        // 无 ACTIVE → 不写 operators（不生成空数组）
        verify(dispatchMapper, never()).updateById(any(ProductionDispatch.class));
    }
}
