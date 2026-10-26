package com.jjx.trace.service.impl;

import com.jjx.common.enums.YesNoEnum;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuotationReviewGroupingTest {
    private UnifiedTraceEventVO event(String id,String group,String action,boolean success) {
        var event=new UnifiedTraceEventVO();
        event.setEventId(id); event.setGroupId(group); event.setTraceId("quote-trace");
        event.setBizType("quotation");event.setBizId("1");event.setAction(action);
        event.setResult((success?YesNoEnum.YES:YesNoEnum.NO).getCode());
        event.setTime(LocalDateTime.of(2026,10,26,15,44,14));
        event.setOperUrl(action.equals("附件新增")?"attachment.ref.附件新增":"/sales/quotation/review/1");
        return event;
    }
    @Test void successfulReviewOwnsItsAttachmentAndFailedAttemptWithoutHidingEither() {
        var attachment=event("oper-8","review-group","附件新增",true);
        var failed=event("oper-9","review-group","审核报价单",false);
        var success=event("oper-10","review-group","审核报价单",true);
        var merged=TraceEventAggregator.mergeAttachmentEvents(List.of(attachment,failed,success));
        assertEquals(1,merged.size()); assertSame(success,merged.getFirst());
        assertEquals(List.of(attachment,failed),success.getChildren());
    }
    @Test void cancelledUploadStaysAnAttachmentAndAnotherDialogCannotConsumeIt() {
        var attachment=event("oper-8","cancelled-group","附件新增",true);
        var review=event("oper-10","another-group","审核报价单",true);
        var merged=TraceEventAggregator.mergeAttachmentEvents(List.of(attachment,review));
        assertEquals(List.of(attachment,review),merged);
        assertTrue(attachment.getChildren().isEmpty());
    }
    @Test void failedReviewRemainsThePrimaryWhenNoRetrySucceeds() {
        var attachment=event("oper-8","review-group","附件新增",true);
        var failed=event("oper-9","review-group","审核报价单",false);
        assertSame(failed,TraceEventAggregator.mergeAttachmentEvents(List.of(attachment,failed)).getFirst());
    }
}
