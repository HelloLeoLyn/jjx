package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.RequirementPreviewRequest;
import com.jjx.production.domain.vo.RequirementSummaryVO;
import com.jjx.production.service.ManufacturingRequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多层制造需求只读试算（dev-20261111-006）。
 *
 * <p>复用既有查看权限，不新增权限点。只提供 preview；持久 confirm 未实现，不暴露假成功接口。
 * 请求中的库存/在途等字段一律不可信，供给由服务端读取。</p>
 */
@RestController
@RequestMapping("/production/requirement")
@RequiredArgsConstructor
@Tag(name = "多层制造需求试算")
public class ManufacturingRequirementController {

    private final ManufacturingRequirementService manufacturingRequirementService;

    @Operation(summary = "多层净需求只读试算")
    @PostMapping("/preview")
    @SaCheckPermission(value = {"production:order:view", "inventory:alert:view"}, mode = SaMode.OR)
    public Result<RequirementSummaryVO> preview(@Validated @RequestBody RequirementPreviewRequest request) {
        return Result.success(manufacturingRequirementService.preview(request));
    }
}
