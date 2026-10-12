package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
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
    private final SysAttachmentMapper files;
    private final AttachmentAccessService access;
    private final JdbcTemplate jdbc;
    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;

    @Transactional(rollbackFor = Exception.class)
    public Long upload(MultipartFile input, String requestKey) {
        StpUtil.checkLogin();
        if (input == null || input.isEmpty()) throw new BusinessException("请选择文件");
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9_-]{16,100}"))
            throw new BusinessException("上传必须提供16至100位requestKey，重试使用同一键");
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
        file.setUploadState(AttachmentUploadState.TEMP); file.setExpiresAt(LocalDateTime.now().plusDays(7));
        file.setUploadOwner(owner); file.setUploadKey(key); file.setCreateBy(owner); file.setCreateTime(LocalDateTime.now());
        files.insert(file);
        return file.getId();
    }

    private String digest(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    /** Cursor paging includes recycled rows. Decision is observational, never authorization to delete later. */
    public List<Map<String, Object>> preview(long afterId, int limit) {
        StpUtil.checkRole("admin");
        if (afterId < 0 || limit < 1 || limit > 100) throw new BusinessException("limit应为1至100");
        List<Long> ids = jdbc.queryForList("SELECT id FROM sys_attachment WHERE id>? ORDER BY id LIMIT ?", Long.class, afterId, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : ids) {
            SysAttachment file = files.selectIncludingDeleted(id);
            String reason;
            Long active = jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE attachment_id=? AND deleted=0", Long.class, id);
            LocalDateTime deadline = null;
            if (active != null && active > 0) reason = "ACTIVE_REFERENCE";
            else if (access.protectedFile(file)) reason = "CONTROLLED_OR_HISTORICAL_REFERENCE";
            else if (file.getUploadState() == null || file.getUploadState() == AttachmentUploadState.LEGACY)
                reason = "LEGACY_REVIEW_REQUIRED";
            else if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment WHERE file_path=? AND id<>?", Long.class, file.getFilePath(), id) > 0)
                reason = "SHARED_PATH_REVIEW_REQUIRED";
            else {
                List<LocalDateTime> dates = jdbc.query("SELECT MAX(deleted_at) AS last_unlinked,COUNT(*) AS history_count FROM sys_attachment_ref WHERE attachment_id=?",
                    (rs, row) -> {
                        var date = rs.getTimestamp("last_unlinked");
                        if (rs.getLong("history_count") > 0) return date == null ? null : date.toLocalDateTime().plusDays(30);
                        return file.getUploadState() == AttachmentUploadState.TEMP && file.getCreateTime() != null
                            ? file.getCreateTime().plusDays(7) : null;
                    }, id);
                deadline = dates.get(0);
                // Never shorten retention due to a stale state or metadata clock.
                if (deadline != null && file.getExpiresAt() != null && file.getExpiresAt().isAfter(deadline)) deadline = file.getExpiresAt();
                reason = deadline == null ? "RETENTION_START_UNKNOWN" : deadline.isAfter(LocalDateTime.now()) ? "RETENTION_PERIOD" : "ELIGIBLE_PREVIEW_ONLY";
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("attachmentId", id); row.put("reason", reason); row.put("eligible", "ELIGIBLE_PREVIEW_ONLY".equals(reason));
            row.put("deadline", deadline); row.put("physicalDeleteEnabled", false); result.add(row);
            log.info("附件清理预演 actor={} id={} reason={} deadline={} physicalDelete=false", StpUtil.getLoginIdAsString(), id, reason, deadline);
        }
        return result;
    }
}
