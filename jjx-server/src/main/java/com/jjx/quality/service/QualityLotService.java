package com.jjx.quality.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityLotItem;
import com.jjx.quality.dto.QualityLotCreateDTO;
import com.jjx.quality.dto.QualityLotItemDTO;
import com.jjx.quality.dto.QualityLotQueryDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 检验批服务 —— dev-20260917-001
 * 统一承载 IQC/IPQC/FQC 的检验批：建批、分批查询、录入项、判定、入库累计（防超入）
 */
public interface QualityLotService {

    /** 建批（支持同一来源多次建批 = 分批；复检传 parentLotId + version） */
    QualityLot createLot(QualityLotCreateDTO dto);

    QualityLot getLot(Long lotId);

    /**
     * dev-20260922-030（用户拍板 A）：重开已关闭的检验批。
     * 批在「合格全部入库 + 不良全部处置」后会自动 CLOSED，之后无法再复检；
     * 业务上入库后仍可能发现问题，故提供显式重开（必须填原因，留痕），状态 CLOSED → JUDGED，之后可正常复检。
     */
    QualityLot reopenLot(Long lotId, String reason);

    /** 同一来源的所有批（分批/复检历史，按版本与创建时间排序） */
    List<QualityLot> listBySource(String sourceType, Long sourceId);

    /** 工单/工序下的所有批（成品侧看 已检/待检 用） */
    List<QualityLot> listByOrder(Long orderId, Long executionId);

    IPage<QualityLot> pageLots(QualityLotQueryDTO query);

    List<QualityLotItem> listItems(Long lotId);

    /** 覆盖式保存检验项（录入/补录） */
    void saveItems(Long lotId, List<QualityLotItemDTO> items);

    /**
     * 行锁读取检验批（SELECT ... FOR UPDATE，必须在事务内）——写操作前先取锁串行化并发
     * （2026-09-21 dev-20260921-030）
     */
    QualityLot lockLot(Long lotId);

    /** 是否为该来源的最新版本（无后继复检版本）——评定/录入/复检只允许作用于最新版 */
    boolean isLatestVersion(Long lotId);

    /**
     * 判定落数：写 已检/合格/不良/判定/检验员，并推进状态
     * 校验：已检 ≤ 批量；合格 + 不良 = 已检；**合格 ≤ 可判合格上界**（dev-20260923-021：已报废/让步未确认量不得重判为良品）
     */
    QualityLot applyJudgement(Long lotId, BigDecimal inspectedQuantity, BigDecimal passQuantity,
                              BigDecimal failQuantity, String result, String inspector);

    /**
     * 判定护栏（dev-20260923-021 一期）：可判合格上界 = 批批量 − 链上已报废未回收 − 让步未客户确认。
     * 数据实时按 quality_ncr_action 汇总（不落冗余列）；查询异常时降级（guardAvailable=false，不阻塞）。
     */
    com.jjx.quality.dto.vo.JudgementGuardVO evaluateJudgementGuard(Long lotId);

    /**
     * 入库/放行累计（防超入守卫）：stored + delta ≤ 批量，否则报错
     */
    QualityLot addStoredQuantity(Long lotId, BigDecimal delta);

    /** 不良处置累计（返工/让步/报废都会调用） */
    QualityLot addDisposedQuantity(Long lotId, BigDecimal delta);

    /**
     * 工单完工口径汇总（dev-20260918-014）：有效 FQC 批（无后继复检版本）的 待检张数 / 合格累计 / 未处置不良。
     * 与 {@code QualityFinishServiceImpl.syncFinishInbound} 同一口径，供工单完工门禁与成品数量回写共用。
     */
    com.jjx.quality.dto.FqcCompletionSummary summarizeEffectiveFqc(Long orderId);

    /**
     * 完工入库过账后回写 → dev-20260918-022/023：
     * 有效 FQC 批（已判定）stored_quantity 置为该批合格数；当"合格全部入库且不良全部处置"置 CLOSED。
     * @return 处理的批数
     */
    int markOrderFinishedStored(Long orderId);
}
