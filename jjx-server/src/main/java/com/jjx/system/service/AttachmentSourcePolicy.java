package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Explicit allowlist, backed by existing controllers and verified schema. Unknown sources fail closed.
 *
 * <p>来源体系治理（dev-20261014-031）：一个来源 = 一份声明式配置（{@link Source}），
 * 全部登记在 {@link #SOURCES} 这唯一一张表里。新增来源只改一处——
 * 「按单号反查ID」「取链路号」「权限拼接」都从这份配置读取，不再各写 if 分支。
 *
 * <p>两套写权限范式（判据写在 {@link Source#docWrite()}）：
 * <ul>
 *   <li><b>凭证/受控文件类</b>：来源 name 以 {@code :doc} 结尾（收货票据 purchase_receipt、
 *       采购发票 purchase_invoice、销售送货回签件 sales_delivery、质量档案回传件 quality_archive、
 *       质量模板文件 quality_template），写权限用 {@code :add}。</li>
 *   <li><b>单据附件类</b>：订单/报价/样品等（sales_order / quotation / sample …），写权限用 {@code :edit}。</li>
 * </ul>
 * 读权限两类一律 {@code :view}。新增来源按上表判据选择 {@code docWrite}。
 */
@Service
@RequiredArgsConstructor
public class AttachmentSourcePolicy {
    private final JdbcTemplate jdbc;

    /**
     * 一个附件来源的完整声明（唯一扩展点）。
     *
     * @param permission 权限前缀；{@code null} = 无独立权限点（仅需登录，见 SOURCES 中 task 说明）。
     * @param sql        存在性/回显单号校验 SQL（check / canRead 用，绑定 来源ID）。
     * @param idByNoSql  按业务单号反查 来源ID（resolveReadId 用，绑定 单号）。
     * @param productSql 校验产品归属（checkProduct 用，绑定 来源ID, 产品ID）；无产品关联为 {@code null}。
     * @param traceSql   取链路号（traceIdOf 用，绑定 来源ID）；无 trace 列为 {@code null} → 返回空。
     * @param docWrite   写权限范式判据：{@code true}=凭证/受控文件类（写 {@code :add}）；
     *                   {@code false}=单据附件类（写 {@code :edit}）。读权限一律 {@code :view}。
     */
    private record Source(String permission, String sql, String idByNoSql, String productSql, String traceSql, boolean docWrite) {}

    /**
     * 来源白名单——唯一真源。每新增一个来源只在此添加一条。
     * 字典 {@code attachment_document_type.ext_data.sources} 的取值必须取自本表键集合（见 {@link #knownSourceTypes()}）。
     */
    private static final Map<String, Source> SOURCES = Map.ofEntries(
        Map.entry("product", new Source("product",
            "SELECT product_code FROM product WHERE product_id=?",
            "SELECT product_id FROM product WHERE product_code=?",
            "SELECT COUNT(*) FROM product WHERE product_id=? AND product_id=?",
            null, false)),
        Map.entry("inquiry", new Source("sales:inquiry",
            "SELECT inquiry_no FROM sales_inquiry WHERE inquiry_id=? AND deleted=0",
            "SELECT inquiry_id FROM sales_inquiry WHERE inquiry_no=? AND deleted=0",
            "SELECT COUNT(*) FROM sales_inquiry i JOIN product p ON (i.product_id=p.product_id OR (i.product_id IS NULL AND i.product_code=p.product_code)) WHERE i.inquiry_id=? AND p.product_id=? AND i.deleted=0",
            "SELECT trace_id FROM sales_inquiry WHERE inquiry_id=? AND deleted=0", false)),
        Map.entry("quotation", new Source("sales:quotation",
            "SELECT quotation_no FROM sales_quotation WHERE quotation_id=? AND deleted=0",
            "SELECT quotation_id FROM sales_quotation WHERE quotation_no=? AND deleted=0",
            "SELECT COUNT(*) FROM sales_quotation_item i JOIN product p ON (i.product_id=p.product_id OR (i.product_id IS NULL AND i.product_code=p.product_code)) WHERE i.quotation_id=? AND p.product_id=?",
            "SELECT trace_id FROM sales_quotation WHERE quotation_id=? AND deleted=0", false)),
        Map.entry("sales_order", new Source("sales:order",
            "SELECT order_no FROM sales_order WHERE order_id=? AND deleted=0 AND NOT EXISTS (SELECT 1 FROM sales_sample_order s WHERE s.order_id=sales_order.order_id AND s.deleted=0)",
            "SELECT order_id FROM sales_order WHERE order_no=? AND deleted=0 AND NOT EXISTS (SELECT 1 FROM sales_sample_order s WHERE s.order_id=sales_order.order_id AND s.deleted=0)",
            "SELECT COUNT(*) FROM sales_order_product i JOIN product p ON (i.product_id=p.product_id OR (i.product_id IS NULL AND i.product_code=p.product_code)) WHERE i.order_id=? AND p.product_id=?",
            "SELECT trace_id FROM sales_order WHERE order_id=? AND deleted=0", false)),
        Map.entry("sample", new Source("sales:sample",
            "SELECT o.order_no FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE s.order_id=? AND s.deleted=0 AND o.deleted=0",
            "SELECT s.order_id FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE o.order_no=? AND s.deleted=0 AND o.deleted=0",
            "SELECT COUNT(*) FROM sales_sample_order i JOIN product p ON (i.product_id=p.product_id OR (i.product_id IS NULL AND i.product_code=p.product_code)) WHERE i.order_id=? AND p.product_id=? AND i.deleted=0",
            "SELECT o.trace_id FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE s.order_id=? AND s.deleted=0 AND o.deleted=0", false)),
        Map.entry("production_order", new Source("production:order",
            "SELECT order_no FROM production_order WHERE order_id=?",
            "SELECT order_id FROM production_order WHERE order_no=?",
            "SELECT COUNT(*) FROM production_order WHERE order_id=? AND product_id=?",
            "SELECT trace_id FROM production_order WHERE order_id=?", false)),
        Map.entry("purchase_order", new Source("purchase:order",
            "SELECT order_no FROM purchase_order WHERE order_id=?",
            "SELECT order_id FROM purchase_order WHERE order_no=?",
            null,
            "SELECT trace_id FROM purchase_order WHERE order_id=?", false)),
        Map.entry("purchase_payment", new Source("purchase:payment",
            "SELECT payment_no FROM purchase_payment WHERE payment_id=?",
            "SELECT payment_id FROM purchase_payment WHERE payment_no=?",
            null, null, false)),
        // task（看板任务）：无独立权限点——看板任务接口对已登录用户开放（登录即边界），
        // 附件沿用同一边界并核验任务存在，故不补权限点；若看板将来引入细粒度权限，此处再补。
        Map.entry("task", new Source(null,
            "SELECT task_code FROM sys_task WHERE task_id=?",
            "SELECT task_id FROM sys_task WHERE task_code=?",
            null, null, false)),
        Map.entry("material", new Source("inventory:material",
            "SELECT material_code FROM inventory_material WHERE material_id=?",
            "SELECT material_id FROM inventory_material WHERE material_code=?",
            "SELECT COUNT(*) FROM inventory_material WHERE material_id=? AND product_id=?",
            null, false)),
        // 收货票据（凭证/受控文件类）：来源名 purchase_receipt，ID 实为入库单（inventory_inbound_order）ID。
        Map.entry("purchase_receipt", new Source("purchase:receipt:doc",
            "SELECT inbound_no FROM inventory_inbound_order WHERE inbound_id=? AND source_type='PURCHASE'",
            "SELECT inbound_id FROM inventory_inbound_order WHERE inbound_no=? AND source_type='PURCHASE'",
            null,
            "SELECT trace_id FROM inventory_inbound_order WHERE inbound_id=? AND source_type='PURCHASE'", true)),
        // 采购发票（凭证/受控文件类）：来源名 purchase_invoice，ID 实为采购单据表 document_type='invoice' 的 ID；无 trace。
        Map.entry("purchase_invoice", new Source("purchase:invoice:doc",
            "SELECT document_no FROM purchase_document WHERE document_id=? AND document_type='invoice'",
            "SELECT document_id FROM purchase_document WHERE document_no=? AND document_type='invoice'",
            null, null, true)),
        // 销售送货回签件（凭证/受控文件类，dev-20261014-023）：独立权限域 sales:delivery:doc，写权限 :add；无 trace。
        Map.entry("sales_delivery", new Source("sales:delivery:doc",
            "SELECT delivery_no FROM sales_delivery WHERE delivery_id=? AND deleted=0",
            "SELECT delivery_id FROM sales_delivery WHERE delivery_no=? AND deleted=0",
            null, null, true)),
        // 质量档案回传扫描件（凭证/受控文件类，dev-20261014-025）：独立权限域 production:quality-archive:doc，写权限 :add；
        // ID 实为质量档案打印记录（quality_template_print_log.id），单号取档案记录号（模板登记优先，回退打印快照）；无产品关联、无 trace。
        Map.entry("quality_archive", new Source("production:quality-archive:doc",
            "SELECT COALESCE(t.record_no, l.record_no) FROM quality_template_print_log l LEFT JOIN quality_template_registry t ON t.id=l.template_id WHERE l.id=?",
            "SELECT l.id FROM quality_template_print_log l LEFT JOIN quality_template_registry t ON t.id=l.template_id WHERE COALESCE(t.record_no, l.record_no)=?",
            null, null, true)),
        // 质量记录模板文件（受控/凭证类，dev-20261014-026）：独立权限域 production:quality-template:doc，写权限 :add；
        // ID 实为质量模板登记表（quality_template_registry.id），单号取记录编号（record_no）；无产品关联、无 trace。
        Map.entry("quality_template", new Source("production:quality-template:doc",
            "SELECT record_no FROM quality_template_registry WHERE id=?",
            "SELECT id FROM quality_template_registry WHERE record_no=?",
            null, null, true))
    );

    /** 来源清单唯一真源：字典 attachment_document_type.ext_data.sources 的取值必须落在此集合内。 */
    public static Set<String> knownSourceTypes() {
        return SOURCES.keySet();
    }

    /** 权限 = 前缀 + 读(:view) / 写（凭证类 :add，单据类 :edit）。前缀为 null 的来源无独立权限点。 */
    private String permission(Source source, boolean write) {
        return source.permission() + (write ? (source.docWrite() ? ":add" : ":edit") : ":view");
    }

    public Long resolveReadId(String type, String number) {
        StpUtil.checkLogin();
        String canonical = canonical(type);
        Source source = source(canonical);
        List<Long> ids = jdbc.queryForList(source.idByNoSql(), Long.class, number.trim());
        // 先解析再判权；即使未匹配也执行同一权限检查，不向无权限用户泄露单号是否存在。
        if (source.permission() != null) StpUtil.checkPermission(source.permission() + ":view");
        return ids.size() == 1 ? ids.get(0) : null;
    }

    /** 取来源单据的链路标识；来源无 trace 或查不到 → null（调用方据此跳过回填） */
    public String traceIdOf(String type, Long id) {
        if (id == null || id <= 0) return null;
        Source source = SOURCES.get(canonical(type));
        String sql = source == null ? null : source.traceSql();
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
            StpUtil.checkPermission(permission(source, write));
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
