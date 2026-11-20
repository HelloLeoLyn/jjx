package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.dto.query.*;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.*;
import com.jjx.inventory.service.impl.*;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryStockSearchTest {
    SqlSession session; InventoryStockQueryService service; JdbcTemplate jdbc;
    @BeforeEach void setup() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:stocksearch"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE inventory_item(inventory_item_id BIGINT PRIMARY KEY,item_type VARCHAR(20),source_id BIGINT,item_code VARCHAR(40),item_name VARCHAR(100),specification VARCHAR(100),unit VARCHAR(20),safe_stock DECIMAL(12,4),create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE inventory_material(material_id BIGINT PRIMARY KEY,material_code VARCHAR(40),material_name VARCHAR(100),specification VARCHAR(100),search_blob VARCHAR(500),supplier_name VARCHAR(100),category_id BIGINT,material_meta VARCHAR(40),material_form VARCHAR(40),color VARCHAR(40),brand VARCHAR(40))");
        jdbc.execute("CREATE TABLE inventory_material_category(category_id BIGINT,category_name VARCHAR(100),name_path VARCHAR(100))");
        jdbc.execute("CREATE TABLE inventory_stock(stock_id BIGINT PRIMARY KEY,inventory_item_id BIGINT,material_id BIGINT,material_code VARCHAR(40),material_name VARCHAR(100),last_update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE inventory_stock_item(item_id BIGINT,inventory_item_id BIGINT,warehouse_id BIGINT,location_id BIGINT,status INT,quantity DECIMAL(12,4),reserved_quantity DECIMAL(12,4),unit_cost DECIMAL(12,4),expiry_date DATE,last_outbound_time TIMESTAMP,last_inbound_time TIMESTAMP,create_time TIMESTAMP)");
        jdbc.update("INSERT INTO inventory_material_category VALUES (11,'离型纸','原材料/离型纸'),(12,'其它','其它')");
        for(int id=1;id<=4;id++) {
            jdbc.update("INSERT INTO inventory_item VALUES (?,'MATERIAL',?,?,?,'120g','M',5,CURRENT_TIMESTAMP)",id,id,"M"+id,"材料"+id);
            jdbc.update("INSERT INTO inventory_material VALUES (?,?,?,'120g',?,NULL,?,'PET','ROLL','白','品牌')",id,"M"+id,"材料"+id,id==4?"其它":"离型纸 120g",id==4?12:11);
            jdbc.update("INSERT INTO inventory_stock VALUES (?,?,?, ?,?,CURRENT_TIMESTAMP)",id,id,id,"M"+id,"材料"+id);
        }
        jdbc.update("INSERT INTO inventory_stock_item VALUES (1,1,1,10,1,10,2,3,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,1,2,20,1,20,0,NULL,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(3,2,1,10,1,2,0,4,NULL,NULL,TIMESTAMP '2020-01-01 00:00:00',TIMESTAMP '2020-01-01 00:00:00'),(4,3,1,11,1,0,0,NULL,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(5,4,2,20,1,8,0,2,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        connect(ds);
    }
    void connect(DataSource ds) throws Exception {
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        if("1".equals(System.getenv("JJX_DEBUG_SQL")))config.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class);
        config.addMapper(InventoryStockReadMapper.class); config.addMapper(InventoryMaterialMapper.class);
        var interceptor=new MybatisPlusInterceptor();interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        var bean=new MybatisSqlSessionFactoryBean();bean.setDataSource(ds);bean.setConfiguration(config);bean.setPlugins(interceptor);
        session=bean.getObject().openSession();
        var categories=mock(InventoryMaterialCategoryService.class);
        var parent=new InventoryMaterialCategory();parent.setCategoryId(10L);parent.setCategoryPath("RAW/PAPER");
        var leaf=new InventoryMaterialCategory();leaf.setCategoryId(11L);leaf.setCategoryPath("RAW/PAPER/RELEASE");
        when(categories.getById(10L)).thenReturn(parent);when(categories.list(org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(parent,leaf));
        var tags=mock(com.jjx.system.service.ISysTagService.class);
        when(tags.resolveBizIdsFilter(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(null);
        var materials=new InventoryMaterialServiceImpl(session.getMapper(InventoryMaterialMapper.class),categories,null,null,null,null,null,null,null,null,tags);
        service=new InventoryStockQueryService(session.getMapper(InventoryStockReadMapper.class),materials);
    }
    @AfterEach void close(){if(session!=null)session.close();}
    @Test void paginationIsStableAndKeywordUsesActualMaterialFilterBeforeCount() {
        var q=new StockQueryDTO();q.setKeyword("离型纸 120g");q.setPageSize(1);
        var first=service.page(q);q.setPageNum(2);var second=service.page(q);
        assertEquals(3,first.getTotal());assertEquals(3,second.getTotal());assertNotEquals(first.getRecords().get(0).getStockId(),second.getRecords().get(0).getStockId());
        assertEquals(3,service.summary(q).getMaterialCount().longValue());
        q.setKeyword("离型纸 不存在");assertEquals(0,service.page(q).getTotal());assertEquals(0,service.summary(q).getMaterialCount().longValue());
    }
    @Test void warehouseAndLocationConstrainSameBatchAndEverySummary() {
        var q=new StockQueryDTO();q.setWarehouseId(1L);q.setLocationId(10L);
        var page=service.page(q);var total=service.summary(q);
        assertEquals(2,page.getTotal());assertEquals(0,new BigDecimal("12").compareTo(total.getTotalQuantity()));
        assertEquals(0,new BigDecimal("38").compareTo(total.getTotalCost()));assertEquals(0,total.getMissingCostBatches());
        assertEquals(0,new BigDecimal("12").compareTo(total.getQuantityByUnit().get("M")));
        q.setLocationId(20L);assertEquals(0,service.page(q).getTotal());
    }
    @Test void lowStockZeroStockAndParentCategoryFilterBeforePagination() {
        var q=new StockQueryDTO();q.setCategoryId(10L);q.setLowStock(true);q.setPageSize(1);
        assertEquals(2,service.page(q).getTotal());assertEquals(2,service.summary(q).getLowStockMaterialCount());
        q.setLowStock(null);q.setOnlyHasStock(false);assertEquals(1,service.page(q).getTotal());assertEquals(0,service.summary(q).getTotalQuantity().signum());
        q.setOnlyHasStock(true);q.setObsolete(true);assertEquals(1,service.page(q).getTotal());
    }
    @Test void emptyUnknownCostAndInvalidInputAreExplicit() {
        var q=new StockQueryDTO();q.setWarehouseId(2L);assertEquals(1,service.summary(q).getMissingCostBatches());
        q.setWarehouseId(999L);assertEquals(0,service.summary(q).getMaterialCount().longValue());
        q.setMinQuantity(BigDecimal.TEN);q.setMaxQuantity(BigDecimal.ONE);assertThrows(com.jjx.common.exception.BusinessException.class,()->service.page(q));
    }
    @Test void realMysqlReadOnlyMapperAndMaterialSearch() throws Exception {
        if(!"1".equals(System.getenv("JJX_TEST_STOCK_QUERY_DATABASE")))return;
        session.close();session=null;
        var ds=new DriverManagerDataSource(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL","jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),"jjx_ro",System.getenv().getOrDefault("JJX_RO_PASS","jjx_ro_2026"));
        connect(ds);
        var q=new StockQueryDTO();q.setPageSize(5);var a=service.page(q);q.setPageNum(2);var b=service.page(q);
        assertTrue(a.getTotal()>5);assertEquals(a.getTotal(),b.getTotal());
        assertTrue(Collections.disjoint(a.getRecords().stream().map(v->v.getStockId()).toList(),b.getRecords().stream().map(v->v.getStockId()).toList()));
        assertEquals(a.getTotal(),service.summary(q).getMaterialCount().longValue());
        q.setKeyword("离型纸");assertTrue(service.page(q).getTotal()>0);assertEquals(service.page(q).getTotal(),service.summary(q).getMaterialCount().longValue());
        q.setAttributeFilters("[{\"key\":\"not_existing\",\"value\":\"none\"}]");assertEquals(0,service.page(q).getTotal());
        System.out.println("MYSQL_READ_ONLY_STOCK_QUERY_OK total="+a.getTotal());
    }
}
