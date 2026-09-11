package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统标签Mapper（dev-20260911-007）
 */
@Mapper
public interface SysTagMapper extends BaseMapper<SysTag> {

    /**
     * 查询某业务对象已挂的标签
     *
     * @param bizType 业务类型
     * @param bizId   业务主键
     * @return 标签列表
     */
    @Select("SELECT t.* FROM sys_tag t JOIN sys_tag_rel r ON r.tag_id = t.tag_id "
            + "WHERE r.biz_type = #{bizType} AND r.biz_id = #{bizId} AND t.del_flag = '0' "
            + "ORDER BY t.sort_order, t.tag_id")
    List<SysTag> selectTagsByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);
}
