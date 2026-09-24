package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityLotItem;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.dto.QualityLotReportVO;
import com.jjx.quality.mapper.QualityLotItemMapper;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.mapper.QualityNcrMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 检验批报告组装 —— dev-20260917-012
 * 来料（IQC）→ JJX-QR-037 进料检验报告；成品（FQC）→ JJX-QR-039 成品检验报告。
 * AQL / AC / RE 取自抽样方案（来料）；成品为全检，抽样字段留空。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityLotReportAssembler {

    private final QualityLotMapper lotMapper;
    private final QualityLotItemMapper itemMapper;
    private final QualityNcrMapper ncrMapper;
    private final com.jjx.quality.service.QualitySamplingPlanService samplingPlanService;
    private final JdbcTemplate jdbcTemplate;

    public QualityLotReportVO build(Long lotId) {
        QualityLot lot = lotMapper.selectById(lotId);
        if (lot == null) {
            throw new BusinessException("检验批不存在: " + lotId);
        }
        QualityLotReportVO vo = new QualityLotReportVO();
        boolean iqc = "IQC".equals(lot.getLotType());
        vo.setRecordNo(iqc ? "JJX-QR-037" : "JJX-QR-039");
        vo.setReportTitle(iqc ? "进料检验报告" : "成品检验报告");
        vo.setLotId(lot.getLotId());
        vo.setLotNo(lot.getLotNo());
        vo.setLotType(lot.getLotType());
        vo.setBatchNo(lot.getBatchNo());
        vo.setMaterialCode(lot.getMaterialCode());
        vo.setMaterialName(lot.getMaterialName());
        vo.setProductCode(lot.getProductCode());
        vo.setProductName(lot.getProductName());
        vo.setLotQuantity(lot.getLotQuantity());
        vo.setInspectedQuantity(lot.getInspectedQuantity());
        vo.setPassQuantity(lot.getPassQuantity());
        vo.setFailQuantity(lot.getFailQuantity());
        vo.setResult(lot.getResult());
        vo.setResultLabel(("fail".equals(lot.getResult())) ? "NG" : ("concession".equals(lot.getResult()) ? "特采" : "OK"));
        vo.setInspector(lot.getInspector());
        vo.setInspectTime(lot.getInspectTime());
        vo.setRemark(lot.getRemark());
        vo.setVersion(lot.getVersion() == null ? "1" : String.valueOf(lot.getVersion()));

        // 抽样信息（来料按批量匹配方案兜底）
        vo.setSampleQuantity(lot.getSampleQuantity() != null ? lot.getSampleQuantity() : lot.getInspectedQuantity());
        vo.setAcceptNumber(lot.getAcceptNumber());
        vo.setRejectNumber(lot.getRejectNumber());
        BigDecimal aql = null;
        String level = null;
        String planName = null;
        // dev-20260924-021：抽样方案已改为「系统配置(JSON)」承载，不再查表
        com.jjx.quality.domain.entity.QualitySamplingPlan plan = null;
        if (lot.getSamplingPlanId() != null) {
            try {
                plan = samplingPlanService.findById(lot.getSamplingPlanId());
            } catch (Exception ignored) {
            }
        }
        if (plan != null) {
            aql = plan.getAqlValue();
            level = plan.getInspectionLevel();
            planName = plan.getPlanName();
        }
        if (aql == null && iqc) {
            try {
                com.jjx.quality.domain.entity.QualitySamplingPlan matched =
                        samplingPlanService.match("IQC", lot.getLotQuantity());
                if (matched != null) {
                    aql = matched.getAqlValue();
                }
            } catch (Exception ignored) {
            }
        }
        vo.setAqlValue(aql);
        vo.setInspectionLevel(level == null && iqc ? "II" : level);
        vo.setSamplingPlanName(planName);

        // 来源单据：来料=入库单（供方），成品=工单（客户）
        if (iqc && lot.getSourceId() != null) {
            try {
                vo.setSourceNo(jdbcTemplate.queryForObject(
                        "SELECT inbound_no FROM inventory_inbound_order WHERE inbound_id = ?", String.class,
                        lot.getSourceId()));
                vo.setPartnerName(jdbcTemplate.queryForObject(
                        "SELECT supplier_name FROM inventory_inbound_order WHERE inbound_id = ?", String.class,
                        lot.getSourceId()));
            } catch (Exception ignored) {
            }
        } else if (lot.getOrderId() != null) {
            try {
                vo.setSourceNo(jdbcTemplate.queryForObject(
                        "SELECT order_no FROM production_order WHERE order_id = ?", String.class, lot.getOrderId()));
                vo.setPartnerName(jdbcTemplate.queryForObject(
                        "SELECT c.customer_name FROM production_order o JOIN sales_order c ON c.order_id = o.sales_order_id "
                                + "WHERE o.order_id = ?", String.class, lot.getOrderId()));
                vo.setSpecification(jdbcTemplate.queryForObject(
                        "SELECT product_spec FROM production_order WHERE order_id = ?", String.class, lot.getOrderId()));
            } catch (Exception ignored) {
            }
        }

        // 检验项（逐件实测 + CR/MA/MI）
        List<QualityLotReportVO.Item> items = new ArrayList<>();
        for (QualityLotItem item : itemMapper.selectList(new LambdaQueryWrapper<QualityLotItem>()
                .eq(QualityLotItem::getLotId, lotId)
                .orderByAsc(QualityLotItem::getSortOrder)
                .orderByAsc(QualityLotItem::getItemId))) {
            QualityLotReportVO.Item row = new QualityLotReportVO.Item();
            row.setCheckItem(item.getCheckItem());
            row.setStandard(item.getStandard());
            row.setInspectionMethod(item.getInspectionMethod());
            row.setEquipment(item.getEquipment());
            row.setSampleValues(item.getSampleValues() != null ? item.getSampleValues() : item.getActualValue());
            row.setCrQuantity(item.getCrQuantity());
            row.setMaQuantity(item.getMaQuantity());
            row.setMiQuantity(item.getMiQuantity());
            row.setResult(item.getResult());
            row.setRemark(item.getRemark());
            items.add(row);
        }
        vo.setItems(items);

        // 不良台账汇总
        List<QualityNcr> ncrs = ncrMapper.selectList(new LambdaQueryWrapper<QualityNcr>()
                .eq(QualityNcr::getLotId, lotId));
        BigDecimal defect = BigDecimal.ZERO, cr = BigDecimal.ZERO, ma = BigDecimal.ZERO, mi = BigDecimal.ZERO,
                disposed = BigDecimal.ZERO;
        for (QualityNcr ncr : ncrs) {
            defect = defect.add(nz(ncr.getDefectQuantity()));
            cr = cr.add(nz(ncr.getCrQuantity()));
            ma = ma.add(nz(ncr.getMaQuantity()));
            mi = mi.add(nz(ncr.getMiQuantity()));
            disposed = disposed.add(nz(ncr.getDisposedQuantity()));
        }
        vo.setDefectQuantity(defect);
        vo.setDefectCr(cr);
        vo.setDefectMa(ma);
        vo.setDefectMi(mi);
        vo.setDefectDisposed(disposed);
        return vo;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
