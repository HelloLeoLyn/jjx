package com.jjx.engineering.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 产品BOM实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("engineering_bom")
public class EngineeringBom {

    /**
     * BOM ID
     */
    @TableId(type = IdType.AUTO)
    private Long bomId;

    /**
     * BOM编码
     */
    private String bomCode;

    /**
     * BOM名称
     */
    private String bomName;

    /** bom类型：engineering工程/manufacturing制造 */
    private String bomType;

    /**
     * Bom版本
     */
    private String bomVersion;

    /**
     * 版本号（V1.0/V2.0...，版本化改造新增）
     */
    private String version;

    /**
     * 来源打样单ID
     */
    private Long sourceSampleId;

    /**
     * 父版本BOM ID
     */
    private Long parentBomId;


    /**
     * 产品ID
     */
    private Long productId;


    /**
     * 审核状态
     */
    private Integer approveStatus;

    /**
     * 审核批注
     */
    private String approveRemark;


    /**
     * 是否当前版本
     */
    private Boolean isCurrent;

    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

    /** 启用日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date effectiveDate;

    /** 失效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expiryDate;
}
