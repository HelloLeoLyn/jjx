package com.jjx.product.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.utils.Sha256Utils;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.domain.vo.EngineeringBomVO;
import com.jjx.product.domain.vo.EngineeringRoutingVO;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布版本的「完整文档快照」构建器（dev-20261013-010）。
 *
 * <p>在发布事务内、写库之前构建并**校验**整份快照：产品信息、规范正文、确认信息、
 * BOM 树（材料图标）、工艺路线（图标/参数）、客供资料、样品与全部工程图及其文件身份。
 * 任一必需内容缺失 / 解析失败 / 文件缺失 / 哈希不符即抛出，由发布事务回滚，不生成半成品版本。</p>
 *
 * <p>快照自包含（带 {@code schemaVersion}），下游（P3）只读本快照即可渲染，不再回查当前工作态。</p>
 */
@Service
@RequiredArgsConstructor
public class WorkSpecDocsetSnapshotService {

    /** 快照格式版本：结构变更时递增，旧消费者按此分支兼容（契约见 task-docs / sys_task 说明）。 */
    public static final int SCHEMA_VERSION = 1;

    private final IEngineeringBomService bomService;
    private final IEngineeringRoutingService routingService;
    private final SysAttachmentMapper attachmentMapper;
    private final ProductStandardProcessMapper processMapper;
    private final ObjectMapper json;

    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;

    /** 工程图种（与前端 productFileCategories.ENGINEERING_DRAWING_CATEGORIES 对齐）。 */
    static final List<String> DRAWING_CATEGORIES = List.of(
            "结构图", "外形尺寸图", "面板图", "线路图", "组装图", "包装图",
            "印刷指导图", "确认图", "菲林", "模具", "刀模参数图", "其他工程图", "产品图集");

    /** 样品资料类别。 */
    static final List<String> SAMPLE_FILE_CATEGORIES = List.of("样品照片", "客户确认样品");

    /** 客供资料类别（产品文件库内明确标注客供的）。 */
    static final List<String> CUSTOMER_FILE_CATEGORIES = List.of("客供稿", "承认书", "客供资料");

    /** 快照内的一个文件条目（冻结展示元数据 + 字节身份）。 */
    public record SnapshotFile(Long attachmentId, String section, String category, String fileName, String displayName,
                               String version, String drawingNo, String fileRole, String mime, Long size,
                               String sha256, String source, String customerDocType, Integer sortOrder) {
    }

    /** 构建结果：完整快照 JSON、清单哈希与文件保留索引（供写 DOCSET_FILE 条目）。 */
    public record DocsetSnapshot(String json, String manifestHash, List<SnapshotFile> files,
                                 int bomItemCount, int routingItemCount) {
    }

    /**
     * 构建并校验整份快照。要求调用方已持有产品行锁（{@code SELECT ... FOR UPDATE}），
     * 从而保证 work_spec / currentBomId / currentRouteId 在构建期间不被并发修改。
     */
    public DocsetSnapshot build(Product product, String workSpecRaw, String versionNo, String publishedBy,
                                LocalDateTime publishedAt, String changeSummary) {
        Long productId = product.getProductId();

        JsonNode spec = parseSpec(workSpecRaw);

        // ---- BOM / 路线：双读一致性（读取期间源数据变化则发布失败）----
        EngineeringBomVO bomVo = null;
        String bomCanon = null;
        if (product.getCurrentBomId() != null) {
            bomVo = bomService.getBomDetail(product.getCurrentBomId());
            if (bomVo == null) throw new BusinessException("发布失败：BOM 不存在（id=" + product.getCurrentBomId() + "）");
            bomCanon = canon(bomVo);
        }
        EngineeringRoutingVO routingVo = null;
        String routingCanon = null;
        if (product.getCurrentRouteId() != null) {
            routingVo = routingService.getRoutingItems(product.getCurrentRouteId());
            if (routingVo == null) throw new BusinessException("发布失败：工艺路线不存在（id=" + product.getCurrentRouteId() + "）");
            routingCanon = canon(routingVo);
        }

        Map<Long, String> iconCache = new HashMap<>();
        JsonNode bomNode = bomVo == null ? null : json.valueToTree(bomVo);
        JsonNode routingNode = routingVo == null ? null : json.valueToTree(routingVo);
        enrichIcons(bomNode, iconCache);
        enrichIcons(routingNode, iconCache);

        List<SnapshotFile> files = collectFiles(product);

        ObjectNode content = json.createObjectNode();
        content.put("schemaVersion", SCHEMA_VERSION);
        content.put("productId", productId);
        content.put("productCode", product.getProductCode());
        content.put("productName", product.getProductName());
        content.put("customerId", product.getCustomerId());
        content.put("customerName", product.getCustomerName());
        content.put("productType", product.getProductType());

        ObjectNode productNode = content.putObject("product");
        productNode.put("productId", productId);
        putText(productNode, "productCode", product.getProductCode());
        putText(productNode, "productName", product.getProductName());
        if (product.getCustomerId() != null) productNode.put("customerId", product.getCustomerId());
        putText(productNode, "customerName", product.getCustomerName());
        putText(productNode, "productType", product.getProductType());
        if (product.getCategoryId() != null) productNode.put("categoryId", product.getCategoryId());
        putText(productNode, "unit", product.getUnit());
        putText(productNode, "specJson", product.getSpecJson());
        putText(productNode, "currentBomVersion", product.getCurrentBomVersion());
        putText(productNode, "currentRoutingVersion", product.getCurrentRoutingVersion());

        content.set("spec", spec);

        ObjectNode confirmation = content.putObject("confirmation");
        confirmation.put("approved", spec.path("approved").asBoolean(false));
        putText(confirmation, "confirmedBy", text(spec, "confirmedBy"));
        putText(confirmation, "confirmedAt", text(spec, "confirmedAt"));
        putText(confirmation, "issueUnit", text(spec, "issueUnit"));
        putText(confirmation, "issueDate", text(spec, "issueDate"));

        ObjectNode publication = content.putObject("publication");
        putText(publication, "versionNo", versionNo);
        putText(publication, "publishedBy", publishedBy);
        if (publishedAt != null) publication.put("publishedAt", publishedAt.toString());
        putText(publication, "changeSummary", changeSummary);

        content.set("bom", bomNode == null ? json.nullNode() : bomNode);
        content.set("routing", routingNode == null ? json.nullNode() : routingNode);

        ArrayNode fileArray = content.putArray("files");
        for (SnapshotFile file : files) fileArray.add(json.valueToTree(file));

        int bomItemCount = countNodes(bomNode);
        int routingItemCount = countNodes(routingNode);
        ObjectNode counts = content.putObject("counts");
        counts.put("files", files.size());
        counts.put("bomItems", bomItemCount);
        counts.put("routingItems", routingItemCount);

        // 清单哈希：覆盖除 manifestHash 外的全部内容。
        String manifestHash = sha256(canon(content));
        content.put("manifestHash", manifestHash);

        // ---- 源一致性复校验：BOM/路线在读取期间若发生变化则发布失败 ----
        if (product.getCurrentBomId() != null
                && !bomCanon.equals(canon(bomService.getBomDetail(product.getCurrentBomId())))) {
            throw new BusinessException("发布失败：BOM 在读取期间发生变化，请重试");
        }
        if (product.getCurrentRouteId() != null
                && !routingCanon.equals(canon(routingService.getRoutingItems(product.getCurrentRouteId())))) {
            throw new BusinessException("发布失败：工艺路线在读取期间发生变化，请重试");
        }

        return new DocsetSnapshot(content.toString(), manifestHash, files, bomItemCount, routingItemCount);
    }

    // ==================== 文件采集与校验 ====================

    /** 采集并校验：结构图 + 产品文件库工程图（现行/已下发） + 样品资料 + 客供资料；按附件 ID 去重。 */
    private List<SnapshotFile> collectFiles(Product product) {
        Long productId = product.getProductId();
        LinkedHashMap<Long, SnapshotFile> chosen = new LinkedHashMap<>();

        List<SysAttachment> drawings = attachmentMapper.selectList(Wrappers.<SysAttachment>lambdaQuery()
                .eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, productId)
                .in(SysAttachment::getCategory, DRAWING_CATEGORIES)
                .and(w -> w.eq(SysAttachment::getIsCurrent, YesNoEnum.YES.getCode())
                        .or().eq(SysAttachment::getReleased, YesNoEnum.YES.getCode()))
                .orderByAsc(SysAttachment::getId));
        for (SysAttachment att : drawings) add(chosen, buildFile(att, "product-library", false));

        List<SysAttachment> samples = attachmentMapper.selectList(Wrappers.<SysAttachment>lambdaQuery()
                .eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, productId)
                .in(SysAttachment::getCategory, SAMPLE_FILE_CATEGORIES)
                .orderByAsc(SysAttachment::getId));
        for (SysAttachment att : samples) add(chosen, buildFile(att, "product-library", false));

        if (product.getProductCode() != null && !product.getProductCode().isBlank()) {
            List<SysAttachment> customerDocs = attachmentMapper.selectList(Wrappers.<SysAttachment>lambdaQuery()
                    .eq(SysAttachment::getCustomerProductCode, product.getProductCode())
                    .orderByAsc(SysAttachment::getId));
            for (SysAttachment att : customerDocs) add(chosen, buildFile(att, "customer-source", false));
        }
        return new ArrayList<>(chosen.values());
    }

    private void add(Map<Long, SnapshotFile> chosen, SnapshotFile file) {
        if (file == null) return;
        chosen.putIfAbsent(file.attachmentId(), file);
    }

    /** 组装单条文件：校验字节存在 + 哈希一致，返回冻结元数据。 */
    private SnapshotFile buildFile(SysAttachment att, String source, boolean customerSource) {
        String hash = validateAndHash(att);
        String category = att.getCategory();
        String section = sectionOf(customerSource, category);
        return new SnapshotFile(att.getId(), section, category, att.getFileName(), att.getDisplayName(),
                att.getVersion(), att.getDrawingNo(), att.getFileRole(), att.getFileType(), att.getFileSize(),
                hash, source, att.getCustomerDocType(), att.getSortOrder());
    }

    private String sectionOf(boolean customerSource, String category) {
        String cat = category == null ? "" : category;
        if (customerSource || CUSTOMER_FILE_CATEGORIES.contains(cat)) return "customer";
        if (SAMPLE_FILE_CATEGORIES.contains(cat)) return "sample";
        if ("印刷指导图".equals(cat)) return "print";
        if ("分色检查表".equals(cat)) return "color";
        return "atlas";
    }

    /** 校验附件可读性并返回实际字节 SHA-256；文件缺失或与库中记录不符即失败。 */
    private String validateAndHash(SysAttachment att) {
        if (att.getFilePath() == null || att.getFilePath().isBlank()) {
            throw new BusinessException("发布失败：附件缺少存储路径（id=" + att.getId() + "）");
        }
        Path root = Paths.get(uploadBasePath).toAbsolutePath().normalize();
        Path path = root.resolve(att.getFilePath()).normalize();
        String actual = Sha256Utils.ofFile(path);
        if (actual == null) {
            throw new BusinessException("发布失败：附件文件缺失或不可读（id=" + att.getId() + "）");
        }
        if (att.getSha256() != null && !att.getSha256().isBlank() && !att.getSha256().equalsIgnoreCase(actual)) {
            throw new BusinessException("发布失败：附件哈希不符（id=" + att.getId() + "）");
        }
        return actual;
    }

    // ==================== 图标冻结 ====================

    /** 递归为含 processId 的节点补写发布时的图标名（冻结；字典后续变化不影响旧版）。 */
    private void enrichIcons(JsonNode node, Map<Long, String> cache) {
        if (node == null) return;
        if (node.isObject()) {
            JsonNode pid = node.get("processId");
            if (pid != null && pid.isNumber()) {
                long id = pid.asLong();
                if (!cache.containsKey(id)) {
                    ProductStandardProcess process = processMapper.selectById(id);
                    cache.put(id, process == null ? null : process.getIcon());
                }
                String icon = cache.get(id);
                if (icon != null && node instanceof ObjectNode obj) obj.put("icon", icon);
            }
            node.forEach(child -> enrichIcons(child, cache));
        } else if (node.isArray()) {
            node.forEach(child -> enrichIcons(child, cache));
        }
    }

    // ==================== 工具 ====================

    /** 解析规范正文；非法 JSON 直接失败（不生成半成品）。 */
    private JsonNode parseSpec(String raw) {
        if (raw == null || raw.isBlank()) return json.createObjectNode();
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            throw new BusinessException("发布失败：规范正文解析失败");
        }
    }

    private String canon(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException("发布失败：无法序列化发布来源");
        }
    }

    /** 统计含 itemId 的对象数（材料/工序条目）。 */
    private int countNodes(JsonNode node) {
        if (node == null) return 0;
        int count = 0;
        if (node.isObject()) {
            if (node.hasNonNull("itemId")) count++;
            for (JsonNode child : node) count += countNodes(child);
        } else if (node.isArray()) {
            for (JsonNode child : node) count += countNodes(child);
        }
        return count;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static void putText(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field);
        else node.put(field, value);
    }

    private static String sha256(String text) {
        String hash = Sha256Utils.ofText(text);
        if (hash == null) throw new BusinessException("发布失败：无法计算快照哈希");
        return hash;
    }
}
