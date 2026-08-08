package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 销售询价单实体类
 * 薄膜开关ERP系统的客户询价需求录入
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_inquiry")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesInquiry extends BaseEntity {

    /** 询价单ID */
    @TableId(type = IdType.AUTO)
    private Long inquiryId;

    /** 链路追踪ID */
    private String traceId;

    /** 询价单编号 */
    private String inquiryNo;

    /** 客户ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 询价日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate inquiryDate;

    /** 预估数量 */
    private Integer expectedQuantity;

    /** 产品描述/规格要求 */
    private String productDescription;

    /** 关联产品ID（标准品必填） */
    private Long productId;

    /** 产品编码（编码生成器自动生成/标准品带出，可改） */
    private String productCode;

    /** 产品名称（非表字段，查询时关联填充） */
    @TableField(exist = false)
    private String productName;

    /** 按键数量 */
    private Integer keyCount;

    /** 尺寸要求 */
    private String sizeDescription;

    /** 材料要求 */
    private String materialRequirements;

    /** 线路要求 */
    private String circuitRequirements;

    /** 连接器要求 */
    private String connectorRequirements;

    /** 特殊要求 */
    private String specialRequirements;

    /** 是否有图纸文件 */
    private Integer hasDrawing;

    /** 状态: 0草稿/1待处理/2已发送/3已转报价/4已确认/5已拒绝/6已过期 */
    private Integer inquiryStatus;

    /** 询价类型: 1标准品 2样品 */
    private Integer inquiryType;

    /** 转报价单ID */
    private Long convertedQuotationId;

    /** 转换时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime convertTime;

    /** 备注 */
    private String remark;

    /** 销售负责人ID */
    private Long salesPersonId;

    /** 销售负责人姓名 */
    private String salesPersonName;

    /** 查询参数：开始日期（非表字段） */
    @TableField(exist = false)
    private LocalDate startDate;

    /** 查询参数：结束日期（非表字段） */
    @TableField(exist = false)
    private LocalDate endDate;

    /** 删除标志 (0: 正常, 1: 删除) */
    @TableLogic
    private Integer deleted;
}
