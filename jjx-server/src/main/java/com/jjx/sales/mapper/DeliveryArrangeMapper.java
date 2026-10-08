package com.jjx.sales.mapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.sales.domain.dto.DeliveryArrangeQueryDTO;
import com.jjx.sales.domain.vo.DeliveryArrangeLineVO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface DeliveryArrangeMapper {
    @Select("""
      <script>
      SELECT p.id,p.order_id,o.order_no,o.customer_id,o.customer_name,COALESCE(NULLIF(TRIM(o.delivery_address),''),NULLIF(TRIM(c.address),'')) AS delivery_address,
        COALESCE(NULLIF(TRIM(o.contact_person),''),c.contact_person) AS contact_person,
        COALESCE(NULLIF(TRIM(o.contact_phone),''),c.contact_phone) AS contact_phone,COALESCE(NULLIF(TRIM(o.currency),''),'CNY') AS currency,
        NULL AS delivery_method,o.delivery_date AS due_date,
        p.product_id,p.product_code,p.product_name,COALESCE(NULLIF(TRIM(p.customer_material_no),''),p.product_name) AS customer_material_no,p.specification,p.unit,
        p.quantity,p.unit_price,COALESCE(a.shipped,0) AS shipped,COALESCE(a.occupied,0) AS occupied,
        p.quantity-COALESCE(a.shipped,0)-COALESCE(a.occupied,0) AS order_remaining_quantity,
        GREATEST(0,COALESCE(s.total_quantity,0)-COALESCE(s.total_reserved,0)) AS stock_available
      FROM sales_order_product p JOIN sales_order o ON o.order_id=p.order_id
      LEFT JOIN sales_customer c ON c.customer_id=o.customer_id AND c.deleted=0
      LEFT JOIN (SELECT i.order_product_id,
        SUM(CASE WHEN d.delivery_status IN (#{shipped},#{received}) THEN i.quantity ELSE 0 END) AS shipped,
        SUM(CASE WHEN d.delivery_status=#{pending} THEN i.quantity ELSE 0 END) AS occupied
        FROM sales_delivery_item i JOIN sales_delivery d ON d.delivery_id=i.delivery_id
        WHERE d.deleted=0 GROUP BY i.order_product_id) a ON a.order_product_id=p.id
      LEFT JOIN inventory_item ii ON ii.item_type=#{productType} AND ii.source_id=p.product_id
      LEFT JOIN inventory_stock s ON s.inventory_item_id=ii.inventory_item_id
      WHERE o.deleted=0 AND o.order_status=#{production}
        AND p.quantity-COALESCE(a.shipped,0)-COALESCE(a.occupied,0)&gt;0
      <if test="q.orderId != null">AND o.order_id=#{q.orderId}</if>
      <if test="q.customerId != null">AND o.customer_id=#{q.customerId}</if>
      <if test="q.keyword != null and q.keyword != ''">
        AND (o.order_no LIKE CONCAT('%',#{q.keyword},'%') OR o.customer_name LIKE CONCAT('%',#{q.keyword},'%')
          OR p.product_code LIKE CONCAT('%',#{q.keyword},'%') OR p.product_name LIKE CONCAT('%',#{q.keyword},'%')
          OR p.customer_material_no LIKE CONCAT('%',#{q.keyword},'%'))
      </if>
      ORDER BY o.delivery_date IS NULL,o.delivery_date,o.order_id,p.id
      </script>
      """)
    IPage<DeliveryArrangeLineVO> page(Page<DeliveryArrangeLineVO> page,
      @Param("q") DeliveryArrangeQueryDTO query, @Param("production") Integer production,
      @Param("pending") Integer pending,@Param("shipped") Integer shipped,
      @Param("received") Integer received,@Param("productType") String productType);
    @Select("""
        SELECT p.order_id, SUM(i.quantity) AS quantity
        FROM sales_delivery_item i
        JOIN sales_delivery d ON d.delivery_id=i.delivery_id AND d.deleted=0
        JOIN sales_order_product p ON p.id=i.order_product_id
        WHERE p.product_id=#{productId} AND d.delivery_status=#{pending}
        GROUP BY p.order_id
        """)
    java.util.List<com.jjx.sales.domain.vo.DeliveryPendingQuantityVO> pendingByOrder(@Param("productId") Long productId, @Param("pending") Integer pending);
}
