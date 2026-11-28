package com.jjx.engineering.printing.search;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 独立随机MySQL夹具库执行真实MyBatis分页，覆盖JSON_TABLE来源查询；不写业务库。 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="JJX_PRINT_STYLE_MYSQL",matches="1")
class PrintPlateSearchTest {
    JdbcTemplate db;
    JdbcTemplate admin;
    String schema;
    @org.junit.jupiter.api.AfterEach void cleanup() {
        if(admin!=null && schema!=null && schema.matches("jjx_print008_test_[0-9a-f]{32}")) admin.execute("DROP DATABASE "+schema);
    }
    PrintSearchService service;
    @BeforeEach void setup() throws Exception {
        schema="jjx_print008_test_"+UUID.randomUUID().toString().replace("-","");
        admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true","root",System.getenv("DB_PASS")));
        admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        var ds=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root",System.getenv("DB_PASS"));
        db=new JdbcTemplate(ds) {
            @Override public void execute(String sql) { super.execute(sql.replaceAll("VARCHAR(?=[,)])","VARCHAR(200)")); }
        };
        db.execute("CREATE TABLE print_artwork(artwork_id BIGINT,artwork_no VARCHAR,name VARCHAR,purpose VARCHAR)");
        db.execute("CREATE TABLE print_artwork_version(version_id BIGINT,artwork_id BIGINT,version_no VARCHAR,status VARCHAR)");
        db.execute("CREATE TABLE print_film_copy(film_id BIGINT,version_id BIGINT,film_no VARCHAR,status VARCHAR)");
        db.execute("CREATE TABLE engineering_screen_frame(frame_id BIGINT,frame_no VARCHAR,status VARCHAR,del_flag VARCHAR,v2_registered INT)");
        db.execute("CREATE TABLE print_frame_stretch(stretch_id BIGINT,frame_id BIGINT,open_frame_id BIGINT,mesh INT,tension_n_cm DECIMAL(9,2),qualified BOOLEAN,end_time TIMESTAMP,stretch_time TIMESTAMP,material VARCHAR)");
        db.execute("CREATE TABLE engineering_screen_plate(plate_id BIGINT,plate_no VARCHAR,status VARCHAR,position VARCHAR,version_id BIGINT,frame_id BIGINT,stretch_id BIGINT,film_copy_id BIGINT,v2_registered INT,washed_time TIMESTAMP,occupied_frame_id BIGINT)");
        db.execute("CREATE TABLE engineering_resource_product_rel(resource_type VARCHAR,resource_id BIGINT,is_active INT,product_id BIGINT)");
        db.execute("CREATE TABLE product(product_id BIGINT,product_code VARCHAR,product_name VARCHAR)");
        db.update("INSERT INTO print_artwork VALUES(1,'ART-01','面板','印刷')");
        db.update("INSERT INTO print_artwork_version VALUES(2,1,'V1','RELEASED')");
        db.update("INSERT INTO print_film_copy VALUES(3,2,'FILM-A','AVAILABLE'),(4,2,'FILM-B','AVAILABLE')");
        db.update("INSERT INTO engineering_screen_frame VALUES(5,'FRAME-01','READY','0',1),(6,'FRAME-02','READY','0',1)");
        db.update("INSERT INTO print_frame_stretch VALUES(7,5,5,305,22.5,TRUE,NULL,CURRENT_TIMESTAMP,'聚酯'),(8,6,6,305,22.5,TRUE,NULL,CURRENT_TIMESTAMP,'聚酯')");
        db.update("INSERT INTO engineering_screen_plate VALUES(9,'PLATE-A','RELEASED','面板',2,5,7,3,1,NULL,5),(10,'PLATE-B','RELEASED','面板',2,6,8,4,1,NULL,6)");
        db.update("INSERT INTO product VALUES(11,'PRODUCT-A','面板产品'),(12,'PRODUCT-B','面板产品')");
        db.update("INSERT INTO engineering_resource_product_rel VALUES('ARTWORK',1,1,11),('ARTWORK',1,1,12)");
        db.execute("ALTER TABLE engineering_screen_plate ADD sources_json JSON NULL, ADD layout TEXT NULL");
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.addMapper(PrintSearchRepository.class);
        var pagination = new MybatisPlusInterceptor(); pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        factory.setPlugins(pagination); factory.setGlobalConfig(new GlobalConfig().setBanner(false));
        service = new PrintSearchService(new SqlSessionTemplate(factory.getObject()).getMapper(PrintSearchRepository.class));
    }
    PrintSearchOption option(String q) { var o = new PrintSearchOption(); o.setQ(q); o.setScene("PLATE"); return o; }

    @Test void secondaryFilmAndProductSearchKeepPhysicalPlateUniqueAndCheckAllVersions() {
        db.update("INSERT INTO print_artwork VALUES(30,'SECOND','第二菲林','印刷')");
        db.update("INSERT INTO print_artwork_version VALUES(31,30,'V2','RELEASED')");
        db.update("INSERT INTO print_film_copy VALUES(32,31,'SECOND-FILM','AVAILABLE')");
        db.update("INSERT INTO product VALUES(33,'SECOND-PRODUCT','第二产品')");
        db.update("INSERT INTO engineering_resource_product_rel VALUES('ARTWORK',30,1,33)");
        db.update("UPDATE engineering_screen_plate SET sources_json=? WHERE plate_id=9",
            "[{\"versionId\":2,\"filmId\":3,\"styles\":[{\"styleNo\":\"A\"}]},{\"versionId\":31,\"filmId\":32,\"styles\":[{\"styleNo\":\"B\"}]}]");
        var result=service.search(option("SECOND-FILM FRAME-01"));
        assertEquals(1,result.getTotal());assertEquals(9L,result.getRecords().getFirst().getPlateId());
        assertTrue(result.getRecords().getFirst().getSourcesJson().contains("32"));
        var byProduct=option("SECOND-PRODUCT");byProduct.setProductId(33L);
        assertEquals(1,service.search(byProduct).getTotal());
        db.update("UPDATE print_artwork_version SET status='DISABLED' WHERE version_id=31");
        assertFalse(service.plate(9).getAvailability());
    }
    @Test void searchesExactFilmCopyAndMultipleTermsWithoutCrossProduct() {
        var result = service.search(option("FILM-A FRAME-01"));
        assertEquals(1,result.getTotal());
        var row = result.getRecords().getFirst();
        assertEquals(9L,row.getPlateId()); assertEquals(3L,row.getFilmId()); assertEquals(7L,row.getStretchId());
        assertEquals("22.50", row.getTensionNCm().toString()); assertTrue(row.getAvailability());
        assertEquals(0,service.search(option("FILM-A PLATE-B")).getTotal());
        assertTrue(row.getDisplayName().startsWith("PLATE-A"));
    }
    @Test void productLinksDoNotDuplicatePlatesAndPaginationCountsPhysicalScreens() {
        var o = option("面板产品"); o.setPageSize(1);
        var first = service.search(o); assertEquals(2,first.getTotal()); assertEquals(1,first.getRecords().size());
        o.setPageNum(2); var second = service.search(o);
        assertNotEquals(first.getRecords().getFirst().getPlateId(),second.getRecords().getFirst().getPlateId());
        o.setProductId(99L); assertEquals(0,service.search(o).getTotal());
    }
    @Test void detailReturnsOnlyRequestedPlateAndUnavailableStatesAreNotSelectable() {
        assertEquals("FILM-B",service.plate(10).getFilmNo());
        db.update("UPDATE engineering_screen_plate SET status='WASHED',washed_time=CURRENT_TIMESTAMP,occupied_frame_id=NULL WHERE plate_id=9");
        assertFalse(service.plate(9).getAvailability());
        assertFalse(service.search(option("PLATE-A")).getRecords().getFirst().getAvailability());
        db.update("UPDATE print_frame_stretch SET qualified=FALSE WHERE stretch_id=8");
        assertFalse(service.plate(10).getAvailability());
        assertThrows(BusinessException.class, () -> service.plate(999));
    }
    @Test void unavailableVersionFrameAndEndedStretchAreAnnotated() {
        db.update("UPDATE print_artwork_version SET status='DRAFT'"); assertFalse(service.plate(9).getAvailability());
        db.update("UPDATE print_artwork_version SET status='RELEASED'");
        db.update("UPDATE engineering_screen_frame SET status='MAINTENANCE' WHERE frame_id=5"); assertFalse(service.plate(9).getAvailability());
        db.update("UPDATE engineering_screen_frame SET status='READY'");
        db.update("UPDATE print_frame_stretch SET end_time=CURRENT_TIMESTAMP,open_frame_id=NULL WHERE stretch_id=7"); assertFalse(service.plate(9).getAvailability());
    }
}
