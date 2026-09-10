package com.jjx.hr.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 员工档案分页查询条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HrEmployeeQueryDTO extends PageQuery {

    /** 关键字：姓名 / 工号 / 手机号 */
    private String keyword;

    private Long deptId;

    /** 岗位（字典 hr_position 的 item_key，精确匹配） */
    private String position;

    /** 在职状态 1试用 2正式 3停薪留职 9离职 */
    private Integer employmentStatus;

    /** 是否只查已关联系统账号的员工 */
    private Boolean onlyLinked;
}
