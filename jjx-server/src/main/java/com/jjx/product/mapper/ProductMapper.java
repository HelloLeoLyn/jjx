package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.ProductVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.builder.annotation.ProviderContext;

import java.util.Map;

/**
 * 产品Mapper接口
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    /**
     * 分页查询产品VO（使用 Provider 方式）
     */
    @SelectProvider(type = ProductSqlProvider.class, method = "getProductFullPage")
    Page<ProductVo> getProductFullPage(Page<Product> page, @Param("ew") LambdaQueryWrapper<Product> wrapper);

    /**
     * SQL 提供者
     */
    class ProductSqlProvider {

        public static String getProductFullPage(ProviderContext context, Map<String, Object> params) {
            @SuppressWarnings("unchecked") LambdaQueryWrapper<Product> wrapper = (LambdaQueryWrapper<Product>) params.get("ew");

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");
            sql.append("p.product_id, ");
            sql.append("p.product_code, ");
            sql.append("p.product_name, ");
            sql.append("p.category_id, ");
            sql.append("pc.category_name, ");
            sql.append("p.product_type, ");
            sql.append("p.product_status, ");
            sql.append("p.current_bom_id, ");
            sql.append("pb.bom_name, ");
            sql.append("pb.bom_code, ");
            sql.append("pb.bom_version, ");
            sql.append("p.current_route_id, ");
            sql.append("pr.routing_name AS route_name, ");
            sql.append("pr.routing_code AS route_code, ");
            sql.append("pr.routing_version AS route_version, ");
            sql.append("p.remark, ");
            sql.append("p.create_time, ");
            sql.append("p.update_time, ");
            sql.append("p.unit ");
            sql.append("FROM product p ");
            sql.append("LEFT JOIN product_category pc ON p.category_id = pc.category_id ");
            sql.append("LEFT JOIN product_bom pb ON p.current_bom_id = pb.bom_id ");
            sql.append("LEFT JOIN product_routing pr ON p.current_route_id = pr.routing_id ");

            // 添加条件
            if (wrapper != null) {
                String customSqlSegment = wrapper.getCustomSqlSegment();
                if (customSqlSegment != null && !customSqlSegment.isEmpty()) {
                    sql.append(customSqlSegment);
                }
            }

            sql.append(" ORDER BY p.create_time DESC");

            return sql.toString();
        }
    }
}
