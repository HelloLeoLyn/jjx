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
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        if (archive.getRecognizeStatus() == null || ArchiveRecognitionStatus.GENERATED.getValue() != archive.getRecognizeStatus()
                || archive.getProductId() == null || archive.getBomId() == null || archive.getRoutingId() == null) return false;
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product p JOIN engineering_bom b ON b.bom_id=? JOIN engineering_routing r ON r.routing_id=? WHERE p.product_id=? AND p.product_status=? AND b.approve_status=? AND r.approve_status=?",
                Integer.class, archive.getBomId(), archive.getRoutingId(), archive.getProductId(), ProductEnums.Status.DEVELOPING.getValue(), ProductEnums.BomStatus.DRAFT.getValue(), ProductEnums.RouteStatus.DRAFT.getValue());
        return count != null && count == 1;
    }

    public EngineeringArchiveImport get(Long id) {
        return archiveMapper.selectById(id);
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
        if (archive.getProductId() == null || archive.getBomId() == null || archive.getRoutingId() == null) {
            return retry(id);
        }
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
        archive.setProductId(null);
        archive.setBomId(null);
        archive.setRoutingId(null);
        archiveMapper.updateById(archive);
        return generateDrafts(archive.getArchiveId());
    }

    private void assertOverwriteAllowed(EngineeringArchiveImport archive) {
        Map<String, Object> product = jdbcTemplate.queryForMap(
                "SELECT product_status FROM product WHERE product_id=?", archive.getProductId());
        Map<String, Object> bom = jdbcTemplate.queryForMap(
                "SELECT approve_status FROM engineering_bom WHERE bom_id=?", archive.getBomId());
        Map<String, Object> routing = jdbcTemplate.queryForMap(
                "SELECT approve_status FROM engineering_routing WHERE routing_id=?", archive.getRoutingId());
        int productStatus = ((Number) product.get("product_status")).intValue();
        int bomStatus = ((Number) bom.get("approve_status")).intValue();
        int routingStatus = ((Number) routing.get("approve_status")).intValue();
        if (productStatus != ProductEnums.Status.DEVELOPING.getValue()
                || bomStatus != ProductEnums.BomStatus.DRAFT.getValue()
                || routingStatus != ProductEnums.RouteStatus.DRAFT.getValue()) {
            throw new BusinessException("该档案已存在审批通过或非草稿数据，禁止覆盖重试");
        }
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
            archive.setExtractedJson(objectMapper.writeValueAsString(root));
            archive.setProductName(text(root, "productName"));
            archive.setProductCode(text(root, "productCode"));
            archive.setRecognizeStatus(ArchiveRecognitionStatus.REVIEW.getValue());
            archive.setRecognizeMessage("本地识别完成，请确认后生成草稿");
        } catch (Exception e) {
            log.warn("本地OCR失败 archiveId={}: {}", archive.getArchiveId(), e.getMessage());
            archive.setRecognizeStatus(ArchiveRecognitionStatus.FAILED.getValue());
            archive.setRecognizeMessage("本地OCR服务不可用或识别失败：" + trimMessage(e.getMessage()));
        }
        archive.setUpdateBy(loginUser());
        archiveMapper.updateById(archive);
    }

    public EngineeringArchiveImport updateResult(Long id, JsonNode result) {
        EngineeringArchiveImport archive = required(id);
        try {
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

    @Transactional(rollbackFor = Exception.class)
    public EngineeringArchiveImport generateDrafts(Long id) {
        EngineeringArchiveImport archive = required(id);
        if (archive.getProductId() != null) throw new BusinessException("该档案已经生成过草稿");
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
                    Long processId = step.path("processId").canConvertToLong() ? step.path("processId").longValue() : null;
                    String processName = text(step, "processName");
                    if (processId != null) {
                        List<Map<String,Object>> p = jdbcTemplate.queryForList("SELECT process_name FROM engineering_standard_process WHERE process_id=? AND is_enabled=1", processId);
                        if (p.isEmpty()) processId = null; else processName = String.valueOf(p.getFirst().get("process_name"));
                    }
                    jdbcTemplate.update("INSERT INTO engineering_routing_item(routing_id,process_id,process_name,major_category,process_order,process_category,description) VALUES(?,?,?,?,?,?,?)",
                            routingId, processId, processName, "ASSEMBLY", order++, workflowType, text(step, "rawText"));
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
                        .eq(ProcessIconSample::getConfirmStatus, IconConfirmStatus.CONFIRMED.getValue())
                        .isNotNull(ProcessIconSample::getProcessId));
        Path directory = Path.of(uploadBasePath).resolve("engineering-archive/icons/" + archive.getArchiveId()).normalize();
        Files.createDirectories(directory);
        for (JsonNode workflow : root.path("workflows")) {
            String type = defaultText(text(workflow, "workflowType"), "OTHER");
            for (JsonNode rawStep : workflow.path("steps")) {
                if (!(rawStep instanceof ObjectNode step)) continue;
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
                step.remove("iconOriginalBase64");
                step.remove("iconNormalizedBase64");
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
    private int countSteps(JsonNode root) { int count = 0; for (JsonNode w : root.path("workflows")) count += w.path("steps").size(); return count; }
    private String trimMessage(String message) { if (message == null) return "未知错误"; return message.length() > 350 ? message.substring(0, 350) : message; }
}
