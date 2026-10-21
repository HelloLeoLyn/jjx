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
import com.jjx.system.service.AttachmentRefOperationLogRecorder;
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
    @org.springframework.beans.factory.annotation.Autowired private com.jjx.system.service.AttachmentDocumentTypeService docTypes;
    @org.springframework.beans.factory.annotation.Autowired private AttachmentAccessService draftAccess;
    private static final Set<String> EMBOSS_KEYS = Set.of("setupHeight", "requiredHeight", "upperTemperature", "lowerTemperature", "pressTime", "holdTime");
    private static final List<String> EMBOSS_ORDER = List.of("setupHeight", "requiredHeight", "upperTemperature", "lowerTemperature", "pressTime", "holdTime");
    private static final Map<String, String> EMBOSS_LABELS = Map.of(
        "setupHeight", "凹凸调机高度", "requiredHeight", "凹凸要求高度",
        "upperTemperature", "凹凸上模温度", "lowerTemperature", "凹凸下模温度",
        "pressTime", "凹凸下压时间", "holdTime", "凹凸保持时间");
    private static final Map<String, String> COLOR_LABELS = Map.of(
        "#252525", "黑色", "#ed00df", "品红", "#e53935", "红色", "#1565c0", "蓝色");
    private static final Set<String> COLORS = Set.of("#252525", "#ed00df", "#e53935", "#1565c0");
    /** 统一业务模块名：作业规范的保存/负责人变更/升级/定版等流水都归到这里（dev-20261020-015）。 */
    private static final String WORK_SPEC_MODULE = "产品作业规范";
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

    public List<Map<String, Object>> responsibleUsers() {
        var users = mapper.responsibleUsers(UserConstants.USER_STATUS_NORMAL);
        if (users.isEmpty()) throw new BusinessException("暂无可选择的工程人员");
        return users;
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode claim(Long id) {
        ObjectNode before = parse(raw(lock(id)));
        Long actor = SecurityUtils.getUserId();
        String name = responsibleName(actor);
        if (mapper.claim(id, actor, name) != 1)
            throw new BusinessException("已被他人制定或已定版，请重新加载作业规范");
        ObjectNode after = parse(mapper.read(id));
        recordOperation(id, "制定规范", before, after);
        return response(id, mapper.read(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode transfer(Long id, ProductWorkSpecDraftDTO input) {
        String previous = raw(lock(id));
        checkRevision(input.getRevision(), previous);
        ObjectNode before = parse(previous);
        if (!before.hasNonNull("preparedById") || before.hasNonNull("finalizedVersionId"))
            throw new BusinessException("只有制定中的作业规范可以转交/改派");
        boolean owner = Objects.equals(before.path("preparedById").asLong(), SecurityUtils.getUserId());
        if (!owner && !canAssignOwner()) throw new BusinessException("只有当前制定负责人或工程主管可以转交/改派");
        if (!Boolean.TRUE.equals(input.getOwnerTransferConfirmed())) throw new BusinessException("转交/改派负责人需要确认");
        Long target = input.getPreparedById();
        String name = responsibleName(target);
        if (Objects.equals(target, before.path("preparedById").asLong())) throw new BusinessException("请选择其他负责人");
        ObjectNode after = before.deepCopy();
        after.put("preparedById", target); after.put("preparedByName", name); after.put("draftManaged", true);
        mapper.write(id, after.toString());
        recordOperation(id, (owner ? "转交：" : "改派：") + ownerLabel(before) + "→" + name, before, after);
        return response(id, mapper.read(id));
    }

    private String responsibleName(Long id) {
        return mapper.responsibleUsers(UserConstants.USER_STATUS_NORMAL).stream()
            .filter(row -> Objects.equals(((Number) row.get("id")).longValue(), id))
            .map(row -> Objects.toString(row.get("name"), "")).findFirst()
            .orElseThrow(() -> new BusinessException("制定负责人必须是工程人员"));
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode saveDraft(Long id, ProductWorkSpecDraftDTO input) {
        // 本次保存创建的附件引用日志跟随作业规范模块，不改产品模块其它附件日志。
        return AttachmentRefOperationLogRecorder.withModule(WORK_SPEC_MODULE, () -> doSaveDraft(id, input));
    }

    private ObjectNode doSaveDraft(Long id, ProductWorkSpecDraftDTO input) {
        String previous = raw(lock(id));
        checkRevision(input.getRevision(), previous);
        ObjectNode old = parse(previous);
        ObjectNode next = input.getSpec() == null ? old.deepCopy() : json.valueToTree(input.getSpec());
        next.remove(List.of("revision", "confirmedBy", "confirmedAt", "confirmedSource"));
        for (String key : List.of("printRemarks", "colorCheck", "basedOnVersionId", "finalizedVersionId", "finalizedFromRevision"))
            if (old.has(key)) next.set(key, old.get(key).deepCopy());
        next.put("draftManaged", true);
        requireOwner(old);
        if (input.getPreparedById() != null && !Objects.equals(input.getPreparedById(), old.path("preparedById").asLong()))
            throw new BusinessException("请使用转交/改派操作变更负责人");
        next.set("preparedById", old.get("preparedById"));
        next.set("preparedByName", old.get("preparedByName"));
        if (input.getAttachments() == null) {
            if (!old.path("draftManaged").asBoolean()) throw new BusinessException("首次保存请提交完整附件清单");
            if (old.has("draftAttachments")) next.set("draftAttachments", old.get("draftAttachments").deepCopy());
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
                ref.setAttachmentId(file.getId()); ref.setSourceType("work_spec"); ref.setSourceId(id); ref.setProductId(id); ref.setDocumentType(item.getDocumentType());
                WorkSpecRef resolved = workSpecRef(id, ref);
                Long refId = resolved.id();
                AttachmentRefMetadataDTO meta = new AttachmentRefMetadataDTO();
                meta.setDocumentType(item.getDocumentType()); meta.setRemark(item.getRemark()); meta.setSortOrder(manifest.size());
                // 新建引用只由 create() 写「附件新增」；自动生成的展示名/排序不另记附件资料修改。
                if (resolved.created()) draftRefs.initAutoNamed(refId, meta);
                else draftRefs.updateAutoNamed(refId, meta);
                ObjectNode entry = manifest.addObject();
                entry.put("attachmentId", file.getId()); entry.put("documentType", item.getDocumentType());
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
        // 类型变更会重排同组名称，待本次引用操作全部完成后再固化最终展示名。
        syncDraftAttachmentNames(next, draftFilesById(id));
        if (input.getSpec() != null) {
            input.getSpec().setStructureFileId(next.hasNonNull("structureFileId") ? next.path("structureFileId").asLong() : null);
            validate(id, input.getSpec());
            changeService.attachSources(id, old, next);
        }
        if (!sameContent(old, next)) {
            beginEdit(old, next);
            mapper.write(id, next.toString());
            recordOperation(id, "保存草稿", old, next);
        }
        return response(id, mapper.read(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode upgradeDraft(Long id, ProductWorkSpecDraftDTO input) {
        return AttachmentRefOperationLogRecorder.withModule(WORK_SPEC_MODULE, () -> doUpgradeDraft(id, input));
    }

    private ObjectNode doUpgradeDraft(Long id, ProductWorkSpecDraftDTO input) {
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
            ref.setSourceType("work_spec"); ref.setSourceId(id); ref.setProductId(id); ref.setDocumentType(entry.path("documentType").asText());
            WorkSpecRef resolved = workSpecRef(id, ref);
            AttachmentRefMetadataDTO meta = new AttachmentRefMetadataDTO();
            meta.setDocumentType(entry.path("documentType").asText());
            meta.setRemark(entry.path("remark").asText("")); meta.setSortOrder(entry.path("sortOrder").asInt());
            if (resolved.created()) draftRefs.initAutoNamed(resolved.id(), meta);
            else draftRefs.updateAutoNamed(resolved.id(), meta);
        }
        for (JsonNode entry : old.path("draftAttachments"))
            if (!selected.contains(entry.path("attachmentId").asLong())) draftRefs.detachWorkSpecFile(id, entry.path("attachmentId").asLong());
        syncDraftAttachmentNames(source, draftFilesById(id));
        mapper.write(id, source.toString());
        recordOperation(id, "从旧版升级", old, source);
        return response(id, mapper.read(id));
    }

    private record WorkSpecRef(Long id, boolean created) {}

    private WorkSpecRef workSpecRef(Long productId, AttachmentRefDTO request) {
        List<SysAttachmentRef> existing = attachmentRefs.selectList(new QueryWrapper<SysAttachmentRef>()
            .eq("attachment_id", request.getAttachmentId()).in("source_type", "product", "work_spec")
            .eq("source_id", productId).eq("deleted", false).ne("ref_state", "draft")
            .orderByAsc("id"));
        return existing.isEmpty()
            ? new WorkSpecRef(draftRefs.create(request), true)
            : new WorkSpecRef(existing.get(0).getId(), false);
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
        event.setModule(WORK_SPEC_MODULE); event.setBizType("product"); event.setBizId(id.toString());
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
        // 兼容：保留字段名快照供技术排查，但人话差异单独写 changes，页面与流水都不再只显示字段名/附件数量。
        java.util.Set<String> keys = new java.util.TreeSet<>(); before.fieldNames().forEachRemaining(keys::add); after.fieldNames().forEachRemaining(keys::add);
        var changed = detail.putArray("changedFields");
        keys.stream().filter(key -> !Objects.equals(before.get(key), after.get(key))).forEach(changed::add);
        Map<Long, JsonNode> oldFiles = attachmentEntries(before), newFiles = attachmentEntries(after);
        Map<Long, String> names = attachmentNames(oldFiles, newFiles);
        List<Long> oldIds = new ArrayList<>(oldFiles.keySet()), newIds = new ArrayList<>(newFiles.keySet());
        detail.set("added", json.valueToTree(newIds.stream().filter(file -> !oldIds.contains(file)).toList()));
        detail.set("removed", json.valueToTree(oldIds.stream().filter(file -> !newIds.contains(file)).toList()));
        detail.set("addedFiles", json.valueToTree(newIds.stream().filter(file -> !oldIds.contains(file))
            .map(file -> attachmentName(names, file)).toList()));
        detail.set("removedFiles", json.valueToTree(oldIds.stream().filter(file -> !newIds.contains(file))
            .map(file -> attachmentName(names, file)).toList()));
        detail.put("orderChanged", !oldIds.equals(newIds));
        List<String> changes = humanChanges(id, before, after, oldFiles, newFiles, names);
        detail.set("changes", json.valueToTree(changes));
        event.setOperParam(summary(action, changes)); event.setDetail(detail.toString());
        operLogs.insert(event);
    }

    /** 保存草稿等事件写人话差异：文件名、刀模具体行、资料类型/展示名/备注、排序和负责人。 */
    private List<String> humanChanges(Long id, ObjectNode before, ObjectNode after,
                                      Map<Long, JsonNode> oldFiles, Map<Long, JsonNode> newFiles,
                                      Map<Long, String> names) {
        List<String> changes = new ArrayList<>();
        String beforeOwner = before.path("preparedByName").asText("");
        String afterOwner = after.path("preparedByName").asText("");
        if (!Objects.equals(beforeOwner, afterOwner))
            changes.add("制定负责人：" + blankText(beforeOwner) + " → " + blankText(afterOwner));

        Long oldStructure = numberOrNull(before.get("structureFileId"));
        Long newStructure = numberOrNull(after.get("structureFileId"));
        if (!Objects.equals(oldStructure, newStructure))
            changes.add("结构图：" + attachmentName(names, oldStructure) + " → " + attachmentName(names, newStructure));

        changes.addAll(dieLocationChanges(id, before, after));
        addSpecFieldChanges(changes, before, after);
        addAttachmentChanges(changes, oldFiles, newFiles, names);

        String oldVersion = textOf(before, "versionNo"), newVersion = textOf(after, "versionNo");
        if (!oldVersion.isBlank() && !oldVersion.equals(newVersion)) changes.add("版本号：" + oldVersion + " → " + blankText(newVersion));
        String oldStatus = textOf(before, "status"), newStatus = textOf(after, "status");
        if (!Objects.equals(oldStatus, newStatus)) changes.add("版本状态：" + blankText(oldStatus) + " → " + blankText(newStatus));
        boolean oldFinalized = before.hasNonNull("finalizedVersionId"), newFinalized = after.hasNonNull("finalizedVersionId");
        if (oldFinalized != newFinalized)
            changes.add(newFinalized ? "状态：制定中 → 已定版" : "状态：已定版 → 制定中");
        return changes;
    }

    private List<String> dieLocationChanges(Long id, ObjectNode before, ObjectNode after) {
        List<String> changes = new ArrayList<>();
        JsonNode oldDie = before.path("dieLocation"), newDie = after.path("dieLocation");
        if (oldDie.isObject() || newDie.isObject()) {
            Map<String, String> labels = dieRowLabels(id);
            java.util.Set<String> keys = new java.util.TreeSet<>();
            oldDie.fieldNames().forEachRemaining(keys::add);
            newDie.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                String oldValue = oldDie.path(key).asText(""), newValue = newDie.path(key).asText("");
                if (Objects.equals(oldValue, newValue)) continue;
                if (oldValue.isBlank() && newValue.isBlank()) continue;
                changes.add("刀模位置·" + labels.getOrDefault(key, key) + "：" + blankText(oldValue) + " → " + blankText(newValue));
            }
        }
        String oldText = textOf(before, "dieLocationText"), newText = textOf(after, "dieLocationText");
        if (!Objects.equals(oldText, newText))
            changes.add("刀模位置（旧版文本）：" + abbreviate(oldText) + " → " + abbreviate(newText));
        return changes;
    }

    private void addSpecFieldChanges(List<String> changes, ObjectNode before, ObjectNode after) {
        addTextChange(changes, "工程要求", textOf(before, "engineeringRequirements"), textOf(after, "engineeringRequirements"));
        String oldColor = textOf(before, "requirementsColor"), newColor = textOf(after, "requirementsColor");
        if (!Objects.equals(oldColor, newColor)) changes.add("要求文字颜色：" + colorLabel(oldColor) + " → " + colorLabel(newColor));
        addTextChange(changes, "发行单位", textOf(before, "issueUnit"), textOf(after, "issueUnit"));
        addTextChange(changes, "发行日期", textOf(before, "issueDate"), textOf(after, "issueDate"));
        for (String key : EMBOSS_ORDER) {
            String oldValue = before.path("emboss").path(key).asText("");
            String newValue = after.path("emboss").path(key).asText("");
            if (!Objects.equals(oldValue, newValue))
                changes.add("凹凸条件·" + EMBOSS_LABELS.get(key) + "：" + blankText(oldValue) + " → " + blankText(newValue));
        }
        String changeSummary = changeListDiff(before, after);
        if (!changeSummary.isBlank()) changes.add(changeSummary);
    }

    private void addAttachmentChanges(List<String> changes, Map<Long, JsonNode> oldFiles,
                                      Map<Long, JsonNode> newFiles, Map<Long, String> names) {
        List<Long> oldIds = new ArrayList<>(oldFiles.keySet()), newIds = new ArrayList<>(newFiles.keySet());
        List<Long> added = newIds.stream().filter(file -> !oldFiles.containsKey(file)).toList();
        List<Long> removed = oldIds.stream().filter(file -> !newFiles.containsKey(file)).toList();
        if (!added.isEmpty()) changes.add("附件新增：" + nameList(added, names));
        if (!removed.isEmpty()) changes.add("附件移除：" + nameList(removed, names));
        List<Long> oldCommon = oldIds.stream().filter(newFiles::containsKey).toList();
        List<Long> newCommon = newIds.stream().filter(oldFiles::containsKey).toList();
        if (!oldCommon.equals(newCommon))
            changes.add("附件顺序：" + nameList(oldCommon, names) + " → " + nameList(newCommon, names));
        for (Long fileId : newCommon) {
            JsonNode oldFile = oldFiles.get(fileId), newFile = newFiles.get(fileId);
            String name = attachmentName(names, fileId);
            String oldType = oldFile.path("documentType").asText(""), newType = newFile.path("documentType").asText("");
            if (!Objects.equals(oldType, newType))
                changes.add("附件「" + name + "」资料类型修改：" + typeLabel(oldType) + " → " + typeLabel(newType));
            String oldDisplay = oldFile.path("displayName").asText(""), newDisplay = newFile.path("displayName").asText("");
            if (Objects.equals(oldType, newType) && !Objects.equals(oldDisplay, newDisplay))
                changes.add("附件「" + name + "」展示名称：" + blankText(oldDisplay) + " → " + blankText(newDisplay));
            String oldRemark = oldFile.path("remark").asText(""), newRemark = newFile.path("remark").asText("");
            if (!Objects.equals(oldRemark, newRemark))
                changes.add("附件「" + name + "」备注：" + blankText(oldRemark) + " → " + blankText(newRemark));
            boolean oldPrint = oldFile.path("print").asBoolean(true), newPrint = newFile.path("print").asBoolean(true);
            if (oldPrint != newPrint)
                changes.add("附件「" + name + "」打印：" + (oldPrint ? "是" : "否") + " → " + (newPrint ? "是" : "否"));
        }
    }

    private Map<Long, JsonNode> attachmentEntries(ObjectNode spec) {
        Map<Long, JsonNode> entries = new java.util.LinkedHashMap<>();
        for (JsonNode item : spec.path("draftAttachments")) {
            if (item.path("attachmentId").isNumber()) entries.put(item.path("attachmentId").asLong(), item);
        }
        return entries;
    }

    private Map<Long, String> attachmentNames(Map<Long, JsonNode> oldFiles, Map<Long, JsonNode> newFiles) {
        Map<Long, String> names = new java.util.LinkedHashMap<>();
        for (Map<Long, JsonNode> entries : List.of(oldFiles, newFiles)) {
            entries.forEach((id, item) -> {
                String label = item.path("displayName").asText("");
                if (label.isBlank()) label = item.path("fileName").asText("");
                if (!label.isBlank()) names.putIfAbsent(id, label);
            });
        }
        java.util.Set<Long> missing = new java.util.LinkedHashSet<>();
        for (Long id : oldFiles.keySet()) if (!names.containsKey(id)) missing.add(id);
        for (Long id : newFiles.keySet()) if (!names.containsKey(id)) missing.add(id);
        if (!missing.isEmpty() && attachments != null) {
            try {
                for (SysAttachment file : attachments.selectBatchIds(missing)) {
                    if (file.getId() != null && file.getFileName() != null && !file.getFileName().isBlank())
                        names.putIfAbsent(file.getId(), file.getFileName());
                }
            } catch (Exception ignored) {
                // 文件名补不到时用附件 ID 兜底，不阻断业务保存。
            }
        }
        return names;
    }

    private String attachmentName(Map<Long, String> names, Long id) {
        if (id == null) return "未设置";
        String name = names.get(id);
        return name == null || name.isBlank() ? "附件 " + id : name;
    }

    private String nameList(List<Long> ids, Map<Long, String> names) {
        if (ids == null || ids.isEmpty()) return "（无）";
        List<String> namesList = ids.stream().limit(20).map(id -> "「" + attachmentName(names, id) + "」").toList();
        return String.join("、", namesList) + (ids.size() > 20 ? " 等 " + ids.size() + " 个" : "");
    }

    private Map<String, String> dieRowLabels(Long productId) {
        Map<String, String> labels = new java.util.HashMap<>();
        try {
            ProductFullVO full = products.getFullProductDetail(productId);
            if (full == null || full.getBom() == null || full.getBom().getItems() == null) return labels;
            for (var item : full.getBom().getItems()) {
                String base = item.getMaterialName() == null || item.getMaterialName().isBlank()
                    ? textOf(item.getMaterialCode()) : item.getMaterialName();
                String extra = java.util.stream.Stream.of(item.getLayer(), item.getProcessName())
                    .filter(value -> value != null && !value.isBlank()).reduce((a, b) -> a + " / " + b).orElse("");
                String label = extra.isBlank() ? base : base + "（" + extra + "）";
                labels.putIfAbsent(textOf(item.getMaterialCode()) + "|" + textOf(item.getLayer()) + "|"
                    + textOf(item.getProcessName()), label);
            }
        } catch (Exception ignored) {
            // BOM 不可读时用行键兜底，仍能定位到具体行。
        }
        return labels;
    }

    private String changeListDiff(ObjectNode before, ObjectNode after) {
        List<String> oldSignatures = changeSignatures(before), newSignatures = changeSignatures(after);
        if (oldSignatures.equals(newSignatures)) return "";
        List<String> oldLabels = changeLabels(before), newLabels = changeLabels(after);
        List<String> parts = new ArrayList<>();
        int common = Math.min(oldSignatures.size(), newSignatures.size());
        for (int i = 0; i < common && parts.size() < 5; i++) {
            if (Objects.equals(oldSignatures.get(i), newSignatures.get(i))) continue;
            parts.add("第" + (i + 1) + "条 " + blankText(oldLabels.get(i)) + " → " + blankText(newLabels.get(i)));
        }
        for (int i = common; i < newSignatures.size() && parts.size() < 5; i++)
            parts.add("新增「" + abbreviate(newLabels.get(i)) + "」");
        for (int i = common; i < oldSignatures.size() && parts.size() < 5; i++)
            parts.add("删除「" + abbreviate(oldLabels.get(i)) + "」");
        return parts.isEmpty() ? "" : "变更记录：" + String.join("；", parts);
    }

    private List<String> changeSignatures(ObjectNode spec) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : spec.path("changes")) {
            values.add(item.path("date").asText("") + "|" + item.path("text").asText("") + "|"
                + item.path("print").asBoolean(true) + "|" + item.path("reason").asText("") + "|"
                + item.path("color").asText(""));
        }
        return values;
    }

    private List<String> changeLabels(ObjectNode spec) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : spec.path("changes")) {
            String date = item.path("date").asText("");
            values.add((date.isBlank() ? "" : date + " ") + item.path("text").asText(""));
        }
        return values;
    }

    private void addTextChange(List<String> changes, String label, String before, String after) {
        if (!Objects.equals(before, after))
            changes.add(label + "：" + abbreviate(before) + " → " + abbreviate(after));
    }

    private String typeLabel(String type) {
        if (type == null || type.isBlank()) return "未分类";
        try {
            String label = docTypes == null ? null : docTypes.labelOf(type);
            return label == null || label.isBlank() ? type : label;
        } catch (Exception e) {
            return type;
        }
    }

    private String colorLabel(String value) {
        if (value == null || value.isBlank()) return "默认";
        return COLOR_LABELS.getOrDefault(value, value);
    }

    private String summary(String action, List<String> changes) {
        String body = changes == null || changes.isEmpty() ? "" : "：" + String.join("；", changes);
        String text = action + body;
        return text.length() > 1000 ? text.substring(0, 1000) + "…" : text;
    }

    private String textOf(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String textOf(String value) { return value == null ? "" : value; }

    private String blankText(String value) { return value == null || value.isBlank() ? "（空）" : value; }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) return "（空）";
        String oneLine = value.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }

    private Long numberOrNull(JsonNode value) {
        return value == null || value.isNull() || !value.isNumber() ? null : value.asLong();
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
        requireOwner(data);
        if (!Objects.equals(sourceRevision, sourceFingerprint(id, data)))
            throw new BusinessException("材料、流程或结构图已变化，请重新加载后确认");
        data.put("confirmedBy", SecurityUtils.getUsername());
        data.put("confirmedAt", LocalDateTime.now().toString());
        data.put("confirmedSource", sourceFingerprint(id, data));
        beginEdit(parse(oldRaw), data);
        mapper.write(id, data.toString());
        return response(id, mapper.read(id));
    }

    /** 显式制定后仅负责人可编辑；定版后需通过升级进入下一次制定。 */
    private void requireOwner(ObjectNode data) {
        if (data.hasNonNull("finalizedVersionId")) throw new BusinessException("作业规范已定版，请先升级新版本");
        if (!data.hasNonNull("preparedById") || data.path("preparedById").asLong() == 0)
            throw new BusinessException("请先点击制定规范");
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
    private Map<Long, Map<String, Object>> draftFilesById(Long id) {
        Map<Long, Map<String, Object>> available = new java.util.HashMap<>();
        for (var file : mapper.draftFiles(id)) {
            Long attachmentId = ((Number) file.get("attachmentId")).longValue();
            // 与 workSpecRef 一致：重复的历史来源取最早的有效引用。
            available.merge(attachmentId, file, (a, b) ->
                ((Number) a.get("refId")).longValue() <= ((Number) b.get("refId")).longValue() ? a : b);
        }
        return available;
    }

    private void syncDraftAttachmentNames(ObjectNode spec, Map<Long, Map<String, Object>> available) {
        for (JsonNode entry : spec.path("draftAttachments")) {
            var file = available.get(entry.path("attachmentId").asLong());
            if (file != null && file.get("displayName") instanceof String name && !name.isBlank())
                ((ObjectNode) entry).put("displayName", name);
        }
    }

    private ObjectNode response(Long id, String raw) {
        ObjectNode data = parse(raw);
        if (data.path("draftAttachments").isArray()) {
            Map<Long, Map<String, Object>> available = draftFilesById(id);
            // 展示名属于当前引用；清单中的旧缓存不能覆盖后端生成结果。
            syncDraftAttachmentNames(data, available);
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
                .eq("attachment_id", fileId).in("source_type", "product", "work_spec").eq("source_id", id)
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
