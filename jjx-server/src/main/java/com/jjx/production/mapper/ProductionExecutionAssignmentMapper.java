package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionExecutionAssignment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工序作业分配 Mapper（WP-B）
 */
@Mapper
public interface ProductionExecutionAssignmentMapper extends BaseMapper<ProductionExecutionAssignment> {
}
