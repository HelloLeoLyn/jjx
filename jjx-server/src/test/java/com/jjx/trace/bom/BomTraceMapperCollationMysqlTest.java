package com.jjx.trace.bom;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "JJX_TEST_BOM_TRACE_DATABASE", matches = "1")
class BomTraceMapperCollationMysqlTest {
    @ParameterizedTest
    @ValueSource(strings = {"utf8mb4_0900_ai_ci", "utf8mb4_unicode_ci"})
    void anchorAndVersionsWorkWithEitherConnectionCollation(String collation) throws Exception {
        var env = System.getenv();
        var source = new DriverManagerDataSource(
            env.getOrDefault("JJX_TEST_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),
            env.getOrDefault("JJX_TEST_MYSQL_USER", "jjx_ro"),
            env.getOrDefault("JJX_TEST_MYSQL_PASSWORD", env.getOrDefault("JJX_RO_PASS", "jjx_ro_2026")));
        try (var connection = source.getConnection()) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement()) {
                statement.execute("SET NAMES utf8mb4 COLLATE " + collation);
            }
            var dataSource = new SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(dataSource);
            assertEquals(collation, jdbc.queryForObject("SELECT @@collation_connection", String.class));
            var ids = jdbc.queryForList("SELECT bom_id FROM engineering_bom ORDER BY bom_id LIMIT 5", Long.class);
            assertFalse(ids.isEmpty(), "Real database must contain a BOM for this regression test");

            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(BomTraceMapper.class);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            try (var session = factory.getObject().openSession()) {
                var mapper = session.getMapper(BomTraceMapper.class);
                for (var id : ids) {
                    var anchor = mapper.anchor(id);
                    assertNotNull(anchor);
                    assertEquals(id, anchor.getBomId());
                    var versions = mapper.versions(anchor.getOutputType(), anchor.getOutputId());
                    assertTrue(versions.stream().anyMatch(version ->
                        id.equals(version.getBomId())
                            && java.util.Objects.equals(anchor.getCreatorName(), version.getCreatorName())));
                }
            }
        }
    }
}
