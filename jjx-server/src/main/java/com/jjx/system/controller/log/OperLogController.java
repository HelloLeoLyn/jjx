package com.jjx.system.controller.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "操作日志管理")
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class OperLogController {

    private final SysOperLogMapper operLogMapper;

    @GetMapping("/oper")
    @Operation(summary = "分页查询操作日志")
    public Result<PageResult<SysOperLog>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<SysOperLog> wrapper = Wrappers.lambdaQuery();
        wrapper.orderByDesc(SysOperLog::getId);
        if (module != null) wrapper.like(SysOperLog::getModule, module);
        if (bizType != null) wrapper.eq(SysOperLog::getBizType, bizType);
        if (traceId != null) wrapper.eq(SysOperLog::getTraceId, traceId);
        if (status != null) wrapper.eq(SysOperLog::getStatus, status);
        Page<SysOperLog> page = new Page<>(pageNum, pageSize);
        Page<SysOperLog> result = operLogMapper.selectPage(page, wrapper);
        return Result.success(PageResult.build(result.getRecords(), result.getTotal()));
    }

    @GetMapping("/operation-log/{id}")
    @Operation(summary = "获取操作日志详情")
    public Result<SysOperLog> getById(@PathVariable Long id) {
        return Result.success(operLogMapper.selectById(id));
    }

    @DeleteMapping("/operation-log/{ids}")
    @Operation(summary = "删除操作日志")
    public Result<Void> delete(@PathVariable List<Long> ids) {
        operLogMapper.deleteBatchIds(ids);
        return Result.success();
    }

    @DeleteMapping("/operation-log/clean")
    @Operation(summary = "清空操作日志")
    public Result<Void> clean() {
        operLogMapper.delete(Wrappers.emptyWrapper());
        return Result.success();
    }
}
