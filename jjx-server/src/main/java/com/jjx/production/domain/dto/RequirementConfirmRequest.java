package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持久确认契约占位（dev-20261111-006）。
 *
 * <p><b>仅契约，不是可成功调用的能力。</b>请求头表、请求级幂等唯一键、分配行唯一键、事务锁和
 * 失效供给重算尚未获准建表；在方案获批并完成迁移前，服务端不暴露 confirm 成功接口，也不返回
 * 假的成功响应。确认/分配后属于本任务后半步，需 Codex 验收第一步后再派。</p>
 */
@Data
@Schema(description = "缺口确认请求契约（尚未实现持久确认）")
public class RequirementConfirmRequest {

    @Schema(description = "产品ID")
    private Long productId;

    @Schema(description = "根 BOM；为空按产品当前/最新已批准 PRODUCT BOM")
    private Long bomId;

    @Schema(description = "产品需求数量")
    private BigDecimal quantity;

    @Schema(description = "需求日期")
    private LocalDate demandDate;

    @Schema(description = "仓库ID，可选")
    private Long warehouseId;

    /** 请求级幂等键：同一 key 重复提交必须返回同一结果，不能重复占用/开工单。 */
    @Schema(description = "请求级幂等键（持久确认阶段使用）")
    private String idempotencyKey;
}
