package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
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
public class SalesInquiry extends BaseEntity {

    /** 询价单ID */
    @TableId(type = IdType.AUTO)
    private Long inquiryId;

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

    /** 状态: draft/pending/sent/accepted/rejected/converted */
    private String inquiryStatus;

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

    /** 删除标志 (0: 正常, 1: 删除) */
    @TableLogic
    private Integer deleted;
}
