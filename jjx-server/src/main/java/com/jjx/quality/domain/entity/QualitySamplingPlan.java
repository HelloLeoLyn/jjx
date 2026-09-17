package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抽样方案（AQL）—— dev-20260917-002
 * 来料建批时按 批量区间 匹配：返回 样本量 n / AC / RE；成品全检不使用。
 */
@Data
@TableName("quality_sampling_plan")
public class QualitySamplingPlan {

    @TableId(type = IdType.AUTO)
    private Long planId;

    private String planName;
    /** IQC/FQC/ALL */
    private String lotType;
    private BigDecimal aqlValue;
    private String inspectionLevel;
    /** 批量区间（含边界） */
    private BigDecimal lotMin;
    private BigDecimal lotMax;
    private BigDecimal sampleQuantity;
    private BigDecimal acceptNumber;
    private BigDecimal rejectNumber;
    private Integer isEnabled;
    private String remark;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer delFlag;
}
