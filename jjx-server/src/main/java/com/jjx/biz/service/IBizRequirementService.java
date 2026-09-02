package com.jjx.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.biz.domain.entity.BizRequirement;
import com.jjx.biz.domain.query.BizRequirementQuery;

public interface IBizRequirementService {

    IPage<BizRequirement> page(BizRequirementQuery query, long pageNum, long pageSize);

    BizRequirement getById(Long requirementId);

    Long create(BizRequirement requirement);

    void update(BizRequirement requirement);

    void remove(Long[] requirementIds);

    /** 提交评审：草稿 → 评审中 */
    void submit(Long requirementId);

    /** 审核：评审中 → 已通过(3)/已驳回(6) */
    void review(Long requirementId, boolean approved, String remark);
}
