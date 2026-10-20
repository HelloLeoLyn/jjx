package com.jjx.trace.service.impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.service.LogSaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** H2 执行真实 MyBatis 查询与分页；不写业务库，不代表实机操作点验。 */
class ProductEngineeringTraceTest {
    JdbcTemplate jdbc;
    SysOperLogMapper logs;
    TraceServiceImpl service;

    @BeforeEach
    void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE engineering_bom(bom_id BIGINT, product_id BIGINT)");
        jdbc.execute("CREATE TABLE engineering_routing(routing_id BIGINT, product_id BIGINT)");
        jdbc.execute("CREATE TABLE sys_oper_log(id BIGINT PRIMARY KEY, user_id BIGINT, username VARCHAR, real_name VARCHAR,"
                + "tenant_id BIGINT, module VARCHAR, business_type INT, oper_url VARCHAR, oper_ip VARCHAR, oper_param VARCHAR,"
                + "biz_type VARCHAR, biz_id VARCHAR, trace_id VARCHAR, biz_status VARCHAR, action VARCHAR, detail VARCHAR,"
                + "cost_time BIGINT, status INT, error_msg VARCHAR, user_agent VARCHAR, create_time TIMESTAMP)");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SysOperLogMapper.class);
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        factory.setPlugins(pagination);
        factory.setGlobalConfig(new GlobalConfig().setBanner(false));
        logs = new SqlSessionTemplate(factory.getObject()).getMapper(SysOperLogMapper.class);
        service = new TraceServiceImpl(logs, null, null, null);
        jdbc.update("INSERT INTO engineering_bom VALUES (11,8),(12,8),(13,9)");
        jdbc.update("INSERT INTO engineering_routing VALUES (11,9),(21,8),(22,8)");
    }

    void event(long id, String type, String bizId, String module, String trace, int seconds) {
        var row = new SysOperLog();
        row.setId(id); row.setBizType(type); row.setBizId(bizId); row.setModule(module);
        row.setTraceId(trace); row.setAction("操作" + id);
        row.setCreateTime(LocalDateTime.of(2026, 10, 20, 10, 0).plusSeconds(seconds));
        logs.insert(row);
    }

    @Test
    void productIncludesAllVersionsAndLegacyTraceWithoutCrossProductOrTypeLeakage() {
        event(3, "bom", "12", "产品BOM管理", "efcfe92bb764426f", 1);
        event(2, "product", "8", "产品作业规范", null, 1);
        event(1, "product", "8", "产品管理", null, 0);
        event(4, "routing", "21", "工艺路线管理", null, 2);
        event(5, "bom", "11", "产品BOM管理", null, 3);
        event(6, "routing", "22", "工艺路线管理", null, 4);
        event(7, "bom", "13", "产品BOM管理", null, 0);
        event(8, "routing", "11", "工艺路线管理", null, 0);
        event(9, "order", "11", "销售订单", null, 0);
        event(10, "product", "9", "产品管理", null, 0);
        event(11, "bom", "11junk", "产品BOM管理", null, 0);
        event(12, "product", "8junk", "产品管理", null, 0);
        var first = service.getEventsByBiz("product", 8L, 1, 2);
        assertEquals(6, first.getTotal());
        assertEquals(3, first.getTotalPages());
        assertEquals(List.of("oper-1", "oper-2"), first.getRecords().stream().map(e -> e.getEventId()).toList());
        var second = service.getEventsByBiz("product", 8L, 2, 2);
        assertEquals(List.of("oper-3", "oper-4"), second.getRecords().stream().map(e -> e.getEventId()).toList());
        assertEquals("产品BOM管理", second.getRecords().getFirst().getModule());
        assertEquals("efcfe92bb764426f", second.getRecords().getFirst().getTraceId());
        assertEquals("工艺路线管理", second.getRecords().getLast().getModule());
        assertEquals(2, service.getEventsByBiz("product", 8L, 3, 2).getRecords().size());
        assertTrue(service.getEventsByBiz("product", 8L, 4, 2).getRecords().isEmpty());
        assertEquals(0, service.getEventsByBiz("product", 99L, 1, 2).getTotal());
        assertEquals(1, service.getEventsByBiz("bom", 11L, 1, 20).getTotal());
    }

    @Test
    void traceExcludesOldEngineeringRowsBeforePagingAndPreservesOtherEventsAndStorage() {
        String trace = "efcfe92bb764426f";
        event(1, "bom", "11", "产品BOM管理", trace, 0);
        event(2, "routing", "21", "工艺路线管理", trace, 0);
        event(3, "order", "8", "销售订单", trace, 1);
        event(4, "sample", "8", "样品单", trace, 2);
        event(5, "attachment", "80", "附件", trace, 100);
        event(6, null, null, "历史事件", trace, 200);
        var before = jdbc.queryForList("SELECT * FROM sys_oper_log ORDER BY id");
        var first = service.getEvents(trace, 1, 2);
        assertEquals(4, first.getTotal());
        assertEquals(List.of("order", "sample"), first.getRecords().stream().map(e -> e.getBizType()).toList());
        var second = service.getEvents(trace, 2, 2);
        assertEquals("attachment", second.getRecords().getFirst().getBizType());
        assertNull(second.getRecords().getLast().getBizType());
        assertEquals(before, jdbc.queryForList("SELECT * FROM sys_oper_log ORDER BY id"));
    }

    @Test
    void engineeringDoesNotInheritFromSourceOrHistoricalLogsWhileInventoryStillDoes() {
        var history = mock(SysOperLogMapper.class);
        var orders = mock(com.jjx.sales.mapper.OrderMapper.class);
        var inbound = mock(com.jjx.inventory.mapper.InventoryInboundOrderMapper.class);
        var outbound = mock(com.jjx.inventory.mapper.InventoryOutboundOrderMapper.class);
        var bom = mock(com.jjx.product.mapper.EngineeringBomMapper.class);
        var routing = mock(com.jjx.product.mapper.EngineeringRoutingMapper.class);
        var save = new LogSaveService(history, null, null, bom, routing, inbound, outbound, orders,
                null, null, null);
        for (var type : List.of("bom", "routing")) {
            assertNull(save.findTraceIdBySource(type, "11"));
            assertNull(save.findTraceIdByBiz(type, "11"));
        }
        verifyNoInteractions(history, bom, routing, orders);
        var order = new com.jjx.sales.domain.entity.SalesOrder(); order.setTraceId("order-trace");
        when(orders.selectById(7L)).thenReturn(order);
        var in = new com.jjx.inventory.domain.InventoryInboundOrder(); in.setSourceId(7L);
        when(inbound.selectById(1L)).thenReturn(in);
        var out = new com.jjx.inventory.domain.InventoryOutboundOrder(); out.setSourceId(7L);
        when(outbound.selectById(2L)).thenReturn(out);
        assertEquals("order-trace", save.findTraceIdBySource("inbound", "1"));
        assertEquals("order-trace", save.findTraceIdBySource("outbound", "2"));
        assertEquals("order-trace", save.findTraceIdBySource("sales_order_review", "7"));
    }
}
