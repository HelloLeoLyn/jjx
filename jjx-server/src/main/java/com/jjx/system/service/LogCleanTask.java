package com.jjx.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.system.config.LogCleanProperties;
import com.jjx.system.domain.entity.SysErrorLog;
import com.jjx.system.domain.entity.SysLoginLog;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysErrorLogMapper;
import com.jjx.system.mapper.SysLoginLogMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogCleanTask {

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysErrorLogMapper errorLogMapper;
    private final LogArchiveService archiveService;
    private final LogCleanProperties properties;

    // 每天凌晨 2:00:00
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanOperLogs() {
        if (Boolean.FALSE.equals(properties.getEnabled())) {
            return;
        }

        log.info("开始清理操作日志...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(properties.getOperLogRetentionDays());

        while (true) {
            List<SysOperLog> logs = operLogMapper.selectList(
                    new LambdaQueryWrapper<SysOperLog>()
                            .lt(SysOperLog::getCreateTime, threshold)
                            .last("LIMIT " + properties.getBatchSize())
            );
            if (logs.isEmpty()) {
                break;
            }

            archiveService.archiveOperLogs(logs);
            operLogMapper.deleteBatchIds(logs.stream().map(SysOperLog::getId).toList());

            if (logs.size() < properties.getBatchSize()) {
                break;
            }
        }
        log.info("操作日志清理完成");
    }

    // 每天凌晨 3:00:00
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanLoginLogs() {
        if (!properties.getEnabled()) {
            return;
        }

        log.info("开始清理登录日志...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(properties.getLoginLogRetentionDays());

        while (true) {
            List<SysLoginLog> logs = loginLogMapper.selectList(
                    new LambdaQueryWrapper<SysLoginLog>()
                            .lt(SysLoginLog::getLoginTime, threshold)
                            .last("LIMIT " + properties.getBatchSize())
            );
            if (logs.isEmpty()) {
                break;
            }

            archiveService.archiveLoginLogs(logs);
            loginLogMapper.deleteBatchIds(logs.stream().map(SysLoginLog::getId).toList());

            if (logs.size() < properties.getBatchSize()) {
                break;
            }
        }
        log.info("登录日志清理完成");
    }

    @Scheduled(cron = "0 0 4 * * ?")
    @Transactional
    public void cleanErrorLogs() {
        if (!properties.getEnabled()) {
            return;
        }

        log.info("开始清理错误日志...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(properties.getErrorLogRetentionDays());

        while (true) {
            List<SysErrorLog> logs = errorLogMapper.selectList(
                    new LambdaQueryWrapper<SysErrorLog>()
                            .lt(SysErrorLog::getTriggerTime, threshold)
                            .eq(SysErrorLog::getHandleStatus, 1)
                            .last("LIMIT " + properties.getBatchSize())
            );
            if (logs.isEmpty()) {
                break;
            }

            archiveService.archiveErrorLogs(logs);
            errorLogMapper.deleteBatchIds(logs.stream().map(SysErrorLog::getId).toList());

            if (logs.size() < properties.getBatchSize()) {
                break;
            }
        }
        log.info("错误日志清理完成");
    }

    @Scheduled(cron = "0 0 12 * * ?")
    public void logStatistics() {
        log.info("【日志统计】操作:{} 登录:{} 错误:{}",
                operLogMapper.selectCount(null),
                loginLogMapper.selectCount(null),
                errorLogMapper.selectCount(null));
    }
}
