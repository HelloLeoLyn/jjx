package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysAttachmentRef;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SysAttachmentRefMapper extends BaseMapper<SysAttachmentRef> {
    /** 按有效来源单据查链路，不能依赖文件首次绑定时留下的 trace_id。 */
    @Select("""
        SELECT r.* FROM sys_attachment_ref r
        WHERE r.deleted=0 AND (
          (r.source_type='inquiry' AND r.source_id IN
            (SELECT inquiry_id FROM sales_inquiry WHERE trace_id=#{traceId} AND deleted=0))
          OR (r.source_type='quotation' AND r.source_id IN
            (SELECT quotation_id FROM sales_quotation WHERE trace_id=#{traceId} AND deleted=0))
          OR (r.source_type IN ('sales_order','order') AND r.source_id IN
            (SELECT order_id FROM sales_order WHERE trace_id=#{traceId} AND deleted=0))
          OR (r.source_type IN ('sample','sample_order') AND r.source_id IN
            (SELECT s.order_id FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id
             WHERE o.trace_id=#{traceId} AND s.deleted=0 AND o.deleted=0))
          OR (r.source_type IN ('purchase_order','purchase') AND r.source_id IN
            (SELECT order_id FROM purchase_order WHERE trace_id=#{traceId}))
          OR (r.source_type IN ('production_order','production') AND r.source_id IN
            (SELECT order_id FROM production_order WHERE trace_id=#{traceId}))
        ) ORDER BY r.create_time, r.id
        """)
    List<SysAttachmentRef> selectByTrace(String traceId);

    // 包括已解除引用：不能因解除后查不到有效引用而重新启用旧来源。
    @Select("SELECT COUNT(*) FROM sys_attachment_ref WHERE attachment_id=#{id}")
    long countHistory(Long id);

    @Select("SELECT * FROM sys_attachment_ref WHERE attachment_id=#{id} AND deleted=0 FOR UPDATE")
    List<SysAttachmentRef> lockActive(Long id);

    @Select("SELECT * FROM sys_attachment_ref WHERE id=#{id}")
    SysAttachmentRef includingDeleted(Long id);
}
