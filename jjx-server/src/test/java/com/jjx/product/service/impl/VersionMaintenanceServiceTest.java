package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.product.domain.converter.EngineeringBomConverterImpl;
import com.jjx.product.mapper.*;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.product.service.IEngineeringRoutingService;
import com.jjx.system.service.OperLogChangeRecorder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** H2 真实事务/行锁验证，不代表生产库写入或页面运行验收。 */
class VersionMaintenanceServiceTest {
    JdbcTemplate db;
    EngineeringBomMapper bomMapper;
    EngineeringRoutingMapper routeMapper;
    OperLogChangeRecorder audit;
    IEngineeringBomService bom;
    IEngineeringRoutingService route;
    EngineeringBomServiceImpl bomImpl;
    EngineeringRoutingServiceImpl routeImpl;
    DataSourceTransactionManager tx;

    @BeforeEach
    void setup() {
        var source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("version-maintenance",new SpringManagedTransactionFactory(),source));
        config.addMapper(EngineeringBomMapper.class);config.addMapper(EngineeringRoutingMapper.class);
        config.addMapper(EngineeringBomItemMapper.class);config.addMapper(EngineeringRoutingItemMapper.class);
        config.addMapper(ProductMapper.class);
        var session=new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        db=new JdbcTemplate(source);tx=new DataSourceTransactionManager(source);
        createTable(EngineeringBom.class);createTable(EngineeringRouting.class);
        bomMapper=spy(session.getMapper(EngineeringBomMapper.class));routeMapper=spy(session.getMapper(EngineeringRoutingMapper.class));
        audit=mock(OperLogChangeRecorder.class);
        bomImpl=spy(new EngineeringBomServiceImpl(bomMapper,mock(EngineeringBomItemMapper.class),mock(ProductMapper.class),
                new EngineeringBomConverterImpl(),mock(com.jjx.production.mapper.ProductionOrderMapper.class),null,mock(com.jjx.system.service.ReviewFlowService.class),mock(com.jjx.event.EventPublisher.class),audit,null));
        ReflectionTestUtils.setField(bomImpl,"baseMapper",bomMapper);
        doReturn(List.of()).when(bomImpl).checkBomForApprove(anyLong());
        routeImpl=new EngineeringRoutingServiceImpl(routeMapper,mock(com.jjx.event.EventPublisher.class),
                mock(EngineeringRoutingItemMapper.class),null,mock(ProductMapper.class),audit);
        ReflectionTestUtils.setField(routeImpl,"baseMapper",routeMapper);
        bom=proxy(bomImpl,IEngineeringBomService.class);route=proxy(routeImpl,IEngineeringRoutingService.class);
    }
    private <T> T proxy(Object target,Class<T> type) {
        var factory=new ProxyFactory(target);factory.setInterfaces(type);
        factory.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
    }
    private void createTable(Class<?> entity) {
        var info=TableInfoHelper.getTableInfo(entity);
        var columns=new StringBuilder(info.getKeyColumn()+" BIGINT AUTO_INCREMENT PRIMARY KEY");
        for(var field:info.getFieldList()) {
            Class<?> type=field.getPropertyType();
            String sql=type==String.class?"VARCHAR(4000)":type==LocalDateTime.class?"TIMESTAMP":type==java.util.Date.class?"DATE":
                    type==Boolean.class?"BOOLEAN":type==BigDecimal.class?"DECIMAL(18,4)":type==Integer.class?"INT":"BIGINT";
            columns.append(',').append(field.getColumn()).append(' ').append(sql);
        }
        if(entity == EngineeringBom.class) columns.append(",approve_by VARCHAR(100),approve_time TIMESTAMP");
        db.execute("CREATE TABLE "+info.getTableName()+" ("+columns+")");
    }
    private void seed(String domain,int id,String version,int status,boolean current) {
        if(domain.equals("bom")) db.update("INSERT INTO engineering_bom(bom_id,bom_code,bom_version,version,product_id,output_type,output_id,approve_status,is_current) VALUES(?,?,?,?,1,'PRODUCT',1,?,?)",id,"B"+id,version,version,status,current);
        else db.update("INSERT INTO engineering_routing(routing_id,routing_code,routing_name,routing_version,version,product_id,output_type,output_id,approve_status,is_current) VALUES(?,?,?,?,?,1,'PRODUCT',1,?,?)",id,"R"+id,"路线"+id,version,version,status,current?1:0);
    }
    private void switchTo(String domain,long id,long expected) {
        if(domain.equals("bom")) bom.setDefaultBom(id,expected);else route.setCurrentVersion(id,expected);
    }
    private int currentCount(String domain) {return db.queryForObject("SELECT COUNT(*) FROM engineering_"+domain+" WHERE is_current=1",Integer.class);}

    @Test
    void bomAuditUsesExistingColumnsAndObjectRowsExposeServerEligibility() {
        seed("bom",1,"V1.0",2,false);
        try(var security=mockStatic(com.jjx.system.utils.SecurityUtils.class)) {
            security.when(com.jjx.system.utils.SecurityUtils::getDisplayName).thenReturn("测试审核人");
            var dto=new com.jjx.product.domain.dto.UpdateBomStatusDTO();dto.setBomId(1L);
            assertTrue(bom.approve(dto));
        }
        var approved=bom.getObjectVersions("PRODUCT",1L).get(0);
        assertEquals("测试审核人",approved.getApproveBy());assertNotNull(approved.getApproveTime());assertTrue(approved.isCanSetCurrent());
        db.update("UPDATE engineering_bom SET effective_date=DATE '2099-01-01' WHERE bom_id=1");
        assertFalse(bom.getObjectVersions("PRODUCT",1L).get(0).isCanSetCurrent());
        seed("routing",1,"V1.0",3,false);
        assertTrue(route.getObjectVersions("PRODUCT",1L).get(0).isCanSetCurrent());
    }

    @Test
    void defaultsCarryDecimalAndCopiesRejectSameObjectVersionAcrossCodes() {
        assertEquals("V1.1",IEngineeringBomService.nextCopyVersion(List.of("V1.0")));
        assertEquals("V10.0",IEngineeringBomService.nextCopyVersion(List.of("V9.9")));
        assertEquals("V1.11",IEngineeringBomService.nextCopyVersion(List.of("V1.9","V1.10")));
        for(String domain:List.of("bom","routing")) {
            seed(domain,1,"V1.0",3,false);
            if(domain.equals("bom")) {
                assertEquals("V1.1",bom.suggestCopyVersion(1L));
                var copy=bom.copyAsNewVersion(1L,null);assertEquals("V1.1",copy.getBomVersion());assertFalse(copy.getIsCurrent());assertEquals(1,copy.getApproveStatus());
                assertThrows(BusinessException.class,()->bom.copyAsNewVersion(1L,"v1.1"));
                seed(domain,100,"V4.0",1,false);
                assertThrows(BusinessException.class,()->bom.copyAsNewVersion(1L,"V4.0"));
            } else {
                assertEquals("V1.1",route.suggestCopyVersion(1L));
                var copy=route.copyAsNewVersion(1L,null);assertEquals("V1.1",copy.getRoutingVersion());assertEquals(0,copy.getIsCurrent());assertEquals(1,copy.getApproveStatus());
                assertThrows(BusinessException.class,()->route.copyAsNewVersion(1L,"v1.1"));
                seed(domain,100,"V4.0",1,false);
                assertThrows(BusinessException.class,()->route.copyAsNewVersion(1L,"V4.0"));
            }
        }
    }
    @Test
    void rejectsUnapprovedVoidedFutureAndExpiredAndRepeatedSwitches() {
        for(String domain:List.of("bom","routing")) {
            seed(domain,1,"V1.0",3,false);seed(domain,2,"V1.1",1,false);seed(domain,3,"V1.2",5,false);
            assertThrows(BusinessException.class,()->switchTo(domain,2,0));assertThrows(BusinessException.class,()->switchTo(domain,3,0));
            switchTo(domain,1,0);assertEquals(1,currentCount(domain));
            assertThrows(BusinessException.class,()->switchTo(domain,1,1));
        }
        seed("bom",4,"V2.0",3,false);db.update("UPDATE engineering_bom SET effective_date=DATE '2099-01-01' WHERE bom_id=4");
        assertThrows(BusinessException.class,()->bom.setDefaultBom(4L,1L));
        db.update("UPDATE engineering_bom SET effective_date=DATE '2000-01-01',expiry_date=CURRENT_DATE WHERE bom_id=4");
        assertThrows(BusinessException.class,()->bom.setDefaultBom(4L,1L));assertEquals(1,currentCount("bom"));
    }
    @Test
    void staleExpectedCurrentAndFailedConditionalUpdateRollBackAllFlags() {
        for(String domain:List.of("bom","routing")) {
            seed(domain,1,"V1.0",3,false);seed(domain,2,"V1.1",3,false);seed(domain,3,"V1.2",3,false);
            switchTo(domain,1,0);switchTo(domain,2,1);
            var ex=assertThrows(BusinessException.class,()->switchTo(domain,3,1));assertTrue(ex.getMessage().contains("刷新"));
            if(domain.equals("bom")) doReturn(0).when(bomMapper).promoteCurrent(3L,3);
            else doReturn(0).when(routeMapper).promoteCurrent(3L,3);
            assertThrows(BusinessException.class,()->switchTo(domain,3,2));
            assertEquals(1,currentCount(domain));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM engineering_"+domain+" WHERE "+(domain.equals("bom")?"bom_id":"routing_id")+"=2 AND is_current=1",Integer.class));
        }
        verify(audit,atLeastOnce()).recordUpdate(anyString(),anyString(),anyString(),anyString(),isNull(),anyString(),anyString(),argThat(changes->changes.get(0).contains("V1.0(#1)→V1.1(#2)")));
    }
    @Test
    void twoConcurrentSwitchRequestsHaveExactlyOneWinner() throws Exception {
        for(String domain:List.of("bom","routing")) {
            seed(domain,1,"V1.0",3,false);seed(domain,2,"V1.1",3,false);
            var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
            try {
                var tasks=new java.util.ArrayList<Future<Boolean>>();
                for(long id:List.of(1L,2L)) tasks.add(pool.submit(()->{
                    barrier.await(5,TimeUnit.SECONDS);
                    try {switchTo(domain,id,0);return true;}catch(BusinessException e){assertTrue(e.getMessage().contains("刷新"));return false;}
                }));
                int wins=0;for(var task:tasks)if(task.get(10,TimeUnit.SECONDS))wins++;
                assertEquals(1,wins);assertEquals(1,currentCount(domain));
            } finally {pool.shutdown();}
        }
    }
    @Test
    void voidAndDeleteAreRestrictedAndCannotBeMadeCurrentAgain() {
        for(String domain:List.of("bom","routing")) {
            seed(domain,1,"V1.0",3,false);switchTo(domain,1,0);
            if(domain.equals("bom")) {bom.voidVersion(1L);assertThrows(BusinessException.class,()->bom.removeBomWithItems(1L));}
            else {route.voidVersion(1L);assertThrows(BusinessException.class,()->route.deleteVersion(1L));}
            assertEquals(0,currentCount(domain));assertThrows(BusinessException.class,()->switchTo(domain,1,0));
            seed(domain,2,"V1.1",4,false);
            if(domain.equals("bom")) assertThrows(BusinessException.class,()->bom.removeBomWithItems(2L));
            else assertThrows(BusinessException.class,()->route.deleteVersion(2L));
            seed(domain,3,"V1.2",1,false);
            if(domain.equals("bom")) bom.removeBomWithItems(3L);else route.deleteVersion(3L);
        }
    }
}
