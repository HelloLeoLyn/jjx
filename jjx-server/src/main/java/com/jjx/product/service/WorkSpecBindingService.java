package com.jjx.product.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.mapper.ProductWorkSpecVersionMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.ProductionOrderStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** 版本选择仅用于新建/正式下达；历史读取永远使用持久化ID。 */
@Service
@RequiredArgsConstructor
public class WorkSpecBindingService {
    private final ProductWorkSpecVersionMapper versions;

    public Long latestPublishedId(Long productId) {
        if (productId == null) return null;
        ProductWorkSpecVersion version = versions.selectLatestPublished(productId);
        return version == null ? null : version.getId();
    }

    public Long requirePublished(Long productId, Long versionId) {
        ProductWorkSpecVersion version = versionId == null ? null : versions.selectById(versionId);
        if (version == null || !Objects.equals(productId, version.getProductId())
                || !"PUBLISHED".equals(version.getStatus())) {
            throw new BusinessException("必须绑定同产品的已发布作业规范版本；历史未知版本不能自动推断");
        }
        return version.getId();
    }

    public Long bindOnIssue(Long productId, Long requestedId) {
        return requirePublished(productId, requestedId == null ? latestPublishedId(productId) : requestedId);
    }

    public boolean isBeforeIssue(Integer status) {
        return ProductionOrderStatusEnum.DRAFT.getValue().equals(status)
                || ProductionOrderStatusEnum.PENDING_APPROVAL.getValue().equals(status)
                || ProductionOrderStatusEnum.REJECTED.getValue().equals(status);
    }

    /** 覆盖手工编辑和状态动作，已下达版本（含历史NULL）不可覆盖。 */
    public void prepareUpdate(ProductionOrder old, ProductionOrder changed) {
        if (!"WORK_ORDER".equalsIgnoreCase(old.getOrderType())) return;
        if (!isBeforeIssue(old.getOrderStatus()) || old.getActualStartTime() != null) {
            if (!Objects.equals(old.getWorkSpecVersionId(), changed.getWorkSpecVersionId())
                    || !Objects.equals(old.getProductId(), changed.getProductId())) {
                throw new BusinessException("工单已下达，禁止修改产品或绑定版本；换版须走执行区间流程");
            }
            if (isBeforeIssue(changed.getOrderStatus())) {
                throw new BusinessException("已下达工单不得退回下达前状态以重新绑定版本");
            }
            if (ProductionOrderStatusEnum.IN_PROGRESS.getValue().equals(changed.getOrderStatus())
                    && !Objects.equals(old.getOrderStatus(), changed.getOrderStatus())) {
                requirePublished(changed.getProductId(), changed.getWorkSpecVersionId());
            }
            return;
        }
        if (changed.getWorkSpecVersionId() != null) {
            requirePublished(changed.getProductId(), changed.getWorkSpecVersionId());
        }
        if (!isBeforeIssue(changed.getOrderStatus())
                && !ProductionOrderStatusEnum.CANCELLED.getValue().equals(changed.getOrderStatus())) {
            changed.setWorkSpecVersionId(bindOnIssue(changed.getProductId(), changed.getWorkSpecVersionId()));
        }
    }
}
