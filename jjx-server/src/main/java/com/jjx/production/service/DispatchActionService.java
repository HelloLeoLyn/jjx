package com.jjx.production.service;

import com.jjx.production.domain.vo.DispatchVO;

/**
 * 派工责任链动作服务（P1-C：Node 化写入 Source of Truth）
 * <p>
 * 四类动作：ASSIGN（初始派工）/ DELEGATE（向下派工）/ REASSIGN（同级改派）/ RETURN（退回上级责任层）
 * <p>
 * 核心原则：
 * - production_dispatch_node = 写 Source of Truth；operators 仅由 Node 生成的 legacy projection
 * - 责任历史不可覆盖：任何一次责任重新落到某个人 = 创建新 Node
 * - 同一 dispatch 最多一个 ACTIVE（DB 唯一约束 + 条件更新 + dispatch 行锁）
 * - legacy-only dispatch 首次写动作前自动 on-write adoption（LEGACY_ON_WRITE_ADOPTION）
 */
public interface DispatchActionService {

    /**
     * ASSIGN：创建某道 execution/dispatch 的第一个责任节点
     *
     * @param executionId  工序执行记录ID
     * @param orderId      工单ID
     * @param targetUserId 第 1 级责任人
     * @param equipmentId  设备ID（可空）
     * @param remark       备注（可空）
     */
    DispatchVO assign(Long executionId, Long orderId, Long targetUserId, Long equipmentId,
                      String remark, String operatorName, Long operatorId);

    /**
     * DELEGATE：当前 ACTIVE 责任人把任务向下交给新责任人
     *
     * @param dispatchId   派工单ID
     * @param targetUserId 新责任人（须在当前责任人可派范围内）
     */
    DispatchVO delegate(Long dispatchId, Long targetUserId, String remark,
                        String operatorName, Long operatorId);

    /**
     * REASSIGN：当前责任层更换责任人（同级换人，历史不可覆盖）
     *
     * @param dispatchId   派工单ID
     * @param targetUserId 新责任人（同层）
     */
    DispatchVO reassign(Long dispatchId, Long targetUserId, String reason,
                        String operatorName, Long operatorId);

    /**
     * RETURN：当前 ACTIVE 退回上级责任层
     * 严格模型：关闭当前 ACTIVE（RETURNED）→ 创建新的上级责任实例（assignee=原上级 assignee，parent=原上级.parent）
     * 禁止重新激活旧 parent Node；禁止 N4.parent = 被退回节点。
     *
     * @param dispatchId 派工单ID
     * @param reason     退回原因
     */
    DispatchVO returnTask(Long dispatchId, String reason,
                          String operatorName, Long operatorId);
}
