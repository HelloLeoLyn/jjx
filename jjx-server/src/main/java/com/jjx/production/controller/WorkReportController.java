package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.dto.WorkReportQueryDTO;
import com.jjx.production.domain.dto.WorkReportReviewDTO;
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
 * 生产报工 Controller（P3 WorkReport + Approval）
 * 只读：详情 / execution 历史 / 我的报工 / 待我审批
 * 写动作：SUBMIT / APPROVE / REJECT / CANCEL；不暴露通用 POST/PUT/DELETE CRUD。
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

    @Operation(summary = "某工序执行的全部报工历史（含 CANCELLED/REJECTED 标记）")
    @SaCheckPermission("production:operation-execution:view")
    @GetMapping("/execution/{executionId}")
    public Result<List<WorkReportVO>> listByExecution(@PathVariable Long executionId) {
        return Result.success(workReportReadService.listByExecutionId(executionId));
    }

    @Operation(summary = "我的报工（分页；reporterId=当前用户）")
    @SaCheckPermission("production:work-report:view")
    @GetMapping("/mine")
    public Result<PageResult<WorkReportVO>> mine(WorkReportQueryDTO queryDTO) {
        return Result.success(workReportReadService.pageMine(queryDTO, SecurityUtils.getUserId()));
    }

    @Operation(summary = "待我审批（分页；生产管理=全部 PENDING，普通用户=下级任务报工）")
    @SaCheckPermission("production:work-report:approve")
    @GetMapping("/pending-approval")
    public Result<PageResult<WorkReportVO>> pendingApproval(WorkReportQueryDTO queryDTO) {
        return Result.success(workReportReadService.pagePendingApproval(queryDTO,
                SecurityUtils.getUserId(), SecurityUtils.hasRole("production:all")));
    }

    // ==================== 写动作 ====================

    @Operation(summary = "提交报工（SUBMIT：仅当前 ACTIVE 执行人可报；数量上限=Task.remaining；"
            + "须 Execution 处于执行中；INSERT PENDING）")
    @SaCheckPermission("production:work-report:add")
    @PostMapping
    public Result<WorkReportVO> submit(@Valid @RequestBody WorkReportSubmitDTO dto) {
        return Result.success(workReportActionService.submit(dto,
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "审批通过（PENDING→APPROVED：pending 转 completed，remaining 不变；一笔只批一次）")
    @SaCheckPermission("production:work-report:approve")
    @PostMapping("/{id}/approve")
    public Result<WorkReportVO> approve(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) WorkReportReviewDTO dto) {
        return Result.success(workReportActionService.approve(id, dto,
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "审批驳回（PENDING→REJECTED：释放 pending 占用，remaining 恢复；驳回原因必填）")
    @SaCheckPermission("production:work-report:approve")
    @PostMapping("/{id}/reject")
    public Result<WorkReportVO> reject(@PathVariable Long id,
                                       @Valid @RequestBody WorkReportReviewDTO dto) {
        return Result.success(workReportActionService.reject(id, dto,
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "撤销报工（CANCEL：PENDING→CANCELLED，条件更新防并发；已撤销幂等；APPROVED 不可撤）")
    @SaCheckPermission("production:work-report:cancel")
    @PostMapping("/{id}/cancel")
    public Result<WorkReportVO> cancel(@PathVariable Long id, @Valid @RequestBody WorkReportCancelDTO dto) {
        return Result.success(workReportActionService.cancel(id, dto,
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }
}
