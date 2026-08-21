package com.jjx.production.service;

import com.jjx.production.domain.vo.WorkReportVO;

import java.util.List;

/**
 * 生产报工读取服务（P2-B：只读能力）
 * 不实现 submit/cancel/update（属 P2-C）。
 */
public interface WorkReportReadService {

    /** 按报工ID查询 */
    WorkReportVO getById(Long reportId);

    /** 按 executionId 查询全部报工（默认 reportTime DESC / createTime DESC / reportId DESC，用于 Execution 报工历史 Drawer） */
    List<WorkReportVO> listByExecutionId(Long executionId);

    /** 按 executionId 查询已提交报工（后续 projection 汇总用） */
    List<WorkReportVO> listSubmittedByExecutionId(Long executionId);

}
