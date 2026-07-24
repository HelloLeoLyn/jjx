package com.jjx.engineering.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("engineering_bom")
public class Bom {
    @TableId(type = IdType.AUTO)
    private Long bomId;
    private String bomCode;
    private String bomName;
    private Long productId;
    private String bomVersion;
    private String bomType;
    private Integer isCurrent;
    private Long approveStatus;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
