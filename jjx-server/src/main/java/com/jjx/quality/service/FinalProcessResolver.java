package com.jjx.quality.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 末道（成品）工序判定 —— dev-20260917-006
 *
 * 口径（2026-09-17 用户定）：是否能起成品检验由"工程标记的末道工序"决定；
 * 判定顺序：① 工序执行上的标记 → ② 工序主数据（engineering_standard_process）标记
 *          → ③ 都没有时退化为"该工单内 process_order 最大的一道"（兼容老数据，仅提示）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinalProcessResolver {

    private final JdbcTemplate jdbcTemplate;

    /** 是否末道工序（无标记时退化为顺序最大） */
    public boolean isFinalExecution(Long executionId) {
        if (executionId == null) {
            return false;
        }
        Integer execFlag = queryInt("SELECT is_final_process FROM production_operation_execution WHERE execution_id = ?", executionId);
        if (execFlag != null && execFlag == 1) {
            return true;
        }
        Long processId = queryLong("SELECT process_id FROM production_operation_execution WHERE execution_id = ?", executionId);
        if (processId != null) {
            Integer spFlag = queryInt("SELECT is_final_process FROM engineering_standard_process WHERE process_id = ?", processId);
            if (spFlag != null && spFlag == 1) {
                return true;
            }
        }
        // 兜底：整单没有任何末道标记时，取 process_order 最大的一道
        Long orderId = queryLong("SELECT order_id FROM production_operation_execution WHERE execution_id = ?", executionId);
        if (orderId == null) {
            return false;
        }
        Integer marked = queryInt("SELECT COUNT(*) FROM production_operation_execution WHERE order_id = ? AND is_final_process = 1", orderId);
        if (marked != null && marked > 0) {
            return false; // 已显式标记了别的工序，当前工序不是末道
        }
        Integer maxOrder = queryInt("SELECT MAX(process_order) FROM production_operation_execution WHERE order_id = ?", orderId);
        Integer currentOrder = queryInt("SELECT process_order FROM production_operation_execution WHERE execution_id = ?", executionId);
        boolean fallback = maxOrder != null && currentOrder != null && maxOrder.equals(currentOrder);
        if (fallback) {
            log.info("工单[{}] 未标记末道工序，按顺序最大退化判定 execution={} 为末道（建议工程补标记）", orderId, executionId);
        }
        return fallback;
    }

    private Integer queryInt(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, args);
        } catch (Exception e) {
            return null;
        }
    }

    private Long queryLong(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, args);
        } catch (Exception e) {
            return null;
        }
    }
}
