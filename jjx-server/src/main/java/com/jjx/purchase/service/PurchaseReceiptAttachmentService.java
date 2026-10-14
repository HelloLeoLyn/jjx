package com.jjx.purchase.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.domain.dto.PurchaseOrderReceiveDTO;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 收货票据只绑定本次入库凭证；调用方的收货事务负责统一提交/回滚。 */
@Service
@RequiredArgsConstructor
public class PurchaseReceiptAttachmentService {
    private final JdbcTemplate jdbc;
    private final SysAttachmentMapper files;
    private final AttachmentRefService refs;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp");

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean prepare(PurchaseOrderReceiveDTO dto) {
        StpUtil.checkLogin();
        // 串行化同一订单的批量收货，重试查询必须在订单锁之后。
        if (jdbc.queryForList("SELECT order_id FROM purchase_order WHERE order_id=? FOR UPDATE", Long.class, dto.getOrderId()).isEmpty())
            throw new BusinessException("采购订单不存在");
        List<Long> ids = ids(dto);
        if (!ids.isEmpty()) StpUtil.checkPermission("purchase:receipt:doc:add");
        String key = dto.getReceiptRequestKey();
        if (key != null) {
            if (!key.matches("[A-Za-z0-9-]{16,64}")) throw new BusinessException("收货草稿标识无效");
            String prefix = prefix(dto);
            List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT inbound_id,inbound_no,remark FROM inventory_inbound_order WHERE source_type='PURCHASE' AND source_id=? AND remark LIKE ? FOR UPDATE",
                dto.getOrderId(), prefix + "%");
            if (!existing.isEmpty()) {
                Map<String, Object> row = existing.get(0);
                if (!marker(dto).equals(row.get("remark"))) throw new BusinessException("本次收货已经确认，不能修改后重复提交，请重新打开收货窗口");
                dto.setInboundId(((Number) row.get("inbound_id")).longValue());
                dto.setInboundNo((String) row.get("inbound_no"));
                return true;
            }
        } else if (!ids.isEmpty()) {
            throw new BusinessException("上传收货票据必须提供收货草稿标识");
        }
        // 固定顺序锁附件，阻止同一文件同时用于两次收货；仅接受本人未过期的临时图片。
        for (Long id : ids) {
            SysAttachment file = files.selectActiveForUpdate(id);
            if (file == null || file.getUploadState() != AttachmentUploadState.TEMP
                    || !StpUtil.getLoginIdAsString().equals(file.getUploadOwner())
                    || file.getExpiresAt() == null || !file.getExpiresAt().isAfter(LocalDateTime.now()))
                throw new BusinessException("票据不是本人有效的临时附件，请重新上传");
            if (!IMAGE_TYPES.contains(file.getFileType() == null ? "" : file.getFileType())
                    || file.getFileSize() == null || file.getFileSize() <= 0 || file.getFileSize() > 10L * 1024 * 1024)
                throw new BusinessException("收货票据仅支持10MB以内的 JPG/PNG/GIF/BMP/WebP 图片");
        }
        return false;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(PurchaseOrderReceiveDTO dto, Long inboundId) {
        if (inboundId == null) {
            if (dto.getReceiptRequestKey() != null || !ids(dto).isEmpty()) throw new BusinessException("本次收货未生成入库凭证，票据未绑定");
            return;
        }
        String inboundNo = jdbc.queryForObject("SELECT inbound_no FROM inventory_inbound_order WHERE inbound_id=? AND source_type='PURCHASE' AND source_id=?",
            String.class, inboundId, dto.getOrderId());
        for (Long id : ids(dto)) {
            AttachmentRefDTO ref = new AttachmentRefDTO();
            ref.setAttachmentId(id);
            ref.setSourceType("purchase_receipt");
            ref.setSourceId(inboundId);
            ref.setRemark("采购收货票据；订单ID=" + dto.getOrderId());
            refs.create(ref);
        }
        // 新建凭证的备注保存确认凭据（草稿键+请求摘要），不增加第二套业务数量字段。
        if (dto.getReceiptRequestKey() != null) jdbc.update(
            "UPDATE inventory_inbound_order SET remark=? WHERE inbound_id=?", marker(dto), inboundId);
        dto.setInboundId(inboundId);
        dto.setInboundNo(inboundNo);
    }

    public List<Map<String, Object>> list(Long orderId) {
        StpUtil.checkPermission("purchase:receipt:doc:view");
        return jdbc.queryForList("""
            SELECT r.id AS refId, a.id AS attachmentId, a.file_name AS fileName,
                   a.file_size AS fileSize, i.inbound_no AS inboundNo
            FROM sys_attachment_ref r
            JOIN sys_attachment a ON a.id=r.attachment_id AND a.deleted=0
            JOIN inventory_inbound_order i ON i.inbound_id=r.source_id AND i.source_type='PURCHASE'
            WHERE r.source_type='purchase_receipt' AND r.deleted=0 AND i.source_id=?
            ORDER BY i.inbound_id DESC, r.id DESC
            """, orderId);
    }

    private List<Long> ids(PurchaseOrderReceiveDTO dto) {
        List<Long> ids = dto.getAttachmentIds();
        if (ids == null) return List.of();
        if (ids.size() > 50 || ids.stream().anyMatch(id -> id == null || id <= 0)) throw new BusinessException("票据附件参数无效");
        return ids.stream().distinct().sorted().toList();
    }

    private String prefix(PurchaseOrderReceiveDTO dto) {
        return "采购收货确认[" + StpUtil.getLoginIdAsString() + ":" + dto.getReceiptRequestKey() + "] ";
    }

    private String marker(PurchaseOrderReceiveDTO dto) {
        String items = dto.getItems().stream()
            .map(i -> i.getItemId() + ":" + i.getReceivedQuantity().stripTrailingZeros().toPlainString())
            .sorted().collect(Collectors.joining(","));
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (dto.getOrderId() + "|" + items + "|" + ids(dto)).getBytes(StandardCharsets.UTF_8)));
            return prefix(dto) + digest;
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
