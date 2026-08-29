package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.vo.SampleTransferPreviewVO;
import com.jjx.sales.dto.transfer.SampleTransferConfirmDTO;
import com.jjx.sales.service.ISampleOrderService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 打样转标准控制器
 * 轻量版弹窗 / 对照版全屏页共用（预览 + 确认转移）
 */
@Tag(name = "打样转标准")
@RestController
@RequestMapping("/sample/transfer")
@RequiredArgsConstructor
public class SampleTransferController extends BaseController {

    private final ISampleOrderService sampleOrderService;

    /**
     * 打样转标准-预览
     * 读取打样数据（工序+物料JSON），自动匹配标准工序/物料，返回带匹配推荐的预览数据
     */
    @Operation(summary = "打样转标准-预览（读取打样数据+自动匹配推荐）")
    @SaCheckPermission(value = {"sales:sample:convert", "sales:sample:view", "engineering:sample:workbench"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    @GetMapping("/preview/{orderId}")
    public Result<SampleTransferPreviewVO> preview(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.previewTransfer(orderId));
    }

    /**
     * 打样转标准-确认转移
     * 接收前端编辑后的标准数据（工序映射+物料映射），生成新版本 BOM/Routing，旧版本失效，回填打样单和产品表
     */
    @Operation(summary = "打样转标准-确认转移（接收前端编辑后的标准数据落库）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'",
            bizId = "#dto.orderId", bizStatus = "7", detail = "#result.data.transferNo")
    @SaCheckPermission(value = {"sales:sample:convert", "engineering:sample:workbench"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    @PostMapping("/confirm")
    public Result<java.util.Map<String, Object>> confirm(@RequestBody SampleTransferConfirmDTO dto) {
        return Result.success(sampleOrderService.confirmTransfer(dto));
    }

    /**
     * 打样转标准-资料转移提醒（DEV-1228）
     * 转量产就绪检查处置栏：不再直接转移，改为发布任务提醒工程执行资料转移
     */
    @Operation(summary = "资料转移提醒（发布任务给工程执行资料转移）")
    @SaCheckPermission(value = {"sales:sample:convert", "sales:sample:view", "engineering:sample:workbench"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    @PostMapping("/remind/{orderId}")
    public Result<java.util.Map<String, Object>> remind(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.remindTransfer(orderId));
    }
}
