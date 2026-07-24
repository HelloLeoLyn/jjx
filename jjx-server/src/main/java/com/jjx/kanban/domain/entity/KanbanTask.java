package com.jjx.kanban.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("kanban_task")
public class KanbanTask {
    @TableId(type = IdType.AUTO)
    private Long taskId;
    private String taskCode;
    private String title;
    private String description;
    private String kanbanType;
    private String columnId;
    private String sourceEvent;
    private Long sourceId;
    private String sourceNo;
    private Long assignRole;
    private String priority;
    private String status;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
