package com.jjx.production.printing;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 生产实际用版接口（P10，dev-20261125-017）。 */
@RestController
@RequestMapping("/production/printing/uses")
@RequiredArgsConstructor
public class PrintUseController {

    private final PrintUseService service;

    @PostMapping
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Map<String, Object>> start(@RequestBody PrintUseCommands command) {
        return Result.success(service.start(command));
    }

    @PostMapping("/end")
    @SaCheckPermission("production:operation-execution:edit")
    public Result<Map<String, Object>> end(@RequestParam Long executionId,
                                           @RequestParam(required = false) String reason) {
        return Result.success(service.end(executionId, reason));
    }

    @GetMapping
    @SaCheckPermission("production:operation-execution:view")
    public Result<List<Map<String, Object>>> list(@RequestParam Long executionId) {
        return Result.success(service.list(executionId));
    }
}
