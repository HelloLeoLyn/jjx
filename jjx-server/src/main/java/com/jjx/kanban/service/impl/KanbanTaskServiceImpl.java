package com.jjx.kanban.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.kanban.domain.entity.KanbanTask;
import com.jjx.kanban.mapper.KanbanTaskMapper;
import com.jjx.kanban.service.KanbanTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class KanbanTaskServiceImpl extends ServiceImpl<KanbanTaskMapper, KanbanTask> implements KanbanTaskService {
    private final KanbanTaskMapper taskMapper;
    @Override
    public Long createTask(KanbanTask task) { taskMapper.insert(task); return task.getTaskId(); }
    @Override
    public boolean updateTask(KanbanTask task) { return taskMapper.updateById(task) > 0; }
    @Override
    public KanbanTask getById(Long id) { return taskMapper.selectById(id); }
}
