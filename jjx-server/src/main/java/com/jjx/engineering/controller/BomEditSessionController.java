package com.jjx.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.BomEditSessionRequest;
import com.jjx.product.domain.vo.BomEditSessionVO;
import com.jjx.product.service.BomEditSessionService;
import com.jjx.product.service.impl.BomEditSessionServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * BOM 编辑占用会话（dev-20261111-018）。
 *
 * <p>独立控制器，避免改动 BomController 既有的保存/审批入口契约。
 * 权限：获取/续租/释放 = engineering:bom:add 或 engineering:bom:edit（OR）；
 * 管理员强制解锁 = 专用 engineering:bom:edit:force-unlock（超管 Sa-Token 通配“*”天然覆盖，
 * 其他角色需要后续正常权限配置授权），普通 engineering:bom:edit 不得冒充。
 */
@RestController
@RequestMapping("/engineering/bom/{bomId}/edit-session")
@RequiredArgsConstructor
public class BomEditSessionController {

    private final BomEditSessionService bomEditSessionService;

    /** 查询占用：只返回显示名/是否本人/过期时间/leaseAvailable，不回传他人 token。 */
    @Operation(summary = "查询BOM编辑占用")
    @GetMapping
    @SaCheckPermission("engineering:bom:view")
    public Result<BomEditSessionVO> describe(@PathVariable Long bomId,
                                             @RequestParam(required = false) String clientId) {
        return Result.success(bomEditSessionService.describe(bomId, clientId));
    }

    /**
     * 获取编辑权（takeover=true 时走管理员强制解锁：先原子解除当前占用，再为本标签页重新取得）。
     * 普通入口不允许接管：同账号第二窗口同样只读。
     */
    @Operation(summary = "获取BOM编辑权")
    @PostMapping
    @SaCheckPermission(value = {"engineering:bom:add", "engineering:bom:edit"}, mode = SaMode.OR)
    public Result<BomEditSessionVO> acquire(@PathVariable Long bomId,
                                            @RequestBody(required = false) BomEditSessionRequest request) {
        if (request != null && Boolean.TRUE.equals(request.getTakeover())) {
            // 绝不用 engineering:bom:edit 冒充管理员解锁：必须是专用权限码。
            StpUtil.checkPermission(BomEditSessionServiceImpl.FORCE_UNLOCK_PERMISSION);
            bomEditSessionService.forceUnlock(bomId);
            // 解除后重新申请：新 token 返回给本次调用方，原占用者的 token 不会复活。
            return Result.success(bomEditSessionService.acquire(bomId, request.getClientId()));
        }
        return Result.success(bomEditSessionService.acquire(bomId, request == null ? null : request.getClientId()));
    }

    @Operation(summary = "续租BOM编辑权")
    @PutMapping
    @SaCheckPermission(value = {"engineering:bom:add", "engineering:bom:edit"}, mode = SaMode.OR)
    public Result<BomEditSessionVO> renew(@PathVariable Long bomId,
                                          @RequestBody BomEditSessionRequest request) {
        return Result.success(bomEditSessionService.renew(bomId, request == null ? null : request.getClientId(),
                request == null ? null : request.getToken()));
    }

    /** 关闭/取消时 compare-token 释放；token 不匹配不会影响持有者。 */
    @Operation(summary = "释放BOM编辑权")
    @DeleteMapping
    @SaCheckPermission(value = {"engineering:bom:add", "engineering:bom:edit"}, mode = SaMode.OR)
    public Result<Boolean> release(@PathVariable Long bomId,
                                   @RequestBody(required = false) BomEditSessionRequest request) {
        return Result.success(bomEditSessionService.release(bomId, request == null ? null : request.getToken()));
    }

    /**
     * 管理员强制解锁：专用权限码通过后真实解除占用并写 sys_oper_log 审计。
     * 无占用时返回“当前无占用”，不是 403；审计写入失败则整个操作失败（不静默无痕）。
     */
    @Operation(summary = "管理员强制解锁BOM编辑占用")
    @PostMapping("/unlock")
    @SaCheckPermission(BomEditSessionServiceImpl.FORCE_UNLOCK_PERMISSION)
    public Result<BomEditSessionVO> unlock(@PathVariable Long bomId) {
        return Result.success(bomEditSessionService.forceUnlock(bomId));
    }

    @ExceptionHandler(cn.dev33.satoken.exception.NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> permissionDenied(cn.dev33.satoken.exception.NotPermissionException exception) {
        return Result.error(com.jjx.common.exception.BusinessExceptionEnum.NO_PERMISSION.getCode(),
                "无BOM编辑占用操作权限");
    }
}
