package com.jjx.production.service;

import com.jjx.production.domain.vo.ReworkTraceVO;

import java.util.List;

/**
 * 返工链投影查询（只读）—— dev-20260923-031。
 *
 * <p>三个入参任选其一（orderId / executionId / ncrId），用于不同页面：
 * 工序执行页按 orderId（整单一次拿全，前端按 executionId 贴标签）、报工/工序详情按 executionId、
 * 不良台账按 ncrId。</p>
 */
public interface ProductionReworkTraceService {

    List<ReworkTraceVO> trace(Long orderId, Long executionId, Long ncrId);
}
