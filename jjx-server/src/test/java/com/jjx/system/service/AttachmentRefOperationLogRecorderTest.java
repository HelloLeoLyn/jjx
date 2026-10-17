package com.jjx.system.service;

import cn.hutool.json.JSONUtil;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysOperLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttachmentRefOperationLogRecorderTest {
    private final LogSaveService logSave = mock(LogSaveService.class);
    private final AttachmentRefOperationLogRecorder recorder = new AttachmentRefOperationLogRecorder(logSave);

    @AfterEach
    void cleanupTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void recordsOnlyAfterCommitAndGroupsChangesForOneBusinessDocument() {
        TransactionSynchronizationManager.initSynchronization();
        recorder.record("quotation", 41L, "trace-41", BusinessType.UPDATE.getCode(),
            "附件资料修改", 101L, 201L, "a.pdf", true, List.of("资料类型：空 → 图纸"));
        recorder.record("quotation", 41L, "trace-41", BusinessType.UPDATE.getCode(),
            "附件资料修改", 102L, 202L, "b.pdf", true, List.of("备注：空 → 客供"));

        verifyNoInteractions(logSave);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        @SuppressWarnings("unchecked")
        var eventsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(logSave).saveOperLogsInOrder(eventsCaptor.capture());
        List<SysOperLog> events = (List<SysOperLog>) eventsCaptor.getValue();
        assertEquals(1, events.size());
        SysOperLog event = events.get(0);
        assertEquals("quotation", event.getBizType());
        assertEquals("41", event.getBizId());
        assertEquals("trace-41", event.getTraceId());
        assertEquals("附件资料修改", event.getAction());
        var detail = JSONUtil.parseObj(event.getDetail());
        assertEquals(2, detail.getJSONArray("attachments").size());
        assertEquals(2, detail.getJSONArray("changes").size());
        assertEquals("a.pdf", detail.getJSONArray("attachments").getJSONObject(0).getStr("fileName"));
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void discardsPendingBusinessEventsWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        recorder.record("inquiry", 7L, "trace-7", BusinessType.INSERT.getCode(),
            "附件新增", 11L, 21L, "drawing.pdf", true, List.of("新增附件：drawing.pdf"));
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(logSave);
    }
}
