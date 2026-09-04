package com.jjx.sales.controller;

import com.jjx.common.constant.LogActions;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.sales.domain.dto.SalesDeliveryQueryDTO;
import com.jjx.sales.domain.vo.SalesDeliveryVO;
import com.jjx.sales.domain.entity.SalesDelivery;
import com.jjx.sales.service.ISalesDeliveryService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售发货单控制器
 */
@Tag(name = "销售发货单管理")
@RestController
@RequestMapping("/sales/deliveries")
@RequiredArgsConstructor
public class SalesDeliveryController {

    private final ISalesDeliveryService salesDeliveryService;

    @Operation(summary = "分页查询发货单")
    @SaCheckPermission("sales:delivery:view")
    @GetMapping
    public Result<Page<SalesDeliveryVO>> list(SalesDeliveryQueryDTO dto) {
        return Result.success(salesDeliveryService.pageQuery(dto));
    }

    @Operation(summary = "查询发货单详情")
    @SaCheckPermission("sales:delivery:view")
    @GetMapping("/{deliveryId}")
    public Result<SalesDeliveryVO> getById(@PathVariable Long deliveryId) {
        SalesDeliveryVO vo = salesDeliveryService.getById(deliveryId);
        return Result.success(vo);
    }

    @Operation(summary = "根据销售订单ID查询发货单")
    @SaCheckPermission("sales:delivery:view")
    @GetMapping("/by-order/{orderId}")
    public Result<List<SalesDeliveryVO>> listByOrderId(@PathVariable Long orderId) {
        return Result.success(salesDeliveryService.listByOrderId(orderId));
    }

    @Operation(summary = "签收发货单")
    @SaCheckPermission("sales:delivery:view")
    @Log(module = "销售发货", businessType = BusinessType.UPDATE,
            bizType = "'sales_delivery'", bizId = "#deliveryId", bizStatus = "'RECEIVED'", action = LogActions.DELIVERY_RECEIVE)
    @PutMapping("/{deliveryId}/receive")
    public Result<Void> receive(@PathVariable Long deliveryId,
                                @RequestBody(required = false) SalesDelivery receiveInfo) {
        salesDeliveryService.receive(deliveryId, receiveInfo);
        return Result.success();
    }

    @Operation(summary = "导出送货单PDF（单张表单）")
    @SaCheckPermission("sales:delivery:view")
    @GetMapping("/export-pdf/{deliveryId}")
    public void exportPdf(@PathVariable Long deliveryId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        SalesDeliveryVO vo = salesDeliveryService.getById(deliveryId);
        byte[] bytes = salesDeliveryService.exportPdf(deliveryId);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode(vo.getDeliveryNo() + ".pdf", java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
    }
}
