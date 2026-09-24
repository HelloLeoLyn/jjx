package com.jjx.quality.service;

import com.jjx.quality.dto.QualityLotCreateDTO;

/**
 * 出货检验（OQC）服务 —— dev-20260921-042（一期：补齐建批口径，不动检验批模型）。
 *
 * <p>口径（2026-09-24 口径收敛）：检验批模型、检验项目、缺陷记录、件级追溯**以既有模型为唯一出处**
 * （见 design/defect-piece-trace-reason-code-dev-20260924-004.md）；OQC 只负责：</p>
 * <ol>
 *   <li>建批时按批量匹配**抽样方案**（quality_sampling_plan.lot_type='OQC'，AQL 配置现成）</li>
 *   <li>预填**检验项目**（复制该产品最近一次 FQC 批的项目，避免质检员从头手录）</li>
 * </ol>
 *
 * <p>判定/复检/不良处置/件级发号 全部复用既有通道，不新增第三套记录。</p>
 */
public interface QualityOqcService {

    /**
     * OQC 建批前准备（幂等/可重复调用）：挂抽样方案 + 预填检验项目。
     * 非 OQC 批直接跳过（不改变其它类型行为）。
     */
    void prepareLot(QualityLotCreateDTO dto);
}
