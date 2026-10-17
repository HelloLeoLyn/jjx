package com.jjx.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
    private static final Logger log = LoggerFactory.getLogger(AttachmentDisplayNameService.class);
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
        return baseName(source, sourceId, productId, documentType);
    }

    /** 不带序号的命名组成部分；作为旧自动名称识别依据。 */
    public String baseName(String source, Long sourceId, Long productId, String documentType) {
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

    public String numbered(String baseName, int sequence) {
        if (baseName == null || sequence < 1) return null;
        return baseName + '-' + String.format(java.util.Locale.ROOT, "%02d", sequence);
    }

    /** 识别此前无编号的自动名称和本规则生成的带编号名称；其他值视为用户自定义。 */
    public boolean isGeneratedName(String displayName, String baseName) {
        if (displayName == null) return true;
        if (baseName == null) return false;
        return displayName.equals(baseName)
            || displayName.matches(Pattern.quote(baseName) + "-[0-9]{2,}");
    }

    /**
     * 按组取得事务级互斥锁。命名锁在事务完成后释放，因此创建、改类型和解除引用的重排互不穿插。
     * MySQL GET_LOCK 名称限制为 64 字符；完整 SHA-256 十六进制串正好 64 字符。
     */
    public void lockGroups(Collection<GroupKey> groups) {
        if (groups == null || groups.isEmpty()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("附件展示名分组锁必须在事务中获取");
        }
        @SuppressWarnings("unchecked")
        Set<String> existing = (Set<String>) TransactionSynchronizationManager.getResource(this);
        if (existing == null) {
            Set<String> heldLocks = new HashSet<>();
            TransactionSynchronizationManager.bindResource(this, heldLocks);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    List<String> names = new ArrayList<>(heldLocks);
                    names.sort(Comparator.reverseOrder());
                    for (String name : names) {
                        try {
                            jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, name);
                        } catch (RuntimeException ex) {
                            log.warn("Failed to release attachment display-name lock", ex);
                        }
                    }
                    if (TransactionSynchronizationManager.hasResource(AttachmentDisplayNameService.this)) {
                        TransactionSynchronizationManager.unbindResource(AttachmentDisplayNameService.this);
                    }
                }
            });
            existing = heldLocks;
        }
        Set<String> held = existing;
        List<String> names = groups.stream().filter(java.util.Objects::nonNull).distinct()
            .map(this::lockName).sorted().toList();
        for (String name : names) {
            if (held.contains(name)) continue;
            Integer acquired = jdbc.queryForObject("SELECT GET_LOCK(?, 10)", Integer.class, name);
            if (!Integer.valueOf(1).equals(acquired)) {
                throw new IllegalStateException("获取附件展示名分组锁超时");
            }
            held.add(name);
        }
    }

    /** 重新分配组内自动名称；自定义名称保留，并从自动序号中排除以避免碰撞。调用方必须先持有该组锁。 */
    public void renumber(String source, Long sourceId, Long productId, String documentType, String operator) {
        String base = baseName(source, sourceId, productId, documentType);
        if (base == null) return;
        List<NameRow> rows = jdbc.query(
            "SELECT id,display_name FROM sys_attachment_ref WHERE source_type=? AND source_id=?"
                + " AND product_id <=> ? AND document_type <=> ? AND deleted=0"
                + " AND COALESCE(ref_state,'active')<>'draft' ORDER BY id FOR UPDATE",
            (rs, rowNum) -> new NameRow(rs.getLong("id"), rs.getString("display_name")),
            source, sourceId, productId, documentType);
        Set<String> customNames = new HashSet<>();
        for (NameRow row : rows) {
            if (!isGeneratedName(row.displayName(), base) && row.displayName() != null) {
                customNames.add(row.displayName());
            }
        }
        int sequence = 1;
        for (NameRow row : rows) {
            if (!isGeneratedName(row.displayName(), base)) continue;
            String next;
            do {
                next = numbered(base, sequence++);
            } while (customNames.contains(next));
            if (!next.equals(row.displayName())) {
                jdbc.update("UPDATE sys_attachment_ref SET display_name=?,update_by=?,update_time=NOW()"
                        + " WHERE id=? AND deleted=0",
                    next, operator, row.id());
            }
        }
    }

    public record GroupKey(String source, Long sourceId, Long productId, String documentType) {}

    private record NameRow(Long id, String displayName) {}

    private String lockName(GroupKey group) {
        String value = part(group.source()) + part(group.sourceId()) + part(group.productId()) + part(group.documentType());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String part(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text.length() + ":" + text + '|';
    }
}
