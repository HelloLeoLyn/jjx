package com.jjx.purchase.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.service.AttachmentRefService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采购发票文件接入公共附件（来源 {@code purchase_invoice}）。
 *
 * <p>语义照收货票据（dev-20261014-018）镜像，按发票简化：文件先临时上传（TEMP），
 * 发票核销(verify)通过时，在**核销事务内**把临时附件建成引用（source_type=purchase_invoice, source_id=invoiceId）。
 * 调用方的核销事务负责统一提交/回滚，故 prepare/complete 均要求 MANDATORY 传播。
 */
@Service
@RequiredArgsConstructor
public class PurchaseInvoiceAttachmentService {
    private final JdbcTemplate jdbc;
    private final SysAttachmentMapper files;
    private final AttachmentRefService refs;

    /** 发票文件允许类型：图片 / PDF / Word / Excel（与老临时上传通道一致）。 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp",
        "application/pdf",
        "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final long MAX_SIZE = 20L * 1024 * 1024;

    /**
     * 核销前校验：锁发票行、逐个校验附件是本人未过期的临时文件（先校验后落库，失败即回滚核销）。
     * 无附件（空集合）时直接放行，不影响“无票据核销”。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void prepare(Long documentId, List<Long> attachmentIds) {
        StpUtil.checkLogin();
        List<Long> ids = ids(attachmentIds);
        if (ids.isEmpty()) return;
        StpUtil.checkPermission("purchase:invoice:doc:add");
        // 串行化同一发票的核销：并发核销在发票行锁处排队，第二次因状态已变而失败。
        if (jdbc.queryForList("SELECT document_id FROM purchase_document WHERE document_id=? FOR UPDATE", Long.class, documentId).isEmpty())
            throw new BusinessException("采购发票不存在");
        // 固定顺序锁附件，阻止同一文件同时用于两次核销；仅接受本人未过期的临时文件。
        for (Long id : ids) {
            SysAttachment file = files.selectActiveForUpdate(id);
            if (file == null || file.getUploadState() != AttachmentUploadState.TEMP
                    || !StpUtil.getLoginIdAsString().equals(file.getUploadOwner())
                    || file.getExpiresAt() == null || !file.getExpiresAt().isAfter(LocalDateTime.now()))
                throw new BusinessException("发票文件不是本人有效的临时附件，请重新上传");
            String type = file.getFileType() == null ? "" : file.getFileType().toLowerCase();
            if (!ALLOWED_TYPES.contains(type)
                    || file.getFileSize() == null || file.getFileSize() <= 0 || file.getFileSize() > MAX_SIZE)
                throw new BusinessException("发票文件仅支持20MB以内的图片/PDF/Word/Excel");
        }
    }

    /** 核销事务内建立引用（源单号/链路由 AttachmentRefService 回填）。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long documentId, List<Long> attachmentIds) {
        for (Long id : ids(attachmentIds)) {
            AttachmentRefDTO ref = new AttachmentRefDTO();
            ref.setAttachmentId(id);
            ref.setSourceType("purchase_invoice");
            ref.setSourceId(documentId);
            ref.setRemark("采购发票文件；发票ID=" + documentId);
            refs.create(ref);
        }
    }

    /** 按发票归集已建立的引用（发票详情/列表展示）。 */
    public List<Map<String, Object>> list(Long documentId) {
        StpUtil.checkPermission("purchase:invoice:doc:view");
        return jdbc.queryForList("""
            SELECT r.id AS refId, a.id AS attachmentId, a.file_name AS fileName,
                   a.file_size AS fileSize, d.document_no AS invoiceNo
            FROM sys_attachment_ref r
            JOIN sys_attachment a ON a.id=r.attachment_id AND a.deleted=0
            JOIN purchase_document d ON d.document_id=r.source_id AND d.document_type='invoice'
            WHERE r.source_type='purchase_invoice' AND r.deleted=0 AND d.document_id=?
            ORDER BY r.id DESC
            """, documentId);
    }

    private List<Long> ids(List<Long> attachmentIds) {
        if (attachmentIds == null) return List.of();
        if (attachmentIds.size() > 50 || attachmentIds.stream().anyMatch(id -> id == null || id <= 0))
            throw new BusinessException("发票附件参数无效");
        return attachmentIds.stream().distinct().sorted().toList();
    }

    /**
     * 从发票的 file_url 里解析“新建发票时上传的公共附件”ID（形如 /system/attachment/download/{id}）。
     * 用于核销时把新建阶段上传、尚未建引用的文件一并绑定；老磁盘路径的行返回 null。
     */
    public Long carriedAttachmentId(String fileUrl) {
        if (fileUrl == null) return null;
        java.util.regex.Matcher m = ATTACHMENT_URL.matcher(fileUrl.trim());
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    private static final java.util.regex.Pattern ATTACHMENT_URL =
        java.util.regex.Pattern.compile("/system/attachment/download/(\\d+)$");
}
