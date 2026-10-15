package com.jjx.sales.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.*;
import com.jjx.system.service.AttachmentEvidenceService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class SampleRoundEvidenceTest {
    @Mock OrderMapper orderMapper;
    @Mock SalesSampleRoundMapper sampleRoundMapper;
    @Mock SalesSampleProcessMapper sampleProcessMapper;
    @Mock AttachmentEvidenceService attachmentEvidence;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks SampleOrderServiceImpl service;

    void readyOrder() {
        var order=new SalesOrder(); order.setOrderId(10L); order.setDeleted(0); order.setEngineeringAcceptor("engineer");order.setSampleRound(2);
        when(orderMapper.selectById(10L)).thenReturn(order);
        var process=new SalesSampleProcess();process.setStatus(2);
        when(sampleProcessMapper.selectByOrderAndRound(10L,2)).thenReturn(List.of(process));
    }
    @Test void completionPersistsOnlyCapturedRefIdsForThisRound() {
        readyOrder();
        when(orderMapper.updateSampleStatus(eq(10L),anyInt(),anyInt())).thenReturn(1);
        when(attachmentEvidence.snapshotSampleDrawings(10L)).thenReturn("[101,102]");
        when(sampleRoundMapper.insert(any(SalesSampleRound.class))).thenReturn(1);
        service.markSampleReady(10L,null);
        var capture=ArgumentCaptor.forClass(SalesSampleRound.class);
        verify(sampleRoundMapper).insert(capture.capture());
        assertEquals(2,capture.getValue().getRoundNo());
        assertEquals("[101,102]",capture.getValue().getAttachmentRefIds());
        assertNull(capture.getValue().getAttachmentIds());
    }
    @Test void eachRoundLoadsItsOwnSavedIds() {
        var one=new SalesSampleRound();one.setAttachmentRefIds("[101]");
        var two=new SalesSampleRound();two.setAttachmentRefIds("[102]");
        when(sampleRoundMapper.selectByOrderId(10L)).thenReturn(List.of(one,two));
        when(attachmentEvidence.listSnapshot("[101]","sample",10L)).thenReturn(List.of(Map.of("refId",101L)));
        when(attachmentEvidence.listSnapshot("[102]","sample",10L)).thenReturn(List.of(Map.of("refId",102L)));
        var rounds=service.listSampleRounds(10L);
        assertEquals(101L,rounds.get(0).getAttachmentRefs().get(0).get("refId"));
        assertEquals(102L,rounds.get(1).getAttachmentRefs().get(0).get("refId"));
    }
    @Test void failedSnapshotRollsBackSampleReadyStatus() {
        readyOrder();
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:sample-evidence-rollback;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE test_status(id INT PRIMARY KEY,status INT)");
        jdbc.update("INSERT INTO test_status VALUES (10,?)",SampleOrderStatusEnum.ENGINEERING.getValue());
        when(orderMapper.updateSampleStatus(eq(10L),anyInt(),anyInt())).thenAnswer(call ->
            jdbc.update("UPDATE test_status SET status=? WHERE id=10",(Integer)call.getArgument(2)));
        when(attachmentEvidence.snapshotSampleDrawings(10L)).thenThrow(new IllegalStateException("snapshot failed"));
        var factory=new ProxyFactory(service);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        var transactional=(SampleOrderServiceImpl)factory.getProxy();
        assertThrows(BusinessException.class,()->transactional.markSampleReady(10L,null));
        assertEquals(SampleOrderStatusEnum.ENGINEERING.getValue(),jdbc.queryForObject("SELECT status FROM test_status WHERE id=10",Integer.class));
        verifyNoInteractions(sampleRoundMapper);
    }
}
