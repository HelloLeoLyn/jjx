package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/** Explicit allowlist, backed by existing controllers and verified schema. Unknown sources fail closed. */
@Service
@RequiredArgsConstructor
public class AttachmentSourcePolicy {
    private final JdbcTemplate jdbc;
    private record Source(String permission, String sql, String productSql) {}
    private static final Map<String, Source> SOURCES = Map.of(
        "product", new Source("product", "SELECT product_code FROM product WHERE product_id=?",
            "SELECT COUNT(*) FROM product WHERE product_id=? AND product_id=?"),
        "inquiry", new Source("sales:inquiry", "SELECT inquiry_no FROM sales_inquiry WHERE inquiry_id=? AND deleted=0",
            "SELECT COUNT(*) FROM sales_inquiry i JOIN product p ON (i.product_id=p.product_id OR (i.product_id IS NULL AND i.product_code=p.product_code)) WHERE i.inquiry_id=? AND p.product_id=? AND i.deleted=0"),
        "quotation", new Source("sales:quotation", "SELECT quotation_no FROM sales_quotation WHERE quotation_id=? AND deleted=0",
            "SELECT COUNT(*) FROM sales_quotation_item i JOIN product p ON (i.product_id=p.product_id OR (i.product_id IS NULL AND i.product_code=p.product_code)) WHERE i.quotation_id=? AND p.product_id=?"),
        "sales_order", new Source("sales:order", "SELECT order_no FROM sales_order WHERE order_id=? AND deleted=0 AND NOT EXISTS (SELECT 1 FROM sales_sample_order s WHERE s.order_id=sales_order.order_id AND s.deleted=0)",
            "SELECT COUNT(*) FROM sales_order_product i JOIN product p ON (i.product_id=p.product_id OR (i.product_id IS NULL AND i.product_code=p.product_code)) WHERE i.order_id=? AND p.product_id=?"),
        "sample", new Source("sales:sample", "SELECT o.order_no FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE s.order_id=? AND s.deleted=0 AND o.deleted=0",
            "SELECT COUNT(*) FROM sales_sample_order i JOIN product p ON (i.product_id=p.product_id OR (i.product_id IS NULL AND i.product_code=p.product_code)) WHERE i.order_id=? AND p.product_id=? AND i.deleted=0"),
        "production_order", new Source("production:order", "SELECT order_no FROM production_order WHERE order_id=?",
            "SELECT COUNT(*) FROM production_order WHERE order_id=? AND product_id=?"),
        "purchase_order", new Source("purchase:order", "SELECT order_no FROM purchase_order WHERE order_id=?", null),
        "material", new Source("inventory:material", "SELECT material_code FROM inventory_material WHERE material_id=?",
            "SELECT COUNT(*) FROM inventory_material WHERE material_id=? AND product_id=?")
    );

    public String canonical(String type) {
        if (type == null) throw new BusinessException("附件来源不能为空");
        return switch (type) {
            case "order" -> "sales_order";
            case "sample_order" -> "sample";
            case "purchase" -> "purchase_order";
            case "production" -> "production_order";
            default -> type;
        };
    }

    public String check(String type, Long id, boolean write) {
        StpUtil.checkLogin();
        Source source = source(type);
        StpUtil.checkPermission(source.permission() + (write ? ":edit" : ":view"));
        if (id == null || id <= 0) throw new BusinessException("业务来源ID无效");
        List<String> numbers = jdbc.queryForList(source.sql(), String.class, id);
        if (numbers.size() != 1) throw new BusinessException("业务来源不存在或不唯一");
        return numbers.get(0);
    }

    public boolean canRead(String type, Long id) {
        if (!StpUtil.isLogin() || type == null || id == null || id <= 0) return false;
        Source source = SOURCES.get(canonical(type));
        return source != null && StpUtil.hasPermission(source.permission() + ":view")
            && jdbc.queryForList(source.sql(), String.class, id).size() == 1;
    }

    public void checkProduct(String type, Long sourceId, Long productId) {
        if (productId == null) return;
        check("product", productId, false);
        Source source = source(type);
        if (source.productSql() == null) throw new BusinessException("该来源的产品关系尚未确认，不能指定productId");
        Long count = jdbc.queryForObject(source.productSql(), Long.class, sourceId, productId);
        if (count == null || count == 0) throw new BusinessException("产品不属于该业务来源");
    }

    private Source source(String type) {
        Source source = SOURCES.get(canonical(type));
        if (source == null) throw new BusinessException("附件来源权限映射未确认，禁止操作：" + type);
        return source;
    }
}
