package com.jjx.hr.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 由员工档案生成的系统账号信息。 */
@Data
public class HrAccountVO {

    private Long userId;

    private String userName;

    private String nickName;

    private Long deptId;

    private String deptName;

    /** 未同步项提示（手机号/邮箱已被占用等） */
    private List<String> warnings = new ArrayList<>();

    /** 是否已绑定角色 */
    private Boolean roleBound;
}
