package com.jjx.quality.service;

import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.dto.QualityLotJudgeDTO;

import java.math.BigDecimal;

/**
 * 成品检验（FQC）收口服务 —— dev-20260917-007
 *
 * 口径：判定与账务分离；入库只做「已检合格 − 已入库」的差额；复检=同批新版本（替换判定，不新增产出）。
 */
public interface QualityFinishService {

    /** 判定检验批：落数 + 不良进台账 +（成品）按差额同步完工入库 */
    QualityLot judgeLot(Long lotId, QualityLotJudgeDTO dto);

    /** 复检：对同一批生成新版本（旧版本保留可查但不再计账） */
    QualityLot reinspectLot(Long lotId);

    /**
     * 按检验批重算工单成品应入库数并做差额同步（同一工单只维护一张完工入库单）
     * @return 本次差额（可为负）
     */
    BigDecimal syncFinishInbound(Long orderId, String reason);
}
