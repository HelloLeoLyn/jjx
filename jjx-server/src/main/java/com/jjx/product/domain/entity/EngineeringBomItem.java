package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品BOM明细实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("engineering_bom_item")
public class EngineeringBomItem {

    /**
     * BOM明细ID
     */
    @TableId(type = IdType.AUTO)
    private Long itemId;  // 建议改为 itemId，与表字段一致

    /**
     * BOM ID
     */
    private Long bomId;

    /**
     * 物料ID（关联库存模块）
     */
    private Long materialId;

    /**
     * 物料编码（冗余，便于查询）
     */
    private String materialCode;

    /**
     * 物料名称（冗余，便于查询）
     */
    private String materialName;

    /**
     * 规格型号（冗余，便于展示）
     */
    private String specification;

    /**
     * 单位
     */
    private String unit;

    /**
     * 用量（标准用量）
     */
    private BigDecimal quantity;

    /**
     * 损耗率(%)
     */
    private Integer lossRate;  // 改为 Integer，0-100

    /**
     * 模数：每份材料可产出产品数量（如4表示一材料做4个产品）
     */
    private BigDecimal moduleQty;

    /**
     * 基数：生产一个产品所需材料份数（如1表示每产品用1份）
     */
    private BigDecimal baseQty;

    /**
     * 最低投料量（安全库存下限）
     */
    private BigDecimal minIssueQty;

    /**
     * 规格-宽度(mm)
     */
    private BigDecimal widthMm;

    /**
     * 规格-长度(mm)
     */
    private BigDecimal lengthMm;

    /**
     * 层（薄膜开关专用）
     */
    private String layer;  // overlay/upper_circuit/spacer/lower_circuit/back_adhesive

    /**
     * 位号（如：LED1, R1, J1）
     */
    private String positionNo;

    /**
     * 来源类型：buy外购/make自制
     */
    private String sourceType;

    /**
     * 替代物料列表（JSON格式）
     * 示例：[{"materialId": 2, "materialCode": "MAT-002", "priority": 1}]
     */
    private String substituteJson;

    /**
     * 排序
     */
    private Integer itemOrder;

    /**
     * 备注
     */
    private String remark;

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

    // ========== 以下为非数据库字段（用于业务计算） ==========

    /**
     * 实际用量（非数据库字段，通过 quantity * (1 + lossRate/100) 计算）
     */
    @TableField(exist = false)
    private BigDecimal actualQuantity;

    /**
     * 单价（从物料模块获取，不存储）
     */
    @TableField(exist = false)
    private BigDecimal unitPrice;

    /**
     * 金额（非数据库字段，通过 actualQuantity * unitPrice 计算）
     */
    @TableField(exist = false)
    private BigDecimal amount;

    // ========== 辅助方法 ==========

    /**
     * 计算实际用量
     */
    public BigDecimal calculateActualQuantity() {
        if (quantity == null) return BigDecimal.ZERO;
        if (lossRate == null || lossRate == 0) return quantity;
        return quantity.multiply(BigDecimal.valueOf(1 + lossRate / 100.0));
    }

    /**
     * 计算金额
     */
    public BigDecimal calculateAmount(BigDecimal unitPrice) {
        if (unitPrice == null) return BigDecimal.ZERO;
        return calculateActualQuantity().multiply(unitPrice);
    }

    /**
     * 设置实际用量（用于前端展示）
     */
    public BigDecimal getActualQuantity() {
        return calculateActualQuantity();
    }

    /**
     * 设置金额（用于前端展示）
     */
    public BigDecimal getAmount() {
        return calculateAmount(this.unitPrice);
    }
}
