package com.jjx.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 附件展示名生成（dev-20261016-003，第一阶段②）。
 *
 * <p>规则（真源：{@code jjx-docs/reference/attachment-refactor-decisions-dev-20261016-001.md} §5-1）：
 * <b>来源类型编码-来源Id-P产品id-一级码-二级码-二级名</b>；
 * 无产品时 P 段为 {@code P0}；<b>除产品 id 外各要素必须齐，缺任一不生成</b>。
 * 例：{@code INQ-3-P123-CUS-CD-客户图纸}。
 *
 * <p>编码对照表与前端 {@code AttachmentEnum} 一致；前端撤「改名」后这里成为唯一处。
 * 目标：展示名由后端按规则统一生成，前端不再录入。
 */
@Service
@RequiredArgsConstructor
public class AttachmentDisplayNameService {
    private static final String CATEGORY = AttachmentDocumentTypeService.CATEGORY;
    private static final String TYPE = AttachmentDocumentTypeService.TYPE;
    /** 产品 id 缺省占位：保持 P 前缀；0 不是合法产品 id，一眼看出"没有产品" */
    private static final String PRODUCT_PLACEHOLDER = "P0";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    private static final Map<String, String> SOURCE_CODE = Map.ofEntries(
        Map.entry("product", "PROD"), Map.entry("inquiry", "INQ"), Map.entry("quotation", "QT"),
        Map.entry("sales_order", "SO"), Map.entry("sample", "SP"), Map.entry("sales_delivery", "DL"),
        Map.entry("purchase_order", "PO"), Map.entry("purchase_payment", "PAY"), Map.entry("purchase_receipt", "IN"),
        Map.entry("purchase_invoice", "INV"), Map.entry("quality_archive", "QA"), Map.entry("quality_template", "TMPL"),
        Map.entry("production_order", "WO"), Map.entry("material", "MTR"));

    private static final Map<String, String> CATEGORY_CODE = Map.of(
        "BUSINESS_DOC", "BIZ", "CUSTOMER_DOC", "CUS", "ENGINEERING_DOC", "ENG",
        "GENERAL", "GEN", "QUALITY_DOC", "QUA");

    private static final Map<String, String> TYPE_CODE = Map.ofEntries(
        Map.entry("CONTRACT", "CT"), Map.entry("ORDER_CONFIRMATION", "OC"),
        Map.entry("SALES_INQUIRY_ATTACHMENT", "IA"), Map.entry("SALES_QUOTATION_ATTACHMENT", "QA"),
        Map.entry("CUSTOMER_APPROVAL", "CA"), Map.entry("CUSTOMER_DRAWING", "CD"),
        Map.entry("CUSTOMER_REQUIREMENT", "CR"), Map.entry("CUSTOMER_SAMPLE", "CS"),
        Map.entry("PRODUCT_IMAGE", "PI"), Map.entry("PRODUCT_SPECIFICATION", "PS"),
        Map.entry("BOM", "BOM"), Map.entry("DIE_CUTTING_PARAMETER", "DP"),
        Map.entry("DIE_DRAWING", "DD"), Map.entry("ENGINEERING_DRAWING", "ED"),
        Map.entry("INK_MIXING_RECORD", "IM"), Map.entry("PRINTING_PROCESS", "PP"),
        Map.entry("PROCESS_ROUTE", "PR"), Map.entry("WORK_INSTRUCTION", "WI"),
        Map.entry("OTHER", "OT"), Map.entry("ABNORMAL_REPORT", "AR"),
        Map.entry("CERTIFICATE_OF_CONFORMITY", "CC"), Map.entry("DEFECT_PHOTO", "DF"),
        Map.entry("INSPECTION_REPORT", "IR"), Map.entry("TEST_RECORD", "TR"),
        Map.entry("STRUCTURE_DRAWING", "SD"), Map.entry("OUTLINE_DRAWING", "OD"),
        Map.entry("PANEL_DRAWING", "PD"), Map.entry("CIRCUIT_DRAWING", "CI"),
        Map.entry("FILM", "FL"), Map.entry("CONFIRM_DRAWING", "CF"),
        Map.entry("PRINT_GUIDE", "PG"), Map.entry("COLOR_CHECK", "CK"));

    /**
     * 按命名规则生成展示名。
     * 要素不齐（除产品 id 外：来源类型 / 来源Id / 二级类型 / 其一级 / 二级名）时返回 {@code null}，由调用方决定留空。
     */
    public String build(String source, Long sourceId, String documentType) {
        return build(source, sourceId, null, documentType);
    }

    public String build(String source, Long sourceId, Long productId, String documentType) {
        if (source == null || source.isBlank() || sourceId == null) return null;
        if (documentType == null || documentType.isBlank()) return null;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT i.label, i.ext_data FROM sys_dict_item i JOIN sys_dict d ON d.dict_code=i.dict_code"
                + " AND d.deleted=0 AND d.is_active=1 WHERE i.dict_code=? AND i.item_key=? AND i.deleted=0 AND i.is_active=1",
            TYPE, documentType);
        if (rows.size() != 1) return null;
        Object label = rows.get(0).get("label");
        String second = label == null ? null : String.valueOf(label).trim();
        String parent;
        try {
            JsonNode ext = json.readTree(String.valueOf(rows.get(0).get("ext_data")));
            parent = ext != null && ext.path("parent").isTextual() ? ext.path("parent").asText() : null;
        } catch (Exception ex) {
            return null;
        }
        if (second == null || second.isEmpty() || parent == null || parent.isBlank()) return null;
        String sourceCode = SOURCE_CODE.getOrDefault(source, source.toUpperCase());
        return sourceCode + '-'
            + sourceId + '-'
            + (productId == null ? PRODUCT_PLACEHOLDER : "P" + productId) + '-'
            + CATEGORY_CODE.getOrDefault(parent, parent) + '-'
            + TYPE_CODE.getOrDefault(documentType, documentType) + '-'
            + second;
    }
}
