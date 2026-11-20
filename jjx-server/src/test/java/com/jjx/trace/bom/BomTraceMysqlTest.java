package com.jjx.trace.bom;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.trace.service.impl.TraceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="JJX_TEST_BOM_TRACE_DATABASE",matches="1")
class BomTraceMysqlTest {
    @Test void yl1025AllVersionsAndLegacyRootRequestsAreReadWithoutChangingOriginalLogs() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL","jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),"jjx_ro",System.getenv().getOrDefault("JJX_RO_PASS","jjx_ro_2026"));
        var jdbc=new JdbcTemplate(ds);var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.addMapper(BomTraceMapper.class);config.addMapper(SysOperLogMapper.class);
        var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);
        var session=new SqlSessionTemplate(factory.getObject());var reader=new BomTraceReader(session.getMapper(BomTraceMapper.class));
        var service=new TraceServiceImpl(session.getMapper(SysOperLogMapper.class),null,null,null);ReflectionTestUtils.setField(service,"bomTraceReader",reader);
        Long anchor=jdbc.queryForObject("SELECT bom_id FROM engineering_bom WHERE bom_code='YL1025PWOO-BOM' AND is_current=1",Long.class);
        Long max=jdbc.queryForObject("SELECT MAX(id) FROM sys_oper_log",Long.class);
        var before=jdbc.queryForList("SELECT id,biz_type,biz_id,action,detail,oper_param FROM sys_oper_log WHERE id<=? ORDER BY id",max);
        var context=reader.context(anchor);assertTrue(context.versions().size()>=3);
        var all=service.getEventsByBiz("bom",anchor,null,1,100);assertTrue(all.getTotal()>=10);
        assertTrue(all.getRecords().stream().anyMatch(e->BomTraceReader.ARCHIVE.equals(e.getEvidenceSource())));
        assertTrue(all.getRecords().stream().anyMatch(e->BomTraceReader.LEGACY.equals(e.getEvidenceSource())));
        assertTrue(all.getRecords().stream().anyMatch(e->"V1.0".equals(e.getBomVersion())));
        assertTrue(all.getRecords().stream().anyMatch(e->"V1.2".equals(e.getBomVersion())));
        var fromOld=service.getEventsByBiz("bom",context.versions().getFirst().getBomId(),null,1,100);
        assertEquals(all.getTotal(),fromOld.getTotal());
        var first=service.getEventsByBiz("bom",anchor,null,1,3);var second=service.getEventsByBiz("bom",anchor,null,2,3);
        assertTrue(Collections.disjoint(first.getRecords().stream().map(e->e.getEventId()).toList(),second.getRecords().stream().map(e->e.getEventId()).toList()));
        assertTrue(service.getEventsByBiz("bom",anchor,BomTraceReader.VERSION_SCOPE,1,100).getTotal()<all.getTotal());
        assertEquals(before,jdbc.queryForList("SELECT id,biz_type,biz_id,action,detail,oper_param FROM sys_oper_log WHERE id<=? ORDER BY id",max));
        System.out.println("MYSQL_BOM_TRACE_READ_ONLY_OK versions="+context.versions().size()+" events="+all.getTotal());
    }
}
