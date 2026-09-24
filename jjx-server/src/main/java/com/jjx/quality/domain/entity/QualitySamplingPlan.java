package com.jjx.quality.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抽样方案（AQL）—— dev-20260917-002
 * 来料/成品建批时按 批量区间 匹配：返回 样本量 n / AC / RE。
 *
 * <p><b>dev-20260924-021 去表化</b>：不再落库（原 quality_sampling_plan 表已由迁移 219 停用），
 * 改由系统配置 {@code sys_config(config_group=quality_config, config_key=quality.sampling_plan)}
 * 的一条 JSON 数组承载 —— 依据 CONVENTIONS §14（低行数/低变动的数据优先用配置，不新建表）。
 * 本类退化为纯 POJO（仅作 JSON 载体，不再带 MyBatis 注解）。</p>
 */
@Data
public class QualitySamplingPlan {

    /** 方案 id（配置数组内唯一，非数据库自增） */
    private Long planId;

    private String planName;

    /** IQC / FQC / OQC / ALL */
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

    /** 以下仅作展示留痕用，不进配置 JSON（保持 JSON 精简、避免时间格式差异） */
    @JsonIgnore
    private String createBy;

    @JsonIgnore
    private LocalDateTime createTime;

    @JsonIgnore
    private String updateBy;

    @JsonIgnore
    private LocalDateTime updateTime;
}
