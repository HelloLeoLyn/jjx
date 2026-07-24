package com.jjx.kanban.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.kanban.domain.entity.KanbanTask;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface KanbanTaskMapper extends BaseMapper<KanbanTask> {}
