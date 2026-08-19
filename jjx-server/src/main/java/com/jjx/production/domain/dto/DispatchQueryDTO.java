package com.jjx.production.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 派工单分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DispatchQueryDTO extends PageQuery {

    /** 工单编号（模糊） */
    private String orderNo;

    /** 责任班组(部门ID) */
    private Long teamId;

    /** 状态（0-4），空=全部 */
    private Integer status;

    /** 工序关键字（模糊） */
    private String keyword;

    /** P1-B：数据范围。ALL=默认（超管/有权限全量，否则我指派的+我参与过）；MINE=我的当前任务（ACTIVE assignee=我）；空=ALL */
    private String scope;
}
