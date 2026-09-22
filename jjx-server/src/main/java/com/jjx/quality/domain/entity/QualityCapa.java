package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("quality_capa")
public class QualityCapa {
    @TableId(type = IdType.AUTO) private Long capaId;
    private String capaNo;
    private Long ncrId;
    private String rootCauseCategory;
    private String rootCause;
    private String actionPlan;
    private Long ownerId;
    private String ownerName;
    private LocalDate dueDate;
    private String verificationResult;
    private String status;
    private LocalDateTime closedTime;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    @TableLogic private Integer delFlag;
}
