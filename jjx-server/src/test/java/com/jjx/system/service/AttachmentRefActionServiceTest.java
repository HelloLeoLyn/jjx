package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.controller.AttachmentRefActionController;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentRefActionServiceTest {
    JdbcTemplate jdbc;
    AttachmentSourcePolicy policy;
    AttachmentRefActionService service;
    final LocalDateTime time = LocalDateTime.of(2026, 10, 21, 10, 0);

    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        policy = mock(AttachmentSourcePolicy.class);
        when(policy.canonical(anyString())).thenAnswer(i -> i.getArgument(0));
        service = new AttachmentRefActionService(jdbc, policy, new ObjectMapper());
        jdbc.execute("CREATE TABLE sys_attachment(id BIGINT PRIMARY KEY,create_by VARCHAR,create_time TIMESTAMP,deleted INT)");
        jdbc.execute("CREATE TABLE sys_attachment_ref(id BIGINT PRIMARY KEY,attachment_id BIGINT,source_type VARCHAR,source_id BIGINT,ref_state VARCHAR,create_by VARCHAR,create_time TIMESTAMP,deleted INT)");
        jdbc.execute("CREATE TABLE sys_oper_log(id BIGINT PRIMARY KEY,biz_type VARCHAR,biz_id VARCHAR,status INT,action VARCHAR,create_time TIMESTAMP,real_name VARCHAR,username VARCHAR,oper_url VARCHAR,detail VARCHAR,oper_param VARCHAR)");
        jdbc.execute("CREATE TABLE sales_quotation_flow(flow_id BIGINT PRIMARY KEY,quotation_id BIGINT,action_name VARCHAR,operator_name VARCHAR,create_time TIMESTAMP,attachment_ref_ids VARCHAR)");
        jdbc.execute("CREATE TABLE review_flow(flow_id BIGINT PRIMARY KEY,biz_type VARCHAR,biz_id BIGINT,action_name VARCHAR,operator_name VARCHAR,create_time TIMESTAMP,attachment_ref_ids VARCHAR)");
        jdbc.execute("CREATE TABLE sales_sample_round(round_id BIGINT PRIMARY KEY,order_id BIGINT,round_no INT,create_time TIMESTAMP,attachment_ref_ids VARCHAR)");
        jdbc.update("INSERT INTO sys_attachment VALUES(100,'uploader',?,0)", time.minusHours(1));
        ref(1, "quotation", 10, "active");
    }

    void ref(long id, String source, long sourceId, String state) {
        jdbc.update("INSERT INTO sys_attachment_ref VALUES(?,100,?,?,?,'123',?,0)", id, source, sourceId, state, time);
    }

    void log(long id, String source, long sourceId, String action, String url, String detail, String params) {
        jdbc.update("INSERT INTO sys_oper_log VALUES(?,?,?,1,?,?,'张三','admin',?,?,?)",
                id, source, String.valueOf(sourceId), action, time.plusSeconds(id), url, detail, params);
    }

    @Test void businessFlowsWinOverDuplicateLogsAndKeepMultipleActions() {
        jdbc.update("INSERT INTO sales_quotation_flow VALUES(1,10,'审核通过','系统',?,'[1]'),(2,10,'客户确认报价','李四',?,'[1]')",
                time.plusSeconds(2), time.plusSeconds(4));
        log(1,"quotation",10,"附件新增","attachment.ref.附件新增","{\"attachments\":[{\"refId\":1,\"id\":100}]}",null);
        log(2,"quotation",10,"审核报价单","/review",null,"{\"attachmentRefIds\":[1]}");
        try (var auth = mockStatic(StpUtil.class)) {
            var result = service.detail(1L);
            assertEquals(2, result.businessActions().size());
            assertEquals("审核通过", result.businessActions().getFirst().actionName());
            assertNull(result.businessActions().getFirst().operatorName());
            assertEquals("李四", result.businessActions().getLast().operatorName());
            assertEquals(time.plusSeconds(1), result.joinedAt());
            assertEquals("张三", result.joinedBy());
            assertEquals("uploader", result.uploadedBy());
            assertEquals(time.minusHours(1), result.uploadedAt());
            assertEquals(1, result.referenceEvents().size());
        }
    }

    @Test void logFallbackRequiresExactRefAndSourceAndSuccessfulResult() {
        log(1,"quotation",10,"客户确认报价","/confirm",null,"{\"attachmentRefIds\":[1]}");
        log(2,"quotation",20,"别单动作","/confirm",null,"{\"attachmentRefIds\":[1]}");
        log(3,"quotation",10,"不同引用","/confirm",null,"{\"attachmentRefIds\":[11]}");
        log(4,"quotation",10,"无效参数","/confirm",null,"bad-json refId=1");
        log(5,"quotation",10,"字符串ID不猜","/confirm",null,"{\"attachmentRefIds\":[\"1\"]}");
        log(6,"quotation",10,"失败动作","/confirm",null,"{\"attachmentRefIds\":[1]}");
        jdbc.update("UPDATE sys_oper_log SET status=0 WHERE id=6");
        try (var auth = mockStatic(StpUtil.class)) {
            var result = service.detail(1L);
            assertEquals(1, result.businessActions().size());
            assertEquals("客户确认报价", result.businessActions().getFirst().actionName());
        }
    }

    @Test void fileReuseDoesNotExposeAnotherReferenceOrOldRecords() {
        ref(2,"quotation",20,"active");
        jdbc.update("INSERT INTO sales_quotation_flow VALUES(1,20,'别单审核','甲',?,'[2]'),(2,10,'旧记录','甲',?,'[1]')",
                time.plusSeconds(1), time.minusDays(1));
        log(1,"quotation",10,"错误文件","attachment.ref.附件新增","{\"attachments\":[{\"refId\":1,\"id\":999}]}",null);
        log(2,"quotation",10,"相邻动作不猜","/confirm",null,"{}");
        try (var auth = mockStatic(StpUtil.class)) {
            var result = service.detail(1L);
            assertTrue(result.businessActions().isEmpty());
            assertTrue(result.referenceEvents().isEmpty());
            assertNull(result.joinedAt());
            verify(policy).check("quotation",10L,false);
        }
    }

    @Test void sourcePermissionIsCheckedBeforeAnyActionRead() {
        doThrow(new BusinessException("无来源权限")).when(policy).check("quotation",10L,false);
        jdbc.execute("DROP TABLE sys_oper_log");
        jdbc.execute("DROP TABLE sales_quotation_flow");
        try (var auth = mockStatic(StpUtil.class)) {
            assertEquals("无来源权限", assertThrows(BusinessException.class,()->service.detail(1L)).getMessage());
        }
    }

    @Test void draftIsOwnerOnlyAndNeverHasJoinedTime() {
        ref(2,"quotation",0,"draft");
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("other");
            assertThrows(BusinessException.class,()->service.detail(2L));
            auth.when(StpUtil::getLoginIdAsString).thenReturn("123");
            var result=service.detail(2L);
            assertNull(result.joinedAt());
            assertTrue(result.businessActions().isEmpty());
            verifyNoInteractions(policy);
        }
    }

    @Test void formalJoinUsesNewLogRatherThanOldDraftCreateTime() {
        log(30,"quotation",10,"附件新增","attachment.ref.附件新增","{\"attachments\":[{\"refId\":1}]}",null);
        try (var auth = mockStatic(StpUtil.class)) {
            assertEquals(time.plusSeconds(30),service.detail(1L).joinedAt());
        }
    }

    @Test void reviewAndSampleRoundUseOnlyThisReferenceWithoutTrace() {
        ref(2,"sample",20,"active");
        jdbc.update("INSERT INTO review_flow VALUES(1,'sample',20,'审核通过','王五',?,'[2]'),(2,'sales_order',20,'他域审核','甲',?,'[2]')",
                time.plusSeconds(1),time.plusSeconds(2));
        jdbc.update("INSERT INTO sales_sample_round VALUES(1,20,1,?,'[2]'),(2,20,2,?,'[1]')",time.plusSeconds(3),time.plusSeconds(4));
        try (var auth = mockStatic(StpUtil.class)) {
            var result=service.detail(2L);
            assertEquals(2,result.businessActions().size());
            assertEquals("审核通过",result.businessActions().getFirst().actionName());
            assertEquals("第 1 轮打样资料",result.businessActions().getLast().actionName());
        }
    }

    @Test void missingAndDeletedReferencesAndFilesAreRejected() {
        try (var auth=mockStatic(StpUtil.class)) {
            assertThrows(BusinessException.class,()->service.detail(0L));
            assertThrows(BusinessException.class,()->service.detail(9L));
            jdbc.update("UPDATE sys_attachment_ref SET deleted=1 WHERE id=1");
            assertThrows(BusinessException.class,()->service.detail(1L));
            jdbc.update("UPDATE sys_attachment_ref SET deleted=0 WHERE id=1");
            jdbc.update("UPDATE sys_attachment SET deleted=1 WHERE id=100");
            assertThrows(BusinessException.class,()->service.detail(1L));
            verifyNoInteractions(policy);
        }
    }

    @Test void controllerExposesOnlySummaryFromCurrentReference() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new AttachmentRefActionController(service)).build();
        try (var auth=mockStatic(StpUtil.class)) {
            mvc.perform(get("/system/attachment/refs/1/actions"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.refId").value(1))
                    .andExpect(jsonPath("$.data.uploadedBy").value("uploader"))
                    .andExpect(jsonPath("$.data.businessActions").isEmpty())
                    .andExpect(jsonPath("$.data.traceId").doesNotExist())
                    .andExpect(jsonPath("$.data.operParam").doesNotExist());
        }
    }
}
