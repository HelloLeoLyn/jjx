package com.jjx.sales.dto.save;

import lombok.Data;

/**
 * 打样工序状态推进DTO（方案A：逐项开始/完成）
 */
@Data
public class SampleProcessItemStatusDTO {

    /**
     * 目标状态：1进行中 2完成（0待做一般不通过此接口）
     */
    private Integer status;
    /** 首次执行印刷工序确认的实体网版；不能覆盖既有执行事实。 */
    private Long actualPlateId;

    /**
     * 耗时（分钟），完成时可填
     */
    private Integer durationMinutes;

    /**
     * 工艺说明/描述，完成时可补充
     */
    private String processNote;

    /**
     * 材料JSON（[{name,spec,qty,unit,materialId,materialCode}]），完成时可补充
     */
    private String materials;
}
