package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.production.domain.entity.QualityTemplatePrintLog;
import com.jjx.production.domain.vo.QualityArchiveVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QualityArchiveMapper extends BaseMapper<QualityTemplatePrintLog> {
    @Select("<script>"
            + "SELECT l.id AS print_log_id, COALESCE(t.record_no, l.record_no) AS record_no, "
            + "t.record_name, t.owner_dept, t.retention_years, l.biz_type, l.biz_id, "
            + "l.operator_name, l.print_time, "
            + "CASE WHEN t.retention_years IS NULL THEN NULL "
            + "ELSE DATE(DATE_ADD(l.print_time, INTERVAL t.retention_years YEAR)) END AS expiry_date, "
            + "EXISTS(SELECT 1 FROM sys_attachment a WHERE a.biz_type = l.biz_type "
            + "AND a.biz_id = l.biz_id AND a.deleted = 0) AS archived "
            + "FROM quality_template_print_log l "
            + "LEFT JOIN quality_template_registry t ON t.id = l.template_id "
            + "WHERE 1 = 1 "
            + "<if test='query.templateId != null'>AND l.template_id = #{query.templateId} </if>"
            + "<if test='query.ownerDept != null and query.ownerDept != \"\"'>AND t.owner_dept = #{query.ownerDept} </if>"
            + "<if test='query.recordNo != null and query.recordNo != \"\"'>"
            + "AND COALESCE(t.record_no, l.record_no) LIKE CONCAT('%', #{query.recordNo}, '%') </if>"
            + "<if test='query.archived != null and query.archived'>"
            + "AND EXISTS(SELECT 1 FROM sys_attachment a WHERE a.biz_type = l.biz_type "
            + "AND a.biz_id = l.biz_id AND a.deleted = 0) </if>"
            + "<if test='query.archived != null and !query.archived'>"
            + "AND NOT EXISTS(SELECT 1 FROM sys_attachment a WHERE a.biz_type = l.biz_type "
            + "AND a.biz_id = l.biz_id AND a.deleted = 0) </if>"
            + "<if test='query.expiryState == \"overdue\"'>"
            + "AND t.retention_years IS NOT NULL AND DATE_ADD(l.print_time, INTERVAL t.retention_years YEAR) &lt; CURDATE() </if>"
            + "<if test='query.expiryState == \"soon\"'>"
            + "AND t.retention_years IS NOT NULL AND DATE_ADD(l.print_time, INTERVAL t.retention_years YEAR) "
            + "BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY) </if>"
            + "ORDER BY l.print_time DESC, l.id DESC"
            + "</script>")
    Page<QualityArchiveVO> selectArchivePage(Page<QualityArchiveVO> page,
                                              @Param("query") com.jjx.production.domain.dto.QualityArchiveQueryDTO query);
}
