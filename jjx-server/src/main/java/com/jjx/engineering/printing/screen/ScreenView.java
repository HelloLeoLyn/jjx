package com.jjx.engineering.printing.screen;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Component
@RequiredArgsConstructor
class ScreenView {
    private final ScreenRepository repo;

    @Transactional(readOnly=true)
    public Map<String,Object> get(boolean frame, long id) {
        var result = frame ? repo.frame(id,false) : repo.plate(id,false);
        if (frame) {
            var stretches = repo.queryForList("""
                select stretch_id stretchId,stretch_no stretchNo,mesh,tension_n_cm tensionNcm,material,qualified,
                stretch_time stretchTime,end_time endTime from print_frame_stretch where frame_id=? order by stretch_id desc
                """, id);
            result.put("stretches", stretches);
            result.put("stretchId", stretches.isEmpty() || stretches.getFirst().get("endTime") != null
                    ? null : stretches.getFirst().get("stretchId"));
            var plates = repo.queryForList("select plate_id from engineering_screen_plate where occupied_frame_id=?",Long.class,id);
            result.put("plateId",plates.isEmpty() ? null : plates.getFirst());
        }
        result.put("history",repo.queryForList("""
            select action_type actionType,before_status beforeStatus,after_status afterStatus,
            description,operator,operate_time operateTime from engineering_resource_maintenance
            where resource_type=? and resource_id=? order by maintenance_id
            """,frame ? "SCREEN_FRAME" : "SCREEN_PLATE",id));
        return result;
    }
}
