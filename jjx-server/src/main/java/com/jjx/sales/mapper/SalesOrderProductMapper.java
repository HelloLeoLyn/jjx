package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.vo.ProductValidationVO;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单产品明细Mapper接口
 */
@Mapper
public interface SalesOrderProductMapper extends BaseMapper<SalesOrderProduct> {
    /**
     * 根据订单ID查询订单产品验证信息
     * @param orderId 订单ID
     * @return 产品验证信息列表
     */
    @Select("SELECT sop.product_id, " +
            "       p.product_code, " +
            "       p.product_name, " +
            "       p.product_status, " +
            "       pc.category_code, " +
            "       pc.category_name, " +
            "       pb.bom_id, " +
            "       pb.bom_code, " +
            "       pb.bom_version, " +
            "       pb.is_current AS is_bom_current_version, " +
            "       pb.approve_status AS bom_status, " +
            "       pr.routing_id, " +
            "       pr.routing_code, " +
            "       pr.routing_name, " +
            "       pr.is_current AS is_routing_current_version, " +
            "       pr.routing_version, " +
            "       pr.approve_status AS routing_status " +
            "FROM sales_order_product sop " +
            "LEFT JOIN product p ON sop.product_id = p.product_id " +
            "LEFT JOIN engineering_bom pb ON p.current_bom_id = pb.bom_id " +
            "LEFT JOIN engineering_routing pr ON p.current_route_id = pr.routing_id " +
            "LEFT JOIN product_category pc ON pc.category_id = p.category_id " +
            "WHERE sop.order_id = #{orderId}")
    List<ProductValidationVO> selectProductValidationByOrderId(@Param("orderId") Long orderId);

    /**
     * dev-20260930-018（P1 齐套口径）：按产品汇总「有效订单（已审核4/已确认6/生产中7）」
     * 的订单需求与行级已发（来自 sales_delivery_item）。用于计算成品"净现货可用"。
     *
     * @param productId 产品ID
     * @return 每行：order_id / is_urgent / delivery_date / demand / shipped
     */
    @Select("SELECT sop.order_id AS order_id, so.is_urgent AS is_urgent, so.create_time AS create_time, " +
            "       SUM(sop.quantity) AS demand, IFNULL(SUM(d.shipped), 0) AS shipped " +
            "FROM sales_order_product sop " +
            "INNER JOIN sales_order so ON so.order_id = sop.order_id " +
            "LEFT JOIN (SELECT p2.id AS opid, SUM(sdi.quantity) AS shipped " +
            "           FROM sales_delivery_item sdi " +
            "           INNER JOIN sales_order_product p2 ON p2.id = sdi.order_product_id " +
            "           INNER JOIN sales_delivery sd ON sd.delivery_id = sdi.delivery_id " +
            "           WHERE sd.deleted = 0 AND sd.delivery_status IN (#{pending},#{shipped},#{received}) GROUP BY p2.id) d ON d.opid = sop.id " +
            "WHERE sop.product_id = #{productId} AND so.order_status IN (4, 6, 7) AND so.deleted = 0 " +
            "GROUP BY sop.order_id, so.is_urgent, so.create_time")
    List<java.util.Map<String, Object>> selectEffectiveDemandByProduct(@Param("productId") Long productId, @Param("pending") Integer pending, @Param("shipped") Integer shipped, @Param("received") Integer received);
}
