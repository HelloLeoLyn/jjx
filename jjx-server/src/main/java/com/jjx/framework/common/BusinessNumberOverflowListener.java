package com.jjx.framework.common;

import com.jjx.notification.domain.dto.NotifyTaskDTO;
import com.jjx.notification.service.NotifyTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 将编号进位告警转成站内通知和待办；告警失败不得阻断业务单据创建。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessNumberOverflowListener {

    private final NotifyTaskService notifyTaskService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOverflow(BusinessNumberOverflowEvent event) {
        try {
            NotifyTaskDTO dto = new NotifyTaskDTO();
            dto.setTitle("业务编号流水已自动进位：" + event.bizType());
            dto.setContent("编号类型=" + event.bizType()
                    + "，周期=" + event.periodKey()
                    + "，配置位数=" + event.configuredDigits()
                    + "，当前位数=" + event.actualDigits()
                    + "，当前流水=" + event.sequence()
                    + "。请评估是否调大 biz_no_rule 配置位数。");
            dto.setBizType("business_number_rule");
            dto.setPriority("high");
            dto.setKanbanModule("dev");
            notifyTaskService.notifyAndCreateTask(dto);
        } catch (Exception ex) {
            log.error("业务编号进位通知失败，但不影响业务单据创建: bizType={}, period={}, sequence={}",
                    event.bizType(), event.periodKey(), event.sequence(), ex);
        }
    }
}
