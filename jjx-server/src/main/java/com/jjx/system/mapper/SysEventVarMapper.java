package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysEventVar;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 事件变量累积注册表（二期，dev-20260923-037）。 */
@Mapper
public interface SysEventVarMapper extends BaseMapper<SysEventVar> {
    @Insert("INSERT INTO sys_event_var(event_code,var_key,description,example,source,last_seen_at) "
            + "VALUES(#{eventCode},#{varKey},#{description},#{example},'collected',NOW()) "
            + "ON DUPLICATE KEY UPDATE last_seen_at=NOW(), example=VALUES(example), "
            + "source=IF(source='manual','manual',VALUES(source))")
    int upsertCollected(@Param("eventCode") String eventCode,
                        @Param("varKey") String varKey,
                        @Param("description") String description,
                        @Param("example") String example);
}
