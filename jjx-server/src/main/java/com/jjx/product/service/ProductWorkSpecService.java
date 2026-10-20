package com.jjx.product.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.dto.ProductWorkSpecDTO;
import com.jjx.product.domain.dto.ProductWorkSpecDraftDTO;
import com.jjx.system.service.AttachmentRefService;
import com.jjx.system.service.AttachmentAccessService;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.dto.AttachmentRefMetadataDTO;
import com.jjx.common.constant.UserConstants;
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
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.domain.entity.SysAttachmentRef;
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
    @org.springframework.beans.factory.annotation.Autowired private AttachmentRefService draftRefs;
    @org.springframework.beans.factory.annotation.Autowired private AttachmentAccessService draftAccess;
    private static final Set<String> EMBOSS_KEYS = Set.of("setupHeight", "requiredHeight", "upperTemperature", "lowerTemperature", "pressTime", "holdTime");
    private static final Set<String> COLORS = Set.of("#252525", "#ed00df", "#e53935", "#1565c0");
    private final ProductWorkSpecMapper mapper;
    private final SysAttachmentMapper attachments;
    private final SysAttachmentRefMapper attachmentRefs;
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
        requireOwner(oldContent);
        oldContent.remove(java.util.List.of("confirmedBy", "confirmedAt", "confirmedSource"));
        // 整组备注由专用入口维护；旧客户端保存其他规范内容也不能覆盖它。
        if (oldContent.has("printRemarks")) next.set("printRemarks", oldContent.get("printRemarks").deepCopy());
        // 分色检查表同样由专用入口维护；通用保存不得覆盖或清空。
        if (oldContent.has("colorCheck")) next.set("colorCheck", oldContent.get("colorCheck").deepCopy());
        for (String key : List.of("draftManaged", "draftAttachments", "preparedById", "preparedByName", "basedOnVersionId", "finalizedVersionId", "finalizedFromRevision"))
            if (oldContent.has(key)) next.set(key, oldContent.get(key).deepCopy());
        changeService.attachSources(id, oldContent, next);
        if (sameContent(oldContent, next)) return response(id, oldRaw);
        beginEdit(oldContent, next);
        mapper.write(id, next.toString());
        recordOperation(id, "保存草稿", oldContent, next);
        return response(id, mapper.read(id));
    }

    public List<Map<String, Object>> responsibleUsers() { return mapper.responsibleUsers(UserConstants.USER_STATUS_NORMAL); }

    public ObjectNode draftHistory(Long id, Long before) {
        if (mapper.exists(id) == 0) throw new BusinessException("产品不存在");
        var rows = mapper.draftHistory(id.toString(), before, YesNoEnum.YES.getCode());
        ObjectNode result = json.createObjectNode();
        result.set("items", json.valueToTree(rows.subList(0, Math.min(50, rows.size()))));
        if (rows.size() > 50) result.put("nextBefore", ((Number) rows.get(49).get("id")).longValue());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode saveDraft(Long id, ProductWorkSpecDraftDTO input) {
        String previous = raw(lock(id));
        checkRevision(input.getRevision(), previous);
        ObjectNode old = parse(previous);
        ObjectNode next = input.getSpec() == null ? old.deepCopy() : json.valueToTree(input.getSpec());
        next.remove(List.of("revision", "confirmedBy", "confirmedAt", "confirmedSource"));
        for (String key : List.of("printRemarks", "colorCheck", "basedOnVersionId", "finalizedVersionId", "finalizedFromRevision"))
            if (old.has(key)) next.set(key, old.get(key).deepCopy());
        next.put("draftManaged", true);
        // 负责人即当前制定任务：首次保存默认当前用户；已存在负责人时只有本人或工程主管可改派（dev-20261020-013）。
        Long actor = SecurityUtils.getUserId();
        Long existingOwner = old.path("draftManaged").asBoolean() && old.hasNonNull("preparedById")
            ? old.path("preparedById").asLong() : null;
        Long requestedOwner = input.getPreparedById();
        boolean ownerChanged = existingOwner != null && requestedOwner != null
            && !Objects.equals(requestedOwner, existingOwner);
        if (ownerChanged && !Objects.equals(existingOwner, actor) && !canAssignOwner())
            throw new BusinessException("只有当前制定负责人或工程主管可以变更负责人");
        if (ownerChanged && !Boolean.TRUE.equals(input.getOwnerTransferConfirmed()))
            throw new BusinessException("变更负责人需要确认：由 " + ownerLabel(old) + " 改为新负责人");
        if (existingOwner != null && !ownerChanged && !Objects.equals(existingOwner, actor))
            throw new BusinessException(readOnlyMessage(old));
        if (existingOwner == null && requestedOwner != null && !Objects.equals(requestedOwner, actor) && !canAssignOwner())
            throw new BusinessException("首次保存只能以当前用户为制定负责人；如需指派他人，请由工程主管操作");
        Long owner = requestedOwner != null ? requestedOwner
            : existingOwner != null ? existingOwner : actor;
        Map<String, Object> user = responsibleUsers().stream().filter(row -> Objects.equals(((Number) row.get("id")).longValue(), owner))
            .findFirst().orElseThrow(() -> new BusinessException("请选择有效的编制负责人"));
        next.put("preparedById", owner); next.put("preparedByName", Objects.toString(user.get("name"), ""));
        if (input.getAttachments() == null) {
            if (!old.path("draftManaged").asBoolean()) throw new BusinessException("首次保存请提交完整附件清单");
            next.set("draftAttachments", old.path("draftAttachments").deepCopy());
        } else {
            var manifest = next.putArray("draftAttachments");
            Set<Long> ids = new java.util.HashSet<>();
            for (var item : input.getAttachments()) {
                if (item == null || item.getAttachmentId() == null || !ids.add(item.getAttachmentId())) throw new BusinessException("附件清单存在空项或重复文件");
                if (mapper.engineeringType(item.getDocumentType()) == 0) throw new BusinessException("工程图集只能使用工程技术资料类型");
                SysAttachment file = attachments.selectActiveForUpdate(item.getAttachmentId());
                if (file == null) throw new BusinessException("附件不存在或已删除");
                draftAccess.checkRead(file);
                if (Boolean.TRUE.equals(item.getPrint()) && !previewable(file)) throw new BusinessException("打印清单只能选择图片或PDF");
                AttachmentRefDTO ref = new AttachmentRefDTO();
                ref.setAttachmentId(file.getId()); ref.setSourceType("product"); ref.setSourceId(id); ref.setProductId(id); ref.setDocumentType(item.getDocumentType());
                Long refId = draftRefs.create(ref);
                AttachmentRefMetadataDTO meta = new AttachmentRefMetadataDTO();
                meta.setDocumentType(item.getDocumentType()); meta.setDisplayName(item.getDisplayName()); meta.setRemark(item.getRemark()); meta.setSortOrder(manifest.size());
                draftRefs.update(refId, meta);
                ObjectNode entry = manifest.addObject();
                entry.put("attachmentId", file.getId()); entry.put("documentType", item.getDocumentType());
                entry.put("displayName", item.getDisplayName() == null || item.getDisplayName().isBlank() ? file.getFileName() : item.getDisplayName());
                entry.put("remark", Objects.toString(item.getRemark(), "")); entry.put("sortOrder", manifest.size() - 1);
                entry.put("print", item.getPrint() == null ? previewable(file) : item.getPrint());
                entry.put("sha256", Objects.toString(file.getSha256(), ""));
            }
            for (JsonNode item : old.path("draftAttachments")) {
                long fileId = item.path("attachmentId").asLong();
                if (!ids.contains(fileId)) draftRefs.detachWorkSpecFile(id, fileId);
            }
            if (next.hasNonNull("structureFileId") && !ids.contains(next.path("structureFileId").asLong())) next.putNull("structureFileId");
        }
        if (input.getSpec() != null) {
            input.getSpec().setStructureFileId(next.hasNonNull("structureFileId") ? next.path("structureFileId").asLong() : null);
            validate(id, input.getSpec());
            changeService.attachSources(id, old, next);
        }
        if (!sameContent(old, next)) {
            beginEdit(old, next);
            mapper.write(id, next.toString());
            boolean ownerMoved = existingOwner != null
                && !Objects.equals(old.path("preparedById").asText(), next.path("preparedById").asText());
            recordOperation(id, ownerMoved ? "变更负责人：" + ownerLabel(old) + "→" + ownerLabel(next) : "保存草稿", old, next);
        }
        return response(id, mapper.read(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode upgradeDraft(Long id, ProductWorkSpecDraftDTO input) {
        String previous = raw(lock(id)); checkRevision(input.getRevision(), previous);
        if (input.getBaseVersionId() == null || !Objects.equals(id, mapper.versionProduct(input.getBaseVersionId()))) throw new BusinessException("请选择本产品的定版版本");
        ObjectNode snapshot = parse(mapper.versionSnapshot(input.getBaseVersionId()));
        if (!snapshot.path("spec").isObject() || !snapshot.path("files").isArray()) throw new BusinessException("该版本缺少完整快照，不能复制为草稿");
        ObjectNode source = ((ObjectNode) snapshot.path("spec")).deepCopy();
        source.remove(List.of("revision", "sourceRevision", "sourceData", "draftFiles", "approved", "confirmedAt", "confirmedBy", "confirmedSource", "finalizedVersionId", "finalizedFromRevision"));
        // 已保存草稿包含修订内容时不覆盖；调用方需先处理现有草稿。
        ObjectNode old = parse(previous);
        if (old.path("draftManaged").asBoolean() && !old.hasNonNull("finalizedVersionId")) throw new BusinessException("当前已有未定版草稿，请先处理当前草稿");
        if (old.path("draftManaged").asBoolean() && old.hasNonNull("preparedById")
                && !Objects.equals(old.path("preparedById").asLong(), SecurityUtils.getUserId()) && !canAssignOwner())
            throw new BusinessException("当前作业规范由 " + ownerLabel(old) + " 制定，只有负责人或工程主管可以升级");
        source.put("draftManaged", true); source.put("basedOnVersionId", input.getBaseVersionId());
        if (!source.path("draftAttachments").isArray()) throw new BusinessException("该版本缺少统一附件清单，不能复制为草稿");
        Set<Long> selected = new java.util.HashSet<>();
        for (JsonNode entry : source.path("draftAttachments")) {
            long attachmentId = entry.path("attachmentId").asLong();
            SysAttachment file = attachments.selectActiveForUpdate(attachmentId);
            if (file == null || !selected.add(attachmentId)) throw new BusinessException("原定版文件缺失或清单重复");
            draftAccess.checkRead(file);
            AttachmentRefDTO ref = new AttachmentRefDTO(); ref.setAttachmentId(attachmentId);
            ref.setSourceType("product"); ref.setSourceId(id); ref.setProductId(id); ref.setDocumentType(entry.path("documentType").asText());
            Long refId = draftRefs.create(ref);
            AttachmentRefMetadataDTO meta = new AttachmentRefMetadataDTO();
            meta.setDocumentType(entry.path("documentType").asText()); meta.setDisplayName(entry.path("displayName").asText());
            meta.setRemark(entry.path("remark").asText("")); meta.setSortOrder(entry.path("sortOrder").asInt());
            draftRefs.update(refId, meta);
        }
        for (JsonNode entry : old.path("draftAttachments"))
            if (!selected.contains(entry.path("attachmentId").asLong())) draftRefs.detachWorkSpecFile(id, entry.path("attachmentId").asLong());
        mapper.write(id, source.toString());
        recordOperation(id, "从旧版升级", old, source);
        return response(id, mapper.read(id));
    }

    private boolean previewable(SysAttachment file) {
        return Objects.toString(file.getFileType(), "").startsWith("image/") || Objects.toString(file.getFileName(), "").toLowerCase(java.util.Locale.ROOT).matches(".*\\.(pdf|png|jpg|jpeg|webp|gif|bmp|svg)$");
    }
    private void beginEdit(ObjectNode old, ObjectNode next) {
        if (old.hasNonNull("finalizedVersionId")) next.set("basedOnVersionId", old.get("finalizedVersionId"));
        next.remove(List.of("finalizedVersionId", "finalizedFromRevision"));
    }
    private boolean sameContent(ObjectNode before, ObjectNode after) {
        // JSON落库读取会把小范围Long还原为Int；按归一后的值比较，避免无改动生成流水。
        return parse(before.toString()).equals(parse(after.toString()));
    }
    public void recordOperation(Long id, String action, ObjectNode before, ObjectNode after) {
        SysOperLog event = new SysOperLog();
        event.setModule("作业规范版本"); event.setBizType("product"); event.setBizId(id.toString());
        event.setBusinessType(BusinessType.UPDATE.getCode()); event.setAction(action);
        event.setUserId(SecurityUtils.getUserId()); event.setUsername(SecurityUtils.getUsername()); event.setRealName(SecurityUtils.getDisplayName());
        event.setStatus(YesNoEnum.YES.getCode()); event.setCreateTime(LocalDateTime.now());
        ObjectNode detail = json.createObjectNode();
        detail.put("beforeRevision", digest(before.toString())); detail.put("afterRevision", digest(after.toString()));
        detail.put("preparedByBefore", before.path("preparedByName").asText(""));
        detail.put("preparedByAfter", after.path("preparedByName").asText(""));
        if (after.has("finalizedVersionId")) detail.set("versionId", after.get("finalizedVersionId"));
        if (after.has("versionId")) detail.set("versionId", after.get("versionId"));
        if (after.has("versionNo")) detail.set("versionNo", after.get("versionNo"));
        if (after.has("basedOnVersionId")) detail.set("basedOnVersionId", after.get("basedOnVersionId"));
        if (after.has("status")) detail.set("status", after.get("status"));
        var changed = detail.putArray("changedFields");
        java.util.Set<String> keys = new java.util.TreeSet<>(); before.fieldNames().forEachRemaining(keys::add); after.fieldNames().forEachRemaining(keys::add);
        keys.stream().filter(key -> !Objects.equals(before.get(key), after.get(key))).forEach(changed::add);
        List<Long> oldIds = new ArrayList<>(), newIds = new ArrayList<>();
        before.path("draftAttachments").forEach(item -> oldIds.add(item.path("attachmentId").asLong()));
        after.path("draftAttachments").forEach(item -> newIds.add(item.path("attachmentId").asLong()));
        detail.set("added", json.valueToTree(newIds.stream().filter(file -> !oldIds.contains(file)).toList()));
        detail.set("removed", json.valueToTree(oldIds.stream().filter(file -> !newIds.contains(file)).toList()));
        detail.put("orderChanged", !oldIds.equals(newIds));
        String beforeOwner = before.path("preparedByName").asText("");
        String afterOwner = after.path("preparedByName").asText("");
        if (!Objects.equals(beforeOwner, afterOwner)) {
            var changes = detail.putArray("changes");
            changes.add("制定负责人：" + (beforeOwner.isBlank() ? "未指定" : beforeOwner)
                + " → " + (afterOwner.isBlank() ? "未指定" : afterOwner));
        }
        event.setOperParam(action + "：附件" + newIds.size() + "个"); event.setDetail(detail.toString());
        operLogs.insert(event);
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode savePrintRemarks(Long id, PrintSpecRemarksDTO dto) {
        String oldRaw = raw(lock(id));
        checkRevision(dto.getRevision(), oldRaw);
        requireOwner(parse(oldRaw));
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
        beginEdit(parse(oldRaw), next);
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
        requireOwner(parse(oldRaw));
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
        beginEdit(parse(oldRaw), next);
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
        beginEdit(parse(oldRaw), data);
        mapper.write(id, data.toString());
        return response(id, mapper.read(id));
    }

    /** 制定任务锁：草稿已有负责人时，只有负责人可编辑；无草稿任务/未指定负责人不受限。 */
    private void requireOwner(ObjectNode data) {
        if (!data.path("draftManaged").asBoolean() || !data.hasNonNull("preparedById")) return;
        if (Objects.equals(data.path("preparedById").asLong(), SecurityUtils.getUserId())) return;
        throw new BusinessException(readOnlyMessage(data));
    }

    /** 管理员 / 工程主管（engineering:all）可改派负责人；换人后原负责人立即只读。 */
    public boolean canAssignOwner() {
        try {
            return SecurityUtils.hasRole("engineering:all") || SecurityUtils.hasRole("admin")
                || SecurityUtils.hasPermission("*:*:*") || SecurityUtils.hasPermission("*");
        } catch (Exception e) {
            return false;
        }
    }

    private String ownerLabel(ObjectNode data) {
        String name = data.path("preparedByName").asText("");
        return name.isBlank() ? "未指定" : name;
    }

    private String readOnlyMessage(ObjectNode data) {
        return "当前作业规范由 " + ownerLabel(data) + " 制定，你只能查看；如需修改请联系负责人或由工程主管改派";
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
        if (data.path("draftManaged").asBoolean()) {
            Map<Long, Map<String, Object>> available = new java.util.HashMap<>();
            for (var file : mapper.draftFiles(id)) available.put(((Number) file.get("attachmentId")).longValue(), file);
            var files = data.putArray("draftFiles");
            for (JsonNode entry : data.path("draftAttachments")) {
                ObjectNode file = available.containsKey(entry.path("attachmentId").asLong()) ? json.valueToTree(available.get(entry.path("attachmentId").asLong())) : json.createObjectNode();
                file.setAll((ObjectNode) entry); files.add(file);
            }
        } else data.set("draftFiles", json.valueToTree(mapper.draftFiles(id)));
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
        JsonNode dieLocation = dto.getDieLocation();
        if (dieLocation != null && !dieLocation.isNull()) {
            if (dieLocation.isTextual()) {
                if (dieLocation.textValue().length() > 500)
                    throw new BusinessException("刀模位置旧版文本不能超过500字");
            } else if (dieLocation.isObject()) {
                if (dieLocation.size() > 1000)
                    throw new BusinessException("刀模位置记录不能超过1000条");
                var fields = dieLocation.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    if (entry.getKey().isBlank() || entry.getKey().length() > 200
                            || !entry.getValue().isTextual() || entry.getValue().textValue().length() > 100)
                        throw new BusinessException("刀模位置记录格式错误或内容超过长度限制");
                }
            } else {
                throw new BusinessException("刀模位置只能是旧版文本或按材料行填写的对象");
            }
        }
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
        List<SysAttachmentRef> refs = attachmentRefs.selectList(new QueryWrapper<SysAttachmentRef>()
                .eq("attachment_id", fileId).eq("source_type", "product").eq("source_id", id)
                .eq("deleted", false));
        if (file == null || refs.isEmpty() || (!"结构图".equals(file.getCategory())
                && refs.stream().noneMatch(ref -> "STRUCTURE_DRAWING".equals(ref.getDocumentType()))))
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
