package com.jjx.production.service.impl;

import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.mapper.ProductionTaskMapper;
import com.jjx.production.service.ProductionTaskAssigneeResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认候选解析实现（候选责任树；组织关系 = sys_dept 部门树 + leader_user_id 负责人关联）
 * <p>
 * 核心原则（P4.5 最终规则）：
 * - 每个层级分配行为同构：树根 = 当前分配人（不可选），全部合法后代均可选
 * - 选择直属负责人形成逐级责任链；选择任意后代形成跨级直派
 * - 已分配 Task：正常分配必须由 Task.assignee 执行（身份门在 assign 内校验）
 * - 没有下属 → 没有分配动作（canAssign=false；assign 内同样校验）
 * <p>
 * 树构造：
 * - 根用户负责部门为根部门，递归全部后代部门
 * - 每层节点 = 部门负责人（leader_user_id）；叶子部门 = 部门内其余用户
 * - 角色（roleKey/roleName）只作资格展示，不参与层级推断
 * <p>
 * 能力缺口：负责人关联依赖 migration 04 的 leader_user_id 回填；
 * 若组织模型未来引入人员直属管理关系，可整体替换本实现而不影响 Task Flow。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProductionTaskAssigneeResolver implements ProductionTaskAssigneeResolver {

    private final ProductionTaskMapper productionTaskMapper;

    @Override
    public List<TaskCandidateVO> listAssignableUsers(Long rootUserId) {
        if (rootUserId == null) {
            return new ArrayList<>();
        }
        List<TaskCandidateVO> users = productionTaskMapper.selectAssigneeTreeUsers(rootUserId);
        return buildTree(rootUserId, users);
    }

    @Override
    public boolean isAssignableTo(Long rootUserId, Long targetUserId) {
        if (targetUserId == null) {
            return false;
        }
        return !targetUserId.equals(rootUserId)
                && containsSelectableUser(listAssignableUsers(rootUserId), targetUserId);
    }

    @Override
    public boolean hasAssignableSubordinates(Long rootUserId) {
        return countSelectableUsers(listAssignableUsers(rootUserId)) > 0;
    }

    private int countSelectableUsers(List<TaskCandidateVO> nodes) {
        int total = 0;
        for (TaskCandidateVO n : nodes) {
            total += (Boolean.TRUE.equals(n.getSelectable()) ? 1 : 0)
                    + countSelectableUsers(n.getChildren() == null ? List.of() : n.getChildren());
        }
        return total;
    }

    private boolean containsSelectableUser(List<TaskCandidateVO> nodes, Long userId) {
        for (TaskCandidateVO n : nodes) {
            if (userId.equals(n.getUserId()) && Boolean.TRUE.equals(n.getSelectable())) {
                return true;
            }
            if (containsSelectableUser(n.getChildren(), userId)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 树组装 ====================

    private List<TaskCandidateVO> buildTree(Long rootUserId, List<TaskCandidateVO> users) {
        if (users.isEmpty()) {
            return new ArrayList<>();
        }
        // 部门层级信息由 selectAssigneeTreeUsers 行内列注入 VO（parentDeptId/deptLeaderId，@JsonIgnore）
        Map<Long, Long> leaderByDept = new LinkedHashMap<>();
        Map<Long, Long> parentByDept = new LinkedHashMap<>();
        Map<Long, List<TaskCandidateVO>> usersByDept = new LinkedHashMap<>();
        for (TaskCandidateVO u : users) {
            u.setRoot(rootUserId.equals(u.getUserId()));
            u.setSelectable(!rootUserId.equals(u.getUserId()));
            if (u.getDeptId() != null) {
                usersByDept.computeIfAbsent(u.getDeptId(), k -> new ArrayList<>()).add(u);
                if (u.getParentDeptId() != null) {
                    parentByDept.putIfAbsent(u.getDeptId(), u.getParentDeptId());
                }
                if (u.getDeptLeaderId() != null) {
                    leaderByDept.putIfAbsent(u.getDeptId(), u.getDeptLeaderId());
                }
            }
        }
        TaskCandidateVO root = null;
        for (TaskCandidateVO u : users) {
            if (rootUserId.equals(u.getUserId())) {
                root = u;
                break;
            }
        }
        if (root == null || root.getDeptId() == null) {
            return new ArrayList<>();
        }
        root.setChildren(buildChildren(root.getDeptId(), rootUserId, usersByDept, leaderByDept, parentByDept));
        List<TaskCandidateVO> tree = new ArrayList<>();
        tree.add(root);
        return tree;
    }

    private List<TaskCandidateVO> buildChildren(Long deptId, Long rootUserId,
                                                Map<Long, List<TaskCandidateVO>> usersByDept,
                                                Map<Long, Long> leaderByDept,
                                                Map<Long, Long> parentByDept) {
        List<TaskCandidateVO> children = new ArrayList<>();
        // 1) 直接子部门：负责人作为节点，递归其下
        for (Map.Entry<Long, Long> e : parentByDept.entrySet()) {
            if (deptId.equals(e.getValue())) {
                Long subDeptId = e.getKey();
                Long leaderId = leaderByDept.get(subDeptId);
                List<TaskCandidateVO> deptUsers = usersByDept.getOrDefault(subDeptId, new ArrayList<>());
                TaskCandidateVO leaderNode = findUser(leaderId, deptUsers);
                if (leaderNode != null) {
                    leaderNode.setChildren(buildChildren(subDeptId, rootUserId, usersByDept, leaderByDept, parentByDept));
                    children.add(leaderNode);
                } else {
                    // 部门无负责人：该部门用户直接平铺（不额外建层）
                    for (TaskCandidateVO u : deptUsers) {
                        if (!rootUserId.equals(u.getUserId())) {
                            children.add(u);
                        }
                    }
                }
            }
        }
        // 2) 本部门其他用户（非负责人、非根）挂在本层末尾
        Long deptLeaderId = leaderByDept.get(deptId);
        for (TaskCandidateVO u : usersByDept.getOrDefault(deptId, new ArrayList<>())) {
            if (!rootUserId.equals(u.getUserId()) && !u.getUserId().equals(deptLeaderId)) {
                children.add(u);
            }
        }
        return children;
    }

    private TaskCandidateVO findUser(Long userId, List<TaskCandidateVO> users) {
        if (userId == null) {
            return null;
        }
        for (TaskCandidateVO u : users) {
            if (userId.equals(u.getUserId())) {
                return u;
            }
        }
        return null;
    }
}
