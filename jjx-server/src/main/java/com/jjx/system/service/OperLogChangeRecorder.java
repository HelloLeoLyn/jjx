package com.jjx.system.service;

import cn.hutool.json.JSONUtil;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 操作日志“字段级变更内容”记录器
 *
 * 后端权威 diff：调用方传入可编辑字段白名单的旧值/新值对比，把变更内容写入
 * sys_oper_log（operParam=人读摘要，detail={"changes":[...]}）。
 * 销售订单 / 销售询价等“整单提交、后端判变更”的编辑场景共用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogChangeRecorder {

    private final LogSaveService logSaveService;

    /**
     * 记录一次“修改”操作日志（业务类型固定为 UPDATE）
     *
     * @param module    日志模块名（如 销售订单 / 询价单管理）
     * @param operUrl   操作标识（如 order.update / inquiry.update）
     * @param bizType   业务类型（如 order / inquiry）
     * @param bizId     业务单据ID
     * @param traceId   链路追踪ID（可空，由链路回退继承）
     * @param bizStatus 业务单据当前状态（编辑不改变状态，取旧值）
     * @param changes   字段级变更清单（可为空，记“无字段变更”）
     */
    public void recordUpdate(String module, String operUrl, String bizType, String bizId,
                             String traceId, Integer bizStatus, List<String> changes) {
        try {
            List<String> safe = changes == null ? List.of() : changes;
            String summary = safe.isEmpty() ? "无字段变更" : String.join("；", safe);

            SysOperLog operLog = new SysOperLog();
            operLog.setModule(module);
            operLog.setBusinessType(BusinessType.UPDATE.getCode());
            operLog.setOperUrl(operUrl);
            operLog.setBizType(bizType);
            operLog.setBizId(bizId);
            operLog.setTraceId(traceId);
            operLog.setBizStatus(bizStatus);
            operLog.setOperParam(summary);
            operLog.setDetail(JSONUtil.toJsonStr(Map.of("changes", safe)));
            operLog.setStatus(1);
            operLog.setCreateTime(LocalDateTime.now());
            try {
                operLog.setUsername(SecurityUtils.getUsername());
                operLog.setUserId(SecurityUtils.getUserId());
                operLog.setRealName(SecurityUtils.getRealName());
            } catch (Exception ignored) {
            }
            logSaveService.saveOperLog(operLog);
            log.info("{}修改已记录变更日志: {}（bizId={}）", module, summary, bizId);
        } catch (Exception e) {
            log.warn("记录{}修改变更日志失败: {}", module, e.getMessage());
        }
    }

    /** 追加一条字段级变更（值不同才记录） */
    public void diff(List<String> changes, String label, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(label + ":" + fmt(oldValue) + "→" + fmt(newValue));
        }
    }

    public String fmt(Object value) {
        return value == null ? "空" : String.valueOf(value);
    }

    public String fmtDate(Date date) {
        if (date == null) {
            return "空";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    public String fmtDate(LocalDate date) {
        return date == null ? "空" : date.toString();
    }
}
