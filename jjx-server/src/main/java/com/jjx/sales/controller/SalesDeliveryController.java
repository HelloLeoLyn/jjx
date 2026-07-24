package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.sales.domain.dto.SalesDeliveryQueryDTO;
import com.jjx.sales.domain.vo.SalesDeliveryVO;
import com.jjx.sales.service.ISalesDeliveryService;
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
}
