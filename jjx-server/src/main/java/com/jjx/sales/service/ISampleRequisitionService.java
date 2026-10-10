package com.jjx.sales.service;

import com.jjx.sales.domain.dto.SampleDefectRecordDTO;
import com.jjx.sales.domain.entity.SalesSampleDefectRecord;
import com.jjx.sales.domain.entity.SalesSampleRequisitionSign;

import java.util.List;

/**
 * 样品需求单会签 + 打样不良记录服务（dev-20261010-028）
 */
public interface ISampleRequisitionService {

    /** 某样品单的全部会签记录 */
    List<SalesSampleRequisitionSign> listSigns(Long sampleOrderId);

    /**
     * 会签：按签字位落痕（同意/不同意+意见）。同 样品单×签字位×轮次 覆盖。
     *
     * @param roleCode SALES/APPROVE/DEPT
     */
    SalesSampleRequisitionSign sign(Long sampleOrderId, String roleCode, Boolean approved, String comment);

    /** 某样品单的全部不良记录 */
    List<SalesSampleDefectRecord> listDefects(Long sampleOrderId);

    /** 新增不良原因及改善记录 */
    SalesSampleDefectRecord addDefect(Long sampleOrderId, SampleDefectRecordDTO dto);

    /** 删除不良记录（逻辑删除） */
    void deleteDefect(Long id);
}
