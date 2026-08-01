package com.jjx.engineering.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.engineering.domain.entity.Bom;

public interface IBomService extends IService<Bom> {

    /**
     * BOM列表（含产品名）
     */
    Object listPage(Object query);

    /**
     * 提交审核：DRAFT(1) → PENDING(2)
     */
    void submitApprove(Long bomId);

    /**
     * 审核通过：PENDING(2) → APPROVED(3)
     */
    void approve(Long bomId, String remark);

    /**
     * 审核驳回：PENDING(2) → REJECTED(4)
     */
    void reject(Long bomId, String remark);
}
