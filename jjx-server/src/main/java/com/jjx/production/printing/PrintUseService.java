package com.jjx.production.printing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产实际用版（P10，dev-20261125-017）。
 * 开工/换版在调用方事务内执行：锁网版、校验状态与版本、关闭旧段、写入新段。
 * 并发由 uk_print_use_segment_exec_open / uk_print_use_segment_plate_open 保证。
 */
@Service
@RequiredArgsConstructor
public class PrintUseService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final com.jjx.engineering.printing.artwork.ArtworkService artworkService;
    private final com.jjx.engineering.printing.screen.ScreenService screenService;

    /** 只读取规范明细中的冻结要求；资料缺失不能回退当前路线或旧网框参数。 */
    public record Plan(Long artworkVersionId, Long defaultPlateId, String requirements) {}

    @Transactional(readOnly = true)
    public Plan requirePlan(Long specVersionId, Long routingItemId) {
        if (specVersionId == null || routingItemId == null)
            throw new BusinessException(422, "印刷工序缺少绑定规范或工序行，不能开工");
        var rows = jdbc.queryForList("""
            SELECT snapshot_json FROM product_work_spec_item
            WHERE spec_version_id=? AND resource_type='DOCSET_JSON'
            """, specVersionId);
        if (rows.size() != 1) throw new BusinessException(422, "规范印刷快照缺失或不唯一，不能开工");
        try {
            var snapshot = json.readTree(String.valueOf(rows.getFirst().get("snapshot_json")));
            if (snapshot == null || !snapshot.path("printingItems").isArray())
                throw new BusinessException(422, "规范缺少冻结印刷要求，不能开工");
            Plan plan = null;
            for (var row : snapshot.path("printingItems")) {
                if (row.path("routingItemId").asLong() != routingItemId) continue;
                if (plan != null || row.path("artworkVersionId").asLong() <= 0 || !row.path("requirements").isObject())
                    throw new BusinessException(422, "规范印刷要求不完整或工序行重复，不能开工");
                plan = new Plan(row.path("artworkVersionId").longValue(),
                        row.hasNonNull("defaultPlateId") ? row.get("defaultPlateId").longValue() : null,
                        row.get("requirements").toString());
            }
            if (plan == null) throw new BusinessException(422, "规范未包含该印刷工序，不能开工");
            return plan;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new BusinessException(422, "规范印刷快照格式异常，不能开工");
        }
    }

    private Map<String,Object> executionIdentity(long executionId, boolean lock) {
        var rows = jdbc.queryForList("""
            SELECT e.execution_id,e.routing_item_id,e.major_category,o.work_spec_version_id
            FROM production_operation_execution e JOIN production_order o ON o.order_id=e.order_id
            WHERE e.execution_id=?
            """ + (lock ? " FOR UPDATE" : ""), executionId);
        if (rows.size()!=1) throw new BusinessException(404, "印刷工序执行不存在");
        var row=rows.getFirst();
        if (!"PRINT".equals(row.get("major_category"))) throw new BusinessException(422, "该工序不是印刷工序");
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> start(PrintUseCommands c) {
        c.validate();
        String operator = SecurityUtils.getUsername();
        if (operator == null || operator.isBlank()) throw new BusinessException(403, "无法确认当前操作人");

        var identity = executionIdentity(c.executionId(), false);
        Long specId = identity.get("work_spec_version_id") == null ? null : ((Number) identity.get("work_spec_version_id")).longValue();
        Long routingId = identity.get("routing_item_id") == null ? null : ((Number) identity.get("routing_item_id")).longValue();
        var plan = requirePlan(specId, routingId);
        if ((c.specVersionId()!=null && !c.specVersionId().equals(specId))
                || (c.routingItemId()!=null && !c.routingItemId().equals(routingId))
                || (c.versionId()!=null && !c.versionId().equals(plan.artworkVersionId())))
            throw new BusinessException(422, "用版指令与工单绑定的规范印刷要求不一致");
        long plateVersion = plan.artworkVersionId();
        // 版本→框→版→执行；参数复核在写用版段之前，备用版不修改已发布规范。
        artworkService.requireUsableVersion(plateVersion);
        screenService.requireUsablePlate(plateVersion, c.plateId(), plan.requirements());
        screenService.requireAvailableForUse(c.plateId(),c.executionId(),null);
        if (!identity.equals(executionIdentity(c.executionId(), true)))
            throw new BusinessException(409, "工单印刷要求已变化，请刷新");

        // 同一工序换版：关闭旧段，不覆盖历史
        jdbc.update("""
            UPDATE print_use_segment SET end_time=NOW(3), reason=COALESCE(?,reason),
                   update_by=?, update_time=NOW(3), lock_version=lock_version+1
            WHERE execution_id=? AND end_time IS NULL
            """, c.reason(), operator, c.executionId());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("executionId", c.executionId());
        snapshot.put("specVersionId", specId);
        snapshot.put("routingItemId", routingId);
        snapshot.put("versionId", plateVersion);
        snapshot.put("plateId", c.plateId());
        snapshot.put("operator", operator);
        snapshot.put("requirements",screenService.validateRequirements(plan.requirements()));

        try {
            jdbc.update("""
                INSERT INTO print_use_segment(execution_id,spec_version_id,routing_item_id,version_id,plate_id,
                       start_time,operator_id,operator_name,reason,request_id,snapshot,create_by,create_time)
                VALUES(?,?,?,?,?,NOW(3),?,?,?,?,?,?,NOW(3))
                """, c.executionId(), specId, routingId, plateVersion, c.plateId(),
                    operator, operator, c.reason(), c.requestId(), json.writeValueAsString(snapshot), operator);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "该网版或工序已有进行中的用版记录");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("用版快照序列化失败", e);
        }

        Long segmentId = jdbc.queryForObject("SELECT last_insert_id()", Long.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("segmentId", segmentId);
        result.put("executionId", c.executionId());
        result.put("plateId", c.plateId());
        result.put("versionId", plateVersion);
        result.put("status", "OPEN");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> end(Long executionId, String reason) {
        if (executionId == null) throw new BusinessException(422, "缺少工序执行ID");
        String operator = SecurityUtils.getUsername();
        int rows = jdbc.update("""
            UPDATE print_use_segment SET end_time=NOW(3), reason=COALESCE(?,reason),
                   update_by=?, update_time=NOW(3), lock_version=lock_version+1
            WHERE execution_id=? AND end_time IS NULL
            """, reason, operator, executionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", executionId);
        result.put("closed", rows);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long executionId) {
        if (executionId == null) return List.of();
        return jdbc.queryForList("""
            SELECT s.segment_id AS segmentId, s.execution_id AS executionId, s.version_id AS versionId,
                   s.plate_id AS plateId, s.start_time AS startTime, s.end_time AS endTime,
                   s.operator_name AS operatorName, s.reason, s.snapshot,
                   p.plate_no AS plateNo, f.frame_no AS frameNo, a.artwork_no AS artworkNo, v.version_no AS versionNo
            FROM print_use_segment s
            LEFT JOIN engineering_screen_plate p ON p.plate_id=s.plate_id
            LEFT JOIN engineering_screen_frame f ON f.frame_id=p.frame_id
            LEFT JOIN print_artwork_version v ON v.version_id=s.version_id
            LEFT JOIN print_artwork a ON a.artwork_id=v.artwork_id
            WHERE s.execution_id=? ORDER BY s.start_time, s.segment_id
            """, executionId);
    }
}
