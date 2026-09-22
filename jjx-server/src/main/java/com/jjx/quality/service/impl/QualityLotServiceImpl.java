package com.jjx.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityLotItem;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检验批服务实现 —— dev-20260917-001
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityLotServiceImpl extends ServiceImpl<QualityLotMapper, QualityLot> implements QualityLotService {

    private static final DateTimeFormatter LOT_NO_DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final QualityLotMapper lotMapper;
    private final QualityLotItemMapper itemMapper;
    private final QualityNcrMapper ncrMapper;
    private final QualityNcrActionMapper ncrActionMapper;

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
        return lotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                .eq(QualityLot::getSourceType, sourceType)
                .eq(QualityLot::getSourceId, sourceId)
                .orderByAsc(QualityLot::getVersion)
                .orderByAsc(QualityLot::getLotId));
    }

    @Override
    public List<QualityLot> listByOrder(Long orderId, Long executionId) {
        return lotMapper.selectList(new LambdaQueryWrapper<QualityLot>()
                .eq(orderId != null, QualityLot::getOrderId, orderId)
                .eq(executionId != null, QualityLot::getExecutionId, executionId)
                .orderByAsc(QualityLot::getLotId));
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
        for (QualityLot lot : lots) {
            lot.setSuperseded(superseded.contains(lot.getLotId()));
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
        if (next.compareTo(lot.getLotQuantity()) > 0) {
            throw new BusinessException("入库/放行累计不能超过该批批量（" + lot.getLotQuantity().toPlainString()
                    + "，已入库 " + nz(lot.getStoredQuantity()).toPlainString() + "）");
        }
        lot.setStoredQuantity(next);
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
        lotMapper.updateById(lot);
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
            BigDecimal left = nz(lot.getFailQuantity()).subtract(nz(lot.getDisposedQuantity()));
            // 返工登记时数量已被预占，但只有报工完成且复检合格才算真正处置完成。
            // 将未完成返工动作加回完工门禁，防止 PROCESSING 状态绕过工单完工检查。
            List<com.jjx.quality.domain.entity.QualityNcr> lotNcrs = ncrMapper.selectList(
                    new LambdaQueryWrapper<com.jjx.quality.domain.entity.QualityNcr>()
                            .eq(com.jjx.quality.domain.entity.QualityNcr::getLotId, lot.getLotId()));
            if (!lotNcrs.isEmpty()) {
                List<Long> ncrIds = lotNcrs.stream().map(com.jjx.quality.domain.entity.QualityNcr::getNcrId).toList();
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
        summary.setEffectiveLotCount(effective);
        summary.setPendingCount(pending);
        summary.setQualifiedTotal(qualified);
        summary.setUndisposedFailQuantity(undisposed);
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

    /** 生成批号 QLyyMMdd0001（批量小，用 count+1 加占用校验，避免额外依赖） */
    private String generateLotNo() {
        String prefix = "QL" + LocalDate.now().format(LOT_NO_DATE);
        long base = nzLong(lotMapper.countByLotNoPrefix(prefix));
        for (long seq = base + 1; seq < base + 10000; seq++) {
            String candidate = prefix + String.format("%04d", seq);
            if (nzLong(lotMapper.countByLotNo(candidate)) == 0) {
                return candidate;
            }
        }
        throw new BusinessException("检验批号生成失败，请检查批号规则");
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long nzLong(Long value) {
        return value == null ? 0L : value;
    }
}
