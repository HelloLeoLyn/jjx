package com.jjx.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.mapper.ReviewFlowMapper;
import com.jjx.system.service.ReviewFlowService;
import com.jjx.system.utils.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewFlowServiceImpl extends ServiceImpl<ReviewFlowMapper, ReviewFlow>
        implements ReviewFlowService {
    private static final String ACTION_SUBMIT = "SUBMIT";

    public ReviewFlowServiceImpl(ReviewFlowMapper reviewFlowMapper) {
        this.baseMapper = reviewFlowMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewFlow record(String bizType, Long bizId, String action, String actionName,
                             Object fromStatus, Object toStatus, String comment, String attachmentIds) {
        ReviewFlow latest = baseMapper.selectOne(Wrappers.<ReviewFlow>lambdaQuery()
                .eq(ReviewFlow::getBizType, bizType)
                .eq(ReviewFlow::getBizId, bizId)
                .orderByDesc(ReviewFlow::getRoundNo)
                .orderByDesc(ReviewFlow::getFlowId)
                .last("LIMIT 1"));

        int roundNo = latest == null ? 1 : latest.getRoundNo();
        if (ACTION_SUBMIT.equalsIgnoreCase(action) && latest != null) {
            roundNo = latest.getRoundNo() + 1;
        } else if (!ACTION_SUBMIT.equalsIgnoreCase(action)) {
            ReviewFlow latestSubmit = baseMapper.selectOne(Wrappers.<ReviewFlow>lambdaQuery()
                    .eq(ReviewFlow::getBizType, bizType)
                    .eq(ReviewFlow::getBizId, bizId)
                    .eq(ReviewFlow::getActionCode, ACTION_SUBMIT)
                    .orderByDesc(ReviewFlow::getRoundNo)
                    .orderByDesc(ReviewFlow::getFlowId)
                    .last("LIMIT 1"));
            if (latestSubmit != null) {
                roundNo = latestSubmit.getRoundNo();
            }
        }

        ReviewFlow flow = new ReviewFlow();
        flow.setBizType(bizType);
        flow.setBizId(bizId);
        flow.setRoundNo(roundNo);
        flow.setActionCode(action);
        flow.setActionName(actionName);
        flow.setFromStatus(statusText(fromStatus));
        flow.setToStatus(statusText(toStatus));
        flow.setOperatorId(currentUserId());
        flow.setOperatorName(currentUsername());
        flow.setCreateBy(currentUsername());
        flow.setComment(comment);
        flow.setAttachmentIds(attachmentIds);
        baseMapper.insert(flow);
        return flow;
    }

    @Override
    public List<ReviewFlow> listByBiz(String bizType, Long bizId) {
        return baseMapper.selectList(Wrappers.<ReviewFlow>lambdaQuery()
                .eq(ReviewFlow::getBizType, bizType)
                .eq(ReviewFlow::getBizId, bizId)
                .orderByAsc(ReviewFlow::getRoundNo)
                .orderByAsc(ReviewFlow::getFlowId));
    }

    private String statusText(Object status) {
        return status == null ? null : String.valueOf(status);
    }

    protected Long currentUserId() {
        return SecurityUtils.getUserId();
    }

    protected String currentUsername() {
        return SecurityUtils.getUsername();
    }
}
