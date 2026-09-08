package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.QualityArchiveQueryDTO;
import com.jjx.production.domain.vo.QualityArchiveVO;
import com.jjx.production.service.QualityArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "质量记录归档")
@RestController
@RequestMapping("/production/quality-archive")
@RequiredArgsConstructor
public class QualityArchiveController {
    private final QualityArchiveService service;

    @Operation(summary = "分页查询归档清单")
    @GetMapping("/page")
    @SaCheckPermission("production:quality-template:view")
    public Result<PageResult<QualityArchiveVO>> page(QualityArchiveQueryDTO query) {
        return Result.success(service.page(query));
    }
}
