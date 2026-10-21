package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.product.domain.vo.StandardProcessCommonVO;
import com.jjx.product.service.StandardProcessUsageService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class StandardProcessCommonController {
    private final StandardProcessUsageService usageService;

    @Operation(summary = "全公司近90天常用工序")
    @GetMapping("/standard-process/common")
    public Result<List<StandardProcessCommonVO>> common(@RequestParam(defaultValue = "12") int limit) {
        return Result.success(usageService.getCommon(limit));
    }
}
