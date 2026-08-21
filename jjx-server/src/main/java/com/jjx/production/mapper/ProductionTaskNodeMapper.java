package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionTaskNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产任务树节点 Mapper
 * BaseMapper 足够：按 executionId / parentNodeId 查询；FOR UPDATE 行锁由 Service 通过 wrapper last() 使用。
 */
@Mapper
public interface ProductionTaskNodeMapper extends BaseMapper<ProductionTaskNode> {
}
