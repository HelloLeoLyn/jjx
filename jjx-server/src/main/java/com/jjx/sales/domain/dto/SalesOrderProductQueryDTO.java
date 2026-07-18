package com.jjx.sales.domain.dto;

import com.jjx.common.core.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单产品明细查询DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "订单产品明细查询DTO")
public class SalesOrderProductQueryDTO extends PageQuery {

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "订单ID", example = "1001")
    private Long orderId;

    @Schema(description = "产品ID", example = "2001")
    private Long productId;

    @Schema(description = "产品编码", example = "P2024001")
    private String productCode;

    @Schema(description = "产品名称", example = "薄膜开关")
    private String productName;

    @Schema(description = "最小数量", example = "1")
    private Integer minQuantity;

    @Schema(description = "最大数量", example = "100")
    private Integer maxQuantity;
}
