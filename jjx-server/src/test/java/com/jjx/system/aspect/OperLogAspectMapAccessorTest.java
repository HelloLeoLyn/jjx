package com.jjx.system.aspect;

import com.jjx.common.core.result.Result;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.service.LogSaveService;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class OperLogAspectMapAccessorTest {

    @Test
    void resolvesMapPropertyInsideResultData() {
        OperLogAspect aspect = new OperLogAspect(
                mock(LogSaveService.class),
                mock(QuotationMapper.class),
                mock(SysAttachmentMapper.class));
        StandardEvaluationContext context = OperLogAspect.createSpelContext();
        OperLogAspect.bindResult(context, Result.success(Map.of("transferNo", "TF2609210001")));
        SysOperLog operLog = new SysOperLog();

        aspect.applyDetail(context, "#result.data.transferNo", operLog);

        assertEquals("TF2609210001", operLog.getDetail());
    }
}
