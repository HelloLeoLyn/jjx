package com.jjx.production.domain.dto;

import lombok.Data;

/**
 * 释放作业剩余入参（WP-B）
 */
@Data
public class AssignmentReleaseDTO {

    /** 释放原因（必填） */
    private String reason;
}
