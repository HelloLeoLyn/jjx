package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.quality.domain.entity.QualitySamplingPlan;
import com.jjx.quality.service.QualitySamplingPlanService;
import com.jjx.system.domain.entity.SysConfig;
import com.jjx.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 抽样方案服务实现 —— dev-20260917-002；dev-20260924-021 改为「系统配置(JSON)」承载。
 *
 * <p>存储：{@code sys_config(config_group=quality_config, config_key=quality.sampling_plan)} 的
 * config_value 是一条 JSON 数组（每元素一个方案对象）。依据 CONVENTIONS §14：该数据只有个位数行、
 * 基本不变，用配置即可，不必占一张表；且种子数据随迁移脚本进新环境（迁移 219）。</p>
 *
 * <p>代价（知情接受）：无法再用 SQL 按区间查、无库级约束 —— 故校验全部在本类内做。
 * 并发：配置为「少量行、人手工维护」场景，直接整串读改写；不做行级并发控制。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualitySamplingPlanServiceImpl implements QualitySamplingPlanService {

    /** 配置分组（与其它质量配置同族） */
    public static final String CONFIG_GROUP = "quality_config";
    /** 配置键 */
    public static final String CONFIG_KEY = "quality.sampling_plan";
    private static final String CONFIG_NAME = "抽样方案(AQL)";
    private static final String CONFIG_REMARK = "来料/成品建批按批量区间匹配样本量/AC/RE（dev-20260924-021 由 quality_sampling_plan 表迁入）";

    private final SysConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<QualitySamplingPlan> listAll() {
        return load().stream()
                .sorted(Comparator
                        .comparing((QualitySamplingPlan p) -> nz(p.getLotType()))
                        .thenComparing(p -> p.getAqlValue() == null ? BigDecimal.ZERO : p.getAqlValue())
                        .thenComparing(p -> p.getLotMin() == null ? BigDecimal.ZERO : p.getLotMin()))
                .toList();
    }

    @Override
    public QualitySamplingPlan findById(Long planId) {
        if (planId == null) {
            return null;
        }
        return load().stream()
                .filter(p -> planId.equals(p.getPlanId()))
                .findFirst().orElse(null);
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

        List<QualitySamplingPlan> plans = load();
        if (plan.getPlanId() == null) {
            long next = plans.stream()
                    .map(QualitySamplingPlan::getPlanId)
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue).max().orElse(0L) + 1L;
            plan.setPlanId(next);
            plans.add(plan);
        } else {
            int idx = -1;
            for (int i = 0; i < plans.size(); i++) {
                if (plan.getPlanId().equals(plans.get(i).getPlanId())) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                plans.set(idx, plan);
            } else {
                plans.add(plan);
            }
        }
        persist(plans);
        return plan;
    }

    @Override
    public void delete(Long planId) {
        if (planId == null) {
            return;
        }
        List<QualitySamplingPlan> plans = load();
        boolean removed = plans.removeIf(p -> planId.equals(p.getPlanId()));
        if (!removed) {
            throw new BusinessException("抽样方案不存在: " + planId);
        }
        persist(plans);
    }

    @Override
    public QualitySamplingPlan match(String lotType, BigDecimal quantity) {
        if (lotType == null || quantity == null) {
            return null;
        }
        return load().stream()
                .filter(p -> p.getIsEnabled() == null || p.getIsEnabled() == 1)
                .filter(p -> lotType.equalsIgnoreCase(nz(p.getLotType())) || "ALL".equalsIgnoreCase(nz(p.getLotType())))
                .filter(p -> p.getLotMin() != null && p.getLotMax() != null
                        && p.getLotMin().compareTo(quantity) <= 0
                        && p.getLotMax().compareTo(quantity) >= 0)
                .min(Comparator.comparing(p -> p.getLotMin() == null ? BigDecimal.ZERO : p.getLotMin()))
                .orElse(null);
    }

    // ==================== 配置读写 ====================

    private List<QualitySamplingPlan> load() {
        SysConfig cfg = configMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, CONFIG_KEY));
        if (cfg == null || cfg.getConfigValue() == null || cfg.getConfigValue().isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<QualitySamplingPlan> plans = objectMapper.readValue(
                    cfg.getConfigValue(), new TypeReference<List<QualitySamplingPlan>>() {
                    });
            return plans == null ? new ArrayList<>() : new ArrayList<>(plans);
        } catch (Exception e) {
            log.warn("抽样方案配置解析失败 configKey={}: {}", CONFIG_KEY, e.getMessage());
            throw new BusinessException("抽样方案配置解析失败：" + e.getMessage());
        }
    }

    private void persist(List<QualitySamplingPlan> plans) {
        String json;
        try {
            json = objectMapper.writeValueAsString(plans);
        } catch (Exception e) {
            throw new BusinessException("抽样方案配置序列化失败：" + e.getMessage());
        }
        SysConfig cfg = configMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, CONFIG_KEY));
        if (cfg == null) {
            cfg = new SysConfig();
            cfg.setConfigKey(CONFIG_KEY);
            cfg.setConfigName(CONFIG_NAME);
            cfg.setConfigGroup(CONFIG_GROUP);
            cfg.setConfigValue(json);
            cfg.setSortOrder(0);
            cfg.setIsActive(1);
            cfg.setRemark(CONFIG_REMARK);
            configMapper.insert(cfg);
        } else {
            cfg.setConfigValue(json);
            configMapper.updateById(cfg);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
