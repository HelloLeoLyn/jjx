package com.jjx.production;

import com.jjx.production.domain.dto.DispatchAssignV1DTO;
import com.jjx.production.domain.dto.DispatchDelegateDTO;
import com.jjx.production.domain.dto.DispatchReassignDTO;
import com.jjx.production.domain.dto.DispatchReturnDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-D 回归测试：正式动作 DTO 结构
 * - ASSIGN V1 不含 level/transferFrom/chainComplete
 * - DELEGATE 不含 level/transferFrom，含 targetUserId
 * - REASSIGN 不含 level/transferFrom，含 targetUserId
 * - RETURN 不含 targetUserId/level，含 reason
 */
class DispatchV1DtoStructureTest {

    @Test
    void assignV1DtoHasNoLevelOrTransferFrom() throws Exception {
        for (Field f : DispatchAssignV1DTO.class.getDeclaredFields()) {
            assertFalse(f.getName().contains("level"), "ASSIGN V1 不得含 level");
            assertFalse(f.getName().contains("transferFrom"), "ASSIGN V1 不得含 transferFrom");
            assertFalse(f.getName().contains("chainComplete"), "ASSIGN V1 不得含 chainComplete");
        }
        // 必需字段存在
        assertTrue(hasField(DispatchAssignV1DTO.class, "executionId"));
        assertTrue(hasField(DispatchAssignV1DTO.class, "orderId"));
        assertTrue(hasField(DispatchAssignV1DTO.class, "targetUserId"));
        assertTrue(hasField(DispatchAssignV1DTO.class, "equipmentId"));
    }

    @Test
    void delegateDtoHasTargetUserIdNoLevel() throws Exception {
        for (Field f : DispatchDelegateDTO.class.getDeclaredFields()) {
            assertFalse(f.getName().contains("level"));
            assertFalse(f.getName().contains("transferFrom"));
        }
        assertTrue(hasField(DispatchDelegateDTO.class, "targetUserId"));
        assertTrue(hasField(DispatchDelegateDTO.class, "remark"));
    }

    @Test
    void reassignDtoHasTargetUserIdNoLevelNoTransferFrom() throws Exception {
        for (Field f : DispatchReassignDTO.class.getDeclaredFields()) {
            assertFalse(f.getName().contains("level"));
            assertFalse(f.getName().contains("transferFrom"));
        }
        assertTrue(hasField(DispatchReassignDTO.class, "targetUserId"));
        assertTrue(hasField(DispatchReassignDTO.class, "reason"));
    }

    @Test
    void returnDtoHasNoTargetUserId() throws Exception {
        for (Field f : DispatchReturnDTO.class.getDeclaredFields()) {
            assertFalse(f.getName().contains("targetUserId"), "RETURN 不允许选择目标人");
            assertFalse(f.getName().contains("level"));
        }
        assertTrue(hasField(DispatchReturnDTO.class, "reason"));
    }

    private boolean hasField(Class<?> clazz, String name) {
        try {
            clazz.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
