package com.jjx.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.printing.artwork.ArtworkCommands;
import com.jjx.product.service.FilmCreationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/engineering/printing/artworks")
@RequiredArgsConstructor
public class FilmCreationController {
    private final FilmCreationService service;

    @PostMapping("/with-initial-version")
    @SaCheckPermission("engineering:film:edit")
    public Result<Map<String, Object>> create(@RequestBody ArtworkCommands command) {
        return Result.success(service.create(command));
    }

    @ExceptionHandler(cn.dev33.satoken.exception.NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> denied() {
        return Result.error(403, "无权新增菲林");
    }
}
