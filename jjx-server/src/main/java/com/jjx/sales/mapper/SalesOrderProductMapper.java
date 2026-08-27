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
}
