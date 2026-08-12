package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.DispatchAssignDTO;
import com.jjx.production.domain.dto.DispatchQueryDTO;
import com.jjx.production.domain.entity.ProductionDispatchLog;
import com.jjx.production.domain.vo.DispatchVO;

import java.util.List;

/**
 * 生产派工 Service
 */
public interface DispatchService {

    /** 分页查询派工单 */
    PageResult<DispatchVO> page(DispatchQueryDTO query);

    /** 工单全部派工单（按工序顺序） */
    List<DispatchVO> listByOrder(Long orderId);

    /** 派工单详情 */
    DispatchVO getById(Long id);

    /** 派工流水 */
    List<ProductionDispatchLog> logs(Long dispatchId);

    /** 单工序指派/改派（dispatchId 存在=改派） */
    DispatchVO assign(DispatchAssignDTO dto, String operatorName, Long operatorId);

    /** 工单批量派工（整单未派工/已退回工序） */
    int batchAssign(DispatchAssignDTO dto, String operatorName, Long operatorId);

    /** 退回（原因必填） */
    void reject(Long dispatchId, String reason, String operatorName, Long operatorId);

    /** 开始（联动执行） */
    void start(Long dispatchId, String operatorName, Long operatorId);

    /** 完成（联动执行） */
    void complete(Long dispatchId, String operatorName, Long operatorId);

    /** 执行联动：按 executionId 回写派工单状态（执行模块调用） */
    void syncByExecution(Long executionId, int status);

    /** 工单级责任班组/负责人 */
    void updateOrderTeam(Long orderId, Long teamId, Long leaderId, String operatorName);
}
