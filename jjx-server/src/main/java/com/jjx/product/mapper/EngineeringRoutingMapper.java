package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EngineeringRoutingMapper extends BaseMapper<EngineeringRouting> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM engineering_routing WHERE COALESCE(output_type,'PRODUCT')=#{outputType} "
            + "AND COALESCE(output_id,product_id)=#{outputId} ORDER BY routing_id FOR UPDATE")
    List<EngineeringRouting> lockObjectVersions(@Param("outputType") String outputType, @Param("outputId") Long outputId);

    @org.apache.ibatis.annotations.Update("UPDATE engineering_routing SET is_current=0 WHERE COALESCE(output_type,'PRODUCT')=#{outputType} "
            + "AND COALESCE(output_id,product_id)=#{outputId} AND is_current=1")
    int clearObjectCurrent(@Param("outputType") String outputType, @Param("outputId") Long outputId);

    @org.apache.ibatis.annotations.Update("UPDATE engineering_routing SET is_current=1 WHERE routing_id=#{id} "
            + "AND approve_status=#{approved} AND COALESCE(is_current,0)=0")
    int promoteCurrent(@Param("id") Long id, @Param("approved") Integer approved);


    @org.apache.ibatis.annotations.Select({
        "<script>",
        "<bind name='draft' value='@com.jjx.common.enums.ApproveStatusEnum@DRAFT.getValue()'/>",
        "<bind name='rejected' value='@com.jjx.common.enums.ApproveStatusEnum@REJECTED.getValue()'/>",
        "<bind name='pending' value='@com.jjx.common.enums.ApproveStatusEnum@PENDING.getValue()'/>",
        "SELECT COALESCE(b.output_type,'PRODUCT') output_type, COALESCE(b.output_id,b.product_id) output_id,",
        "MAX(b.product_id) product_id, MAX(CASE WHEN COALESCE(b.output_type,'PRODUCT')='MATERIAL' THEN m.material_code ELSE p.product_code END) product_code,",
        "MAX(CASE WHEN COALESCE(b.output_type,'PRODUCT')='MATERIAL' THEN m.material_name ELSE p.product_name END) product_name,",
        "COALESCE(MAX(m.specification), MAX(CAST(p.spec_json AS CHAR))) specification,",
        "COUNT(*) version_count, MAX(CASE WHEN b.approve_status IN (#{draft},#{rejected}) THEN 1 ELSE 0 END) has_draft,",
        "MAX(CASE WHEN b.approve_status=#{pending} THEN 1 ELSE 0 END) has_pending, MAX(COALESCE(b.update_time,b.create_time)) update_time",
        "FROM engineering_routing b LEFT JOIN product p ON COALESCE(b.output_type,'PRODUCT')='PRODUCT' AND p.product_id=COALESCE(b.output_id,b.product_id)",
        "LEFT JOIN inventory_material m ON b.output_type='MATERIAL' AND m.material_id=b.output_id",
        "WHERE COALESCE(b.output_id,b.product_id) IS NOT NULL",
        "<if test='query.productId != null'>AND b.product_id=#{query.productId}</if>",
        "<if test='query.productCode != null and query.productCode != &quot;&quot;'>AND (CASE WHEN b.output_type='MATERIAL' THEN m.material_code ELSE p.product_code END) LIKE CONCAT('%',#{query.productCode},'%')</if>",
        "GROUP BY COALESCE(b.output_type,'PRODUCT'), COALESCE(b.output_id,b.product_id)",
        "HAVING MAX(CASE WHEN 1=1",
        "<if test='query.routingCode != null and query.routingCode != &quot;&quot;'>AND b.routing_code LIKE CONCAT('%',#{query.routingCode},'%')</if>",
        "<if test='query.routingName != null and query.routingName != &quot;&quot;'>AND b.routing_name LIKE CONCAT('%',#{query.routingName},'%')</if>",
        "<if test='query.approveStatus != null and query.approveStatus != &quot;&quot;'>AND b.approve_status=#{query.approveStatus}</if>",
        "<if test='query.isCurrent != null'>AND b.is_current=#{query.isCurrent}</if>",
        "THEN 1 ELSE 0 END)=1",
        "ORDER BY output_type,output_id",
        "</script>"
    })
    com.baomidou.mybatisplus.core.metadata.IPage<com.jjx.product.service.IEngineeringBomService.ProductVersionRow> selectProductPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.jjx.product.service.IEngineeringBomService.ProductVersionRow> page,
            @Param("query") com.jjx.product.domain.dto.EngineeringRoutingQueryDTO query);


    
    @Select("SELECT * FROM engineering_routing WHERE routing_id=#{routingId} FOR UPDATE")
    EngineeringRouting selectForUpdate(@Param("routingId") Long routingId);

    /**
     * 根据产品ID查询当前版本的工艺路线
     */
    @Select("SELECT * FROM engineering_routing WHERE product_id = #{productId} AND is_current = 1")
    EngineeringRouting selectCurrentByProductId(@Param("productId") Long productId);
    
    /**
     * 根据产品编码查询当前版本的工艺路线
     */
    @Select("SELECT * FROM engineering_routing WHERE product_code = #{productCode} AND is_current = 1")
    EngineeringRouting selectCurrentByProductCode(@Param("productCode") String productCode);
    
    /**
     * 查询产品的所有版本工艺路线
     */
    @Select("SELECT * FROM engineering_routing WHERE product_id = #{productId} ORDER BY routing_version DESC")
    List<EngineeringRouting> selectAllVersionsByProductId(@Param("productId") Long productId);
    
    /**
     * 设置指定产品所有路线为非当前版本
     */
    @Update("UPDATE engineering_routing SET is_current = 0 WHERE product_id = #{productId}")
    void setAllNotCurrent(@Param("productId") Long productId);

    /** 按产出身份失效当前版本（MATERIAL 路线没有 product_id）。 */
    @Update("UPDATE engineering_routing SET is_current = 0 WHERE COALESCE(output_type,'PRODUCT') = #{outputType} "
            + "AND COALESCE(output_id, product_id) = #{outputId}")
    void setAllNotCurrentByOutput(@Param("outputType") String outputType, @Param("outputId") Long outputId);

    /** 按产出身份查询当前路线。 */
    @Select("SELECT * FROM engineering_routing WHERE COALESCE(output_type,'PRODUCT') = #{outputType} "
            + "AND COALESCE(output_id, product_id) = #{outputId} AND is_current = 1")
    EngineeringRouting selectCurrentByOutput(@Param("outputType") String outputType, @Param("outputId") Long outputId);

    /** 按产出身份查询全部版本。 */
    @Select("SELECT * FROM engineering_routing WHERE COALESCE(output_type,'PRODUCT') = #{outputType} "
            + "AND COALESCE(output_id, product_id) = #{outputId} ORDER BY routing_version DESC")
    List<EngineeringRouting> selectAllVersionsByOutput(@Param("outputType") String outputType, @Param("outputId") Long outputId);
    
    /**
     * 根据审核状态查询
     */
    @Select("SELECT * FROM engineering_routing WHERE approve_status = #{approveStatus}")
    List<EngineeringRouting> selectByApproveStatus(@Param("approveStatus") Long approveStatus);
}