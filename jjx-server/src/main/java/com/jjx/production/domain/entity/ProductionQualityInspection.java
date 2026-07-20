package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("production_quality_inspection")
public class ProductionQualityInspection {
    @TableId(type = IdType.AUTO)
    private Long inspectionId;
    private String inspectionNo;
    private String inspectionType;
    private Long orderId;
    private Long materialId;
    private Long productId;
    private String inspector;
    private LocalDateTime inspectTime;
    private String result;
    private Integer totalQty;
    private Integer passQty;
    private Integer failQty;
    private String defectDesc;
    private String remark;
    @TableLogic
    private String delFlag;
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
