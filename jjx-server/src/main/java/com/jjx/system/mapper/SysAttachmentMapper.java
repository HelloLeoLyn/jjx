package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysAttachment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用附件 Mapper
 */
@Mapper
public interface SysAttachmentMapper extends BaseMapper<SysAttachment> {

    /**
     * 逻辑删除（软删进回收站，保留物理文件）
     */
    @Update("UPDATE sys_attachment SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int logicalDelete(Long id);

    /**
     * 恢复（回收站还原）
     */
    @Update("UPDATE sys_attachment SET deleted = 0, update_time = NOW() WHERE id = #{id}")
    int restore(Long id);

    /**
     * 物理删除（真删记录，配合物理文件删除）
     */
    @Delete("DELETE FROM sys_attachment WHERE id = #{id}")
    int physicalDelete(Long id);

    /**
     * 回收站列表（已删除附件）
     */
    @Select("SELECT * FROM sys_attachment WHERE deleted = 1 ORDER BY update_time DESC")
    List<SysAttachment> selectRecycled();

    /**
     * 回收站中删除时间早于指定时间的附件（清理过期用）
     */
    @Select("SELECT * FROM sys_attachment WHERE deleted = 1 AND update_time < #{time}")
    List<SysAttachment> selectRecycledBefore(LocalDateTime time);
}
