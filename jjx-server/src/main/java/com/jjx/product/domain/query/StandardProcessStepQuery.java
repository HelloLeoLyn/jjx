package com.jjx.product.domain.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标准工序查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StandardProcessStepQuery extends PageQuery {

    /** 工序编码 */
    private String stepCode;

    /** 工序名称 */
    private String stepName;

    /** 工序类型：general通用/special专用 */
    private String stepType;

    /** 工序类别：printing印刷/cutting冲切/laminating贴合/testing测试/assembly装配/packing包装 */
    private String stepCategory;

    /** 是否启用 */
    private Boolean enabled;

    /** 设备类型 */
    private String equipmentType;

    /** 技能等级要求 */
    private String skillLevel;

    /** 关键词搜索（编码、名称、类别） */
    private String keyword;

    /**
     * 开始日期
     */
    private String startDate;

    /**
     * 结束日期
     */
    private String endDate;

    /** 排序字段 */
    private String orderBy = "step_code";

    /** 排序方向：asc/desc */
    private String orderDirection = "asc";
}
