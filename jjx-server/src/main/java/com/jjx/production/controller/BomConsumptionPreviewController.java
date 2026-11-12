package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.BomConsumptionPreviewRequest;
import com.jjx.production.domain.vo.BomConsumptionPreviewVO;
import com.jjx.production.service.BomConsumptionPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BOM 明细耗用只读试算（dev-20261112-007）。
 *
 * <p>只读：按明细行当前编辑态试算每件净耗/总需求/参考米卷，不落库、不写 BOM/库存/任务数据；
 * 是否保存仍由用户在 BOM 保存入口确认，本接口不承担持久化。</p>
 *
 * <p>跳距辅助仍走 004 既有 GET /production/bom/pitch-requirement/preview，本接口不新增、不改、不代理。</p>
 */
@Tag(name = "BOM耗用试算")
@RestController
@RequestMapping("/production/bom/consumption")
@RequiredArgsConstructor
public class BomConsumptionPreviewController {

    private final BomConsumptionPreviewService bomConsumptionPreviewService;

    /** BOM 明细耗用只读试算：产出数量 + 逐行比例/损耗/最低投料 → 净耗、总需求、参考米卷。 */
    @Operation(summary = "BOM明细耗用只读试算")
    @PostMapping("/preview")
    @SaCheckPermission(value = {"engineering:bom:view", "engineering:bom:add", "engineering:bom:edit"},
            mode = SaMode.OR)
    public Result<BomConsumptionPreviewVO> preview(@RequestBody BomConsumptionPreviewRequest request) {
        return Result.success(bomConsumptionPreviewService.preview(request));
    }
}
