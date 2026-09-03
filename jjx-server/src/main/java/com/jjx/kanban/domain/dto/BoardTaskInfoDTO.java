package com.jjx.kanban.domain.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 看板任务内容更新 DTO
 */
@Data
public class BoardTaskInfoDTO {
    private String title;
    private String description;
    private String priority;
    private String assigneeName;
    private LocalDate deadline;
    private String remark;
}
