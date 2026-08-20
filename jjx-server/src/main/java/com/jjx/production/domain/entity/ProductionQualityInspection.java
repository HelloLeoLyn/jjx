package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("production_quality_inspection")
public class ProductionQualityInspection {
    @TableId(type = IdType.AUTO)
    private Long inspectionId;
    private String inspectionNo;
    private String inspectionType;
    private Long orderId;
    /** P3-B：关联工序执行（IPQC/FQC 必填；IQC/OQC 可空） */
    private Long executionId;
    /** P3-B：关联报工（IPQC 可空/推荐；FQC=NULL；IQC/OQC=NULL） */
    private Long workReportId;
    private Long materialId;
    private Long productId;
    private String inspector;
    private LocalDateTime inspectTime;
    private String result;
    /** P3-B：实际检验数量（DECIMAL(18,4)） */
    private BigDecimal totalQty;
    /** P3-B：质量认可合格数量（DECIMAL(18,4)） */
    private BigDecimal passQty;
    /** P3-B：质量判定不合格数量（DECIMAL(18,4)） */
    private BigDecimal failQty;
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
