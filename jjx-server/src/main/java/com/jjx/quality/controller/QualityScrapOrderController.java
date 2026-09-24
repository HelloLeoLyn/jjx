package com.jjx.quality.controller;

import com.jjx.common.core.result.Result;
import com.jjx.quality.domain.entity.QualityScrapOrder;
import com.jjx.quality.service.QualityScrapOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成品报废单接口 —— dev-20260924-006（报废线三期）。
 *
 * <p>单据由报废处置生效时自动生成（含超阈值审批通过的场景）；本接口只做查询。
 * 打印凭据（车间实物交接签字）版式后置，不属本期。</p>
 */
@Tag(name = "质量管理-成品报废单")
@RestController
@RequestMapping("/quality/scrap-order")
@RequiredArgsConstructor
public class QualityScrapOrderController {

    private final QualityScrapOrderService scrapOrderService;

    @Operation(summary = "成品报废单列表（关键字：报废单号 / 不良单号 / 工单号）")
    @GetMapping("/list")
    public Result<List<QualityScrapOrder>> list(@RequestParam(required = false) String keyword) {
        return Result.success(scrapOrderService.list(keyword));
    }

    @Operation(summary = "成品报废单详情")
    @GetMapping("/{scrapId}")
    public Result<QualityScrapOrder> detail(@PathVariable Long scrapId) {
        return Result.success(scrapOrderService.detail(scrapId));
    }
}
