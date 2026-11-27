package com.jjx.production.printing.kit;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 多资源齐套接口（dev-20261126-002）。 */
@RestController
@RequestMapping("/production/printing/kit")
@RequiredArgsConstructor
public class PrintKitController {

    private final PrintKitService service;

    @GetMapping("/product/{productId}")
    @SaCheckPermission("production:operation-execution:view")
    public Result<Map<String, Object>> productPrecheck(@PathVariable long productId) {
        return Result.success(service.productPrecheck(productId));
    }

    @GetMapping("/order/{orderId}")
    @SaCheckPermission("production:operation-execution:view")
    public Result<Map<String, Object>> orderKit(@PathVariable long orderId) {
        return Result.success(service.orderKit(orderId));
    }

    @PostMapping("/order/{orderId}/recheck")
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Map<String, Object>> recheck(@PathVariable long orderId) {
        return Result.success(service.recheck(orderId));
    }

    @GetMapping("/shortages")
    @SaCheckPermission("production:operation-execution:view")
    public Result<List<Map<String, Object>>> shortages(@RequestParam(required = false) Long orderId,
                                                       @RequestParam(required = false) Integer status) {
        return Result.success(service.shortages(orderId, status));
    }

    @PostMapping("/shortages/{id}/close")
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Void> close(@PathVariable long id, @RequestParam(required = false) String reason) {
        service.close(id, reason);
        return Result.success(null);
    }
}
