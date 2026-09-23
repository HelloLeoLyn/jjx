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

    /**
     * 同步台账（再次提交/更正场景）：同一检验批已有未结台账 → 覆盖不良数量与分类；否则新建。
     * 用于来料重复提交同一行、复检更正等，避免同一批不良被重复记账。
     */
    QualityNcr syncFromLot(QualityLot lot, BigDecimal defectQuantity, BigDecimal crQuantity,
                           BigDecimal maQuantity, BigDecimal miQuantity,
                           String defectReason, String inspector);


    QualityNcr getNcr(Long ncrId);

    IPage<QualityNcr> pageNcrs(QualityLotQueryDTO query);

    /** 按工单/工序反查不良（工单详情用） */
    List<QualityNcr> listByOrder(Long orderId, Long executionId);

    List<QualityNcr> listByLot(Long lotId);

    List<QualityNcrAction> listActions(Long ncrId);

    /**
     * 复检换代：被取代批上未完成的不良单（PENDING/DISPOSING）随批作废（VOID），连同其未完成处置单。
     * dev-20260923-022：防"失效批还能处置" → 避免把已经不存在的货加进良品库存。
     *
     * @return 作废的不良单数量
     */
    int voidOpenDispositionsBySupersededLot(Long lotId, String reason);

    /**
     * 登记处置（返工/让步接收/报废）
     * 校验：数量 ≤ 待处置；让步接收必须已获客户确认；
     * 联动：NCR 已处置数量 + 检验批处置数量；处置完则台账结案（CLOSED）
     */
    QualityNcrAction dispose(Long ncrId, QualityNcrDisposeDTO dto);

    /** IQC 只允许从隔离处置入口同步 NCR，避免两个台账各自执行一次库存动作。 */
    QualityNcrAction syncIqcDisposition(Long lotId, String quarantineAction, BigDecimal quantity,
                                        String operatorName, String remark);

    /**
     * 撤销已生效(DONE)的处置（dev-20260923-022 二期）：受控动作 —— 需权限点 quality:ncr:revoke + 必填原因 + 留痕。
     * 本期支持 SCRAP（报废无库存影响，可安全逆）；让步接收已转良品库存 / 返工已建执行与复检批，需反向处理（下一步）。
     * 副作用：处置单→VOID；台账已处置量回落、状态回退；检验批已处置量回落（判定上界随之上抬）。
     */
    QualityNcrAction revokeAction(Long actionId, String reason, String operatorName);

    /** 处置执行完成（返工完工再检合格/报废已扣库存/让步已转良品后由 008 调用） */
    QualityNcrAction completeAction(Long actionId, String resultRemark, Long reworkExecutionId);
}
