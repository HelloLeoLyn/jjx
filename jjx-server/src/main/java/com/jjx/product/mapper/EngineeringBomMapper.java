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
