package com.jjx.engineering.printing.screen;

import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
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

    boolean occupied(long frame) {
        return exists("select plate_id from engineering_screen_plate where occupied_frame_id=? for update", frame);
    }
}
