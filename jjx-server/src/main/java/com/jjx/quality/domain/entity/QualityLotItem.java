package com.jjx.quality.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验批检测项目结果 —— dev-20260917-001
 * sample_values 用 | 分隔保存逐件实测值，供报告 SAMPLE1..n 逐件展示（报告补齐见 012）。
 */
@Data
@TableName("quality_lot_item")
public class QualityLotItem {

    @TableId(type = IdType.AUTO)
    private Long itemId;

    private Long lotId;

    private String checkItem;
    private String standard;
    private String inspectionMethod;
    private String equipment;

    /** 逐件实测值（| 分隔） */
    private String sampleValues;
    /** 实测值（汇总/兼容） */
    private String actualValue;

    private BigDecimal crQuantity;
    private BigDecimal maQuantity;
    private BigDecimal miQuantity;

    /** pass/fail/pending */
    private String result;

    private String remark;
    private Integer sortOrder;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
