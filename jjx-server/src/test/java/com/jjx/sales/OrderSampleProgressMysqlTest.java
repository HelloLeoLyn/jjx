package com.jjx.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.enums.SalesOrderTypeEnum;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 仅会话临时表遮蔽同名业务表；不写持久表，不需要服务或迁移。 */
@EnabledIfEnvironmentVariable(named="JJX_TEST_MYSQL", matches="1")
class OrderSampleProgressMysqlTest {
    @Test void actualBatchSqlMatchesActiveOptionsAndRestoresAfterClosing() throws Exception {
        try (var ds = new SingleConnectionDataSource(
                System.getenv().getOrDefault("JJX_TEST_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true"),
                System.getenv().getOrDefault("DB_USER", "root"), System.getenv().getOrDefault("DB_PASS", "123456"), true)) {
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TEMPORARY TABLE sales_order (order_id BIGINT, order_no VARCHAR(50), customer_id BIGINT, order_type INT, deleted INT DEFAULT 0)");
            jdbc.execute("CREATE TEMPORARY TABLE sales_order_product (id BIGINT, order_id BIGINT, product_id BIGINT, product_code VARCHAR(50))");
            jdbc.execute("CREATE TEMPORARY TABLE sales_sample_order (order_id BIGINT, source_type VARCHAR(50), source_no VARCHAR(50), product_id BIGINT, product_code VARCHAR(50), sample_status INT, deleted INT DEFAULT 0)");
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            config.addMapper(SalesSampleOrderMapper.class);
            // MySQL 临时表不能在一条 SQL 中打开两次；来源订单使用同构临时副本，仅替换表名。
            config.addInterceptor(new TempSourceAlias());
            var bean = new MybatisSqlSessionFactoryBean(); bean.setDataSource(ds); bean.setConfiguration(config);
            bean.setGlobalConfig(new GlobalConfig().setBanner(false));
            var mapper = new SqlSessionTemplate(bean.getObject()).getMapper(SalesSampleOrderMapper.class);
            jdbc.update("INSERT INTO sales_order VALUES (1,'SO1',7,?,0),(2,'SO2',7,?,0),(3,'SO3',7,?,0)",
                    SalesOrderTypeEnum.STANDARD.getCode(), SalesOrderTypeEnum.STANDARD.getCode(), SalesOrderTypeEnum.STANDARD.getCode());
            jdbc.update("INSERT INTO sales_order_product VALUES (11,1,101,' A '),(12,1,102,'B'),(13,1,103,'C'),(21,2,201,'D')");
            var statuses = List.of(SampleOrderStatusEnum.CREATED, SampleOrderStatusEnum.REQUEST,
                    SampleOrderStatusEnum.ENGINEERING, SampleOrderStatusEnum.SAMPLE_READY,
                    SampleOrderStatusEnum.SAMPLE_SENT, SampleOrderStatusEnum.REJECTED,
                SampleOrderStatusEnum.CONFIRMED, SampleOrderStatusEnum.TRANSFERRED).stream().map(SampleOrderStatusEnum::getValue).toList();
            jdbc.execute("CREATE TEMPORARY TABLE source_orders AS SELECT * FROM sales_order");
            var initial = mapper.selectOrderSampleProgress(List.of(1L,2L,3L), "SALES_ORDER", SalesOrderTypeEnum.SAMPLE.getCode(), statuses);
            assertEquals(3, initial.size());
            assertEquals(3, initial.get(0).getSampleableProductCount());
            assertEquals(0, initial.get(0).getActiveSampleCount());
            assertEquals(0, initial.get(2).getSampleableProductCount());
            // All six active statuses match product A via normalized code (different ID); no join multiplication.
            for (int n = 0; n < statuses.size(); n++) {
                long id = 100 + n;
                jdbc.update("INSERT INTO sales_order VALUES (?, ?,7,?,0)", id, "SP" + id, SalesOrderTypeEnum.SAMPLE.getCode());
                jdbc.update("INSERT INTO sales_sample_order VALUES (?,'SALES_ORDER','SO1',999,'a',?,0)", id, statuses.get(n));
            }
            var partial = mapper.selectOrderSampleProgress(List.of(1L,2L), "SALES_ORDER", SalesOrderTypeEnum.SAMPLE.getCode(), statuses);
            assertEquals(8, partial.get(0).getActiveSampleCount()); assertEquals(2, partial.get(0).getSampleableProductCount());
            assertEquals(0, partial.get(1).getActiveSampleCount());
            assertEquals(8, mapper.selectActiveOrderSamples("SALES_ORDER","SO1",7L,101L," A ",SalesOrderTypeEnum.SAMPLE.getCode(),statuses,false).size());
            // ID match with changed code. Wrong customer/source/type and soft deletions never count.
            for (int n = 0; n < 7; n++) {
                long id = 200 + n;
                jdbc.update("INSERT INTO sales_order VALUES (?, ?,?,?,?)", id, "SP" + id, n == 1 ? 8 : 7,
                        n == 3 ? SalesOrderTypeEnum.STANDARD.getCode() : SalesOrderTypeEnum.SAMPLE.getCode(), n == 4 ? 1 : 0);
                jdbc.update("INSERT INTO sales_sample_order VALUES (?,?,?,?,?,?,?)", id, n == 2 ? "QUOTATION" : "SALES_ORDER",
                        n == 6 ? "OTHER" : "SO1", 102, "changed", statuses.get(0), n == 5 ? 1 : 0);
            }
            var matched = mapper.selectOrderSampleProgress(List.of(1L), "SALES_ORDER", SalesOrderTypeEnum.SAMPLE.getCode(), statuses).get(0);
            assertEquals(9, matched.getActiveSampleCount()); assertEquals(1, matched.getSampleableProductCount());
            jdbc.update("INSERT INTO sales_order VALUES (300,'SP300',7,?,0)", SalesOrderTypeEnum.SAMPLE.getCode());
            jdbc.update("INSERT INTO sales_sample_order VALUES (300,'SALES_ORDER','SO1',103,'C',?,0)", statuses.get(0));
            var full = mapper.selectOrderSampleProgress(List.of(1L), "SALES_ORDER", SalesOrderTypeEnum.SAMPLE.getCode(), statuses).get(0);
            assertEquals(10, full.getActiveSampleCount()); assertEquals(0, full.getSampleableProductCount());
            for (var terminal : SampleOrderStatusEnum.values()) {
                if (statuses.contains(terminal.getValue())) continue;
                jdbc.update("UPDATE sales_sample_order SET sample_status=?", terminal.getValue());
                var closed = mapper.selectOrderSampleProgress(List.of(1L), "SALES_ORDER", SalesOrderTypeEnum.SAMPLE.getCode(), statuses).get(0);
                assertEquals(0, closed.getActiveSampleCount()); assertEquals(3, closed.getSampleableProductCount());
            }
        }
    }
    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(
            type=org.apache.ibatis.executor.statement.StatementHandler.class, method="prepare",
            args={java.sql.Connection.class, Integer.class}))
    static class TempSourceAlias implements org.apache.ibatis.plugin.Interceptor {
        @Override public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            var handler = (org.apache.ibatis.executor.statement.StatementHandler) invocation.getTarget();
            var sql = handler.getBoundSql();
            org.apache.ibatis.reflection.SystemMetaObject.forObject(sql).setValue("sql",
                    sql.getSql().replace("sales_order src", "source_orders src"));
            return invocation.proceed();
        }
    }

}
