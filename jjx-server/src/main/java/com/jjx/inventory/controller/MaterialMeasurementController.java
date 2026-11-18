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
    public record Preview(@com.fasterxml.jackson.annotation.JsonUnwrapped MeasurementResult result,
                          java.math.BigDecimal inventoryArea) { }
    @PostMapping("/preview")
    @SaCheckLogin
    public Result<Preview> preview(@RequestBody MaterialMeasurementService.PreviewRequest request) {
        var result=service.preview(request);
        return Result.success(new Preview(result,MeasurementCalculator.inventoryArea(result)));
    }
}
