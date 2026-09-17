package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.quality.domain.entity.QualitySamplingPlan;
import com.jjx.quality.mapper.QualitySamplingPlanMapper;
import com.jjx.quality.service.QualitySamplingPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 抽样方案服务实现 —— dev-20260917-002
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualitySamplingPlanServiceImpl extends ServiceImpl<QualitySamplingPlanMapper, QualitySamplingPlan>
        implements QualitySamplingPlanService {

    private final QualitySamplingPlanMapper planMapper;

    @Override
    public List<QualitySamplingPlan> listAll() {
        return planMapper.selectList(new LambdaQueryWrapper<QualitySamplingPlan>()
                .orderByAsc(QualitySamplingPlan::getLotType)
                .orderByAsc(QualitySamplingPlan::getAqlValue)
                .orderByAsc(QualitySamplingPlan::getLotMin));
    }

    @Override
    public QualitySamplingPlan savePlan(QualitySamplingPlan plan) {
        if (plan == null) {
            throw new BusinessException("抽样方案不能为空");
        }
        if (plan.getLotMin() == null || plan.getLotMax() == null
                || plan.getLotMin().compareTo(plan.getLotMax()) > 0) {
            throw new BusinessException("批量区间不合法（下限必须小于等于上限）");
        }
        if (plan.getSampleQuantity() == null || plan.getSampleQuantity().signum() <= 0) {
            throw new BusinessException("样本量必须大于 0");
        }
        if (plan.getAcceptNumber() == null || plan.getRejectNumber() == null) {
            throw new BusinessException("AC / RE 不能为空");
        }
        if (plan.getIsEnabled() == null) {
            plan.setIsEnabled(1);
        }
        if (plan.getLotType() == null || plan.getLotType().isBlank()) {
            plan.setLotType("IQC");
        }
        if (plan.getAqlValue() == null) {
            plan.setAqlValue(new BigDecimal("1.000"));
        }
        if (plan.getPlanId() == null) {
            planMapper.insert(plan);
        } else {
            planMapper.updateById(plan);
        }
        return plan;
    }

    @Override
    public void delete(Long planId) {
        planMapper.deleteById(planId);
    }

    @Override
    public QualitySamplingPlan match(String lotType, BigDecimal quantity) {
        if (lotType == null || quantity == null) {
            return null;
        }
        return planMapper.selectList(new LambdaQueryWrapper<QualitySamplingPlan>()
                        .eq(QualitySamplingPlan::getIsEnabled, 1)
                        .in(QualitySamplingPlan::getLotType, lotType, "ALL")
                        .le(QualitySamplingPlan::getLotMin, quantity)
                        .ge(QualitySamplingPlan::getLotMax, quantity)
                        .orderByAsc(QualitySamplingPlan::getLotMin))
                .stream().findFirst().orElse(null);
    }
}
