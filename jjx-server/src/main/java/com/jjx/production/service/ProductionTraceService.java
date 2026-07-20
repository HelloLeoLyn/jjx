package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.TraceQueryDTO;
import com.jjx.production.domain.vo.TraceVO;

import java.util.List;

public interface ProductionTraceService {
    /** 分页查询追溯记录 */
    PageResult<TraceVO> page(TraceQueryDTO query);
    /** 按追溯编码查询（正追溯） */
    List<TraceVO> traceForward(String traceCode);
    /** 按追溯编码查询（反追溯） */
    List<TraceVO> traceBackward(String traceCode);
}
