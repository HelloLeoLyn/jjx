package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 多层净需求只读试算请求（dev-20261111-006）。
 *
 * <p>只允许需求条件：产品、可选已批准根 BOM、数量、需求日期、仓库。库存/在途/工单产出
 * 一律由服务端读取，客户端提交的同名字段不会被采信。</p>
 */
@Data
@Schema(description = "多层净需求只读试算请求")
public class RequirementPreviewRequest {

    @NotNull(message = "产品不能为空")
    @Schema(description = "产品ID", example = "1")
    private Long productId;

    @Schema(description = "显式根 BOM；为空时按产品当前/最新已批准 PRODUCT BOM 选择")
    private Long bomId;

    @NotNull(message = "需求数量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "需求数量必须大于0")
    @Schema(description = "产品需求数量", example = "300")
    private BigDecimal quantity;

    @Schema(description = "需求日期，默认当天；用于库存安全保护和按期供应筛选")
    private LocalDate demandDate;

    @Schema(description = "仓库ID，可选；为空按材料级库存汇总")
    private Long warehouseId;
}
