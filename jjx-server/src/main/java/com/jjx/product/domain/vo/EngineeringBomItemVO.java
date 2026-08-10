package com.jjx.product.domain.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EngineeringBomItemVO {

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
     * 应用料（含损耗）= 用量×(1+损耗率/100)
     */
    private BigDecimal appliedQty;

    /**
     * 实际投料（板材/卷材且最低投料>0时按最低投料向上取整，否则=应用料）
     */
    private BigDecimal actualIssueQty;

    /**
     * 物料类型（R=板材/卷材，用于展示投料计算说明）
     */
    private String materialType;

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


}
