package com.jjx.quality.service.impl;

import com.jjx.quality.domain.entity.QualityLotItem;
import com.jjx.quality.domain.entity.QualitySamplingPlan;
import com.jjx.quality.dto.QualityLotCreateDTO;
import com.jjx.quality.dto.QualityLotItemDTO;
import com.jjx.quality.mapper.QualityLotItemMapper;
import com.jjx.quality.service.QualityOqcService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 出货检验（OQC）实现 —— dev-20260921-042 一期。
 *
 * <p>只做两件事：按批量挂抽样方案、预填检验项目；其余（判定/复检/不良/件级）走既有通道。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityOqcServiceImpl implements QualityOqcService {

    private final com.jjx.quality.service.QualitySamplingPlanService samplingPlanService;
    private final QualityLotItemMapper lotItemMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void prepareLot(QualityLotCreateDTO dto) {
        if (dto == null || !"OQC".equals(dto.getLotType())) {
            return;
        }
        applySamplingPlan(dto);
        prefillItems(dto);
    }

    /** 按批量匹配启用的 OQC 抽样方案（AQL 配置在 系统参数 → quality_config → quality.sampling_plan） */
    private void applySamplingPlan(QualityLotCreateDTO dto) {
        if (dto.getSamplingPlanId() != null) {
            return;
        }
        BigDecimal qty = dto.getLotQuantity() == null ? BigDecimal.ZERO : dto.getLotQuantity();
        if (qty.signum() <= 0) {
            return;
        }
        try {
            QualitySamplingPlan plan = samplingPlanService.match("OQC", qty);
            if (plan == null) {
                log.info("OQC 建批未匹配到抽样方案（批量 {}），按全检处理待人工录入: batch={}",
                        qty.toPlainString(), dto.getBatchNo());
                return;
            }
            dto.setSamplingPlanId(plan.getPlanId());
            dto.setSampleQuantity(plan.getSampleQuantity());
            dto.setAcceptNumber(plan.getAcceptNumber());
            dto.setRejectNumber(plan.getRejectNumber());
            log.info("OQC 建批已挂抽样方案: plan={} 批量={} 抽样={} Ac={} Re={}", plan.getPlanName(),
                    qty.toPlainString(), plan.getSampleQuantity(), plan.getAcceptNumber(), plan.getRejectNumber());
        } catch (Exception e) {
            log.warn("OQC 抽样方案匹配失败（不阻断建批）: {}", e.getMessage());
        }
    }

    /** 预填检验项目：复制该产品最近一次 FQC 批的项目（无则留空由质检员手录） */
    private void prefillItems(QualityLotCreateDTO dto) {
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            return;
        }
        String productCode = dto.getProductCode();
        if (productCode == null || productCode.isBlank()) {
            return;
        }
        try {
            Long sourceLotId = jdbcTemplate.queryForObject(
                    "SELECT lot_id FROM quality_lot WHERE lot_type = 'FQC' AND product_code = ? AND del_flag = 0"
                            + " ORDER BY lot_id DESC LIMIT 1",
                    Long.class, productCode);
            if (sourceLotId == null) {
                return;
            }
            List<QualityLotItem> items = lotItemMapper.selectList(Wrappers.<QualityLotItem>lambdaQuery()
                    .eq(QualityLotItem::getLotId, sourceLotId)
                    .orderByAsc(QualityLotItem::getSortOrder)
                    .orderByAsc(QualityLotItem::getItemId));
            List<QualityLotItemDTO> copy = new ArrayList<>();
            for (QualityLotItem item : items) {
                QualityLotItemDTO d = new QualityLotItemDTO();
                d.setCheckItem(item.getCheckItem());
                d.setStandard(item.getStandard());
                d.setInspectionMethod(item.getInspectionMethod());
                d.setEquipment(item.getEquipment());
                d.setSortOrder(item.getSortOrder());
                copy.add(d);
            }
            dto.setItems(copy);
            if (!copy.isEmpty()) {
                log.info("OQC 建批已预填检验项目 {} 项（复制自产品 {} 的最近 FQC 批 #{}）",
                        copy.size(), productCode, sourceLotId);
            }
        } catch (Exception e) {
            log.info("OQC 预填检验项目未命中（按手录处理）: productCode={} err={}", productCode, e.getMessage());
        }
    }
}
