package com.jjx.quality.service;

import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.dto.vo.QualityNcrPieceVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 不良件级追溯服务 —— dev-20260924-004（报废线一期）。
 *
 * <p>方案：jjx-docs/design/defect-piece-trace-reason-code-dev-20260924-004.md（v3）</p>
 * <ul>
 *   <li>件表/缺陷记录表只做身份与追溯，**不参与任何库存数量计算**（库存仍只由 inventory_transaction 驱动）</li>
 *   <li>原因 = 检验项目（quality_lot_item.check_item）+ 不合格分级（CR/MA/MI）；不建全局原因字典</li>
 *   <li>件是处置的最小单位（一件一个决定）；一件可多条缺陷记录</li>
 * </ul>
 */
public interface QualityNcrPieceService {

    /**
     * 判定后按件发号（幂等：同一不良单只按需补齐到 N 件，不重复发）。
     * 同时落缺陷记录（来自检验单项目计数；无项目明细时落「其他」兜底），并回填不良单首因。
     *
     * @return 本次新发件数（0 = 无需新发）
     */
    int issuePieces(QualityNcr ncr);

    /**
     * 件级处置：把该不良单「待处置」的件按序号挂到处置单上（件数 = 本次处置数量）。
     *
     * @param disposeType REWORK/CONCESSION/SCRAP/RETURN
     * @return 实际挂上的件数
     */
    int attachPieces(Long ncrId, Long actionId, String disposeType, BigDecimal quantity);

    /** 撤销处置：该处置单上的件回到「待处置」（与处置单撤销同事务） */
    int releasePieces(Long actionId);

    /** 随批作废：未处置/返工中的件置 VOID（禁止再处置） */
    int voidOpenPieces(Long ncrId);

    /** 件列表（含缺陷记录）；无件时返回空列表 */
    List<QualityNcrPieceVO> listPieces(Long ncrId);

    /** 检验单项目不合格合计 Σ(CR+MA+MI) —— 判定「不良数量 N」的联动校验基准 */
    BigDecimal sumLotItemDefects(Long lotId);
}
