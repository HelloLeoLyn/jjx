package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.domain.entity.QualityNcrAction;
import com.jjx.quality.dto.QualityLotQueryDTO;
import com.jjx.quality.dto.QualityNcrDisposeDTO;
import com.jjx.quality.mapper.QualityNcrActionMapper;
import com.jjx.quality.mapper.QualityNcrMapper;
import com.jjx.quality.service.QualityLotService;
import com.jjx.quality.service.QualityNcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 不良台账服务实现 —— dev-20260917-003
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityNcrServiceImpl extends ServiceImpl<QualityNcrMapper, QualityNcr> implements QualityNcrService {

    private static final DateTimeFormatter NCR_NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    /** 处置方式：只有这三种 */
    private static final Set<String> ACTION_TYPES = Set.of("REWORK", "CONCESSION", "SCRAP");

    private final QualityNcrMapper ncrMapper;
    private final QualityNcrActionMapper actionMapper;
    private final QualityLotService qualityLotService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityNcr createFromLot(QualityLot lot, BigDecimal defectQuantity, BigDecimal crQuantity,
                                    BigDecimal maQuantity, BigDecimal miQuantity,
                                    String defectReason, String inspector) {
        if (lot == null || lot.getLotId() == null) {
            throw new BusinessException("检验批不存在，无法建不良台账");
        }
        BigDecimal defect = nz(defectQuantity);
        if (defect.signum() <= 0) {
            throw new BusinessException("不良数量必须大于 0");
        }
        QualityNcr ncr = new QualityNcr();
        ncr.setNcrNo(generateNcrNo());
        ncr.setLotId(lot.getLotId());
        ncr.setLotType(lot.getLotType());
        ncr.setOrderId(lot.getOrderId());
        ncr.setExecutionId(lot.getExecutionId());
        ncr.setMaterialId(lot.getMaterialId());
        ncr.setMaterialCode(lot.getMaterialCode());
        ncr.setMaterialName(lot.getMaterialName());
        ncr.setProductId(lot.getProductId());
        ncr.setProductCode(lot.getProductCode());
        ncr.setProductName(lot.getProductName());
        ncr.setBatchNo(lot.getBatchNo());
        ncr.setDefectQuantity(defect);
        ncr.setCrQuantity(nz(crQuantity));
        ncr.setMaQuantity(nz(maQuantity));
        ncr.setMiQuantity(nz(miQuantity));
        ncr.setDefectReason(defectReason);
        ncr.setStatus("PENDING");
        ncr.setDisposedQuantity(BigDecimal.ZERO);
        ncr.setInspector(inspector);
        ncr.setDelFlag(0);
        ncrMapper.insert(ncr);
        log.info("不良台账已建立: ncrNo={} 批={} 不良={}", ncr.getNcrNo(), lot.getLotNo(), defect.toPlainString());
        return ncr;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityNcr syncFromLot(QualityLot lot, BigDecimal defectQuantity, BigDecimal crQuantity,
                                  BigDecimal maQuantity, BigDecimal miQuantity,
                                  String defectReason, String inspector) {
        if (lot == null || lot.getLotId() == null) {
            throw new BusinessException("检验批不存在，无法同步不良台账");
        }
        BigDecimal defect = nz(defectQuantity);
        if (defect.signum() <= 0) {
            return null; // 无不良 → 不建台账
        }
        QualityNcr open = ncrMapper.selectList(new LambdaQueryWrapper<QualityNcr>()
                        .eq(QualityNcr::getLotId, lot.getLotId())
                        .ne(QualityNcr::getStatus, "CLOSED")
                        .orderByDesc(QualityNcr::getNcrId))
                .stream().findFirst().orElse(null);
        if (open == null) {
            return createFromLot(lot, defect, crQuantity, maQuantity, miQuantity, defectReason, inspector);
        }
        open.setDefectQuantity(defect);
        open.setCrQuantity(nz(crQuantity));
        open.setMaQuantity(nz(maQuantity));
        open.setMiQuantity(nz(miQuantity));
        open.setDefectReason(defectReason);
        BigDecimal disposed = nz(open.getDisposedQuantity());
        open.setStatus(disposed.compareTo(defect) >= 0 ? "CLOSED"
                : (disposed.signum() > 0 ? "DISPOSING" : "PENDING"));
        ncrMapper.updateById(open);
        log.info("不良台账同步（更正）: ncrNo={} 不良={}", open.getNcrNo(), defect.toPlainString());
        return open;
    }

    @Override
    public QualityNcr getNcr(Long ncrId) {
        QualityNcr ncr = ncrMapper.selectById(ncrId);
        if (ncr == null) {
            throw new BusinessException("不良台账不存在: " + ncrId);
        }
        return ncr;
    }

    @Override
    public IPage<QualityNcr> pageNcrs(QualityLotQueryDTO query) {
        QualityLotQueryDTO q = query == null ? new QualityLotQueryDTO() : query;
        return ncrMapper.selectPage(new Page<>(q.getPageNum(), q.getPageSize()),
                new LambdaQueryWrapper<QualityNcr>()
                        .eq(StringUtils.isNotBlank(q.getLotType()), QualityNcr::getLotType, q.getLotType())
                        .eq(StringUtils.isNotBlank(q.getStatus()), QualityNcr::getStatus, q.getStatus())
                        .eq(q.getOrderId() != null, QualityNcr::getOrderId, q.getOrderId())
                        .eq(q.getExecutionId() != null, QualityNcr::getExecutionId, q.getExecutionId())
                        .eq(StringUtils.isNotBlank(q.getMaterialCode()), QualityNcr::getMaterialCode, q.getMaterialCode())
                        .eq(StringUtils.isNotBlank(q.getBatchNo()), QualityNcr::getBatchNo, q.getBatchNo())
                        .orderByDesc(QualityNcr::getNcrId));
    }

    @Override
    public List<QualityNcr> listByOrder(Long orderId, Long executionId) {
        return ncrMapper.selectList(new LambdaQueryWrapper<QualityNcr>()
                .eq(orderId != null, QualityNcr::getOrderId, orderId)
                .eq(executionId != null, QualityNcr::getExecutionId, executionId)
                .orderByAsc(QualityNcr::getNcrId));
    }

    @Override
    public List<QualityNcr> listByLot(Long lotId) {
        return ncrMapper.selectList(new LambdaQueryWrapper<QualityNcr>()
                .eq(QualityNcr::getLotId, lotId)
                .orderByAsc(QualityNcr::getNcrId));
    }

    @Override
    public List<QualityNcrAction> listActions(Long ncrId) {
        return actionMapper.selectList(new LambdaQueryWrapper<QualityNcrAction>()
                .eq(QualityNcrAction::getNcrId, ncrId)
                .orderByAsc(QualityNcrAction::getActionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityNcrAction dispose(Long ncrId, QualityNcrDisposeDTO dto) {
        QualityNcr ncr = getNcr(ncrId);
        if (dto == null || StringUtils.isBlank(dto.getActionType())) {
            throw new BusinessException("请选择处置方式（返工/让步接收/报废）");
        }
        String actionType = dto.getActionType().toUpperCase();
        if (!ACTION_TYPES.contains(actionType)) {
            throw new BusinessException("处置方式不合法：" + dto.getActionType() + "（仅支持 返工/让步接收/报废）");
        }
        BigDecimal quantity = nz(dto.getQuantity());
        if (quantity.signum() <= 0) {
            throw new BusinessException("处置数量必须大于 0");
        }
        BigDecimal pending = ncr.pendingQuantity();
        if (quantity.compareTo(pending) > 0) {
            throw new BusinessException("处置数量不能超过待处置数量（" + pending.toPlainString() + "）");
        }
        // 让步接收必须有客户确认（否则属于返修范畴，系统不支持返修）
        if ("CONCESSION".equals(actionType) && !Boolean.TRUE.equals(dto.getCustomerConfirmed())) {
            throw new BusinessException("让步接收必须先取得客户确认");
        }

        QualityNcrAction action = new QualityNcrAction();
        action.setNcrId(ncrId);
        action.setActionType(actionType);
        action.setQuantity(quantity);
        action.setStatus("PENDING");
        action.setCustomerConfirmed(Boolean.TRUE.equals(dto.getCustomerConfirmed()) ? 1 : 0);
        if (Boolean.TRUE.equals(dto.getCustomerConfirmed())) {
            action.setCustomerConfirmTime(LocalDateTime.now());
        }
        action.setApprovedBy(dto.getApprovedBy());
        action.setApprovedTime(LocalDateTime.now());
        action.setResultRemark(dto.getResultRemark());
        action.setOperatorName(dto.getOperatorName());
        action.setDelFlag(0);
        actionMapper.insert(action);

        BigDecimal disposed = nz(ncr.getDisposedQuantity()).add(quantity);
        ncr.setDisposedQuantity(disposed);
        ncr.setStatus(disposed.compareTo(nz(ncr.getDefectQuantity())) >= 0 ? "CLOSED" : "DISPOSING");
        ncrMapper.updateById(ncr);
        // 同步检验批的已处置数量（防超处置）
        qualityLotService.addDisposedQuantity(ncr.getLotId(), quantity);
        log.info("不良处置登记: ncrNo={} 方式={} 数量={} 已处置={}/{}", ncr.getNcrNo(), actionType,
                quantity.toPlainString(), disposed.toPlainString(), nz(ncr.getDefectQuantity()).toPlainString());
        return action;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityNcrAction completeAction(Long actionId, String resultRemark, Long reworkExecutionId) {
        QualityNcrAction action = actionMapper.selectById(actionId);
        if (action == null) {
            throw new BusinessException("处置单不存在: " + actionId);
        }
        action.setStatus("DONE");
        action.setResultRemark(resultRemark == null ? action.getResultRemark() : resultRemark);
        if (reworkExecutionId != null) {
            action.setReworkExecutionId(reworkExecutionId);
        }
        actionMapper.updateById(action);
        return action;
    }

    private String generateNcrNo() {
        String prefix = "NCR" + LocalDate.now().format(NCR_NO_DATE);
        long base = ncrMapper.selectCount(new LambdaQueryWrapper<QualityNcr>()
                .likeRight(QualityNcr::getNcrNo, prefix));
        for (long seq = base + 1; seq < base + 10000; seq++) {
            String candidate = prefix + String.format("%04d", seq);
            Long exists = ncrMapper.selectCount(new LambdaQueryWrapper<QualityNcr>()
                    .eq(QualityNcr::getNcrNo, candidate));
            if (exists == null || exists == 0) {
                return candidate;
            }
        }
        throw new BusinessException("不良单号生成失败");
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
