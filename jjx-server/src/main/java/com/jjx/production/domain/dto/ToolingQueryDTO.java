package com.jjx.production.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工装模具分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolingQueryDTO extends PageQuery {

    /** 类型：SCREEN=网框 DIE=刀模，空=全部 */
    private String type;

    /** 关键字（编号/名称模糊） */
    private String keyword;

    /** 状态（0-4），空=全部 */
    private Integer status;
}
