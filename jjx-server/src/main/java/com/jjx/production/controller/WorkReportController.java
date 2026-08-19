package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.vo.WorkReportVO;
import com.jjx.production.service.WorkReportActionService;
import com.jjx.production.service.WorkReportReadService;
import com.jjx.system.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生产报工 Controller（P2-B：仅只读）
 * SUBMIT/CANCEL 动作属 P2-C；不暴露通用 POST/PUT/DELETE CRUD（WorkReport 未来正式写动作只有 SUBMIT/CANCEL）。
 */
@Tag(name = "生产报工")
@RestController
@RequestMapping("/production/work-report")
@RequiredArgsConstructor
public class WorkReportController extends BaseController {

    private final WorkReportReadService workReportReadService;
    private final WorkReportActionService workReportActionService;

    @Operation(summary = "报工单条详情")
    @SaCheckPermission("production:operation-execution:view")
    @GetMapping("/{id}")
    public Result<WorkReportVO> getById(@PathVariable Long id) {
        return Result.success(workReportReadService.getById(id));
    }

    @Operation(summary = "某工序执行的全部报工历史（含已撤销，CANCELLED 标记）")
    @SaCheckPermission("production:operation-execution:view")
    @GetMapping("/execution/{executionId}")
    public Result<List<WorkReportVO>> listByExecution(@PathVariable Long executionId) {
        return Result.success(workReportReadService.listByExecutionId(executionId));
    }

    // ==================== P2-C 动作 ====================

    @Operation(summary = "提交报工（SUBMIT：仅当前 ACTIVE 责任人可报；数量/工时由后端解析并重算 projection）")
    @SaCheckPermission("production:work-report:add")
    @PostMapping
    public Result<WorkReportVO> submit(@Valid @RequestBody WorkReportSubmitDTO dto) {
        return Result.success(workReportActionService.submit(dto,
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "撤销报工（CANCEL：SUBMITTED→CANCELLED，条件更新防并发；已撤销幂等）")
    @SaCheckPermission("production:work-report:cancel")
    @PostMapping("/{id}/cancel")
    public Result<WorkReportVO> cancel(@PathVariable Long id, @Valid @RequestBody WorkReportCancelDTO dto) {
        return Result.success(workReportActionService.cancel(id, dto,
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }
}
