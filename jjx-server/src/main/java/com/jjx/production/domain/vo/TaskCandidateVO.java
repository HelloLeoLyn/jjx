package com.jjx.production.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务可分配候选人员（候选责任树节点）
 * <p>
 * 由 ProductionTaskAssigneeResolver 产出：
 * - 树根 = 分配根用户（首次分配=当前生产管理者本人；已分配 Task=当前 assignee）
 * - 节点 = 根用户负责部门的全部后代部门人员（部门树组织，任意层级可选）
 * - 角色只作资格展示（roleKey/roleName），不参与层级推断
 */
@Data
@Schema(description = "任务可分配候选人员（责任树节点）")
public class TaskCandidateVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录名")
    private String userName;

    @Schema(description = "姓名")
    private String nickName;

    @Schema(description = "部门ID")
    private Long deptId;

    @Schema(description = "部门名称")
    private String deptName;

    @Schema(description = "主生产角色标识（dispatch_mgr/dispatch_leader/worker；仅展示）")
    private String roleKey;

    @Schema(description = "主生产角色名称")
    private String roleName;

    @Schema(description = "是否分配根节点（当前分配人，仅作为责任树入口，不可选）")
    private Boolean root;

    @Schema(description = "是否可作为本次分配对象（根节点=false，合法后代=true）")
    private Boolean selectable;

    /** 内部组织字段（不序列化）：部门层级，供 Resolver 组装责任树 */
    @JsonIgnore
    private Long parentDeptId;

    @JsonIgnore
    private Long deptLeaderId;

    @Schema(description = "下属责任树（空=叶子）")
    private List<TaskCandidateVO> children = new ArrayList<>();
}
