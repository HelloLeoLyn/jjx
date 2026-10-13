package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AttachmentSourcePolicyTest {
    @Test
    void taskAttachmentsUseBoardLoginBoundaryAndRequireExistingTask() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM sys_task"), eq(String.class), eq(42L)))
                .thenReturn(List.of("dev-20261014-001"));
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            assertEquals("dev-20261014-001", policy.check("task", 42L, false));
            assertEquals("dev-20261014-001", policy.check("task", 42L, true));
            assertTrue(policy.canRead("task", 42L));
            assertThrows(BusinessException.class, () -> policy.check("task", 43L, false));
            assertThrows(BusinessException.class, () -> policy.check("unknown", 42L, false));
        }
    }
}
