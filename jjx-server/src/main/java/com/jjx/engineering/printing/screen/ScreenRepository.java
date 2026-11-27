package com.jjx.engineering.printing.screen;

import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
class ScreenRepository {
    final JdbcTemplate db;

    Map<String,Object> one(String sql, Object... args) {
        var rows = db.queryForList(sql, args);
        if (rows.isEmpty()) throw new BusinessException(404, "印刷资源不存在");
        return rows.getFirst();
    }

    boolean exists(String sql, Object... args) {
        return !db.queryForList(sql, args).isEmpty();
    }

    long insertedId() {
        return db.queryForObject("select last_insert_id()", Long.class);
    }

    Map<String,Object> frame(long id, boolean lock) {
        return one("""
            select frame_id frameId,frame_no frameNo,frame_type frameType,location,status,revision
            from engineering_screen_frame where frame_id=? and v2_registered=1 and del_flag='0'
            """ + (lock ? " for update" : ""), id);
    }

    Map<String,Object> plate(long id, boolean lock) {
        return one("""
            select plate_id plateId,frame_id frameId,stretch_id stretchId,version_id versionId,
            film_copy_id filmId,plate_no plateNo,position,status,revision,released_by releasedBy,
            released_time releasedTime,washed_time washedTime,end_reason endReason,create_by createdBy
            from engineering_screen_plate where plate_id=? and v2_registered=1
            """ + (lock ? " for update" : ""), id);
    }

    /** P07/M1：新模型网框分页列表（只读，仅 v2_registered=1）。 */
    Map<String, Object> listFrames(String keyword, String status, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE f.v2_registered=1 AND f.del_flag='0'");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (f.frame_no LIKE ? OR f.frame_type LIKE ? OR f.location LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND f.status=?");
            args.add(status);
        }
        Integer total = db.queryForObject("SELECT COUNT(*) FROM engineering_screen_frame f" + where, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        var rows = db.queryForList("""
            SELECT f.frame_id AS frameId, f.frame_no AS frameNo, f.frame_type AS frameType, f.location,
                   f.status, f.revision, f.remark,
                   s.mesh, s.stretch_id AS stretchId,
                   p.plate_id AS plateId, p.plate_no AS plateNo
            FROM engineering_screen_frame f
            LEFT JOIN print_frame_stretch s ON s.open_frame_id=f.frame_id
            LEFT JOIN engineering_screen_plate p ON p.occupied_frame_id=f.frame_id
            """ + where + " ORDER BY f.frame_no LIMIT ? OFFSET ?",
            pageArgs.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", total == null ? 0 : total);
        return result;
    }

    /** 同一版本「可用网版」数：已放行、未洗版、网框未报废（唯一口径）。 */
    int usablePlateCount(long versionId) {
        Integer count = db.queryForObject("""
            SELECT COUNT(*) FROM engineering_screen_plate p
            JOIN engineering_screen_frame f ON f.frame_id=p.frame_id
            WHERE p.version_id=? AND p.v2_registered=1 AND p.status='RELEASED'
              AND p.washed_time IS NULL AND f.del_flag='0' AND f.status<>'SCRAPPED'
            """, Integer.class, versionId);
        return count == null ? 0 : count;
    }

    boolean occupied(long frame) {
        return exists("select plate_id from engineering_screen_plate where occupied_frame_id=? for update", frame);
    }
}
