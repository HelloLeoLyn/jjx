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
import com.jjx.system.service.ProductCustomerDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 发布版本的「完整文档快照」构建器（dev-20261013-010；返修 dev-20261013-010）。
 *
 * <p>在发布事务内、写库之前构建并**校验**整份快照：产品信息、规范正文与确认信息（批准状态按工作态
 * 口径 confirmedSource×来源摘要 计算）、BOM 树（材料图标）、工艺路线（图标/参数）、客供资料
 * （产品库类别 + 客户来源）、样品与全部工程图及其文件元数据与字节身份。任一必需内容缺失 / 解析失败 /
 * 文件缺失 / 哈希不符 / 源并发变化即抛出，由发布事务回滚，不生成半成品版本。</p>
 *
 * <p>快照自包含（带 {@code schemaVersion}），下游（P3）只读本快照即可渲染，不再回查当前工作态。</p>
 */
@Service
@RequiredArgsConstructor
public class WorkSpecDocsetSnapshotService {

    /** 快照格式版本：结构变更时递增，旧消费者按此分支兼容。 */
    public static final int SCHEMA_VERSION = 1;

    private final IEngineeringBomService bomService;
    private final IEngineeringRoutingService routingService;
    private final ProductWorkSpecService workSpecService;
    private final SysAttachmentMapper attachmentMapper;
    private final ProductStandardProcessMapper processMapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProductCustomerDocumentService customerDocuments;

    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;

    /** 受控图标资源目录（打包的 SVG；未配置时按常见相对路径探测）。 */
    @Value("${jjx.icon.assets.dir:}")
    private String iconAssetsDir;

    /** 工程图种（与前端 productFileCategories.ENGINEERING_DRAWING_CATEGORIES 对齐）。 */
    static final List<String> DRAWING_CATEGORIES = List.of(
            "结构图", "外形尺寸图", "面板图", "线路图", "组装图", "包装图",
            "印刷指导图", "确认图", "菲林", "模具", "刀模参数图", "其他工程图", "产品图集");

    /** 样品资料类别。 */
    static final List<String> SAMPLE_FILE_CATEGORIES = List.of("样品照片", "客户确认样品");

    /** 客供资料类别（产品文件库内明确标注客供的）。 */
    static final List<String> CUSTOMER_FILE_CATEGORIES = List.of("客供稿", "承认书", "客供资料");

    private static final Pattern ATTACHMENT_URL_ID =
            Pattern.compile("/(?:attachment|attachments)/(?:download/)?(\\d+)(?:\\?.*)?$");
    private static final Pattern BUNDLED_ICON = Pattern.compile("^[\\p{IsHan}A-Za-z0-9_\\-]+$");

    /** 快照内的一个文件条目（冻结展示元数据 + 字节身份）。 */
    public record SnapshotFile(Long attachmentId, String section, String category, String documentType,
                               String fileName, String displayName,
                               String version, String drawingNo, String fileRole, String mime, Long size,
                               String sha256, String source, String customerDocType, Integer sortOrder) {
        SnapshotFile withSection(String newSection) {
            return new SnapshotFile(attachmentId, newSection, category, documentType, fileName, displayName, version,
                    drawingNo, fileRole, mime, size, sha256, source, customerDocType, sortOrder);
        }
    }

    /** 冻结的图标资源（字节身份 + 受控来源），防止“同名 SVG 内容变化”。 */
    public record IconRef(String value, String kind, String symbolId, Long attachmentId, String sha256, String asset) {
    }

    /** 构建结果：完整快照 JSON、清单哈希、文件保留索引与图标索引。 */
    public record DocsetSnapshot(String json, String manifestHash, List<SnapshotFile> files,
                                 List<IconRef> icons, int bomItemCount, int routingItemCount) {
    }

    /**
     * 构建并校验整份快照。要求调用方已持有产品行锁（{@code SELECT ... FOR UPDATE}）。
     * 构建首尾对 BOM / 路线 / 产品附件 / 图标工序行加 {@code FOR SHARE} 锁读并比对源令牌，
     * 覆盖工作态正文、BOM、路线、客供资料与文件集合的并发变化。
     */
    public DocsetSnapshot build(Product product, String versionNo, String publishedBy,
                                LocalDateTime publishedAt, String changeSummary) {
        Long productId = product.getProductId();

        // 锁读来源并冻结读取窗口（FOR SHARE 读最新已提交，且阻塞窗口内修改）。
        String tokenBefore = sourceToken(product);

        // 规范正文：复用工作态读取口径（approved 由 confirmedSource×来源摘要计算，非原始字段）。
        ObjectNode spec = workSpecService.get(productId);
        workSpecService.freezeProjectMaterials(spec);

        // 文件集合（结构图/产品库工程图/样品/产品库客供/客户来源客供），按附件 ID 去重。
        List<SnapshotFile> files = new ArrayList<>(collectFiles(product, spec));
        Set<Long> fileIds = new TreeSet<>();
        for (SnapshotFile file : files) fileIds.add(file.attachmentId());

        EngineeringBomVO bomVo = null;
        if (product.getCurrentBomId() != null) {
            bomVo = bomService.getBomDetail(product.getCurrentBomId());
            if (bomVo == null) throw new BusinessException("发布失败：BOM 不存在（id=" + product.getCurrentBomId() + "）");
        }
        EngineeringRoutingVO routingVo = null;
        if (product.getCurrentRouteId() != null) {
            routingVo = routingService.getRoutingItems(product.getCurrentRouteId());
            if (routingVo == null) throw new BusinessException("发布失败：工艺路线不存在（id=" + product.getCurrentRouteId() + "）");
        }

        JsonNode bomNode = bomVo == null ? null : json.valueToTree(bomVo);
        JsonNode routingNode = routingVo == null ? null : json.valueToTree(routingVo);

        // 图标：解析为不可变资源（打包 SVG 字节归档 / 上传附件），回写 icon + iconSha256。
        Set<Long> processIds = new TreeSet<>();
        collectProcessIds(spec.path("projectMaterials"), processIds);
        collectProcessIds(bomNode, processIds);
        collectProcessIds(routingNode, processIds);
        Map<Long, String> iconNames = processIconNames(processIds);
        Map<String, IconRef> icons = new LinkedHashMap<>();
        enrichIcons(spec.path("projectMaterials"), iconNames, icons);
        enrichIcons(bomNode, iconNames, icons);
        enrichIcons(routingNode, iconNames, icons);
        for (IconRef ref : icons.values()) {
            if ("uploaded".equals(ref.kind()) && ref.attachmentId() != null && fileIds.add(ref.attachmentId())) {
                SysAttachment att = attachmentMapper.selectById(ref.attachmentId());
                if (att != null) files.add(buildFile(att, "icon", false, null).withSection("icon"));
            }
        }

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
        putText(confirmation, "sourceRevision", text(spec, "sourceRevision"));

        ObjectNode publication = content.putObject("publication");
        putText(publication, "versionNo", versionNo);
        putText(publication, "publishedBy", publishedBy);
        if (publishedAt != null) publication.put("publishedAt", publishedAt.toString());
        putText(publication, "changeSummary", changeSummary);

        content.set("bom", bomNode == null ? json.nullNode() : bomNode);
        content.set("routing", routingNode == null ? json.nullNode() : routingNode);

        ObjectNode iconsNode = content.putObject("icons");
        for (IconRef ref : icons.values()) iconsNode.set(ref.value(), json.valueToTree(ref));

        ArrayNode fileArray = content.putArray("files");
        for (SnapshotFile file : files) {
            ObjectNode frozen = json.valueToTree(file);
            if (spec.path("draftManaged").asBoolean()) for (JsonNode entry : spec.path("draftAttachments")) {
                if (entry.path("attachmentId").asLong() == file.attachmentId()) frozen.put("printSelected", entry.path("print").asBoolean());
            }
            fileArray.add(frozen);
        }

        int bomItemCount = countNodes(bomNode);
        int routingItemCount = countNodes(routingNode);
        ObjectNode counts = content.putObject("counts");
        counts.put("files", files.size());
        counts.put("icons", icons.size());
        counts.put("bomItems", bomItemCount);
        counts.put("routingItems", routingItemCount);

        // 清单哈希：覆盖除 manifestHash 外的全部内容。
        String manifestHash = sha256(canon(content));
        content.put("manifestHash", manifestHash);

        // 源一致性复校验：BOM/路线/附件/图标在读取期间若发生变化则发布失败。
        if (!tokenBefore.equals(sourceToken(product))) {
            throw new BusinessException("发布失败：发布来源在读取期间发生变化，请重试");
        }

        return new DocsetSnapshot(content.toString(), manifestHash, files, new ArrayList<>(icons.values()),
                bomItemCount, routingItemCount);
    }

    // ==================== 文件采集与校验 ====================

    /** 采集：选定结构图 + 产品库工程图（现行/已下发） + 样品 + 产品库客供类别 + 客户来源客供；按附件 ID 去重。 */
    private List<SnapshotFile> collectFiles(Product product, ObjectNode spec) {
        Long productId = product.getProductId();
        LinkedHashMap<Long, SnapshotFile> chosen = new LinkedHashMap<>();
        // 附件 → 二级资料类型（dev-20261019-017）：图集从图种 category 收敛到 documentType，category 继续保留兼容
        Map<Long, String> documentTypes = documentTypesOf(productId);

        if (spec.path("draftManaged").asBoolean()) {
            for (JsonNode entry : spec.path("draftAttachments")) {
                long attachmentId = entry.path("attachmentId").asLong();
                SysAttachment att = attachmentMapper.selectActiveForUpdate(attachmentId);
                String label = entry.path("displayName").asText("");
                if (label.isBlank()) label = att == null ? "附件" : att.getFileName();
                label = label + "（ID=" + attachmentId + "）";
                if (att == null) throw new BusinessException("定版失败：" + label + "文件已删除或不存在");
                // 新规范附件来源为 work_spec；历史 product 引用仍可随草稿定版。
                String refScope = " FROM sys_attachment_ref WHERE source_type IN ('product','work_spec') AND source_id=? AND attachment_id=? AND deleted=0 AND ref_state<>'draft'";
                Long bound = jdbc.queryForObject("SELECT COUNT(*)" + refScope + " AND document_type=?",
                    Long.class, productId, attachmentId, entry.path("documentType").asText());
                if (bound == null || bound == 0) {
                    Long references = jdbc.queryForObject("SELECT COUNT(*)" + refScope, Long.class, productId, attachmentId);
                    String reason = references != null && references > 0 ? "资料类型已变化" : "已从该产品的规范附件中移除";
                    throw new BusinessException("定版失败：" + label + reason + "，请重新加载并保存草稿");
                }
                SnapshotFile file = buildFile(att, "work-spec-draft", false, entry.path("documentType").asText());
                if (!entry.path("sha256").asText().isBlank() && !entry.path("sha256").asText().equalsIgnoreCase(file.sha256())) throw new BusinessException("定版失败：" + label + "文件内容已变化，请重新保存草稿");
                chosen.put(attachmentId, new SnapshotFile(file.attachmentId(), "PRINT_GUIDE".equals(file.documentType()) ? "print" : "atlas",
                    file.category(), file.documentType(), file.fileName(), entry.path("displayName").asText(file.fileName()),
                    file.version(), file.drawingNo(), file.fileRole(), file.mime(), file.size(), file.sha256(), file.source(), file.customerDocType(), entry.path("sortOrder").asInt()));
            }
        }

        if (!spec.path("draftManaged").asBoolean()) {
        // 1) 规范正文选定的结构图：显式纳入（不受现行/已下发过滤，避免旧版有、新版无）。
        Long structureFileId = spec.path("structureFileId").isNumber() ? spec.path("structureFileId").asLong() : null;
        if (structureFileId != null) {
            SysAttachment structure = attachmentMapper.selectById(structureFileId);
            if (structure == null) {
                throw new BusinessException("发布失败：选定结构图不存在（id=" + structureFileId + "）");
            }
            add(chosen, buildFile(structure, "product-library", false, documentTypes.get(structure.getId())));
        }

        // 2) 产品库工程图（现行或已下发）。
        for (SysAttachment att : attachmentMapper.selectList(Wrappers.<SysAttachment>lambdaQuery()
                .eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, productId)
                .in(SysAttachment::getCategory, DRAWING_CATEGORIES)
                .and(w -> w.eq(SysAttachment::getIsCurrent, YesNoEnum.YES.getCode())
                        .or().eq(SysAttachment::getReleased, YesNoEnum.YES.getCode()))
                .orderByAsc(SysAttachment::getId))) {
            add(chosen, buildFile(att, "product-library", false, documentTypes.get(att.getId())));
        }

        }
        // 3) 样品资料。
        for (SysAttachment att : attachmentMapper.selectList(Wrappers.<SysAttachment>lambdaQuery()
                .eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, productId)
                .in(SysAttachment::getCategory, SAMPLE_FILE_CATEGORIES)
                .orderByAsc(SysAttachment::getId))) {
            add(chosen, buildFile(att, "product-library", false, documentTypes.get(att.getId())));
        }

        // 4) 产品库客供类别（客供稿/承认书/客供资料）——不再仅依赖 customer_product_code。
        for (SysAttachment att : attachmentMapper.selectList(Wrappers.<SysAttachment>lambdaQuery()
                .eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, productId)
                .in(SysAttachment::getCategory, CUSTOMER_FILE_CATEGORIES)
                .orderByAsc(SysAttachment::getId))) {
            add(chosen, buildFile(att, "product-library", true, documentTypes.get(att.getId())));
        }

        // 5) 客户来源客供资料：与页面共用有效引用查询，并冻结引用上的展示名/资料类型。
        for (Map<String, Object> row : customerDocuments.list(productId)) {
            Long attachmentId = ((Number) row.get("id")).longValue();
            SysAttachment att = attachmentMapper.selectById(attachmentId);
            if (att == null) throw new BusinessException("发布失败：客供附件不存在（id=" + attachmentId + "）");
            String rowDocType = row.get("customer_doc_type") == null ? null : String.valueOf(row.get("customer_doc_type"));
            if (rowDocType != null && rowDocType.isBlank()) rowDocType = null;
            SnapshotFile file = buildFile(att, "customer-source", true,
                    rowDocType != null ? rowDocType : documentTypes.get(att.getId()));
            add(chosen, new SnapshotFile(file.attachmentId(), file.section(), (String) row.get("category"),
                    file.documentType(), file.fileName(), (String) row.get("display_name"), file.version(), file.drawingNo(),
                    file.fileRole(), file.mime(), file.size(), file.sha256(), file.source(),
                    (String) row.get("customer_doc_type"), row.get("sort_order") instanceof Number n ? n.intValue() : null));
        }
        return new ArrayList<>(chosen.values());
    }

    private void add(Map<Long, SnapshotFile> chosen, SnapshotFile file) {
        if (file == null) return;
        chosen.putIfAbsent(file.attachmentId(), file);
    }

    /** 组装单条文件：校验字节存在 + 哈希一致，返回冻结元数据（含二级资料类型 documentType）。 */
    private SnapshotFile buildFile(SysAttachment att, String source, boolean customerSource, String documentType) {
        String hash = validateAndHash(att);
        String category = att.getCategory();
        String section = sectionOf(customerSource, category);
        return new SnapshotFile(att.getId(), section, category, documentType, att.getFileName(), att.getDisplayName(),
                att.getVersion(), att.getDrawingNo(), att.getFileRole(), att.getFileType(), att.getFileSize(),
                hash, source, att.getCustomerDocType(), att.getSortOrder());
    }

    /** 产品范围内附件 → 二级资料类型（ref 上的 document_type；同一附件取首条非空）。 */
    private Map<Long, String> documentTypesOf(Long productId) {
        Map<Long, String> map = new HashMap<>();
        jdbc.query("SELECT attachment_id,document_type FROM sys_attachment_ref "
                        + "WHERE deleted=0 AND document_type IS NOT NULL AND document_type<>'' "
                        + "AND ((source_type='product' AND source_id=?) OR product_id=?) ORDER BY id",
                (rs, rowNum) -> {
                    map.putIfAbsent(rs.getLong("attachment_id"), rs.getString("document_type"));
                    return null;
                }, productId, productId);
        return map;
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

    private void collectProcessIds(JsonNode node, Set<Long> out) {
        if (node == null) return;
        if (node.isObject()) {
            JsonNode pid = node.get("processId");
            if (pid != null && pid.isNumber()) out.add(pid.asLong());
            node.forEach(child -> collectProcessIds(child, out));
        } else if (node.isArray()) {
            node.forEach(child -> collectProcessIds(child, out));
        }
    }

    private Map<Long, String> processIconNames(Set<Long> processIds) {
        Map<Long, String> names = new HashMap<>();
        for (Long id : processIds) {
            ProductStandardProcess process = processMapper.selectById(id);
            if (process != null && process.getIcon() != null && !process.getIcon().isBlank()) {
                names.put(id, process.getIcon());
            }
        }
        return names;
    }

    /** 为含 processId 的节点回写 icon（原始值）与 iconSha256（图标字节身份），并登记 IconRef。 */
    private void enrichIcons(JsonNode node, Map<Long, String> iconNames, Map<String, IconRef> icons) {
        if (node == null) return;
        if (node.isObject()) {
            JsonNode pid = node.get("processId");
            if (pid != null && pid.isNumber()) {
                String value = iconNames.get(pid.asLong());
                if (value != null && node instanceof ObjectNode obj) {
                    IconRef ref = icons.computeIfAbsent(value, this::resolveIcon);
                    obj.put("icon", value);
                    obj.put("iconSha256", ref.sha256());
                }
            }
            node.forEach(child -> enrichIcons(child, iconNames, icons));
        } else if (node.isArray()) {
            node.forEach(child -> enrichIcons(child, iconNames, icons));
        }
    }

    /** 将图标值解析为不可变资源；无法解析为受控字节来源时失败（不冻结可变名称/外部URL）。 */
    private IconRef resolveIcon(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("发布失败：图标引用为空");
        }
        boolean external = value.startsWith("http://") || value.startsWith("https://")
                || value.startsWith("/") || value.startsWith("./");
        if (external) {
            Matcher matcher = ATTACHMENT_URL_ID.matcher(value);
            if (!matcher.find()) {
                throw new BusinessException("发布失败：图标为外部URL，不能作为不可变依据：" + value);
            }
            long attachmentId = Long.parseLong(matcher.group(1));
            SysAttachment att = attachmentMapper.selectById(attachmentId);
            if (att == null) {
                throw new BusinessException("发布失败：图标附件不存在（id=" + attachmentId + "）");
            }
            String hash = validateAndHash(att);
            return new IconRef(value, "uploaded", null, attachmentId, hash, null);
        }
        if (!BUNDLED_ICON.matcher(value).matches()) {
            throw new BusinessException("发布失败：无法识别的图标引用：" + value);
        }
        Path asset = resolveBundledAsset(value);
        if (asset == null) {
            throw new BusinessException("发布失败：打包图标资源缺失（" + value + ".svg），请配置 jjx.icon.assets.dir");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(asset);
        } catch (IOException e) {
            throw new BusinessException("发布失败：图标资源不可读（" + value + "）");
        }
        String hash = Sha256Utils.ofText(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        String relative = archiveIcon(bytes, hash);
        return new IconRef(value, "bundled", "icon-" + value, null, hash, relative);
    }

    /** 打包 SVG 归档到受控存储（内容寻址，不可变），返回相对路径。 */
    private String archiveIcon(byte[] bytes, String hash) {
        String relative = "icon-archive/" + hash + ".svg";
        Path root = Paths.get(uploadBasePath).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new BusinessException("发布失败：图标归档路径非法");
        if (!Files.isRegularFile(target)) {
            try {
                Files.createDirectories(target.getParent());
                Path tmp = Files.createTempFile(target.getParent(), hash, ".tmp");
                Files.write(tmp, bytes);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new BusinessException("发布失败：图标资源归档失败（" + hash + "）");
            }
        }
        return relative;
    }

    private Path resolveBundledAsset(String name) {
        List<Path> candidates = new ArrayList<>();
        if (iconAssetsDir != null && !iconAssetsDir.isBlank()) candidates.add(Paths.get(iconAssetsDir));
        candidates.add(Paths.get("jjx-web/src/icons/jjx"));
        candidates.add(Paths.get("../jjx-web/src/icons/jjx"));
        candidates.add(Paths.get("src/icons/jjx"));
        for (Path root : candidates) {
            Path path = root.resolve(name + ".svg");
            if (Files.isRegularFile(path)) return path;
        }
        return null;
    }

    // ==================== 源一致性（锁读 + 令牌） ====================

    /**
     * 对发布来源行加 {@code FOR SHARE} 锁读并生成令牌。
     * 锁读读取**最新已提交**数据（不受事务一致性快照掩盖），并在事务提交前阻塞对这些行的修改，
     * 因此首尾令牌一致即证明读取窗口内来源未被并发修改。
     */
    private String sourceToken(Product product) {
        StringBuilder token = new StringBuilder();
        if (product.getCurrentBomId() != null) {
            appendRows(token, jdbc.queryForList(
                    "SELECT bom_id,bom_code,bom_version,approve_status,update_time FROM engineering_bom WHERE bom_id=? FOR SHARE",
                    product.getCurrentBomId()));
            appendRows(token, jdbc.queryForList(
                    "SELECT item_id,material_id,process_id,split,quantity,unit,layer,item_order,remark,update_time FROM engineering_bom_item WHERE bom_id=? FOR SHARE",
                    product.getCurrentBomId()));
        }
        if (product.getCurrentRouteId() != null) {
            appendRows(token, jdbc.queryForList(
                    "SELECT routing_id,routing_version,approve_status,update_time FROM engineering_routing WHERE routing_id=? FOR SHARE",
                    product.getCurrentRouteId()));
            appendRows(token, jdbc.queryForList(
                    "SELECT detail_id,process_id,parent_id,workflow_seq,process_order,custom_labor_hours,custom_process_params,update_time FROM engineering_routing_item WHERE routing_id=? FOR SHARE",
                    product.getCurrentRouteId()));
        }
        appendRows(token, jdbc.queryForList(
                "SELECT id,sha256,category,file_role,is_current,released,file_name,version,drawing_no,file_path,biz_id,customer_product_code,update_time "
                        + "FROM sys_attachment WHERE deleted=0 AND biz_type='product' AND biz_id=? FOR SHARE",
                product.getProductId()));
        appendRows(token, customerDocuments.lockForSnapshot(product.getProductId()));
        appendRows(token, jdbc.queryForList("SELECT process_id,icon,update_time FROM engineering_standard_process FOR SHARE"));
        appendRows(token, jdbc.queryForList("SELECT r.id,r.source_type,r.ref_state,r.attachment_id,r.document_type,r.sort_order,r.display_name,r.remark,a.sha256,a.file_path,a.deleted "
            + "FROM sys_attachment_ref r JOIN sys_attachment a ON a.id=r.attachment_id WHERE r.source_type IN ('product','work_spec') AND r.source_id=? AND r.deleted=0 ORDER BY r.id FOR SHARE", product.getProductId()));
        return sha256(token.toString());
    }

    private void appendRows(StringBuilder token, List<Map<String, Object>> rows) {
        token.append(canon(rows)).append('\n');
    }

    // ==================== 工具 ====================

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
