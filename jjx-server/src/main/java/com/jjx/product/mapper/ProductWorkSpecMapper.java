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

    /** 只投影工程差异，不返回操作日志请求参数或用户隐私字段。 */
    @Select("""
        SELECT l.id, DATE_FORMAT(l.create_time, '%Y-%m-%d') AS changeDate, l.detail,
               CASE WHEN b.bom_id IS NOT NULL THEN CONCAT('BOM ', b.bom_code, ' / ', COALESCE(b.bom_version, ''))
                    ELSE CONCAT('工艺路线 ', r.routing_code, ' / ', COALESCE(r.routing_version, '')) END AS sourceLabel
        FROM sys_oper_log l
        LEFT JOIN engineering_bom b ON l.biz_type = 'bom' AND l.biz_id = CAST(b.bom_id AS CHAR)
             AND b.product_id = #{productId} AND b.approve_status = #{bomApproved} AND l.action = #{bomAction}
        LEFT JOIN engineering_routing r ON l.biz_type = 'routing' AND l.biz_id = CAST(r.routing_id AS CHAR)
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
