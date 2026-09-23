package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.enums.AllowedActionEnum;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.domain.entity.QualityNcrAction;
import com.jjx.quality.dto.QualityLotQueryDTO;
import com.jjx.quality.dto.QualityNcrDisposeDTO;
import com.jjx.quality.mapper.QualityNcrActionMapper;
import com.jjx.quality.mapper.QualityNcrMapper;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.service.QualityLotService;
import com.jjx.quality.service.QualityNcrService;
import com.jjx.quality.service.support.AllowedActionResolver;
import com.jjx.quality.service.QualityFinishService;
import com.jjx.quality.service.QualityCapaService;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionTask;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 不良台账服务实现 —— dev-20260917-003
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityNcrServiceImpl extends ServiceImpl<QualityNcrMapper, QualityNcr> implements QualityNcrService {

    /** 处置方式：只有这三种 */
    private static final Set<String> ACTION_TYPES = Set.of("REWORK", "CONCESSION", "RETURN", "SCRAP");

    private final QualityNcrMapper ncrMapper;
    private final QualityNcrActionMapper actionMapper;
    private final QualityLotMapper qualityLotMapper;
    private final QualityLotService qualityLotService;
    /** 延迟获取，避免 QualityFinishService -> QualityNcrService -> QualityFinishService 构造器循环。 */
    private final org.springframework.beans.factory.ObjectProvider<QualityFinishService> qualityFinishServiceProvider;
    private final ProductionOperationExecutionMapper executionMapper;
    private final ProductionTaskMapper taskMapper;
    private final com.jjx.product.mapper.ProductStandardProcessMapper standardProcessMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final QualityCapaService capaService;
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    /** 用 ObjectProvider 延迟取，避免 库存→质量→库存 的循环依赖。 */
    private final org.springframework.beans.factory.ObjectProvider<com.jjx.inventory.service.InventoryInboundService> inventoryInboundServiceProvider;
    private final RedisSequenceService redisSequenceService;

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
        // dev-20260923-046：结案判据统一 —— 处置量够 **且无未关闭 CAPA**（原实现此处不查 CAPA，有洞）
        boolean canClose = canCloseNcr(disposed, defect, capaService.countOpen(open.getNcrId(), null));
        open.setStatus(canClose ? "CLOSED"
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
        IPage<QualityNcr> page = ncrMapper.selectPage(new Page<>(q.getPageNum(), q.getPageSize()),
                new LambdaQueryWrapper<QualityNcr>()
                        .eq(StringUtils.isNotBlank(q.getLotType()), QualityNcr::getLotType, q.getLotType())
                        .eq(StringUtils.isNotBlank(q.getStatus()), QualityNcr::getStatus, q.getStatus())
                        .eq(q.getOrderId() != null, QualityNcr::getOrderId, q.getOrderId())
                        .eq(q.getExecutionId() != null, QualityNcr::getExecutionId, q.getExecutionId())
                        .eq(StringUtils.isNotBlank(q.getMaterialCode()), QualityNcr::getMaterialCode, q.getMaterialCode())
                        .eq(StringUtils.isNotBlank(q.getBatchNo()), QualityNcr::getBatchNo, q.getBatchNo())
                        .orderByDesc(QualityNcr::getNcrId));
        // dev-20260923-036：台账列表补展示字段（检验批号 / 工单号 / 来源批是否已失效），
        // 原来页面只能显示「工单 #2 / 批 #11」这种裸 ID，看不出对的是哪张单、哪张批。
        fillDisplayFields(page.getRecords());
        return page;
    }

    /** 台账列表展示字段回填（不落库）：批号、工单号、来源批已失效标记 */
    private void fillDisplayFields(List<QualityNcr> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        java.util.Set<Long> lotIds = rows.stream().map(QualityNcr::getLotId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, QualityLot> lotMap = new java.util.HashMap<>();
        java.util.Set<Long> superseded = new java.util.HashSet<>();
        if (!lotIds.isEmpty()) {
            for (QualityLot lot : qualityLotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                    .in(QualityLot::getLotId, lotIds))) {
                lotMap.put(lot.getLotId(), lot);
            }
            for (QualityLot child : qualityLotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                    .in(QualityLot::getParentLotId, lotIds))) {
                if (child.getParentLotId() != null) {
                    superseded.add(child.getParentLotId());
                }
            }
        }
        java.util.Map<Long, String> orderNoMap = new java.util.HashMap<>();
        for (QualityNcr row : rows) {
            if (row.getOrderId() != null && !orderNoMap.containsKey(row.getOrderId())) {
                try {
                    com.jjx.production.domain.entity.ProductionOrder order =
                            productionOrderMapper.selectById(row.getOrderId());
                    orderNoMap.put(row.getOrderId(), order == null ? null : order.getOrderNo());
                } catch (Exception e) {
                    orderNoMap.put(row.getOrderId(), null);
                }
            }
        }
        for (QualityNcr row : rows) {
            QualityLot lot = row.getLotId() == null ? null : lotMap.get(row.getLotId());
            if (lot != null) {
                row.setLotNo(lot.getLotNo());
                row.setLotSuperseded(superseded.contains(lot.getLotId()));
            }
            if (row.getOrderId() != null) {
                row.setOrderNo(orderNoMap.get(row.getOrderId()));
            }
            // dev-20260923-039：允许动作由唯一出处算出并下发（前端只按它渲染，不再写状态条件）
            row.setAllowedActions(com.jjx.common.enums.AllowedActionEnum.codesOf(
                    com.jjx.quality.service.support.AllowedActionResolver.forNcr(
                            row.getStatus(), Boolean.TRUE.equals(row.getLotSuperseded()), pendingQuantityOf(row))));
        }
    }

    /** 待处置数量：已作废/已结一律 0（与前端口径一致，见 dev-20260923-038） */
    private BigDecimal pendingQuantityOf(QualityNcr row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        String status = row.getStatus() == null ? "" : row.getStatus().trim().toUpperCase();
        if ("VOID".equals(status) || "CLOSED".equals(status)) {
            return BigDecimal.ZERO;
        }
        BigDecimal left = nz(row.getDefectQuantity()).subtract(nz(row.getDisposedQuantity()));
        return left.signum() > 0 ? left : BigDecimal.ZERO;
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
        List<QualityNcrAction> actions = actionMapper.selectList(new LambdaQueryWrapper<QualityNcrAction>()
                .eq(QualityNcrAction::getNcrId, ncrId)
                .orderByAsc(QualityNcrAction::getActionId));
        // dev-20260923-039：处置记录行的允许动作同样由唯一出处下发（撤销/补料等按钮只按它渲染）
        for (QualityNcrAction action : actions) {
            action.setAllowedActions(com.jjx.common.enums.AllowedActionEnum.codesOf(
                    com.jjx.quality.service.support.AllowedActionResolver
                            .forNcrAction(action.getActionType(), action.getStatus())));
        }
        return actions;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityNcrAction dispose(Long ncrId, QualityNcrDisposeDTO dto) {
        QualityNcr ncr = getNcr(ncrId);
        // dev-20260923-039：守卫改走**唯一出处**（AllowedActionResolver）——决策与拒绝文案都与下发前端的那份同源，
        // 不再在守卫里各写 if（038 的两道判断被收敛到这里；口径见 045 §2.3）。
        boolean lotSuperseded = isSourceLotSuperseded(ncr.getLotId());
        java.math.BigDecimal pending = pendingQuantityOf(ncr);
        if (!com.jjx.quality.service.support.AllowedActionResolver
                .forNcr(ncr.getStatus(), lotSuperseded, pending)
                .contains(com.jjx.common.enums.AllowedActionEnum.NCR_DISPOSE)) {
            throw new BusinessException(com.jjx.quality.service.support.AllowedActionResolver
                    .ncrDisposeBlockReason(ncr.getStatus(), lotSuperseded, pending, ncr.getNcrNo()));
        }
        QualityLot lot = qualityLotMapper.selectById(ncr.getLotId());
        if (lot != null && "IQC".equals(lot.getLotType())) {
            throw new BusinessException("IQC 不良请从来料隔离处置入口操作，系统将自动同步不良台账");
        }
        return disposeInternal(ncr, dto, false);
    }

    /** 来源检验批是否已被后继复检版本取代（链上失效）—— dev-20260923-038 */
    private boolean isSourceLotSuperseded(Long lotId) {
        if (lotId == null) {
            return false;
        }
        Long children = qualityLotMapper.selectCount(new LambdaQueryWrapper<QualityLot>()
                .eq(QualityLot::getParentLotId, lotId));
        return children != null && children > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityNcrAction syncIqcDisposition(Long lotId, String quarantineAction, BigDecimal quantity,
                                                String operatorName, String remark) {
        QualityLot lot = qualityLotMapper.selectById(lotId);
        if (lot == null || !"IQC".equals(lot.getLotType())) {
            throw new BusinessException("IQC 检验批不存在");
        }
        QualityNcr ncr = ncrMapper.selectOne(new LambdaQueryWrapper<QualityNcr>()
                .eq(QualityNcr::getLotId, lotId)
                .in(QualityNcr::getStatus, "PENDING", "DISPOSING")
                .orderByDesc(QualityNcr::getNcrId).last("LIMIT 1"));
        if (ncr == null) {
            throw new BusinessException("IQC 不良台账不存在，禁止单独执行库存处置");
        }
        String actionType = switch (quarantineAction) {
            case "RELEASE" -> "CONCESSION";
            case "RETURN" -> "RETURN";
            case "REWORK" -> "REWORK";
            case "SCRAP" -> "SCRAP";
            default -> throw new BusinessException("不支持的 IQC 隔离处置方式");
        };
        QualityNcrDisposeDTO dto = new QualityNcrDisposeDTO();
        dto.setActionType(actionType);
        dto.setQuantity(quantity);
        dto.setCustomerConfirmed("CONCESSION".equals(actionType));
        dto.setOperatorName(operatorName);
        dto.setResultRemark(remark);
        return disposeInternal(ncr, dto, true);
    }

    private QualityNcrAction disposeInternal(QualityNcr ncr, QualityNcrDisposeDTO dto, boolean iqcInventoryManaged) {
        Long ncrId = ncr.getNcrId();
        if (dto == null || StringUtils.isBlank(dto.getActionType())) {
            throw new BusinessException("请选择处置方式（返工/让步接收/报废）");
        }
        String actionType = dto.getActionType().toUpperCase();
        if (!ACTION_TYPES.contains(actionType)) {
            throw new BusinessException("处置方式不合法：" + dto.getActionType() + "（仅支持 返工/让步接收/报废）");
        }
        // dev-20260923-022（看板 2205 / dev-20260922-029）：失效批禁止再处置 ——
        // 被复检换代取代的批（或已随批作废的 NCR）如果还能处置，点一次让步接收就会把"已经不存在的货"加进良品库存。
        if ("VOID".equals(ncr.getStatus())) {
            throw new BusinessException("该不良单已随批作废（VOID），不能处置");
        }
        if (!qualityLotService.isLatestVersion(ncr.getLotId())) {
            throw new BusinessException("该批已失效（已有复检新版本），不能继续处置；请对该批的最新版本处理");
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
        // dev-20260923-046：三条结案路径共用同一判据（处置量够 + 无未关闭 CAPA）
        boolean canClose = canCloseNcr(disposed, ncr.getDefectQuantity(), capaService.countOpen(ncrId, null));
        ncr.setStatus("REWORK".equals(actionType) ? "DISPOSING" : canClose ? "CLOSED" : "DISPOSING");
        ncrMapper.updateById(ncr);
        // 同步检验批的已处置数量（防超处置）
        qualityLotService.addDisposedQuantity(ncr.getLotId(), quantity);
        // 处置与库存联动（dev-20260917-008）
        if (iqcInventoryManaged) {
            action.setStatus("REWORK".equals(actionType) ? "PROCESSING" : "DONE");
            actionMapper.updateById(action);
        } else {
            applyStockEffect(ncr, action, actionType, quantity, dto);
        }
        log.info("不良处置登记: ncrNo={} 方式={} 数量={} 已处置={}/{}", ncr.getNcrNo(), actionType,
                quantity.toPlainString(), disposed.toPlainString(), nz(ncr.getDefectQuantity()).toPlainString());
        return action;
    }

    // ==================== dev-20260923-022：随批作废（VOID） ====================

    /**
     * 复检换代：把被取代批上仍处 PENDING/DISPOSING 的不良单（及其未完成的处置单）随批作废（VOID）。
     *
     * <p>业内口径（ISO 9001 §8.7 / SAP QM 使用决策）：批一旦被后继复检版本取代，其未完成的处置
     * 不得再生效；没有这一步就会出现「对已经不存在的批做让步接收 → 良品库存凭空增加」。
     *
     * @param lotId  被取代的检验批
     * @param reason 触发原因（如「复检换代：QL260923007」）
     * @return 作废的不良单数量
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int voidOpenDispositionsBySupersededLot(Long lotId, String reason) {
        if (lotId == null) {
            return 0;
        }
        List<QualityNcr> openNcrs = ncrMapper.selectList(new LambdaQueryWrapper<QualityNcr>()
                .eq(QualityNcr::getLotId, lotId)
                .in(QualityNcr::getStatus, "PENDING", "DISPOSING")
                .eq(QualityNcr::getDelFlag, 0));
        if (openNcrs == null || openNcrs.isEmpty()) {
            return 0;
        }
        String tail = "【随批失效】" + (StringUtils.isBlank(reason) ? "" : reason + "：")
                + "该批已有复检新版本，不良单随之作废(VOID)，禁止再处置（dev-20260923-022）";
        int voided = 0;
        for (QualityNcr ncr : openNcrs) {
            voidNcrWithOpenActions(ncr, tail);
            voided++;
        }
        log.info("复检换代：随批作废不良单 {} 张（lotId={} 原因={}）", voided, lotId, reason);
        return voided;
    }

    /**
     * 随批作废（单张，**正式动作**）—— dev-20260923-040。
     *
     * <p>背景：原来只能靠复检换代自动触发（或有人用手写 SQL 改库），口径容易分叉。
     * 本方法把「随批作废」升格为可审计的正式入口：
     * ① 守卫同源：用 {@code AllowedActionResolver} 判定（仅「来源批已失效 + 单还开着」才给 NCR_VOID_SUPERSEDED），
     *    不允许时直接抛出与前端同源的文案；
     * ② 必填原因（留痕）；③ 幂等：已 VOID 直接返回 0，不产生第二次副作用。
     *
     * @return 本次作废的不良单数量（0 或 1）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int voidSupersededNcr(Long ncrId, String reason, String operatorName) {
        if (ncrId == null) {
            throw new BusinessException("不良单ID不能为空");
        }
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new BusinessException("随批作废必须填写原因（留痕要求）");
        }
        QualityNcr ncr = ncrMapper.selectById(ncrId);
        if (ncr == null) {
            throw new BusinessException("不良单不存在: " + ncrId);
        }
        if ("VOID".equals(ncr.getStatus())) {
            // 幂等：已作废不再动（重复点击/重试都安全）
            log.info("随批作废幂等命中（已 VOID）: ncrNo={} by={}", ncr.getNcrNo(), operatorName);
            return 0;
        }
        boolean sourceLotSuperseded = isSourceLotSuperseded(ncr.getLotId());
        List<AllowedActionEnum> allowed = AllowedActionResolver.forNcr(
                ncr.getStatus(), sourceLotSuperseded, pendingQuantityOf(ncr));
        if (!allowed.contains(AllowedActionEnum.NCR_VOID_SUPERSEDED)) {
            throw new BusinessException("该不良单当前不允许「随批作废」：" + ncr.getNcrNo()
                    + (sourceLotSuperseded ? "（当前状态 " + ncr.getStatus() + "）" : "（来源检验批未被后继复检版本取代）"));
        }
        String tail = "【随批失效】" + (operatorName == null || operatorName.isBlank() ? "" : operatorName + "：") + r
                + "；该批已有复检新版本，不良单随之作废(VOID)，禁止再处置（dev-20260923-040）";
        voidNcrWithOpenActions(ncr, tail);
        log.info("随批作废不良单 1 张（ncrNo={} lotId={} 原因={} 操作人={}）",
                ncr.getNcrNo(), ncr.getLotId(), r, operatorName);
        return 1;
    }

    /**
     * 作废单张不良单及其未完成处置单并追加留痕 —— 批量（复检换代）与单张（040 正式动作）两个入口共用，
     * 保证两条路径口径完全一致（禁止手写 SQL 改台账的根因）。
     */
    private void voidNcrWithOpenActions(QualityNcr ncr, String tail) {
        List<QualityNcrAction> openActions = actionMapper.selectList(new LambdaQueryWrapper<QualityNcrAction>()
                .eq(QualityNcrAction::getNcrId, ncr.getNcrId())
                .in(QualityNcrAction::getStatus, "PENDING", "PROCESSING")
                .eq(QualityNcrAction::getDelFlag, 0));
        for (QualityNcrAction action : openActions) {
            action.setStatus("VOID");
            action.setResultRemark(appendRemark(action.getResultRemark(), tail));
            actionMapper.updateById(action);
        }
        ncr.setStatus("VOID");
        ncr.setRemark(appendRemark(ncr.getRemark(), tail));
        ncrMapper.updateById(ncr);
    }

    /** 备注追加（留痕用；截断保护，remark 列 500） */
    // ==================== dev-20260923-022 二期：撤销流 ====================

    /**
     * 撤销已生效(DONE)的处置。
     *
     * <p>业内依据：处置结论=一次性使用决策，可 reset 但必须**带权限 + 原因 + 审计**（SAP QM）。
     * 本期只放 SCRAP（无库存影响）；CONCESSION（已转良品库存）/REWORK（已建执行与复检批）需先做反向处理。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityNcrAction revokeAction(Long actionId, String reason, String operatorName) {
        if (actionId == null) {
            throw new BusinessException("处置单ID不能为空");
        }
        if (StringUtils.isBlank(reason)) {
            throw new BusinessException("撤销必须填写原因（留痕要求）");
        }
        QualityNcrAction action = actionMapper.selectById(actionId);
        if (action == null) {
            throw new BusinessException("处置单不存在: " + actionId);
        }
        if ("VOID".equals(action.getStatus())) {
            throw new BusinessException("该处置单已作废（VOID），无需重复撤销");
        }
        if (!"DONE".equals(action.getStatus())) {
            throw new BusinessException("仅支持撤销「已生效(DONE)」的处置（当前：" + action.getStatus() + "）");
        }
        String type = action.getActionType() == null ? "" : action.getActionType().toUpperCase();
        if (!"SCRAP".equals(type)) {
            throw new BusinessException("暂不支持撤销「" + type + "」处置：让步接收已转良品库存、"
                    + "返工已建执行/复检批，需先做反向库存或返工冲销（下一步支持）");
        }
        QualityNcr ncr = ncrMapper.selectById(action.getNcrId());
        if (ncr == null) {
            throw new BusinessException("不良台账不存在: " + action.getNcrId());
        }
        BigDecimal qty = nz(action.getQuantity());
        // 1) 处置单作废 + 留痕（谁/何时/为何）
        action.setStatus("VOID");
        action.setResultRemark(appendRemark(action.getResultRemark(),
                "【撤销】原因：" + reason.trim() + "；操作人：" + (StringUtils.isBlank(operatorName) ? "-" : operatorName)
                        + "；时间：" + LocalDateTime.now().withNano(0) + "（dev-20260923-022）"));
        actionMapper.updateById(action);
        // 2) 台账：已处置量回落 + 状态回退
        BigDecimal disposed = nz(ncr.getDisposedQuantity()).subtract(qty);
        if (disposed.signum() < 0) {
            disposed = BigDecimal.ZERO;
        }
        ncr.setDisposedQuantity(disposed);
        if (!"VOID".equals(ncr.getStatus())) {
            ncr.setStatus(disposed.signum() <= 0 ? "PENDING" : "DISPOSING");
        }
        ncr.setRemark(appendRemark(ncr.getRemark(), "【撤销处置】" + type + " "
                + qty.stripTrailingZeros().toPlainString() + " 件（原因：" + reason.trim() + "）"));
        ncrMapper.updateById(ncr);
        // 3) 检验批已处置量回落 → 判定上界随之上抬（可重新判定；若批已 CLOSED 需先「重开」）
        try {
            qualityLotService.addDisposedQuantity(ncr.getLotId(), qty.negate());
        } catch (Exception e) {
            log.warn("撤销处置后回写检验批已处置量失败（不阻断撤销）: lotId={} err={}", ncr.getLotId(), e.getMessage());
        }
        log.info("处置已撤销: actionId={} ncrNo={} 类型={} 数量={} 原因={} 操作人={}", actionId, ncr.getNcrNo(),
                type, qty.toPlainString(), reason.trim(), operatorName);
        return action;
    }

    private String appendRemark(String origin, String add) {
        String base = origin == null ? "" : origin.trim();
        if (base.length() > 300) {
            base = base.substring(0, 300);
        }
        return base.isEmpty() ? add : base + " ｜ " + add;
    }

    /**
     * 处置的库存影响（dev-20260917-008；2026-09-18 dev-20260918-021 改为 fail-closed）：
     * - 让步接收（特采）：不良数量转良品库存并打特采标记（写 ADJUST 凭证，带 lotId/ncrId）；
     * - 报废：在"只入合格数"的账务口径下不良品从未进良品库，故不产生库存扣减，仅记台账；
     * - 返工：需真实返工工序 + 复检（另立任务 dev-20260918-028），此处不置 DONE。
     *
     * 原则：库存联动失败【必须整体回滚】，禁止"台账已关、库存未动"（此前 catch 吞异常 → 账实不符）。
     */
    private void applyStockEffect(QualityNcr ncr, QualityNcrAction action, String actionType, BigDecimal quantity,
                                  QualityNcrDisposeDTO dto) {
        if ("CONCESSION".equals(actionType) && ncr.getOrderId() != null) {
            com.jjx.inventory.service.InventoryInboundService inboundService =
                    inventoryInboundServiceProvider.getIfAvailable();
            if (inboundService == null) {
                throw new BusinessException("库存服务不可用，让步接收无法联动库存，已回滚处置");
            }
            inboundService.adjustFinishStock(ncr.getOrderId(), ncr.getLotId(), ncr.getNcrId(), quantity,
                    "让步接收（特采）转良品：不良 " + quantity.toPlainString() + " 件");
            action.setStatus("DONE");
            action.setResultRemark("让步接收已转良品库存（特采标记）");
            actionMapper.updateById(action);
        } else if ("SCRAP".equals(actionType)) {
            action.setStatus("DONE");
            action.setResultRemark("报废已登记：不良品未进入良品库存，无需库存扣减（口径A）");
            actionMapper.updateById(action);
        } else if ("REWORK".equals(actionType)) {
            createReworkExecution(ncr, action, quantity, dto);
            action.setStatus("PROCESSING");
            actionMapper.updateById(action);
        }
    }

    private void createReworkExecution(QualityNcr ncr, QualityNcrAction action, BigDecimal quantity,
                                       QualityNcrDisposeDTO dto) {
        if (ncr.getOrderId() == null) {
            throw new BusinessException("返工处置必须关联生产工单");
        }
        if (dto.getStandardProcessId() == null) {
            throw new BusinessException("返工处置必须选择标准工序");
        }
        com.jjx.product.domain.entity.ProductStandardProcess process =
                standardProcessMapper.selectById(dto.getStandardProcessId());
        if (process == null || !Integer.valueOf(1).equals(process.getIsEnabled())) {
            throw new BusinessException("所选返工工序不存在或已停用");
        }
        Integer maxOrder = executionMapper.selectList(new LambdaQueryWrapper<ProductionOperationExecution>()
                        .eq(ProductionOperationExecution::getOrderId, ncr.getOrderId())
                        .orderByDesc(ProductionOperationExecution::getProcessOrder).last("LIMIT 1"))
                .stream().map(ProductionOperationExecution::getProcessOrder).findFirst().orElse(0);
        ProductionOperationExecution execution = new ProductionOperationExecution();
        execution.setExecutionType("REWORK");
        execution.setOrderId(ncr.getOrderId());
        execution.setProcessId(process.getProcessId());
        execution.setProcessName(process.getProcessName());
        execution.setMajorCategory(StringUtils.defaultIfBlank(process.getProcessCategory(), process.getProcessType()));
        java.util.Map<String, Object> instructions = new java.util.LinkedHashMap<>();
        instructions.put("ncrNo", ncr.getNcrNo());
        instructions.put("qualityStandard", process.getQualityStandard());
        instructions.put("description", process.getDescription());
        instructions.put("skillRequirement", process.getSkillRequirement());
        instructions.put("processParamTemplate", process.getProcessParamTemplate());
        instructions.put("reworkRequirement", dto.getReworkRequirement());
        try {
            execution.setCustomProcessParams(objectMapper.writeValueAsString(instructions));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("返工作业说明序列化失败");
        }
        execution.setProcessOrder(maxOrder + 1);
        execution.setTaskSeq(1L);
        execution.setInputQuantity(quantity);
        execution.setOutputQuantity(BigDecimal.ZERO);
        execution.setQualifiedQuantity(BigDecimal.ZERO);
        execution.setDefectiveQuantity(BigDecimal.ZERO);
        execution.setExecutionStatus(0);
        executionMapper.insert(execution);

        ProductionTask task = new ProductionTask();
        // dev-20260923-035：返工任务号与工单同构 —— WO-<工单号>-P<2位工序序>-T001。
        // 原来叫 NCR-4-REWORK，在派工管理/任务列表里看不出属于哪张工单（其实 execution 已挂在原工单下）。
        String orderNo = null;
        try {
            com.jjx.production.domain.entity.ProductionOrder reworkOrder =
                    productionOrderMapper.selectById(ncr.getOrderId());
            orderNo = reworkOrder == null ? null : reworkOrder.getOrderNo();
        } catch (Exception e) {
            log.warn("读取工单号失败（返工任务号回落为 REWORK 前缀）: orderId={} err={}", ncr.getOrderId(), e.getMessage());
        }
        task.setTaskNo(reworkTaskNo(orderNo, execution.getProcessOrder(), 1L));
        task.setExecutionId(execution.getExecutionId());
        task.setTaskQuantity(quantity);
        task.setStatus("PENDING");
        task.setVersion(0);
        // dev-20260923-033：返工工序必须有责任人 —— 默认派给该工单的「一级负责人」（取该工单根任务责任人）。
        // 取不到就 fail-closed：宁可不建工序，也不要生成一条没人能看到、没人能报工的返工任务
        // （实测：原实现 assignee 为空 → 「我的任务」里看不到 → 返工永远停在"执行中"）。
        Long firstLevelAssignee = resolveOrderFirstLevelAssignee(ncr.getOrderId());
        if (firstLevelAssignee == null) {
            throw new BusinessException("返工工序需要有责任人：该工单还没有一级负责人，"
                    + "请先在「工序执行」页把工序派给负责人后再登记返工");
        }
        task.setAssigneeId(firstLevelAssignee);
        task.setCreateBy(action.getOperatorName());
        taskMapper.insert(task);
        action.setReworkExecutionId(execution.getExecutionId());
        action.setResultRemark("已创建返工工序「" + process.getProcessName()
                + "」和生产任务，完成报工后进入 FQC 复检");
    }

    /**
     * 返工任务号（与正常任务同构）：WO-&lt;工单号&gt;-P&lt;2位工序序&gt;-T&lt;2位任务序&gt; —— dev-20260923-035；
     * T 位 3→2 由 dev-20260923-029（单号第 5 批）统一。
     * 工单号取不到时回落 REWORK 前缀，保证号仍可读、可追溯（NCR 号在工序作业说明里，不丢）。
     */
    static String reworkTaskNo(String orderNo, Integer processOrder, long taskSeq) {
        String order = (orderNo == null || orderNo.isBlank()) ? "REWORK" : orderNo.trim();
        int po = processOrder == null ? 0 : processOrder;
        return String.format("%s-P%02d-T%02d", order, po, taskSeq);
    }

    /**
     * 该工单的「一级负责人」= 该工单任一工序根任务（parent_task_id 为空）的责任人，取最新一条。
     * dev-20260923-033：返工工序默认派给他，再由他派给工人（与正常工序的派工链一致）。
     */
    private Long resolveOrderFirstLevelAssignee(Long orderId) {
        if (orderId == null) {
            return null;
        }
        List<ProductionOperationExecution> executions = executionMapper.selectList(
                new LambdaQueryWrapper<ProductionOperationExecution>()
                        .eq(ProductionOperationExecution::getOrderId, orderId));
        if (executions.isEmpty()) {
            return null;
        }
        List<Long> executionIds = executions.stream()
                .map(ProductionOperationExecution::getExecutionId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (executionIds.isEmpty()) {
            return null;
        }
        List<ProductionTask> roots = taskMapper.selectList(new LambdaQueryWrapper<ProductionTask>()
                .in(ProductionTask::getExecutionId, executionIds)
                .isNull(ProductionTask::getParentTaskId)
                .isNotNull(ProductionTask::getAssigneeId)
                .orderByDesc(ProductionTask::getTaskId)
                .last("LIMIT 1"));
        return roots.isEmpty() ? null : roots.get(0).getAssigneeId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityNcrAction completeAction(Long actionId, String resultRemark, Long reworkExecutionId) {
        QualityNcrAction action = actionMapper.selectById(actionId);
        if (action == null) {
            throw new BusinessException("处置单不存在: " + actionId);
        }
        if ("DONE".equals(action.getStatus())) {
            return action; // 幂等：已完成直接返回
        }
        if ("REWORK".equals(action.getActionType())) {
            QualityNcr ncr = getNcr(action.getNcrId());
            Long executionId = action.getReworkExecutionId();
            ProductionOperationExecution execution = executionId == null ? null : executionMapper.selectById(executionId);
            if (execution == null || !Integer.valueOf(4).equals(execution.getExecutionStatus())) {
                throw new BusinessException("返工工序尚未完成报工，不能进入复检结案");
            }
            if (action.getReinspectionLotId() == null) {
                QualityFinishService qualityFinishService = qualityFinishServiceProvider.getIfAvailable();
                if (qualityFinishService == null) {
                    throw new BusinessException("成品复检服务不可用，暂不能生成返工复检批");
                }
                QualityLot reinspection = qualityFinishService.reinspectLot(ncr.getLotId());
                action.setReinspectionLotId(reinspection.getLotId());
                action.setResultRemark("返工报工已完成，已生成 FQC 复检批 " + reinspection.getLotNo());
                actionMapper.updateById(action);
                return action;
            }
            QualityLot reinspection = qualityLotMapper.selectById(action.getReinspectionLotId());
            if (reinspection == null || !"pass".equalsIgnoreCase(reinspection.getResult())) {
                throw new BusinessException("FQC 复检尚未合格，返工处置不能完成");
            }
            reworkExecutionId = executionId;
            // dev-20260923-046：返工完成也是结案路径之一，同样必须看未关闭 CAPA（原实现直接关）
            boolean canClose = canCloseNcr(ncr.getDisposedQuantity(), ncr.getDefectQuantity(),
                    capaService.countOpen(ncr.getNcrId(), null));
            ncr.setStatus(canClose ? "CLOSED" : "DISPOSING");
            ncrMapper.updateById(ncr);
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
        return redisSequenceService.generateBusinessNumberByType("quality_ncr", "NCR", "yyMMdd", 3);
    }

    /**
     * 不良单能否结案（dev-20260923-046）—— **三条结案路径共用**：
     * 处置完成（completeAction）/ 复检更正同步台账（syncFromLot）/ 返工完成。
     *
     * <p>判据：处置量已覆盖不良量 **且没有未关闭的 CAPA**。
     * 盲存原因：口径要求「有未关闭 CAPA 的不良单不得结案」，但此前只有处置完成路径查了 CAPA，
     * 另两条路径直接 `disposed≥defect → CLOSED`，造成门禁有洞。</p>
     */
    static boolean canCloseNcr(BigDecimal disposed, BigDecimal defect, long openCapaCount) {
        return nz(disposed).compareTo(nz(defect)) >= 0 && openCapaCount == 0;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
