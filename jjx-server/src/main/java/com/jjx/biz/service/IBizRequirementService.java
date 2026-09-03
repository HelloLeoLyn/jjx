package com.jjx.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.biz.domain.entity.BizRequirement;
import com.jjx.biz.domain.entity.BizRequirementApproval;
import com.jjx.biz.domain.query.BizRequirementQuery;

import java.util.List;

public interface IBizRequirementService {

    IPage<BizRequirement> page(BizRequirementQuery query, long pageNum, long pageSize);

    BizRequirement getById(Long requirementId);

    Long create(BizRequirement requirement);

    void update(BizRequirement requirement);

    void remove(Long[] requirementIds);

    /** 提交评审：草稿 → 评审中(2)，会签轮次 +1，通知四部门会签 */
    void submit(Long requirementId);

    /** 四部门会签：任一不同意→驳回(6)；全部同意→自动通过(3)。返回最新需求单 */
    BizRequirement signApproval(Long requirementId, String approvalRole, boolean approved, String comment);

    /** 会签记录（全部轮次） */
    List<BizRequirementApproval> listApprovals(Long requirementId);

    /** 开始执行：已通过(3) → 执行中(4) */
    void startExecute(Long requirementId);

    /** 关闭：执行中(4) → 已关闭(5)，登记执行结果 */
    void closeRequirement(Long requirementId, String result);

    /** 变更升版：CHANGE 且关联产品时，复制产品当前 BOM/工艺路线为新版本（新版本 DRAFT 非当前，待工程编辑审批） */
    java.util.Map<String, Object> upgradeRelated(Long requirementId, String newVersion);
}
