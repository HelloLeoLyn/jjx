package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityInspectionQueryDTO;
import com.jjx.production.domain.dto.QualityInspectionUpdateDTO;
import com.jjx.production.domain.vo.QualityInspectionVO;

import java.util.List;

public interface QualityInspectionService {
    PageResult<QualityInspectionVO> page(QualityInspectionQueryDTO query);
    QualityInspectionVO getById(Long id);
    Long create(QualityInspectionCreateDTO dto);
    void update(QualityInspectionUpdateDTO dto);
    void delete(Long id);
    Object getStatistics();

    /** 导出质检报告PDF（给客户看） */
    byte[] exportPdf(Long id);

    /** 导出质检报告Excel（给客户看） */
    byte[] exportExcel(Long id);

    // ============ P3-B 读取能力（供 P3-C FQC/IPQC 联动） ============

    /** 按订单查质检列表 */
    List<QualityInspectionVO> listByOrderId(Long orderId);

    /** 按工序执行查质检列表 */
    List<QualityInspectionVO> listByExecutionId(Long executionId);

    /** 按报工查质检列表 */
    List<QualityInspectionVO> listByWorkReportId(Long workReportId);

    /** 某 execution 的 FQC 历史（按创建时间倒序） */
    List<QualityInspectionVO> listFqcHistory(Long executionId);

    /** 某 execution 是否存在 PENDING FQC */
    boolean hasPendingFqc(Long executionId);

    /** 某 execution 是否存在 PASS FQC */
    boolean hasPassFqc(Long executionId);

    /**
     * P3-B 关联一致性校验：workReportId 非空时校验
     *   workReport.executionId == quality.executionId
     *   workReport.orderId == quality.orderId
     * @return true=一致；false=不一致或数据缺失
     */
    boolean checkWorkReportLink(Long workReportId, Long executionId, Long orderId);
}
