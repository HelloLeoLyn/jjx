package com.jjx.hr.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工档案（人事主数据）。
 *
 * <p>2026-09-10：人事管理模块 P0。设计与账号解耦——{@code userId} 可空，一对一可选关联 sys_user；
 * 身份证号以 AES 加密存储（见 {@link com.jjx.hr.support.IdCardCipher}），展示层按权限脱敏。</p>
 */
@Data
@TableName("hr_employee")
public class HrEmployee {

    @TableId(type = IdType.AUTO)
    private Long empId;

    /** 工号（唯一） */
    private String empNo;

    private String name;

    /** 性别 1男 2女 */
    private Integer sex;

    /** 部门ID → sys_dept */
    private Long deptId;

    /** 岗位（字典 hr_position 的 item_key） */
    private String position;

    private String phone;

    private String email;

    /** 进厂日期 */
    private LocalDate hireDate;

    /** 离职日期 */
    private LocalDate leaveDate;

    /** 在职状态 1试用 2正式 3停薪留职 9离职 */
    private Integer employmentStatus;

    /** 身份证号（AES 加密存储） */
    private String idCardNo;

    /** 身份证地址（敏感） */
    private String idCardAddress;

    /** 现住址（敏感） */
    private String currentAddress;

    /** 学历（字典 hr_education） */
    private String education;

    private String major;

    /** 个人履历 */
    private String resume;

    /** 关联系统账号 sys_user.user_id（可空） */
    private Long userId;

    private String remark;

    @TableLogic(value = "0", delval = "1")
    private String delFlag;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
