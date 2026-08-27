package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_flow")
public class ReviewFlow {
    @TableId(value = "flow_id", type = IdType.AUTO)
    private Long flowId;
    private String bizType;
    private Long bizId;
    private Integer roundNo;
    private String actionCode;
    private String actionName;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private String comment;
    private String attachmentIds;
    private String extraJson;
    private String createBy;
    private LocalDateTime createTime;
}
