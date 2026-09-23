package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.vo.ReworkTraceVO;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.ProductionReworkTraceService;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.domain.entity.QualityNcrAction;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.mapper.QualityNcrActionMapper;
import com.jjx.quality.mapper.QualityNcrMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 返工链投影（只读）实现 —— dev-20260923-031 一期。
 *
 * <p>口径：一条返工 = 不良单里的一条 REWORK 处置动作 + 它生成的返工工序（execution_type=REWORK）
 * + 该工序的报工 + 复检批（reinspection_lot_id）。本类只读不写，供页面贴「返工身份与进度」。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionReworkTraceServiceImpl implements ProductionReworkTraceService {

    private final QualityNcrActionMapper ncrActionMapper;
    private final QualityNcrMapper ncrMapper;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionWorkReportMapper workReportMapper;
    private final QualityLotMapper qualityLotMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<ReworkTraceVO> trace(Long orderId, Long executionId, Long ncrId) {
        List<QualityNcrAction> actions = reworkActions(orderId, executionId, ncrId);
        List<ReworkTraceVO> result = new ArrayList<>();
        for (QualityNcrAction action : actions) {
            result.add(toTrace(action));
        }
        return result;
    }

    /** 三种入口任选其一：不良单 / 返工工序 / 工单 */
    private List<QualityNcrAction> reworkActions(Long orderId, Long executionId, Long ncrId) {
        LambdaQueryWrapper<QualityNcrAction> query = new LambdaQueryWrapper<QualityNcrAction>()
                .eq(QualityNcrAction::getActionType, "REWORK")
                .orderByAsc(QualityNcrAction::getActionId);
        if (ncrId != null) {
            query.eq(QualityNcrAction::getNcrId, ncrId);
            return ncrActionMapper.selectList(query);
        }
        if (executionId != null) {
            query.eq(QualityNcrAction::getReworkExecutionId, executionId);
            return ncrActionMapper.selectList(query);
        }
        if (orderId == null) {
            return List.of();
        }
        List<QualityNcr> ncrs = ncrMapper.selectList(new LambdaQueryWrapper<QualityNcr>()
                .eq(QualityNcr::getOrderId, orderId));
        if (ncrs.isEmpty()) {
            return List.of();
        }
        query.in(QualityNcrAction::getNcrId, ncrs.stream().map(QualityNcr::getNcrId).toList());
        return ncrActionMapper.selectList(query);
    }

    private ReworkTraceVO toTrace(QualityNcrAction action) {
        ReworkTraceVO vo = new ReworkTraceVO();
        vo.setActionId(action.getActionId());
        vo.setActionStatus(action.getStatus());
        vo.setReworkQuantity(nz(action.getQuantity()));

        QualityNcr ncr = action.getNcrId() == null ? null : ncrMapper.selectById(action.getNcrId());
        if (ncr != null) {
            vo.setNcrId(ncr.getNcrId());
            vo.setNcrNo(ncr.getNcrNo());
            vo.setDefectQuantity(nz(ncr.getDefectQuantity()));
            vo.setDisposedQuantity(nz(ncr.getDisposedQuantity()));
        }

        ProductionOperationExecution execution = action.getReworkExecutionId() == null
                ? null : executionMapper.selectById(action.getReworkExecutionId());
        if (execution != null) {
            vo.setExecutionId(execution.getExecutionId());
            vo.setProcessName(execution.getProcessName());
            vo.setExecutionStatus(execution.getExecutionStatus());
            vo.setReworkRequirement(reworkRequirementOf(execution));
            vo.setReportedQuantity(reportedQuantity(execution.getExecutionId()));
        }

        QualityLot lot = action.getReinspectionLotId() == null
                ? null : qualityLotMapper.selectById(action.getReinspectionLotId());
        if (lot != null) {
            vo.setReinspectionLotId(lot.getLotId());
            vo.setReinspectionLotNo(lot.getLotNo());
            vo.setRecoveredQuantity(nz(lot.getPassQuantity()));
        }

        vo.setStatusText(buildStatusText(action.getStatus(),
                execution == null ? null : execution.getExecutionStatus(),
                vo.getReworkQuantity(), vo.getReportedQuantity(), vo.getRecoveredQuantity(), lot != null));
        return vo;
    }

    /** 该返工工序已审批通过的报工量（合格+不良，即实际修了多少件） */
    private BigDecimal reportedQuantity(Long executionId) {
        List<ProductionWorkReport> reports = workReportMapper.selectList(
                new LambdaQueryWrapper<ProductionWorkReport>()
                        .eq(ProductionWorkReport::getExecutionId, executionId)
                        .eq(ProductionWorkReport::getReportStatus, "APPROVED"));
        BigDecimal total = BigDecimal.ZERO;
        for (ProductionWorkReport report : reports) {
            total = total.add(nz(report.getQualifiedQuantity())).add(nz(report.getDefectiveQuantity()));
        }
        return total;
    }

    /** 返工要求/作业说明：取自返工工序的 custom_process_params（建工序时写入的 NCR 快照） */
    private String reworkRequirementOf(ProductionOperationExecution execution) {
        String raw = execution.getCustomProcessParams();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(raw, Map.class);
            for (String key : List.of("reworkRequirement", "qualityStandard", "description")) {
                Object value = map.get(key);
                if (value != null && !String.valueOf(value).isBlank()) {
                    return String.valueOf(value);
                }
            }
        } catch (Exception e) {
            log.debug("返工工序作业说明解析失败: executionId={} err={}", execution.getExecutionId(), e.getMessage());
        }
        return null;
    }

    /**
     * 一句人话：现在到哪一步、下一步谁做什么（方案 §5.3 模板）。
     * 抽成静态方法便于单测覆盖全部分支。
     */
    public static String buildStatusText(String actionStatus, Integer executionStatus, BigDecimal reworkQuantity,
                                         BigDecimal reportedQuantity, BigDecimal recoveredQuantity,
                                         boolean hasReinspectionLot) {
        BigDecimal qty = nz(reworkQuantity);
        String quota = plain(qty);
        if ("DONE".equals(actionStatus)) {
            BigDecimal recovered = nz(recoveredQuantity);
            if (recovered.compareTo(qty) >= 0) {
                return "返工复检合格 " + plain(recovered) + "/" + quota + " 件，已回收良品";
            }
            return "返工复检合格 " + plain(recovered) + "/" + quota + " 件，还差 "
                    + plain(qty.subtract(recovered)) + " 件（走让步接收或报废）";
        }
        int status = executionStatus == null ? -1 : executionStatus;
        switch (status) {
            case 0:
            case 1:
                return "把报废/不良的 " + quota + " 件修回来——先开工这道返工工序";
            case 2:
                return nz(reportedQuantity).signum() > 0
                        ? "返工进行中，已报 " + plain(reportedQuantity) + "/" + quota + " 件"
                        : "返工进行中，尚未报工";
            case 3:
                return "返工已暂停，恢复后继续报工";
            case 4:
                return hasReinspectionLot
                        ? "返工已完工，去成品检验判定复检批并回收"
                        : "返工已完工，去成品检验判定并复检（复检合格才算回收）";
            case 5:
            case 6:
                return "返工工序已跳过/取消——需另走让步接收或报废";
            default:
                return status < 0
                        ? "返工处置已登记，返工工序待生成"
                        : "返工在制（工序状态 " + status + "）";
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String plain(BigDecimal value) {
        return nz(value).stripTrailingZeros().toPlainString();
    }
}
