package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打样BOM物料清单（结构化）
 * 打样过程的层结构物料数据，转量产时生成BOM草稿依据（DEV-455）
 */
@Data
@TableName("sales_sample_bom")
public class SalesSampleBom {

    /** 打样BOM记录ID */
    @TableId(type = IdType.AUTO)
    private Long bomId;

    /** 样品单ID(sales_order.order_id) */
    private Long orderId;

    /** 打样轮次 */
    private Integer roundNo;

    /** 层结构(面板/线路/间隔/背胶/其他) */
    private String layerName;

    /** 物料名称 */
    private String materialName;

    /** 规格 */
    private String specification;

    /** 用量 */
    private BigDecimal quantity;

    /** 单位 */
    private String unit;

    /** 备注 */
    private String remark;

    /** 录入人 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
