package com.jjx.production.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.TraceQueryDTO;
import com.jjx.production.domain.vo.TraceVO;
import com.jjx.production.service.ProductionTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "生产追溯")
@RestController
@RequestMapping("/production/trace")
@RequiredArgsConstructor
public class ProductionTraceController {

    private final ProductionTraceService traceService;

    @Operation(summary = "分页查询追溯记录")
    @GetMapping("/page")
    public Result<PageResult<TraceVO>> page(TraceQueryDTO query) {
        return Result.success(traceService.page(query));
    }

    @Operation(summary = "正追溯")
    @GetMapping("/forward/{traceCode}")
    public Result<List<TraceVO>> traceForward(@PathVariable String traceCode) {
        return Result.success(traceService.traceForward(traceCode));
    }

    @Operation(summary = "反追溯")
    @GetMapping("/backward/{traceCode}")
    public Result<List<TraceVO>> traceBackward(@PathVariable String traceCode) {
        return Result.success(traceService.traceBackward(traceCode));
    }
}
