package com.jjx.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.service.IFilmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "工程薄膜管理")
@RestController
@RequestMapping("/engineering/film")
@RequiredArgsConstructor
public class FilmController {
    private final IFilmService filmService;

    @Operation(summary = "薄膜列表")
    @SaCheckPermission("engineering:film:view")
    @GetMapping("/page")
    public Result<?> page() {
        return Result.success(filmService.listPage(null));
    }
}
