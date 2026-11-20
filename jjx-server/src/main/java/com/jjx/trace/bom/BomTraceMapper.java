package com.jjx.trace.bom;
import com.jjx.system.domain.entity.SysOperLog;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface BomTraceMapper {
    String VERSION="SELECT b.bom_id,b.bom_code,b.bom_version version,COALESCE(NULLIF(b.output_type,''),'PRODUCT') output_type,"
        +"COALESCE(b.output_id,b.product_id) output_id,b.parent_bom_id,b.create_time,"
        +"COALESCE((SELECT NULLIF(u.nick_name,'') FROM sys_user u WHERE u.user_name=b.create_by OR CAST(u.user_id AS CHAR)=b.create_by LIMIT 1),b.create_by) creator_name FROM engineering_bom b ";
    @Select(VERSION+"WHERE b.bom_id=#{id}")
    BomTraceVersion anchor(@Param("id") Long id);
    @Select(VERSION+"WHERE COALESCE(NULLIF(b.output_type,''),'PRODUCT')=#{outputType} AND COALESCE(b.output_id,b.product_id)=#{outputId} ORDER BY b.create_time,b.bom_id")
    List<BomTraceVersion> versions(@Param("outputType") String outputType,@Param("outputId") Long outputId);
    String FAMILY="s.biz_type='bom' AND JSON_UNQUOTE(JSON_EXTRACT(s.detail,'$.bomObject.outputType'))=#{outputType} "
        +"AND JSON_UNQUOTE(JSON_EXTRACT(s.detail,'$.bomObject.outputId'))=CAST(#{outputId} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci";
    @Select("SELECT s.* FROM sys_oper_log s WHERE "+FAMILY+" ORDER BY s.id DESC")
    List<SysOperLog> snapshots(@Param("outputType") String outputType,@Param("outputId") Long outputId);
    @Select("SELECT s.* FROM sys_oper_log s WHERE s.biz_type='bom' AND s.biz_id=#{id} AND JSON_EXTRACT(s.detail,'$.bomObject') IS NOT NULL ORDER BY s.id DESC LIMIT 1")
    SysOperLog deletedAnchor(@Param("id") String id);
    String ROOT_IDS="JSON_EXTRACT(CASE WHEN JSON_VALID(s.oper_param) THEN s.oper_param ELSE '{}' END,'$.request.products[*].rootBomId')";
    @Select({"<script>SELECT s.* FROM sys_oper_log s WHERE s.biz_type='bom' AND (s.biz_id IN",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "OR ((s.biz_id IS NULL OR s.biz_id='') AND s.oper_url IN ('/engineering/bom/review/package/submit','/engineering/bom/review/package/approve','/engineering/bom/review/package/reject') AND (",
        "<foreach collection='ids' item='id' separator=' OR '>",
        "JSON_CONTAINS("+ROOT_IDS+",JSON_ARRAY(CAST(#{id} AS UNSIGNED))) OR JSON_CONTAINS("+ROOT_IDS+",JSON_ARRAY(#{id}))",
        "</foreach>))) ORDER BY s.create_time,s.id</script>"})
    List<SysOperLog> logs(@Param("ids") List<String> ids);
}
