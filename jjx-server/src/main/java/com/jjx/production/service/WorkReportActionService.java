package com.jjx.production.service;

import com.jjx.production.domain.dto.WorkReportCancelDTO;
import com.jjx.production.domain.dto.WorkReportSubmitDTO;
import com.jjx.production.domain.vo.WorkReportVO;

/**
 * 生产报工动作服务（P2-C）
 * 正式写动作只有 SUBMIT / CANCEL；禁止普通 UPDATE/DELETE。
 */
public interface WorkReportActionService {

    /** SUBMIT：创建一次不可覆盖的报工事实（内部解析 execution/reporter 锚点；成功后重算 execution projection） */
    WorkReportVO submit(WorkReportSubmitDTO dto, String operatorName, Long operatorId);

    /** CANCEL：SUBMITTED → CANCELLED（条件更新防并发；成功后重算 projection；已 CANCELLED 幂等） */
    WorkReportVO cancel(Long reportId, WorkReportCancelDTO dto, String operatorName, Long operatorId);
}
