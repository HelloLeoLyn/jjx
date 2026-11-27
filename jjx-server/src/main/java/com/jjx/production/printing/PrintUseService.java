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

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> start(PrintUseCommands c) {
        c.validate();
        String operator = SecurityUtils.getUsername();
        if (operator == null || operator.isBlank()) throw new BusinessException(403, "无法确认当前操作人");

        var plates = jdbc.queryForList("""
            SELECT plate_id, frame_id, version_id, status, washed_time
            FROM engineering_screen_plate WHERE plate_id=? AND v2_registered=1 FOR UPDATE
            """, c.plateId());
        if (plates.isEmpty()) throw new BusinessException(404, "网版不存在");
        var plate = plates.get(0);
        if (!"RELEASED".equals(String.valueOf(plate.get("status"))))
            throw new BusinessException(422, "网版未放行，不能开工使用");
        if (plate.get("washed_time") != null) throw new BusinessException(409, "网版已洗版，不能使用");

        long plateVersion = ((Number) plate.get("version_id")).longValue();
        if (c.versionId() != null && plateVersion != c.versionId())
            throw new BusinessException(422, "网版图案版本与工单要求不一致");

        var frames = jdbc.queryForList("SELECT status FROM engineering_screen_frame WHERE frame_id=?", plate.get("frame_id"));
        if (!frames.isEmpty() && "SCRAPPED".equals(String.valueOf(frames.get(0).get("status"))))
            throw new BusinessException(409, "网框已报废，不能使用");

        // 同一工序换版：关闭旧段，不覆盖历史
        jdbc.update("""
            UPDATE print_use_segment SET end_time=NOW(3), reason=COALESCE(?,reason),
                   update_by=?, update_time=NOW(3), lock_version=lock_version+1
            WHERE execution_id=? AND end_time IS NULL
            """, c.reason(), operator, c.executionId());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("executionId", c.executionId());
        snapshot.put("specVersionId", c.specVersionId());
        snapshot.put("routingItemId", c.routingItemId());
        snapshot.put("versionId", plateVersion);
        snapshot.put("plateId", c.plateId());
        snapshot.put("operator", operator);

        try {
            jdbc.update("""
                INSERT INTO print_use_segment(execution_id,spec_version_id,routing_item_id,version_id,plate_id,
                       start_time,operator_id,operator_name,reason,request_id,snapshot,create_by,create_time)
                VALUES(?,?,?,?,?,NOW(3),?,?,?,?,?,?,NOW(3))
                """, c.executionId(), c.specVersionId(), c.routingItemId(), plateVersion, c.plateId(),
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
