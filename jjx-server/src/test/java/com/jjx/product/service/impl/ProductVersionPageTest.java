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
class ProductVersionPageTest {
    private SqlSession session(String driver, String url, String user, String password) {
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("bom-page", new JdbcTransactionFactory(),
                new UnpooledDataSource(driver, url, user, password)));
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(plugin);
        configuration.addMapper(EngineeringBomMapper.class);
        configuration.addMapper(com.jjx.product.mapper.EngineeringRoutingMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
    }

    private EngineeringBomServiceImpl service(SqlSession session) {
        return new EngineeringBomServiceImpl(session.getMapper(EngineeringBomMapper.class), null,
                mock(ProductMapper.class), new EngineeringBomConverterImpl(), null, null, null, null, null, null);
    }

    @Test
    void groupsObjectsBeforePagingAndKeepsFullSummaryAfterStatusFilter() throws Exception {
        try (var session=session("org.h2.Driver","jdbc:h2:mem:productPage;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","")) {
            var sql=session.getConnection().createStatement();
            sql.execute("CREATE TABLE engineering_bom (bom_id BIGINT PRIMARY KEY, bom_code VARCHAR(100), bom_name VARCHAR(100), bom_version VARCHAR(20), version VARCHAR(20), bom_type VARCHAR(30), source_sample_id BIGINT, parent_bom_id BIGINT, product_id BIGINT, output_type VARCHAR(20) DEFAULT 'PRODUCT', output_id BIGINT, approve_status INT, approve_remark VARCHAR(100), is_current BOOLEAN, create_by VARCHAR(30), create_time TIMESTAMP, update_by VARCHAR(30), update_time TIMESTAMP, remark VARCHAR(100), effective_date TIMESTAMP, expiry_date TIMESTAMP, approve_by VARCHAR(50), approve_time TIMESTAMP)");
            sql.execute("CREATE TABLE product (product_id BIGINT, product_code VARCHAR(100), product_name VARCHAR(100), spec_json VARCHAR(200))");

            sql.execute("CREATE TABLE inventory_material(material_id BIGINT,material_code VARCHAR(50),material_name VARCHAR(100),specification VARCHAR(100))");
            sql.execute("CREATE TABLE engineering_routing(routing_id BIGINT,routing_code VARCHAR(50),routing_name VARCHAR(100),routing_version VARCHAR(20),product_id BIGINT,output_type VARCHAR(20),output_id BIGINT,approve_status INT,is_current INT,create_time TIMESTAMP,update_time TIMESTAMP)");
            sql.execute("INSERT INTO product VALUES(1,'P1','产品甲','{}'),(2,'P2','产品乙','{}')");
            sql.execute("INSERT INTO inventory_material VALUES(1,'M1','物料甲','10mm')");
            sql.execute("INSERT INTO engineering_bom(bom_id,bom_code,bom_version,product_id,output_type,output_id,approve_status,is_current,effective_date,expiry_date) VALUES (1,'A','V9.9',1,'PRODUCT',1,3,true,'2020-01-01',null),(2,'A','V10.0',1,'PRODUCT',1,1,false,null,null),(3,'OTHER','V2.0',1,'PRODUCT',1,2,false,null,null),(4,'B','V1.0',2,'PRODUCT',2,3,true,'2020-01-01','2020-02-01'),(5,'M','V1.0',null,'MATERIAL',1,3,true,null,null)");
            sql.execute("INSERT INTO engineering_routing VALUES(1,'R1','路线','V9.9',1,'PRODUCT',1,3,1,null,null),(2,'R1','路线','V10.0',1,'PRODUCT',1,1,0,null,null),(3,'R2','路线','V1.0',2,'PRODUCT',2,1,1,null,null),(4,'RM','物料路线','V1.0',null,'MATERIAL',1,3,1,null,null)");
            var bom=service(session); var query=new BomListQuery(); query.setPageSize(1);
            var seen=new java.util.HashSet<String>();
            for(int page=1;page<=3;page++) {
                query.setPageNum(page); var result=bom.getProductPage(query);
                assertEquals(3,result.getTotal()); assertEquals(1,result.getRecords().size());
                var row=result.getRecords().get(0); assertTrue(seen.add(row.getOutputType()+row.getOutputId()));
            }
            query.setPageNum(1);query.setProductCode("P1");query.setApproveStatus("1");
            var row=bom.getProductPage(query).getRecords().get(0);
            assertEquals(3,row.getVersionCount()); assertEquals("V10.0",row.getLatestVersion());assertEquals("V9.9",row.getCurrentVersion());
            assertTrue(row.isHasDraft());assertTrue(row.isHasPending());
            assertEquals(3,bom.getObjectVersions("PRODUCT",1L).size());
            query.setApproveStatus(null);query.setProductCode("P2");assertNull(bom.getProductPage(query).getRecords().get(0).getCurrentId());
            query.setProductCode("missing");assertEquals(0,bom.getProductPage(query).getTotal());
            var route=new EngineeringRoutingServiceImpl(session.getMapper(com.jjx.product.mapper.EngineeringRoutingMapper.class),null,null,null,null,null);
            var rq=new com.jjx.product.domain.dto.EngineeringRoutingQueryDTO();rq.setPageSize(1);seen.clear();
            for(int page=1;page<=3;page++) {
                rq.setPageNum(page);var result=route.getProductPage(rq);assertEquals(3,result.getTotal());assertEquals(1,result.getRecords().size());
                var r=result.getRecords().get(0);assertTrue(seen.add(r.getOutputType()+r.getOutputId()));assertNull(r.getCurrentEffectiveDate());
            }
            rq.setPageNum(1);rq.setProductCode("P1");rq.setApproveStatus(1);var r=route.getProductPage(rq).getRecords().get(0);
            assertEquals(2,r.getVersionCount());assertEquals("V10.0",r.getLatestVersion());assertEquals("V9.9",r.getCurrentVersion());
            rq.setProductCode("P2");assertNull(route.getProductPage(rq).getRecords().get(0).getCurrentId());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named="JJX_PRODUCT_VERSIONS_RO",matches="true")
    void existingMysqlSchemaReadOnly() throws Exception {
        try(var session=session("com.mysql.cj.jdbc.Driver","jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&serverTimezone=Asia/Shanghai","jjx_ro",System.getenv("JJX_BOM_DB_PASSWORD"))) {
            session.getConnection().setReadOnly(true);
            var b=service(session).getProductPage(new BomListQuery());
            var route=new EngineeringRoutingServiceImpl(session.getMapper(com.jjx.product.mapper.EngineeringRoutingMapper.class),null,null,null,null,null);
            var r=route.getProductPage(new com.jjx.product.domain.dto.EngineeringRoutingQueryDTO());
            for(var row:b.getRecords()) assertEquals(row.getVersionCount(),service(session).getObjectVersions(row.getOutputType(),row.getOutputId()).size());
            System.out.printf("MySQL只读: BOM对象=%d, 工艺路线对象=%d%n",b.getTotal(),r.getTotal());
        }
    }
}
