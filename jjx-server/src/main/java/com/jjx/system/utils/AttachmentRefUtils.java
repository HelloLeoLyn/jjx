package com.jjx.system.utils;

import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 附件引用 ID 入参校验（附件双写收口 dev-20261014-028）。
 *
 * <p>业务接口由「老字段（附件 file id 列表）」改为「附件引用 ID（sys_attachment_ref.id）」时统一走这里：
 * 拒老字段（宁可报错不可静默丢留痕）+ 校验引用归属本单据（防串单）。
 * 样板：b69bc97f（订单审核 OrderStatusServiceImpl.approveOrder）。
 */
public final class AttachmentRefUtils {

    private AttachmentRefUtils() {
    }

    /** 老字段（附件 file id 列表）只要非空即拒绝，强制调用方改传引用 ID。 */
    public static void rejectLegacyField(String legacyIds, String message) {
        if (legacyIds != null && !legacyIds.isBlank()) {
            throw new BusinessException(message);
        }
    }

    /**
     * 校验引用 ID：非空、数量受限、无重复，且每条引用确实属于该来源单据。
     *
     * @param mapper     引用表 mapper
     * @param refIds     引用 ID 列表（可为空）
     * @param sourceType 来源类型（与引用表 source_type 一致，如 quotation / sample）
     * @param sourceId   来源单据 ID
     * @param message    引用不归属本单据时的提示
     */
    public static void assertRefsBelong(SysAttachmentRefMapper mapper, List<Long> refIds,
                                        String sourceType, Long sourceId, String message) {
        if (refIds == null || refIds.isEmpty()) {
            return;
        }
        if (refIds.size() > 100 || refIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException("附件引用无效或数量超过限制");
        }
        Set<Long> unique = new HashSet<>(refIds);
        if (unique.size() != refIds.size()) {
            throw new BusinessException("附件引用重复");
        }
        for (Long refId : refIds) {
            SysAttachmentRef ref = mapper.selectOne(Wrappers.<SysAttachmentRef>lambdaQuery()
                    .eq(SysAttachmentRef::getId, refId).last("FOR UPDATE"));
            if (ref == null || Boolean.TRUE.equals(ref.getDeleted()) || !sourceType.equals(ref.getSourceType())
                    || ref.getSourceId() == null || !ref.getSourceId().equals(sourceId)) {
                throw new BusinessException(message);
            }
        }
    }

    public static String toJson(List<Long> refIds) {
        return refIds == null || refIds.isEmpty() ? null : refIds.stream()
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
