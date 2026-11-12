package com.jjx.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 跳距辅助定额只读预览出参（dev-20261112-004）。
 *
 * <p>语义边界：本预览只给「节距理论耗用」与「可按 BOM 落库的比例建议」，
 * 不叠加损耗、不计算整卷实发/最低投料、不写任何表。</p>
 */
@Data
public class PitchRequirementPreviewVO {

    /** 卷宽回显 mm。 */
    private BigDecimal widthMm;

    /** 跳距回显 mm（>0，最多 2 位小数，如 80.5）。 */
    private BigDecimal pitchMm;

    /** 每跳产出回显 PCS。 */
    private BigDecimal pcsPerJump;

    /** 归一后的投入单位：M / M2。 */
    private String inputUnit;

    /** 实际使用的产出数量 PCS。 */
    private Integer outputPcs;

    /** 未传 outputPcs 而使用默认 100 时为 true。 */
    private boolean outputPcsDefaulted;

    /** 模数建议 = 每跳产出 PCS。 */
    private BigDecimal moduleQtySuggested;

    /** 基数建议（每跳投入量，精确值）；无法在 decimal(12,4) 内精确表达时为 null。 */
    private BigDecimal baseQtySuggested;

    /** 基数权威精确文本（有理数，如 39/2500），不受 decimal 精度限制。 */
    private String baseQtyExactText;

    /** baseQty 能否按 decimal(12,4) 原值落库（true 才可直接回填 engineering_bom_item.base_qty）。 */
    private boolean baseQtyStorableInBom;

    /** 不可直接落库时的说明；可落库时为空。 */
    private String baseQtyStorageNote;

    /** 总理论耗用权威精确文本（有理数）。 */
    private String exactConsumptionText;

    /** 总理论耗用展示值：4 位小数 HALF_UP，仅供展示，不作为权威输入。 */
    private BigDecimal displayConsumption;

    /** 耗用单位：M / M2（与 inputUnit 一致）。 */
    private String unit;

    /** 是否已叠加损耗；本预览恒为 false。 */
    private boolean lossIncluded;

    /** 损耗口径说明。 */
    private String lossNote;

    /** 是否含整卷实发/最低投料；本预览恒为 false。 */
    private boolean actualIssueIncluded;

    /** 整卷实发口径说明。 */
    private String issueNote;

    /** 计算口径与边界说明。 */
    private List<String> notes;
}
