package com.jjx.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.measurement.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory/material/measurement")
@RequiredArgsConstructor
public class MaterialMeasurementController {
    private final MaterialMeasurementService service;
    @PostMapping("/preview")
    @SaCheckLogin
    public Result<MeasurementResult> preview(@RequestBody MaterialMeasurementService.PreviewRequest request) {
        return Result.success(service.preview(request));
    }
}
