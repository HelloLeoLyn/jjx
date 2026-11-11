package com.jjx.production.service;

import com.jjx.production.domain.dto.RequirementPreviewRequest;
import com.jjx.production.domain.vo.RequirementSummaryVO;

/**
 * 多层制造需求的统一后端能力（dev-20261111-006）。
 *
 * <p>本阶段只提供只读 preview：按显式 sub_bom_id 展开、精确有理数计算、跨层汇齐后做供给净额。
 * 持久确认/并发分配未实现，不能从任何入口返回假的 confirm 成功。</p>
 */
public interface ManufacturingRequirementService {

    RequirementSummaryVO preview(RequirementPreviewRequest request);
}
