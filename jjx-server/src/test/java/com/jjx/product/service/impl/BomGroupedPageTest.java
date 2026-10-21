package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.jjx.product.domain.converter.EngineeringBomConverterImpl;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IEngineeringBomService.BomListQuery;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 执行真实 MyBatis 分页 SQL；H2 反例与可选真实库只读核对。 */
class BomGroupedPageTest {
    private SqlSession session(String driver, String url, String user, String password) {
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("bom-page", new JdbcTransactionFactory(),
                new UnpooledDataSource(driver, url, user, password)));
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(plugin);
        configuration.addMapper(EngineeringBomMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
    }

    private EngineeringBomServiceImpl service(SqlSession session) {
        return new EngineeringBomServiceImpl(session.getMapper(EngineeringBomMapper.class), null,
                mock(ProductMapper.class), new EngineeringBomConverterImpl(), null, null, null, null, null, null);
    }

    @Test
    void groupsBeforePagingAndKeepsCurrentOrNewestAndFullTimeline() throws Exception {
        try (var session = session("org.h2.Driver", "jdbc:h2:mem:bomGrouped;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            var sql = session.getConnection().createStatement();
            sql.execute("CREATE TABLE engineering_bom (bom_id BIGINT PRIMARY KEY, bom_code VARCHAR(100), bom_name VARCHAR(100), bom_version VARCHAR(20), version VARCHAR(20), bom_type VARCHAR(30), source_sample_id BIGINT, parent_bom_id BIGINT, product_id BIGINT, approve_status INT, approve_remark VARCHAR(100), is_current BOOLEAN, create_by VARCHAR(30), create_time TIMESTAMP, update_by VARCHAR(30), update_time TIMESTAMP, remark VARCHAR(100), effective_date TIMESTAMP, expiry_date TIMESTAMP)");
            sql.execute("CREATE TABLE product (product_id BIGINT, product_code VARCHAR(100), product_name VARCHAR(100))");
            sql.execute("INSERT INTO product VALUES (1,'P1','产品甲')");
            for (int i = 1; i <= 5; i++) {
                sql.execute("INSERT INTO engineering_bom (bom_id,bom_code,bom_name,bom_version,is_current,approve_status,create_time,update_time,product_id) VALUES (" + i + ",'A','测试BOM','V" + i + "'," + (i == 2) + "," + ProductEnums.BomStatus.APPROVED.getValue() + ",'2026-01-01','2026-01-01',1)");
            }
            sql.execute("INSERT INTO engineering_bom (bom_id,bom_code,bom_version,is_current,create_time,update_time) VALUES (6,'B','V1',false,'2026-01-01','2026-12-01'),(7,'B','V2',false,'2026-02-01','2026-02-01')");
            var service = service(session);
            var query = new BomListQuery();
            query.setPageSize(1);
            var first = service.getBomListPage(query);
            assertEquals(2, first.getTotal());
            assertEquals(1, first.getRecords().size());
            assertEquals(2L, first.getRecords().get(0).getBomId());
            assertEquals(5, first.getRecords().get(0).getVersionCount());
            query.setPageNum(2);
            var second = service.getBomListPage(query);
            assertEquals(7L, second.getRecords().get(0).getBomId());
            assertFalse(second.getRecords().get(0).getIsCurrent());
            assertEquals(2, second.getRecords().get(0).getVersionCount());
            assertEquals(5, service.getBomVersions("A").size());
            assertEquals("V5", service.getBomVersions("A").get(0).getBomVersion());
            assertThrows(RuntimeException.class, () -> service.getBomVersions(" "));
            query.setPageNum(1);
            query.setPageSize(10);
            query.setShowAllVersions(true);
            assertEquals(7, service.getBomListPage(query).getTotal());
            assertEquals(7, service.getBomListPage(query).getRecords().size());
            query.setProductCode("P1");
            query.setProductName("产品甲");
            query.setBomName("测试");
            query.setApproveStatus(String.valueOf(ProductEnums.BomStatus.APPROVED.getValue()));
            assertEquals(5, service.getBomListPage(query).getTotal());
            query.setShowAllVersions(false);
            assertEquals(1, service.getBomListPage(query).getTotal());
            query.setProductCode("missing");
            assertEquals(0, service.getBomListPage(query).getTotal());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JJX_BOM_READONLY_DB", matches = "true")
    void currentDatabaseHasTwoBomGroupsAndSevenVersions() throws Exception {
        try (var session = session("com.mysql.cj.jdbc.Driver", "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&serverTimezone=Asia/Shanghai", "jjx_ro", System.getenv("JJX_BOM_DB_PASSWORD"))) {
            session.getConnection().setReadOnly(true);
            var service = service(session);
            var query = new BomListQuery();
            var grouped = service.getBomListPage(query);
            assertEquals(2, grouped.getTotal());
            assertEquals(2, grouped.getRecords().size());
            for (var row : grouped.getRecords()) {
                int expected = row.getBomCode().equals("GF405MEOO-BOM") ? 4 : 3;
                assertEquals(expected, row.getVersionCount());
                assertEquals(expected, service.getBomVersions(row.getBomCode()).size());
                System.out.printf("BOM实际查询: %s %s versions=%d current=%s%n", row.getBomCode(), row.getBomVersion(), row.getVersionCount(), row.getIsCurrent());
            }
            query.setShowAllVersions(true);
            var flat = service.getBomListPage(query);
            assertEquals(7, flat.getTotal());
            assertEquals(7, flat.getRecords().size());
            System.out.printf("BOM实际查询: grouped=%d flat=%d%n", grouped.getTotal(), flat.getTotal());
        }
    }
}
