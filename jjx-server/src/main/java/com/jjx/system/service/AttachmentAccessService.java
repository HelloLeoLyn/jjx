package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttachmentAccessService {
    private final AttachmentSourcePolicy sources;
    private final SysAttachmentRefMapper refs;
    private final JdbcTemplate jdbc;

    public void checkRead(SysAttachment file) {
        StpUtil.checkLogin();
        if (file == null || Boolean.TRUE.equals(file.getDeleted())) throw new BusinessException("附件不存在");
        if (!canRead(file)) throw new BusinessException("无权查看附件或历史来源尚未确认");
    }

    public boolean canRead(SysAttachment file) {
        if (file == null || Boolean.TRUE.equals(file.getDeleted()) || !StpUtil.isLogin()) return false;
        if (file.getUploadState() == AttachmentUploadState.TEMP || file.getUploadState() == AttachmentUploadState.ORPHAN)
            return StpUtil.getLoginIdAsString().equals(file.getUploadOwner()) && file.getExpiresAt() != null
                && file.getExpiresAt().isAfter(java.time.LocalDateTime.now());
        List<SysAttachmentRef> active = refs.selectList(Wrappers.<SysAttachmentRef>lambdaQuery()
            .eq(SysAttachmentRef::getAttachmentId, file.getId()).last("FOR SHARE"));
        if (active.stream().anyMatch(r -> sources.canRead(r.getSourceType(), r.getSourceId()))) return true;
        // An explicitly detached legacy association must never continue to grant access.
        Long history = jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE attachment_id=? AND source_type=? AND source_id=?",
            Long.class, file.getId(), file.getBizType() == null ? null : sources.canonical(file.getBizType()), file.getBizId());
        return history != null && history == 0 && sources.canRead(file.getBizType(), file.getBizId());
    }

    public boolean protectedFile(SysAttachment file) {
        if (YesNoEnum.YES.getCode().equals(file.getIsControlled()) || YesNoEnum.YES.getCode().equals(file.getReleased())
                || YesNoEnum.YES.getCode().equals(file.getIsCurrent())) return true;
        // All versions, including archived/deleted ones, retain their original file identity.
        // quality_template_registry 的 file_id 额外要求「附件建档时间不晚于模板建档时间」：
        // 时间条件只是异常防护——挡住历史 ID 被后来新建的附件复用而造成的误认（V1-A 实测 19 条异常引用）。
        // 它**不构成附件身份证明**：时间匹配不等于内容一致；身份一致需要绑定时的内容指纹（sha256），
        // 现表未存该字段，故此类引用仍需人工复核（见 jjx-docs/history 的对账清单）。
        Long hard = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM product_work_spec_item WHERE resource_type IN ('DRAWING','DOCSET_FILE') AND source_id=?) + (SELECT COUNT(*) FROM quality_template_registry q WHERE q.file_id=? AND (SELECT a2.create_time FROM sys_attachment a2 WHERE a2.id=q.file_id) <= q.create_time) + (SELECT COUNT(*) FROM engineering_film WHERE file_id=?) + (SELECT COUNT(*) FROM product WHERE JSON_UNQUOTE(JSON_EXTRACT(work_spec_json,'$.structureFileId'))=?)",
            Long.class, file.getId(), file.getId(), file.getId(), String.valueOf(file.getId()));
        if (hard != null && hard > 0) return true;
        Long reviewedRefs = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref r JOIN review_flow f ON f.biz_type=r.source_type "
                + "AND f.biz_id=r.source_id AND JSON_CONTAINS(f.attachment_ref_ids, CAST(r.id AS JSON)) "
                + "WHERE r.attachment_id=? AND f.attachment_ref_ids IS NOT NULL",
            Long.class, file.getId());
        if (reviewedRefs != null && reviewedRefs > 0) return true;
        // 历史证据按引用 ID 精确匹配；即使引用已解除，文件仍受保护。
        Long quotationRefs = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref r JOIN sales_quotation_flow f ON r.source_type='quotation' "
                + "AND f.quotation_id=r.source_id AND JSON_CONTAINS(f.attachment_ref_ids, CAST(r.id AS JSON)) "
                + "WHERE r.attachment_id=?", Long.class, file.getId());
        if (quotationRefs != null && quotationRefs > 0) return true;
        Long sampleRefs = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref r JOIN sales_sample_round f ON r.source_type='sample' "
                + "AND f.order_id=r.source_id AND JSON_CONTAINS(f.attachment_ref_ids, CAST(r.id AS JSON)) "
                + "WHERE r.attachment_id=?", Long.class, file.getId());
        return sampleRefs != null && sampleRefs > 0;
    }

    public void checkUnlink(SysAttachment file) {
        if (protectedFile(file)) throw new BusinessException("受控、已发布或历史硬引用附件禁止解除；需由原业务流程处理");
    }
}
