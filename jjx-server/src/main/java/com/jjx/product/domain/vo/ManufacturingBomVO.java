package com.jjx.product.domain.vo;

import com.jjx.engineering.domain.entity.EngineeringBomItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/** 制造 BOM 方案保存/回读视图。 */
@Data
public class ManufacturingBomVO {
    private Long bomId;
    private String bomCode;
    private String bomName;
    private String bomVersion;
    private String outputType;
    private Long outputId;
    private Long productId;
    private Integer approveStatus;
    private Boolean isCurrent;
    private Date effectiveDate;
    private Date expiryDate;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<EngineeringBomItem> items;
    /** 服务端内容指纹（dev-20261111-018）；保存时回传为 expectedContentRevision。 */
    private String contentRevision;
}
