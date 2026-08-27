package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionTaskEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产任务流转事件 Mapper（P2 Task Flow 业务流水）
 */
@Mapper
public interface ProductionTaskEventMapper extends BaseMapper<ProductionTaskEvent> {
}
