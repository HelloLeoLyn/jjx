package com.jjx.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.biz.domain.entity.BizRequirement;
import com.jjx.biz.domain.query.BizRequirementQuery;
import com.jjx.biz.enums.RequirementStatusEnum;
import com.jjx.biz.mapper.BizRequirementMapper;
import com.jjx.biz.service.IBizRequirementService;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 业务需求单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizRequirementServiceImpl implements IBizRequirementService {

    private final BizRequirementMapper requirementMapper;
    private final RedisSequenceService redisSequenceService;

    @Override
    public IPage<BizRequirement> page(BizRequirementQuery query, long pageNum, long pageSize) {
        LambdaQueryWrapper<BizRequirement> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.isNotBlank(query.getRequirementNo()), BizRequirement::getRequirementNo, query.getRequirementNo());
            wrapper.eq(StringUtils.isNotBlank(query.getRequirementType()), BizRequirement::getRequirementType, query.getRequirementType());
            wrapper.eq(query.getRequirementStatus() != null, BizRequirement::getRequirementStatus, query.getRequirementStatus());
            wrapper.like(StringUtils.isNotBlank(query.getTitle()), BizRequirement::getTitle, query.getTitle());
            wrapper.eq(StringUtils.isNotBlank(query.getSource()), BizRequirement::getSource, query.getSource());
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
                requirement.setApplicantId(com.jjx.system.utils.SecurityUtils.getUserId());
                requirement.setApplicantName(com.jjx.system.utils.SecurityUtils.getRealName());
            } catch (Exception ignored) {
            }
        }
        if (requirement.getApplyTime() == null && RequirementStatusEnum.DRAFT.getValue().equals(requirement.getRequirementStatus()) == false) {
            requirement.setApplyTime(LocalDateTime.now());
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
        BizRequirement upd = new BizRequirement();
        upd.setRequirementId(requirementId);
        upd.setRequirementStatus(RequirementStatusEnum.REVIEWING.getValue());
        upd.setApplyTime(LocalDateTime.now());
        requirementMapper.updateById(upd);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long requirementId, boolean approved, String remark) {
        BizRequirement exist = requirementMapper.selectById(requirementId);
        if (exist == null) throw new BusinessException("需求单不存在");
        if (!RequirementStatusEnum.REVIEWING.getValue().equals(exist.getRequirementStatus())) {
            throw new BusinessException("仅评审中的需求单可审核");
        }
        BizRequirement upd = new BizRequirement();
        upd.setRequirementId(requirementId);
        upd.setRequirementStatus(approved
                ? RequirementStatusEnum.APPROVED.getValue()
                : RequirementStatusEnum.REJECTED.getValue());
        upd.setReviewerId(com.jjx.system.utils.SecurityUtils.getUserId());
        upd.setReviewerName(com.jjx.system.utils.SecurityUtils.getRealName());
        upd.setReviewTime(LocalDateTime.now());
        upd.setReviewRemark(remark);
        requirementMapper.updateById(upd);
    }
}
