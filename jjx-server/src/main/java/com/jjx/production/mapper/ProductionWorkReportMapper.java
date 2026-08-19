package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionWorkReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产报工 Mapper
 * P2-B 仅提供基础查询能力（BaseMapper + Wrapper 足够：按 executionId/reportStatus/dispatchNodeId/reporterId/reportId），
 * 不提前写大量 XML。SUBMIT/CANCEL 动作属 P2-C。
 */
@Mapper
public interface ProductionWorkReportMapper extends BaseMapper<ProductionWorkReport> {
}
