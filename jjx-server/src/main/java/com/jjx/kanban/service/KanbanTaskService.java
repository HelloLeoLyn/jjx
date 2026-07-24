package com.jjx.kanban.service;
import com.jjx.kanban.domain.entity.KanbanTask;
public interface KanbanTaskService {
    Long createTask(KanbanTask task);
    boolean updateTask(KanbanTask task);
    KanbanTask getById(Long id);
}
