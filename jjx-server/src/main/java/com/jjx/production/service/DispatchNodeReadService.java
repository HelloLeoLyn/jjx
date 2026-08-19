package com.jjx.production.service;

import com.jjx.production.domain.vo.DispatchNodeVO;
import com.jjx.production.domain.vo.DispatchNodeComparisonVO;

import java.util.List;

/**
 * 派工责任链节点读取服务（P1-B：Node-first Read Model）
 * <p>
 * Source of Truth 规则（P1 定稿）：
 * - production_dispatch_node = 新模型 Source of Truth
 * - production_dispatch.operators = Legacy Projection / Legacy Fallback
 * <p>
 * Node-first + legacy fallback：
 * - dispatch 存在 Node → 所有责任链/当前责任人/参与判断必须用 Node，禁止再解析 operators 修正结果
 * - dispatch 完全不存在 Node → 允许读取 legacy operators（仅迁移过渡，until P1-E cutover）
 * <p>
 * 本服务只读 Node；写入（ASSIGN/DELEGATE/REASSIGN/RETURN）属 P1-C。
 */
public interface DispatchNodeReadService {

    /**
     * 某 dispatch 的责任链历史（Responsibility History）
     * 按 assignedAt/createTime/nodeId 稳定排序（不是单纯 parent 树），回答"责任按时间先后经过了谁"。
     * 存在 Node → Node 历史；无 Node → legacy fallback 转兼容 DTO（仅展示）。
     */
    List<DispatchNodeVO> getResponsibilityChain(Long dispatchId);

    /**
     * 当前 ACTIVE 责任节点（Current Responsibility）
     * 存在 Node → node_status='ACTIVE' 唯一节点；无 Node → legacy fallback 取最后有效 operator。
     * 返回 null = 无当前责任人（Case 4）。
     */
    DispatchNodeVO getCurrentActiveNode(Long dispatchId);

    /** 该 dispatch 是否已有 Node（数据源判定：true=Node-first，false=legacy fallback） */
    boolean hasNodes(Long dispatchId);

    /** 用户是否曾在任意 dispatch 的责任链中出现过（全局判断，Node-first + legacy fallback） */
    boolean hasUserParticipated(Long userId);

    /** 用户是否是某 dispatch 的当前 ACTIVE 责任人（Node-first；无 Node 时 legacy fallback 末位判断） */
    boolean isCurrentAssignee(Long dispatchId, Long userId);

    /**
     * Node vs legacy operators 一致性诊断（P1-E cutover 前检查工具，非业务 API）
     */
    DispatchNodeComparisonVO compareNodeAndLegacy(Long dispatchId);
}
