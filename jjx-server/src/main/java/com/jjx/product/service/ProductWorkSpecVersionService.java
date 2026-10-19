package com.jjx.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.utils.Sha256Utils;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    @org.springframework.beans.factory.annotation.Autowired private ProductWorkSpecService draftService;

    private final ProductWorkSpecMapper workSpecMapper;
    private final ProductMapper productMapper;
    private final SysAttachmentMapper attachmentMapper;
    private final ProductWorkSpecVersionMapper versionMapper;
    private final ProductWorkSpecItemMapper itemMapper;
    private final WorkSpecDocsetSnapshotService docsetSnapshotService;
    /** 附件引用（dev-20261019-017）：图集条目补二级资料类型 documentType。 */
    private final SysAttachmentRefMapper attachmentRefMapper;
    private final ObjectMapper json;

    /** 上传根目录（补算历史附件 sha256 用；dev-20261011-034）。 */
    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;

    /** 凹凸差异字段：内部键 → 中文名（dev-20261011-023）。 */
    private static final Object[][] EMBOSS_DIFF_FIELDS = {
            {"setupHeight", "调机高度"}, {"requiredHeight", "要求高度"},
            {"upperTemperature", "上模温度"}, {"lowerTemperature", "下模温度"},
            {"pressTime", "下压时间"}, {"holdTime", "保持时间"},
    };

    /** 工程图种（与前端 productFileCategories.ENGINEERING_DRAWING_CATEGORIES 对齐；dev-20261011-034）。 */
    private static final List<String> DRAWING_CATEGORIES = List.of(
            "结构图", "外形尺寸图", "面板图", "线路图", "组装图", "包装图",
            "印刷指导图", "确认图", "菲林", "模具", "刀模参数图", "其他工程图", "产品图集");

    /** 样品资料类别（dev-20261011-034）。 */
    private static final List<String> SAMPLE_FILE_CATEGORIES = List.of("样品照片", "客户确认样品");

    /** 发布时「未纳入」的子页签（固定提示；dev-20261011-034：工程图集/样品资料已纳入，去掉「样品」）。 */
    private static final List<String> NOT_INCLUDED = List.of(
            "客供资料", "领料单", "油墨", "实际检测记录");

    /** 发布新版本（产品内串行，避免并发产生重复版本号/混合快照）。 */
    @Transactional(rollbackFor = Exception.class)
    public ProductWorkSpecVersion publish(Long productId, String changeSummary) {
        return publish(productId, changeSummary, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductWorkSpecVersion publish(Long productId, String changeSummary, String revision) {
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
        ObjectNode draft = (ObjectNode) parseSafe(raw);
        boolean managed = draft.path("draftManaged").asBoolean();
        if (managed) {
            if (draft.hasNonNull("finalizedVersionId") && (sha256(raw).equals(revision) || draft.path("finalizedFromRevision").asText().equals(revision))) {
                ProductWorkSpecVersion previous = versionMapper.selectById(draft.path("finalizedVersionId").asLong());
                if (previous == null || !productId.equals(previous.getProductId())) throw new BusinessException("原定版版本不存在");
                return previous;
            }
            if (!sha256(raw).equals(revision)) throw new BusinessException("草稿已变化，请重新加载后定版");
            if (!draft.hasNonNull("preparedById") || draft.path("preparedByName").asText().isBlank()) throw new BusinessException("请先保存编制负责人");
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
        // 结构图 + 工程图集 + 样品资料：逐条引用附件（记 attachmentId + 类别/图种 + 版本 + sha256）
        // dev-20261011-034：由「单张结构图」扩为「多张 DRAWING 条目」，去重后按 id 升序。
        int drawingOrder = 3;
        Set<Long> usedDrawingIds = new HashSet<>();
        Long structureFileId = structureFileId(raw);
        if (!managed && structureFileId != null) {
            SysAttachment structure = attachmentMapper.selectById(structureFileId);
            if (structure != null && usedDrawingIds.add(structure.getId())) {
                items.add(drawingItem(structure, "结构图", drawingOrder++));
            }
        }
        for (SysAttachment att : managed ? List.<SysAttachment>of() : drawingAttachments(productId)) {
            if (usedDrawingIds.add(att.getId())) {
                items.add(drawingItem(att, att.getCategory(), drawingOrder++));
            }
        }
        for (SysAttachment att : managed ? List.<SysAttachment>of() : attachmentsByCategory(productId, SAMPLE_FILE_CATEGORIES)) {
            if (usedDrawingIds.add(att.getId())) {
                items.add(drawingItem(att, att.getCategory(), drawingOrder++));
            }
        }

        // 完整文档快照（dev-20261013-010）：在写库前构建并校验；缺件 / 解析失败 / 哈希不符 / 源并发变化即失败。
        LocalDateTime publishedAt = LocalDateTime.now();
        WorkSpecDocsetSnapshotService.DocsetSnapshot snapshot =
                docsetSnapshotService.build(product, versionNo, displayName(), publishedAt, changeSummary);

        int fileOrder = drawingOrder;
        for (WorkSpecDocsetSnapshotService.SnapshotFile file : snapshot.files()) {
            ObjectNode ref = json.createObjectNode();
            ref.put("attachmentId", file.attachmentId());
            if (file.section() != null) ref.put("section", file.section());
            if (file.category() != null) ref.put("category", file.category());
            if (file.documentType() != null && !file.documentType().isBlank()) ref.put("documentType", file.documentType());
            if (file.fileName() != null) ref.put("fileName", file.fileName());
            if (file.sha256() != null) ref.put("sha256", file.sha256());
            items.add(newItem(WorkSpecResourceType.DOCSET_FILE.getCode(), file.attachmentId(), null,
                    file.section(), ref.toString(), file.sha256(), fileOrder++));
        }
        items.add(newItem(WorkSpecResourceType.DOCSET_JSON.getCode(), null, null, null,
                snapshot.json(), snapshot.manifestHash(), fileOrder));

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
        version.setPublishedAt(publishedAt);
        version.setPublishedBy(displayName());
        if (draft.hasNonNull("basedOnVersionId")) version.setBasedOnVersionId(draft.path("basedOnVersionId").asLong());
        version.setDeleted(0);
        versionMapper.insert(version);

        for (ProductWorkSpecItem item : items) {
            item.setSpecVersionId(version.getId());
            itemMapper.insert(item);
        }
        if (managed) {
            ObjectNode finalized = draft.deepCopy(); finalized.put("finalizedVersionId", version.getId()); finalized.put("finalizedFromRevision", revision);
            workSpecMapper.write(productId, finalized.toString());
            draftService.recordOperation(productId, "定版 " + versionNo, draft, finalized);
        }
        return version;
    }

    /** 某产品的发布版本列表（新→旧） */
    @Transactional(readOnly = true)
    public List<ProductWorkSpecVersion> list(Long productId) {
        return versionMapper.selectByProductId(productId);
    }

    /**
     * 停用版本（PUBLISHED → RETIRED；dev-20261011-033）。
     * 停用后不再被「最新已发布版本」选中 → 新工单/新订单不能再绑该版；
     * 已绑定该版的历史工单/订单不受影响（按持久化 versionId 读取）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductWorkSpecVersion retire(Long versionId) {
        return changeStatus(versionId, "RETIRED", "该版本已停用");
    }

    /** 恢复版本（RETIRED → PUBLISHED；dev-20261011-033）。 */
    @Transactional(rollbackFor = Exception.class)
    public ProductWorkSpecVersion restore(Long versionId) {
        return changeStatus(versionId, "PUBLISHED", "该版本已是启用状态");
    }

    /** 仅改 status；不做物理删除（保留版本与历史绑定）。 */
    private ProductWorkSpecVersion changeStatus(Long versionId, String target, String alreadyMessage) {
        ProductWorkSpecVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("发布版本不存在: " + versionId);
        }
        if (target.equals(version.getStatus())) {
            throw new BusinessException(alreadyMessage);
        }
        version.setStatus(target);
        version.setUpdateBy(displayName());
        versionMapper.updateById(version);
        if (draftService != null) {
            ObjectNode before = json.createObjectNode().put("versionId", versionId).put("versionNo", version.getVersionNo());
            draftService.recordOperation(version.getProductId(), "RETIRED".equals(target) ? "停用版本" : "恢复版本", before, before.deepCopy().put("status", target));
        }
        return versionMapper.selectById(versionId);
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
        boolean hasFullSnapshot = itemMapper.selectByVersionId(versionId).stream()
                .anyMatch(item -> WorkSpecResourceType.DOCSET_JSON.getCode().equals(item.getResourceType()));
        result.put("notIncluded", hasFullSnapshot
                ? NOT_INCLUDED.stream().filter(label -> !"客供资料".equals(label)).toList()
                : NOT_INCLUDED);
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

    /**
     * 任意两个已发布版本的字段级差异（dev-20261011-028）：比较两份 SPEC_JSON 快照。
     * 只读展示，不改发布/绑定。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compare(Long versionIdA, Long versionIdB) {
        ProductWorkSpecVersion a = versionMapper.selectById(versionIdA);
        if (a == null) {
            throw new BusinessException("发布版本不存在: " + versionIdA);
        }
        ProductWorkSpecVersion b = versionMapper.selectById(versionIdB);
        if (b == null) {
            throw new BusinessException("发布版本不存在: " + versionIdB);
        }
        if (!java.util.Objects.equals(a.getProductId(), b.getProductId())) {
            throw new BusinessException("只能比较同一产品的发布版本");
        }
        List<Map<String, Object>> fields = compareSnapshots(
                parseSafe(specSnapshot(a.getId())), parseSafe(specSnapshot(b.getId())));
        addDrawingSetDiff(fields, a, b);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", a.getProductId());
        result.put("changed", !fields.isEmpty());
        result.put("fields", fields);
        result.put("versionA", versionBrief(a));
        result.put("versionB", versionBrief(b));
        result.put("notIncluded", NOT_INCLUDED);
        return result;
    }

    /** 追加「工程图 / 样品资料」集合差异（按 图种|附件|哈希 归一比较；dev-20261011-034，支持多条目）。 */
    private void addDrawingSetDiff(List<Map<String, Object>> fields, ProductWorkSpecVersion a, ProductWorkSpecVersion b) {
        String sa = drawingSignature(a.getId());
        String sb = drawingSignature(b.getId());
        if (sa.equals(sb)) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", "工程图 / 样品资料");
        row.put("before", sa);
        row.put("after", sb);
        fields.add(row);
    }

    /** 某版本的 DRAWING 条目签名（资料类型|附件ID|哈希；优先 documentType，旧快照回退 category/operationRef）。 */
    private String drawingSignature(Long versionId) {
        return itemMapper.selectByVersionId(versionId).stream()
                .filter(i -> WorkSpecResourceType.DRAWING.getCode().equals(i.getResourceType()))
                .map(i -> {
                    String key = i.getOperationRef() == null ? "" : i.getOperationRef();
                    JsonNode snapshot = parseSafe(i.getSnapshotJson());
                    if (snapshot.hasNonNull("documentType") && !snapshot.get("documentType").asText().isBlank()) {
                        key = snapshot.get("documentType").asText();
                    }
                    return key + "|"
                            + (i.getSourceId() == null ? "" : i.getSourceId()) + "|"
                            + (i.getContentHash() == null ? "" : i.getContentHash());
                })
                .sorted()
                .collect(Collectors.joining("\n"));
    }

    /** 附件对应的二级资料类型（ref.document_type；多引用取首条非空）。 */
    private String documentTypeOf(Long attachmentId) {
        if (attachmentId == null) return null;
        for (SysAttachmentRef ref : attachmentRefMapper.selectList(Wrappers.<SysAttachmentRef>lambdaQuery()
                .eq(SysAttachmentRef::getAttachmentId, attachmentId)
                .orderByAsc(SysAttachmentRef::getId))) {
            if (ref.getDocumentType() != null && !ref.getDocumentType().isBlank()) return ref.getDocumentType();
        }
        return null;
    }

    private Map<String, Object> versionBrief(ProductWorkSpecVersion version) {
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("id", version.getId());
        brief.put("versionNo", version.getVersionNo());
        brief.put("publishedAt", version.getPublishedAt());
        brief.put("publishedBy", version.getPublishedBy());
        brief.put("changeSummary", version.getChangeSummary());
        return brief;
    }

    /** 快照 vs 当前工作态的字段级差异（diff 与 statuses 共用）。 */
    private List<Map<String, Object>> buildDiffFields(ProductWorkSpecVersion version) {
        return compareSnapshots(parseSafe(specSnapshot(version.getId())),
                parseSafe(workSpecMapper.read(version.getProductId())));
    }

    /** 取某发布版本的规范正文快照（SPEC_JSON 项）。 */
    private String specSnapshot(Long versionId) {
        for (ProductWorkSpecItem item : itemMapper.selectByVersionId(versionId)) {
            if (WorkSpecResourceType.SPEC_JSON.getCode().equals(item.getResourceType())) {
                return item.getSnapshotJson();
            }
        }
        return null;
    }

    /** 两份规范正文快照的字段级差异（前一份 vs 后一份）。 */
    private List<Map<String, Object>> compareSnapshots(JsonNode snap, JsonNode cur) {
        List<Map<String, Object>> fields = new ArrayList<>();
        addDiff(fields, "工程附件清单", snap.get("draftAttachments"), cur.get("draftAttachments"));
        addDiff(fields, "制定负责人", snap.get("preparedById"), cur.get("preparedById"));
        addDiff(fields, "负责人姓名", snap.get("preparedByName"), cur.get("preparedByName"));
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
        return Sha256Utils.ofText(text);
    }

    /** 工程图集：产品文件库中类别∈工程图种，且现行(is_current)或已下发(released)的图纸附件。 */
    private List<SysAttachment> drawingAttachments(Long productId) {
        return attachmentMapper.selectList(Wrappers.<SysAttachment>lambdaQuery()
                .eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, productId)
                .in(SysAttachment::getCategory, DRAWING_CATEGORIES)
                .and(w -> w.eq(SysAttachment::getIsCurrent, YesNoEnum.YES.getCode())
                        .or().eq(SysAttachment::getReleased, YesNoEnum.YES.getCode()))
                .orderByAsc(SysAttachment::getId));
    }

    /** 产品文件库中指定类别的附件（样品资料等）。 */
    private List<SysAttachment> attachmentsByCategory(Long productId, List<String> categories) {
        return attachmentMapper.selectList(Wrappers.<SysAttachment>lambdaQuery()
                .eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, productId)
                .in(SysAttachment::getCategory, categories)
                .orderByAsc(SysAttachment::getId));
    }

    /** 一条 DRAWING 引用条目：attachmentId + 类别/图种(operationRef) + 版本 + sha256（引用，不复制文件）。 */
    private ProductWorkSpecItem drawingItem(SysAttachment att, String categoryOrRef, int sortOrder) {
        String hash = attachmentSha256(att);
        ObjectNode ref = json.createObjectNode();
        ref.put("attachmentId", att.getId());
        if (att.getFileName() != null) ref.put("fileName", att.getFileName());
        if (att.getCategory() != null) ref.put("category", att.getCategory());
        String documentType = documentTypeOf(att.getId());
        if (documentType != null) ref.put("documentType", documentType);
        if (att.getVersion() != null) ref.put("version", att.getVersion());
        if (att.getFileRole() != null) ref.put("fileRole", att.getFileRole());
        ref.put("sha256", hash);
        return newItem(WorkSpecResourceType.DRAWING.getCode(), att.getId(), null, categoryOrRef,
                ref.toString(), hash, sortOrder);
    }

    /** 附件 sha256：优先取已存值；历史附件为空时按实际文件补算并回写（避免快照哈希悬空）。 */
    private String attachmentSha256(SysAttachment att) {
        if (att.getSha256() != null && !att.getSha256().isBlank()) {
            return att.getSha256();
        }
        if (att.getFilePath() == null || att.getFilePath().isBlank()) {
            return null;
        }
        String hash = Sha256Utils.ofFile(java.nio.file.Path.of(uploadBasePath).resolve(att.getFilePath()));
        if (hash != null) {
            SysAttachment patch = new SysAttachment();
            patch.setId(att.getId());
            patch.setSha256(hash);
            attachmentMapper.updateById(patch);
        }
        return hash;
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
