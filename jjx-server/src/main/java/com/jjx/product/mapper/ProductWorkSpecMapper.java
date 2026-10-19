package com.jjx.product.mapper;

import org.apache.ibatis.annotations.*;
import java.util.Map;
import java.util.List;

/** 独立字段更新，普通产品编辑不会覆写工程规范。 */
@Mapper
public interface ProductWorkSpecMapper {
    @Select("SELECT COUNT(*) FROM product WHERE product_id = #{id}")
    int exists(Long id);

    @Select("SELECT CAST(work_spec_json AS CHAR) FROM product WHERE product_id = #{id}")
    String read(Long id);

    @Select("SELECT product_id, work_spec_json FROM product WHERE product_id = #{id} FOR UPDATE")
    Map<String, Object> lock(Long id);

    @Update("UPDATE product SET work_spec_json = #{json} WHERE product_id = #{id}")
    int write(@Param("id") Long id, @Param("json") String json);

    @Select("SELECT user_id AS id, COALESCE(NULLIF(nick_name,''),user_name) AS name FROM sys_user WHERE del_flag=#{normal} AND status=#{normal} ORDER BY nick_name,user_id")
    List<Map<String, Object>> responsibleUsers(@Param("normal") String normal);

    @Select("SELECT COUNT(*) FROM sys_dict_item WHERE dict_code='attachment_document_type' AND item_key=#{type} AND deleted=0 AND is_active=1 AND JSON_UNQUOTE(JSON_EXTRACT(ext_data,'$.parent'))='ENGINEERING_DOC'")
    int engineeringType(@Param("type") String type);

    @Select("""
        SELECT r.id AS refId,r.attachment_id AS attachmentId,r.document_type AS documentType,
          r.display_name AS displayName,r.remark,r.sort_order AS sortOrder,
          a.file_name AS fileName,a.file_type AS fileType,a.file_size AS fileSize,a.sha256
        FROM sys_attachment_ref r JOIN sys_attachment a ON a.id=r.attachment_id AND a.deleted=0
        JOIN sys_dict_item d ON d.dict_code='attachment_document_type' AND d.item_key=r.document_type
          AND d.deleted=0 AND JSON_UNQUOTE(JSON_EXTRACT(d.ext_data,'$.parent'))='ENGINEERING_DOC'
        WHERE r.deleted=0 AND r.ref_state<>'draft' AND r.source_type='product' AND r.source_id=#{id}
        ORDER BY r.sort_order,r.id
        """)
    List<Map<String, Object>> draftFiles(Long id);

    @Select("SELECT snapshot_json FROM product_work_spec_item WHERE spec_version_id=#{versionId} AND resource_type='DOCSET_JSON' ORDER BY id LIMIT 1")
    String versionSnapshot(Long versionId);

    @Select("SELECT product_id FROM product_work_spec_version WHERE id=#{versionId} AND deleted=0")
    Long versionProduct(Long versionId);

    @Select("SELECT id,action,COALESCE(NULLIF(real_name,''),username) AS operatorName,create_time AS changedAt,detail FROM sys_oper_log WHERE biz_type='product' AND biz_id=#{productId} AND module='作业规范版本' AND status=#{success} AND (#{before} IS NULL OR id<#{before}) ORDER BY id DESC LIMIT 51")
    List<Map<String, Object>> draftHistory(@Param("productId") String productId,@Param("before") Long before,@Param("success") Integer success);

    @Select("""
        SELECT id, COALESCE(NULLIF(real_name, ''), username) AS operatorName,
               DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS changedAt, detail
        FROM sys_oper_log
        WHERE biz_type = 'product' AND biz_id = #{productId} AND action = #{action}
          AND status = #{success} AND (#{before} IS NULL OR id < #{before})
        ORDER BY id DESC LIMIT 51
        """)
    List<Map<String, Object>> printRemarksHistory(@Param("productId") String productId,
            @Param("before") Long before, @Param("action") String action, @Param("success") Integer success);

    /** 只投影工程差异，不返回操作日志请求参数或用户隐私字段。 */
    @Select("""
        SELECT l.id, DATE_FORMAT(l.create_time, '%Y-%m-%d') AS changeDate, l.detail,
               CASE WHEN b.bom_id IS NOT NULL THEN CONCAT('BOM ', b.bom_code, ' / ', COALESCE(b.bom_version, ''))
                    ELSE CONCAT('工艺路线 ', r.routing_code, ' / ', COALESCE(r.routing_version, '')) END AS sourceLabel
        FROM sys_oper_log l
        LEFT JOIN engineering_bom b ON l.biz_type = 'bom' AND l.biz_id = CAST(b.bom_id AS CHAR) COLLATE utf8mb4_unicode_ci
             AND b.product_id = #{productId} AND b.approve_status = #{bomApproved} AND l.action = #{bomAction}
        LEFT JOIN engineering_routing r ON l.biz_type = 'routing' AND l.biz_id = CAST(r.routing_id AS CHAR) COLLATE utf8mb4_unicode_ci
             AND r.product_id = #{productId} AND r.approve_status = #{routeApproved} AND l.action = #{routeAction}
        WHERE (b.bom_id IS NOT NULL OR r.routing_id IS NOT NULL)
          AND l.status = #{success} AND l.detail IS NOT NULL
          AND (#{before} IS NULL OR l.id < #{before}) AND (#{onlyId} IS NULL OR l.id = #{onlyId})
        ORDER BY l.id DESC LIMIT 51
        """)
    List<Map<String, Object>> changeSources(@Param("productId") Long productId, @Param("before") Long before,
            @Param("onlyId") Long onlyId, @Param("bomApproved") Integer bomApproved,
            @Param("routeApproved") Integer routeApproved, @Param("bomAction") String bomAction,
            @Param("routeAction") String routeAction, @Param("success") Integer success);
}
