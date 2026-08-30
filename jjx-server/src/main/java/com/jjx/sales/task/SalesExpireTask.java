package com.jjx.sales.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.sales.domain.entity.SalesInquiry;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.enums.InquiryStatus;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesInquiryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 销售单据过期定时任务（DEV-589）
 * <ul>
 *   <li>报价单：valid_until 早于今天且仍处于活动状态 → 置为已过期(4)</li>
 *   <li>询价单：询价日期超过 90 天且仍处于活动状态 → 置为已过期(6)</li>
 * </ul>
 * 说明：到期日当天仍视为有效，次日凌晨执行时过期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesExpireTask {

    private final QuotationMapper quotationMapper;
    private final SalesInquiryMapper inquiryMapper;

    /** 询价单无有效期字段，按询价日期 + 90 天近似过期 */
    private static final int INQUIRY_VALID_DAYS = 90;

    /** 每天凌晨 1:30 过期报价单 */
    @Scheduled(cron = "0 30 1 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void expireQuotations() {
        List<Integer> activeStatuses = List.of(
                QuotationStatus.DRAFT.getValue(),          // 草稿
                QuotationStatus.SENT.getValue(),           // 已发送
                QuotationStatus.PENDING_REVIEW.getValue(), // 待审核
                QuotationStatus.MODIFYING.getValue());     // 改单
        LambdaUpdateWrapper<SalesQuotation> wrapper = Wrappers.lambdaUpdate();
        wrapper.lt(SalesQuotation::getValidUntil, LocalDate.now())
                .in(SalesQuotation::getQuotationStatus, activeStatuses)
                .eq(SalesQuotation::getDeleted, 0)
                .set(SalesQuotation::getQuotationStatus, QuotationStatus.EXPIRED.getValue());
        int rows = quotationMapper.update(null, wrapper);
        if (rows > 0) {
            log.info("[过期任务] 报价单置为已过期 {} 条", rows);
        }
    }

    /** 每天凌晨 1:35 过期询价单 */
    @Scheduled(cron = "0 35 1 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void expireInquiries() {
        List<Integer> activeStatuses = List.of(
                InquiryStatus.DRAFT.getValue(),   // 草稿
                InquiryStatus.PENDING.getValue(), // 待处理
                InquiryStatus.SENT.getValue());   // 已发送
        LambdaUpdateWrapper<SalesInquiry> wrapper = Wrappers.lambdaUpdate();
        wrapper.lt(SalesInquiry::getInquiryDate, LocalDate.now().minusDays(INQUIRY_VALID_DAYS))
                .in(SalesInquiry::getInquiryStatus, activeStatuses)
                .eq(SalesInquiry::getDeleted, 0)
                .set(SalesInquiry::getInquiryStatus, InquiryStatus.EXPIRED.getValue());
        int rows = inquiryMapper.update(null, wrapper);
        if (rows > 0) {
            log.info("[过期任务] 询价单置为已过期 {} 条", rows);
        }
    }
}
