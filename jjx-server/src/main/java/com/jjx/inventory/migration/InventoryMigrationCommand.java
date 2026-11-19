package com.jjx.inventory.migration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.master.*;
import com.jjx.inventory.service.impl.*;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import com.jjx.system.mapper.*;
import com.jjx.system.service.SysConfigService;
import com.jjx.system.service.impl.SysTagServiceImpl;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.Path;
import java.util.List;
import static com.jjx.inventory.migration.InventoryMigrationData.*;

/** 一次性迁移命令：无Web服务器、无调度器、不启停现有服务，复用正式建档/库存/序列服务。
 * preview使用只读账号；import必须显式--execute与在制任务码。
 */
public final class InventoryMigrationCommand {
    private InventoryMigrationCommand() { }
    public record Runtime(InventoryMigrationService service, JdbcTemplate jdbc, ObjectMapper json, com.jjx.inventory.service.InventoryStockMutationService mutation, InventoryStockMapper stockMapper) { }
    public static Runtime connect(String url,String user,String password) throws Exception {
        var ds=new DriverManagerDataSource(url,user,password);
        var manager=new DataSourceTransactionManager(ds);
        var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (Class<?> type:List.of(InventoryMaterialMapper.class,InventoryMaterialCategoryMapper.class,
                AttributeTemplateMapper.class,MaterialMasterMapper.class,SysDictItemMapper.class,SysDictMapper.class,
                PurchaseSupplierMapper.class,SysTagMapper.class,SysTagRelMapper.class,SysConfigMapper.class,
                SysNumberSequenceMapper.class,InventoryInboundOrderMapper.class,InventoryInboundItemMapper.class,
                InventoryStockItemMapper.class,InventoryStockMapper.class,InventoryTransactionMapper.class,
                InventoryItemMapper.class,InventoryStorageLocationMapper.class)) config.addMapper(type);
        var factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        factory.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session=new SqlSessionTemplate(factory.getObject());
        ObjectMapper json=new ObjectMapper().findAndRegisterModules();
        var materials=session.getMapper(InventoryMaterialMapper.class);
        var stockMapper=session.getMapper(InventoryStockMapper.class);
        // 本命令不运行通知/派工订阅器；迁移来源凭证和操作者构成持久化审计。
        EventPublisher events=(code,payload) -> { };
        var sequence=new RedisSequenceService(null,new SysConfigService(session.getMapper(SysConfigMapper.class),event -> { }),json,session.getMapper(SysNumberSequenceMapper.class),event -> { });
        var tags=new SysTagServiceImpl(session.getMapper(SysTagMapper.class),session.getMapper(SysTagRelMapper.class));
        // 只调用既有generateCode/existsByCode；未使用的业务协作依赖不初始化，避免拉起后台服务。
        var numbers=new InventoryMaterialServiceImpl(materials,null,events,stockMapper,sequence,null,null,null,null,null,tags);
        var catalog=new MaterialMasterCatalog(session.getMapper(InventoryMaterialCategoryMapper.class),session.getMapper(AttributeTemplateMapper.class),session.getMapper(SysDictItemMapper.class),session.getMapper(SysDictMapper.class),json);
        var master=new MaterialMasterService(catalog,session.getMapper(MaterialMasterMapper.class),materials,numbers,session.getMapper(PurchaseSupplierMapper.class),tags,events,json);
        var stockItems=session.getMapper(InventoryStockItemMapper.class);
        var tx=session.getMapper(InventoryTransactionMapper.class);
        var mutation=new InventoryStockMutationServiceImpl(stockItems,stockMapper,tx);
        var repository=new InventoryMigrationRepository(new JdbcTemplate(ds));
        var target=new InventoryMigrationService(repository,master,materials,session.getMapper(InventoryInboundOrderMapper.class),session.getMapper(InventoryInboundItemMapper.class),stockItems,tx,session.getMapper(InventoryStorageLocationMapper.class),new InventoryItemServiceImpl(session.getMapper(InventoryItemMapper.class)),mutation,json,sequence);
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return new Runtime((InventoryMigrationService)proxy.getProxy(),new JdbcTemplate(ds),json,mutation,stockMapper);
    }
    public static void main(String[] args) throws Exception {
        if (args.length==0 || "--help".equals(args[0])) {
            System.out.println("preview <manifest.json>：只读预检；import <manifest.json> --execute <dev任务码>：正式迁移（不启服务）。连接用JJX_MIGRATION_URL/USER/PASS；写入前须确认用户授权、备份条件、任务归属。");return;
        }
        boolean execute="import".equals(args[0]);
        if ((!execute && !"preview".equals(args[0])) || args.length!=(execute?4:2) || execute && !"--execute".equals(args[2])) throw new IllegalArgumentException("参数无效，请用--help");
        String user=System.getenv().getOrDefault("JJX_MIGRATION_USER",execute?"root":"jjx_ro");
        String password=System.getenv().getOrDefault("JJX_MIGRATION_PASS",execute?"123456":"jjx_ro_2026");
        String url=System.getenv().getOrDefault("JJX_MIGRATION_URL","jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
        Runtime runtime=connect(url,user,password);
        Batch batch=runtime.json().readValue(Path.of(args[1]).toFile(),Batch.class);
        if (!execute) { System.out.println(runtime.json().writeValueAsString(runtime.service().preview(batch))); return; }
        Integer claimed=runtime.jdbc().queryForObject("SELECT COUNT(*) FROM sys_task WHERE task_code=? AND status=1 AND assignee_name='Codex'",Integer.class,args[3]);
        if (claimed==null || claimed!=1) throw new IllegalStateException("必须提供Codex已认领的准确任务码");
        // 本机agent无登录用户ID，审计记录Codex，不冒用管理员身份。
        Long operatorId=null;
        System.out.println(runtime.json().writeValueAsString(runtime.service().importBatch(batch,"Codex","Codex",operatorId)));
    }
}
