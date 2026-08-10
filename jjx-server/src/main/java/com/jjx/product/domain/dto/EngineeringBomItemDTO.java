package com.jjx.product.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * BOM明细DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EngineeringBomItemDTO {

    /** 明细ID（编辑时必填，新增时不填） */
    private Long itemId;

    /** 物料ID */
    @NotNull(message = "物料不能为空")
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 规格型号 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 用量 */
    @NotNull(message = "用量不能为空")
    @Positive(message = "用量必须大于0")
    private BigDecimal quantity;

    /** 应用料（含损耗，后端计算，Excel导入可直读） */
    private BigDecimal appliedQty;

    /** 实际投料（按最低投料向上取整，后端计算，Excel导入可直读） */
    private BigDecimal actualIssueQty;

    /** 损耗率(%) */
    private Integer lossRate;

    /** 模数：每份材料可产出产品数量 */
    private BigDecimal moduleQty;

    /** 基数：每个产品所需材料份数 */
    private BigDecimal baseQty;

    /** 最低投料量 */
    private BigDecimal minIssueQty;

    /** 规格-宽度(mm) */
    private BigDecimal widthMm;

    /** 规格-长度(mm) */
    private BigDecimal lengthMm;

    /** 层（薄膜开关专用） */
    private String layer;

    /** 位号 */
    private String positionNo;

    /** 来源类型：buy外购/make自制 */
    private String sourceType;

    /** 替代物料列表（JSON） */
    private String substituteJson;

    /** 排序 */
    @JsonAlias(value = {"sortOrder","itemOrder"})
    private Integer itemOrder;

    /** 备注 */
    private String remark;
}
