package com.jjx.system.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 产品客供资料的唯一业务附件入口：只读有效引用，不回退附件表的旧业务归属。 */
@Service
@RequiredArgsConstructor
public class ProductCustomerDocumentService {
    private final JdbcTemplate jdbc;
    private final AttachmentSourcePolicy sources;

    static final String QUERY = """
            SELECT a.id,a.file_name,a.file_path,a.file_size,a.file_type,a.sha256,a.version,
                   a.create_by,a.create_time,a.update_time,
                   r.id AS ref_id,r.source_type AS biz_type,r.source_id AS biz_id,
                   r.product_id,r.product_code,r.document_type AS customer_doc_type,
                   r.display_name,r.sort_order,r.update_time AS ref_update_time,
                   COALESCE(dt.label,r.document_type) AS category,
                   CASE r.source_type WHEN 'inquiry' THEN '询价单' WHEN 'quotation' THEN '报价单'
                        WHEN 'sales_order' THEN '销售订单' WHEN 'sample' THEN '样品单' END AS sourceType,
                   CASE r.source_type WHEN 'inquiry' THEN i.inquiry_no WHEN 'quotation' THEN q.quotation_no
                        ELSE o.order_no END AS sourceNo
              FROM sys_attachment_ref r
              JOIN sys_attachment a ON a.id=r.attachment_id AND a.deleted=0
              JOIN product p ON p.product_id=?
                   AND (r.product_id=p.product_id OR (r.product_id IS NULL AND r.product_code=p.product_code))
                   AND (NULLIF(r.product_code,'') IS NULL OR r.product_code=p.product_code)
              LEFT JOIN sys_dict_item dt ON dt.dict_code='attachment_document_type'
                   AND dt.item_key=r.document_type AND dt.deleted=0
              LEFT JOIN sales_inquiry i ON r.source_type='inquiry' AND r.source_id=i.inquiry_id AND i.deleted=0
              LEFT JOIN sales_quotation q ON r.source_type='quotation' AND r.source_id=q.quotation_id AND q.deleted=0
              LEFT JOIN sales_order o ON r.source_type IN ('sales_order','sample') AND r.source_id=o.order_id AND o.deleted=0
              LEFT JOIN sales_sample_order so ON r.source_id=so.order_id AND so.deleted=0
             WHERE r.deleted=0 AND r.ref_state<>'draft' AND (
                   (i.inquiry_id IS NOT NULL AND (i.product_id=p.product_id OR (i.product_id IS NULL AND i.product_code=p.product_code)))
                OR (q.quotation_id IS NOT NULL AND EXISTS
                    (SELECT 1 FROM sales_quotation_item qi WHERE qi.quotation_id=q.quotation_id
                     AND (qi.product_id=p.product_id OR (qi.product_id IS NULL AND qi.product_code=p.product_code))))
                OR (r.source_type='sales_order' AND o.order_id IS NOT NULL AND so.order_id IS NULL AND EXISTS
                    (SELECT 1 FROM sales_order_product op WHERE op.order_id=o.order_id
                     AND (op.product_id=p.product_id OR (op.product_id IS NULL AND op.product_code=p.product_code))))
                OR (r.source_type='sample' AND o.order_id IS NOT NULL AND so.order_id IS NOT NULL
                    AND (so.product_id=p.product_id OR (so.product_id IS NULL AND so.product_code=p.product_code)))
             )
             ORDER BY a.create_time DESC,r.id DESC
            """;

    public List<Map<String, Object>> list(Long productId) {
        return query(productId, false);
    }

    /** 发布事务内锁定引用和文件；用于来源令牌，解除/改名/类型变化都纳入并发校验。 */
    public List<Map<String, Object>> lockForSnapshot(Long productId) {
        return query(productId, true);
    }

    private List<Map<String, Object>> query(Long productId, boolean lock) {
        if (productId == null) return List.of();
        return jdbc.queryForList(QUERY + (lock ? " FOR SHARE" : ""), productId).stream()
                .filter(row -> sources.canRead((String) row.get("biz_type"), ((Number) row.get("biz_id")).longValue()))
                .toList();
    }
}
