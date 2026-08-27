package com.jjx.production.service;

import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityJudgeDTO;
import com.jjx.production.domain.vo.QualityInspectionVO;

/**
 * 质检正式动作（P3-C）
 *
 * 原则：
 *  - QualityInspection 是质量事实，WorkReport 是生产申报事实，两者禁止互相覆盖。
 *  - 已判定(PASS/FAIL) 的 QualityInspection 不可修改结果/数量；复检必须新建记录。
 *  - FQC 在最后有效 Execution 完成后自动创建 PENDING；Order complete 前必须存在当前有效的 FQC PASS。
 *  - FQC PASS → ProductionOrder.finishedQuantity = FQC.passQuantity（不覆盖 WorkReport/Execution qualified）。
 *  - FQC FAIL → Order 不允许完成；最后有效 Execution 恢复到可继续生产/报工的现有状态（EXECUTING）。
 *  - IPQC V1 人工创建，不自动触发。
 */
public interface QualityActionService {

    /**
     * 创建质检（人工创建 IPQC/FQC 等；workReportId 非空时后端反查 WorkReport 校验一致性）
     */
    Long createInspection(QualityInspectionCreateDTO dto);

    /**
     * 判定 PASS / FAIL（不可变：已判定后禁止再次判定；复检走 reinspect）
     */
    QualityInspectionVO judge(Long inspectionId, QualityJudgeDTO dto);

    /**
     * 复检：复制原单上下文创建一条新的 PENDING 质检单（不覆盖历史）
     */
    Long reinspect(Long inspectionId);

    /**
     * 最后有效 Execution 完成后自动创建 PENDING FQC（幂等：同 execution 已有 PENDING FQC 时不重复创建）
     *
     * @return 新质检单ID；已有 PENDING 时返回 null
     */
    Long createFqcForExecution(Long executionId);
}
