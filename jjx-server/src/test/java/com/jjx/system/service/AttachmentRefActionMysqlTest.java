package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** 显式启用的真实库只读验证；不建表、不插数据、不登录运行中的服务。 */
@EnabledIfEnvironmentVariable(named="JJX_ACTION_MYSQL_URL",matches=".+")
class AttachmentRefActionMysqlTest {
    @Test void currentQuotationEvidenceMatchesBusinessFlowsWithoutWrites() throws Exception {
        try (var connection=DriverManager.getConnection(System.getenv("JJX_ACTION_MYSQL_URL"),
                System.getenv("JJX_ACTION_MYSQL_USER"),System.getenv("JJX_ACTION_MYSQL_PASSWORD"));
             var auth=mockStatic(StpUtil.class)) {
            connection.setReadOnly(true);
            var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            var service=new AttachmentRefActionService(jdbc,new AttachmentSourcePolicy(jdbc),new ObjectMapper());
            List<Long> ids=jdbc.queryForList("SELECT id FROM sys_attachment_ref WHERE source_type='quotation' AND source_no='QT261021001' AND deleted=0 ORDER BY id",Long.class);
            assertEquals(3,ids.size(),"只读样本已变化，需重新核对，不能用旧预期证明通过");
            var first=service.detail(ids.get(0));
            var second=service.detail(ids.get(1));
            var third=service.detail(ids.get(2));
            assertTrue(first.businessActions().isEmpty());
            assertEquals(List.of("审核通过"),second.businessActions().stream().map(a->a.actionName()).toList());
            assertEquals(List.of("客户确认报价"),third.businessActions().stream().map(a->a.actionName()).toList());
            assertNotNull(first.joinedAt());
            assertNotNull(second.joinedAt());
            assertNotNull(third.joinedAt());
            assertNull(third.businessActions().getFirst().operatorName());
            System.out.println("MySQL READ ONLY QT261021001: refs="+ids+"; actions=[] / 审核通过 / 客户确认报价; joinedAt verified; no business writes");
        }
    }
}
