package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysNumberSequence;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysNumberSequenceMapper extends BaseMapper<SysNumberSequence> {

    @Insert("""
            INSERT INTO sys_number_sequence(sequence_key, period_key, current_value, create_time, update_time)
            VALUES(#{sequenceKey}, #{periodKey}, #{startValue}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE current_value = current_value + 1, update_time = NOW()
            """)
    int advance(@Param("sequenceKey") String sequenceKey,
                @Param("periodKey") String periodKey,
                @Param("startValue") long startValue);

    @Select("""
            SELECT current_value FROM sys_number_sequence
            WHERE sequence_key = #{sequenceKey} AND period_key = #{periodKey}
            FOR UPDATE
            """)
    Long selectCurrentForUpdate(@Param("sequenceKey") String sequenceKey,
                                @Param("periodKey") String periodKey);
}
