package com.jjx.product.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "菲林VO")
public class ProductFilmVO {
    
    private Long filmId;
    private String filmCode;
    private String filmName;
    private String filmType;
    private String filmTypeName;
    private Long productId;
    private String productCode;
    private String productName;
    private String version;
    private Integer isCurrent;
    private String isCurrentName;
    private Long parentFilmId;
    private String filmSize;
    private BigDecimal filmThickness;
    private String filmMaterial;
    private String color;
    private Long fileId;
    private String filePath;
    private String fileName;
    private String technicalSpec;
    private String designNotes;
    private Long processId;
    private String processCode;
    private Integer approveStatus;
    private String approveStatusName;
    private Long approverId;
    private String approverName;
    private LocalDateTime approveTime;
    private String approveRemark;
    private Long designerId;
    private String designerName;
    private LocalDateTime designTime;
    private Integer isReleased;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;
}