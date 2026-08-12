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
}
