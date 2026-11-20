package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.stocktake.*;
import com.jjx.inventory.service.impl.InventoryStockMutationServiceImpl;
import com.jjx.inventory.location.LocationHierarchyMapper;
import com.jjx.inventory.location.LocationHierarchyPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实库事务验证：仅通过正式服务过账，外层事务始终回滚，不留下盘点或数量变化，不启服务。 */
@EnabledIfEnvironmentVariable(named="JJX_TEST_QUICK_STOCKTAKE_DATABASE",matches="1")
class QuickStocktakeMysqlTest {
    @Test void saveConfirmAndRetryUseRealMysqlThenRollBackAllBusinessChanges() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL","jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),System.getenv().getOrDefault("DB_USER","root"),System.getenv().getOrDefault("DB_PASS","123456"));
        var jdbc=new JdbcTemplate(ds);var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        for(var type:List.of(QuickStocktakeMapper.class,InventoryStocktakeOrderMapper.class,InventoryStocktakeItemMapper.class,InventoryStockItemMapper.class,
            InventoryItemMapper.class,InventoryInboundItemMapper.class,InventoryWarehouseMapper.class,InventoryStorageLocationMapper.class,
            InventoryStockMapper.class,InventoryTransactionMapper.class,LocationHierarchyMapper.class))config.addMapper(type);
        var bean=new MybatisSqlSessionFactoryBean();bean.setDataSource(ds);bean.setConfiguration(config);
        var session=new SqlSessionTemplate(bean.getObject());var manager=new DataSourceTransactionManager(ds);
        var stocks=session.getMapper(InventoryStockItemMapper.class);var items=session.getMapper(InventoryItemMapper.class);
        var locations=session.getMapper(InventoryStorageLocationMapper.class);
        var mutation=new InventoryStockMutationServiceImpl(stocks,session.getMapper(InventoryStockMapper.class),session.getMapper(InventoryTransactionMapper.class));
        mutation.setLocationPolicy(new LocationHierarchyPolicy(session.getMapper(LocationHierarchyMapper.class),locations,items));
        var target=new QuickStocktakeService(session.getMapper(QuickStocktakeMapper.class),session.getMapper(InventoryStocktakeOrderMapper.class),session.getMapper(InventoryStocktakeItemMapper.class),stocks,
            items,session.getMapper(InventoryInboundItemMapper.class),session.getMapper(InventoryWarehouseMapper.class),locations,mutation);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var service=(QuickStocktakeService)proxy.getProxy();
        Long id=jdbc.queryForObject("SELECT b.item_id FROM inventory_stock_item b JOIN inventory_item i ON i.inventory_item_id=b.inventory_item_id WHERE b.status=1 AND i.status=1 AND b.quantity-COALESCE(b.reserved_quantity,0)>0.001 ORDER BY b.item_id LIMIT 1",Long.class);
        var before=stocks.selectById(id);String uuid=UUID.randomUUID().toString();String no="STQ"+uuid.replace("-","");
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            try {
                var stock=stocks.selectById(id);var item=items.selectById(stock.getInventoryItemId());
                var request=new QuickStocktakeData.Save(uuid,stock.getInventoryItemId(),id,QuickStocktakeService.fingerprint(stock,item),stock.getQuantity().subtract(new BigDecimal("0.0001")),"事务回滚验证");
                var receipt=service.save(request,1L,"事务验证","Codex-test");
                assertEquals(receipt.stocktakeId(),service.save(request,1L,"事务验证","Codex-test").stocktakeId());
                assertEquals(0,before.getQuantity().compareTo(stocks.selectById(id).getQuantity()));
                service.confirm(receipt.stocktakeId(),1L,"事务验证","Codex-test");service.confirm(receipt.stocktakeId(),1L,"事务验证","Codex-test");
                assertEquals(0,request.actualQuantity().compareTo(stocks.selectById(id).getQuantity()));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE source_no=? AND source_type='STOCKTAKE'",Integer.class,no));
                assertEquals(0,new BigDecimal("-0.0001").compareTo(jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_transaction WHERE source_no=?",BigDecimal.class,no)));
            } finally {tx.setRollbackOnly();}
        });
        assertEquals(0,before.getQuantity().compareTo(stocks.selectById(id).getQuantity()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_stocktake_order WHERE stocktake_no=?",Integer.class,no));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE source_no=?",Integer.class,no));
        System.out.println("MYSQL_QUICK_STOCKTAKE_ROLLBACK_OK");
    }
}
