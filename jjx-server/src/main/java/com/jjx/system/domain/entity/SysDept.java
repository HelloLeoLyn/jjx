package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

/**
 * 部门实体
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("sys_dept")
@JsonIgnoreProperties
public class SysDept extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门ID */
    @TableId(value = "dept_id")
    private Long id;

    /** 父部门ID */
    private Long parentId;

    /** 祖级列表 */
    @TableField(exist = false)
    private String ancestors;

    /** 部门名称 */
    private String deptName;

    /** 显示顺序 */
    private Integer orderNum;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 部门状态（0正常 1停用） */
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 父部门名称 */
    @TableField(exist = false)
    private String parentName;

    /** 子部门 */
    @TableField(exist = false)
    private List<SysDept> children;

    /** 备注 */
    @TableField(exist = false)
    private String remark;



}
