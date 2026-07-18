package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.ProductBom;
import com.jjx.product.domain.query.ProductBomQuery;
import com.jjx.product.domain.vo.ProductBomVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 产品BOM Mapper接口
 */
@Mapper
public interface ProductBomMapper extends BaseMapper<ProductBom> {

    /**
     * 查询BOM总数
     */
    @Select({
        "<script>",
        "SELECT COUNT(*) FROM product_bom pb ",
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
    long selectBomCount(@Param("query") ProductBomQuery query);

    /**
     * 分页查询BOM列表
     */
    @Select({
        "<script>",
        "SELECT ",
        "   pb.bom_id, pb.bom_code, pb.product_id, pb.bom_version, ",
        "   pb.bom_type, pb.is_current, pb.effective_date, pb.expiry_date, ",
        "   pb.create_time, pb.update_time, pb.approve_status, pb.remark, ",
        "   p.product_code, p.product_name ",
        "FROM product_bom pb ",
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
        @Result(property = "bomType", column = "bom_type"),
        @Result(property = "isCurrent", column = "is_current"),
        @Result(property = "effectiveDate", column = "effective_date"),
        @Result(property = "expiryDate", column = "expiry_date"),
        @Result(property = "createTime", column = "create_time"),
        @Result(property = "updateTime", column = "update_time"),
        @Result(property = "approveStatus", column = "approve_status"),
        @Result(property = "remark", column = "remark")
    })
    List<ProductBomVO> selectBomList(@Param("query") ProductBomQuery query, @Param("offset") int offset);

    /**
     * 查询产品的最新BOM版本
     */
    @Select("SELECT * FROM product_bom WHERE product_id = #{productId} ORDER BY create_time DESC LIMIT 1")
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
    ProductBom selectLatestVersion(@Param("productId") Long productId);
}
