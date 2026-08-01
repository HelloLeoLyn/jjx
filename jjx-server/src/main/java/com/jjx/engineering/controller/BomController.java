package com.jjx.engineering.controller;

import com.jjx.common.core.result.Result;
import com.jjx.engineering.service.IBomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "工程BOM管理")
@RestController
@RequestMapping("/engineering/bom")
@RequiredArgsConstructor
public class BomController {

    private final IBomService bomService;

    @Operation(summary = "获取BOM列表")
    @GetMapping("/page")
    public Result<?> page() {
        return Result.success(bomService.listPage(null));
    }

    @Operation(summary = "BOM提交审核")
    @PutMapping("/submit/{bomId}")
    public Result<Void> submit(@PathVariable Long bomId) {
        bomService.submitApprove(bomId);
        return Result.success();
    }

    @Operation(summary = "审核BOM")
    @PutMapping("/approve/{bomId}")
    public Result<Void> approve(@PathVariable Long bomId, @RequestBody(required = false) Map<String, Object> dto) {
        String remark = dto != null && dto.get("remark") != null ? String.valueOf(dto.get("remark")) : null;
        bomService.approve(bomId, remark);
        return Result.success();
    }

    @Operation(summary = "驳回BOM")
    @PutMapping("/reject/{bomId}")
    public Result<Void> reject(@PathVariable Long bomId, @RequestBody(required = false) Map<String, Object> dto) {
        String remark = dto != null && dto.get("remark") != null ? String.valueOf(dto.get("remark")) : null;
        bomService.reject(bomId, remark);
        return Result.success();
    }
}
