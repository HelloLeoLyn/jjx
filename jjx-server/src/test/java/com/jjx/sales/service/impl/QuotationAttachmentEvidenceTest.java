package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.sales.mapper.*;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.utils.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationAttachmentEvidenceTest {
    @Mock QuotationMapper quotationMapper;
    @Mock QuotationFlowMapper quotationFlowMapper;
    @Mock SalesQuotationItemMapper quotationItemMapper;
    @Mock SysAttachmentRefMapper attachmentRefMapper;
    @Mock com.jjx.event.EventPublisher eventPublisher;
    @Spy @InjectMocks QuotationServiceImpl service;
    static {
        for (Class<?> type : List.of(SysAttachmentRef.class,SalesQuotationItem.class))
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),"evidence-test"),type);
    }

    SalesQuotation quotation(QuotationStatus status) {
        var q = new SalesQuotation(); q.setQuotationId(10L); q.setQuotationStatus(status.getValue());
        q.setCustomerId(1L); q.setQuotationDate(LocalDate.now()); q.setTotalAmount(BigDecimal.TEN); q.setFinalAmount(BigDecimal.TEN);
        doReturn(q).when(service).selectQuotationById(10L);
        return q;
    }
    void validRef() {
        var r = new SysAttachmentRef(); r.setId(100L); r.setAttachmentId(900L); r.setSourceType("quotation"); r.setSourceId(10L); r.setDeleted(false);
        when(attachmentRefMapper.selectOne(any())).thenReturn(r);
    }
    void act(QuotationServiceImpl target, String action, String legacy, List<Long> refs) {
        switch(action) {
            case "send" -> target.sendQuotation(10L,legacy,refs);
            case "submit" -> target.submitReview(10L,legacy,refs);
            case "review" -> target.reviewQuotation(10L,true,"ok",legacy,refs);
            case "reviewReject" -> target.reviewQuotation(10L,false,"需修改",legacy,refs);
            case "status" -> target.updateQuotationStatus(10L,QuotationStatus.ACCEPTED.getValue(),legacy,refs);
            case "confirm" -> target.confirmQuotation(10L,legacy,refs);
            case "reject" -> target.rejectQuotation(10L,null,legacy,refs);
            case "modify" -> target.modifyQuotation(10L,legacy,refs);
            default -> throw new AssertionError(action);
        }
    }
    @ParameterizedTest @ValueSource(strings={"send","submit","review","reviewReject","status","confirm","reject","modify"})
    void allActionsRejectLegacyBeforeChangingStatus(String action) {
        assertThrows(BusinessException.class,()->act(service,action,"900",List.of()));
        verifyNoInteractions(quotationMapper,quotationFlowMapper);
    }
    @ParameterizedTest @ValueSource(strings={"send","submit","review","reviewReject","status","confirm","reject","modify"})
    void allActionsPersistRefIdentityInsteadOfFileIdentity(String action) {
        validRef();
        var status=switch(action) {case "send" -> QuotationStatus.APPROVED; case "submit" -> QuotationStatus.DRAFT;
            case "review","reviewReject" -> QuotationStatus.PENDING_REVIEW; case "modify" -> QuotationStatus.COMPLETED; default -> QuotationStatus.SENT;};
        quotation(status);
        if(action.equals("send")||action.equals("submit")) {
            doNothing().when(service).recalcQuotationAmounts(10L);
            when(quotationItemMapper.selectCount(any())).thenReturn(1L);
        }
        when(quotationMapper.updateById(any(SalesQuotation.class))).thenReturn(1);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(request));
        when(quotationFlowMapper.insert(any(SalesQuotationFlow.class))).thenAnswer(call -> {
            ((SalesQuotationFlow) call.getArgument(0)).setFlowId(42L);
            return 1;
        });
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(7L);
            security.when(SecurityUtils::getDisplayName).thenReturn("报价审核员");
            act(service,action,null,List.of(100L));
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
        var captured=ArgumentCaptor.forClass(SalesQuotationFlow.class);
        verify(quotationFlowMapper).insert(captured.capture());
        assertEquals("[100]",captured.getValue().getAttachmentRefIds());
        assertNull(captured.getValue().getAttachmentIds());
        assertEquals(7L,captured.getValue().getOperatorId());
        assertEquals("报价审核员",captured.getValue().getOperatorName());
        assertEquals(java.util.Map.of("quotationId",10L,"reviewFlowId","qf-42"),
                request.getAttribute("jjx.quotationFlowLog"));
    }
    @Test void otherQuotationRefIsRejected() {
        var ref=new SysAttachmentRef();ref.setSourceType("quotation");ref.setSourceId(20L);
        when(attachmentRefMapper.selectOne(any())).thenReturn(ref);
        assertThrows(BusinessException.class,()->service.confirmQuotation(10L,null,List.of(100L)));
        verifyNoInteractions(quotationMapper,quotationFlowMapper);
    }
    @Test void removedOrDuplicateRefIsRejected() {
        assertThrows(BusinessException.class,()->service.confirmQuotation(10L,null,List.of(100L,100L)));
        when(attachmentRefMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class,()->service.confirmQuotation(10L,null,List.of(100L)));
        verifyNoInteractions(quotationMapper,quotationFlowMapper);
    }
    @Test void evidenceFailureRollsBackStatusInSameTransaction() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:quotation-evidence-rollback;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE test_status(id INT PRIMARY KEY,status INT)");
        jdbc.update("INSERT INTO test_status VALUES (10,?)",QuotationStatus.SENT.getValue());
        quotation(QuotationStatus.SENT);
        when(quotationMapper.updateById(any(SalesQuotation.class))).thenAnswer(call ->
            jdbc.update("UPDATE test_status SET status=? WHERE id=10",((SalesQuotation)call.getArgument(0)).getQuotationStatus()));
        when(quotationFlowMapper.insert(any(SalesQuotationFlow.class))).thenThrow(new IllegalStateException("write failed"));
        var factory=new ProxyFactory(service);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        var transactional=(QuotationServiceImpl)factory.getProxy();
        try(var security=mockStatic(SecurityUtils.class)) {
            assertThrows(IllegalStateException.class,()->transactional.confirmQuotation(10L,null,List.of()));
        }
        assertEquals(QuotationStatus.SENT.getValue(),jdbc.queryForObject("SELECT status FROM test_status WHERE id=10",Integer.class));
        verifyNoInteractions(eventPublisher);
    }
}
