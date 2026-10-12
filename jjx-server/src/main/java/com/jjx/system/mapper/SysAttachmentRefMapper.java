package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysAttachmentRef;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SysAttachmentRefMapper extends BaseMapper<SysAttachmentRef> {
    @Select("SELECT * FROM sys_attachment_ref WHERE attachment_id=#{id} AND deleted=0 FOR UPDATE")
    List<SysAttachmentRef> lockActive(Long id);

    @Select("SELECT * FROM sys_attachment_ref WHERE id=#{id}")
    SysAttachmentRef includingDeleted(Long id);
}
