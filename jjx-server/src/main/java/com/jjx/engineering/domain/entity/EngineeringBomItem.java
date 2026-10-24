package com.jjx.engineering.domain.entity;

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
     * 父节点明细ID（指向本表item_id，NULL=根节点，树形结构）
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentMaterialId;

    /**
     * 物料ID（关联库存模块；快速模式可空，审核员补齐）
     */
    private Long materialId;

    /**
     * 行审核状态（dev-20261020-029）：1草稿 2待审核 3审核通过 4审核驳回 5待移除
     */
    private Integer reviewStatus;

    /**
     * 行审核驳回原因
     */
    private String rejectReason;

    /**
     * 批准时内容指纹（SHA-256），用于识别已通过行是否被修改
     */
    private String approvedFingerprint;

    /** 行审核通过人 */
    private String approvedBy;

    /** 行审核通过时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedTime;

    /** 待移除行是否已被审核员确认：0否 1是 */
    private Boolean removeConfirmed;

    /** 移除确认人 */
    private String removeConfirmedBy;

    /** 移除确认时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime removeConfirmedTime;

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

    /** 跳距（含单位，如50mm） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String split;

    /**
     * 项目（标准工序）引用 engineering_standard_process.process_id
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long processId;

    /**
     * 项目（标准工序）名称冗余
     */
    private String processName;

    /**
     * 兼容旧列；公共物料由项目为空派生。
     */
    private Boolean isCommon;

    /** 兼容旧接口字段；公共物料只由项目是否为空决定，不采用历史标记。 */
    public Boolean getIsCommon() {
        return processId == null && (processName == null || processName.isBlank());
    }

    /**
     * 单位
     */
    private String unit;

    /**
     * 用量（标准用量）
     */
    private BigDecimal quantity;

    /**
     * 应用料（含损耗）= 用量×(1+损耗率/100)
     */
    private BigDecimal appliedQty;

    /**
     * 实际投料（板材/卷材且最低投料>0时按最低投料向上取整，否则=应用料）
     */
    private BigDecimal actualIssueQty;

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
     * 物料类型（R=板材/卷材，查询时带出，前端展示投料计算说明）
     */
    @TableField(exist = false)
    private String materialType;

    /**
     * 前端组合出的默认规格，仅作提交与日志对照；查询默认值从原始来源组合，不落业务表。
     */
    @TableField(exist = false)
    private String materialSpecification;

    /** 当前物料的原始尺寸/属性/模板，前端负责组合默认规格。 */
    @TableField(exist = false)
    private java.util.Map<String, Object> materialSpecificationSource;

    /** 上次保存时的系统规格，仅由操作日志带出，不落业务表。 */
    @TableField(exist = false)
    private String specificationBaseline;

    /** 上次保存时规格与系统值不同；用于保留人工清空的值。 */
    @TableField(exist = false)
    private Boolean specificationManual;

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
