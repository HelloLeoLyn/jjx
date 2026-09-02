package com.jjx.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.domain.entity.ScreenMaster;
import com.jjx.engineering.service.IScreenMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "网版主数据管理")
@RestController
@RequestMapping("/engineering/screen")
@RequiredArgsConstructor
public class ScreenMasterController {

    private final IScreenMasterService screenService;

    @Operation(summary = "分页查询网版")
    @SaCheckPermission("engineering:screen:view")
    @GetMapping("/page")
    public Result<IPage<ScreenMaster>> page(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String screenNo,
                                            @RequestParam(required = false) String frameType,
                                            @RequestParam(required = false) String content,
                                            @RequestParam(required = false) Integer status) {
        return Result.success(screenService.page(pageNum, pageSize, screenNo, frameType, content, status));
    }

    @Operation(summary = "网版详情")
    @SaCheckPermission("engineering:screen:view")
    @GetMapping("/{screenId}")
    public Result<ScreenMaster> detail(@PathVariable Long screenId) {
        return Result.success(screenService.getById(screenId));
    }

    @Operation(summary = "新增网版")
    @SaCheckPermission("engineering:screen:add")
    @PostMapping
    public Result<Long> create(@RequestBody ScreenMaster screen) {
        return Result.success(screenService.create(screen));
    }

    @Operation(summary = "编辑网版")
    @SaCheckPermission("engineering:screen:edit")
    @PutMapping
    public Result<Void> update(@RequestBody ScreenMaster screen) {
        screenService.update(screen);
        return Result.success();
    }

    @Operation(summary = "生效/停用")
    @SaCheckPermission("engineering:screen:edit")
    @PutMapping("/{screenId}/status")
    public Result<Void> changeStatus(@PathVariable Long screenId, @RequestParam Integer status) {
        screenService.changeStatus(screenId, status);
        return Result.success();
    }

    @Operation(summary = "删除网版")
    @SaCheckPermission("engineering:screen:delete")
    @DeleteMapping("/{screenId}")
    public Result<Void> delete(@PathVariable Long screenId) {
        screenService.delete(screenId);
        return Result.success();
    }

    @Operation(summary = "网版联想（1225 印刷工序网框输入）")
    @GetMapping("/suggest")
    public Result<List<Map<String, Object>>> suggest(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer limit) {
        return Result.success(screenService.suggest(keyword, limit));
    }
}
