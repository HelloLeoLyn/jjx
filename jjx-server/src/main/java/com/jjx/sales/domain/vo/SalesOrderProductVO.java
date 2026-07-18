package com.jjx.sales.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单产品明细响应VO
 */
@Data
@Schema(description = "订单产品明细响应VO")
public class SalesOrderProductVO {

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "产品数量", example = "10")
    private Integer quantity;

    @Schema(description = "产品金额", example = "1000.00")
    private BigDecimal amount;

    @Schema(description = "订单ID", example = "1001")
    private Long orderId;

    @Schema(description = "产品ID", example = "2001")
    private Long productId;

    @Schema(description = "单位", example = "PCS")
    private String unit;

    @Schema(description = "单价", example = "100.00")
    private BigDecimal unitPrice;

    @Schema(description = "产品编码", example = "P2024001")
    private String productCode;

    @Schema(description = "产品名称", example = "薄膜开关")
    private String productName;

    @Schema(description = "创建时间", example = "2026-04-20 10:00:00")
    private Date createTime;

    @Schema(description = "创建人", example = "admin")
    private String createBy;

    @Schema(description = "更新时间", example = "2026-04-20 10:00:00")
    private Date updateTime;

    @Schema(description = "更新人", example = "admin")
    private String updateBy;
}
