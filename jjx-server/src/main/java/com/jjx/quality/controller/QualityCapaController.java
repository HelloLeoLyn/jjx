package com.jjx.quality.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.quality.domain.entity.QualityCapa;
import com.jjx.quality.service.QualityCapaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/quality/capa")
public class QualityCapaController {
    private final QualityCapaService service;

    @GetMapping("/page")
    @SaCheckPermission("quality:capa:view")
    public Result<Page<QualityCapa>> page(@RequestParam(defaultValue="1") long pageNum,
            @RequestParam(defaultValue="10") long pageSize, @RequestParam(required=false) String status,
            @RequestParam(required=false) Long ncrId) {
        return Result.success(service.page(pageNum, pageSize, status, ncrId));
    }

    @PostMapping
    @SaCheckPermission("quality:ncr:dispose")
    public Result<QualityCapa> create(@RequestBody QualityCapa capa) { return Result.success(service.create(capa)); }

    @PutMapping("/{id}/advance")
    @SaCheckPermission("quality:ncr:dispose")
    public Result<QualityCapa> advance(@PathVariable Long id, @RequestBody QualityCapa capa) {
        return Result.success(service.advance(id, capa));
    }
}
