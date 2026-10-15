package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.dto.AttachmentDrawingMetaDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/** V1-A deliberately exposes preview only. No scheduler or physical deletion entrypoint. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentLifecycleService {
    /** 自动清理的 actor 标识（写入应用日志，不依赖登录态）。 */
    public static final String AUTO_ACTOR = "system:scheduler";
    private final SysAttachmentMapper files;
    private final AttachmentAccessService access;
    private final JdbcTemplate jdbc;
    private final SysOperLogMapper operLogs;
    private final AttachmentSourcePolicy sources;
    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;

    @Transactional(rollbackFor = Exception.class)
    public Long upload(MultipartFile input, String requestKey) {
        return upload(input, requestKey, null, null, null, null);
    }

    /**
     * 临时上传（公共附件链路）。新增可选图纸元数据 {@code drawingMeta}（dev-20261014-029，019 方案B）：
     * 非空时写入 {@code sys_attachment} 既有图纸列（category/drawing_no/drawing_name/version/file_role），
     * 不传/null = 不写这些列，行为与改造前完全一致；不改表结构、不搬列、不动其它来源行为。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long upload(MultipartFile input, String requestKey, AttachmentDrawingMetaDTO drawingMeta) {
        return upload(input, requestKey, drawingMeta, null, null, null);
    }

    /**
     * 临时上传（含"草稿行"）。dev-20261016-009：未保存单据的附件在上传时**同事务**落一条草稿引用
     * （ref_state=draft、source_id 空、带 draft_key/product_id），单据保存时再转正。
     * 不传 draftKey = 行为与改造前完全一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long upload(MultipartFile input, String requestKey, AttachmentDrawingMetaDTO drawingMeta,
                       String draftSourceType, Long draftProductId, String draftKey) {
        StpUtil.checkLogin();
        if (input == null || input.isEmpty()) throw new BusinessException("请选择文件");
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9_-]{16,100}"))
            throw new BusinessException("上传必须提供16至100位requestKey，重试使用同一键");
        // 图纸元数据先归一化校验（fail-fast，避免无效元数据产生孤立临时文件）
        DrawingMeta meta = normalizeDrawingMeta(drawingMeta);
        String owner = StpUtil.getLoginIdAsString();
        // Serialize retries before writing bytes. This is the same authenticated sys_user primary key.
        if (jdbc.queryForList("SELECT user_id FROM sys_user WHERE user_id=? FOR UPDATE", owner).isEmpty())
            throw new BusinessException("上传用户不存在");
        String key = digest((owner + ":" + requestKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<SysAttachment> existing = files.selectList(Wrappers.<SysAttachment>lambdaQuery().eq(SysAttachment::getUploadKey, key));
        String hash;
        try (InputStream stream = input.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; int length;
            while ((length = stream.read(buffer)) != -1) md.update(buffer, 0, length);
            hash = HexFormat.of().formatHex(md.digest());
        } catch (Exception ex) { throw new BusinessException("无法计算上传文件SHA-256"); }
        if (!existing.isEmpty()) {
            SysAttachment file = existing.get(0);
            access.checkRead(file);
            if (!hash.equals(file.getSha256())) throw new BusinessException("同一requestKey不能上传不同文件");
            return file.getId();
        }
        String name = input.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255) throw new BusinessException("文件名无效");
        Path root = Paths.get(uploadBasePath).toAbsolutePath().normalize();
        String relative = "attachment-temp/" + UUID.randomUUID() + ".bin";
        Path target = root.resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream stream = input.getInputStream()) { Files.copy(stream, target); } // CREATE_NEW, never overwrite.
        } catch (java.io.IOException ex) {
            log.error("临时上传写入失败，保留路径供人工核查：{}", target, ex);
            throw new BusinessException("临时上传失败，可使用同一requestKey重试");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) log.warn("临时上传事务回滚，孤立文件仅登记待人工核查，不自动删除：{}", target);
            }
        });
        SysAttachment file = new SysAttachment();
        file.setFileName(name); file.setFilePath(relative); file.setFileSize(input.getSize());
        file.setFileType(input.getContentType()); file.setSha256(hash); file.setDeleted(false);
        file.setCategory(meta.category()); file.setDrawingNo(meta.drawingNo()); file.setDrawingName(meta.drawingName());
        file.setVersion(meta.version()); file.setFileRole(meta.fileRole());
        file.setUploadState(AttachmentUploadState.TEMP); file.setExpiresAt(LocalDateTime.now().plusDays(7));
        file.setUploadOwner(owner); file.setUploadKey(key); file.setCreateBy(owner); file.setCreateTime(LocalDateTime.now());
        files.insert(file);
        // 草稿行（dev-20261016-009）：仅未保存单据（传了 draftKey）才建；同事务，失败一并回滚。
        if (draftKey != null && !draftKey.isBlank()) {
            String draftSource = sources.canonical(draftSourceType);
            if (draftSource == null || draftSource.isBlank()
                    || !AttachmentSourcePolicy.knownSourceTypes().contains(draftSource))
                throw new BusinessException("上传草稿必须提供合法来源类型");
            jdbc.update("INSERT INTO sys_attachment_ref"
                    + " (attachment_id,source_type,source_id,product_id,draft_key,ref_state,deleted,create_by,create_time)"
                    + " VALUES (?,?,NULL,?,?,'draft',0,?,NOW())",
                file.getId(), draftSource, draftProductId, draftKey.trim(), owner);
        }
        return file.getId();
    }

    /**
     * 到期清理草稿行（dev-20261016-012 / S4）：把超过 N 天还没落定的"草稿行"软删，
     * 并把它引用的 TEMP 文件一并标到期（expires_at=NOW()），不留孤儿（口径 §7-2 甲）。
     * **不自动跑**（无 scheduler；由运维/跟踪卡按需触发）。返回清理条数。
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredDrafts(int retentionDays) {
        int days = retentionDays > 0 ? retentionDays : 7;
        List<Long> ids = jdbc.queryForList(
            "SELECT attachment_id FROM sys_attachment_ref WHERE ref_state='draft' AND deleted=0"
                + " AND create_time < DATE_SUB(NOW(), INTERVAL ? DAY)", Long.class, days);
        if (ids == null || ids.isEmpty()) return 0;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) in.append(i == 0 ? "?" : ",?");
        Object[] refArgs = new Object[ids.size() + 1];
        refArgs[0] = AUTO_ACTOR;
        for (int i = 0; i < ids.size(); i++) refArgs[i + 1] = ids.get(i);
        jdbc.update("UPDATE sys_attachment_ref SET deleted=1,deleted_by=?,deleted_at=NOW()"
            + " WHERE ref_state='draft' AND deleted=0 AND attachment_id IN (" + in + ")", refArgs);
        jdbc.update("UPDATE sys_attachment SET expires_at=NOW()"
            + " WHERE upload_state='TEMP' AND id IN (" + in + ")", ids.toArray());
        return ids.size();
    }

    private String digest(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    /**
     * 归一化可选图纸元数据：全部可选；不传=不写列。校验长度；图纸编号/版本统一大写（与老 upload-product 口径一致，
     * 避免同图纸分组因大小写拆开）；文件用途仅接受 ORIGINAL/PRINT；填了名称/用途却没填图纸编号则拒绝（同老口径）。
     */
    private DrawingMeta normalizeDrawingMeta(AttachmentDrawingMetaDTO meta) {
        if (meta == null) return DrawingMeta.EMPTY;
        String category = drawingText(meta.getCategory(), 50, "图种");
        String drawingNo = drawingText(meta.getDrawingNo(), 80, "图纸编号");
        String drawingName = drawingText(meta.getDrawingName(), 120, "图纸名称");
        String version = drawingText(meta.getVersion(), 50, "版本");
        String fileRole = drawingText(meta.getFileRole(), 20, "文件用途");
        if (drawingNo == null && (drawingName != null || fileRole != null))
            throw new BusinessException("填写图纸编号后才能关联文件用途及图纸名称");
        if (drawingNo != null) {
            drawingNo = drawingNo.toUpperCase(Locale.ROOT);
            if (version == null) throw new BusinessException("版本不能为空");
            version = version.toUpperCase(Locale.ROOT);
        }
        if (fileRole != null && !"ORIGINAL".equals(fileRole) && !"PRINT".equals(fileRole))
            throw new BusinessException("文件用途只能为原稿或预览打印件");
        return new DrawingMeta(category, drawingNo, drawingName, version, fileRole);
    }

    private String drawingText(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > max) throw new BusinessException(label + "不能超过" + max + "个字符");
        return text;
    }

    private record DrawingMeta(String category, String drawingNo, String drawingName, String version, String fileRole) {
        static final DrawingMeta EMPTY = new DrawingMeta(null, null, null, null, null);
    }

    /** Cursor paging includes recycled rows. Decision is observational, never authorization to delete later. */
    public List<Map<String, Object>> preview(long afterId, int limit) {
        StpUtil.checkRole("admin");
        if (afterId < 0 || limit < 1 || limit > 100) throw new BusinessException("limit应为1至100");
        List<Long> ids = jdbc.queryForList("SELECT id FROM sys_attachment WHERE id>? ORDER BY id LIMIT ?", Long.class, afterId, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : ids) {
            Decision decision = evaluate(id);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("attachmentId", id); row.put("reason", decision.reason()); row.put("eligible", decision.eligible());
            row.put("deadline", decision.deadline()); row.put("physicalDeleteEnabled", false);
            SysAttachment file = decision.file();
            if (file != null) {
                row.put("fileName", file.getFileName()); row.put("fileSize", file.getFileSize());
                row.put("uploadState", file.getUploadState() == null ? null : file.getUploadState().name());
                row.put("uploadOwner", file.getUploadOwner());
                row.put("createTime", file.getCreateTime()); row.put("expiresAt", file.getExpiresAt());
            }
            result.add(row);
            log.info("附件清理预演 actor={} id={} reason={} deadline={} physicalDelete=false", StpUtil.getLoginIdAsString(), id, decision.reason(), decision.deadline());
        }
        return result;
    }

    /**
     * 到期、无有效引用、非受控的附件按「标记删除」移入回收站（可恢复）。
     * 只写 deleted 标记，不删磁盘文件、不放宽删除守卫；误判可由管理员在文件管理页恢复。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recycleEligible(long afterId, int limit) {
        StpUtil.checkRole("admin");
        return recycle(afterId, limit, StpUtil.getLoginIdAsString());
    }

    /**
     * 定时任务专用入口（无人值守）：不校验登录态/角色，判定与「只标记、不删字节」语义与
     * {@link #recycleEligible(long, int)} 完全一致，仅 actor 固定为 {@link #AUTO_ACTOR}。
     * 供 {@code AttachmentCleanupTask} 游标分页调用；绝不调用物理删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> autoRecycleExpired(long afterId, int limit) {
        return recycle(afterId, limit, AUTO_ACTOR);
    }

    private Map<String, Object> recycle(long afterId, int limit, String actor) {
        if (afterId < 0 || limit < 1 || limit > 100) throw new BusinessException("limit应为1至100");
        List<Long> ids = jdbc.queryForList("SELECT id FROM sys_attachment WHERE id>? ORDER BY id LIMIT ?", Long.class, afterId, limit);
        int recycled = 0; long cursor = afterId;
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (Long id : ids) {
            cursor = id;
            Decision decision = evaluate(id);
            if (!decision.eligible()) {
                Map<String, Object> skip = new LinkedHashMap<>();
                skip.put("attachmentId", id); skip.put("reason", decision.reason()); skipped.add(skip);
                continue;
            }
            if (files.logicalDelete(id) > 0) recycled++;
            log.info("附件移入回收站 actor={} id={} reason={} deadline={} physicalDelete=false",
                    actor, id, decision.reason(), decision.deadline());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", ids.size()); result.put("recycled", recycled);
        result.put("skipped", skipped); result.put("nextAfterId", cursor); result.put("physicalDeleteEnabled", false);
        return result;
    }

    /**
     * 定时自动清理的「一次运行」汇总操作日志（无人值守、不依赖登录态）：
     * 仅在本次运行确有 recycle 动作时写入一条 {@code sys_oper_log}，使「何时、自动清了多少」在 DB 长期留存。
     * 写入失败只记 error，不阻断任务。
     */
    public void recordAutoCleanupRun(int scanned, int recycled) {
        try {
            LocalDateTime now = LocalDateTime.now();
            SysOperLog entry = new SysOperLog();
            entry.setUserId(0L);
            entry.setUsername(AUTO_ACTOR);
            entry.setRealName("系统定时任务");
            entry.setTenantId(1L);
            entry.setModule("附件引用");
            entry.setBusinessType(BusinessType.DELETE.getCode());
            entry.setAction("自动清理到期临时附件（移入回收站，不删字节）");
            entry.setBizType("sys_attachment");
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("scanned", scanned);
            detail.put("recycled", recycled);
            detail.put("physicalDelete", false);
            detail.put("actor", AUTO_ACTOR);
            detail.put("cleanedAt", now.toString());
            entry.setDetail(JSONUtil.toJsonStr(detail));
            entry.setStatus(YesNoEnum.YES.getCode());
            entry.setCreateTime(now);
            operLogs.insert(entry);
        } catch (Exception ex) {
            log.error("附件自动清理补写操作日志失败 scanned={} recycled={}", scanned, recycled, ex);
        }
    }

    /**
     * 留痕式释放（省磁盘分支）：删磁盘文件、保留数据库记录（upload_state 置 PURGED）。
     * 对象与清理预演命中的行相同；有有效引用 / 受控或历史引用 / 共享路径 / 已回收 / 未到期 一律跳过。
     * 顺序：先删字节成功，再写状态；删字节失败保持原样（宁可留文件，不留「状态说删了其实没删」）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> purgeEligible(long afterId, int limit) {
        StpUtil.checkRole("admin");
        if (afterId < 0 || limit < 1 || limit > 100) throw new BusinessException("limit应为1至100");
        List<Long> ids = jdbc.queryForList("SELECT id FROM sys_attachment WHERE id>? ORDER BY id LIMIT ?", Long.class, afterId, limit);
        Path root = Paths.get(uploadBasePath).toAbsolutePath().normalize();
        int purged = 0; long cursor = afterId;
        List<Map<String, Object>> skipped = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        for (Long id : ids) {
            cursor = id;
            Decision decision = evaluate(id);
            SysAttachment file = decision.file();
            if (!decision.eligible() || file == null) {
                Map<String, Object> skip = new LinkedHashMap<>();
                skip.put("attachmentId", id); skip.put("reason", decision.reason()); skipped.add(skip);
                continue;
            }
            Path target = root.resolve(file.getFilePath()).normalize();
            if (!target.startsWith(root)) {
                log.error("附件释放被拒（路径越界）id={} filePath={}", id, file.getFilePath());
                Map<String, Object> bad = new LinkedHashMap<>();
                bad.put("attachmentId", id); bad.put("reason", "PATH_OUT_OF_ROOT"); failed.add(bad);
                continue;
            }
            try {
                Files.deleteIfExists(target); // 先删字节：删不掉就什么都不改
            } catch (java.io.IOException ex) {
                log.error("附件释放失败（保留文件与状态）id={} path={}", id, target, ex);
                Map<String, Object> bad = new LinkedHashMap<>();
                bad.put("attachmentId", id); bad.put("reason", "FILE_DELETE_FAILED"); failed.add(bad);
                continue;
            }
            if (jdbc.update("UPDATE sys_attachment SET upload_state='PURGED' WHERE id=? AND deleted=0", id) > 0) {
                purged++;
                // 按文件补写操作日志：biz_id=attachmentId，action/detail 带 fileName 与 sha256。
                LocalDateTime purgedAt = LocalDateTime.now();
                recordPurgeLog(id, file, purgedAt);
                log.info("附件字节已释放 actor={} id={} fileName={} sha256={} state=PURGED（记录保留）",
                        StpUtil.getLoginIdAsString(), id, file.getFileName(), file.getSha256());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanned", ids.size()); result.put("purged", purged);
        result.put("skipped", skipped); result.put("failed", failed);
        result.put("nextAfterId", cursor); result.put("recordsKept", true);
        return result;
    }

    /**
     * 释放成功后按文件补写一条操作日志（sink 到 {@code sys_oper_log}）：
     * biz_id=attachmentId，action/detail 带 fileName 与 sha256，使「何时、谁、释放了哪个文件」在 DB 长期留存。
     * 与状态更新同一事务（正常情况下 Atomic）；写入失败只记 error 不阻断释放——磁盘字节已不可恢复，
     * 此时回滚状态反而会与实际不符（状态说没删、文件其实已删）。应用日志 log.info 会被 LogCleanTask 清掉，故必须落库。
     */
    private void recordPurgeLog(Long id, SysAttachment file, LocalDateTime purgedAt) {
        try {
            SysOperLog entry = new SysOperLog();
            entry.setUserId(SecurityUtils.getUserId());
            entry.setUsername(SecurityUtils.getUsername());
            entry.setRealName(SecurityUtils.getRealName());
            entry.setTenantId(SecurityUtils.getTenantId());
            entry.setModule("附件引用");
            entry.setBusinessType(BusinessType.DELETE.getCode());
            entry.setAction("释放附件字节（删文件、保留记录）");
            entry.setBizType("sys_attachment");
            entry.setBizId(String.valueOf(id));
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("attachmentId", id);
            detail.put("fileName", file.getFileName());
            detail.put("fileSize", file.getFileSize());
            detail.put("sha256", file.getSha256());
            detail.put("purgedAt", purgedAt.toString());
            entry.setDetail(JSONUtil.toJsonStr(detail));
            entry.setStatus(YesNoEnum.YES.getCode());
            entry.setCreateTime(purgedAt);
            operLogs.insert(entry);
        } catch (Exception ex) {
            log.error("释放附件字节补写操作日志失败 id={} fileName={}", id, file.getFileName(), ex);
        }
    }

    /** 单行判定（只读）。eligible 仅表示「可移入回收站」，不代表允许物理删除。 */
    private Decision evaluate(Long id) {
        SysAttachment file = files.selectIncludingDeleted(id);
        if (file == null) return new Decision("ROW_MISSING", null, false, null);
        if (Boolean.TRUE.equals(file.getDeleted())) return new Decision("ALREADY_RECYCLED", null, false, file);
        Long active = jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE attachment_id=? AND deleted=0", Long.class, id);
        if (active != null && active > 0) return new Decision("ACTIVE_REFERENCE", null, false, file);
        if (access.protectedFile(file)) return new Decision("CONTROLLED_OR_HISTORICAL_REFERENCE", null, false, file);
        if (file.getUploadState() == null || file.getUploadState() == AttachmentUploadState.LEGACY)
            return new Decision("LEGACY_REVIEW_REQUIRED", null, false, file);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment WHERE file_path=? AND id<>?", Long.class, file.getFilePath(), id) > 0)
            return new Decision("SHARED_PATH_REVIEW_REQUIRED", null, false, file);
        List<LocalDateTime> dates = jdbc.query("SELECT MAX(deleted_at) AS last_unlinked,COUNT(*) AS history_count FROM sys_attachment_ref WHERE attachment_id=?",
                (rs, row) -> {
                    var date = rs.getTimestamp("last_unlinked");
                    if (rs.getLong("history_count") > 0) return date == null ? null : date.toLocalDateTime().plusDays(30);
                    return file.getUploadState() == AttachmentUploadState.TEMP && file.getCreateTime() != null
                            ? file.getCreateTime().plusDays(7) : null;
                }, id);
        LocalDateTime deadline = dates.isEmpty() ? null : dates.get(0);
        // Never shorten retention due to a stale state or metadata clock.
        if (deadline != null && file.getExpiresAt() != null && file.getExpiresAt().isAfter(deadline)) deadline = file.getExpiresAt();
        if (deadline == null) return new Decision("RETENTION_START_UNKNOWN", null, false, file);
        if (deadline.isAfter(LocalDateTime.now())) return new Decision("RETENTION_PERIOD", deadline, false, file);
        return new Decision("ELIGIBLE_PREVIEW_ONLY", deadline, true, file);
    }

    private record Decision(String reason, LocalDateTime deadline, boolean eligible, SysAttachment file) {}
}
