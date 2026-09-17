package com.jjx.quality.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.domain.entity.QualityNcrAction;
import com.jjx.quality.dto.QualityLotQueryDTO;
import com.jjx.quality.dto.QualityNcrDisposeDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 不良台账服务 —— dev-20260917-003
 * 台账是库存变动的唯一依据（合格入库之外的另一种来源）；处置动作在此登记，库存联动见 008。
 */
public interface QualityNcrService {

    /** 判定为不合格时建台账（不良数量、缺陷分类、原因、来源全部可追） */
    QualityNcr createFromLot(QualityLot lot, BigDecimal defectQuantity, BigDecimal crQuantity,
                             BigDecimal maQuantity, BigDecimal miQuantity,
                             String defectReason, String inspector);

    QualityNcr getNcr(Long ncrId);

    IPage<QualityNcr> pageNcrs(QualityLotQueryDTO query);

    /** 按工单/工序反查不良（工单详情用） */
    List<QualityNcr> listByOrder(Long orderId, Long executionId);

    List<QualityNcr> listByLot(Long lotId);

    List<QualityNcrAction> listActions(Long ncrId);

    /**
     * 登记处置（返工/让步接收/报废）
     * 校验：数量 ≤ 待处置；让步接收必须已获客户确认；
     * 联动：NCR 已处置数量 + 检验批处置数量；处置完则台账结案（CLOSED）
     */
    QualityNcrAction dispose(Long ncrId, QualityNcrDisposeDTO dto);

    /** 处置执行完成（返工完工再检合格/报废已扣库存/让步已转良品后由 008 调用） */
    QualityNcrAction completeAction(Long actionId, String resultRemark, Long reworkExecutionId);
}
