package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysEventLastPayload;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 事件最近一次 payload（2026-09-23 dev-20260921-014）。
 */
@Mapper
public interface SysEventLastPayloadMapper extends BaseMapper<SysEventLastPayload> {

    /** 覆盖写（一事件一行）：事件触发时记录 payload，供配置页试渲染。 */
    @Insert("INSERT INTO sys_event_last_payload(event_code, payload, biz_id, update_time) "
            + "VALUES(#{eventCode}, #{payload}, #{bizId}, NOW()) "
            + "ON DUPLICATE KEY UPDATE payload = VALUES(payload), biz_id = VALUES(biz_id), update_time = NOW()")
    int upsert(@Param("eventCode") String eventCode,
               @Param("payload") String payload,
               @Param("bizId") String bizId);
}
