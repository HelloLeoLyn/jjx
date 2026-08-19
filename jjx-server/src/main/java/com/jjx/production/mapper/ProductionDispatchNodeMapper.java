package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionDispatchNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 派工责任链节点 Mapper
 * P1-A 仅提供基础查询能力（按 dispatch / 状态 / parent 查询），
 * 完整业务动作（ASSIGN/DELEGATE/REASSIGN/RETURN）由 P1-C Service 层实现。
 */
@Mapper
public interface ProductionDispatchNodeMapper extends BaseMapper<ProductionDispatchNode> {
}
