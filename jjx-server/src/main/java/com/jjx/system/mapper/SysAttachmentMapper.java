package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysAttachment;
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

    /** 版本操作使用当前读，避免等待产品锁后仍读取事务开始时的旧快照。 */
    @Select("SELECT * FROM sys_attachment WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SysAttachment selectActiveForUpdate(Long id);

    /**
     * 取某产品某图纸编号下的全部附件（行锁）。
     * 归属按引用表判定（source_type='product' AND product_id），不再依赖已废弃的 biz_type/biz_id（dev-20261014-016 第3项）。
     */
    @Select("SELECT a.* FROM sys_attachment a "
            + "JOIN sys_attachment_ref r ON r.attachment_id = a.id AND r.deleted = 0 "
            + "AND r.source_type = 'product' AND r.product_id = #{productId} "
            + "WHERE a.drawing_no = #{drawingNo} AND a.deleted = 0 FOR UPDATE")
    List<SysAttachment> selectDrawingForUpdate(@org.apache.ibatis.annotations.Param("productId") Long productId,
                                              @org.apache.ibatis.annotations.Param("drawingNo") String drawingNo);

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

    @Select("SELECT * FROM sys_attachment WHERE id = #{id}")
    SysAttachment selectIncludingDeleted(Long id);

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
