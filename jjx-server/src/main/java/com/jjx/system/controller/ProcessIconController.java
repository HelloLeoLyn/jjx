package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import com.jjx.system.service.ProcessIconService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/engineering/process-icons")
@RequiredArgsConstructor
public class ProcessIconController {
    private final ProcessIconService icons;

    @PostMapping
    @Log(module = "工序图标", businessType = BusinessType.INSERT, bizType = "'engineering_jjx_icon'", bizId = "#result.data", action = "登记永久共享图标")
    public Result<Long> upload(@RequestParam MultipartFile file, @RequestParam String requestKey,
                              @RequestParam String label, @RequestParam String category) {
        return Result.success(icons.upload(file, requestKey, label, category));
    }
}
