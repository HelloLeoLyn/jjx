package com.jjx.production;

import com.jjx.production.enums.DispatchAssigneeTypeEnum;
import com.jjx.production.enums.DispatchNodeStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-A 回归测试：责任链节点状态/责任主体类型枚举映射
 */
class DispatchNodeStatusEnumTest {

    @Test
    void nodeStatusMappingIsCorrect() {
        assertEquals("ACTIVE", DispatchNodeStatusEnum.ACTIVE.getCode());
        assertEquals("当前责任持有中", DispatchNodeStatusEnum.ACTIVE.getLabel());
        assertEquals("DELEGATED", DispatchNodeStatusEnum.DELEGATED.getCode());
        assertEquals("已向下委派", DispatchNodeStatusEnum.DELEGATED.getLabel());
        assertEquals("REASSIGNED", DispatchNodeStatusEnum.REASSIGNED.getCode());
        assertEquals("已被同级改派", DispatchNodeStatusEnum.REASSIGNED.getLabel());
        assertEquals("RETURNED", DispatchNodeStatusEnum.RETURNED.getCode());
        assertEquals("已退回上级责任层", DispatchNodeStatusEnum.RETURNED.getLabel());
        assertEquals("COMPLETED", DispatchNodeStatusEnum.COMPLETED.getCode());
        assertEquals("责任链最终完成", DispatchNodeStatusEnum.COMPLETED.getLabel());
        assertEquals("CANCELLED", DispatchNodeStatusEnum.CANCELLED.getCode());
        assertEquals("任务取消", DispatchNodeStatusEnum.CANCELLED.getLabel());
    }

    @Test
    void nodeStatusFromCodeAndLabelOf() {
        assertEquals(DispatchNodeStatusEnum.ACTIVE, DispatchNodeStatusEnum.fromCode("ACTIVE"));
        assertEquals(DispatchNodeStatusEnum.RETURNED, DispatchNodeStatusEnum.fromCode("RETURNED"));
        assertNull(DispatchNodeStatusEnum.fromCode("UNKNOWN"));
        // 未知值 labelOf 原样返回（兼容历史，不抛异常）
        assertEquals("HISTORIC_STATUS", DispatchNodeStatusEnum.labelOf("HISTORIC_STATUS"));
        assertNull(DispatchNodeStatusEnum.labelOf(null));
    }

    @Test
    void nodeStatusIsSeparateFromDispatchAndExecution() {
        // NodeStatus 与 DispatchStatus / ExecutionStatus 完全分离：值域不同
        assertFalse(DispatchNodeStatusEnum.ACTIVE.getCode().equals("0"));
        assertFalse(DispatchNodeStatusEnum.COMPLETED.getCode().equals("4"));
        assertTrue(DispatchNodeStatusEnum.values().length == 6);
    }

    @Test
    void assigneeTypeOnlyUser() {
        assertEquals("USER", DispatchAssigneeTypeEnum.USER.getCode());
        assertEquals("用户", DispatchAssigneeTypeEnum.USER.getLabel());
        assertEquals(DispatchAssigneeTypeEnum.USER, DispatchAssigneeTypeEnum.fromCode("USER"));
        assertNull(DispatchAssigneeTypeEnum.fromCode("ORG"));
        assertNull(DispatchAssigneeTypeEnum.fromCode("TEAM"));
        assertNull(DispatchAssigneeTypeEnum.fromCode("WORKSHOP"));
        // 未知值兼容
        assertEquals("HISTORIC_TYPE", DispatchAssigneeTypeEnum.labelOf("HISTORIC_TYPE"));
    }
}
