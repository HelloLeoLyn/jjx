package com.jjx.production;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionDispatch;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.enums.DispatchNodeStatusEnum;
import com.jjx.production.mapper.ProductionDispatchLogMapper;
import com.jjx.production.mapper.ProductionDispatchMapper;
import com.jjx.production.mapper.ProductionDispatchNodeMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.migration.DispatchNodeBackfillParser;
import com.jjx.production.service.DispatchNodeReadService;
import com.jjx.production.service.impl.DispatchActionServiceImpl;
import com.jjx.system.domain.entity.SysUser;
import com.jjx.system.mapper.SysDeptMapper;
import com.jjx.system.mapper.SysUserMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P1-C 回归测试：on-write adoption（legacy-only dispatch 首次写动作自动接管）
 * - 无 Node + operators 有数据 → 事务内转 Node（LEGACY_ON_WRITE_ADOPTION）→ 再执行动作
 * - 有 Node → 不重复 adopt
 * - 非法 operators → 写动作失败，不产生半链
 * - ASSIGN 前置：execution 完成/取消拒绝；重复 ASSIGN 拒绝
 */
class DispatchLegacyAdoptionTest {

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

    private ProductionDispatch legacyDispatch() {
        ProductionDispatch d = new ProductionDispatch();
        d.setDispatchId(20L);
        d.setOrderId(2L);
        d.setStatus(2);
        d.setOperators("[{\"userId\":98,\"userName\":\"组长\",\"level\":1},"
                + "{\"userId\":104,\"userName\":\"工人\",\"level\":1}]");
        d.setAssignedBy(94L);
        d.setAssignedByName("prod_manager");
        d.setAssignTime(LocalDateTime.of(2026, 8, 19, 12, 0));
        return d;
    }

    private SysUser user(Long id, String name) {
        SysUser u = new SysUser();
        u.setUserId(id);
        u.setUserName(name);
        u.setNickName(name);
        u.setDeptId(7L);
        return u;
    }

    @Test
    void assignRejectsCompletedExecution() {
        ProductionOperationExecution exec = new ProductionOperationExecution();
        exec.setExecutionId(1L);
        exec.setExecutionStatus(4); // 已完成
        when(executionMapper.selectById(1L)).thenReturn(exec);
        when(sysUserMapper.selectById(96L)).thenReturn(user(96L, "车间主任"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.assign(1L, 2L, 96L, null, null, "admin", 1L));
            assertTrue(ex.getMessage().contains("不可派工"));
        }
    }

    @Test
    void assignRejectsWhenActiveNodeExists() {
        ProductionOperationExecution exec = new ProductionOperationExecution();
        exec.setExecutionId(1L);
        exec.setExecutionStatus(0);
        when(executionMapper.selectById(1L)).thenReturn(exec);
        when(sysUserMapper.selectById(96L)).thenReturn(user(96L, "车间主任"));
        // 已有 dispatch
        ProductionDispatch exist = new ProductionDispatch();
        exist.setDispatchId(20L);
        exist.setExecutionId(1L);
        when(dispatchMapper.selectOne(any())).thenReturn(exist); // 查已有 dispatch + FOR UPDATE
        when(nodeReadService.getCurrentActiveNode(20L)).thenReturn(
                new com.jjx.production.domain.vo.DispatchNodeVO()); // 已有 ACTIVE（非 null）

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.assign(1L, 2L, 96L, null, null, "admin", 1L));
            assertTrue(ex.getMessage().contains("已派工"));
        }
    }

    @Test
    void delegateAdoptsLegacyThenExecutes() {
        // legacy-only dispatch 20：无 Node + operators 有数据
        when(dispatchMapper.selectById(20L)).thenReturn(legacyDispatch());
        when(dispatchMapper.selectOne(any())).thenReturn(legacyDispatch()); // FOR UPDATE
        when(nodeReadService.hasNodes(20L)).thenReturn(false); // 无 Node → adopt

        // 目标用户 106（组长 98 的手下）
        when(sysUserMapper.selectById(106L)).thenReturn(user(106L, "工人2"));
        when(sysUserMapper.selectById(98L)).thenReturn(user(98L, "组长"));
        // adopt 时读取 legacy 中两个用户
        when(sysUserMapper.selectById(104L)).thenReturn(user(104L, "工人"));

        // 当前 ACTIVE = legacy 末位（adopt 后生成：98 DELEGATED, 104 ACTIVE）
        ProductionDispatchNode active104 = new ProductionDispatchNode();
        active104.setNodeId(2L);
        active104.setDispatchId(20L);
        active104.setParentNodeId(1L);
        active104.setAssigneeId(104L);
        active104.setAssigneeName("工人");
        active104.setNodeStatus(DispatchNodeStatusEnum.ACTIVE.getCode());
        when(nodeMapper.selectOne(any())).thenReturn(active104); // 第一次=当前ACTIVE查询

        when(nodeMapper.update(any(), any())).thenReturn(1);
        when(nodeMapper.insert(any(ProductionDispatchNode.class))).thenReturn(1);
        when(nodeMapper.selectList(any())).thenReturn(List.of(
                activeNode(1L, 98L, "组长", "DELEGATED"),
                activeNode(2L, 104L, "工人", "ACTIVE"),
                activeNode(3L, 106L, "工人2", "ACTIVE"))); // projection 用
        when(dispatchMapper.updateById(any(ProductionDispatch.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(104L); // 工人本人下派
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            // WP-C 收口后：下派 = 当前 ACTIVE 责任人本人 + delegate 权限
            mocked.when(() -> SecurityUtils.hasPermission("production:dispatch:delegate")).thenReturn(true);
            service.delegate(20L, 106L, null, "工人", 104L);
        }

        // adopt 产生 2 个 legacy Node（98 DELEGATED + 104 ACTIVE），再 delegate 产生新节点
        var insertCap = ArgumentCaptor.forClass(ProductionDispatchNode.class);
        verify(nodeMapper, atLeast(3)).insert(insertCap.capture());
        // 断言 adopt 节点 remark 标记
        List<ProductionDispatchNode> allInserts = insertCap.getAllValues();
        boolean adoptedMarked = allInserts.stream()
                .anyMatch(n -> DispatchActionServiceImpl.MARKER_ON_WRITE_ADOPTION.equals(n.getRemark()));
        assertTrue(adoptedMarked, "adoption 节点应标记 LEGACY_ON_WRITE_ADOPTION");
        // 断言新 delegate 节点 remark 不是 adoption 标记
        boolean newActionNotAdoption = allInserts.stream()
                .anyMatch(n -> n.getRemark() == null && n.getAssigneeId().equals(106L));
        assertTrue(newActionNotAdoption, "新动作节点不应带 adoption 标记");
    }

    @Test
    void invalidLegacyJsonBlocksAction() {
        ProductionDispatch bad = legacyDispatch();
        bad.setOperators("not-json{{");
        when(dispatchMapper.selectById(20L)).thenReturn(bad);
        when(dispatchMapper.selectOne(any())).thenReturn(bad);
        when(nodeReadService.hasNodes(20L)).thenReturn(false);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::getUserId).thenReturn(104L);
            mocked.when(() -> SecurityUtils.hasPermission("*:*:*")).thenReturn(false);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.delegate(20L, 106L, null, "工人", 104L));
            assertTrue(ex.getMessage().contains("遗留派工数据无法解析"));
        }
        // 不产生任何 Node 写入
        verify(nodeMapper, never()).insert(any(ProductionDispatchNode.class));
        verify(nodeMapper, never()).update(any(), any());
    }

    private ProductionDispatchNode activeNode(Long id, Long assigneeId, String name, String status) {
        ProductionDispatchNode n = new ProductionDispatchNode();
        n.setNodeId(id);
        n.setDispatchId(20L);
        n.setAssigneeId(assigneeId);
        n.setAssigneeName(name);
        n.setNodeStatus(status);
        return n;
    }
}
