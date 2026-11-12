package com.jjx.production.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 跳距辅助定额只读预览入参（dev-20261112-004）。
 *
 * <p>数值一律用 {@link BigDecimal} 接参，便于在服务层统一拒绝空值、非正数与非整数，
 * 而不是让 Spring 绑定层直接抛类型错误。本预览不落库、不读写 BOM/库存。</p>
 */
@Data
public class PitchRequirementPreviewRequest {

    /** 卷宽 mm（对齐 engineering_bom_item.width_mm decimal(10,2)，最多 2 位小数）。 */
    private BigDecimal widthMm;

    /** 跳距 mm（界面只输整数，单位固定 mm）。 */
    private BigDecimal pitchMm;

    /** 每跳产出 PCS（正整数）。 */
    private BigDecimal pcsPerJump;

    /** 投入单位：M 或 M2（米/㎡ 可归一，其他拒绝）。 */
    private String inputUnit;

    /** 产出数量 PCS；空 = 默认 100，显式传必须 >0 正整数。 */
    private BigDecimal outputPcs;
}
