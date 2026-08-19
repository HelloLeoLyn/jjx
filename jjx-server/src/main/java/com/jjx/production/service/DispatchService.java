package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.DispatchAssignDTO;
import com.jjx.production.domain.dto.DispatchAssignV1DTO;
import com.jjx.production.domain.dto.DispatchQueryDTO;
import com.jjx.production.domain.entity.ProductionDispatchLog;
import com.jjx.production.domain.vo.DispatchNodeComparisonVO;
import com.jjx.production.domain.vo.DispatchNodeVO;
import com.jjx.production.domain.vo.DispatchVO;

import java.util.List;
import com.jjx.system.domain.vo.DeptVO;
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
     * 责任班组可选执行人（该部门及全部下级部门成员；非超管限定在其管辖范围内）
     */
    List<SysUserVO> teamPersons(Long teamId);

    /** 当前用户可派工？（超管/生产负责人/被派工过） */
    boolean canAssign(Long userId);

    /** 执行人候选：自己 + 手下（负责部门+下级部门成员） */
    List<SysUserVO> myPersons();

    /**
     * 当前用户管辖部门树（负责部门 + 全部下级，超管全量）——责任班组可选范围
     */
    List<DeptVO> myDeptTree();

    /** 工单全部派工单（按工序顺序） */
    List<DispatchVO> listByOrder(Long orderId);

    /** 派工单详情 */
    DispatchVO getById(Long id);

    /** 派工流水 */
    List<ProductionDispatchLog> logs(Long dispatchId);

    /** 单工序指派/改派（Legacy compatibility adapter. Do not use from Dispatch V1 frontend.） */
    DispatchVO assign(DispatchAssignDTO dto, String operatorName, Long operatorId);

    /** 初始派工（P1-D 正式 V1 API，无 level/transferFrom） */
    DispatchVO assignV1(DispatchAssignV1DTO dto, String operatorName, Long operatorId);

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

    // ==================== P1-B 只读：Node-first responsibility chain ====================

    /** 责任链历史（Node-first；无 Node → legacy fallback 兼容 DTO） */
    List<DispatchNodeVO> nodes(Long dispatchId);

    /** 当前 ACTIVE 责任节点（Node-first；无 Node → legacy 末位 operator） */
    DispatchNodeVO currentNode(Long dispatchId);

    /** Node vs legacy 一致性诊断（P1-E cutover 工具，非业务 API） */
    DispatchNodeComparisonVO compareNodeAndLegacy(Long dispatchId);

    // ==================== P1-C 动作（Node 化） ====================

    /** DELEGATE：当前 ACTIVE 责任人向下派工 */
    DispatchVO delegate(Long dispatchId, Long targetUserId, String remark, String operatorName, Long operatorId);

    /** REASSIGN：当前责任层同级换人（历史不可覆盖） */
    DispatchVO reassign(Long dispatchId, Long targetUserId, String reason, String operatorName, Long operatorId);

    /** RETURN：当前 ACTIVE 退回上级责任层（创建新的上级责任实例，不激活旧 parent） */
    DispatchVO returnTask(Long dispatchId, String reason, String operatorName, Long operatorId);
}
