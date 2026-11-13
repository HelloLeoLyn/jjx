package com.jjx.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.domain.dto.BomChangeRequestDTO;
import com.jjx.product.domain.entity.BomChangeRequest;
import com.jjx.product.domain.entity.BomChangeRequestItem;
import com.jjx.product.domain.vo.BomCheckIssueVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.BomChangeRequestItemMapper;
import com.jjx.product.mapper.BomChangeRequestMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * BOM 变更单（ECN-lite，dev-20261112-009）。
 *
 * <p>一张单打包多层 BOM：提交时逐张调用 IEngineeringBomService.checkBomForSubmit（019 单张校验器），
 * 任一不通过整单不提交；批准后按生效日期统一生效，旧版本自动失效（expiry_date = 新生效日）。
 * 不修改 approve_status 语义；不写库存/领料/成本。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BomChangeRequestService {

    private static final String DRAFT = "DRAFT";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String EFFECTIVE = "EFFECTIVE";
    private static final String CANCELLED = "CANCELLED";

    private final BomChangeRequestMapper changeMapper;
    private final BomChangeRequestItemMapper itemMapper;
    private final EngineeringBomMapper bomMapper;
    private final IEngineeringBomService bomService;

    /** 新建变更单（草稿），至少一张 BOM。 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(BomChangeRequestDTO dto) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("变更单至少包含一张 BOM");
        }
        BomChangeRequest change = new BomChangeRequest();
        change.setChangeNo("BCR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        change.setTitle(dto.getTitle() == null || dto.getTitle().isBlank() ? "BOM变更单" : dto.getTitle().trim());
        change.setEffectiveDate(dto.getEffectiveDate());
        change.setStatus(DRAFT);
        change.setCreatedBy(operator());
        change.setCreatedTime(LocalDateTime.now());
        change.setRemark(dto.getRemark());
        changeMapper.insert(change);
        for (BomChangeRequestDTO.Item row : dto.getItems()) {
            addItem(change.getChangeId(), row);
        }
        return change.getChangeId();
    }

    private void addItem(Long changeId, BomChangeRequestDTO.Item row) {
        if (row == null || row.getBomId() == null) {
            throw new BusinessException("变更明细缺少 BOM");
        }
        EngineeringBom bom = bomMapper.selectById(row.getBomId());
        if (bom == null) {
            throw new BusinessException("BOM[" + row.getBomId() + "]不存在");
        }
        BomChangeRequestItem item = new BomChangeRequestItem();
        item.setChangeId(changeId);
        item.setBomId(row.getBomId());
        item.setBomVersion(row.getBomVersion() == null ? bom.getBomVersion() : row.getBomVersion());
        item.setTargetEffectiveDate(row.getTargetEffectiveDate());
        item.setCreatedTime(LocalDateTime.now());
        itemMapper.insert(item);
    }

    /** 提交：逐张校验，全部通过才进入待审批；校验结果留快照。 */
    @Transactional(rollbackFor = Exception.class)
    public BomChangeRequest submit(Long changeId) {
        BomChangeRequest change = require(changeId);
        if (!DRAFT.equals(change.getStatus())) {
            throw new BusinessException("当前状态不可提交：" + change.getStatus());
        }
        List<BomChangeRequestItem> items = itemsOf(changeId);
        if (items.isEmpty()) {
            throw new BusinessException("变更单没有明细，不能提交");
        }
        StringBuilder problems = new StringBuilder();
        for (BomChangeRequestItem item : items) {
            List<BomCheckIssueVO> issues = bomService.checkBomForSubmit(item.getBomId());
            item.setValidateResult(issues == null || issues.isEmpty() ? "OK" : summarize(issues));
            itemMapper.updateById(item);
            if (issues != null && !issues.isEmpty()) {
                problems.append("BOM[").append(item.getBomId()).append("]：").append(summarize(issues)).append("\n");
            }
        }
        if (problems.length() > 0) {
            throw new BusinessException("变更单存在不通过的 BOM，整单不可提交：\n" + problems);
        }
        change.setStatus(PENDING);
        changeMapper.updateById(change);
        return change;
    }

    /** 审批通过（不改 BOM 状态，等待生效）。 */
    @Transactional(rollbackFor = Exception.class)
    public BomChangeRequest approve(Long changeId) {
        BomChangeRequest change = require(changeId);
        if (!PENDING.equals(change.getStatus())) {
            throw new BusinessException("当前状态不可审批：" + change.getStatus());
        }
        change.setStatus(APPROVED);
        change.setApprovedBy(operator());
        change.setApprovedTime(LocalDateTime.now());
        changeMapper.updateById(change);
        return change;
    }

    /** 生效：按日期判定；force=true 为管理员「立即生效」。整单一起生效，不允许部分。 */
    @Transactional(rollbackFor = Exception.class)
    public BomChangeRequest activate(Long changeId, boolean force) {
        BomChangeRequest change = require(changeId);
        if (!APPROVED.equals(change.getStatus())) {
            throw new BusinessException("只有已批准变更单可生效，当前：" + change.getStatus());
        }
        LocalDate today = LocalDate.now();
        LocalDate plan = change.getEffectiveDate() == null ? today : change.getEffectiveDate();
        if (plan.isAfter(today) && !force) {
            throw new BusinessException("未到生效日期（" + plan + "），如需提前请使用「立即生效」");
        }
        LocalDate effective = force ? today : plan;
        List<BomChangeRequestItem> items = itemsOf(changeId);
        for (BomChangeRequestItem item : items) {
            EngineeringBom bom = bomMapper.selectById(item.getBomId());
            if (bom == null) {
                throw new BusinessException("BOM[" + item.getBomId() + "]不存在，整单不生效");
            }
            if (!ProductEnums.BomStatus.APPROVED.getValue().equals(bom.getApproveStatus())) {
                throw new BusinessException("BOM[" + bom.getBomCode() + "]未处于已批准状态，整单不生效");
            }
            LocalDate rowEffective = item.getTargetEffectiveDate() != null ? item.getTargetEffectiveDate() : effective;
            retireOtherCurrent(bom, rowEffective);
            bom.setEffectiveDate(java.sql.Date.valueOf(rowEffective));
            bom.setExpiryDate(null);
            bom.setIsCurrent(true);
            bomMapper.updateById(bom);
        }
        change.setStatus(EFFECTIVE);
        change.setActivatedBy(operator());
        change.setActivatedTime(LocalDateTime.now());
        changeMapper.updateById(change);
        return change;
    }

    /** 旧当前版本失效：valid_to = 新版本生效日，并取消 is_current；同产品同产出范围内。 */
    private void retireOtherCurrent(EngineeringBom target, LocalDate newEffective) {
        LambdaQueryWrapper<EngineeringBom> wrapper = new LambdaQueryWrapper<EngineeringBom>()
                .ne(EngineeringBom::getBomId, target.getBomId())
                .eq(EngineeringBom::getIsCurrent, true)
                .eq(EngineeringBom::getProductId, target.getProductId())
                .eq(EngineeringBom::getOutputType, target.getOutputType() == null ? "PRODUCT" : target.getOutputType());
        for (EngineeringBom old : bomMapper.selectList(wrapper)) {
            old.setExpiryDate(java.sql.Date.valueOf(newEffective));
            old.setIsCurrent(false);
            bomMapper.updateById(old);
        }
    }

    /** 取消：不改变任何 BOM。 */
    @Transactional(rollbackFor = Exception.class)
    public BomChangeRequest cancel(Long changeId) {
        BomChangeRequest change = require(changeId);
        if (EFFECTIVE.equals(change.getStatus())) {
            throw new BusinessException("已生效变更单不可取消");
        }
        change.setStatus(CANCELLED);
        changeMapper.updateById(change);
        return change;
    }

    public BomChangeRequest detail(Long changeId) {
        return require(changeId);
    }

    public List<BomChangeRequestItem> itemsOf(Long changeId) {
        return itemMapper.selectList(new LambdaQueryWrapper<BomChangeRequestItem>()
                .eq(BomChangeRequestItem::getChangeId, changeId).orderByAsc(BomChangeRequestItem::getItemId));
    }

    private BomChangeRequest require(Long changeId) {
        BomChangeRequest change = changeId == null ? null : changeMapper.selectById(changeId);
        if (change == null) {
            throw new BusinessException("BOM变更单不存在：" + changeId);
        }
        return change;
    }

    private static String summarize(List<BomCheckIssueVO> issues) {
        List<String> parts = new ArrayList<>();
        for (BomCheckIssueVO issue : issues) {
            parts.add((issue.getField() == null ? "校验" : issue.getField())
                    + (issue.getMaterialCode() == null ? "" : "[" + issue.getMaterialCode() + "]")
                    + (issue.getMessage() == null ? "" : "：" + issue.getMessage()));
        }
        return String.join("；", parts);
    }

    private static String operator() {
        String name = SecurityUtils.getRealName();
        if (name == null || name.isBlank()) {
            name = SecurityUtils.getUsername();
        }
        return name == null ? "" : name;
    }
}
