package com.jjx.system.domain.vo;

import com.jjx.system.domain.entity.SysAttachment;
import lombok.Data;

import java.util.List;

/**
 * 文件管理统计VO（DEV-736）
 */
@Data
public class FileStatsVO {

    /** 上传目录总大小（字节，不含 backup） */
    private Long totalSize;
    /** 上传目录文件总数 */
    private Long totalCount;
    /** 今日新增大小（字节） */
    private Long todayAddedSize;
    /** 今日新增数量 */
    private Long todayAddedCount;
    /** 磁盘占用率（0-100） */
    private Double usedPercent;
    /** 今日新增是否超阈值（file.alert.daily_size，默认500MB） */
    private Boolean dailyAlert;
    /** 总占用是否超阈值（file.alert.total_percent，默认80%） */
    private Boolean totalAlert;
    /** 备份目录 */
    private String backupPath;
    /** 近30天每日新增统计 */
    private List<DailyStat> dailyStats;
    /** 按业务类型统计 */
    private List<BizTypeStat> bizTypeStats;
    /** 大文件 Top20 */
    private List<SysAttachment> topFiles;

    @Data
    public static class DailyStat {
        private String date;
        private Long count;
        private Long size;
    }

    @Data
    public static class BizTypeStat {
        private String bizType;
        private Long count;
        private Long size;
    }

    /** 备份执行结果 */
    @Data
    public static class BackupResult {
        private String type;      // daily / weekly
        private String date;
        private Integer total;
        private Integer success;
        private Long totalBytes;

        public BackupResult() {}

        public BackupResult(String type, String date, Integer total, Integer success, Long totalBytes) {
            this.type = type;
            this.date = date;
            this.total = total;
            this.success = success;
            this.totalBytes = totalBytes;
        }
    }

    /** 预警检查结果 */
    @Data
    public static class AlertResult {
        private Boolean dailyAlert;
        private Boolean totalAlert;

        public AlertResult() {}

        public AlertResult(Boolean dailyAlert, Boolean totalAlert) {
            this.dailyAlert = dailyAlert;
            this.totalAlert = totalAlert;
        }
    }

    /** 产品文件迁移结果 */
    @Data
    public static class MigrateResult {
        private Integer productCount;      // 匹配到产品的目录数
        private Integer fileCount;         // 扫描到的文件总数
        private Integer successCount;      // 成功迁移数
        private Integer skippedFiles;      // 跳过文件数（系统文件/重复）
        private List<String> skippedProducts; // 未匹配产品的目录

        public MigrateResult() {}

        public MigrateResult(Integer productCount, Integer fileCount, Integer successCount,
                             Integer skippedFiles, List<String> skippedProducts) {
            this.productCount = productCount;
            this.fileCount = fileCount;
            this.successCount = successCount;
            this.skippedFiles = skippedFiles;
            this.skippedProducts = skippedProducts;
        }
    }
}
