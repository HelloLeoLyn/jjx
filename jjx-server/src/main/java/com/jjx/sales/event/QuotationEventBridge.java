package com.jjx.sales.event;

import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.service.NotificationService;
import com.jjx.sales.domain.entity.SalesInquiry;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesInquiryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 询价/报价事件联动桥接器（DEV-592）
 * 监听询价/报价事件 → 通过 sys_notification 通知对应销售负责人
 * 事件无消费者问题修复：inquiry.converted / quotation.confirmed / rejected / reviewed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotationEventBridge {

    private final NotificationService notificationService;
    private final QuotationMapper quotationMapper;
    private final SalesInquiryMapper inquiryMapper;

    /** 客户确认报价 → 通知销售可转订单/样品单 */
    @EventListener(condition = "#payload?.eventCode == 'quotation.confirmed'")
    public void onQuotationConfirmed(Map<String, Object> payload) {
        notifyQuotationSales(payload, "报价单已获客户确认", "客户已确认报价，可转为订单或样品单");
    }

    /** 客户拒绝报价 → 通知销售可重新报价 */
    @EventListener(condition = "#payload?.eventCode == 'quotation.rejected'")
    public void onQuotationRejected(Map<String, Object> payload) {
        notifyQuotationSales(payload, "报价单已被客户拒绝", "客户已拒绝报价，可修改后重新报价");
    }

    /** 报价审核（通过/驳回）→ 通知销售 */
    @EventListener(condition = "#payload?.eventCode == 'quotation.reviewed'")
    public void onQuotationReviewed(Map<String, Object> payload) {
        Object approved = payload.get("approved");
        boolean pass = approved != null && Boolean.parseBoolean(String.valueOf(approved));
        notifyQuotationSales(payload,
                pass ? "报价单审核通过" : "报价单审核被驳回",
                pass ? "报价已审核通过，可发送报价给客户" : "报价审核被驳回，请查看驳回原因并修改后重新提交");
    }

    /** 询价转报价 → 通知销售补全明细并定价 */
    @EventListener(condition = "#payload?.eventCode == 'inquiry.converted'")
    public void onInquiryConverted(Map<String, Object> payload) {
        try {
            Object inquiryIdObj = payload.get("inquiryId");
            if (inquiryIdObj == null) return;
            SalesInquiry inquiry = inquiryMapper.selectById(Long.valueOf(inquiryIdObj.toString()));
            if (inquiry == null || inquiry.getSalesPersonId() == null) return;
            send(getTriggerUserId(payload), inquiry.getSalesPersonId(), inquiry.getSalesPersonName(),
                    "询价单已转报价", "询价单[" + inquiry.getInquiryNo() + "]已转报价，请补全报价明细并定价",
                    "inquiry", String.valueOf(inquiry.getInquiryId()));
        } catch (Exception e) {
            log.error("询价转报价通知失败: {}", e.getMessage());
        }
    }

    /** 报价单类事件：按 bizId 查报价单，通知销售负责人 */
    private void notifyQuotationSales(Map<String, Object> payload, String title, String content) {
        try {
            Object bizId = payload.get("bizId");
            if (bizId == null) return;
            SalesQuotation quotation = quotationMapper.selectById(Long.valueOf(bizId.toString()));
            if (quotation == null || quotation.getSalesPersonId() == null) return;
            send(getTriggerUserId(payload), quotation.getSalesPersonId(), quotation.getSalesPersonName(),
                    title, content + "，报价单[" + quotation.getQuotationNo() + "]",
                    "quotation", String.valueOf(quotation.getQuotationId()));
        } catch (Exception e) {
            log.error("报价单通知失败: {}", e.getMessage());
        }
    }

    private Long getTriggerUserId(Map<String, Object> payload) {
        Object v = payload.get("triggerUserId");
        return v != null ? Long.valueOf(v.toString()) : null;
    }

    private void send(Long triggerUserId, Long receiverId, String receiverName,
                      String title, String content, String bizType, String bizId) {
        // 自己操作自己，不重复通知
        if (receiverId != null && receiverId.equals(triggerUserId)) {
            return;
        }
        NotificationCreateDTO dto = new NotificationCreateDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setNotificationType("SYSTEM");
        dto.setBizType(bizType);
        dto.setBizId(bizId);
        dto.setReceiverId(receiverId);
        dto.setReceiverName(receiverName);
        dto.setPriority("NORMAL");
        notificationService.createNotification(dto);
    }
}
