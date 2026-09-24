package com.jjx.quality.service;

import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.domain.entity.QualityNcrAction;
import com.jjx.quality.domain.entity.QualityScrapOrder;

import java.util.List;

/**
 * 成品报废单服务 —— dev-20260924-006（报废线三期）。
 *
 * <p>形态对齐来料 {@code inventory_iqc_scrap_order}：一笔报废处置 → 一张报废单（幂等 uk(action_id)）；
 * 报废被撤销 → 单据 VOID。报废单只是凭据，**库存不受影响**。</p>
 */
public interface QualityScrapOrderService {

    /**
     * 某笔报废处置生效时出单（幂等：同一 action 只出一张；已存在直接返回既有单）。
     *
     * @return 报废单ID（失败返回 null，不阻断处置主流程）
     */
    Long createForAction(QualityNcrAction action, QualityNcr ncr);

    /** 处置撤销联动：把该处置单的报废单置 VOID（无单时返回 0） */
    int voidByAction(Long actionId);

    /** 列表（按 单号/不良单号/工单号 关键字；倒序；limit 200） */
    List<QualityScrapOrder> list(String keyword);

    /** 详情 */
    QualityScrapOrder detail(Long scrapId);
}
