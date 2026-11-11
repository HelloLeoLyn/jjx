package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.vo.ManufacturingSnapshotVO;
import com.jjx.production.service.ManufacturingHistorySnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 工单制造历史快照（dev-20261111-009 G1）：只读，不改任何历史与库存。 */
@RestController
@RequestMapping("/production/order")
@RequiredArgsConstructor
@Tag(name = "制造历史快照")
public class ManufacturingHistorySnapshotController {

    private final ManufacturingHistorySnapshotService manufacturingHistorySnapshotService;

    @Operation(summary = "工单制造历史快照")
    @GetMapping("/{orderId}/manufacturing-snapshot")
    @SaCheckPermission("production:order:view")
    public Result<ManufacturingSnapshotVO> snapshot(@PathVariable Long orderId,
                                                    @RequestParam(required = false) Integer depth) {
        return Result.success(manufacturingHistorySnapshotService.snapshot(orderId, depth));
    }
}
