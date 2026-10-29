package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.system.domain.dto.AttachmentRefBatchDTO;
import com.jjx.system.domain.dto.AttachmentRefBatchItem;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.dto.AttachmentRefMetadataDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentRefService {
    private final SysAttachmentMapper files;
    private final SysAttachmentRefMapper refs;
    private final AttachmentSourcePolicy sources;
    private final AttachmentAccessService access;
    private final AttachmentDocumentTypeService types;
    private final AttachmentDisplayNameService displayNames;
    private final JdbcTemplate jdbc;
    @Autowired
    private AttachmentRefOperationLogRecorder operationLogs;

    @Transactional(rollbackFor = Exception.class)
    public Long create(AttachmentRefDTO request) {
        String source = sources.canonical(request.getSourceType());
        String sourceNo = sources.check(source, request.getSourceId(), true);
        // 归属收口（dev-20261015-027）：前端可能只传编码或都没传 —— 来源唯一产品时后端兜底写全（多产品/无产品不猜）
        Long productId = request.getProductId();
        String productCode = optional(request.getProductCode(), 64);
        sources.checkProduct(source, request.getSourceId(), productId);
        // 与现行切换/下发采用相同产品锁，补件不能与整版操作交错。
        if ("product".equals(source) && productId != null) {
            jdbc.queryForList("SELECT product_id FROM product WHERE product_id=? FOR UPDATE", productId);
        }
        SysAttachment file = lock(request.getAttachmentId());
        if (file.getUploadState() == AttachmentUploadState.ASSET)
            throw new BusinessException("共享资产不能建立业务附件引用");
        List<SysAttachmentRef> active = refs.lockActive(file.getId());
        access.checkRead(file); // Both original read permission and target edit permission are required.
        // 回填附件链路标识：上传时单据可能还没保存（不知道 trace），建引用时一定存在
        String traceId = sources.traceIdOf(source, request.getSourceId());
        if (traceId != null && !traceId.isBlank()) {
            jdbc.update("UPDATE sys_attachment SET trace_id=? WHERE id=? AND (trace_id IS NULL OR trace_id='')",
                traceId, file.getId());
        }
        for (SysAttachmentRef ref : active) {
            if (source.equals(ref.getSourceType()) && request.getSourceId().equals(ref.getSourceId())
                    && Objects.equals(productId, ref.getProductId())) {
                AttachmentDisplayNameService.GroupKey existingGroup = group(ref);
                displayNames.lockGroups(List.of(existingGroup));
                renumber(existingGroup);
                return ref.getId();
            }
        }
        String type = optional(request.getDocumentType(), 100);
        types.validate(type, source);
        validateProductDrawing(source, productId, file);
        // 同组自动名称编号须串行分配；事务完成后释放分组锁。
        displayNames.lockGroups(List.of(group(source, request.getSourceId(), productId, type)));
        String displayName = optional(request.getDisplayName(), 255);
        // 转正（dev-20261016-012 / S3）：未保存时上传建的"草稿行"（同一附件 + 同一上传人）→ 补归属、置 active
        SysAttachmentRef draft = refs.selectOne(Wrappers.<SysAttachmentRef>lambdaQuery()
            .eq(SysAttachmentRef::getAttachmentId, file.getId())
            .eq(SysAttachmentRef::getRefState, "draft")
            .eq(SysAttachmentRef::getCreateBy, StpUtil.getLoginIdAsString())
            .last("LIMIT 1"));
        if (draft != null) {
            jdbc.update("UPDATE sys_attachment_ref SET source_type=?,source_id=?,source_no=?,product_id=?,product_code=?,"
                    + "document_type=?,display_name=?,remark=?,sort_order=?,ref_state='active',"
                    + "update_by=?,update_time=NOW(),deleted=0,deleted_by=NULL,deleted_at=NULL WHERE id=?",
                source, request.getSourceId(), sourceNo, productId, productCode, type, displayName,
                optional(request.getRemark(), 500), order(request.getSortOrder()),
                StpUtil.getLoginIdAsString(), draft.getId());
            renumber(group(source, request.getSourceId(), productId, type));
            jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=NULL WHERE id=?",
                file.getUploadState() == AttachmentUploadState.LEGACY ? AttachmentUploadState.LEGACY.name() : AttachmentUploadState.BOUND.name(),
                file.getId());
            recordAdded(source, request.getSourceId(), traceId, draft.getId(), file,
                productId, productCode);
            return draft.getId();
        }
        SysAttachmentRef ref = new SysAttachmentRef();
        ref.setAttachmentId(file.getId()); ref.setSourceType(source); ref.setSourceId(request.getSourceId());
        ref.setSourceNo(sourceNo); ref.setProductCode(productCode); ref.setProductId(productId); ref.setDocumentType(type);
        ref.setDisplayName(displayName); ref.setRemark(optional(request.getRemark(), 500));
        ref.setSortOrder(order(request.getSortOrder())); ref.setDeleted(false);
        ref.setCreateBy(StpUtil.getLoginIdAsString()); ref.setCreateTime(LocalDateTime.now());
        refs.insert(ref);
        renumber(group(source, request.getSourceId(), productId, type));
        jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=NULL WHERE id=?",
            file.getUploadState() == AttachmentUploadState.LEGACY ? AttachmentUploadState.LEGACY.name() : AttachmentUploadState.BOUND.name(), file.getId());
        recordAdded(source, request.getSourceId(), traceId, ref.getId(), file,
            productId, productCode);
        return ref.getId();
    }

    public List<SysAttachmentRef> list(String source, Long id) {
        return list(source, id, null);
    }

    public List<SysAttachmentRef> list(String source, Long id, String sourceNo) {
        boolean hasNumber = sourceNo != null && !sourceNo.isBlank();
        if ((id != null) == hasNumber) throw new BusinessException("来源ID与来源单号须二选一");
        source = sources.canonical(source);
        if (hasNumber) {
            id = sources.resolveReadId(source, sourceNo);
            if (id == null) return List.of();
        }
        sources.check(source, id, false);
        List<SysAttachmentRef> rows = refs.selectList(Wrappers.<SysAttachmentRef>lambdaQuery().eq(SysAttachmentRef::getSourceType, source)
            .eq(SysAttachmentRef::getSourceId, id).orderByAsc(SysAttachmentRef::getSortOrder, SysAttachmentRef::getId));
        fillFileMetadata(rows);
        fillProductIds(rows);
        return rows;
    }

    public List<SysAttachmentRef> product(Long id) {
        sources.check("product", id, false);
        List<SysAttachmentRef> rows = refs.selectList(Wrappers.<SysAttachmentRef>lambdaQuery().and(q -> q.eq(SysAttachmentRef::getProductId, id)
            .or(w -> w.eq(SysAttachmentRef::getSourceType, "product").eq(SysAttachmentRef::getSourceId, id)))
            .ne(SysAttachmentRef::getRefState, "draft")
            .orderByAsc(SysAttachmentRef::getSortOrder, SysAttachmentRef::getId));
        fillFileMetadata(rows);
        return rows.stream().filter(r -> sources.canRead(r.getSourceType(), r.getSourceId())).toList();
    }

    /** 草稿行列表（未保存单据的待保存附件；限本人 + 按草稿键）。dev-20261016-009 */
    public List<SysAttachmentRef> drafts(String draftKey) {
        if (draftKey == null || draftKey.isBlank()) return List.of();
        List<SysAttachmentRef> rows = refs.selectList(Wrappers.<SysAttachmentRef>lambdaQuery()
            .eq(SysAttachmentRef::getDraftKey, draftKey.trim())
            .eq(SysAttachmentRef::getRefState, "draft")
            .eq(SysAttachmentRef::getCreateBy, StpUtil.getLoginIdAsString())
            .orderByAsc(SysAttachmentRef::getId));
        fillFileMetadata(rows);
        fillProductIds(rows);
        return rows;
    }

    /** 草稿行改二级类型（仅本人 draft 行）。 */
    public void updateDraft(Long id, String documentType) {
        SysAttachmentRef ref = ownDraft(id);
        types.validate(documentType, ref.getSourceType());
        jdbc.update("UPDATE sys_attachment_ref SET document_type=?,update_by=?,update_time=NOW()"
                + " WHERE id=? AND ref_state='draft' AND deleted=0",
            optional(documentType, 100), StpUtil.getLoginIdAsString(), id);
    }

    /** 草稿行移除（软删引用行；文件由到期清理处理）。 */
    public void removeDraft(Long id) {
        ownDraft(id);
        String actor = StpUtil.getLoginIdAsString();
        jdbc.update("UPDATE sys_attachment_ref SET deleted=1,deleted_by=?,deleted_at=NOW(),update_by=?,update_time=NOW()"
                + " WHERE id=? AND ref_state='draft' AND deleted=0", actor, actor, id);
    }

    /** 取本人草稿行；非草稿/非本人 → 报错。 */
    private SysAttachmentRef ownDraft(Long id) {
        SysAttachmentRef ref = refs.selectById(id);
        if (ref == null || !"draft".equals(ref.getRefState()))
            throw new BusinessException("待保存附件不存在");
        if (!Objects.equals(ref.getCreateBy(), StpUtil.getLoginIdAsString()))
            throw new BusinessException("只能操作本人上传的待保存附件");
        return ref;
    }

    /**
     * 批量回填附件文件元数据（文件名/大小/类型 + 图纸列）。
     * 仅用于展示：一条 IN 查询，不改引用模型、不改权限判定、不写任何数据。
     * 附件已软删时仍回填（历史行仍需展示原文件名），故直接查表而不用逻辑删除过滤的 Mapper。
     * 图纸列（category/version/drawing_no/drawing_name/file_role/is_current/is_controlled/released）供工程图纸库分组/现行/受控/下发展示（dev-20261014-016 第3项）。
     */
    private void fillFileMetadata(List<SysAttachmentRef> rows) {
        if (rows == null || rows.isEmpty()) return;
        List<Long> ids = rows.stream().map(SysAttachmentRef::getAttachmentId)
            .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        Map<Long, Object[]> files = new HashMap<>();
        jdbc.query("SELECT id,file_name,file_size,file_type,category,version,drawing_no,drawing_name,file_role,is_current,is_controlled,released,sha256 FROM sys_attachment WHERE id IN (" + placeholders + ")",
            (rs, rowNum) -> {
                files.put(rs.getLong("id"), new Object[]{
                    rs.getString("file_name"),
                    rs.getObject("file_size") == null ? 0L : rs.getLong("file_size"),
                    rs.getString("file_type"),
                    rs.getString("category"),
                    rs.getString("version"),
                    rs.getString("drawing_no"),
                    rs.getString("drawing_name"),
                    rs.getString("file_role"),
                    rs.getObject("is_current") == null ? null : rs.getInt("is_current"),
                    rs.getObject("is_controlled") == null ? null : rs.getInt("is_controlled"),
                    rs.getObject("released") == null ? null : rs.getInt("released"), rs.getString("sha256")});
                return null;
            },
            ids.toArray());
        for (SysAttachmentRef ref : rows) {
            Object[] meta = files.get(ref.getAttachmentId());
            if (meta == null) continue;
            ref.setFileName((String) meta[0]);
            ref.setFileSize((Long) meta[1]);
            ref.setFileType((String) meta[2]);
            ref.setCategory((String) meta[3]);
            ref.setVersion((String) meta[4]);
            ref.setDrawingNo((String) meta[5]);
            ref.setDrawingName((String) meta[6]);
            ref.setFileRole((String) meta[7]);
            ref.setIsCurrent((Integer) meta[8]);
            ref.setIsControlled((Integer) meta[9]);
            ref.setReleased((Integer) meta[10]);
            ref.setSha256((String) meta[11]);
        }
    }

    /**
     * 同版可补多个同用途文件；新文件沿用该版现行状态，但须单独确认下发。
     * 图纸经公共链路（temp 上传写图纸列 + 建引用）落库后附件表无 biz_type/biz_id，故归属改为
     * 「引用表 source_type='product' AND product_id」+ 附件表 drawing_no 判定（不依赖已废弃字段）。
     * 仅对 product 来源且带图纸编号的附件生效；其它来源、普通产品资料不受影响。
     * 调用方持有产品锁，与整版切换/下发串行。重复内容仅由前端提示。
     */
    private void validateProductDrawing(String source, Long productId, SysAttachment file) {
        String drawingNo = file.getDrawingNo();
        if (!"product".equals(source) || productId == null || drawingNo == null || drawingNo.isBlank()) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT a.category,a.version,a.is_current FROM sys_attachment a "
                + "JOIN sys_attachment_ref r ON r.attachment_id=a.id AND r.deleted=0 AND r.source_type='product' AND r.product_id=? "
                + "WHERE a.deleted=0 AND a.drawing_no=? AND a.id<>? FOR UPDATE",
            productId, drawingNo, file.getId());
        for (Map<String, Object> row : rows) {
            if (!Objects.equals(text(row.get("category")), file.getCategory())) throw new BusinessException("同一图纸编号的文件类别必须一致");
        }
        // 已绑定文件再建其它来源引用不改变其发布记录；仅初始化新上传的文件。
        if (file.getUploadState() == AttachmentUploadState.TEMP) {
            boolean current = rows.stream().anyMatch(row -> Objects.equals(text(row.get("version")), file.getVersion()) && isYes(row.get("is_current")));
            jdbc.update("UPDATE sys_attachment SET is_current=?,released=?,released_at=NULL,released_by=NULL WHERE id=?",
                current ? YesNoEnum.YES.getCode() : YesNoEnum.NO.getCode(), YesNoEnum.NO.getCode(), file.getId());
        }
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }

    private boolean isYes(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() == 1;
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * 批量回填引用行的产品 id（仅展示用）：按 product_code 解析，一条 IN 查询。
     * 只补 product_id 为空的行；已有真值不查不改；编码为空或产品不存在则保持 null。
     * 不写库、不改引用模型、不改权限判定。（口径 dev-20261014-001，仿 fillFileMetadata）
     */
    private void fillProductIds(List<SysAttachmentRef> rows) {
        if (rows == null || rows.isEmpty()) return;
        List<String> codes = rows.stream()
            .filter(r -> r.getProductId() == null && r.getProductCode() != null && !r.getProductCode().isBlank())
            .map(SysAttachmentRef::getProductCode).distinct().toList();
        if (codes.isEmpty()) return;
        String placeholders = codes.stream().map(c -> "?").collect(Collectors.joining(","));
        Map<String, Long> productIds = new HashMap<>();
        jdbc.query("SELECT product_id,product_code FROM product WHERE product_code IN (" + placeholders + ")",
            (rs, rowNum) -> {
                productIds.put(rs.getString("product_code"), rs.getLong("product_id"));
                return null;
            },
            codes.toArray());
        for (SysAttachmentRef ref : rows) {
            if (ref.getProductId() != null) continue;
            String code = ref.getProductCode();
            if (code == null || code.isBlank()) continue;
            Long productId = productIds.get(code);
            if (productId != null) ref.setProductId(productId);
        }
    }

    public List<java.util.Map<String, Object>> duplicates(String hash, String source, Long id) {
        source = sources.canonical(source);
        sources.check(source, id, false);
        if (hash == null || !hash.matches("(?i)[0-9a-f]{64}")) throw new BusinessException("SHA-256必须为64位十六进制");
        return jdbc.queryForList("SELECT r.id AS refId,a.id AS attachmentId,a.file_name,a.file_size,a.sha256 FROM sys_attachment_ref r JOIN sys_attachment a ON a.id=r.attachment_id AND a.deleted=0 WHERE r.source_type=? AND r.source_id=? AND r.deleted=0 AND a.sha256=? ORDER BY r.id",
            source, id, hash.toLowerCase(java.util.Locale.ROOT));
    }

    public SysAttachmentRef readable(Long id) {
        SysAttachmentRef ref = refs.selectById(id);
        if (ref == null) throw new BusinessException("附件引用不存在");
        sources.check(ref.getSourceType(), ref.getSourceId(), false);
        return ref;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AttachmentRefMetadataDTO body) {
        updateMetadata(id, body, false, true);
    }

    /**
     * 新建引用初始化：自动生成的展示名与排序只落库，不另记「附件资料修改」。
     * 用户真正修改资料类型/展示名/备注仍走 {@link #updateAutoNamed}。
     */
    @Transactional(rollbackFor = Exception.class)
    public void initAutoNamed(Long id, AttachmentRefMetadataDTO body) {
        updateMetadata(id, body, true, false);
    }

    /** 作业规范统一命名：忽略客户端缓存的展示名，修复原文件名占用展示名的旧引用。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateAutoNamed(Long id, AttachmentRefMetadataDTO body) {
        updateMetadata(id, body, true, true);
    }

    private void updateMetadata(Long id, AttachmentRefMetadataDTO body, boolean automaticName, boolean logChanges) {
        SysAttachmentRef initial = readable(id);
        SysAttachment file = lock(initial.getAttachmentId());
        SysAttachmentRef ref = refs.lockActive(initial.getAttachmentId()).stream().filter(r -> r.getId().equals(id))
            .findFirst().orElseThrow(() -> new BusinessException("附件引用已解除"));
        sources.check(ref.getSourceType(), ref.getSourceId(), true);
        String type = optional(body.getDocumentType(), 100);
        if (!Objects.equals(type, ref.getDocumentType())) types.validate(type, ref.getSourceType());
        String oldDisplayName = ref.getDisplayName();
        String oldBase = displayNames.baseName(ref.getSourceType(), ref.getSourceId(), ref.getProductId(), ref.getDocumentType());
        boolean generated = displayNames.isGeneratedName(oldDisplayName, oldBase);
        boolean typeChanged = !Objects.equals(type, ref.getDocumentType());
        String requestedDisplayName = automaticName ? null : optional(body.getDisplayName(), 255);
        boolean rebuildAutomaticName = automaticName && (!generated || typeChanged);
        String displayName = automaticName ? (rebuildAutomaticName ? null : oldDisplayName) : typeChanged && generated
            && (requestedDisplayName == null || Objects.equals(requestedDisplayName, oldDisplayName))
                ? null
                : (body.getDisplayName() != null ? requestedDisplayName : oldDisplayName);
        String remark = optional(body.getRemark(), 500);
        int sortOrder = order(body.getSortOrder());
        boolean generatedNameWillBeRebuilt = typeChanged && generated
            && (requestedDisplayName == null || Objects.equals(requestedDisplayName, oldDisplayName));
        boolean sortChanged = (ref.getSortOrder() == null ? 0 : ref.getSortOrder()) != sortOrder;
        // 自动命名模式下排序由草稿清单位置派生，单条记录不再记「排序」流水；真正换位由保存草稿的人话差异承担。
        List<String> changes = metadataChanges(ref, type, displayName, remark, sortOrder,
            !generatedNameWillBeRebuilt, !automaticName);
        if (changes.isEmpty() && !rebuildAutomaticName && !sortChanged) return;
        Set<AttachmentDisplayNameService.GroupKey> affected = new LinkedHashSet<>();
        if (typeChanged || (!automaticName && body.getDisplayName() != null) || rebuildAutomaticName) {
            affected.add(group(ref));
            affected.add(group(ref.getSourceType(), ref.getSourceId(), ref.getProductId(), type));
            displayNames.lockGroups(affected);
        }
        jdbc.update("UPDATE sys_attachment_ref SET document_type=?,display_name=?,remark=?,sort_order=?,update_by=?,update_time=NOW() WHERE id=? AND deleted=0",
            type, displayName, remark, sortOrder, StpUtil.getLoginIdAsString(), id);
        renumber(affected);
        if (rebuildAutomaticName) {
            SysAttachmentRef named = refs.selectById(id);
            if (named == null || named.getDisplayName() == null || named.getDisplayName().isBlank())
                throw new BusinessException("附件展示名生成失败，请检查资料类型字典");
            // 资料类型变化会顺带重排自动展示名，只记用户真正改的类型，不把生成名另记一条。
            changes = metadataChanges(ref, type, named.getDisplayName(), remark, sortOrder,
                !(typeChanged && generated), !automaticName);
        }
        if (logChanges && !changes.isEmpty()) recordRefChange(ref, file, BusinessType.UPDATE.getCode(), "附件资料修改", changes);
    }

    /** 仅供统一作业规范事务移除当前草稿关联；历史硬引用和其它业务引用不动。 */
    @Transactional(rollbackFor = Exception.class)
    public void detachWorkSpecFile(Long productId, Long attachmentId) {
        sources.check("product", productId, true);
        SysAttachment file = lock(attachmentId);
        List<SysAttachmentRef> active = refs.lockActive(attachmentId);
        boolean removalRecorded = false;
        for (SysAttachmentRef ref : active) {
            if (!("product".equals(ref.getSourceType()) || "work_spec".equals(ref.getSourceType()))
                || !Objects.equals(productId, ref.getSourceId())) continue;
            jdbc.update("UPDATE sys_attachment_ref SET deleted=1,deleted_at=NOW(),deleted_by=?,update_by=?,update_time=NOW() WHERE id=? AND deleted=0",
                StpUtil.getLoginIdAsString(), StpUtil.getLoginIdAsString(), ref.getId());
            // 同一文件同一次移除只记一条「附件移除」，不借资料修改动作冒充。
            if (!removalRecorded && operationLogs != null) {
                operationLogs.record(ref.getSourceType(), ref.getSourceId(),
                    sources.traceIdOf(ref.getSourceType(), ref.getSourceId()), BusinessType.DELETE.getCode(),
                    "附件移除", ref.getId(), file.getId(), fileName(file), false,
                    List.of("移除附件" + productLabel(ref.getProductId(), ref.getProductCode())
                        + "：「" + fileName(file) + "」"));
                removalRecorded = true;
            }
        }
        if (active.stream().allMatch(ref -> ("product".equals(ref.getSourceType()) || "work_spec".equals(ref.getSourceType()))
            && Objects.equals(productId, ref.getSourceId()))) {
            jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=? WHERE id=?",
                AttachmentUploadState.ORPHAN.name(), LocalDateTime.now().plusDays(30), file.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void unlink(Long id) {
        SysAttachmentRef ref = refs.includingDeleted(id);
        if (ref == null) throw new BusinessException("附件引用不存在");
        sources.check(ref.getSourceType(), ref.getSourceId(), true);
        SysAttachment file = lock(ref.getAttachmentId());
        List<SysAttachmentRef> active = refs.lockActive(file.getId());
        if (active.stream().noneMatch(r -> r.getId().equals(id))) return; // Retry does not move the retention clock.
        access.checkUnlink(file);
        AttachmentDisplayNameService.GroupKey group = group(ref);
        displayNames.lockGroups(List.of(group));
        jdbc.update("UPDATE sys_attachment_ref SET deleted=1,deleted_at=NOW(),deleted_by=?,update_by=?,update_time=NOW() WHERE id=? AND deleted=0",
            StpUtil.getLoginIdAsString(), StpUtil.getLoginIdAsString(), id);
        renumber(group);
        if (active.size() == 1) {
            jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=? WHERE id=?",
                file.getUploadState() == AttachmentUploadState.LEGACY ? AttachmentUploadState.LEGACY.name() : AttachmentUploadState.ORPHAN.name(),
                LocalDateTime.now().plusDays(30), file.getId());
        }
        recordRefChange(ref, file, BusinessType.DELETE.getCode(), "附件解绑", List.of("解绑"));
    }

    /**
     * 批量修改引用元数据（按行部分更新：每条只写自己非 null 的字段，null 表示不改）。
     * 与单条 {@link #update}（全量替换、null=清空）语义不同；整批一次事务，任一条失败全部回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdate(AttachmentRefBatchDTO body) {
        List<AttachmentRefBatchItem> items = body.getItems() == null ? null
            : body.getItems().stream().filter(Objects::nonNull).toList();
        if (items == null || items.isEmpty()) throw new BusinessException("请选择要修改的附件");
        if (items.size() > 500) throw new BusinessException("单次最多修改 500 条");
        boolean silentLog = Boolean.TRUE.equals(body.getSilent());
        // 同一 refId 只处理第一条，避免同批内自相矛盾
        Map<Long, AttachmentRefBatchItem> unique = new LinkedHashMap<>();
        for (AttachmentRefBatchItem item : items) {
            if (item.getRefId() == null) throw new BusinessException("附件引用不能为空");
            unique.putIfAbsent(item.getRefId(), item);
        }
        Map<Long, SysAttachmentRef> locked = new LinkedHashMap<>();
        Map<Long, SysAttachment> lockedFiles = new LinkedHashMap<>();
        for (Long id : unique.keySet().stream().sorted().toList()) {
            SysAttachmentRef initial = readable(id);
            SysAttachment file = lock(initial.getAttachmentId());
            SysAttachmentRef ref = refs.lockActive(file.getId()).stream().filter(r -> r.getId().equals(id))
                .findFirst().orElseThrow(() -> new BusinessException("附件引用已解除"));
            sources.check(ref.getSourceType(), ref.getSourceId(), true);
            AttachmentRefBatchItem item = unique.get(id);
            if (item.getDocumentType() != null) {
                String nextType = optional(item.getDocumentType(), 100);
                if (!Objects.equals(nextType, ref.getDocumentType())) types.validate(nextType, ref.getSourceType());
            }
            locked.put(id, ref);
            lockedFiles.put(id, file);
        }

        Set<AttachmentDisplayNameService.GroupKey> affected = new LinkedHashSet<>();
        for (Map.Entry<Long, SysAttachmentRef> entry : locked.entrySet()) {
            AttachmentRefBatchItem item = unique.get(entry.getKey());
            SysAttachmentRef ref = entry.getValue();
            String nextType = item.getDocumentType() == null ? ref.getDocumentType() : optional(item.getDocumentType(), 100);
            boolean typeChanged = !Objects.equals(nextType, ref.getDocumentType());
            String oldBase = displayNames.baseName(ref.getSourceType(), ref.getSourceId(), ref.getProductId(), ref.getDocumentType());
            boolean generated = displayNames.isGeneratedName(ref.getDisplayName(), oldBase);
            String requestedDisplayName = optional(item.getDisplayName(), 255);
            String nextDisplayName = typeChanged && generated
                && (requestedDisplayName == null || Objects.equals(requestedDisplayName, ref.getDisplayName()))
                    ? null
                    : (item.getDisplayName() != null ? requestedDisplayName : ref.getDisplayName());
            String nextRemark = item.getRemark() != null ? optional(item.getRemark(), 500) : ref.getRemark();
            int nextSortOrder = item.getSortOrder() != null
                ? order(item.getSortOrder())
                : (ref.getSortOrder() == null ? 0 : ref.getSortOrder());
            boolean generatedNameWillBeRebuilt = typeChanged && generated
                && (requestedDisplayName == null || Objects.equals(requestedDisplayName, ref.getDisplayName()));
            List<String> changes = metadataChanges(ref, nextType, nextDisplayName, nextRemark, nextSortOrder,
                !generatedNameWillBeRebuilt, true);
            if (!changes.isEmpty() && (!Objects.equals(nextType, ref.getDocumentType())
                    || !Objects.equals(nextDisplayName, ref.getDisplayName()))) {
                affected.add(group(ref));
                affected.add(group(ref.getSourceType(), ref.getSourceId(), ref.getProductId(), nextType));
            }
        }
        displayNames.lockGroups(affected);

        Map<String, String> traceIds = new HashMap<>();
        for (Map.Entry<Long, SysAttachmentRef> entry : locked.entrySet()) {
            Long id = entry.getKey();
            AttachmentRefBatchItem item = unique.get(id);
            SysAttachmentRef ref = entry.getValue();
            String type = item.getDocumentType() == null ? ref.getDocumentType() : optional(item.getDocumentType(), 100);
            boolean typeChanged = !Objects.equals(type, ref.getDocumentType());
            String oldBase = displayNames.baseName(ref.getSourceType(), ref.getSourceId(), ref.getProductId(), ref.getDocumentType());
            boolean generated = displayNames.isGeneratedName(ref.getDisplayName(), oldBase);
            String requestedDisplayName = optional(item.getDisplayName(), 255);
            String displayName = typeChanged && generated
                && (requestedDisplayName == null || Objects.equals(requestedDisplayName, ref.getDisplayName()))
                    ? null
                    : (item.getDisplayName() != null ? requestedDisplayName : ref.getDisplayName());
            String remark = item.getRemark() != null ? optional(item.getRemark(), 500) : ref.getRemark();
            int sortOrder = item.getSortOrder() != null
                ? order(item.getSortOrder())
                : (ref.getSortOrder() == null ? 0 : ref.getSortOrder());
            boolean generatedNameWillBeRebuilt = typeChanged && generated
                && (requestedDisplayName == null || Objects.equals(requestedDisplayName, ref.getDisplayName()));
            List<String> changes = metadataChanges(ref, type, displayName, remark, sortOrder,
                !generatedNameWillBeRebuilt, true);
            if (changes.isEmpty()) continue;
            jdbc.update("UPDATE sys_attachment_ref SET document_type=?,display_name=?,remark=?,sort_order=?,update_by=?,update_time=NOW() WHERE id=? AND deleted=0",
                type, displayName, remark, sortOrder, StpUtil.getLoginIdAsString(), id);
            SysAttachment file = lockedFiles.get(id);
            if (!silentLog)
                recordRefChange(ref, file, BusinessType.UPDATE.getCode(), "附件资料修改", changes,
                traceIdOf(ref, traceIds));
        }
        renumber(affected);
        return locked.size();
    }

    /** 批量解除引用（逐条语义与 {@link #unlink} 一致；整批一次事务，任一条失败全部回滚）。 */
    @Transactional(rollbackFor = Exception.class)
    public int batchUnlink(List<Long> rawIds) {
        List<Long> ids = distinctIds(rawIds);
        Map<Long, UnlinkCandidate> candidates = new LinkedHashMap<>();
        for (Long id : ids.stream().sorted().toList()) {
            SysAttachmentRef ref = refs.includingDeleted(id);
            if (ref == null) throw new BusinessException("附件引用不存在");
            sources.check(ref.getSourceType(), ref.getSourceId(), true);
            SysAttachment file = lock(ref.getAttachmentId());
            List<SysAttachmentRef> active = refs.lockActive(file.getId());
            if (active.stream().noneMatch(r -> r.getId().equals(id))) continue; // 已解除：幂等跳过，不移动保留期时钟
            access.checkUnlink(file);
            candidates.put(id, new UnlinkCandidate(ref, file, active));
        }
        Set<AttachmentDisplayNameService.GroupKey> affected = new LinkedHashSet<>();
        candidates.values().forEach(candidate -> affected.add(group(candidate.ref())));
        displayNames.lockGroups(affected);
        int changed = 0;
        Map<String, String> traceIds = new HashMap<>();
        for (Map.Entry<Long, UnlinkCandidate> entry : candidates.entrySet()) {
            Long id = entry.getKey();
            UnlinkCandidate candidate = entry.getValue();
            SysAttachment file = candidate.file();
            List<SysAttachmentRef> active = candidate.active();
            jdbc.update("UPDATE sys_attachment_ref SET deleted=1,deleted_at=NOW(),deleted_by=?,update_by=?,update_time=NOW() WHERE id=? AND deleted=0",
                StpUtil.getLoginIdAsString(), StpUtil.getLoginIdAsString(), id);
            if (active.size() == 1) {
                jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=? WHERE id=?",
                    file.getUploadState() == AttachmentUploadState.LEGACY ? AttachmentUploadState.LEGACY.name() : AttachmentUploadState.ORPHAN.name(),
                    LocalDateTime.now().plusDays(30), file.getId());
            }
            recordRefChange(candidate.ref(), file, BusinessType.DELETE.getCode(), "附件解绑", List.of("解绑"),
                traceIdOf(candidate.ref(), traceIds));
            changed++;
        }
        renumber(affected);
        return changed;
    }

    /** 批量入参清洗：去空、去重、限流（单次上限 500），空集合直接拒绝。 */
    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要操作的附件");
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) throw new BusinessException("请选择要操作的附件");
        if (distinct.size() > 500) throw new BusinessException("单次最多操作 500 条");
        return distinct;
    }

    private void recordAdded(String source, Long sourceId, String traceId, Long refId, SysAttachment file,
                             Long productId, String productCode) {
        if (operationLogs == null) return;
        operationLogs.record(source, sourceId, traceId, BusinessType.INSERT.getCode(), "附件新增",
            refId, file.getId(), fileName(file), true,
            List.of("新增附件" + productLabel(productId, productCode) + "：「" + fileName(file) + "」"));
    }

    private void recordRefChange(SysAttachmentRef ref, SysAttachment file, int businessType,
                                 String action, List<String> changes) {
        recordRefChange(ref, file, businessType, action, changes,
            sources.traceIdOf(ref.getSourceType(), ref.getSourceId()));
    }

    private void recordRefChange(SysAttachmentRef ref, SysAttachment file, int businessType,
                                 String action, List<String> changes, String traceId) {
        if (operationLogs == null) return;
        String fileLabel = "附件「" + fileName(file) + "」" + productLabel(ref.getProductId(), ref.getProductCode());
        List<String> details = changes.stream().map(change -> fileLabel + "：" + change).toList();
        operationLogs.record(ref.getSourceType(), ref.getSourceId(), traceId,
            businessType, action, ref.getId(), ref.getAttachmentId(), fileName(file),
            businessType != BusinessType.DELETE.getCode(), details);
    }

    private String traceIdOf(SysAttachmentRef ref, Map<String, String> cache) {
        String key = ref.getSourceType() + ":" + ref.getSourceId();
        if (!cache.containsKey(key)) cache.put(key, sources.traceIdOf(ref.getSourceType(), ref.getSourceId()));
        return cache.get(key);
    }

    private String productLabel(Long productId, String productCode) {
        if (productCode != null && !productCode.isBlank()) return "（产品 " + productCode + "）";
        return productId == null ? "（整单）" : "（产品 ID " + productId + "）";
    }

    private List<String> metadataChanges(SysAttachmentRef ref, String documentType, String displayName,
                                         String remark, int sortOrder, boolean includeDisplayName,
                                         boolean includeSort) {
        List<String> changes = new java.util.ArrayList<>();
        if (!Objects.equals(ref.getDocumentType(), documentType))
            changes.add("资料类型修改：" + typeLabel(ref.getDocumentType()) + " → " + typeLabel(documentType));
        if (includeDisplayName) addChange(changes, "展示名称", ref.getDisplayName(), displayName);
        addChange(changes, "备注", ref.getRemark(), remark);
        if (includeSort) addChange(changes, "排序", ref.getSortOrder() == null ? 0 : ref.getSortOrder(), sortOrder);
        return changes;
    }

    /** 流水写人话：资料类型用字典中文名，不把枚举编码直接抛给用户。 */
    private String typeLabel(String type) {
        if (type == null || type.isBlank()) return "未分类";
        try {
            String label = types.labelOf(type);
            return label == null || label.isBlank() ? type : label;
        } catch (Exception e) {
            return type;
        }
    }

    private void addChange(List<String> changes, String label, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(label + "：" + (oldValue == null ? "空" : oldValue)
                + " → " + (newValue == null ? "空" : newValue));
        }
    }

    private String fileName(SysAttachment file) {
        return file == null || file.getFileName() == null || file.getFileName().isBlank()
            ? "附件" + (file == null ? "" : file.getId()) : file.getFileName();
    }

    private SysAttachment lock(Long id) {
        SysAttachment file = files.selectActiveForUpdate(id);
        if (file == null) throw new BusinessException("附件不存在或已删除");
        return file;
    }

    private AttachmentDisplayNameService.GroupKey group(SysAttachmentRef ref) {
        return group(ref.getSourceType(), ref.getSourceId(), ref.getProductId(), ref.getDocumentType());
    }

    private AttachmentDisplayNameService.GroupKey group(String source, Long sourceId, Long productId, String type) {
        return new AttachmentDisplayNameService.GroupKey(source, sourceId, productId, type);
    }

    private void renumber(AttachmentDisplayNameService.GroupKey group) {
        renumber(Set.of(group));
    }

    private void renumber(Set<AttachmentDisplayNameService.GroupKey> groups) {
        if (groups == null || groups.isEmpty()) return;
        String operator = StpUtil.getLoginIdAsString();
        groups.forEach(group -> displayNames.renumber(group.source(), group.sourceId(), group.productId(), group.documentType(), operator));
    }

    private record UnlinkCandidate(SysAttachmentRef ref, SysAttachment file, List<SysAttachmentRef> active) {}

    private int order(Integer value) {
        if (value != null && value < 0) throw new BusinessException("排序号不能为负");
        return value == null ? 0 : value;
    }
    private String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > max) throw new BusinessException("附件元数据超出长度限制");
        return value.trim();
    }
}
