package com.jjx.production.service;

import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityJudgeDTO;
import com.jjx.production.domain.vo.QualityInspectionVO;

/**
 * 质检正式动作（P3-C）—— dev-20260918-017/018 收敛为「IQC/IPQC 专用」
 *
 * 原则：
 *  - QualityInspection 是质量事实，WorkReport 是生产申报事实，两者禁止互相覆盖。
 *  - 已判定(PASS/FAIL) 的 QualityInspection 不可修改结果/数量；复检必须新建记录。
 *  - IPQC V1 人工创建，不自动触发。
 *
 * 2026-09-18（质量归一·阶段1）：
 *  - 旧 FQC 已下线，成品检验统一走 quality_lot 检验批；本服务不再承载 FQC 建单/判定/处置。
 *    （原 createFqcForExecution / disposeFqcFailure 已删除）
 *  - IQC 仍依赖本服务的 createInspection / judge / reinspect。
 */
public interface QualityActionService {

    /**
     * 创建质检（人工创建 IPQC 等；workReportId 非空时后端反查 WorkReport 校验一致性）。
     * 传入 FQC 类型将被拒绝（旧 FQC 已下线）。
     */
    Long createInspection(QualityInspectionCreateDTO dto);

    /**
     * 判定 PASS / FAIL（不可变：已判定后禁止再次判定；复检走 reinspect）。
     * 对 FQC 类型显式拒绝（旧 FQC 已下线）。
     */
    QualityInspectionVO judge(Long inspectionId, QualityJudgeDTO dto);

    /**
     * 复检：复制原单上下文创建一条新的 PENDING 质检单（不覆盖历史）。
     * 对 FQC 类型显式拒绝（旧 FQC 已下线）。
     */
    Long reinspect(Long inspectionId);
}
