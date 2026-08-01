package com.jjx.system.service;

import com.jjx.system.domain.entity.SysErrorLog;
import com.jjx.system.domain.entity.SysLoginLog;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysErrorLogMapper;
import com.jjx.system.mapper.SysLoginLogMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogSaveService {

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysErrorLogMapper errorLogMapper;

    @Async("logExecutor")
    public void saveOperLog(SysOperLog operLog) {
        try {
            operLogMapper.insert(operLog);
        } catch (Exception e) {
            log.error("保存操作日志失败: {}", e.getMessage());
        }
    }

    @Async("logExecutor")
    public void saveLoginLog(SysLoginLog loginLog) {
        try {
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("保存登录日志失败: {}", e.getMessage());
        }
    }

    /**
     * 按 bizType+bizId 从历史操作日志继承 traceId（同单据所有操作共享链路）
     */
    public String findTraceIdByBiz(String bizType, String bizId) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysOperLog> w =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            if (bizType != null && !bizType.isEmpty()) {
                w.eq(SysOperLog::getBizType, bizType);
            }
            w.eq(SysOperLog::getBizId, bizId)
                    .isNotNull(SysOperLog::getTraceId)
                    .ne(SysOperLog::getTraceId, "")
                    .orderByDesc(SysOperLog::getId)
                    .last("LIMIT 1");
            SysOperLog log = operLogMapper.selectOne(w);
            return log != null ? log.getTraceId() : null;
        } catch (Exception e) {
            log.error("查询历史traceId失败: {}", e.getMessage());
            return null;
        }
    }

    @Async("logExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveErrorLog(SysErrorLog errorLog) {
        try {
            errorLogMapper.insert(errorLog);
        } catch (Exception e) {
            log.error("保存错误日志失败: {}", e.getMessage());
        }
    }
}
