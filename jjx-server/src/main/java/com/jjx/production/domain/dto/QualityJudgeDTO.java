package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 质检判定入参（P3-C）
 * 正式质量动作：判定 PASS / FAIL（不可变：已判定后禁止修改质量事实，复检走新记录）。
 */
@Data
@Schema(description = "质检判定入参")
public class QualityJudgeDTO {

    @NotNull(message = "判定结果必填(PASS/FAIL)")
    @Schema(description = "判定结果：PASS-合格 / FAIL-不合格")
    private String result;

    @Schema(description = "实际检验数量(>=0)")
    private BigDecimal totalQty;

    @Schema(description = "质量认可合格数量(>=0；PASS 时必须 >0)")
    private BigDecimal passQty;

    @Schema(description = "质量判定不合格数量(>=0)")
    private BigDecimal failQty;

    @Schema(description = "缺陷描述（FAIL 建议填写）")
    private String defectDesc;

    @Schema(description = "备注")
    private String remark;
}
