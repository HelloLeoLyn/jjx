package com.jjx.quality.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.domain.entity.QualityNcrAction;
import com.jjx.quality.dto.QualityLotQueryDTO;
import com.jjx.quality.dto.QualityNcrDisposeDTO;
import com.jjx.quality.service.QualityNcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 不良台账接口 —— dev-20260917-003
 * 说明：本任务提供台账与处置登记；库存联动（返工/让步/报废动库存）见 008。
 */
@Tag(name = "质量管理-不良台账")
@RestController
@RequestMapping("/quality/ncr")
@RequiredArgsConstructor
public class QualityNcrController {

    private final QualityNcrService ncrService;
    /** dev-20260924-004：不良件级明细（件号/主缺陷/实测值/状态 + 缺陷记录） */
    private final com.jjx.quality.service.QualityNcrPieceService ncrPieceService;

    @Operation(summary = "不良台账分页（类型/状态/工单/物料/批次过滤）")
    @GetMapping("/page")
    public Result<Object> page(QualityLotQueryDTO query) {
        return Result.success(ncrService.pageNcrs(query));
    }

    @Operation(summary = "不良台账详情")
    @GetMapping("/{ncrId}")
    public Result<QualityNcr> detail(@PathVariable Long ncrId) {
        return Result.success(ncrService.getNcr(ncrId));
    }

    @Operation(summary = "隔离台账（dev-20260924-007/031：在隔离的货 = 未处置不良；默认只看成品，includeIqc=true 含来料；只做标识不动库存）")
    @GetMapping("/quarantine")
    public Result<List<com.jjx.quality.dto.vo.QuarantineRowVO>> quarantine(
            @RequestParam(defaultValue = "false") boolean includeIqc) {
        return Result.success(ncrService.listQuarantine(includeIqc));
    }

    @Operation(summary = "按工单/工序反查不良（工单详情用）")
    @GetMapping("/by-order")
    public Result<List<QualityNcr>> byOrder(@RequestParam Long orderId,
                                            @RequestParam(required = false) Long executionId) {
        return Result.success(ncrService.listByOrder(orderId, executionId));
    }

    @Operation(summary = "按检验批查不良")
    @GetMapping("/by-lot/{lotId}")
    public Result<List<QualityNcr>> byLot(@PathVariable Long lotId) {
        return Result.success(ncrService.listByLot(lotId));
    }

    @Operation(summary = "处置记录")
    @GetMapping("/{ncrId}/actions")
    public Result<List<QualityNcrAction>> actions(@PathVariable Long ncrId) {
        return Result.success(ncrService.listActions(ncrId));
    }

    @Operation(summary = "不良件级明细（件号 / 主缺陷 / 实测值 / 状态 + 全部缺陷记录）（dev-20260924-004）")
    @GetMapping("/{ncrId}/pieces")
    public Result<List<com.jjx.quality.dto.vo.QualityNcrPieceVO>> pieces(@PathVariable Long ncrId) {
        return Result.success(ncrPieceService.listPieces(ncrId));
    }

    @Operation(summary = "登记处置（返工/让步接收/报废；让步接收需客户确认）")
    @PostMapping("/{ncrId}/dispose")
    public Result<QualityNcrAction> dispose(@PathVariable Long ncrId, @RequestBody QualityNcrDisposeDTO dto) {
        return Result.success(ncrService.dispose(ncrId, dto));
    }

    @Operation(summary = "处置执行完成（返工完工/报废扣减/让步转良品后回调）")
    @PostMapping("/action/{actionId}/complete")
    public Result<QualityNcrAction> complete(@PathVariable Long actionId,
                                            @RequestParam(required = false) String resultRemark,
                                            @RequestParam(required = false) Long reworkExecutionId) {
        return Result.success(ncrService.completeAction(actionId, resultRemark, reworkExecutionId));
    }

    @Operation(summary = "撤销已生效处置（dev-20260923-022：受控动作，必填原因，留痕；本期支持报废）")
    @SaCheckPermission("quality:ncr:revoke")
    @PostMapping("/action/{actionId}/revoke")
    public Result<QualityNcrAction> revoke(@PathVariable Long actionId, @RequestParam String reason) {
        return Result.success(ncrService.revokeAction(actionId, reason,
                com.jjx.system.utils.SecurityUtils.getDisplayName()));
    }

    @Operation(summary = "报废审批通过（dev-20260924-005：待审批→生效；审批人≠提交人，超管可代；通过才计入台账与件级）")
    @SaCheckPermission("quality:ncr:scrap-approve")
    @PostMapping("/action/{actionId}/scrap/approve")
    public Result<QualityNcrAction> approveScrap(@PathVariable Long actionId,
                                                 @RequestParam(required = false) String remark) {
        return Result.success(ncrService.approveScrap(actionId, remark,
                com.jjx.system.utils.SecurityUtils.getDisplayName()));
    }

    @Operation(summary = "报废驳回（dev-20260924-005：待审批→作废，必填原因，留痕；台账/件级不动）")
    @SaCheckPermission("quality:ncr:scrap-approve")
    @PostMapping("/action/{actionId}/scrap/reject")
    public Result<QualityNcrAction> rejectScrap(@PathVariable Long actionId, @RequestParam String reason) {
        return Result.success(ncrService.rejectScrap(actionId, reason,
                com.jjx.system.utils.SecurityUtils.getDisplayName()));
    }

    @Operation(summary = "随批作废不良单（dev-20260923-040：来源批已被后继复检版本取代时的**正式入口**；需权限+必填原因+留痕，幂等）")
    @SaCheckPermission("quality:ncr:void-superseded")
    @PostMapping("/{ncrId}/void-superseded")
    public Result<Integer> voidSuperseded(@PathVariable Long ncrId, @RequestParam String reason) {
        return Result.success(ncrService.voidSupersededNcr(ncrId, reason,
                com.jjx.system.utils.SecurityUtils.getDisplayName()));
    }
}
