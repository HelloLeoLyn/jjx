package com.jjx.product.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/** 某产出对象的候选制造方案（含草稿标记），供独立窗口选择。 */
@Data
public class ManufacturingSchemeVO {
    private Long bomId;
    private String bomCode;
    private String bomName;
    private String bomVersion;
    private String outputType;
    private Long outputId;
    private Integer approveStatus;
    private Boolean isCurrent;
    /** 当前产品上下文下的主方案标记；productId 为空时为 null。 */
    private Boolean isPrimary;
    private Integer itemCount;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date expiryDate;
    private String remark;
    /** 采用 token：只表达已保存方案，不落库；父 BOM 保存时重新校验。 */
    private String selectionToken;
}
