package com.jjx.engineering.archive;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.product.enums.ProductEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EngineeringArchiveImportService {
    private static final String SOURCE = "history_archive";
    private static final String VERSION = "V1.0";
    private static final String BOM_TYPE = "engineering";
    private static final String ROUTING_TYPE = "history_archive";

    private final EngineeringArchiveImportMapper archiveMapper;
    private final ProcessIconSampleMapper iconSampleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SysAttachmentMapper attachmentMapper;

    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;
    @Value("${engineering.archive.ocr-url:http://127.0.0.1:8866}")
    private String ocrUrl;

    public Page<EngineeringArchiveImport> page(long pageNum, long pageSize) {
        Page<EngineeringArchiveImport> result = archiveMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<EngineeringArchiveImport>()
                        .orderByDesc(EngineeringArchiveImport::getCreateTime));
        for (EngineeringArchiveImport row : result.getRecords()) {
            row.setOverwriteAllowed(canOverwrite(row));
        }
        return result;
    }

    private boolean canOverwrite(EngineeringArchiveImport archive) {
        if (archive.getRecognizeStatus() == null || ArchiveRecognitionStatus.GENERATED.getValue() != archive.getRecognizeStatus()) return false;
        return !hasApprovedProduct(archive) && !hasApprovedBom(archive) && !hasApprovedRouting(archive);
    }

    public EngineeringArchiveImport get(Long id) {
        return archiveMapper.selectById(id);
    }

    public Path resolvePreviewImage(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) throw new BusinessException("图片路径不能为空");
        Path base = Path.of(uploadBasePath).toAbsolutePath().normalize();
        Path relative = Path.of(relativePath).normalize();
        if (relative.isAbsolute() || relative.startsWith("..") || !relative.startsWith("engineering-archive")) {
            throw new BusinessException("图片路径不合法");
        }
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base) || !Files.isRegularFile(target)) throw new BusinessException("图片不存在");
        return target;
    }

    public Map<String, Object> ocrHealth() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(ocrUrl + "/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return Map.of("available", response.statusCode() == 200, "statusCode", response.statusCode());
        } catch (Exception e) {
            return Map.of("available", false, "message", "本地 OCR 服务未启动");
        }
    }

    public List<ProcessIconSample> samples(Long archiveId) {
        List<ProcessIconSample> samples = iconSampleMapper.selectList(new LambdaQueryWrapper<ProcessIconSample>()
                .eq(archiveId != null, ProcessIconSample::getArchiveId, archiveId)
                .orderByDesc(ProcessIconSample::getCreateTime));
        for (ProcessIconSample sample : samples) {
            try {
                byte[] image = Files.readAllBytes(Path.of(uploadBasePath).resolve(sample.getOriginalPath()).normalize());
                sample.setPreviewBase64("data:image/png;base64," + Base64.getEncoder().encodeToString(image));
            } catch (Exception ignored) { }
        }
        return samples;
    }

    public EngineeringArchiveImport uploadAndRecognize(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择历史档案图片");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("第一版仅支持 JPG/PNG 图片");
        }
        try {
            byte[] bytes = file.getBytes();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            EngineeringArchiveImport existing = archiveMapper.selectOne(new LambdaQueryWrapper<EngineeringArchiveImport>()
                    .eq(EngineeringArchiveImport::getFileHash, hash));
            if (existing != null) return existing;

            String originalName = file.getOriginalFilename() == null ? "archive.jpg" : file.getOriginalFilename();
            String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : ".jpg";
            String relative = "engineering-archive/" + LocalDate.now() + "/" + UUID.randomUUID() + extension;
            Path target = Path.of(uploadBasePath).resolve(relative).normalize();
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);

            String user = loginUser();
            EngineeringArchiveImport archive = new EngineeringArchiveImport();
            archive.setFileName(originalName);
            archive.setFilePath(relative);
            archive.setFileHash(hash);
            archive.setRecognizeStatus(ArchiveRecognitionStatus.RECOGNIZING.getValue());
            archive.setCreateBy(user);
            archive.setUpdateBy(user);
            archiveMapper.insert(archive);
            SysAttachment attachment = new SysAttachment();
            attachment.setBizType("engineering_archive");
            attachment.setBizId(archive.getArchiveId());
            attachment.setCategory("历史档案原图");
            attachment.setFileName(originalName);
            attachment.setFilePath(relative);
            attachment.setFileSize((long) bytes.length);
            attachment.setFileType(contentType);
            attachment.setRemark("历史档案录入原始文件");
            attachment.setCreateBy(user);
            attachment.setUpdateBy(user);
            attachmentMapper.insert(attachment);
            recognize(archive, bytes, originalName);
            return archive;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("历史档案上传失败：" + e.getMessage());
        }
    }

    public EngineeringArchiveImport retry(Long id) {
        EngineeringArchiveImport archive = required(id);
        try {
            byte[] bytes = Files.readAllBytes(Path.of(uploadBasePath).resolve(archive.getFilePath()).normalize());
            archive.setRecognizeStatus(ArchiveRecognitionStatus.RECOGNIZING.getValue());
            archive.setRecognizeMessage(null);
            archiveMapper.updateById(archive);
            recognize(archive, bytes, archive.getFileName());
            return archive;
        } catch (Exception e) {
            archive.setRecognizeStatus(ArchiveRecognitionStatus.FAILED.getValue());
            archive.setRecognizeMessage(trimMessage(e.getMessage()));
            archiveMapper.updateById(archive);
            return archive;
        }
    }

    /** 重新识别并覆盖本档案关联的未审批草稿。已审批/已发布数据禁止覆盖。 */
    @Transactional(rollbackFor = Exception.class)
    public EngineeringArchiveImport overwriteRetry(Long id) {
        EngineeringArchiveImport archive = required(id);
        assertOverwriteAllowed(archive);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(Path.of(uploadBasePath).resolve(archive.getFilePath()).normalize());
            archive.setRecognizeStatus(ArchiveRecognitionStatus.RECOGNIZING.getValue());
            archive.setRecognizeMessage(null);
            archiveMapper.updateById(archive);
            recognize(archive, bytes, archive.getFileName());
        } catch (Exception e) {
            archive.setRecognizeStatus(ArchiveRecognitionStatus.FAILED.getValue());
            archive.setRecognizeMessage("覆盖重试失败：" + trimMessage(e.getMessage()));
            archiveMapper.updateById(archive);
            return archive;
        }
        if (archive.getRecognizeStatus() == null || ArchiveRecognitionStatus.REVIEW.getValue() != archive.getRecognizeStatus()) return archive;
        deleteGeneratedDrafts(archive);
        // MyBatis 默认跳过 NULL 字段，必须显式清空数据库关联，避免再次进入覆盖递归。
        jdbcTemplate.update("UPDATE engineering_archive_import SET product_id=NULL,bom_id=NULL,routing_id=NULL WHERE archive_id=?",
                archive.getArchiveId());
        archive.setProductId(null);
        archive.setBomId(null);
        archive.setRoutingId(null);
        return generateDrafts(archive.getArchiveId());
    }

    private void assertOverwriteAllowed(EngineeringArchiveImport archive) {
        if (hasApprovedProduct(archive) || hasApprovedBom(archive) || hasApprovedRouting(archive)) {
            throw new BusinessException("该档案已存在审批通过或非草稿数据，禁止覆盖重试");
        }
    }

    private boolean hasApprovedProduct(EngineeringArchiveImport archive) {
        return archive.getProductId() != null && exists("SELECT COUNT(*) FROM product WHERE product_id=? AND product_status IN (?, ?, ?)", archive.getProductId(), ProductEnums.Status.APPROVED.getValue(), ProductEnums.Status.RELEASED.getValue(), ProductEnums.Status.OBSOLETE.getValue());
    }
    private boolean hasApprovedBom(EngineeringArchiveImport archive) {
        return archive.getBomId() != null && exists("SELECT COUNT(*) FROM engineering_bom WHERE bom_id=? AND approve_status=?", archive.getBomId(), ProductEnums.BomStatus.APPROVED.getValue());
    }
    private boolean hasApprovedRouting(EngineeringArchiveImport archive) {
        return archive.getRoutingId() != null && exists("SELECT COUNT(*) FROM engineering_routing WHERE routing_id=? AND approve_status=?", archive.getRoutingId(), ProductEnums.RouteStatus.APPROVED.getValue());
    }
    private boolean exists(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private void deleteGeneratedDrafts(EngineeringArchiveImport archive) {
        jdbcTemplate.update("DELETE FROM engineering_bom_item WHERE bom_id=?", archive.getBomId());
        jdbcTemplate.update("DELETE FROM engineering_routing_item WHERE routing_id=?", archive.getRoutingId());
        jdbcTemplate.update("DELETE FROM engineering_bom WHERE bom_id=? AND approve_status=?",
                archive.getBomId(), ProductEnums.BomStatus.DRAFT.getValue());
        jdbcTemplate.update("DELETE FROM engineering_routing WHERE routing_id=? AND approve_status=?",
                archive.getRoutingId(), ProductEnums.RouteStatus.DRAFT.getValue());
        jdbcTemplate.update("DELETE FROM product WHERE product_id=? AND product_status=?",
                archive.getProductId(), ProductEnums.Status.DEVELOPING.getValue());
    }

    private void recognize(EngineeringArchiveImport archive, byte[] bytes, String fileName) {
        try {
            String json = callLocalOcr(bytes, fileName);
            JsonNode root = objectMapper.readTree(json);
            persistAndMatchIcons(archive, root);
            normalizeRecognizedContent(root);
            persistDraftImages(archive, root);
            archive.setExtractedJson(objectMapper.writeValueAsString(root));
            archive.setProductName(text(root, "productName"));
            archive.setProductCode(text(root, "productCode"));
            archive.setRecognizeStatus(ArchiveRecognitionStatus.REVIEW.getValue());
            archive.setRecognizeTime(LocalDateTime.now());
            archive.setRecognizeMessage("本地识别完成，请查看并修正识别草稿");
        } catch (Exception e) {
            log.warn("本地OCR失败 archiveId={}: {}", archive.getArchiveId(), e.getMessage());
            archive.setRecognizeStatus(ArchiveRecognitionStatus.FAILED.getValue());
            archive.setRecognizeTime(LocalDateTime.now());
            archive.setRecognizeMessage("本地OCR服务不可用或识别失败：" + trimMessage(e.getMessage()));
        }
        archive.setUpdateBy(loginUser());
        archiveMapper.updateById(archive);
    }

    @Transactional(rollbackFor = Exception.class)
    public EngineeringArchiveImport updateResult(Long id, JsonNode result) {
        EngineeringArchiveImport archive = required(id);
        try {
            learnConfirmedIconMappings(result);
            normalizeRecognizedContent(result);
            archive.setExtractedJson(objectMapper.writeValueAsString(result));
            archive.setProductName(text(result, "productName"));
            archive.setProductCode(text(result, "productCode"));
            archive.setRecognizeStatus(ArchiveRecognitionStatus.REVIEW.getValue());
            archive.setUpdateBy(loginUser());
            archiveMapper.updateById(archive);
            return archive;
        } catch (Exception e) {
            throw new BusinessException("保存识别结果失败：" + e.getMessage());
        }
    }

    /** 将人工确认的单工序图标沉淀为最新映射；自动匹配结果不会自动学习。 */
    private void learnConfirmedIconMappings(JsonNode root) {
        for (JsonNode workflow : root.path("workflows")) {
            for (JsonNode step : workflow.path("steps")) {
                if (!step.path("processMappingConfirmed").asBoolean(false)) continue;
                if ("COMPOSITE".equals(text(step, "processStructure")) && step.path("components").isArray()) {
                    learnCompositeIconMapping(step);
                } else {
                    learnIconMapping(step);
                }
            }
        }
    }

    /** 子工序本身也是图标样本，和普通图标共用样本表。 */
    private void learnCompositeIconMapping(JsonNode step) {
        Long sampleId = step.path("iconSampleId").canConvertToLong() ? step.path("iconSampleId").longValue() : null;
        if (sampleId == null) return;
        ProcessIconSample sample = iconSampleMapper.selectById(sampleId);
        if (sample == null) return;
        int order = 1;
        for (JsonNode component : step.path("components")) {
            Long processId = component.path("processId").canConvertToLong() ? component.path("processId").longValue() : null;
            if (processId == null) continue;
            String suffix = "#component-" + order;
            String hash = defaultText(text(component, "perceptualHash"), sample.getPerceptualHash());
            Long childId = jdbcTemplate.query(
                    "SELECT sample_id FROM engineering_process_icon_sample WHERE parent_sample_id=? AND component_order=? LIMIT 1",
                    rs -> rs.next() ? rs.getLong(1) : null, sampleId, order);
            if (childId == null) {
                insert("INSERT INTO engineering_process_icon_sample(process_id,archive_id,workflow_type,step_no,original_path,normalized_path,perceptual_hash,match_score,confirm_status,usage_count,use_as_system_icon,create_by,update_by,parent_sample_id,component_order,work_instruction) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        processId, sample.getArchiveId(), sample.getWorkflowType(), sample.getStepNo(),
                        sample.getOriginalPath() + suffix, sample.getNormalizedPath() + suffix, hash, 1d,
                        IconConfirmStatus.CONFIRMED.getValue(), 1, 1, loginUser(), loginUser(), sampleId, order,
                        text(component, "workInstruction"));
            } else {
                jdbcTemplate.update("UPDATE engineering_process_icon_sample SET process_id=?,perceptual_hash=?,confirm_status=?,use_as_system_icon=1,work_instruction=?,update_by=? WHERE sample_id=?",
                        processId, hash, IconConfirmStatus.CONFIRMED.getValue(), text(component, "workInstruction"), loginUser(), childId);
            }
            order++;
        }
        sample.setProcessId(null);
        sample.setConfirmStatus(IconConfirmStatus.CONFIRMED.getValue());
        sample.setUseAsSystemIcon(1);
        sample.setUsageCount((sample.getUsageCount() == null ? 0 : sample.getUsageCount()) + 1);
        sample.setUpdateBy(loginUser());
        iconSampleMapper.updateById(sample);
    }

    private void learnIconMapping(JsonNode step) {
        Long sampleId = step.path("iconSampleId").canConvertToLong()
                ? step.path("iconSampleId").longValue() : null;
        Long processId = step.path("processId").canConvertToLong()
                ? step.path("processId").longValue() : null;
        if (sampleId == null || processId == null) return;
        ProcessIconSample sample = iconSampleMapper.selectById(sampleId);
        if (sample == null || sample.getPerceptualHash() == null) return;
        Integer enabled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM engineering_standard_process WHERE process_id=? AND is_enabled=1", Integer.class, processId);
        if (enabled == null || enabled == 0) return;
        jdbcTemplate.update("UPDATE engineering_process_icon_sample SET confirm_status=?,use_as_system_icon=0 " +
                        "WHERE perceptual_hash=? AND sample_id<>? AND confirm_status=?",
                IconConfirmStatus.PENDING.getValue(), sample.getPerceptualHash(), sampleId,
                IconConfirmStatus.CONFIRMED.getValue());
        sample.setProcessId(processId);
        sample.setConfirmStatus(IconConfirmStatus.CONFIRMED.getValue());
        sample.setUseAsSystemIcon(1);
        sample.setUsageCount((sample.getUsageCount() == null ? 0 : sample.getUsageCount()) + 1);
        sample.setUpdateBy(loginUser());
        iconSampleMapper.updateById(sample);
    }

    /** 标准工序已匹配时，清理 OCR 把图标误读成单字/符号的伪原文。 */
    private void normalizeRecognizedContent(JsonNode root) {
        for (JsonNode workflow : root.path("workflows")) {
            for (JsonNode step : workflow.path("steps")) {
                if (!(step instanceof ObjectNode object)) continue;
                if (!object.has("ocrRawText") && object.has("rawText")) {
                    object.set("ocrRawText", object.get("rawText"));
                }
                boolean composite = "COMPOSITE".equals(text(step, "processStructure"));
                if (composite && step.path("components").isArray()) {
                    object.put("contentType", "ICON_ONLY");
                    object.put("recognizedText", "复合图标");
                    for (JsonNode component : step.path("components")) {
                        if (component instanceof ObjectNode child) {
                            if (!child.has("ocrRawText") && child.has("text")) {
                                child.set("ocrRawText", child.get("text"));
                            }
                            if (looksLikeIconNoise(text(child, "text"))) {
                                child.putNull("text");
                                child.put("recognizedText", "图标");
                                child.put("contentType", "ICON_ONLY");
                            }
                        }
                    }
                    continue;
                }
                if (!step.path("processId").canConvertToLong()) {
                    Map<String, Object> textProcess = resolveTextProcess(text(step, "rawText"));
                    if (textProcess != null) {
                        object.put("processId", ((Number) textProcess.get("process_id")).longValue());
                        object.put("processName", String.valueOf(textProcess.get("process_name")));
                        object.put("recognizedText", String.valueOf(textProcess.get("process_name")));
                    }
                }
                if (!step.path("processId").canConvertToLong()) continue;
                String rawText = text(step, "rawText");
                if (looksLikeIconNoise(rawText)) {
                    object.putNull("rawText");
                    object.put("recognizedText", "图标");
                    object.put("contentType", "ICON_ONLY");
                } else if (rawText != null && !rawText.isBlank()) {
                    if (!object.has("recognizedText") || object.path("recognizedText").isNull()) {
                        object.put("recognizedText", rawText);
                    }
                    object.put("contentType", "TEXT_ONLY");
                }
            }
        }
    }

    private boolean looksLikeIconNoise(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim();
        if (normalized.length() == 1 && !normalized.matches("[A-Za-z0-9]")) return true;
        return normalized.matches("[^\\p{L}\\p{N}]+") || normalized.matches("[\\u4e00-\\u9fff]");
    }

    /** 文本别名只做明确映射，避免把备注文字误当成标准工序。 */
    private Map<String, Object> resolveTextProcess(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if ("QC".equalsIgnoreCase(normalized)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT process_id,process_name FROM engineering_standard_process WHERE process_name=? AND is_enabled=1 LIMIT 2", "品检");
            return rows.size() == 1 ? rows.getFirst() : null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT process_id,process_name FROM engineering_standard_process WHERE process_name=? AND is_enabled=1 LIMIT 2", normalized);
        return rows.size() == 1 ? rows.getFirst() : null;
    }

    @Transactional(rollbackFor = Exception.class)
    public EngineeringArchiveImport generateDrafts(Long id) {
        EngineeringArchiveImport archive = required(id);
        // 已有生成记录时只重建当前已保存的人工确认结果，不能重新 OCR 覆盖人工选择。
        if (archive.getProductId() != null || archive.getBomId() != null || archive.getRoutingId() != null) {
            assertOverwriteAllowed(archive);
            deleteGeneratedDrafts(archive);
            jdbcTemplate.update("UPDATE engineering_archive_import SET product_id=NULL,bom_id=NULL,routing_id=NULL WHERE archive_id=?",
                    archive.getArchiveId());
            archive.setProductId(null);
            archive.setBomId(null);
            archive.setRoutingId(null);
        }
        try {
            JsonNode root = objectMapper.readTree(archive.getExtractedJson());
            String code = text(root, "productCode");
            String name = text(root, "productName");
            if (code == null || code.isBlank() || name == null || name.isBlank()) {
                throw new BusinessException("请先确认产品名称和产品编号");
            }
            Integer duplicate = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product WHERE product_code=?", Integer.class, code);
            if (duplicate != null && duplicate > 0) throw new BusinessException("产品编号已存在：" + code);
            String user = loginUser();
            long productId = insert("INSERT INTO product(product_code,product_name,product_type,product_status,from_source,remark,create_by,update_by) VALUES(?,?,?,?,?,?,?,?)",
                    code, name, "custom", ProductEnums.Status.DEVELOPING.getValue(), SOURCE, text(root, "productRemark"), user, user);
            long bomId = insert("INSERT INTO engineering_bom(bom_code,bom_name,product_id,bom_version,version,bom_type,is_current,approve_status,remark,create_by,update_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    "BOM-" + code, name + " 历史档案BOM", productId, VERSION, VERSION, BOM_TYPE, true,
                    ProductEnums.BomStatus.DRAFT.getValue(), "来源历史档案 #" + id, user, user);
            int itemOrder = 1;
            for (JsonNode material : root.path("materials")) {
                String materialName = text(material, "materialName");
                if (materialName == null || materialName.isBlank()) continue;
                List<Map<String, Object>> matches = jdbcTemplate.queryForList(
                        "SELECT material_id,material_code FROM inventory_material WHERE material_name=? AND status=1 LIMIT 2", materialName);
                Long materialId = matches.size() == 1 ? ((Number) matches.getFirst().get("material_id")).longValue() : null;
                String materialCode = matches.size() == 1 ? String.valueOf(matches.getFirst().get("material_code")) : null;
                jdbcTemplate.update("INSERT INTO engineering_bom_item(bom_id,material_id,material_code,material_name,quantity,unit,item_order,specification,source_type,create_by,update_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        bomId, materialId, materialCode, materialName, decimal(material, "quantity", 1),
                        defaultText(text(material, "unit"), "PCS"), itemOrder++, text(material, "specification"), "buy", user, user);
            }
            long routingId = insert("INSERT INTO engineering_routing(routing_code,routing_name,product_id,product_code,product_name,routing_type,routing_version,version,is_current,approve_status,process_count,description,create_by,update_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    "RT-" + code, name + " 历史档案工艺", productId, code, name, ROUTING_TYPE, VERSION, VERSION, true,
                    ProductEnums.BomStatus.DRAFT.getValue(), countSteps(root), "来源历史档案 #" + id, user, user);
            int order = 1;
            for (JsonNode workflow : root.path("workflows")) {
                String workflowType = defaultText(text(workflow, "workflowType"), "OTHER");
                for (JsonNode step : workflow.path("steps")) {
                    if ("EMPTY".equals(text(step, "contentType"))) continue;
                    boolean composite = "COMPOSITE".equals(text(step, "processStructure"));
                    if (composite && step.path("components").isArray() && step.path("components").size() > 0) {
                        String groupName = defaultText(text(step, "processName"), text(step, "rawText"));
                        long parentId = insert("INSERT INTO engineering_routing_item(routing_id,process_id,process_name,major_category,process_order,process_category,description,work_instruction,remark,group_id,group_order,group_name) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                                routingId, null, groupName, "ASSEMBLY", order++, workflowType,
                                text(step, "rawText"), text(step, "workInstruction"), text(step, "operationRemark"),
                                null, 0, groupName);
                        long groupId = parentId;
                        int groupOrder = 1;
                        for (JsonNode component : step.path("components")) {
                            Long processId = component.path("processId").canConvertToLong()
                                    ? component.path("processId").longValue() : null;
                            String processName = text(component, "text");
                            if (processId != null) {
                                List<Map<String,Object>> p = jdbcTemplate.queryForList(
                                        "SELECT process_name FROM engineering_standard_process WHERE process_id=? AND is_enabled=1", processId);
                                if (p.isEmpty()) processId = null;
                                else processName = String.valueOf(p.getFirst().get("process_name"));
                            }
                            Integer indexNumber = extractIndexNumber(component);
                            jdbcTemplate.update("INSERT INTO engineering_routing_item(routing_id,process_id,process_name,major_category,process_order,process_category,description,work_instruction,remark,group_id,group_order,group_name,parent_id,index_number) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                    routingId, processId, processName, "ASSEMBLY", null, workflowType,
                                    text(component, "text"), text(component, "workInstruction"),
                                    text(step, "operationRemark"), groupId, groupOrder++, groupName, parentId, indexNumber);
                        }
                    } else {
                        Long processId = step.path("processId").canConvertToLong() ? step.path("processId").longValue() : null;
                        String processName = text(step, "processName");
                        if (processId != null) {
                            List<Map<String,Object>> p = jdbcTemplate.queryForList("SELECT process_name FROM engineering_standard_process WHERE process_id=? AND is_enabled=1", processId);
                            if (p.isEmpty()) processId = null; else processName = String.valueOf(p.getFirst().get("process_name"));
                        }
                        jdbcTemplate.update("INSERT INTO engineering_routing_item(routing_id,process_id,process_name,major_category,process_order,process_category,description,work_instruction,remark) VALUES(?,?,?,?,?,?,?,?,?)",
                                routingId, processId, processName, "ASSEMBLY", order++, workflowType,
                                text(step, "rawText"), text(step, "workInstruction"), text(step, "operationRemark"));
                    }
                }
            }
            jdbcTemplate.update("UPDATE product SET current_bom_id=?,current_route_id=?,current_bom_version=?,current_routing_version=? WHERE product_id=?",
                    bomId, routingId, VERSION, VERSION, productId);
            archive.setProductId(productId);
            archive.setBomId(bomId);
            archive.setRoutingId(routingId);
            archive.setRecognizeStatus(ArchiveRecognitionStatus.GENERATED.getValue());
            archive.setRecognizeMessage("已生成产品、BOM和工艺路线草稿");
            archive.setUpdateBy(user);
            archiveMapper.updateById(archive);
            return archive;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("生成草稿失败：" + e.getMessage());
        }
    }

    public ProcessIconSample confirmSample(Long sampleId, Long processId) {
        ProcessIconSample sample = iconSampleMapper.selectById(sampleId);
        if (sample == null) throw new BusinessException("图标样本不存在");
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM engineering_standard_process WHERE process_id=? AND is_enabled=1", Integer.class, processId);
        if (count == null || count == 0) throw new BusinessException("标准工序不存在或已停用");
        sample.setProcessId(processId);
        sample.setConfirmStatus(IconConfirmStatus.CONFIRMED.getValue());
        sample.setUpdateBy(loginUser());
        iconSampleMapper.updateById(sample);
        return sample;
    }

    private void persistAndMatchIcons(EngineeringArchiveImport archive, JsonNode root) throws Exception {
        List<ProcessIconSample> confirmed = iconSampleMapper.selectList(
                new LambdaQueryWrapper<ProcessIconSample>()
                        .eq(ProcessIconSample::getConfirmStatus, IconConfirmStatus.CONFIRMED.getValue()));
        Set<Long> compositeSampleIds = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT parent_sample_id FROM engineering_process_icon_sample WHERE parent_sample_id IS NOT NULL AND confirm_status=1", Long.class));
        Path directory = Path.of(uploadBasePath).resolve("engineering-archive/icons/" + archive.getArchiveId()).normalize();
        Files.createDirectories(directory);
        for (JsonNode workflow : root.path("workflows")) {
            String type = defaultText(text(workflow, "workflowType"), "OTHER");
            for (JsonNode rawStep : workflow.path("steps")) {
                if (!(rawStep instanceof ObjectNode step)) continue;
                String contentType = text(step, "contentType");
                if ("EMPTY".equals(contentType) || "TEXT_ONLY".equals(contentType)) continue;
                String original64 = text(step, "iconOriginalBase64");
                String normalized64 = text(step, "iconNormalizedBase64");
                String hash = text(step, "perceptualHash");
                if (original64 == null || normalized64 == null || hash == null || hash.length() != 16) continue;
                int stepNo = step.path("stepNo").asInt();
                String stem = type.toLowerCase() + "-" + stepNo + "-" + UUID.randomUUID();
                Path original = directory.resolve(stem + ".png");
                Path normalized = directory.resolve(stem + "-normalized.png");
                Files.write(original, Base64.getDecoder().decode(original64));
                Files.write(normalized, Base64.getDecoder().decode(normalized64));
                ProcessIconSample best = null;
                int bestDistance = Integer.MAX_VALUE;
                long targetHash = Long.parseUnsignedLong(hash, 16);
                for (ProcessIconSample candidate : confirmed) {
                    // 同一视觉符号在面板/上线/下线中的工艺含义可能不同，禁止跨流程段套用映射。
                    if (!type.equals(candidate.getWorkflowType())) continue;
                    boolean composite = "COMPOSITE".equals(text(step, "processStructure"));
                    if (candidate.getProcessId() == null && (!composite || !compositeSampleIds.contains(candidate.getSampleId()))) continue;
                    try {
                        int distance = Long.bitCount(targetHash ^ Long.parseUnsignedLong(candidate.getPerceptualHash(), 16));
                        if (distance < bestDistance) { bestDistance = distance; best = candidate; }
                    } catch (RuntimeException ignored) { }
                }
                double score = best == null ? 0d : 1d - bestDistance / 64d;
                ProcessIconSample sample = new ProcessIconSample();
                sample.setProcessId(score >= 0.875d ? best.getProcessId() : null);
                sample.setArchiveId(archive.getArchiveId());
                sample.setWorkflowType(type);
                sample.setStepNo(stepNo);
                sample.setOriginalPath(Path.of(uploadBasePath).relativize(original).toString());
                sample.setNormalizedPath(Path.of(uploadBasePath).relativize(normalized).toString());
                sample.setPerceptualHash(hash);
                sample.setMatchScore(java.math.BigDecimal.valueOf(score));
                sample.setConfirmStatus(IconConfirmStatus.PENDING.getValue());
                sample.setUsageCount(0);
                sample.setUseAsSystemIcon(0);
                sample.setCreateBy(loginUser());
                sample.setUpdateBy(loginUser());
                iconSampleMapper.insert(sample);
                step.put("iconSampleId", sample.getSampleId());
                step.put("matchScore", score);
                if (sample.getProcessId() != null) step.put("processId", sample.getProcessId());
                if ("COMPOSITE".equals(text(step, "processStructure"))) {
                    matchCompositeComponents(step, type, confirmed);
                }
                // 子图标未独立匹配成功时保留待确认，不能把相似历史复合格按顺序套用。
                step.remove("iconOriginalBase64");
                step.remove("iconNormalizedBase64");
            }
        }
    }

    /** 复合格拆分后的每个子项按普通工序规则独立匹配；整格映射只作为未命中项的兜底。 */
    private void matchCompositeComponents(ObjectNode step, String workflowType, List<ProcessIconSample> confirmed) {
        for (JsonNode node : step.path("components")) {
            if (!(node instanceof ObjectNode component) || component.path("processId").canConvertToLong()) continue;
            String rawText = text(component, "text");
            if (rawText != null && !rawText.isBlank()) {
                List<Map<String, Object>> processes = jdbcTemplate.queryForList(
                        "SELECT process_id FROM engineering_standard_process WHERE process_name=? AND is_enabled=1 LIMIT 2", rawText);
                if (processes.size() == 1) {
                    component.put("processId", ((Number) processes.getFirst().get("process_id")).longValue());
                    component.put("confirmed", false);
                    continue;
                }
            }
                String hash = text(component, "perceptualHash");
                if (hash == null || hash.length() != 16) continue;
            if (isDegeneratePerceptualHash(hash)) continue;
            long targetHash;
            try { targetHash = Long.parseUnsignedLong(hash, 16); } catch (RuntimeException ignored) { continue; }
            ProcessIconSample best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (ProcessIconSample candidate : confirmed) {
                if (!workflowType.equals(candidate.getWorkflowType()) || candidate.getProcessId() == null) continue;
                if (isDegeneratePerceptualHash(candidate.getPerceptualHash())) continue;
                try {
                    int distance = Long.bitCount(targetHash ^ Long.parseUnsignedLong(candidate.getPerceptualHash(), 16));
                    if (distance < bestDistance) { bestDistance = distance; best = candidate; }
                } catch (RuntimeException ignored) { }
            }
            if (best != null && 1d - bestDistance / 64d >= 0.875d) {
                component.put("processId", best.getProcessId());
                component.put("confirmed", false);
            }
        }
    }

    /** 全 0/全 F 哈希通常表示切片为空白或被边框吞没，不能用于标准工序映射。 */
    private boolean isDegeneratePerceptualHash(String hash) {
        if (hash == null || hash.length() != 16) return true;
        return hash.matches("0{16}") || hash.matches("[fF]{16}");
    }

    /** 将 OCR 返回的区域/整格图片落到本地，草稿 JSON 只保留可追溯路径。 */
    private void persistDraftImages(EngineeringArchiveImport archive, JsonNode root) throws Exception {
        Path base = Path.of(uploadBasePath).toAbsolutePath().normalize();
        Path directory = base.resolve("engineering-archive/crops/" + archive.getArchiveId()).normalize();
        if (!directory.startsWith(base)) throw new BusinessException("识别切片目录越界");
        Files.createDirectories(directory);
        persistDraftImages(root, directory, "archive");
    }

    private void persistDraftImages(JsonNode node, Path directory, String prefix) throws Exception {
        if (node == null) return;
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                persistDraftImages(node.get(index), directory, prefix + "-" + (index + 1));
            }
            return;
        }
        if (!(node instanceof ObjectNode object)) return;
        List<String> fields = new java.util.ArrayList<>();
        object.fieldNames().forEachRemaining(fields::add);
        for (String field : fields) {
            JsonNode value = object.get(field);
            if (field.endsWith("ImageBase64") && value != null && value.isTextual() && !value.asText().isBlank()) {
                String stem = field.substring(0, field.length() - "ImageBase64".length())
                        .replaceAll("[^A-Za-z0-9_-]", "-");
                String fileName = prefix + "-" + stem + "-" + UUID.randomUUID() + ".png";
                Path target = directory.resolve(fileName).normalize();
                if (!target.startsWith(directory)) throw new BusinessException("识别切片文件越界");
                Files.write(target, Base64.getDecoder().decode(value.asText()));
                object.put(field.substring(0, field.length() - "Base64".length()) + "Path",
                        Path.of(uploadBasePath).toAbsolutePath().normalize().relativize(target).toString());
                object.remove(field);
            } else {
                persistDraftImages(value, directory, prefix + "-" + field.replaceAll("[^A-Za-z0-9_-]", "-"));
            }
        }
    }

    private String callLocalOcr(byte[] fileBytes, String fileName) throws Exception {
        String boundary = "----JjxArchive" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream(fileBytes.length + 512);
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" +
                fileName.replace("\"", "") + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(fileBytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(ocrUrl + "/recognize"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).proxy(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
            @Override public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) { }
        }).build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("本地OCR返回HTTP " + response.statusCode() + "：" + response.body());
        }
        return response.body();
    }

    private EngineeringArchiveImport required(Long id) {
        EngineeringArchiveImport row = archiveMapper.selectById(id);
        if (row == null) throw new BusinessException("历史档案不存在");
        return row;
    }
    private long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, keys);
        if (keys.getKey() == null) throw new BusinessException("未取得新建记录ID");
        return keys.getKey().longValue();
    }
    private String loginUser() { return StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginId()) : "system"; }
    private String text(JsonNode node, String field) { JsonNode value = node == null ? null : node.get(field); return value == null || value.isNull() ? null : value.asText().trim(); }
    private String defaultText(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private java.math.BigDecimal decimal(JsonNode node, String field, int fallback) { try { return node.path(field).isNumber() ? node.path(field).decimalValue() : new java.math.BigDecimal(node.path(field).asText()); } catch (Exception e) { return java.math.BigDecimal.valueOf(fallback); } }
    private int countSteps(JsonNode root) {
        int count = 0;
        for (JsonNode workflow : root.path("workflows")) {
            for (JsonNode step : workflow.path("steps")) {
                if ("EMPTY".equals(text(step, "contentType"))) continue;
                if ("COMPOSITE".equals(text(step, "processStructure")) && step.path("components").isArray()) {
                    count += step.path("components").size();
                } else {
                    count++;
                }
            }
        }
        return count;
    }
    private Integer extractIndexNumber(JsonNode component) {
        String instruction = text(component, "workInstruction");
        Long processId = component.path("processId").canConvertToLong() ? component.path("processId").longValue() : null;
        if (processId == null) return null;
        List<Map<String, Object>> properties = jdbcTemplate.queryForList(
                "SELECT has_index,has_work_instruction FROM engineering_standard_process WHERE process_id=? AND is_enabled=1 LIMIT 1", processId);
        if (properties.isEmpty()) return null;
        boolean hasIndex = ((Number) properties.getFirst().get("has_index")).intValue() == 1;
        boolean hasInstruction = ((Number) properties.getFirst().get("has_work_instruction")).intValue() == 1;
        if (!hasIndex || hasInstruction) return null;
        Integer value = trailingNumber(instruction);
        return value != null ? value : trailingNumber(text(component, "text"));
    }
    private Integer trailingNumber(String value) {
        if (value == null || value.isBlank()) return null;
        Matcher matcher = Pattern.compile("(\\d+)\\s*$").matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
    private String trimMessage(String message) { if (message == null) return "未知错误"; return message.length() > 350 ? message.substring(0, 350) : message; }
}
