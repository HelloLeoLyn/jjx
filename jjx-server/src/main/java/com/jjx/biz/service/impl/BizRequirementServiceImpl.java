package com.jjx.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.biz.domain.entity.BizRequirement;
import com.jjx.biz.domain.entity.BizRequirementApproval;
import com.jjx.biz.domain.query.BizRequirementQuery;
import com.jjx.biz.enums.RequirementStatusEnum;
import com.jjx.biz.mapper.BizRequirementApprovalMapper;
import com.jjx.biz.mapper.BizRequirementMapper;
import com.jjx.biz.service.IBizRequirementService;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.domain.vo.EngineeringRoutingVO;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.product.service.IEngineeringRoutingService;
import com.jjx.system.domain.entity.SysUserRole;
import com.jjx.system.mapper.SysUserRoleMapper;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务需求单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizRequirementServiceImpl implements IBizRequirementService {

    /** 会签四部门（QR-030）→ 可签系统角色 */
    private static final Map<String, List<Long>> APPROVAL_ROLE_IDS = new HashMap<>();

    static {
        APPROVAL_ROLE_IDS.put("ENGINEERING", List.of(17L));          // 工程部 → engineering:ops
        APPROVAL_ROLE_IDS.put("MAKING", List.of(28L));               // 制造部 → production:all
        APPROVAL_ROLE_IDS.put("PURCHASE", List.of(26L, 23L));        // 采购/仓库 → purchase:ops / inventory:ops
        APPROVAL_ROLE_IDS.put("QUALITY", List.of(29L));              // 品管部 → production:ops（暂无品管角色，生产中心代签）
    }

    private static final List<String> APPROVAL_ROLES = new ArrayList<>(APPROVAL_ROLE_IDS.keySet());

    private final BizRequirementMapper requirementMapper;
    private final BizRequirementApprovalMapper approvalMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final RedisSequenceService redisSequenceService;
    private final EventPublisher eventPublisher;
    private final IEngineeringBomService bomService;
    private final IEngineeringRoutingService routingService;

    @Override
    public IPage<BizRequirement> page(BizRequirementQuery query, long pageNum, long pageSize) {
        LambdaQueryWrapper<BizRequirement> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.isNotBlank(query.getRequirementNo()), BizRequirement::getRequirementNo, query.getRequirementNo());
            wrapper.eq(StringUtils.isNotBlank(query.getRequirementType()), BizRequirement::getRequirementType, query.getRequirementType());
            wrapper.eq(query.getRequirementStatus() != null, BizRequirement::getRequirementStatus, query.getRequirementStatus());
            wrapper.like(StringUtils.isNotBlank(query.getTitle()), BizRequirement::getTitle, query.getTitle());
            wrapper.eq(StringUtils.isNotBlank(query.getSource()), BizRequirement::getSource, query.getSource());
            wrapper.eq(StringUtils.isNotBlank(query.getChangeType()), BizRequirement::getChangeType, query.getChangeType());
            if (StringUtils.isNotBlank(query.getBizNo())) {
                wrapper.and(w -> w.like(BizRequirement::getBizNo, query.getBizNo())
                        .or().like(BizRequirement::getRequirementNo, query.getBizNo()));
            }
            if (query.getStartDate() != null) {
                wrapper.ge(BizRequirement::getApplyTime, query.getStartDate().atStartOfDay());
            }
            if (query.getEndDate() != null) {
                wrapper.lt(BizRequirement::getApplyTime, query.getEndDate().plusDays(1).atStartOfDay());
            }
        }
        wrapper.orderByDesc(BizRequirement::getRequirementId);
        return requirementMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public BizRequirement getById(Long requirementId) {
        BizRequirement req = requirementMapper.selectById(requirementId);
        if (req == null) {
            throw new BusinessException("需求单不存在");
        }
        return req;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(BizRequirement requirement) {
        if (requirement.getTitle() == null || requirement.getTitle().isBlank()) {
            throw new BusinessException("需求标题不能为空");
        }
        requirement.setRequirementNo(redisSequenceService.generateBusinessNumber("RQ", "业务需求单号"));
        if (requirement.getRequirementType() == null || requirement.getRequirementType().isBlank()) {
            requirement.setRequirementType("CHANGE");
        }
        if (requirement.getRequirementStatus() == null) {
            requirement.setRequirementStatus(RequirementStatusEnum.DRAFT.getValue());
        }
        if (requirement.getUrgency() == null || requirement.getUrgency().isBlank()) {
            requirement.setUrgency("normal");
        }
        if (requirement.getApplicantId() == null) {
            try {
                requirement.setApplicantId(SecurityUtils.getUserId());
                requirement.setApplicantName(SecurityUtils.getRealName());
            } catch (Exception ignored) {
            }
        }
        if (requirement.getApplyTime() == null && !RequirementStatusEnum.DRAFT.getValue().equals(requirement.getRequirementStatus())) {
            requirement.setApplyTime(LocalDateTime.now());
        }
        if (requirement.getCurrentRound() == null) {
            requirement.setCurrentRound(0);
        }
        requirementMapper.insert(requirement);
        return requirement.getRequirementId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(BizRequirement requirement) {
        BizRequirement exist = requirementMapper.selectById(requirement.getRequirementId());
        if (exist == null) {
            throw new BusinessException("需求单不存在");
        }
        // 只有草稿/已驳回可改
        Integer st = exist.getRequirementStatus();
        if (st != null && !Arrays.asList(
                RequirementStatusEnum.DRAFT.getValue(),
                RequirementStatusEnum.REJECTED.getValue()).contains(st)) {
            throw new BusinessException("当前状态不可编辑（仅草稿/已驳回可修改）");
        }
        // 驳回后编辑保存 → 回到草稿（可重新提交进入下一会签轮次）
        if (RequirementStatusEnum.REJECTED.getValue().equals(exist.getRequirementStatus())) {
            requirement.setRequirementStatus(RequirementStatusEnum.DRAFT.getValue());
        }
        requirementMapper.updateById(requirement);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long[] requirementIds) {
        for (Long id : requirementIds) {
            BizRequirement exist = requirementMapper.selectById(id);
            if (exist == null) continue;
            if (!RequirementStatusEnum.DRAFT.getValue().equals(exist.getRequirementStatus())) {
                throw new BusinessException("需求单[" + exist.getRequirementNo() + "]非草稿状态不可删除");
            }
        }
        requirementMapper.deleteBatchIds(Arrays.asList(requirementIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long requirementId) {
        BizRequirement exist = requirementMapper.selectById(requirementId);
        if (exist == null) throw new BusinessException("需求单不存在");
        if (!RequirementStatusEnum.DRAFT.getValue().equals(exist.getRequirementStatus())) {
            throw new BusinessException("仅草稿可提交评审");
        }
        int round = (exist.getCurrentRound() == null ? 0 : exist.getCurrentRound()) + 1;
        BizRequirement upd = new BizRequirement();
        upd.setRequirementId(requirementId);
        upd.setRequirementStatus(RequirementStatusEnum.REVIEWING.getValue());
        upd.setCurrentRound(round);
        upd.setApplyTime(LocalDateTime.now());
        requirementMapper.updateById(upd);
        // 通知四部门会签角色去会签
        fireEvent("biz.requirement.submitted", exist, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizRequirement signApproval(Long requirementId, String approvalRole, boolean approved, String comment) {
        BizRequirement exist = requirementMapper.selectById(requirementId);
        if (exist == null) throw new BusinessException("需求单不存在");
        if (!RequirementStatusEnum.REVIEWING.getValue().equals(exist.getRequirementStatus())) {
            throw new BusinessException("仅评审中的需求单可会签");
        }
        if (approvalRole == null || !APPROVAL_ROLES.contains(approvalRole)) {
            throw new BusinessException("未知会签部门: " + approvalRole);
        }
        // 部门权限校验：当前用户角色需命中该部门映射角色
        Long userId = SecurityUtils.getUserId();
        List<Long> myRoleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        boolean allowed = APPROVAL_ROLE_IDS.get(approvalRole).stream().anyMatch(myRoleIds::contains);
        if (!allowed) {
            throw new BusinessException("当前账号无权代表该部门会签");
        }
        int round = exist.getCurrentRound() == null ? 1 : exist.getCurrentRound();
        // 本轮该部门已签 → 覆盖改签；否则新增
        BizRequirementApproval existAp = approvalMapper.selectOne(new LambdaQueryWrapper<BizRequirementApproval>()
                .eq(BizRequirementApproval::getRequirementId, requirementId)
                .eq(BizRequirementApproval::getRoundNo, round)
                .eq(BizRequirementApproval::getApprovalRole, approvalRole));
        BizRequirementApproval ap = existAp != null ? existAp : new BizRequirementApproval();
        ap.setRequirementId(requirementId);
        ap.setRoundNo(round);
        ap.setApprovalRole(approvalRole);
        ap.setApprovalUserId(userId);
        ap.setApprovalUserName(currentUserName());
        ap.setApproveResult(approved ? 1 : 2);
        ap.setComment(comment);
        ap.setApproveTime(LocalDateTime.now());
        if (existAp != null) {
            approvalMapper.updateById(ap);
        } else {
            approvalMapper.insert(ap);
        }
        // 流转判定：任一部门不同意 → 驳回；四部门全部同意 → 自动通过
        if (!approved) {
            BizRequirement upd = new BizRequirement();
            upd.setRequirementId(requirementId);
            upd.setRequirementStatus(RequirementStatusEnum.REJECTED.getValue());
            upd.setReviewerId(userId);
            upd.setReviewerName(currentUserName());
            upd.setReviewTime(LocalDateTime.now());
            upd.setReviewRemark("会签驳回(" + approvalRole + "): " + (comment == null ? "" : comment.trim()));
            requirementMapper.updateById(upd);
            fireEvent("biz.requirement.rejected", exist, exist.getApplicantId(),
                    "会签部门[" + approvalRole + "]不同意：" + (comment == null ? "" : comment.trim()));
        } else {
            List<BizRequirementApproval> roundList = approvalMapper.selectList(new LambdaQueryWrapper<BizRequirementApproval>()
                    .eq(BizRequirementApproval::getRequirementId, requirementId)
                    .eq(BizRequirementApproval::getRoundNo, round));
            Set<String> agreedRoles = new HashSet<>();
            for (BizRequirementApproval a : roundList) {
                if (a.getApproveResult() != null && a.getApproveResult() == 1) {
                    agreedRoles.add(a.getApprovalRole());
                }
            }
            if (agreedRoles.containsAll(APPROVAL_ROLES)) {
                BizRequirement upd = new BizRequirement();
                upd.setRequirementId(requirementId);
                upd.setRequirementStatus(RequirementStatusEnum.APPROVED.getValue());
                upd.setReviewerId(userId);
                upd.setReviewerName(currentUserName());
                upd.setReviewTime(LocalDateTime.now());
                upd.setReviewRemark("四部门会签全部通过");
                requirementMapper.updateById(upd);
                fireEvent("biz.requirement.approved", exist, exist.getApplicantId(), null);
            }
        }
        return requirementMapper.selectById(requirementId);
    }

    @Override
    public List<BizRequirementApproval> listApprovals(Long requirementId) {
        return approvalMapper.selectList(new LambdaQueryWrapper<BizRequirementApproval>()
                .eq(BizRequirementApproval::getRequirementId, requirementId)
                .orderByDesc(BizRequirementApproval::getRoundNo)
                .orderByAsc(BizRequirementApproval::getApprovalRole));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startExecute(Long requirementId) {
        BizRequirement exist = requirementMapper.selectById(requirementId);
        if (exist == null) throw new BusinessException("需求单不存在");
        if (!RequirementStatusEnum.APPROVED.getValue().equals(exist.getRequirementStatus())) {
            throw new BusinessException("仅审核通过的需求单可开始执行");
        }
        BizRequirement upd = new BizRequirement();
        upd.setRequirementId(requirementId);
        upd.setRequirementStatus(RequirementStatusEnum.EXECUTING.getValue());
        upd.setExecuteBy(currentUserName());
        upd.setExecuteTime(LocalDateTime.now());
        requirementMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeRequirement(Long requirementId, String result) {
        BizRequirement exist = requirementMapper.selectById(requirementId);
        if (exist == null) throw new BusinessException("需求单不存在");
        if (!RequirementStatusEnum.EXECUTING.getValue().equals(exist.getRequirementStatus())) {
            throw new BusinessException("仅执行中的需求单可关闭");
        }
        BizRequirement upd = new BizRequirement();
        upd.setRequirementId(requirementId);
        upd.setRequirementStatus(RequirementStatusEnum.CLOSED.getValue());
        upd.setExecuteResult(result == null || result.isBlank() ? null : result.trim());
        upd.setCloseTime(LocalDateTime.now());
        requirementMapper.updateById(upd);
    }

    private String currentUserName() {
        try {
            String name = SecurityUtils.getRealName();
            if (name == null || name.isBlank()) {
                name = SecurityUtils.getUsername();
            }
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upgradeRelated(Long requirementId, String newVersion) {
        BizRequirement exist = requirementMapper.selectById(requirementId);
        if (exist == null) throw new BusinessException("需求单不存在");
        if (!"CHANGE".equals(exist.getRequirementType())) {
            throw new BusinessException("仅变更类型需求可执行升版");
        }
        Integer st = exist.getRequirementStatus();
        boolean canUpgrade = st != null && (RequirementStatusEnum.APPROVED.getValue().equals(st)
                || RequirementStatusEnum.EXECUTING.getValue().equals(st));
        if (!canUpgrade) {
            throw new BusinessException("仅已通过/执行中的变更需求可升版");
        }
        if (newVersion == null || newVersion.isBlank()) {
            throw new BusinessException("新版本号不能为空");
        }
        if (!"product".equals(exist.getBizType()) || exist.getBizId() == null) {
            throw new BusinessException("该变更未关联产品，无法自动升版（请在变更单上关联产品）");
        }
        Long productId = exist.getBizId();
        List<String> logs = new ArrayList<>();
        // BOM 升版（新版本 DRAFT + isCurrent=false，待工程编辑/审批/切换）
        try {
            EngineeringBom curBom = bomService.getDefaultBomByProductId(productId);
            if (curBom != null) {
                bomService.copyAsNewVersion(curBom.getBomId(), newVersion);
                logs.add("BOM【" + curBom.getBomCode() + "】已升版为 " + newVersion + "（新版本待编辑提交审批）");
            } else {
                logs.add("产品无当前生效 BOM，已跳过");
            }
        } catch (Exception e) {
            logs.add("BOM 升版失败：" + e.getMessage());
        }
        // 工艺路线升版
        try {
            EngineeringRoutingVO curRt = routingService.getCurrentByProductId(productId);
            if (curRt != null) {
                routingService.copyAsNewVersion(curRt.getRoutingId(), newVersion);
                logs.add("工艺路线【" + curRt.getRoutingCode() + "】已升版为 " + newVersion + "（新版本待编辑提交审批）");
            } else {
                logs.add("产品无当前生效工艺路线，已跳过");
            }
        } catch (Exception e) {
            logs.add("工艺路线升版失败：" + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requirementNo", exist.getRequirementNo());
        result.put("newVersion", newVersion);
        result.put("logs", logs);
        return result;
    }

    /**
     * 手动发布事件：通知由 sys_event_config 控制；receiverId 非空时直接发给该用户（如申请人）
     */
    private void fireEvent(String eventCode, BizRequirement req, Long receiverId, String remark) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("bizId", req.getRequirementId());
            payload.put("bizType", "biz_requirement");
            payload.put("requirementNo", req.getRequirementNo());
            payload.put("title", req.getTitle());
            if (receiverId != null) {
                payload.put("receiverId", receiverId);
            }
            if (remark != null) {
                payload.put("remark", remark);
            }
            try {
                payload.put("triggerUserId", SecurityUtils.getUserId());
            } catch (Exception ignored) {
            }
            eventPublisher.fire(eventCode, payload);
        } catch (Exception e) {
            log.error("需求单事件发布失败: event={}, reqId={}, err={}", eventCode, req.getRequirementId(), e.getMessage());
        }
    }
}
