package com.jjx.product.domain.vo;

import com.jjx.engineering.domain.entity.EngineeringBomItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * BOM VO
 */
@Data
public class EngineeringBomVO {

    /** BOM ID */
    private Long bomId;

    /** BOM编码 */
    private String bomCode;

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 版本号 */
    private String bomVersion;

    /**
     * 版本号（V1.0/V2.0，版本化统一字段）
     */
    private String version;

    /** BOM类型 */
    private String bomType;

    /** 是否当前版本 */
    private Boolean isCurrent;

    /** 生效日期 */
    private Date effectiveDate;

    /** 失效日期 */
    private Date expiryDate;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 创建时间 */
    private String createBy;

    /** 更新时间 */
    private String updateBy;

    /** 审批状态 */
    private Integer approveStatus;

    /** 审批状态 */
    private String approveRemark;

    /** 备注 */
    private String remark;

    /** 明细 */
    private List<EngineeringBomItem> items;
}
