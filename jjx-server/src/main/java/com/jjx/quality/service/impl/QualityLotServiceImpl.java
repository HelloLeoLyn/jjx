package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.enums.AllowedActionEnum;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityLotItem;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.dto.FqcCompletionSummary;
import com.jjx.quality.dto.QualityLotCreateDTO;
import com.jjx.quality.dto.QualityLotItemDTO;
import com.jjx.quality.dto.QualityLotQueryDTO;
import com.jjx.quality.enums.QualityLotStatusEnum;
import com.jjx.quality.enums.QualityLotTypeEnum;
import com.jjx.quality.mapper.QualityLotItemMapper;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.mapper.QualityNcrMapper;
import com.jjx.quality.mapper.QualityNcrActionMapper;
import com.jjx.quality.service.QualityLotService;
import com.jjx.quality.service.support.AllowedActionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检验批服务实现 —— dev-20260917-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityLotServiceImpl extends ServiceImpl<QualityLotMapper, QualityLot> implements QualityLotService {

    private final QualityLotMapper lotMapper;
    private final QualityLotItemMapper itemMapper;
    private final QualityNcrMapper ncrMapper;
    private final QualityNcrActionMapper ncrActionMapper;
    private final RedisSequenceService redisSequenceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityLot createLot(QualityLotCreateDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getLotType())) {
            throw new BusinessException("检验批类型不能为空（IQC/IPQC/FQC）");
        }
        if (QualityLotTypeEnum.getByCode(dto.getLotType()) == null) {
            throw new BusinessException("检验批类型不合法：" + dto.getLotType());
        }
        BigDecimal lotQty = dto.getLotQuantity() == null ? BigDecimal.ZERO : dto.getLotQuantity();
        if (lotQty.signum() <= 0) {
            throw new BusinessException("检验批量必须大于 0");
        }
        // 防重复建批：同一来源 + 同一来源行 + 同一版本只允许一条
        int version = dto.getVersion() == null ? 1 : dto.getVersion();
        if (StringUtils.isNotBlank(dto.getSourceType()) && dto.getSourceId() != null) {
            Long exists = lotMapper.selectCount(new LambdaQueryWrapper<QualityLot>()
                    .eq(QualityLot::getSourceType, dto.getSourceType())
                    .eq(QualityLot::getSourceId, dto.getSourceId())
                    .eq(dto.getSourceItemId() != null, QualityLot::getSourceItemId, dto.getSourceItemId())
                    .eq(QualityLot::getVersion, version));
            if (exists != null && exists > 0) {
                throw new BusinessException("该来源已存在同版本检验批，如需再次检验请走复检（新版本）");
            }
        }

        QualityLot lot = new QualityLot();
        lot.setLotNo(generateLotNo());
        lot.setLotType(dto.getLotType());
        lot.setSourceType(dto.getSourceType());
        lot.setSourceId(dto.getSourceId());
        lot.setSourceItemId(dto.getSourceItemId());
        lot.setOrderId(dto.getOrderId());
        lot.setExecutionId(dto.getExecutionId());
        lot.setMaterialId(dto.getMaterialId());
        lot.setMaterialCode(dto.getMaterialCode());
        lot.setMaterialName(dto.getMaterialName());
        lot.setProductId(dto.getProductId());
        lot.setProductCode(dto.getProductCode());
        lot.setProductName(dto.getProductName());
        lot.setBatchNo(dto.getBatchNo());
        lot.setLotQuantity(lotQty);
        lot.setInspectedQuantity(BigDecimal.ZERO);
        lot.setPassQuantity(BigDecimal.ZERO);
        lot.setFailQuantity(BigDecimal.ZERO);
        lot.setStoredQuantity(BigDecimal.ZERO);
        lot.setDisposedQuantity(BigDecimal.ZERO);
        lot.setSamplingPlanId(dto.getSamplingPlanId());
        lot.setSampleQuantity(dto.getSampleQuantity());
        lot.setAcceptNumber(dto.getAcceptNumber());
        lot.setRejectNumber(dto.getRejectNumber());
        lot.setResult("pending");
        lot.setStatus(QualityLotStatusEnum.PENDING.getCode());
        lot.setParentLotId(dto.getParentLotId());
        lot.setVersion(version);
        lot.setRemark(dto.getRemark());
        lot.setDelFlag(0);
        lotMapper.insert(lot);

        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            saveItems(lot.getLotId(), dto.getItems());
        }
        log.info("检验批已创建: lotNo={} type={} 批量={} 来源={}:{}", lot.getLotNo(), lot.getLotType(),
                lotQty.toPlainString(), dto.getSourceType(), dto.getSourceId());
        return lot;
    }

    @Override
    public QualityLot getLot(Long lotId) {
        QualityLot lot = lotMapper.selectById(lotId);
        if (lot == null) {
            throw new BusinessException("检验批不存在: " + lotId);
        }
        // dev-20260923-039：详情也下发 allowedActions（列表/详情同一口径）
        fillReinspectInfo(new ArrayList<>(List.of(lot)));
        return lot;
    }

    @Override
    public QualityLot lockLot(Long lotId) {
        QualityLot lot = lotMapper.selectForUpdate(lotId);
        if (lot == null) {
            throw new BusinessException("检验批不存在: " + lotId);
        }
        return lot;
    }

    @Override
    public boolean isLatestVersion(Long lotId) {
        Long children = lotMapper.selectCount(new LambdaQueryWrapper<QualityLot>()
                .eq(QualityLot::getParentLotId, lotId));
        return children == null || children == 0;
    }

    @Override
    public List<QualityLot> listBySource(String sourceType, Long sourceId) {
        if (StringUtils.isBlank(sourceType) || sourceId == null) {
            return new ArrayList<>();
        }
        List<QualityLot> lots = lotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                .eq(QualityLot::getSourceType, sourceType)
                .eq(QualityLot::getSourceId, sourceId)
                .orderByAsc(QualityLot::getVersion)
                .orderByAsc(QualityLot::getLotId));
        fillReinspectInfo(lots);
        return lots;
    }

    @Override
    public List<QualityLot> listByOrder(Long orderId, Long executionId) {
        List<QualityLot> lots = lotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                .eq(orderId != null, QualityLot::getOrderId, orderId)
                .eq(executionId != null, QualityLot::getExecutionId, executionId)
                .orderByAsc(QualityLot::getLotId));
        fillReinspectInfo(lots);
        return lots;
    }

    @Override
    public IPage<QualityLot> pageLots(QualityLotQueryDTO query) {
        QualityLotQueryDTO q = query == null ? new QualityLotQueryDTO() : query;
        LambdaQueryWrapper<QualityLot> wrapper = new LambdaQueryWrapper<QualityLot>()
                .eq(StringUtils.isNotBlank(q.getLotType()), QualityLot::getLotType, q.getLotType())
                .eq(StringUtils.isNotBlank(q.getStatus()), QualityLot::getStatus, q.getStatus())
                .like(StringUtils.isNotBlank(q.getLotNo()), QualityLot::getLotNo, q.getLotNo())
                .eq(q.getOrderId() != null, QualityLot::getOrderId, q.getOrderId())
                .eq(q.getExecutionId() != null, QualityLot::getExecutionId, q.getExecutionId())
                .eq(q.getMaterialId() != null, QualityLot::getMaterialId, q.getMaterialId())
                .eq(StringUtils.isNotBlank(q.getMaterialCode()), QualityLot::getMaterialCode, q.getMaterialCode())
                .eq(q.getProductId() != null, QualityLot::getProductId, q.getProductId())
                .eq(StringUtils.isNotBlank(q.getBatchNo()), QualityLot::getBatchNo, q.getBatchNo())
                .orderByDesc(QualityLot::getLotId);
        if (Boolean.TRUE.equals(q.getHasPendingDefect())) {
            wrapper.apply("fail_quantity > disposed_quantity");
        }
        IPage<QualityLot> page = lotMapper.selectPage(new Page<>(q.getPageNum(), q.getPageSize()), wrapper);
        fillReinspectInfo(page.getRecords());
        return page;
    }

    /**
     * 补列表展示用的复检关系字段（dev-20260922-012 G5）：父批号 + 是否已被后继版本取代。
     * 两次批量查询，不做逐行 N+1。
     */
    private void fillReinspectInfo(List<QualityLot> lots) {
        if (lots == null || lots.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> parentIds = new HashSet<>();
        for (QualityLot lot : lots) {
            if (lot.getLotId() != null) {
                ids.add(lot.getLotId());
            }
            if (lot.getParentLotId() != null) {
                parentIds.add(lot.getParentLotId());
            }
        }
        if (!parentIds.isEmpty()) {
            for (QualityLot parent : lotMapper.selectBatchIds(parentIds)) {
                for (QualityLot lot : lots) {
                    if (parent.getLotId() != null && parent.getLotId().equals(lot.getParentLotId())) {
                        lot.setParentLotNo(parent.getLotNo());
                    }
                }
            }
        }
        Set<Long> superseded = new HashSet<>();
        if (!ids.isEmpty()) {
            List<QualityLot> children = lotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                    .select(QualityLot::getParentLotId)
                    .in(QualityLot::getParentLotId, ids));
            for (QualityLot child : children) {
                if (child.getParentLotId() != null) {
                    superseded.add(child.getParentLotId());
                }
            }
        }
        // dev-20260923-039（第二片）：「有未处置不良」也算进动作判据（有则不给批级动作，引导去不良台账）
        Set<Long> withOpenDefect = new HashSet<>();
        if (!ids.isEmpty()) {
            List<QualityNcr> openNcrs = ncrMapper.selectList(new LambdaQueryWrapper<QualityNcr>()
                    .select(QualityNcr::getLotId)
                    .in(QualityNcr::getLotId, ids)
                    .in(QualityNcr::getStatus, "PENDING", "DISPOSING")
                    .apply("defect_quantity > disposed_quantity"));
            if (openNcrs != null) {
                for (QualityNcr ncr : openNcrs) {
                    if (ncr.getLotId() != null) {
                        withOpenDefect.add(ncr.getLotId());
                    }
                }
            }
        }
        for (QualityLot lot : lots) {
            boolean isSuperseded = superseded.contains(lot.getLotId());
            lot.setSuperseded(isSuperseded);
            // dev-20260923-039（第二片）：allowedActions 由唯一出处算好下发，前端只按它渲染
            lot.setAllowedActions(AllowedActionEnum.codesOf(AllowedActionResolver.forLot(
                    lot.getStatus(), isSuperseded, withOpenDefect.contains(lot.getLotId()))));
        }
    }

    @Override
    public List<QualityLotItem> listItems(Long lotId) {
        return itemMapper.selectList(new LambdaQueryWrapper<QualityLotItem>()
                .eq(QualityLotItem::getLotId, lotId)
                .orderByAsc(QualityLotItem::getSortOrder)
                .orderByAsc(QualityLotItem::getItemId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long lotId, List<QualityLotItemDTO> items) {
        // 2026-09-21（dev-20260921-030）：录入前先取行锁 + 状态/版本守卫
        // —— 原实现只校验批存在，判定后仍能全量覆盖检验项（会篡改已判定记录的依据）。
        QualityLot lot = lockLot(lotId);
        String status = lot.getStatus();
        if (!QualityLotStatusEnum.PENDING.getCode().equals(status)
                && !QualityLotStatusEnum.INSPECTING.getCode().equals(status)) {
            throw new BusinessException("该检验批已判定（" + QualityLotStatusEnum.labelOf(status)
                    + "），检验项已冻结；如需重录请先「复检」生成新版本");
        }
        if (!isLatestVersion(lotId)) {
            throw new BusinessException("该批已存在复检新版本，请对最新版本操作");
        }
        // dev-20260923-039：守卫同源 —— 录入也走唯一出处（批级动作在有未处置不良时不可用）
        assertAction(lot, AllowedActionEnum.LOT_INSPECT);
        itemMapper.delete(new LambdaQueryWrapper<QualityLotItem>().eq(QualityLotItem::getLotId, lotId));
        if (items == null || items.isEmpty()) {
            return;
        }
        int order = 0;
        for (QualityLotItemDTO dto : items) {
            if (dto == null || StringUtils.isBlank(dto.getCheckItem())) {
                continue;
            }
            QualityLotItem item = new QualityLotItem();
            item.setLotId(lotId);
            item.setCheckItem(dto.getCheckItem());
            item.setStandard(dto.getStandard());
            item.setInspectionMethod(dto.getInspectionMethod());
            item.setEquipment(dto.getEquipment());
            item.setSampleValues(dto.getSampleValues());
            item.setActualValue(dto.getActualValue());
            item.setCrQuantity(dto.getCrQuantity() == null ? BigDecimal.ZERO : dto.getCrQuantity());
            item.setMaQuantity(dto.getMaQuantity() == null ? BigDecimal.ZERO : dto.getMaQuantity());
            item.setMiQuantity(dto.getMiQuantity() == null ? BigDecimal.ZERO : dto.getMiQuantity());
            item.setResult(dto.getResult());
            item.setRemark(dto.getRemark());
            item.setSortOrder(dto.getSortOrder() == null ? order++ : dto.getSortOrder());
            itemMapper.insert(item);
        }
        // dev-20260922-011（G6）：录入过检验项就把状态从「待检」推进到「检验中」，
        // 让列表能区分"还没录"与"已录待判定"（此前 saveItems 完全不改状态，页面上看不出录没录）。
        // 语义不变式：PENDING=未录入、INSPECTING=已录入待判定、JUDGED=已判定、CLOSED=已关闭。
        if (QualityLotStatusEnum.PENDING.getCode().equals(status)) {
            lot.setStatus(QualityLotStatusEnum.INSPECTING.getCode());
            lotMapper.updateById(lot);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityLot applyJudgement(Long lotId, BigDecimal inspectedQuantity, BigDecimal passQuantity,
                                     BigDecimal failQuantity, String result, String inspector) {
        // 2026-09-21（dev-20260921-030）：行锁串行化同批判定；拒绝 已关闭 / 历史版本
        QualityLot lot = lockLot(lotId);
        String status = lot.getStatus();
        if (QualityLotStatusEnum.CLOSED.getCode().equals(status)) {
            throw new BusinessException("该检验批已关闭，不能再判定");
        }
        if (!isLatestVersion(lotId)) {
            throw new BusinessException("该批已存在复检新版本，请对最新版本判定");
        }
        // dev-20260923-039：守卫同源（判定）
        assertAction(lot, AllowedActionEnum.LOT_JUDGE);
        BigDecimal inspected = nz(inspectedQuantity);
        BigDecimal pass = nz(passQuantity);
        BigDecimal fail = nz(failQuantity);
        if (inspected.signum() <= 0) {
            throw new BusinessException("检验数量必须大于 0");
        }
        if (inspected.compareTo(lot.getLotQuantity()) > 0) {
            throw new BusinessException("检验数量不能超过该批批量（" + lot.getLotQuantity().toPlainString() + "）");
        }
        if (pass.add(fail).compareTo(inspected) != 0) {
            throw new BusinessException("合格数量 + 不良数量必须等于检验数量");
        }
        // 2026-09-23（dev-20260923-021 一期）：可判合格上界护栏 ——
        // 链上已报废（SCRAP DONE，未回收）/ 让步未客户确认的量，不得通过换版本重判回良品（业内数量守恒）。
        com.jjx.quality.dto.vo.JudgementGuardVO guard = buildJudgementGuard(lot);
        if (Boolean.TRUE.equals(guard.getGuardAvailable())
                && pass.compareTo(guard.getUpperBound()) > 0) {
            throw new BusinessException(judgementGuardMessage(guard, pass));
        }
        lot.setInspectedQuantity(inspected);
        lot.setPassQuantity(pass);
        lot.setFailQuantity(fail);
        lot.setResult(result);
        lot.setInspector(inspector);
        lot.setInspectTime(java.time.LocalDateTime.now());
        lot.setStatus(QualityLotStatusEnum.JUDGED.getCode());
        lotMapper.updateById(lot);
        return lot;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityLot addStoredQuantity(Long lotId, BigDecimal delta) {
        QualityLot lot = getLot(lotId);
        BigDecimal next = nz(lot.getStoredQuantity()).add(nz(delta));
        // dev-20260923（022 收尾）：已入库数不允许为负 —— 红冲把净额冲回 0 即止（此前会写出 −98 这类异常值）
        if (next.signum() < 0) {
            log.warn("检验批已入库数将被写成负数，已按 0 收敛: lotNo={} 原值={} delta={}",
                    lot.getLotNo(), nz(lot.getStoredQuantity()).toPlainString(), nz(delta).toPlainString());
            next = BigDecimal.ZERO;
        }
        if (next.compareTo(lot.getLotQuantity()) > 0) {
            throw new BusinessException("入库/放行累计不能超过该批批量（" + lot.getLotQuantity().toPlainString()
                    + "，已入库 " + nz(lot.getStoredQuantity()).toPlainString() + "）");
        }
        lot.setStoredQuantity(next);
        closeIfSettled(lot);
        lotMapper.updateById(lot);
        return lot;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityLot addDisposedQuantity(Long lotId, BigDecimal delta) {
        QualityLot lot = getLot(lotId);
        BigDecimal next = nz(lot.getDisposedQuantity()).add(nz(delta));
        if (next.compareTo(nz(lot.getFailQuantity())) > 0) {
            throw new BusinessException("已处置数量不能超过不良数量（"
                    + nz(lot.getFailQuantity()).toPlainString() + "）");
        }
        lot.setDisposedQuantity(next);
        closeIfSettled(lot);
        lotMapper.updateById(lot);
        return lot;
    }

    private void closeIfSettled(QualityLot lot) {
        if (QualityLotStatusEnum.JUDGED.getCode().equals(lot.getStatus())
                && nz(lot.getStoredQuantity()).compareTo(nz(lot.getPassQuantity())) >= 0
                && nz(lot.getDisposedQuantity()).compareTo(nz(lot.getFailQuantity())) >= 0) {
            lot.setStatus(QualityLotStatusEnum.CLOSED.getCode());
        }
    }

    /**
     * dev-20260922-030（用户拍板 A）：重开已关闭的检验批（CLOSED → JUDGED），之后可正常复检。
     * 必须填原因（留痕到 remark）；只允许最新版本、且必须是 CLOSED。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualityLot reopenLot(Long lotId, String reason) {
        QualityLot lot = lockLot(lotId);
        if (!QualityLotStatusEnum.CLOSED.getCode().equals(lot.getStatus())) {
            throw new BusinessException("只有已关闭的检验批可以重开（当前：" 
                    + QualityLotStatusEnum.labelOf(lot.getStatus()) + "）");
        }
        if (!isLatestVersion(lotId)) {
            throw new BusinessException("该批已有复检新版本，请对最新版本操作");
        }
        // dev-20260923-039：守卫同源（重开）
        assertAction(lot, AllowedActionEnum.LOT_REOPEN);
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new BusinessException("重开必须填写原因（用于留痕）");
        }
        String by;
        try {
            by = com.jjx.system.utils.SecurityUtils.getDisplayName();
        } catch (Exception e) {
            by = "system";
        }
        lot.setStatus(QualityLotStatusEnum.JUDGED.getCode());
        String note = "【重开】" + by + "：" + r;
        String base = lot.getRemark() == null ? "" : lot.getRemark().trim();
        String next = base.isEmpty() ? note : base + " ｜ " + note;
        lot.setRemark(next.length() > 500 ? next.substring(0, 500) : next);
        lotMapper.updateById(lot);
        log.info("检验批已重开: lotNo={} 操作人={} 原因={}", lot.getLotNo(), by, r);
        return lot;
    }

    @Override
    public FqcCompletionSummary summarizeEffectiveFqc(Long orderId) {
        FqcCompletionSummary summary = new FqcCompletionSummary();
        if (orderId == null) {
            return summary;
        }
        List<QualityLot> lots = lotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                .eq(QualityLot::getLotType, QualityLotTypeEnum.FQC.getCode())
                .eq(QualityLot::getOrderId, orderId)
                .eq(QualityLot::getDelFlag, 0)
                .orderByAsc(QualityLot::getLotId));
        if (lots.isEmpty()) {
            return summary;
        }
        summary.setHasLot(true);
        // 被后继复检版本取代的批：parent_lot_id 命中任一批即视为失效（与 syncFinishInbound 同口径）
        Set<Long> superseded = new HashSet<>();
        for (QualityLot lot : lots) {
            if (lot.getParentLotId() != null) {
                superseded.add(lot.getParentLotId());
            }
        }
        BigDecimal qualified = BigDecimal.ZERO;
        BigDecimal undisposed = BigDecimal.ZERO;
        BigDecimal disposedTotal = BigDecimal.ZERO;
        java.util.Set<Long> allNcrIds = new java.util.HashSet<>();
        int pending = 0;
        int effective = 0;
        for (QualityLot lot : lots) {
            if (superseded.contains(lot.getLotId())) {
                continue; // 已有复检新版本 → 不计账
            }
            effective++;
            boolean judged = lot.getInspectedQuantity() != null && lot.getInspectedQuantity().signum() > 0;
            if (!judged) {
                pending++;
                continue;
            }
            qualified = qualified.add(nz(lot.getPassQuantity()));
            disposedTotal = disposedTotal.add(nz(lot.getDisposedQuantity()));
            BigDecimal left = nz(lot.getFailQuantity()).subtract(nz(lot.getDisposedQuantity()));
            // 返工登记时数量已被预占，但只有报工完成且复检合格才算真正处置完成。
            // 将未完成返工动作加回完工门禁，防止 PROCESSING 状态绕过工单完工检查。
            List<com.jjx.quality.domain.entity.QualityNcr> lotNcrs = ncrMapper.selectList(
                    new LambdaQueryWrapper<com.jjx.quality.domain.entity.QualityNcr>()
                            .eq(com.jjx.quality.domain.entity.QualityNcr::getLotId, lot.getLotId()));
            if (!lotNcrs.isEmpty()) {
                List<Long> ncrIds = lotNcrs.stream().map(com.jjx.quality.domain.entity.QualityNcr::getNcrId).toList();
                allNcrIds.addAll(ncrIds);
                BigDecimal processingRework = ncrActionMapper.selectList(
                                new LambdaQueryWrapper<com.jjx.quality.domain.entity.QualityNcrAction>()
                                        .in(com.jjx.quality.domain.entity.QualityNcrAction::getNcrId, ncrIds)
                                        .eq(com.jjx.quality.domain.entity.QualityNcrAction::getActionType, "REWORK")
                                        .ne(com.jjx.quality.domain.entity.QualityNcrAction::getStatus, "DONE"))
                        .stream().map(com.jjx.quality.domain.entity.QualityNcrAction::getQuantity)
                        .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                left = left.add(processingRework);
            }
            if (left.signum() > 0) {
                undisposed = undisposed.add(left);
            }
        }
        // dev-20260923-028：已登记报废合计（一句话原因用）——按本单全部不良单一次性汇总，避免逐批查询
        BigDecimal scrapped = BigDecimal.ZERO;
        if (!allNcrIds.isEmpty()) {
            scrapped = ncrActionMapper.selectList(
                            new LambdaQueryWrapper<com.jjx.quality.domain.entity.QualityNcrAction>()
                                    .in(com.jjx.quality.domain.entity.QualityNcrAction::getNcrId, allNcrIds)
                                    .eq(com.jjx.quality.domain.entity.QualityNcrAction::getActionType, "SCRAP")
                                    .eq(com.jjx.quality.domain.entity.QualityNcrAction::getStatus, "DONE"))
                    .stream().map(com.jjx.quality.domain.entity.QualityNcrAction::getQuantity)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        summary.setEffectiveLotCount(effective);
        summary.setPendingCount(pending);
        summary.setQualifiedTotal(qualified);
        summary.setUndisposedFailQuantity(undisposed);
        summary.setDisposedFailTotal(disposedTotal);
        summary.setScrappedTotal(scrapped);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markOrderFinishedStored(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        List<QualityLot> lots = lotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                .eq(QualityLot::getLotType, QualityLotTypeEnum.FQC.getCode())
                .eq(QualityLot::getOrderId, orderId)
                .eq(QualityLot::getDelFlag, 0)
                .orderByAsc(QualityLot::getLotId));
        if (lots.isEmpty()) {
            return 0;
        }
        Set<Long> superseded = new HashSet<>();
        for (QualityLot lot : lots) {
            if (lot.getParentLotId() != null) {
                superseded.add(lot.getParentLotId());
            }
        }
        int changed = 0;
        for (QualityLot lot : lots) {
            if (superseded.contains(lot.getLotId())) {
                continue;
            }
            boolean judged = lot.getInspectedQuantity() != null && lot.getInspectedQuantity().signum() > 0;
            if (!judged) {
                continue;
            }
            BigDecimal pass = nz(lot.getPassQuantity());
            BigDecimal fail = nz(lot.getFailQuantity());
            BigDecimal disposed = nz(lot.getDisposedQuantity());
            if (nz(lot.getStoredQuantity()).compareTo(pass) < 0) {
                lot.setStoredQuantity(pass);
            }
            // 关闭：合格全部入库 + 不良全部处置
            if (nz(lot.getStoredQuantity()).compareTo(pass) >= 0 && disposed.compareTo(fail) >= 0) {
                lot.setStatus(QualityLotStatusEnum.CLOSED.getCode());
            }
            lotMapper.updateById(lot);
            changed++;
        }
        log.info("完工入库回写检验批已入库/关闭: orderId={} 批数={}", orderId, changed);
        return changed;
    }

    /** 统一走持久化号段，避免 count+1 的并发冲突。 */
    private String generateLotNo() {
        return redisSequenceService.generateBusinessNumberByType("quality_lot", "QL", "yyMMdd", 3);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ==================== 判定护栏（dev-20260923-021 一期） ====================

    /**
     * 判定护栏（对外只读）：可判合格上界 = 批批量 − 链上已报废未回收 − 让步未客户确认。
     * 设计依据 jjx-docs/design/fqc-judgement-upper-bound-dev-20260923-021.md。
     */
    @Override
    public com.jjx.quality.dto.vo.JudgementGuardVO evaluateJudgementGuard(Long lotId) {
        return buildJudgementGuard(getLot(lotId));
    }

    /**
     * 计算护栏：实时汇总链上处置单（quality_ncr_action，DONE）；
     * SCRAP → 不可回收（扣减）；CONCESSION 未确认 → 扣减；REWORK → 可回收（不扣减，回收走返工后的复检批）。
     * 任何异常都降级为「不校验」（guardAvailable=false + WARN），宁可不挡也不做错杀。
     */
    private com.jjx.quality.dto.vo.JudgementGuardVO buildJudgementGuard(QualityLot lot) {
        com.jjx.quality.dto.vo.JudgementGuardVO vo = new com.jjx.quality.dto.vo.JudgementGuardVO();
        vo.setLotId(lot.getLotId());
        vo.setLotNo(lot.getLotNo());
        vo.setLotQuantity(nz(lot.getLotQuantity()));
        vo.setScrappedQuantity(BigDecimal.ZERO);
        vo.setConcessionPendingQuantity(BigDecimal.ZERO);
        vo.setGuardAvailable(true);
        try {
            List<Long> chainIds = chainLotIds(lot);
            BigDecimal scrap = BigDecimal.ZERO;
            BigDecimal concessionPending = BigDecimal.ZERO;
            if (!chainIds.isEmpty()) {
                for (Map<String, Object> row : ncrActionMapper.sumDoneActionsByLotIds(chainIds)) {
                    String type = row.get("actionType") == null ? "" : String.valueOf(row.get("actionType"));
                    BigDecimal qty = toDecimal(row.get("qty"));
                    BigDecimal confirmed = toDecimal(row.get("confirmedQty"));
                    if ("SCRAP".equalsIgnoreCase(type)) {
                        scrap = scrap.add(qty);
                    } else if ("CONCESSION".equalsIgnoreCase(type)) {
                        BigDecimal unconfirmed = qty.subtract(confirmed);
                        if (unconfirmed.signum() > 0) {
                            concessionPending = concessionPending.add(unconfirmed);
                        }
                    }
                }
            }
            vo.setScrappedQuantity(scrap);
            vo.setConcessionPendingQuantity(concessionPending);
            BigDecimal upper = com.jjx.quality.service.support.JudgementBoundCalculator
                    .upperBound(vo.getLotQuantity(), scrap, concessionPending);
            vo.setUpperBound(upper);
            vo.setSuggestedPass(upper);
            vo.setSuggestedFail(com.jjx.quality.service.support.JudgementBoundCalculator
                    .suggestedFail(vo.getLotQuantity(), upper));
            vo.setNeedWarning(com.jjx.quality.service.support.JudgementBoundCalculator
                    .needWarning(vo.getLotQuantity(), upper));
            if (Boolean.TRUE.equals(vo.getNeedWarning())) {
                vo.setMessage(judgementGuardMessage(vo, null));
            }
        } catch (Exception e) {
            log.warn("判定护栏计算失败，已降级为不校验: lotId={} err={}", lot.getLotId(), e.getMessage());
            vo.setGuardAvailable(false);
            vo.setUpperBound(vo.getLotQuantity());
            vo.setSuggestedPass(vo.getLotQuantity());
            vo.setSuggestedFail(BigDecimal.ZERO);
            vo.setNeedWarning(false);
            vo.setMessage(null);
        }
        return vo;
    }

    /** 批链（含自身）：沿 parent_lot_id 回溯，限 50 跳防脏数据成环 */
    private List<Long> chainLotIds(QualityLot lot) {
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        QualityLot cur = lot;
        int hop = 0;
        while (cur != null && cur.getLotId() != null && seen.add(cur.getLotId()) && hop++ < 50) {
            ids.add(cur.getLotId());
            Long parentId = cur.getParentLotId();
            cur = parentId == null ? null : lotMapper.selectById(parentId);
        }
        return ids;
    }

    /**
     * 护栏提示文案（可操作）：说清上限、不可回收量、以及两条合法出路（返工 / 让步）。
     *
     * @param submitted 本次提交的合格量（null = 仅用于展示）
     */
    private String judgementGuardMessage(com.jjx.quality.dto.vo.JudgementGuardVO vo, BigDecimal submitted) {
        StringBuilder sb = new StringBuilder();
        if (submitted != null) {
            sb.append("合格数量 ").append(submitted.stripTrailingZeros().toPlainString())
              .append(" 超过本批可判合格上限 ").append(vo.getUpperBound().stripTrailingZeros().toPlainString()).append("；");
        }
        sb.append("本批可判合格上限 ").append(vo.getUpperBound().stripTrailingZeros().toPlainString())
          .append("（批批量 ").append(vo.getLotQuantity().stripTrailingZeros().toPlainString());
        if (nz(vo.getScrappedQuantity()).signum() > 0) {
            sb.append(" − 已报废 ").append(vo.getScrappedQuantity().stripTrailingZeros().toPlainString());
        }
        if (nz(vo.getConcessionPendingQuantity()).signum() > 0) {
            sb.append(" − 让步未确认 ").append(vo.getConcessionPendingQuantity().stripTrailingZeros().toPlainString());
        }
        sb.append("）。已报废/让步未确认的数量不可回填良品；如需放行，请先登记")
          .append("返工处置（REWORK）并走返工复检，或按让步接收处理（需客户确认）；")
          .append("若原报废判定有误，可先「撤销」该报废处置（需质量主管权限）再重新判定。");
        return sb.toString();
    }

    private BigDecimal toDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return BigDecimal.valueOf(((Number) v).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ==================== dev-20260923-039：状态×动作唯一出处的守卫同源 ====================

    @Override
    public boolean hasOpenDefect(Long lotId) {
        if (lotId == null) {
            return false;
        }
        Long cnt = ncrMapper.selectCount(new LambdaQueryWrapper<QualityNcr>()
                .eq(QualityNcr::getLotId, lotId)
                .in(QualityNcr::getStatus, "PENDING", "DISPOSING")
                .apply("defect_quantity > disposed_quantity"));
        return cnt != null && cnt > 0;
    }

    @Override
    public void assertAction(QualityLot lot, AllowedActionEnum action) {
        if (lot == null || action == null) {
            return;
        }
        boolean superseded = lot.getLotId() != null && !isLatestVersion(lot.getLotId());
        boolean openDefect = hasOpenDefect(lot.getLotId());
        List<AllowedActionEnum> allowed = AllowedActionResolver.forLot(
                lot.getStatus(), superseded, openDefect);
        if (!allowed.contains(action)) {
            throw new BusinessException(AllowedActionResolver.lotBlockReason(
                    lot.getStatus(), superseded, openDefect));
        }
    }
}
