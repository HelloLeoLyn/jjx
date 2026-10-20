package com.jjx.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.product.domain.entity.ProductWorkSpecItem;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.enums.WorkSpecResourceType;
import com.jjx.product.mapper.*;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 MyBatis 批量 SQL + 隔离 H2；不代表生产库或页面运行态验证。 */
class ProductWorkSpecBatchStatusTest {
    JdbcTemplate db;
    SqlSession session;
    ProductWorkSpecMapper specs;
    ProductWorkSpecVersionMapper versions;
    ProductWorkSpecItemMapper items;
    ProductWorkSpecVersionService service;

    @BeforeEach
    void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false");
        session = newSession(ds);
        session.getConnection(); // 持有连接，让内存库在 JdbcTemplate 调用之间保持存在。
        db = new JdbcTemplate(ds);
        db.execute("CREATE TABLE product(product_id BIGINT PRIMARY KEY, work_spec_json VARCHAR(20000))");
        specs = spy(session.getMapper(ProductWorkSpecMapper.class));
        versions = mock(ProductWorkSpecVersionMapper.class);
        items = mock(ProductWorkSpecItemMapper.class);
        service = new ProductWorkSpecVersionService(specs, mock(ProductMapper.class), mock(SysAttachmentMapper.class),
                versions, items, mock(WorkSpecDocsetSnapshotService.class), mock(SysAttachmentRefMapper.class),
                new ObjectMapper());
    }

    private SqlSession newSession(JdbcDataSource ds) {
        var config = new Configuration(new Environment("batch-status", new JdbcTransactionFactory(), ds));
        config.addInterceptor(new H2CharCastCompatibility());
        config.addMapper(ProductWorkSpecMapper.class);
        return new SqlSessionFactoryBuilder().build(config).openSession(true);
    }

    /** MySQL 的无长度 CHAR 保留全文；H2 会截为 1 字符，仅在测试执行前适配该方言差异。 */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    public static class H2CharCastCompatibility implements Interceptor {
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            var handler = (StatementHandler) invocation.getTarget();
            var bound = handler.getBoundSql();
            assertTrue(bound.getSql().contains("CAST(work_spec_json AS CHAR)"));
            SystemMetaObject.forObject(bound).setValue("sql",
                    bound.getSql().replace("CAST(work_spec_json AS CHAR)", "CAST(work_spec_json AS VARCHAR)"));
            return invocation.proceed();
        }
    }

    @AfterEach
    void close() {
        session.close();
    }

    private void insert(long id, String raw) {
        db.update("INSERT INTO product VALUES (?, ?)", id, raw);
    }

    private void published(long productId, String snapshot) {
        var version = new ProductWorkSpecVersion();
        version.setId(productId + 100);
        version.setProductId(productId);
        version.setVersionNo("V1.0");
        version.setPublishedAt(LocalDateTime.of(2026, 10, 20, 10, 0));
        when(versions.selectLatestPublished(productId)).thenReturn(version);
        var item = new ProductWorkSpecItem();
        item.setResourceType(WorkSpecResourceType.SPEC_JSON.getCode());
        item.setSnapshotJson(snapshot);
        when(items.selectByVersionId(version.getId())).thenReturn(List.of(item));
    }

    @Test
    void twentyProductsUseOneJsonQueryIncludingPublishedDiffs() {
        List<Long> ids = LongStream.rangeClosed(1, 20).boxed().toList();
        for (long id : ids) {
            String raw = "{\"preparedById\":" + id + ",\"preparedByName\":\"工程" + id
                    + "\",\"finalizedVersionId\":" + (id + 100) + "}";
            insert(id, raw);
            published(id, raw);
        }
        // 已发布且修改过的产品，也必须复用同一次读取。
        db.update("UPDATE product SET work_spec_json=? WHERE product_id=20",
                "{\"preparedById\":20,\"preparedByName\":\"工程20\",\"finalizedVersionId\":120,\"dieLocation\":\"A\"}");

        var rows = service.statuses(ids);

        assertEquals(20, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            long id = i + 1L;
            var row = rows.get(i);
            assertEquals(id, row.get("productId"));
            assertEquals(id, row.get("preparedById"));
            assertEquals("工程" + id, row.get("preparedByName"));
            assertEquals(id + 100, row.get("finalizedVersionId"));
            assertEquals(id + 100, row.get("latestVersionId"));
            assertEquals("V1.0", row.get("latestVersionNo"));
            assertEquals(LocalDateTime.of(2026, 10, 20, 10, 0), row.get("latestPublishedAt"));
            assertEquals(id == 20, row.get("hasUnpublishedChanges"));
        }
        verify(specs, times(1)).readBatch(ids);
        verify(specs, never()).read(anyLong());
        verifyNoMoreInteractions(specs);
    }

    @Test
    void missingNullBlankMalformedAndWrongTypesKeepFieldsEmpty() {
        insert(1, null);
        insert(2, " ");
        insert(3, "{broken");
        insert(4, "{}");
        insert(5, "{\"preparedById\":\"7\",\"preparedByName\":8,\"finalizedVersionId\":\"9\"}");
        published(3, "{}");
        // 6 不存在；7 为合法 JSON null；8 为非对象 JSON。
        insert(7, "null");
        insert(8, "[]");
        var ids = LongStream.rangeClosed(1, 8).boxed().toList();

        var rows = assertDoesNotThrow(() -> service.statuses(ids));

        assertEquals(8, rows.size());
        for (var row : rows) {
            for (String key : List.of("preparedById", "preparedByName", "finalizedVersionId")) {
                assertTrue(row.containsKey(key));
                assertNull(row.get(key));
            }
            assertEquals(false, row.get("hasUnpublishedChanges"));
        }
        assertEquals(103L, rows.get(2).get("latestVersionId"));
        assertNull(rows.get(5).get("latestVersionId"));
        verify(specs).readBatch(ids);
        verify(specs, never()).read(anyLong());
    }

    @Test
    void emptyOrNullIdsDoNotQuery() {
        assertTrue(service.statuses(null).isEmpty());
        assertTrue(service.statuses(List.of()).isEmpty());
        assertTrue(service.statuses(Arrays.asList(null, null)).isEmpty());
        verifyNoInteractions(specs, versions, items);
    }

    @Test
    void nullIdsAreSkippedAndDuplicateOrderIsPreserved() {
        insert(1, "{\"preparedById\":7}");
        insert(2, "{\"preparedByName\":\"工程乙\"}");

        var rows = service.statuses(Arrays.asList(2L, null, 1L, 2L));

        assertEquals(List.of(2L, 1L, 2L), rows.stream().map(row -> row.get("productId")).toList());
        assertEquals("工程乙", rows.get(0).get("preparedByName"));
        assertNull(rows.get(0).get("preparedById"));
        assertEquals(7L, rows.get(1).get("preparedById"));
        assertNull(rows.get(1).get("preparedByName"));
        verify(specs).readBatch(List.of(2L, 1L));
        verify(specs, never()).read(anyLong());
    }
}
