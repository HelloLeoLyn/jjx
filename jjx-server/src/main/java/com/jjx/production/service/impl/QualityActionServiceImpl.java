package com.jjx.production.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityJudgeDTO;
import com.jjx.production.domain.entity.ProductionQualityInspection;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityInspectionTypeEnum;
import com.jjx.production.mapper.ProductionQualityInspectionMapper;
import com.jjx.production.service.QualityActionService;
import com.jjx.production.service.QualityInspectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 质检正式动作实现（P3-C）—— dev-20260918-017/018 收敛为「IQC/IPQC 专用」
 *
 * 原则：
 *  - QualityInspection = 质量事实，WorkReport = 生产申报事实，两者禁止互相覆盖。
 *  - 已判定(PASS/FAIL) 不可修改结果/数量；复检必须新建记录。
 *  - IPQC V1 人工创建，不自动触发；FAIL 只记录质量事实，不控制完整生产状态机。
 *
 * 2026-09-18 变更（质量归一·阶段1）：
 *  - 旧 FQC（成品检验）已下线：成品检验统一走 quality_lot 检验批（质量管理→成品检验）。
 *    本类不再创建/判定/处置 FQC；判定入口对 FQC 显式拒绝，防止旧表再落 FQC 数据。
 *  - 删除：createFqcForExecution / handleFqcPass / handleFqcFail / disposeFqcFailure。
 *  - IQC 仍依赖本类的 createInspection / judge / reinspect（InventoryInboundServiceImpl 调用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityActionServiceImpl implements QualityActionService {

    private final ProductionQualityInspectionMapper inspectionMapper;
    private final QualityInspectionService qualityInspectionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInspection(QualityInspectionCreateDTO dto) {
        // 旧 FQC 已下线：禁止通过旧路径创建 FQC 质检单
        if (QualityInspectionTypeEnum.FQC.getCode().equals(dto.getInspectionType())) {
            throw new BusinessException("旧成品检验(FQC)已下线，请在 质量管理→成品检验 用检验批(quality_lot)建批");
        }
        // workReportId 非空 → 后端反查 WorkReport 校验关联一致性（不信任客户端组合 ID）
        if (dto.getWorkReportId() != null) {
            boolean linkOk = qualityInspectionService.checkWorkReportLink(
                    dto.getWorkReportId(), dto.getExecutionId(), dto.getOrderId());
            if (!linkOk) {
                throw new BusinessException("报工与工序/订单关联不一致，无法创建质检");
            }
        }
        return qualityInspectionService.create(dto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityInspectionVO judge(Long inspectionId, QualityJudgeDTO dto) {
        ProductionQualityInspection entity = inspectionMapper.selectById(inspectionId);
        if (entity == null) throw new BusinessException("检验单不存在: " + inspectionId);

        // 旧 FQC 已下线（dev-20260918-017/018）：成品检验统一走 quality_lot 检验批
        if (QualityInspectionTypeEnum.FQC.getCode().equals(entity.getInspectionType())) {
            throw new BusinessException("旧成品检验(FQC)已下线，请在 质量管理→成品检验 用检验批(quality_lot)判定");
        }

        // 不可变：已判定（PASS/FAIL）禁止再次判定
        String cur = entity.getResult();
        if (QualityInspectionResultEnum.PASS.getCode().equals(cur)
                || QualityInspectionResultEnum.FAIL.getCode().equals(cur)) {
            throw new BusinessException("质检结果已确定，不可修改；复检请新建质检单");
        }
        // 判定结果合法性
        boolean pass = QualityInspectionResultEnum.PASS.getCode().equals(dto.getResult());
        boolean fail = QualityInspectionResultEnum.FAIL.getCode().equals(dto.getResult());
        if (!pass && !fail) {
            throw new BusinessException("判定结果不合法（PASS/FAIL）");
        }
        // 数量校验：>=0 且 合格+不合格 <= 检验数量
        BigDecimal total = dto.getTotalQty() == null ? BigDecimal.ZERO : dto.getTotalQty();
        BigDecimal passQty = dto.getPassQty() == null ? BigDecimal.ZERO : dto.getPassQty();
        BigDecimal failQty = dto.getFailQty() == null ? BigDecimal.ZERO : dto.getFailQty();
        if (total.signum() < 0 || passQty.signum() < 0 || failQty.signum() < 0) {
            throw new BusinessException("检验/合格/不合格数量不能为负数");
        }
        if (passQty.add(failQty).compareTo(total) != 0) {
            throw new BusinessException("合格+不合格数量必须等于检验数量");
        }
        if (pass && passQty.signum() <= 0) {
            throw new BusinessException("判定合格时合格数量必须大于 0");
        }

        // 写入质量事实（不可变：仅本次判定写入，之后禁止覆盖）
        entity.setResult(dto.getResult());
        entity.setTotalQty(total);
        entity.setPassQty(passQty);
        entity.setFailQty(failQty);
        entity.setRemainingFailQty(failQty);
        if (failQty.signum() > 0 && entity.getDisposition() == null) {
            entity.setDisposition(com.jjx.production.enums.QualityDispositionEnum.INTERNAL_SORT.getCode());
        }
        entity.setDefectDesc(dto.getDefectDesc());
        entity.setRemark(dto.getRemark());
        entity.setInspector(com.jjx.system.utils.SecurityUtils.getUsername());
        entity.setInspectTime(java.time.LocalDateTime.now());
        inspectionMapper.updateById(entity);

        log.info("质检判定 inspectionId={} type={} result={} total={} pass={} fail={}",
                inspectionId, entity.getInspectionType(), dto.getResult(), total, passQty, failQty);
        return qualityInspectionService.getById(inspectionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reinspect(Long inspectionId) {
        ProductionQualityInspection old = inspectionMapper.selectById(inspectionId);
        if (old == null) throw new BusinessException("检验单不存在: " + inspectionId);
        if (QualityInspectionTypeEnum.FQC.getCode().equals(old.getInspectionType())) {
            throw new BusinessException("旧成品检验(FQC)已下线，复检请在 质量管理→成品检验 走检验批复检(新版本)");
        }
        // 复制上下文新建 PENDING（不覆盖历史；不需要 previousInspectionId）
        QualityInspectionCreateDTO dto = new QualityInspectionCreateDTO();
        dto.setInspectionType(old.getInspectionType());
        dto.setSourceType(old.getSourceType());
        dto.setSourceId(old.getSourceId());
        dto.setSourceItemId(old.getSourceItemId());
        dto.setBatchNo(old.getBatchNo());
        dto.setPreviousInspectionId(old.getInspectionId());
        dto.setInspectionVersion(old.getInspectionVersion() == null ? 2 : old.getInspectionVersion() + 1);
        dto.setDisposition(old.getDisposition());
        dto.setOrderId(old.getOrderId());
        dto.setExecutionId(old.getExecutionId());
        dto.setWorkReportId(old.getWorkReportId());
        dto.setMaterialId(old.getMaterialId());
        dto.setProductId(old.getProductId());
        dto.setInspector(com.jjx.system.utils.SecurityUtils.getUsername());
        dto.setRemark("复检（源自 " + old.getInspectionNo() + "）");
        java.util.List<com.jjx.production.domain.vo.InspectionItemVO> oldItems =
                qualityInspectionService.getById(inspectionId).getItems();
        if (oldItems != null) {
            dto.setItems(oldItems.stream().map(item -> {
                com.jjx.production.domain.dto.InspectionItemDTO copy =
                        new com.jjx.production.domain.dto.InspectionItemDTO();
                copy.setCheckItem(item.getCheckItem());
                copy.setStandard(item.getStandard());
                copy.setInspectionMethod(item.getInspectionMethod());
                copy.setEquipment(item.getEquipment());
                copy.setActualValue(item.getActualValue());
                copy.setResult(item.getResult());
                copy.setCrQuantity(item.getCrQuantity());
                copy.setMaQuantity(item.getMaQuantity());
                copy.setMiQuantity(item.getMiQuantity());
                copy.setRemark(item.getRemark());
                return copy;
            }).collect(java.util.stream.Collectors.toList()));
        }
        Long newId = qualityInspectionService.create(dto);
        log.info("质检复检：{} → 新单 {}", old.getInspectionNo(), newId);
        return newId;
    }
}
