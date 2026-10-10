package com.jjx.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.dto.ProductWorkSpecDTO;
import com.jjx.product.domain.dto.PrintSpecRemarksDTO;
import com.jjx.product.domain.dto.ColorCheckDTO;
import com.jjx.product.enums.ProcessCategoryEnum;
import com.jjx.product.enums.ColorCheckItemEnum;
import com.jjx.product.enums.ColorCheckResultEnum;
import com.jjx.common.constant.LogActions;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.utils.OperLogDetailBuilder;
import com.jjx.product.domain.vo.ProductFullVO;
import com.jjx.product.mapper.ProductWorkSpecMapper;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class ProductWorkSpecService {
    private static final Set<String> EMBOSS_KEYS = Set.of("setupHeight", "requiredHeight", "upperTemperature", "lowerTemperature", "pressTime", "holdTime");
    private static final Set<String> COLORS = Set.of("#252525", "#ed00df", "#e53935", "#1565c0");
    private final ProductWorkSpecMapper mapper;
    private final SysAttachmentMapper attachments;
    private final IProductService products;
    private final ObjectMapper json;
    private final ProductWorkSpecChangeService changeService;
    private final SysOperLogMapper operLogs;
    private static final List<ProcessCategoryEnum> PRINT_GROUPS = List.of(
            ProcessCategoryEnum.PANEL, ProcessCategoryEnum.UP_LINE, ProcessCategoryEnum.DOWN_LINE);

    public ObjectNode get(Long id) {
        return response(id, mapper.read(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode save(Long id, ProductWorkSpecDTO dto) {
        Map<String, Object> row = lock(id);
        String oldRaw = raw(row);
        checkRevision(dto.getRevision(), oldRaw);
        validate(id, dto);
        ObjectNode next = parse(json.valueToTree(dto).toString());
        next.remove("revision");
        ObjectNode oldContent = parse(oldRaw).deepCopy();
        oldContent.remove(java.util.List.of("confirmedBy", "confirmedAt", "confirmedSource"));
        // 整组备注由专用入口维护；旧客户端保存其他规范内容也不能覆盖它。
        if (oldContent.has("printRemarks")) next.set("printRemarks", oldContent.get("printRemarks").deepCopy());
        // 分色检查表同样由专用入口维护；通用保存不得覆盖或清空。
        if (oldContent.has("colorCheck")) next.set("colorCheck", oldContent.get("colorCheck").deepCopy());
        changeService.attachSources(id, oldContent, next);
        if (oldContent.equals(next)) return response(id, oldRaw);
        mapper.write(id, next.toString());
        return response(id, mapper.read(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode savePrintRemarks(Long id, PrintSpecRemarksDTO dto) {
        String oldRaw = raw(lock(id));
        checkRevision(dto.getRevision(), oldRaw);
        if (dto.getRemarks().keySet().stream().anyMatch(key -> PRINT_GROUPS.stream().noneMatch(group -> group.getCode().equals(key))))
            throw new BusinessException("仅支持面板、上线、下线的整组印刷备注");
        ObjectNode next = parse(oldRaw);
        ObjectNode remarks = json.createObjectNode();
        List<String> changes = new ArrayList<>();
        var fields = json.createArrayNode();
        for (ProcessCategoryEnum group : PRINT_GROUPS) {
            String key = group.getCode();
            String before = next.path("printRemarks").path(key).asText("");
            String after = dto.getRemarks().getOrDefault(key, "");
            remarks.put(key, after);
            if (!Objects.equals(before, after)) {
                String label = group.getLabel() + "印刷整组备注";
                changes.add(label + "：" + (before.isEmpty() ? "（空）" : before) + " → " + (after.isEmpty() ? "（空）" : after));
                fields.addObject().put("field", "printRemarks." + key).put("label", label).put("before", before).put("after", after);
            }
        }
        if (changes.isEmpty()) return response(id, oldRaw);
        next.set("printRemarks", remarks);
        next.remove(List.of("confirmedBy", "confirmedAt", "confirmedSource"));
        mapper.write(id, next.toString());
        SysOperLog log = new SysOperLog();
        log.setModule("印刷规范");
        log.setBusinessType(BusinessType.UPDATE.getCode());
        log.setBizType("product");
        log.setBizId(id.toString());
        log.setAction(LogActions.PRINT_SPEC_REMARKS_EDIT);
        log.setOperUrl("/product/" + id + "/work-spec/print-remarks");
        log.setOperParam("修改 " + changes.size() + " 组印刷备注");
        ObjectNode detail = parse(OperLogDetailBuilder.changes(changes));
        detail.set("fields", fields);
        log.setDetail(detail.toString());
        log.setUserId(SecurityUtils.getUserId());
        log.setUsername(SecurityUtils.getUsername());
        log.setRealName(SecurityUtils.getRealName());
        log.setCreateTime(LocalDateTime.now());
        log.setStatus(YesNoEnum.YES.getCode());
        // 与备注同事务，写日志失败即回滚，不走可丢失的异步通道。
        operLogs.insert(log);
        return response(id, mapper.read(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode saveColorCheck(Long id, ColorCheckDTO dto) {
        String oldRaw = raw(lock(id));
        checkRevision(dto.getRevision(), oldRaw);
        for (String key : dto.getItems().keySet())
            if (!ColorCheckItemEnum.codes().contains(key)) throw new BusinessException("未知的分色检查项");
        ObjectNode next = parse(oldRaw);
        ObjectNode before = next.path("colorCheck").isObject() ? (ObjectNode) next.path("colorCheck") : json.createObjectNode();
        ObjectNode items = json.createObjectNode();
        List<String> changes = new ArrayList<>();
        var fields = json.createArrayNode();
        String operator = SecurityUtils.getUsername();
        String now = LocalDateTime.now().toString();
        // 固定按声明顺序遍历，保证无修改时不产生伪变更。
        for (Map.Entry<String, String> entry : ColorCheckItemEnum.labels().entrySet()) {
            String key = entry.getKey();
            ColorCheckDTO.Item input = dto.getItems().get(key);
            String result = input == null || input.getResult() == null ? "" : input.getResult().trim();
            String reason = input == null || input.getReason() == null ? "" : input.getReason().trim();
            if (!ColorCheckResultEnum.isValid(result)) throw new BusinessException("分色检查结论不合法");
            if (ColorCheckResultEnum.INCORRECT.getCode().equals(result) && reason.isEmpty())
                throw new BusinessException("选择“错误”的检查项必须填写原因");
            if (!ColorCheckResultEnum.INCORRECT.getCode().equals(result)) reason = "";
            String oldResult = before.path("items").path(key).path("result").asText("");
            String oldReason = before.path("items").path(key).path("reason").asText("");
            ObjectNode node = items.putObject(key);
            node.put("result", result);
            node.put("reason", reason);
            if (!result.isEmpty()) { node.put("by", operator); node.put("at", now); }
            if (!Objects.equals(oldResult, result) || !Objects.equals(oldReason, reason)) {
                String label = entry.getValue();
                changes.add(label + "：" + colorSummary(oldResult, oldReason) + " → " + colorSummary(result, reason));
                fields.addObject().put("field", "colorCheck." + key).put("label", label)
                        .put("before", colorSummary(oldResult, oldReason)).put("after", colorSummary(result, reason));
            }
        }
        if (changes.isEmpty()) return response(id, oldRaw);
        ObjectNode colorCheck = json.createObjectNode();
        colorCheck.set("items", items);
        colorCheck.put("updatedBy", operator);
        colorCheck.put("updatedAt", now);
        next.set("colorCheck", colorCheck);
        next.remove(List.of("confirmedBy", "confirmedAt", "confirmedSource"));
        mapper.write(id, next.toString());
        SysOperLog log = new SysOperLog();
        log.setModule("规范分色检查表");
        log.setBusinessType(BusinessType.UPDATE.getCode());
        log.setBizType("product");
        log.setBizId(id.toString());
        log.setAction(LogActions.WORK_SPEC_COLOR_CHECK_EDIT);
        log.setOperUrl("/product/" + id + "/work-spec/color-check");
        log.setOperParam("修改 " + changes.size() + " 项分色检查");
        ObjectNode detail = parse(OperLogDetailBuilder.changes(changes));
        detail.set("fields", fields);
        log.setDetail(detail.toString());
        log.setUserId(SecurityUtils.getUserId());
        log.setUsername(SecurityUtils.getUsername());
        log.setRealName(SecurityUtils.getRealName());
        log.setCreateTime(LocalDateTime.now());
        log.setStatus(YesNoEnum.YES.getCode());
        // 与检查结果同事务，写日志失败即回滚，不走可丢失的异步通道。
        operLogs.insert(log);
        return response(id, mapper.read(id));
    }

    private String colorSummary(String result, String reason) {
        String text = ColorCheckResultEnum.label(result);
        return reason.isEmpty() ? text : text + "：" + reason;
    }

    public ObjectNode printRemarksHistory(Long id, Long before) {
        if (mapper.exists(id) == 0) throw new BusinessException("产品不存在");
        var rows = mapper.printRemarksHistory(id.toString(), before, LogActions.PRINT_SPEC_REMARKS_EDIT, YesNoEnum.YES.getCode());
        ObjectNode result = json.createObjectNode();
        var items = result.putArray("items");
        for (var row : rows.subList(0, Math.min(50, rows.size()))) {
            ObjectNode item = items.addObject();
            item.put("id", ((Number) row.get("id")).longValue());
            item.put("operatorName", Objects.toString(row.get("operatorName"), ""));
            item.put("changedAt", Objects.toString(row.get("changedAt"), ""));
            item.set("fields", parse(Objects.toString(row.get("detail"), "{}")).path("fields"));
        }
        if (rows.size() > 50) result.put("nextBefore", ((Number) rows.get(49).get("id")).longValue());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode confirm(Long id, String revision, String sourceRevision) {
        Map<String, Object> row = lock(id);
        String oldRaw = raw(row);
        checkRevision(revision, oldRaw);
        ObjectNode data = parse(oldRaw);
        if (!Objects.equals(sourceRevision, sourceFingerprint(id, data)))
            throw new BusinessException("材料、流程或结构图已变化，请重新加载后确认");
        data.put("confirmedBy", SecurityUtils.getUsername());
        data.put("confirmedAt", LocalDateTime.now().toString());
        data.put("confirmedSource", sourceFingerprint(id, data));
        mapper.write(id, data.toString());
        return response(id, mapper.read(id));
    }

    private Map<String, Object> lock(Long id) {
        Map<String, Object> row = mapper.lock(id);
        if (row == null) throw new BusinessException("产品不存在");
        return row;
    }
    private String raw(Map<String, Object> row) {
        Object value = row.get("work_spec_json");
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return value == null ? null : value.toString();
    }
    private void checkRevision(String revision, String raw) {
        if (!Objects.equals(revision, digest(raw == null ? "" : raw)))
            throw new BusinessException("工程规范已被其他入口修改，请重新加载后编辑");
    }
    private ProductFullVO requireProduct(Long id) {
        if (mapper.exists(id) == 0) throw new BusinessException("产品不存在");
        ProductFullVO product = products.getFullProductDetail(id);
        if (product == null || product.getProduct() == null) throw new BusinessException("产品不存在");
        return product;
    }
    private ObjectNode response(Long id, String raw) {
        ObjectNode data = parse(raw);
        data.put("revision", digest(raw == null ? "" : raw));
        ObjectNode sources = sourceData(id, data);
        String sourceRevision = digest(sources.toString());
        data.put("sourceRevision", sourceRevision);
        boolean approved = data.hasNonNull("confirmedSource")
                && data.path("confirmedSource").asText().equals(sourceRevision);
        data.put("approved", approved);
        data.remove("confirmedSource");
        ObjectNode publicSources = sources.deepCopy();
        publicSources.remove(java.util.List.of("structure", "structureMissing"));
        data.set("sourceData", publicSources);
        if (!approved) data.remove(java.util.List.of("confirmedBy", "confirmedAt"));
        return data;
    }
    private ObjectNode parse(String raw) {
        try {
            if (raw == null || raw.isBlank()) return json.createObjectNode();
            JsonNode node = json.readTree(raw);
            if (!(node instanceof ObjectNode object)) throw new BusinessException("工程规范数据格式错误");
            return object;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("工程规范数据解析失败，请联系管理员");
        }
    }
    private void validate(Long id, ProductWorkSpecDTO dto) {
        if (dto.getEmboss() != null) dto.getEmboss().forEach((key, value) -> {
            if (!EMBOSS_KEYS.contains(key)) throw new BusinessException("未知凹凸参数");
            if (value != null && !value.isBlank() && (!value.matches("[+-]?\\d{1,12}(\\.\\d{1,6})?") || value.length() > 20))
                throw new BusinessException("凹凸参数请填写数值，未使用的项目可留空");
        });
        color(dto.getRequirementsColor());
        if (dto.getChanges() != null) dto.getChanges().forEach(change -> {
            if (change == null) throw new BusinessException("变更记录格式错误");
            color(change.getColor());
        });
        if (dto.getStructureFileId() != null) structure(id, dto.getStructureFileId());
    }
    private void color(String value) {
        if (value != null && !value.isBlank() && !COLORS.contains(value)) throw new BusinessException("不支持的文字颜色");
    }
    private SysAttachment structure(Long id, Long fileId) {
        SysAttachment file = attachments.selectById(fileId);
        if (file == null || !"product".equals(file.getBizType()) || !Objects.equals(id, file.getBizId()) || !"结构图".equals(file.getCategory()))
            throw new BusinessException("请选择该产品已有的结构图");
        String name = file.getFileName() == null ? "" : file.getFileName().toLowerCase(java.util.Locale.ROOT);
        String mime = file.getFileType() == null ? "" : file.getFileType();
        if (!mime.startsWith("image/") && !mime.contains("pdf") && !name.matches(".*\\.(png|jpg|jpeg|webp|gif|bmp|svg|pdf)$"))
            throw new BusinessException("结构图请选择可预览的图片或PDF打印件");
        return file;
    }
    private String sourceFingerprint(Long id, ObjectNode data) {
        return digest(sourceData(id, data).toString());
    }
    /** 页面与确认指纹使用同一份引用数据；仅返回纸张需要的产品字段，避免带出价格。 */
    private ObjectNode sourceData(Long id, ObjectNode data) {
        ProductFullVO full = requireProduct(id);
        ObjectNode sources = json.createObjectNode();
        ObjectNode product = json.createObjectNode();
        product.put("productId", full.getProduct().getProductId());
        product.put("productCode", full.getProduct().getProductCode());
        product.put("productName", full.getProduct().getProductName());
        product.put("customerName", full.getProduct().getCustomerName());
        sources.set("product", product);
        sources.set("bom", json.valueToTree(full.getBom()));
        sources.set("routing", json.valueToTree(full.getRouting()));
        if (data.hasNonNull("structureFileId")) {
            SysAttachment file = attachments.selectById(data.path("structureFileId").asLong());
            if (file == null) sources.put("structureMissing", true);
            else sources.set("structure", json.valueToTree(file));
        }
        return sources;
    }
    private String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
