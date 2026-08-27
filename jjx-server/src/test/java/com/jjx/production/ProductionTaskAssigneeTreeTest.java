package com.jjx.production;

import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.mapper.ProductionTaskMapper;
import com.jjx.production.service.impl.DefaultProductionTaskAssigneeResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionTaskAssigneeTreeTest {

    @Test
    void directorCanAssignLeaderOrAnyDescendantWhileTreeKeepsOrganizationHierarchy() {
        ProductionTaskMapper mapper = mock(ProductionTaskMapper.class);
        when(mapper.selectAssigneeTreeUsers(96L)).thenReturn(List.of(
                user(96L, "冲型车间主任", 9L, 5L, 96L),
                user(100L, "冲型一组组长", 10L, 9L, 100L),
                user(106L, "冲型一组工人", 10L, 9L, 100L),
                user(101L, "冲型二组组长", 11L, 9L, 101L),
                user(107L, "冲型二组工人", 11L, 9L, 101L)
        ));
        DefaultProductionTaskAssigneeResolver resolver =
                new DefaultProductionTaskAssigneeResolver(mapper);

        List<TaskCandidateVO> tree = resolver.listAssignableUsers(96L);

        assertEquals(1, tree.size());
        TaskCandidateVO director = tree.getFirst();
        assertEquals(96L, director.getUserId());
        assertFalse(director.getSelectable());
        assertEquals(List.of(100L, 101L),
                director.getChildren().stream().map(TaskCandidateVO::getUserId).toList());
        assertEquals(List.of(106L),
                director.getChildren().getFirst().getChildren().stream()
                        .map(TaskCandidateVO::getUserId).toList());
        assertEquals(List.of(107L),
                director.getChildren().get(1).getChildren().stream()
                        .map(TaskCandidateVO::getUserId).toList());

        assertFalse(resolver.isAssignableTo(96L, 96L), "不能分配给当前分配人自己");
        assertTrue(resolver.isAssignableTo(96L, 100L), "支持主任逐级派给一组组长");
        assertTrue(resolver.isAssignableTo(96L, 106L), "支持主任跨级直派一组工人");
        assertTrue(resolver.hasAssignableSubordinates(96L));
    }

    private static TaskCandidateVO user(long userId, String name, long deptId,
                                        long parentDeptId, long deptLeaderId) {
        TaskCandidateVO value = new TaskCandidateVO();
        value.setUserId(userId);
        value.setNickName(name);
        value.setDeptId(deptId);
        value.setParentDeptId(parentDeptId);
        value.setDeptLeaderId(deptLeaderId);
        return value;
    }
}
