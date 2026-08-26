package com.jjx.production.service;

import com.jjx.production.domain.vo.TaskCandidateVO;

import java.util.List;

/**
 * 任务可分配候选解析能力（P2 Task Flow）
 * <p>
 * 边界：
 * - ProductionTaskService 只依赖本接口，内部不得出现 role_key / 主任 / 组长 / 工人等层级判断
 * - 当前组织模型（sys_dept/sys_user/sys_role）缺少“人员直属管理关系”，
 *   默认实现的角色判断只是临时组织映射策略，不是 ProductionTask 领域规则
 * - P5 或组织模型完善时允许整体替换实现，不得影响 Task Flow 领域逻辑
 */
public interface ProductionTaskAssigneeResolver {

    /**
     * 当前执行人可分配的人员集合
     *
     * @param currentAssigneeId 当前 Task 执行人ID；null 表示未分配第一层（仅全局角色调用，返回全部正常用户）
     */
    List<TaskCandidateVO> listAssignableUsers(Long currentAssigneeId);

    /**
     * assign 服务端二次校验：targetUserId 是否在当前执行人可分配集合内
     */
    boolean isAssignableTo(Long currentAssigneeId, Long targetUserId);

    /**
     * 分配权限门：当前执行人的可分配集合内是否存在可选下属。
     * 规则：自己只作为树根展示且不可选，全部合法后代均可选。
     */
    boolean hasAssignableSubordinates(Long rootUserId);
}
