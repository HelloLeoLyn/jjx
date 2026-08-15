package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.DispatchAssignDTO;
import com.jjx.production.domain.dto.DispatchQueryDTO;
import com.jjx.production.domain.entity.ProductionDispatchLog;
import com.jjx.production.domain.vo.DispatchVO;

import java.util.List;
import com.jjx.system.domain.vo.SysUserVO;

/**
 * 生产派工 Service
 */
public interface DispatchService {

    /** 分页查询派工单 */
    PageResult<DispatchVO> page(DispatchQueryDTO query);

    /**
     * 工单待派工序（未派工/已退回），批量派工弹窗计数用
     */
    List<DispatchVO> listPending(Long orderId);

    /**
     * 某人的手下（其负责部门 + 所有下级部门成员，排除自己）——转派候选
     */
    List<SysUserVO> underlings(Long userId);

    /**
     * 责任班组可选执行人（该班组 + 所有上级部门成员，向上递归）
     */
    List<SysUserVO> teamPersons(Long teamId);

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
