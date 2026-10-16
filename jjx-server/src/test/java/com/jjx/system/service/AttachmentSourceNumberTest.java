package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.controller.SysAttachmentRefController;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentSourceNumberTest {
    @Test
    void controllerQueriesByNumberAndReturnsFileColumnsWithoutChangingIdRoute() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:source_number;MODE=MySQL");
             MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(db, true));
            jdbc.execute("CREATE TABLE sales_inquiry(inquiry_id BIGINT, inquiry_no VARCHAR, deleted INT)");
            jdbc.execute("INSERT INTO sales_inquiry VALUES (7,'INQ007',0),(8,'DELETED',1)");
            jdbc.execute("CREATE TABLE sys_attachment(id BIGINT,file_name VARCHAR,file_size BIGINT,file_type VARCHAR,category VARCHAR,version VARCHAR,drawing_no VARCHAR,drawing_name VARCHAR,file_role VARCHAR,is_current INT,is_controlled INT,released INT)");
            jdbc.execute("INSERT INTO sys_attachment(id,file_name,file_size,file_type) VALUES (40,'drawing.pdf',1234,'application/pdf')");
            var mapper = mock(SysAttachmentRefMapper.class);
            when(mapper.selectList(any())).thenAnswer(inv -> {
                var row = new SysAttachmentRef();
                row.setId(90L); row.setAttachmentId(40L); row.setSourceType("inquiry");
                row.setSourceId(7L); row.setSourceNo("INQ007"); row.setCreateBy("test-uploader");
                row.setCreateTime(LocalDateTime.of(2026, 10, 14, 10, 0));
                return new ArrayList<>(List.of(row));
            });
            var policy = new AttachmentSourcePolicy(jdbc);
            var service = new AttachmentRefService(null, mapper, policy, null, null, mock(AttachmentDisplayNameService.class), jdbc);
            var mvc = MockMvcBuilders.standaloneSetup(new SysAttachmentRefController(service, null, null, null)).build();
            for (String key : List.of("sourceNo", "sourceId")) {
                mvc.perform(get("/system/attachment/refs").param("sourceType", "inquiry")
                    .param(key, key.equals("sourceNo") ? "INQ007" : "7"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].sourceType").value("inquiry"))
                    .andExpect(jsonPath("$.data[0].sourceNo").value("INQ007"))
                    .andExpect(jsonPath("$.data[0].fileName").value("drawing.pdf"))
                    .andExpect(jsonPath("$.data[0].fileSize").value(1234))
                    .andExpect(jsonPath("$.data[0].createBy").value("test-uploader"))
                    .andExpect(jsonPath("$.data[0].createTime").exists());
            }
            for (String number : List.of("NO_SUCH_NO", "DELETED", "' OR 1=1 --")) {
                mvc.perform(get("/system/attachment/refs").param("sourceType", "inquiry").param("sourceNo", number))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
            }
            verify(mapper, times(2)).selectList(any());
        }
    }

    @Test
    void permissionIsCheckedAfterResolutionEvenWhenNumberIsMissing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var events = new ArrayList<String>();
        when(jdbc.queryForList(anyString(), eq(Long.class), anyString())).thenAnswer(inv -> {
            events.add("resolve"); return List.of();
        });
        var policy = new AttachmentSourcePolicy(jdbc);
        var mapper = mock(SysAttachmentRefMapper.class);
        var service = new AttachmentRefService(null, mapper, policy, null, null, mock(AttachmentDisplayNameService.class), jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.checkPermission("sales:inquiry:view")).thenAnswer(inv -> {
                events.add("permission"); throw new BusinessException("无权限");
            });
            assertThrows(BusinessException.class, () -> service.list("inquiry", null, "NO_SUCH_NO"));
            assertEquals(List.of("resolve", "permission"), events);
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void sourceScopeRequiresExactlyOneLocator() {
        var policy = mock(AttachmentSourcePolicy.class);
        var service = new AttachmentRefService(null, null, policy, null, null, mock(AttachmentDisplayNameService.class), null);
        assertThrows(BusinessException.class, () -> service.list("inquiry", null, null));
        assertThrows(BusinessException.class, () -> service.list("inquiry", 7L, "INQ007"));
        verifyNoInteractions(policy);
    }

    @Test
    void orderAndSampleNumbersRespectExistingScopeAndAliases() throws Exception {
        try (var db = DriverManager.getConnection("jdbc:h2:mem:source_scopes;MODE=MySQL");
             MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(db, true));
            jdbc.execute("CREATE TABLE sales_order(order_id BIGINT,order_no VARCHAR,deleted INT)");
            jdbc.execute("CREATE TABLE sales_sample_order(order_id BIGINT,deleted INT)");
            jdbc.execute("INSERT INTO sales_order VALUES (1,'SO1',0),(2,'SP2',0),(3,'SP3',1)");
            jdbc.execute("INSERT INTO sales_sample_order VALUES (2,0),(3,0)");
            var policy = new AttachmentSourcePolicy(jdbc);
            assertEquals(1L, policy.resolveReadId("order", "SO1"));
            assertNull(policy.resolveReadId("sales_order", "SP2"));
            assertEquals(2L, policy.resolveReadId("sample_order", "SP2"));
            assertNull(policy.resolveReadId("sample", "SO1"));
            assertNull(policy.resolveReadId("sample", "SP3"));
            assertThrows(BusinessException.class, () -> policy.resolveReadId("unknown", "SO1"));
        }
    }
}
