package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.PitchRequirementPreviewRequest;
import com.jjx.production.domain.vo.PitchRequirementPreviewVO;
import com.jjx.production.service.PitchRequirementCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跳距辅助定额只读预览（dev-20261112-004）。
 *
 * <p>只读：不落库、不写 BOM/库存/任务数据，重复调用不产生写入；
 * 建议值是否回填仍由用户在 BOM 保存入口确认，本接口不承担持久化。</p>
 */
@Tag(name = "跳距辅助定额")
@RestController
@RequestMapping("/production/bom/pitch-requirement")
public class PitchRequirementController {

    /** 跳距辅助定额预览：宽/跳距/每跳产出/投入单位/产出 PCS → 理论耗用与 base/module 建议。 */
    @Operation(summary = "跳距辅助定额只读预览")
    @GetMapping("/preview")
    @SaCheckPermission(value = {"engineering:bom:view", "engineering:bom:add", "engineering:bom:edit"}, mode = SaMode.OR)
    public Result<PitchRequirementPreviewVO> preview(PitchRequirementPreviewRequest request) {
        return Result.success(PitchRequirementCalculator.preview(request));
    }
}
