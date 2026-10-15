package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.dto.AttachmentRefBatchDTO;
import com.jjx.system.domain.dto.AttachmentRefBatchItem;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.dto.AttachmentRefMetadataDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @Transactional(rollbackFor = Exception.class)
    public Long create(AttachmentRefDTO request) {
        String source = sources.canonical(request.getSourceType());
        String sourceNo = sources.check(source, request.getSourceId(), true);
        // 归属收口（dev-20261015-027）：前端可能只传编码或都没传 —— 来源唯一产品时后端兜底写全（多产品/无产品不猜）
        Long productId = request.getProductId();
        String productCode = optional(request.getProductCode(), 64);
        if (productId == null || productCode == null) {
            AttachmentSourcePolicy.ProductRef resolved = sources.singleProductOf(source, request.getSourceId());
            if (resolved != null) {
                if (productId == null) productId = resolved.id();
                if (productCode == null) productCode = resolved.code();
            }
        }
        sources.checkProduct(source, request.getSourceId(), productId);
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
                    && Objects.equals(productId, ref.getProductId())) return ref.getId();
        }
        String type = optional(request.getDocumentType(), 100);
        types.validate(type, source);
        validateProductDrawing(source, productId, file);
        // 展示名（dev-20261016-003）：调用方没传时，后端按命名规则生成
        // 规则：来源码-来源Id-P产品id-一级码-二级码-二级名（无产品 P0；除产品 id 外缺任一不生成）
        String displayName = optional(request.getDisplayName(), 255);
        if (displayName == null) displayName = displayNames.build(source, request.getSourceId(), productId, type);
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
            jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=NULL WHERE id=?",
                file.getUploadState() == AttachmentUploadState.LEGACY ? AttachmentUploadState.LEGACY.name() : AttachmentUploadState.BOUND.name(),
                file.getId());
            return draft.getId();
        }
        SysAttachmentRef ref = new SysAttachmentRef();
        ref.setAttachmentId(file.getId()); ref.setSourceType(source); ref.setSourceId(request.getSourceId());
        ref.setSourceNo(sourceNo); ref.setProductCode(productCode); ref.setProductId(productId); ref.setDocumentType(type);
        ref.setDisplayName(displayName); ref.setRemark(optional(request.getRemark(), 500));
        ref.setSortOrder(order(request.getSortOrder())); ref.setDeleted(false);
        ref.setCreateBy(StpUtil.getLoginIdAsString()); ref.setCreateTime(LocalDateTime.now());
        refs.insert(ref);
        jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=NULL WHERE id=?",
            file.getUploadState() == AttachmentUploadState.LEGACY ? AttachmentUploadState.LEGACY.name() : AttachmentUploadState.BOUND.name(), file.getId());
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
        jdbc.query("SELECT id,file_name,file_size,file_type,category,version,drawing_no,drawing_name,file_role,is_current,is_controlled,released FROM sys_attachment WHERE id IN (" + placeholders + ")",
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
                    rs.getObject("released") == null ? null : rs.getInt("released")});
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
        }
    }

    /**
     * 产品工程图纸唯一性守卫（同图纸编号、同版本下原稿/打印件各一份）（dev-20261014-016 第3项）。
     * 图纸经公共链路（temp 上传写图纸列 + 建引用）落库后附件表无 biz_type/biz_id，故归属改为
     * 「引用表 source_type='product' AND product_id」+ 附件表 drawing_no 判定（不依赖已废弃字段）。
     * 仅对 product 来源且带图纸编号的附件生效；其它来源、普通产品资料不受影响。
     * 说明：为换取吞吐未对产品行加锁，判重为尽力而为（UI 侧另有同批预检）；极端并发下仍可能两个同版同用途件同时通过。
     */
    private void validateProductDrawing(String source, Long productId, SysAttachment file) {
        String drawingNo = file.getDrawingNo();
        if (!"product".equals(source) || productId == null || drawingNo == null || drawingNo.isBlank()) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT a.category,a.version,a.file_role,a.released FROM sys_attachment a "
                + "JOIN sys_attachment_ref r ON r.attachment_id=a.id AND r.deleted=0 AND r.source_type='product' AND r.product_id=? "
                + "WHERE a.deleted=0 AND a.drawing_no=? AND a.id<>?",
            productId, drawingNo, file.getId());
        for (Map<String, Object> row : rows) {
            boolean sameVersion = Objects.equals(text(row.get("version")), file.getVersion());
            if (sameVersion && isYes(row.get("released"))) throw new BusinessException("该版本已下发，请先撤回或上传新版本");
            if (!Objects.equals(text(row.get("category")), file.getCategory())) throw new BusinessException("同一图纸编号的文件类别必须一致");
            if (sameVersion && Objects.equals(text(row.get("file_role")), file.getFileRole()))
                throw new BusinessException("该图纸版本已存在相同用途的文件，请使用新版本或先删除未下发的重复文件");
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
        SysAttachmentRef initial = readable(id);
        lock(initial.getAttachmentId());
        SysAttachmentRef ref = refs.lockActive(initial.getAttachmentId()).stream().filter(r -> r.getId().equals(id))
            .findFirst().orElseThrow(() -> new BusinessException("附件引用已解除"));
        sources.check(ref.getSourceType(), ref.getSourceId(), true);
        String type = optional(body.getDocumentType(), 100);
        if (!Objects.equals(type, ref.getDocumentType())) types.validate(type, ref.getSourceType());
        jdbc.update("UPDATE sys_attachment_ref SET document_type=?,display_name=?,remark=?,sort_order=?,update_by=?,update_time=NOW() WHERE id=? AND deleted=0",
            type, optional(body.getDisplayName(), 255), optional(body.getRemark(), 500), order(body.getSortOrder()), StpUtil.getLoginIdAsString(), id);
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
        jdbc.update("UPDATE sys_attachment_ref SET deleted=1,deleted_at=NOW(),deleted_by=?,update_by=?,update_time=NOW() WHERE id=? AND deleted=0",
            StpUtil.getLoginIdAsString(), StpUtil.getLoginIdAsString(), id);
        if (active.size() == 1) {
            jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=? WHERE id=?",
                file.getUploadState() == AttachmentUploadState.LEGACY ? AttachmentUploadState.LEGACY.name() : AttachmentUploadState.ORPHAN.name(),
                LocalDateTime.now().plusDays(30), file.getId());
        }
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
        // 同一 refId 只处理第一条，避免同批内自相矛盾
        Map<Long, AttachmentRefBatchItem> unique = new LinkedHashMap<>();
        for (AttachmentRefBatchItem item : items) {
            if (item.getRefId() == null) throw new BusinessException("附件引用不能为空");
            unique.putIfAbsent(item.getRefId(), item);
        }
        int changed = 0;
        for (Map.Entry<Long, AttachmentRefBatchItem> entry : unique.entrySet()) {
            Long id = entry.getKey();
            AttachmentRefBatchItem item = entry.getValue();
            SysAttachmentRef initial = readable(id);
            lock(initial.getAttachmentId());
            SysAttachmentRef ref = refs.lockActive(initial.getAttachmentId()).stream().filter(r -> r.getId().equals(id))
                .findFirst().orElseThrow(() -> new BusinessException("附件引用已解除"));
            sources.check(ref.getSourceType(), ref.getSourceId(), true);
            String type = ref.getDocumentType();
            if (item.getDocumentType() != null) { // 部分更新：未传则保留原值
                type = optional(item.getDocumentType(), 100);
                if (!Objects.equals(type, ref.getDocumentType())) types.validate(type, ref.getSourceType());
            }
            String displayName = item.getDisplayName() != null ? optional(item.getDisplayName(), 255) : ref.getDisplayName();
            String remark = item.getRemark() != null ? optional(item.getRemark(), 500) : ref.getRemark();
            int sortOrder = item.getSortOrder() != null
                ? order(item.getSortOrder())
                : (ref.getSortOrder() == null ? 0 : ref.getSortOrder());
            jdbc.update("UPDATE sys_attachment_ref SET document_type=?,display_name=?,remark=?,sort_order=?,update_by=?,update_time=NOW() WHERE id=? AND deleted=0",
                type, displayName, remark, sortOrder, StpUtil.getLoginIdAsString(), id);
            changed++;
        }
        return changed;
    }

    /** 批量解除引用（逐条语义与 {@link #unlink} 一致；整批一次事务，任一条失败全部回滚）。 */
    @Transactional(rollbackFor = Exception.class)
    public int batchUnlink(List<Long> rawIds) {
        List<Long> ids = distinctIds(rawIds);
        int changed = 0;
        for (Long id : ids) {
            SysAttachmentRef ref = refs.includingDeleted(id);
            if (ref == null) throw new BusinessException("附件引用不存在");
            sources.check(ref.getSourceType(), ref.getSourceId(), true);
            SysAttachment file = lock(ref.getAttachmentId());
            List<SysAttachmentRef> active = refs.lockActive(file.getId());
            if (active.stream().noneMatch(r -> r.getId().equals(id))) continue; // 已解除：幂等跳过，不移动保留期时钟
            access.checkUnlink(file);
            jdbc.update("UPDATE sys_attachment_ref SET deleted=1,deleted_at=NOW(),deleted_by=?,update_by=?,update_time=NOW() WHERE id=? AND deleted=0",
                StpUtil.getLoginIdAsString(), StpUtil.getLoginIdAsString(), id);
            if (active.size() == 1) {
                jdbc.update("UPDATE sys_attachment SET upload_state=?,expires_at=? WHERE id=?",
                    file.getUploadState() == AttachmentUploadState.LEGACY ? AttachmentUploadState.LEGACY.name() : AttachmentUploadState.ORPHAN.name(),
                    LocalDateTime.now().plusDays(30), file.getId());
            }
            changed++;
        }
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

    private SysAttachment lock(Long id) {
        SysAttachment file = files.selectActiveForUpdate(id);
        if (file == null) throw new BusinessException("附件不存在或已删除");
        return file;
    }
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
