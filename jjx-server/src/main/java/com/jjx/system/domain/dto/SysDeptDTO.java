package com.jjx.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 系统部门DTO
 */
@Getter
@Setter
public class SysDeptDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 部门ID（修改时必填）
     */
    private Long id;

    /**
     * 父部门ID
     */
    @NotNull(message = "上级部门不能为空")
    private Long parentId;

    /**
     * 部门名称
     */
    @NotBlank(message = "部门名称不能为空")
    @Size(min = 2, max = 30, message = "部门名称长度必须在2-30位之间")
    private String deptName;

    /**
     * 显示顺序
     */
    @NotNull(message = "显示顺序不能为空")
    private Integer orderNum;

    /**
     * 负责人
     */
    private String leader;

    /**
     * 负责人用户ID（DEV-1106：部门负责人改为用户组件选择，落库关联用户）
     */
    private Long leaderUserId;

    /**
     * 联系电话
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 部门状态（0正常 1停用）
     */
    @NotBlank(message = "部门状态不能为空")
    private String status;
}
