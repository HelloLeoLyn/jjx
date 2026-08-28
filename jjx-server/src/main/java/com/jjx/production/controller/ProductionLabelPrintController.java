package com.jjx.production.controller;

import com.jjx.common.core.result.Result;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "标签打印")
@RestController
@RequestMapping("/production/label-print")
public class ProductionLabelPrintController {

    @Operation(summary = "记录标签打印留痕")
    @PostMapping("/log")
    @Log(module = "标签打印", businessType = BusinessType.OTHER,
            bizType = "'label_print'", bizId = "#bizId")
    public Result<Void> printLog(@RequestParam String bizId) {
        return Result.success();
    }
}
