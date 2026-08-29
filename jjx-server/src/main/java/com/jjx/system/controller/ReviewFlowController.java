package com.jjx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.service.ReviewFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/review-flow")
@RequiredArgsConstructor
public class ReviewFlowController {
    private final ReviewFlowService reviewFlowService;

    @GetMapping("/list")
    @SaCheckPermission("system:reviewFlow:view")
    public Result<List<ReviewFlow>> list(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.success(reviewFlowService.listByBiz(bizType, bizId));
    }
}
