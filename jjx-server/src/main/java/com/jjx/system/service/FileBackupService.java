package com.jjx.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.notification.domain.dto.NotificationCreateDTO;
import com.jjx.notification.service.NotificationService;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.vo.FileStatsVO;
import com.jjx.system.mapper.SysAttachmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件备份调度 + 容量预警（DEV-736）
 * <ul>
 *   <li>每日 23:30：增量备份当日新增附件到 backup/daily/{日期}/（镜像相对路径）</li>
 *   <li>每周日 03:00：全量复制 upload（business/product 等，排除 backup）到 backup/weekly/{日期}_full/</li>
 *   <li>每小时第 5 分钟：容量预警检查（单日新增 > 阈值 / 磁盘占用 > 阈值 → 站内通知 admin）</li>
 * </ul>
 * 阈值配置（sys_config）：file.alert.daily_size（MB，默认500）、file.alert.total_percent（%，默认80）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileBackupService {

    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;

    /** 备份根目录，默认 upload/backup */
    @Value("${file.backup.path:}")
    private String backupBasePath;

    private final SysAttachmentMapper attachmentMapper;
    private final SysConfigService sysConfigService;
    private final NotificationService notificationService;
    private final ProductMapper productMapper;
    private final ISysAttachmentService attachmentService;

    /** 预警去重：同一天同类预警只发一次 */
    private volatile String lastDailyAlertDate = "";
    private volatile String lastTotalAlertDate = "";

    // ==================== 定时调度 ====================

    /** 每日 23:30 增量备份当日新增附件 */
    @Scheduled(cron = "0 30 23 * * ?")
    public void dailyBackup() {
        try {
            FileStatsVO.BackupResult result = runDailyBackup();
            log.info("[文件备份] 日备份完成: {}", result);
        } catch (Exception e) {
            log.error("[文件备份] 日备份异常: {}", e.getMessage(), e);
        }
    }

    /** 每周日 03:00 全量备份 */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void weeklyBackup() {
        try {
            FileStatsVO.BackupResult result = runWeeklyBackup();
            log.info("[文件备份] 周全量备份完成: {}", result);
        } catch (Exception e) {
            log.error("[文件备份] 周全量备份异常: {}", e.getMessage(), e);
        }
    }

    /** 每小时第 5 分钟容量预警检查 */
    @Scheduled(cron = "0 5 * * * ?")
    public void alertCheck() {
        try {
            FileStatsVO.AlertResult result = runAlertCheck();
            log.info("[文件备份] 容量预警检查完成: dailyAlert={}, totalAlert={}", result.getDailyAlert(), result.getTotalAlert());
        } catch (Exception e) {
            log.error("[文件备份] 容量预警检查异常: {}", e.getMessage(), e);
        }
    }

    /** 每日 03:30 清理回收站中删除超过30天的附件（DEV-737） */
    @Scheduled(cron = "0 30 3 * * ?")
    public void recycleClean() {
        try {
            int count = attachmentService.permanentDeleteExpired(30);
            if (count > 0) {
                log.info("[回收站] 定时清理完成: {} 个附件", count);
            }
        } catch (Exception e) {
            log.error("[回收站] 定时清理异常: {}", e.getMessage(), e);
        }
    }

    // ==================== 手动入口（Controller 调用） ====================

    public FileStatsVO.BackupResult runDailyBackup() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<SysAttachment> todayList = attachmentMapper.selectList(
                new LambdaQueryWrapper<SysAttachment>()
                        .ge(SysAttachment::getCreateTime, todayStart)
                        .orderByAsc(SysAttachment::getCreateTime));
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path targetRoot = backupRoot().resolve("daily").resolve(dateStr);

        int success = 0;
        long totalBytes = 0;
        for (SysAttachment att : todayList) {
            try {
                Path src = Paths.get(uploadBasePath, att.getFilePath());
                if (!Files.exists(src)) continue;
                Path dest = targetRoot.resolve(att.getFilePath());
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                success++;
                totalBytes += Files.size(src);
            } catch (IOException e) {
                log.warn("[文件备份] 单文件备份失败: path={}, err={}", att.getFilePath(), e.getMessage());
            }
        }
        writeBackupLog(targetRoot, String.format("daily %s 成功%d/%d 共%dMB",
                dateStr, success, todayList.size(), totalBytes / 1024 / 1024));
        cleanupOldBackups("daily", 14);
        log.info("[文件备份] 日备份: 共{}个 成功{}个 {}MB", todayList.size(), success, totalBytes / 1024 / 1024);
        return new FileStatsVO.BackupResult("daily", dateStr, todayList.size(), success, totalBytes);
    }

    public FileStatsVO.BackupResult runWeeklyBackup() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path targetRoot = backupRoot().resolve("weekly").resolve(dateStr + "_full");
        long copied = copyDirRecursive(Paths.get(uploadBasePath), targetRoot, "backup");
        writeBackupLog(targetRoot, String.format("weekly %s 全量复制 %d 个文件", dateStr, copied));
        cleanupOldBackups("weekly", 8);
        log.info("[文件备份] 周全量: {}个文件", copied);
        return new FileStatsVO.BackupResult("weekly", dateStr, (int) copied, (int) copied, 0L);
    }

    public FileStatsVO.AlertResult runAlertCheck() {
        FileStatsVO stats = stats();
        String today = LocalDate.now().toString();
        boolean dailyAlert = Boolean.TRUE.equals(stats.getDailyAlert());
        boolean totalAlert = Boolean.TRUE.equals(stats.getTotalAlert());

        if (dailyAlert && !today.equals(lastDailyAlertDate)) {
            lastDailyAlertDate = today;
            sendAlert("文件增长异常预警",
                    String.format("今日新增附件 %s，超过阈值 %dMB（sys_config: file.alert.daily_size），疑似大文件批量上传，请到「文件管理」页查看清理。",
                            formatSize(stats.getTodayAddedSize()), dailyThresholdMB()));
        }
        if (totalAlert && !today.equals(lastTotalAlertDate)) {
            lastTotalAlertDate = today;
            sendAlert("存储空间预警",
                    String.format("upload 目录磁盘总占用已达 %.1f%%（阈值 %d%%），请到「文件管理」页清理或迁移（sys_config: file.alert.total_percent）。",
                            stats.getUsedPercent(), totalPercentThreshold()));
        }
        return new FileStatsVO.AlertResult(dailyAlert, totalAlert);
    }

    /** 文件统计（管理页用） */
    public FileStatsVO stats() {        FileStatsVO vo = new FileStatsVO();
        Path uploadRoot = Paths.get(uploadBasePath);

        // 目录扫描：总大小/总数（排除 backup）
        long totalSize = 0, totalCount = 0;
        if (Files.exists(uploadRoot)) {
            try (Stream<Path> stream = Files.walk(uploadRoot)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    if (Files.isDirectory(p)) continue;
                    Path rel = uploadRoot.relativize(p);
                    if (rel.startsWith("backup")) continue;
                    try {
                        totalSize += Files.size(p);
                        totalCount++;
                    } catch (IOException ignore) {
                    }
                }
            } catch (IOException e) {
                log.warn("[文件管理] 扫描上传目录失败: {}", e.getMessage());
            }
        }
        vo.setTotalSize(totalSize);
        vo.setTotalCount(totalCount);

        // 磁盘占用率
        double usedPercent = 0;
        try {
            FileStore store = Files.getFileStore(uploadRoot);
            long totalSpace = store.getTotalSpace();
            long usable = store.getUsableSpace();
            usedPercent = totalSpace > 0 ? (totalSpace - usable) * 100.0 / totalSpace : 0;
        } catch (IOException ignore) {
        }
        vo.setUsedPercent(Math.round(usedPercent * 10) / 10.0);

        // 今日新增（数据库）
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<SysAttachment> todayList = attachmentMapper.selectList(
                new LambdaQueryWrapper<SysAttachment>().ge(SysAttachment::getCreateTime, todayStart));
        long todaySize = todayList.stream()
                .mapToLong(a -> a.getFileSize() == null ? 0 : a.getFileSize()).sum();
        vo.setTodayAddedSize(todaySize);
        vo.setTodayAddedCount((long) todayList.size());

        // 近30天按日期
        LocalDateTime monthAgo = LocalDate.now().minusDays(29).atStartOfDay();
        List<SysAttachment> monthList = attachmentMapper.selectList(
                new LambdaQueryWrapper<SysAttachment>().ge(SysAttachment::getCreateTime, monthAgo));
        Map<String, long[]> dateMap = new LinkedHashMap<>();
        for (SysAttachment a : monthList) {
            String d = a.getCreateTime() == null ? "未知" : a.getCreateTime().toLocalDate().toString();
            long[] v = dateMap.computeIfAbsent(d, k -> new long[2]);
            v[0]++;
            v[1] += a.getFileSize() == null ? 0 : a.getFileSize();
        }
        List<FileStatsVO.DailyStat> dailyStats = new ArrayList<>();
        dateMap.forEach((d, v) -> {
            FileStatsVO.DailyStat ds = new FileStatsVO.DailyStat();
            ds.setDate(d);
            ds.setCount(v[0]);
            ds.setSize(v[1]);
            dailyStats.add(ds);
        });
        dailyStats.sort(Comparator.comparing(FileStatsVO.DailyStat::getDate));
        vo.setDailyStats(dailyStats);

        // 按 bizType 统计 + 大文件 Top20
        List<SysAttachment> all = attachmentMapper.selectList(null);
        Map<String, long[]> bizMap = new LinkedHashMap<>();
        for (SysAttachment a : all) {
            String b = a.getBizType() == null || a.getBizType().isBlank() ? "未分类" : a.getBizType();
            long[] v = bizMap.computeIfAbsent(b, k -> new long[2]);
            v[0]++;
            v[1] += a.getFileSize() == null ? 0 : a.getFileSize();
        }
        List<FileStatsVO.BizTypeStat> bizStats = new ArrayList<>();
        bizMap.forEach((b, v) -> {
            FileStatsVO.BizTypeStat bs = new FileStatsVO.BizTypeStat();
            bs.setBizType(b);
            bs.setCount(v[0]);
            bs.setSize(v[1]);
            bizStats.add(bs);
        });
        bizStats.sort(Comparator.comparing(FileStatsVO.BizTypeStat::getSize).reversed());
        vo.setBizTypeStats(bizStats);

        all.sort(Comparator.comparing(a -> a.getFileSize() == null ? 0L : a.getFileSize(), Comparator.reverseOrder()));
        vo.setTopFiles(all.size() > 20 ? new ArrayList<>(all.subList(0, 20)) : all);

        // 预警状态
        vo.setDailyAlert(todaySize > dailyThresholdMB() * 1024L * 1024L);
        vo.setTotalAlert(usedPercent >= totalPercentThreshold());
        vo.setBackupPath(backupRoot().toString());
        return vo;
    }

    // ==================== 产品文件迁移工具（DEV-737） ====================

    /**
     * 一次性迁移工具：扫描源目录下的产品目录（如桌面 jjx/JST-xxx/）
     * 规则：目录名括号前部分=产品编码（如 JST-263MEMC(7600-005 Rev003) → JST-263MEMC）
     * 产品存在才迁移；第一层子目录=类别；保持子目录结构；重复文件跳过
     */
    @Transactional(rollbackFor = Exception.class)
    public FileStatsVO.MigrateResult migrateProductFiles(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new BusinessException("源目录不能为空");
        }
        Path srcRoot = Paths.get(sourcePath);
        if (!Files.exists(srcRoot) || !Files.isDirectory(srcRoot)) {
            throw new BusinessException("源目录不存在: " + sourcePath);
        }

        List<String> skippedProducts = new ArrayList<>();
        int productCount = 0, fileCount = 0, successCount = 0, skippedFiles = 0;

        try (Stream<Path> dirs = Files.list(srcRoot)) {
            for (Path productDir : (Iterable<Path>) dirs::iterator) {
                if (!Files.isDirectory(productDir)) continue;
                String dirName = productDir.getFileName().toString();
                // 产品编码：括号前部分（如 JST-263MEMC(7600-005 Rev003) → JST-263MEMC）
                String productCode = dirName.split("\\(")[0].trim();
                Product product = productMapper.selectOne(
                        new LambdaQueryWrapper<Product>().eq(Product::getProductCode, productCode));
                if (product == null) {
                    skippedProducts.add(dirName);
                    log.info("[文件迁移] 跳过未建档产品目录: {}", dirName);
                    continue;
                }
                productCount++;

                // 已迁移文件路径（幂等去重）
                List<SysAttachment> existing = attachmentMapper.selectList(
                        new LambdaQueryWrapper<SysAttachment>()
                                .eq(SysAttachment::getBizType, "product")
                                .eq(SysAttachment::getBizId, product.getProductId()));
                Set<String> existingPaths = existing.stream()
                        .map(SysAttachment::getFilePath).collect(Collectors.toSet());

                try (Stream<Path> files = Files.walk(productDir)) {
                    for (Path f : (Iterable<Path>) files::iterator) {
                        if (Files.isDirectory(f)) continue;
                        String fileName = f.getFileName().toString();
                        if (isSystemFile(fileName)) {
                            skippedFiles++;
                            continue;
                        }
                        fileCount++;
                        Path rel = productDir.relativize(f);
                        String category = rel.getNameCount() > 0 ? rel.getName(0).toString() : "未分类";
                        String subPath = rel.toString().replace('\\', '/');
                        String relativePath = "product" + File.separator + productCode + File.separator + subPath;
                        if (existingPaths.contains(relativePath)) {
                            skippedFiles++;
                            continue;
                        }
                        Path dest = Paths.get(uploadBasePath, relativePath);
                        try {
                            Files.createDirectories(dest.getParent());
                            Files.copy(f, dest, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            log.warn("[文件迁移] 复制失败: {} err={}", f, e.getMessage());
                            skippedFiles++;
                            continue;
                        }

                        SysAttachment att = new SysAttachment();
                        att.setBizType("product");
                        att.setBizId(product.getProductId());
                        att.setCategory(category);
                        att.setFileName(fileName);
                        att.setFilePath(relativePath);
                        try {
                            att.setFileSize(Files.size(dest));
                        } catch (IOException ignore) {
                        }
                        att.setFileType(guessFileType(fileName));
                        attachmentMapper.insert(att);
                        successCount++;
                    }
                }
            }
        } catch (IOException e) {
            log.error("[文件迁移] 迁移失败: {}", e.getMessage(), e);
            throw new BusinessException("产品文件迁移失败: " + e.getMessage());
        }

        log.info("[文件迁移] 完成: 产品{}个 文件{}/成功{} 跳过{} 未匹配{}",
                productCount, fileCount, successCount, skippedFiles, skippedProducts.size());
        return new FileStatsVO.MigrateResult(productCount, fileCount, successCount, skippedFiles, skippedProducts);
    }

    private boolean isSystemFile(String name) {
        String n = name.toLowerCase();
        return n.equals("thumbs.db") || n.equals(".ds_store") || n.equals("desktop.ini");
    }

    private String guessFileType(String fileName) {
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";
        switch (ext) {
            case "pdf": return "application/pdf";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "bmp": return "image/bmp";
            case "webp": return "image/webp";
            case "cdr": return "application/x-cdr";
            case "dwg": return "application/acad";
            case "dxf": return "application/dxf";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "zip": return "application/zip";
            default: return "application/octet-stream";
        }
    }

    // ==================== 内部方法 ====================

    private Path backupRoot() {
        if (backupBasePath == null || backupBasePath.isBlank()) {
            return Paths.get(uploadBasePath, "backup");
        }
        return Paths.get(backupBasePath);
    }

    private long copyDirRecursive(Path srcRoot, Path destRoot, String excludeDir) {
        long count = 0;
        if (!Files.exists(srcRoot)) return 0;
        try (Stream<Path> stream = Files.walk(srcRoot)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                if (Files.isDirectory(src)) continue;
                Path rel = srcRoot.relativize(src);
                if (rel.startsWith(excludeDir)) continue;
                Path dest = destRoot.resolve(rel.toString());
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        } catch (IOException e) {
            log.error("[文件备份] 目录复制失败: {} -> {}, err={}", srcRoot, destRoot, e.getMessage());
        }
        return count;
    }

    private void cleanupOldBackups(String type, int keepDays) {
        Path root = backupRoot().resolve(type);
        if (!Files.exists(root)) return;
        LocalDate cutoff = LocalDate.now().minusDays(keepDays);
        try (Stream<Path> stream = Files.list(root)) {
            for (Path dir : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(dir)) continue;
                String name = dir.getFileName().toString();
                // daily: yyyy-MM-dd；weekly: yyyy-MM-dd_full
                String datePart = name.endsWith("_full") ? name.substring(0, name.length() - 5) : name;
                try {
                    LocalDate d = LocalDate.parse(datePart);
                    if (d.isBefore(cutoff)) {
                        deleteRecursive(dir);
                        log.info("[文件备份] 清理过期备份: {}", dir);
                    }
                } catch (Exception ignore) {
                    // 非日期目录不处理
                }
            }
        } catch (IOException e) {
            log.warn("[文件备份] 清理备份目录失败: {}", e.getMessage());
        }
    }

    private void deleteRecursive(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignore) {
                }
            });
        } catch (IOException ignore) {
        }
    }

    private void writeBackupLog(Path targetRoot, String msg) {
        try {
            Files.createDirectories(targetRoot);
            Files.writeString(targetRoot.resolve("backup.log"),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + msg + "\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("[文件备份] 写备份日志失败: {}", e.getMessage());
        }
    }

    private int dailyThresholdMB() {
        String v = sysConfigService.getValue("file.alert.daily_size");
        try {
            return v == null ? 500 : Math.max(1, Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return 500;
        }
    }

    private int totalPercentThreshold() {
        String v = sysConfigService.getValue("file.alert.total_percent");
        try {
            return v == null ? 80 : Math.min(99, Math.max(1, Integer.parseInt(v.trim())));
        } catch (NumberFormatException e) {
            return 80;
        }
    }

    private void sendAlert(String title, String content) {
        NotificationCreateDTO dto = new NotificationCreateDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setNotificationType("system");
        dto.setPriority("high");
        dto.setReceiverId(1L);
        dto.setReceiverName("系统管理员");
        try {
            notificationService.createNotification(dto);
            log.info("[文件备份] 预警通知已发送: {}", title);
        } catch (Exception e) {
            log.error("[文件备份] 预警通知发送失败: {}", e.getMessage());
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1fMB", bytes / 1024.0 / 1024);
        return String.format("%.2fGB", bytes / 1024.0 / 1024 / 1024);
    }
}
