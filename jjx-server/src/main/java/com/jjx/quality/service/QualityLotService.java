package com.jjx.quality.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityLotItem;
import com.jjx.quality.dto.QualityLotCreateDTO;
import com.jjx.quality.dto.QualityLotItemDTO;
import com.jjx.quality.dto.QualityLotQueryDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 检验批服务 —— dev-20260917-001
 * 统一承载 IQC/IPQC/FQC 的检验批：建批、分批查询、录入项、判定、入库累计（防超入）
 */
public interface QualityLotService {

    /** 建批（支持同一来源多次建批 = 分批；复检传 parentLotId + version） */
    QualityLot createLot(QualityLotCreateDTO dto);

    QualityLot getLot(Long lotId);

    /** 同一来源的所有批（分批/复检历史，按版本与创建时间排序） */
    List<QualityLot> listBySource(String sourceType, Long sourceId);

    /** 工单/工序下的所有批（成品侧看 已检/待检 用） */
    List<QualityLot> listByOrder(Long orderId, Long executionId);

    IPage<QualityLot> pageLots(QualityLotQueryDTO query);

    List<QualityLotItem> listItems(Long lotId);

    /** 覆盖式保存检验项（录入/补录） */
    void saveItems(Long lotId, List<QualityLotItemDTO> items);

    /**
     * 判定落数：写 已检/合格/不良/判定/检验员，并推进状态
     * 校验：已检 ≤ 批量；合格 + 不良 = 已检
     */
    QualityLot applyJudgement(Long lotId, BigDecimal inspectedQuantity, BigDecimal passQuantity,
                              BigDecimal failQuantity, String result, String inspector);

    /**
     * 入库/放行累计（防超入守卫）：stored + delta ≤ 批量，否则报错
     */
    QualityLot addStoredQuantity(Long lotId, BigDecimal delta);

    /** 不良处置累计（返工/让步/报废都会调用） */
    QualityLot addDisposedQuantity(Long lotId, BigDecimal delta);
}
