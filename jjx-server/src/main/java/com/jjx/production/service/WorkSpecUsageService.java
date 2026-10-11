package com.jjx.production.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.mapper.ProductWorkSpecVersionMapper;
import com.jjx.production.domain.dto.WorkSpecUsageRegisterDTO;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.domain.entity.WorkSpecUsage;
import com.jjx.production.domain.vo.WorkSpecUsageVO;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.mapper.WorkSpecUsageMapper;
import com.jjx.product.service.WorkSpecBindingService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单换版执行区间服务（dev-20261011-013）
 * 换版登记 = 校验同产品已发布版本 → 写执行区间 → 把工单「当前执行版本」更新为新版本。
 * 历史段落在 work_spec_usage；工单单一字段只表示「当前执行」，禁止用来代替履历。
 */
@Service
@RequiredArgsConstructor
public class WorkSpecUsageService {

    private final WorkSpecUsageMapper usageMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final ProductWorkSpecVersionMapper versionMapper;
    private final WorkSpecBindingService workSpecBindingService;

    /**
     * 登记换版执行区间。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkSpecUsageVO register(WorkSpecUsageRegisterDTO dto) {
        ProductionOrder order = productionOrderMapper.selectForUpdate(dto.getWorkOrderId());
        if (order == null) {
            throw new BusinessException("生产工单不存在: " + dto.getWorkOrderId());
        }
        if (!"WORK_ORDER".equalsIgnoreCase(order.getOrderType())) {
            throw new BusinessException("仅生产工单可登记换版执行区间");
        }
        // 版本必须属同产品且已发布（复用绑定服务；历史未知/别产品/未发布一律拒绝）
        Long versionId = workSpecBindingService.requirePublished(order.getProductId(), dto.getSpecVersionId());

        if (dto.getQtyFrom() != null && dto.getQtyTo() != null
                && dto.getQtyFrom().compareTo(dto.getQtyTo()) > 0) {
            throw new BusinessException("数量区间起点不得大于终点");
        }
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && dto.getStartTime().isAfter(dto.getEndTime())) {
            throw new BusinessException("生效起止时间起点不得晚于终点");
        }

        WorkSpecUsage usage = new WorkSpecUsage();
        usage.setWorkOrderId(order.getOrderId());
        usage.setSpecVersionId(versionId);
        usage.setQtyFrom(dto.getQtyFrom());
        usage.setQtyTo(dto.getQtyTo());
        usage.setStartTime(dto.getStartTime());
        usage.setEndTime(dto.getEndTime());
        usage.setChangeReason(dto.getChangeReason());
        usage.setApprovedBy(resolveApprovedBy(dto.getApprovedBy()));
        usage.setApprovedAt(LocalDateTime.now());
        usage.setDeleted(0);
        usageMapper.insert(usage);

        // 换版把工单「当前执行版本」更新为新版本（唯一允许改变已下达绑定的路径）
        ProductionOrder update = new ProductionOrder();
        update.setOrderId(order.getOrderId());
        update.setWorkSpecVersionId(versionId);
        productionOrderMapper.updateById(update);

        return getVo(usage.getId());
    }

    /** 某工单的执行区间列表（老→新）。 */
    @Transactional(readOnly = true)
    public List<WorkSpecUsageVO> list(Long workOrderId) {
        if (workOrderId == null) {
            throw new BusinessException("生产工单ID不能为空");
        }
        return usageMapper.selectVoByWorkOrderId(workOrderId);
    }

    /** 删除一条执行区间记录（逻辑删除；不改动工单当前执行版本）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WorkSpecUsage usage = usageMapper.selectById(id);
        if (usage == null) {
            throw new BusinessException("执行区间记录不存在: " + id);
        }
        usageMapper.deleteById(id);
    }

    // ==================== 内部 ====================

    private WorkSpecUsageVO getVo(Long id) {
        WorkSpecUsage usage = usageMapper.selectById(id);
        if (usage == null) {
            throw new BusinessException("执行区间记录不存在: " + id);
        }
        ProductWorkSpecVersion version = versionMapper.selectById(usage.getSpecVersionId());
        WorkSpecUsageVO vo = new WorkSpecUsageVO();
        vo.setId(usage.getId());
        vo.setWorkOrderId(usage.getWorkOrderId());
        vo.setSpecVersionId(usage.getSpecVersionId());
        vo.setVersionNo(version == null ? null : version.getVersionNo());
        vo.setVersionStatus(version == null ? null : version.getStatus());
        vo.setQtyFrom(usage.getQtyFrom());
        vo.setQtyTo(usage.getQtyTo());
        vo.setStartTime(usage.getStartTime());
        vo.setEndTime(usage.getEndTime());
        vo.setChangeReason(usage.getChangeReason());
        vo.setApprovedBy(usage.getApprovedBy());
        vo.setApprovedAt(usage.getApprovedAt());
        vo.setCreateBy(usage.getCreateBy());
        vo.setCreateTime(usage.getCreateTime());
        return vo;
    }

    private String resolveApprovedBy(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        try {
            return SecurityUtils.getDisplayName();
        } catch (Exception e) {
            return null;
        }
    }
}
