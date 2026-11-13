package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.domain.query.EngineeringBomQuery;
import com.jjx.product.domain.vo.EngineeringBomVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 产品BOM Mapper接口
 */
@Mapper
public interface EngineeringBomMapper extends BaseMapper<EngineeringBom> {
    @org.apache.ibatis.annotations.Select({
        "<script>",
        "<bind name='draft' value='@com.jjx.product.enums.ProductEnums$BomStatus@DRAFT.getValue()'/>",
        "<bind name='rejected' value='@com.jjx.product.enums.ProductEnums$BomStatus@REJECT.getValue()'/>",
        "<bind name='pending' value='@com.jjx.product.enums.ProductEnums$BomStatus@REVIEWING.getValue()'/>",
        "SELECT COALESCE(b.output_type,'PRODUCT') output_type, COALESCE(b.output_id,b.product_id) output_id,",
        "MAX(b.product_id) product_id, MAX(CASE WHEN COALESCE(b.output_type,'PRODUCT')='MATERIAL' THEN m.material_code ELSE p.product_code END) product_code,",
        "MAX(CASE WHEN COALESCE(b.output_type,'PRODUCT')='MATERIAL' THEN m.material_name ELSE p.product_name END) product_name,",
        "COALESCE(MAX(m.specification), MAX(CAST(p.spec_json AS CHAR))) specification,",
        "COUNT(*) version_count, MAX(CASE WHEN b.approve_status IN (#{draft},#{rejected}) THEN 1 ELSE 0 END) has_draft,",
        "MAX(CASE WHEN b.approve_status=#{pending} THEN 1 ELSE 0 END) has_pending, MAX(COALESCE(b.update_time,b.create_time)) update_time",
        "FROM engineering_bom b LEFT JOIN product p ON COALESCE(b.output_type,'PRODUCT')='PRODUCT' AND p.product_id=COALESCE(b.output_id,b.product_id)",
        "LEFT JOIN inventory_material m ON b.output_type='MATERIAL' AND m.material_id=b.output_id",
        "WHERE COALESCE(b.output_id,b.product_id) IS NOT NULL",
        "<if test='query.productId != null'>AND b.product_id=#{query.productId}</if>",
        "<if test='query.productCode != null and query.productCode != &quot;&quot;'>AND (CASE WHEN b.output_type='MATERIAL' THEN m.material_code ELSE p.product_code END) LIKE CONCAT('%',#{query.productCode},'%')</if>",
        "<if test='query.productName != null and query.productName != &quot;&quot;'>AND (CASE WHEN b.output_type='MATERIAL' THEN m.material_name ELSE p.product_name END) LIKE CONCAT('%',#{query.productName},'%')</if>",
        "GROUP BY COALESCE(b.output_type,'PRODUCT'), COALESCE(b.output_id,b.product_id)",
        "HAVING MAX(CASE WHEN 1=1",
        "<if test='query.bomCode != null and query.bomCode != &quot;&quot;'>AND b.bom_code LIKE CONCAT('%',#{query.bomCode},'%')</if>",
        "<if test='query.bomName != null and query.bomName != &quot;&quot;'>AND b.bom_name LIKE CONCAT('%',#{query.bomName},'%')</if>",
        "<if test='query.bomType != null and query.bomType != &quot;&quot;'>AND b.bom_type=#{query.bomType}</if>",
        "<if test='query.approveStatus != null and query.approveStatus != &quot;&quot;'>AND b.approve_status=#{query.approveStatus}</if>",
        "THEN 1 ELSE 0 END)=1",
        "ORDER BY output_type,output_id",
        "</script>"
    })
    com.baomidou.mybatisplus.core.metadata.IPage<com.jjx.product.service.IEngineeringBomService.ProductVersionRow> selectProductPage(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.jjx.product.service.IEngineeringBomService.ProductVersionRow> page,
            @Param("query") com.jjx.product.service.IEngineeringBomService.BomListQuery query);



    @Select("SELECT b.* FROM engineering_bom b WHERE COALESCE(output_type,'PRODUCT')=#{outputType} "
            + "AND COALESCE(output_id,product_id)=#{outputId} ORDER BY bom_id DESC")
    List<com.jjx.product.service.IEngineeringBomService.BomVersionRow> selectObjectVersions(
            @Param("outputType") String outputType, @Param("outputId") Long outputId);

    /** 父 BOM 保存行锁，避免并发覆盖正在编辑的明细。 */
    @Select("SELECT * FROM engineering_bom WHERE bom_id = #{bomId} FOR UPDATE")
    EngineeringBom selectForUpdate(@Param("bomId") Long bomId);

    /**
     * 显式下层引用变更的全局串行化锁（dev-20261111-004）。
     * 在事务内按 bom_id 固定顺序锁定全部 BOM 行；所有会写 sub_bom_id 的保存都先取本锁，
     * 避免 A→B 与 B→A 两个事务各自快照读通过校验后互写形成环。锁为数据库行锁，跨实例有效；
     * 本语句必须是该事务的第一条数据库语句，失败时随事务回滚自动释放。
     */
    @Select("SELECT bom_id FROM engineering_bom ORDER BY bom_id FOR UPDATE")
    List<Long> lockGraphForMutation();

    /**
     * 查询BOM总数
     */
    @Select({
        "<script>",
        "SELECT COUNT(*) FROM engineering_bom pb ",
        "LEFT JOIN product p ON pb.product_id = p.product_id ",
        "WHERE 1=1 ",
        "<if test='query.bomCode != null and query.bomCode != \"\"'>",
        "   AND pb.bom_code LIKE CONCAT('%', #{query.bomCode}, '%') ",
        "</if>",
        "<if test='query.productId != null'>",
        "   AND pb.product_id = #{query.productId} ",
        "</if>",
        "<if test='query.productCode != null and query.productCode != \"\"'>",
        "   AND p.product_code LIKE CONCAT('%', #{query.productCode}, '%') ",
        "</if>",
        "<if test='query.productName != null and query.productName != \"\"'>",
        "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') ",
        "</if>",
        "<if test='query.bomType != null and query.bomType != \"\"'>",
        "   AND pb.bom_type = #{query.bomType} ",
        "</if>",
        "<if test='query.bomVersion != null and query.bomVersion != \"\"'>",
        "   AND pb.bom_version = #{query.bomVersion} ",
        "</if>",
        "<if test='query.isCurrent != null'>",
        "   AND pb.is_current = #{query.isCurrent} ",
        "</if>",
        "<if test='query.approveStatus != null and query.approveStatus != \"\"'>",
        "   AND pb.approve_status = #{query.approveStatus} ",
        "</if>",
        "</script>"
    })
    long selectBomCount(@Param("query") EngineeringBomQuery query);

    /**
     * 分页查询BOM列表
     */
    @Select({
        "<script>",
        "SELECT ",
        "   pb.bom_id, pb.bom_code, pb.product_id, pb.bom_version, pb.version, ",
        "   pb.bom_type, pb.is_current, pb.effective_date, pb.expiry_date, ",
        "   pb.create_time, pb.update_time, pb.approve_status, pb.remark, ",
        "   p.product_code, p.product_name ",
        "FROM engineering_bom pb ",
        "LEFT JOIN product p ON pb.product_id = p.product_id ",
        "WHERE 1=1 ",
        "<if test='query.bomCode != null and query.bomCode != \"\"'>",
        "   AND pb.bom_code LIKE CONCAT('%', #{query.bomCode}, '%') ",
        "</if>",
        "<if test='query.productId != null'>",
        "   AND pb.product_id = #{query.productId} ",
        "</if>",
        "<if test='query.productCode != null and query.productCode != \"\"'>",
        "   AND p.product_code LIKE CONCAT('%', #{query.productCode}, '%') ",
        "</if>",
        "<if test='query.productName != null and query.productName != \"\"'>",
        "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') ",
        "</if>",
        "<if test='query.bomType != null and query.bomType != \"\"'>",
        "   AND pb.bom_type = #{query.bomType} ",
        "</if>",
        "<if test='query.bomVersion != null and query.bomVersion != \"\"'>",
        "   AND pb.bom_version = #{query.bomVersion} ",
        "</if>",
        "<if test='query.isCurrent != null'>",
        "   AND pb.is_current = #{query.isCurrent} ",
        "</if>",
        "<if test='query.approveStatus != null and query.approveStatus != \"\"'>",
        "   AND pb.approve_status = #{query.approveStatus} ",
        "</if>",
        "ORDER BY pb.create_time DESC ",
        "LIMIT #{query.pageSize} OFFSET #{offset}",
        "</script>"
    })
    @Results({
        @Result(property = "bomId", column = "bom_id", id = true),
        @Result(property = "bomCode", column = "bom_code"),
        @Result(property = "productId", column = "product_id"),
        @Result(property = "productCode", column = "product_code"),
        @Result(property = "productName", column = "product_name"),
        @Result(property = "bomVersion", column = "bom_version"),
        @Result(property = "version", column = "version"),
        @Result(property = "bomType", column = "bom_type"),
        @Result(property = "isCurrent", column = "is_current"),
        @Result(property = "effectiveDate", column = "effective_date"),
        @Result(property = "expiryDate", column = "expiry_date"),
        @Result(property = "createTime", column = "create_time"),
        @Result(property = "updateTime", column = "update_time"),
        @Result(property = "approveStatus", column = "approve_status"),
        @Result(property = "remark", column = "remark")
    })
    List<EngineeringBomVO> selectBomList(@Param("query") EngineeringBomQuery query, @Param("offset") int offset);

    /**
     * 查询产品的最新BOM版本
     */
    @Select("SELECT * FROM engineering_bom WHERE product_id = #{productId} ORDER BY create_time DESC LIMIT 1")
    @Results({
        @Result(property = "bomId", column = "bom_id", id = true),
        @Result(property = "bomCode", column = "bom_code"),
        @Result(property = "productId", column = "product_id"),
        @Result(property = "bomVersion", column = "bom_version"),
        @Result(property = "bomType", column = "bom_type"),
        @Result(property = "isCurrent", column = "is_current"),
        @Result(property = "effectiveDate", column = "effective_date"),
        @Result(property = "expiryDate", column = "expiry_date"),
        @Result(property = "createTime", column = "create_time"),
        @Result(property = "updateTime", column = "update_time"),
        @Result(property = "approveStatus", column = "approve_status"),
        @Result(property = "approveRemark", column = "approve_remark"),
        @Result(property = "remark", column = "remark")
    })
    EngineeringBom selectLatestVersion(@Param("productId") Long productId);
}
