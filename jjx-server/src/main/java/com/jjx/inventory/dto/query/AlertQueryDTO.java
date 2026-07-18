package com.jjx.inventory.dto.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 预警查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AlertQueryDTO extends Page<Object> {

    private Long alertId;
    private String alertType;
    private String alertLevel;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String status;
    private LocalDateTime alertTimeStart;
    private LocalDateTime alertTimeEnd;
    private String orderBy;
    private String orderDirection;
}
