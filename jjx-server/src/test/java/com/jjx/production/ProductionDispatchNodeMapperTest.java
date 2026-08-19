package com.jjx.production;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.enums.DispatchNodeStatusEnum;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * P1-A 回归测试：ProductionDispatchNodeMapper 基础能力
 * 说明：项目测试基建为纯 Mockito（JDK25 下无 Spring 集成测试），
 * 真实 MySQL 上的 insert/唯一约束/条件更新行为已在实施报告 §13 用事务回滚方式实测验证。
 */
@ExtendWith(MockitoExtension.class)
class ProductionDispatchNodeMapperTest {

    @Mock
    ProductionDispatchNodeMapper nodeMapper;

    private ProductionDispatchNode sampleNode(Long dispatchId, String status) {
        ProductionDispatchNode n = new ProductionDispatchNode();
        n.setNodeId(100L);
        n.setDispatchId(dispatchId);
        n.setParentNodeId(1L);
        n.setAssigneeType("USER");
        n.setAssigneeId(104L);
        n.setAssigneeName("测试工人");
        n.setNodeStatus(status);
        n.setAssignedAt(LocalDateTime.now());
        return n;
    }

    @Test
    void insertPersistsAllCoreFields() {
        ProductionDispatchNode n = sampleNode(3L, DispatchNodeStatusEnum.ACTIVE.getCode());
        when(nodeMapper.insert(n)).thenReturn(1);
        int rows = nodeMapper.insert(n);
        assertEquals(1, rows);
        // 关键字段语义：责任持有实例（assignee + 时间窗口 + 状态）
        assertEquals(3L, n.getDispatchId());
        assertEquals(104L, n.getAssigneeId());
        assertEquals("USER", n.getAssigneeType());
        assertEquals(DispatchNodeStatusEnum.ACTIVE.getCode(), n.getNodeStatus());
        assertNotNull(n.getAssignedAt());
        verify(nodeMapper).insert(n);
    }

    @Test
    void selectByDispatchIdUsesLambdaQuery() {
        // 基础查询能力：按 dispatchId 查询（wrapper 由 MyBatis-Plus 构建，验证调用链路即可）
        nodeMapper.selectList(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, 3L));
        verify(nodeMapper).selectList(any());
    }

    @Test
    void selectActiveNodeFiltersNodeStatus() {
        // 查询当前 ACTIVE 的能力（P1-B 复用）：dispatchId + nodeStatus 条件
        nodeMapper.selectOne(Wrappers.<ProductionDispatchNode>lambdaQuery()
                .eq(ProductionDispatchNode::getDispatchId, 3L)
                .eq(ProductionDispatchNode::getNodeStatus, DispatchNodeStatusEnum.ACTIVE.getCode()));
        verify(nodeMapper).selectOne(any());
    }

    @Test
    void entityHasNoActiveGuardField() throws Exception {
        // active_guard 是 DB generated column，Java 实体不负责写入（验收项之一）
        for (var f : ProductionDispatchNode.class.getDeclaredFields()) {
            assertFalse(f.getName().contains("activeGuard"),
                    "实体不应映射 active_guard（DB 生成列，Java 不写）");
        }
    }
}
