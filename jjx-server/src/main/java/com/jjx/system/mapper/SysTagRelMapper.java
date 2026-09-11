package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysTagRel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 系统标签关联Mapper（dev-20260911-007）
 */
@Mapper
public interface SysTagRelMapper extends BaseMapper<SysTagRel> {

    /**
     * 清除某业务对象的所有标签关联（重设标签前调用）
     *
     * @param bizType 业务类型
     * @param bizId   业务主键
     * @return 影响行数
     */
    @Delete("DELETE FROM sys_tag_rel WHERE biz_type = #{bizType} AND biz_id = #{bizId}")
    int deleteByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);
}
