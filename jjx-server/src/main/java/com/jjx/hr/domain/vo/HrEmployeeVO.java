package com.jjx.hr.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 员工档案展示 VO（含部门/岗位/账号名称，敏感字段按权限脱敏）。 */
@Data
public class HrEmployeeVO {

    private Long empId;

    private String empNo;

    private String name;

    private Integer sex;

    private Long deptId;

    private String deptName;

    /** 岗位（字典 hr_position 的 item_key） */
    private String position;

    private String phone;

    private String email;

    private LocalDate hireDate;

    private LocalDate leaveDate;

    private Integer employmentStatus;

    /** 身份证号：有 hr:employee:sensitive 权限时返回明文，否则脱敏 */
    private String idCardNo;

    /** 身份证地址：同上 */
    private String idCardAddress;

    /** 现住址：同上 */
    private String currentAddress;

    private String education;

    private String major;

    private String resume;

    private Long userId;

    /** 关联账号用户名 */
    private String userName;

    private String remark;

    /** 当前登录人是否可见敏感字段明文（前端据此显示提示/决定是否回传原值） */
    private Boolean sensitiveVisible;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
