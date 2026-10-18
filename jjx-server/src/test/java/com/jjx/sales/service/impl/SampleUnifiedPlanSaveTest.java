package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.dto.save.SampleProcessPlanDTO;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.*;
import com.jjx.system.utils.SecurityUtils;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实业务服务+Spring事务；mapper以JDBC写本地H2，非真实业务MySQL。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SampleUnifiedPlanSaveTest {
    @Mock OrderMapper orders;
    @Mock SalesSampleProcessMapper processes;
    @Mock SalesSampleBomMapper materials;
    @InjectMocks SampleOrderServiceImpl target;
    JdbcTemplate jdbc;
    SampleOrderServiceImpl service;

    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:unified_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE test_order(id BIGINT PRIMARY KEY,note VARCHAR(2000),status INT,current_process VARCHAR(100))");
        jdbc.execute("CREATE TABLE test_process(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100),status INT,start_time TIMESTAMP,materials VARCHAR(2000),params VARCHAR(2000))");
        jdbc.execute("CREATE TABLE test_bom(name VARCHAR(100))");
        jdbc.update("INSERT INTO test_order VALUES(10,'旧备注',?,'旧工序')", SampleOrderStatusEnum.ENGINEERING.getValue());
        jdbc.update("INSERT INTO test_process VALUES(41,'旧工序',1,TIMESTAMP '2026-10-18 10:00:00',NULL,NULL)");
        jdbc.update("INSERT INTO test_bom VALUES('旧材料')");
        when(orders.selectById(10L)).thenAnswer(call -> {
            Map<String,Object> row = jdbc.queryForMap("SELECT * FROM test_order WHERE id=10");
            var order = new SalesOrder(); order.setOrderId(10L);order.setDeleted(0);order.setSampleRound(1);
            order.setSampleStatus((Integer) row.get("STATUS"));order.setEngineeringNote((String) row.get("NOTE"));
            return order;
        });
        when(orders.updateById(any(SalesOrder.class))).thenAnswer(call -> {
            SalesOrder row=call.getArgument(0);
            if(row.getEngineeringNote()!=null) jdbc.update("UPDATE test_order SET note=? WHERE id=10",row.getEngineeringNote());
            return 1;
        });
        when(processes.selectList(any())).thenAnswer(call -> readProcesses());
        when(processes.selectByOrderAndRound(10L,1)).thenAnswer(call -> readProcesses());
        when(processes.delete(any())).thenAnswer(call -> jdbc.update("DELETE FROM test_process"));
        when(processes.insert(any(SalesSampleProcess.class))).thenAnswer(call -> {
            SalesSampleProcess row=call.getArgument(0);
            return jdbc.update("INSERT INTO test_process(name,status,start_time,materials,params) VALUES(?,?,?,?,?)",row.getProcessName(),row.getStatus(),row.getStartTime(),row.getMaterials(),row.getCustomProcessParams());
        });
        when(materials.delete(any())).thenAnswer(call -> jdbc.update("DELETE FROM test_bom"));
        when(materials.insert(any(SalesSampleBom.class))).thenAnswer(call -> {
            SalesSampleBom row=call.getArgument(0);return jdbc.update("INSERT INTO test_bom VALUES(?)",row.getMaterialName());
        });
        when(materials.selectByOrderId(10L)).thenReturn(List.of());
        var proxy = new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        service=(SampleOrderServiceImpl) proxy.getProxy();
    }
    List<SalesSampleProcess> readProcesses() {
        return jdbc.query("SELECT * FROM test_process ORDER BY id",(rs,n)->{
            var row=new SalesSampleProcess();row.setProcessId(rs.getLong("id"));row.setProcessName(rs.getString("name"));
            row.setStatus(rs.getInt("status"));Timestamp t=rs.getTimestamp("start_time");if(t!=null)row.setStartTime(t.toLocalDateTime());
            return row;
        });
    }
    SampleProcessPlanDTO request() {
        var dto=new SampleProcessPlanDTO();dto.setEngineeringNote("新备注");
        var item=new SampleProcessPlanDTO.Item();item.setProcessName("新组装");item.setStatus(1);item.setProcessId(41L);
        var print=new SampleProcessPlanDTO.Item();print.setProcessName("新印刷");print.setMajorCategory("PRINT");print.setCustomProcessParams("{\"printName\":\"新印刷\"}");
        dto.setItems(List.of(item,print));
        var bom=new SalesSampleBom();bom.setMaterialName("新独立材料");bom.setQuantity(BigDecimal.ONE);
        dto.setStandaloneMaterials(List.of(bom));return dto;
    }
    String note(){return jdbc.queryForObject("SELECT note FROM test_order WHERE id=10",String.class);}

    @Test void fullSaveCommitsNoteAssemblyPrintAndMaterialsTogether() {
        try(var security=mockStatic(SecurityUtils.class)) {service.saveProcessPlan(10L,request());}
        assertEquals("新备注",note());
        assertEquals(List.of("新组装","新印刷"),jdbc.queryForList("SELECT name FROM test_process ORDER BY id",String.class));
        assertEquals("新独立材料",jdbc.queryForObject("SELECT name FROM test_bom",String.class));
        assertEquals(Timestamp.valueOf("2026-10-18 10:00:00"),jdbc.queryForObject("SELECT start_time FROM test_process WHERE name='新组装'",Timestamp.class));
        assertTrue(jdbc.queryForObject("SELECT params FROM test_process WHERE name='新印刷'",String.class).contains("新印刷"));
    }
    @Test void noteOnlyKeepsProcessIdsExecutionAndMaterialRows() {
        var dto=new SampleProcessPlanDTO();dto.setEngineeringNote("只改备注");service.saveProcessPlan(10L,dto);
        assertEquals("只改备注",note());assertEquals(41L,readProcesses().getFirst().getProcessId());
        assertNotNull(readProcesses().getFirst().getStartTime());
        assertEquals("旧材料",jdbc.queryForObject("SELECT name FROM test_bom",String.class));
        verify(processes,never()).delete(any());verify(processes,never()).insert(any(SalesSampleProcess.class));
        verify(materials,never()).delete(any());
    }
    @Test void failureRollsBackNoteProcessAndMaterialChanges() {
        var dto=request();dto.getStandaloneMaterials().getFirst().setMaterialName(" ");
        try(var security=mockStatic(SecurityUtils.class)) {assertThrows(BusinessException.class,()->service.saveProcessPlan(10L,dto));}
        assertEquals("旧备注",note());assertEquals(41L,readProcesses().getFirst().getProcessId());
        assertEquals("旧工序",readProcesses().getFirst().getProcessName());
        assertEquals("旧材料",jdbc.queryForObject("SELECT name FROM test_bom",String.class));
    }
    @Test void omittedNotePreservesValueAndExplicitEmptyClearsIt() {
        var dto=new SampleProcessPlanDTO();dto.setItems(List.of());
        try(var security=mockStatic(SecurityUtils.class)) {service.saveProcessPlan(10L,dto);}
        assertEquals("旧备注",note());dto.setEngineeringNote("");dto.setItems(null);service.saveProcessPlan(10L,dto);assertEquals("",note());
    }
    @Test void historyRoundCannotChangeNoteOrProcesses() {
        var dto=request();dto.setRoundNo(2);assertThrows(BusinessException.class,()->service.saveProcessPlan(10L,dto));
        assertEquals("旧备注",note());assertEquals(41L,readProcesses().getFirst().getProcessId());
    }
    @Test void noteLengthAndOriginalNoteStatusGuardRemainEnforced() {
        var dto=new SampleProcessPlanDTO();dto.setEngineeringNote("x".repeat(2001));assertThrows(BusinessException.class,()->service.saveProcessPlan(10L,dto));
        jdbc.update("UPDATE test_order SET status=?",SampleOrderStatusEnum.SAMPLE_READY.getValue());
        dto.setEngineeringNote("新备注");assertThrows(BusinessException.class,()->service.saveProcessPlan(10L,dto));
        assertEquals("旧备注",note());
    }
    @Test void unchangedNullNoteDoesNotBlockExistingPlanSaveOutsideEngineering() {
        jdbc.update("UPDATE test_order SET status=?,note=NULL",SampleOrderStatusEnum.SAMPLE_READY.getValue());
        var dto=request();dto.setEngineeringNote(null);
        try(var security=mockStatic(SecurityUtils.class)) {service.saveProcessPlan(10L,dto);}
        assertNull(note());assertEquals(2,readProcesses().size());
    }

}
