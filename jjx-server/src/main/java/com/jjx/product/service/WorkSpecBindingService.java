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

    /** 当前已发布版本ID；无已发布版本时返回 null（销售采用版本侧允许为空，不报错）。 */
    public Long latestPublishedId(Long productId) {
        if (productId == null) return null;
        ProductWorkSpecVersion version = versions.selectLatestPublished(productId);
        return version == null ? null : version.getId();
    }

    /**
     * 校验显式绑定的版本：必须存在、属同产品且已发布。
     * <p>versionId 为 null 视为「未绑定」——历史未知版本不自动推断，直接报错。</p>
     */
    public Long requirePublished(Long productId, Long versionId) {
        if (versionId == null) {
            throw new BusinessException("工单必须绑定已发布的作业规范版本；历史未知版本不能自动推断");
        }
        ProductWorkSpecVersion version = versions.selectById(versionId);
        if (version == null) {
            throw new BusinessException("所绑作业规范版本不存在（ID " + versionId + "），请重新选择该产品已发布的版本");
        }
        if (!Objects.equals(productId, version.getProductId())) {
            throw new BusinessException("所绑作业规范版本不属于该产品（版本 " + version.getVersionNo()
                    + "），请选择该产品已发布的版本");
        }
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw new BusinessException("所绑作业规范版本未发布（版本 " + version.getVersionNo() + "，当前状态 "
                    + version.getStatus() + "），请先在产品作业规范页发布版本后再下达");
        }
        return version.getId();
    }

    /**
     * 下达时确定绑定版本（工单新建、计划转工单、状态推进到下达后）：
     * 显式指定则校验；未指定则取该产品当前已发布版本。
     * 两者都取不到有效版本时按具体原因区分文案——「该产品无已发布版本」引导先去发布。
     */
    public Long bindOnIssue(Long productId, Long requestedId) {
        Long targetId = requestedId != null ? requestedId : latestPublishedId(productId);
        if (targetId == null) {
            throw new BusinessException("该产品无已发布的作业规范版本，请先在产品作业规范页发布版本后再下达");
        }
        return requirePublished(productId, targetId);
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
