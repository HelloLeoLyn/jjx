package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.product.enums.ProcessCategoryEnum;
import com.jjx.product.enums.ProcessTypeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 枚举数据控制器
 */
@Tag(name = "枚举数据", description = "获取枚举数据接口")
@RestController
@RequestMapping("/api/v1/product/enums")
public class ProductEnumController {

    @Operation(summary = "获取工序类型枚举")
    @GetMapping("/process-types")
    public Result<List<Map<String, String>>> getProcessTypes() {
        List<Map<String, String>> list = Arrays.stream(ProcessTypeEnum.values())
                .map(e -> Map.of("code", e.getCode(), "name", e.getName(), "tagType", e.getTagType()))
                .toList();
        return Result.success(list);
    }

    @Operation(summary = "获取工序类别枚举")
    @GetMapping("/process-categories")
    public Result<List<Map<String, String>>> getProcessCategories() {
        List<Map<String, String>> list = Arrays.stream(ProcessCategoryEnum.values())
                .map(e -> Map.of("code", e.getCode(), "name", e.getName(), "tagType", e.getTagType()))
                .toList();
        return Result.success(list);
    }

    @Operation(summary = "获取审核状态枚举")
    @GetMapping("/approve-status")
    public Result<ApproveStatusEnum[]> getApproveStatus() {
        return Result.success(ApproveStatusEnum.values());
    }
}