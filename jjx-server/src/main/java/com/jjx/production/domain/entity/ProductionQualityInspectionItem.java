package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("production_quality_inspection_item")
public class ProductionQualityInspectionItem {
    @TableId(type = IdType.AUTO)
    private Long itemId;
    private Long inspectionId;
    private String checkItem;
    private String standard;
    private String inspectionMethod;
    private String equipment;
    private String actualValue;
    private String result;
    private java.math.BigDecimal crQuantity;
    private java.math.BigDecimal maQuantity;
    private java.math.BigDecimal miQuantity;
    private String remark;
}
