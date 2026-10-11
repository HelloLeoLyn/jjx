package com.jjx.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.ProductWorkSpecItem;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.enums.WorkSpecResourceType;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.ProductWorkSpecItemMapper;
import com.jjx.product.mapper.ProductWorkSpecMapper;
import com.jjx.product.mapper.ProductWorkSpecVersionMapper;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 产品作业规范发布版本服务（dev-20261011-008）
 * 发布 = 把当前工作态资料固化为不可变版本：规范正文存快照，BOM/路线/图纸记引用（+哈希）。
 */
@Service
@RequiredArgsConstructor
public class ProductWorkSpecVersionService {

    private final ProductWorkSpecMapper workSpecMapper;
    private final ProductMapper productMapper;
    private final SysAttachmentMapper attachmentMapper;
    private final ProductWorkSpecVersionMapper versionMapper;
    private final ProductWorkSpecItemMapper itemMapper;
    private final ObjectMapper json;

    /** 凹凸差异字段：内部键 → 中文名（dev-20261011-023）。 */
    private static final Object[][] EMBOSS_DIFF_FIELDS = {
            {"setupHeight", "调机高度"}, {"requiredHeight", "要求高度"},
            {"upperTemperature", "上模温度"}, {"lowerTemperature", "下模温度"},
            {"pressTime", "下压时间"}, {"holdTime", "保持时间"},
    };

    /** 发布时「未纳入」的子页签（固定提示，dev-20261011-023）。 */
    private static final List<String> NOT_INCLUDED = List.of(
            "客供资料", "样品", "领料单", "油墨", "实际检测记录");

    /** 发布新版本（产品内串行，避免并发产生重复版本号/混合快照）。 */
    @Transactional(rollbackFor = Exception.class)
    public ProductWorkSpecVersion publish(Long productId, String changeSummary) {
        if (workSpecMapper.lock(productId) == null) {
            throw new BusinessException("产品不存在: " + productId);
        }
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("产品不存在: " + productId);
        }
        String raw = workSpecMapper.read(productId);
        if (raw == null || raw.isBlank()) {
            raw = "{}";
        }

        int seq = Math.toIntExact(versionMapper.selectCount(
                Wrappers.<ProductWorkSpecVersion>lambdaQuery()
                        .eq(ProductWorkSpecVersion::getProductId, productId))) + 1;
        String versionNo = "V" + seq + ".0";

        List<ProductWorkSpecItem> items = new ArrayList<>();
        // 规范正文：存完整快照
        items.add(newItem(WorkSpecResourceType.SPEC_JSON.getCode(), null, null, null, raw, sha256(raw), 0));
        // BOM：引用当前已批准版本（记来源 + 版本号）
        if (product.getCurrentBomId() != null) {
            ObjectNode ref = json.createObjectNode();
            ref.put("bomId", product.getCurrentBomId());
            if (product.getCurrentBomVersion() != null) ref.put("bomVersion", product.getCurrentBomVersion());
            items.add(newItem(WorkSpecResourceType.BOM.getCode(), product.getCurrentBomId(), null, null,
                    ref.toString(), null, 1));
        }
        // 工艺路线：引用当前已批准版本
        if (product.getCurrentRouteId() != null) {
            ObjectNode ref = json.createObjectNode();
            ref.put("routingId", product.getCurrentRouteId());
            if (product.getCurrentRoutingVersion() != null) ref.put("routingVersion", product.getCurrentRoutingVersion());
            items.add(newItem(WorkSpecResourceType.ROUTING.getCode(), product.getCurrentRouteId(), null, null,
                    ref.toString(), null, 2));
        }
        // 结构图/工程图：引用附件 + 文件哈希（受控附件不可删）
        Long structureFileId = structureFileId(raw);
        if (structureFileId != null) {
            SysAttachment att = attachmentMapper.selectById(structureFileId);
            ObjectNode ref = json.createObjectNode();
            ref.put("attachmentId", structureFileId);
            if (att != null) {
                if (att.getFileName() != null) ref.put("fileName", att.getFileName());
                if (att.getVersion() != null) ref.put("version", att.getVersion());
            }
            items.add(newItem(WorkSpecResourceType.DRAWING.getCode(), structureFileId, null, null,
                    ref.toString(), att != null ? att.getSha256() : null, 3));
        }

        String manifest = items.stream()
                .sorted(Comparator.comparing(ProductWorkSpecItem::getResourceType))
                .map(i -> i.getResourceType() + "|" + (i.getSourceId() == null ? "" : i.getSourceId())
                        + "|" + (i.getContentHash() == null ? "" : i.getContentHash()))
                .collect(Collectors.joining("\n"));

        ProductWorkSpecVersion version = new ProductWorkSpecVersion();
        version.setProductId(productId);
        version.setVersionNo(versionNo);
        version.setStatus("PUBLISHED");
        version.setChangeSummary(changeSummary);
        version.setManifestHash(sha256(manifest));
        version.setPublishedAt(LocalDateTime.now());
        version.setPublishedBy(displayName());
        version.setDeleted(0);
        versionMapper.insert(version);

        for (ProductWorkSpecItem item : items) {
            item.setSpecVersionId(version.getId());
            itemMapper.insert(item);
        }
        return version;
    }

    /** 某产品的发布版本列表（新→旧） */
    @Transactional(readOnly = true)
    public List<ProductWorkSpecVersion> list(Long productId) {
        return versionMapper.selectByProductId(productId);
    }

    /**
     * 发布版本（SPEC_JSON 快照）与当前工作态(work_spec_json)的字段级差异（dev-20261011-023）。
     * 只读预览，不改发布/绑定逻辑。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> diff(Long versionId) {
        ProductWorkSpecVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("发布版本不存在: " + versionId);
        }
        List<Map<String, Object>> fields = buildDiffFields(version);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("versionId", version.getId());
        result.put("versionNo", version.getVersionNo());
        result.put("productId", version.getProductId());
        result.put("changed", !fields.isEmpty());
        result.put("fields", fields);
        result.put("notIncluded", NOT_INCLUDED);
        return result;
    }

    /**
     * 列表用只读状态：每个产品的最新已发布版本 + 有无「未发布改动」（dev-20261011-027）。
     * hasUnpublishedChanges 复用 diff() 的字段级比较口径；无发布版本时为 false（列表显示「未发布」）。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> statuses(List<Long> productIds) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (productIds == null || productIds.isEmpty()) {
            return out;
        }
        for (Long productId : productIds) {
            if (productId == null) {
                continue;
            }
            ProductWorkSpecVersion latest = versionMapper.selectLatestPublished(productId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", productId);
            if (latest == null) {
                row.put("latestVersionId", null);
                row.put("latestVersionNo", null);
                row.put("latestPublishedAt", null);
                row.put("hasUnpublishedChanges", false);
            } else {
                row.put("latestVersionId", latest.getId());
                row.put("latestVersionNo", latest.getVersionNo());
                row.put("latestPublishedAt", latest.getPublishedAt());
                row.put("hasUnpublishedChanges", !buildDiffFields(latest).isEmpty());
            }
            out.add(row);
        }
        return out;
    }

    /** 快照 vs 当前工作态的字段级差异（diff 与 statuses 共用）。 */
    private List<Map<String, Object>> buildDiffFields(ProductWorkSpecVersion version) {
        String snapshot = null;
        for (ProductWorkSpecItem item : itemMapper.selectByVersionId(version.getId())) {
            if (WorkSpecResourceType.SPEC_JSON.getCode().equals(item.getResourceType())) {
                snapshot = item.getSnapshotJson();
                break;
            }
        }
        JsonNode snap = parseSafe(snapshot);
        JsonNode cur = parseSafe(workSpecMapper.read(version.getProductId()));

        List<Map<String, Object>> fields = new ArrayList<>();
        addDiff(fields, "加工要求", snap.get("engineeringRequirements"), cur.get("engineeringRequirements"));
        addDiff(fields, "刀模位置", snap.get("dieLocation"), cur.get("dieLocation"));
        addDiff(fields, "结构图", snap.get("structureFileId"), cur.get("structureFileId"));
        addDiff(fields, "变更", snap.get("changes"), cur.get("changes"));

        JsonNode se = snap.path("emboss");
        JsonNode ce = cur.path("emboss");
        for (Object[] f : EMBOSS_DIFF_FIELDS) {
            addDiff(fields, "凹凸·" + f[1], se.get((String) f[0]), ce.get((String) f[0]));
        }

        JsonNode spr = snap.path("printRemarks");
        JsonNode cpr = cur.path("printRemarks");
        for (String key : unionKeys(spr, cpr)) {
            addDiff(fields, "印刷整组备注·" + key, spr.get(key), cpr.get(key));
        }

        JsonNode scc = snap.path("colorCheck").path("items");
        JsonNode ccc = cur.path("colorCheck").path("items");
        for (String key : unionKeys(scc, ccc)) {
            addDiff(fields, "分色检查·" + key, scc.path(key).get("result"), ccc.path(key).get("result"));
        }
        return fields;
    }

    /** 发布版本详情（版本 + 条目） */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long versionId) {
        ProductWorkSpecVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("发布版本不存在: " + versionId);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("version", version);
        result.put("items", itemMapper.selectByVersionId(versionId));
        return result;
    }

    // ==================== 内部 ====================

    private void addDiff(List<Map<String, Object>> out, String label, JsonNode before, JsonNode after) {
        String b = nodeText(before);
        String a = nodeText(after);
        if (b.equals(a)) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("before", b);
        row.put("after", a);
        out.add(row);
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private JsonNode parseSafe(String raw) {
        if (raw == null || raw.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    private Set<String> unionKeys(JsonNode a, JsonNode b) {
        Set<String> keys = new TreeSet<>();
        if (a != null && a.isObject()) {
            a.fieldNames().forEachRemaining(keys::add);
        }
        if (b != null && b.isObject()) {
            b.fieldNames().forEachRemaining(keys::add);
        }
        return keys;
    }

    private ProductWorkSpecItem newItem(String type, Long sourceId, Long sourceVersionId, String operationRef,
                                        String snapshotJson, String contentHash, int sortOrder) {
        ProductWorkSpecItem item = new ProductWorkSpecItem();
        item.setResourceType(type);
        item.setSourceId(sourceId);
        item.setSourceVersionId(sourceVersionId);
        item.setOperationRef(operationRef);
        item.setSnapshotJson(snapshotJson);
        item.setContentHash(contentHash);
        item.setSortOrder(sortOrder);
        item.setCreateBy(safeUsername());
        item.setCreateTime(LocalDateTime.now());
        return item;
    }

    /** 从规范正文 JSON 取结结构图附件 ID（可选） */
    private Long structureFileId(String raw) {
        try {
            JsonNode node = json.readTree(raw);
            JsonNode fileId = node.get("structureFileId");
            return fileId != null && fileId.isNumber() ? fileId.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private String safeUsername() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            return null;
        }
    }

    private String displayName() {
        try {
            String realName = SecurityUtils.getRealName();
            return (realName != null && !realName.isBlank()) ? realName : SecurityUtils.getUsername();
        } catch (Exception e) {
            return null;
        }
    }
}
