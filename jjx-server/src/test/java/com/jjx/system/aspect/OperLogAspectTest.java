package com.jjx.system.aspect;

import com.jjx.common.core.result.Result;
import com.jjx.sales.domain.vo.SalesInquiryEditVO;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.service.LogSaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class OperLogAspectTest {

    private OperLogAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new OperLogAspect(
            mock(LogSaveService.class),
            mock(com.jjx.sales.mapper.QuotationMapper.class),
            mock(SysAttachmentMapper.class));
    }

    @Test
    void shouldResolveDetailFromMethodResult() {
        SalesInquiryEditVO editVO = new SalesInquiryEditVO();
        editVO.setDetailMessage("{\"changes\":[\"预估单价:10→12\"]}");
        StandardEvaluationContext context = new StandardEvaluationContext();
        OperLogAspect.bindResult(context, Result.success(editVO));
        SysOperLog operLog = new SysOperLog();

        aspect.applyDetail(context, "#result.data.detailMessage", operLog);

        assertEquals(editVO.getDetailMessage(), operLog.getDetail());
    }

    @Test
    void shouldResolveNumericAndConditionalBizStatus() throws Exception {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("approved", true);

        assertEquals(5, aspect.resolveBizStatus(context, "5"));
        assertEquals(6, aspect.resolveBizStatus(context, "#approved ? 6 : 3"));
    }

    @Test
    void shouldResolveEnumBizStatusByCode() throws Exception {
        StandardEvaluationContext context = new StandardEvaluationContext();

        assertEquals(0, aspect.resolveBizStatus(context,
            "T(com.jjx.sales.enums.InquiryStatus).DRAFT"));
    }

    @Test
    void shouldRecognizeOnlyAttachmentIdsDetailExpression() {
        assertEquals(true, OperLogAspect.isAttachmentDetailExpression("#attachmentIds"));
        assertEquals(false,
            OperLogAspect.isAttachmentDetailExpression("#result.data.detailMessage"));
    }
}
