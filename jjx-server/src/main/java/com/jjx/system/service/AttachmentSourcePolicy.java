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
        "purchase_payment", new Source("purchase:payment", "SELECT payment_no FROM purchase_payment WHERE payment_id=?", null),
        // 看板任务接口对已登录用户开放，暂无独立权限点；附件沿用同一边界并核验任务存在。
        "task", new Source(null, "SELECT task_code FROM sys_task WHERE task_id=?", null),
        "material", new Source("inventory:material", "SELECT material_code FROM inventory_material WHERE material_id=?",
            "SELECT COUNT(*) FROM inventory_material WHERE material_id=? AND product_id=?")
    );

    /**
     * 各来源单据的链路标识查询（用于建引用时回填附件的 trace_id）。
     * 无 trace 列的表（product / inventory_material）不在此表 → traceIdOf 返回 null，附件保持空。
     */
    private static final Map<String, String> TRACE_SQL = Map.of(
        "inquiry", "SELECT trace_id FROM sales_inquiry WHERE inquiry_id=? AND deleted=0",
        "quotation", "SELECT trace_id FROM sales_quotation WHERE quotation_id=? AND deleted=0",
        "sales_order", "SELECT trace_id FROM sales_order WHERE order_id=? AND deleted=0",
        "sample", "SELECT o.trace_id FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE s.order_id=? AND s.deleted=0 AND o.deleted=0",
        "purchase_order", "SELECT trace_id FROM purchase_order WHERE order_id=?",
        "production_order", "SELECT trace_id FROM production_order WHERE order_id=?"
    );

    /** 显式白名单，单号只作为绑定参数；样品与普通订单沿用各自的来源边界。 */
    private static final Map<String, String> ID_BY_NO_SQL = Map.of(
        "product", "SELECT product_id FROM product WHERE product_code=?",
        "inquiry", "SELECT inquiry_id FROM sales_inquiry WHERE inquiry_no=? AND deleted=0",
        "quotation", "SELECT quotation_id FROM sales_quotation WHERE quotation_no=? AND deleted=0",
        "sales_order", "SELECT order_id FROM sales_order WHERE order_no=? AND deleted=0 AND NOT EXISTS (SELECT 1 FROM sales_sample_order s WHERE s.order_id=sales_order.order_id AND s.deleted=0)",
        "sample", "SELECT s.order_id FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE o.order_no=? AND s.deleted=0 AND o.deleted=0",
        "production_order", "SELECT order_id FROM production_order WHERE order_no=?",
        "purchase_order", "SELECT order_id FROM purchase_order WHERE order_no=?",
        "purchase_payment", "SELECT payment_id FROM purchase_payment WHERE payment_no=?",
        "task", "SELECT task_id FROM sys_task WHERE task_code=?",
        "material", "SELECT material_id FROM inventory_material WHERE material_code=?"
    );

    public Long resolveReadId(String type, String number) {
        StpUtil.checkLogin();
        String canonical = canonical(type);
        Source source = source(canonical);
        List<Long> ids = jdbc.queryForList(ID_BY_NO_SQL.get(canonical), Long.class, number.trim());
        // 先解析再判权；即使未匹配也执行同一权限检查，不向无权限用户泄露单号是否存在。
        if (source.permission() != null) StpUtil.checkPermission(source.permission() + ":view");
        return ids.size() == 1 ? ids.get(0) : null;
    }

    /** 取来源单据的链路标识；来源无 trace 或查不到 → null（调用方据此跳过回填） */
    public String traceIdOf(String type, Long id) {
        if (id == null || id <= 0) return null;
        String sql = TRACE_SQL.get(canonical(type));
        if (sql == null) return null;
        List<String> rows = jdbc.queryForList(sql, String.class, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

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
        if (source.permission() != null) {
            StpUtil.checkPermission(source.permission() + (write ? ":edit" : ":view"));
        }
        if (id == null || id <= 0) throw new BusinessException("业务来源ID无效");
        List<String> numbers = jdbc.queryForList(source.sql(), String.class, id);
        if (numbers.size() != 1) throw new BusinessException("业务来源不存在或不唯一");
        return numbers.get(0);
    }

    public boolean canRead(String type, Long id) {
        if (!StpUtil.isLogin() || type == null || id == null || id <= 0) return false;
        Source source = SOURCES.get(canonical(type));
        return source != null && (source.permission() == null || StpUtil.hasPermission(source.permission() + ":view"))
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
