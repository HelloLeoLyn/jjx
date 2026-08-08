package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import com.jjx.system.domain.vo.FileStatsVO;
import com.jjx.system.service.FileBackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件管理（统计/备份/预警，DEV-736）
 */
@Tag(name = "文件管理")
@RestController
@RequestMapping("/system/file")
@RequiredArgsConstructor
public class FileManageController {

    private final FileBackupService fileBackupService;

    @Operation(summary = "文件统计（总量/今日新增/按日期/按类型/大文件TopN/预警状态）")
    @GetMapping("/stats")
    public Result<FileStatsVO> stats() {
        return Result.success(fileBackupService.stats());
    }

    @Operation(summary = "手动执行每日增量备份")
    @PostMapping("/backup/daily")
    public Result<FileStatsVO.BackupResult> backupDaily() {
        return Result.success(fileBackupService.runDailyBackup());
    }

    @Operation(summary = "手动执行每周全量备份")
    @PostMapping("/backup/weekly")
    public Result<FileStatsVO.BackupResult> backupWeekly() {
        return Result.success(fileBackupService.runWeeklyBackup());
    }

    @Operation(summary = "手动触发容量预警检查")
    @PostMapping("/alert/check")
    public Result<FileStatsVO.AlertResult> alertCheck() {
        return Result.success(fileBackupService.runAlertCheck());
    }

    @Operation(summary = "产品文件迁移工具（扫描源目录→upload/product，产品须已建档）")
    @PostMapping("/migrate-product")
    public Result<FileStatsVO.MigrateResult> migrateProduct(@RequestParam String sourcePath) {
        return Result.success(fileBackupService.migrateProductFiles(sourcePath));
    }
}
