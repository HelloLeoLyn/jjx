package com.jjx.quality.service;

import com.jjx.quality.domain.entity.QualitySamplingPlan;

import java.math.BigDecimal;
import java.util.List;

/**
 * 抽样方案服务 —— dev-20260917-002
 * dev-20260924-021：存储改为系统配置（sys_config.quality.sampling_plan 的 JSON），本接口不变。
 */
public interface QualitySamplingPlanService {

    List<QualitySamplingPlan> listAll();

    /** 按 id 取单条（配置数组内查找）；不存在返回 null */
    QualitySamplingPlan findById(Long planId);

    QualitySamplingPlan savePlan(QualitySamplingPlan plan);

    void delete(Long planId);

    /**
     * 按类型 + 批量匹配方案（取最小区间包含批量、启用中、区间最小的那条）
     * @param lotType IQC/IPQC/FQC
     * @param quantity 本批批量
     * @return 未配置返回 null（调用方降级为全检/手填）
     */
    QualitySamplingPlan match(String lotType, BigDecimal quantity);
}
