package com.jjx.sales.dto.save;

import lombok.Data;

/**
 * 打样工序更新DTO（8-03：材料JSON改body传输，避免长URL超限）
 */
@Data
public class SampleProcessDTO {
    /** 工序名称 */
    private String process;
    /** 材料JSON（[{name,spec,qty,unit,materialId,materialCode}]） */
    private String materials;
    /** 工艺说明 */
    private String processNote;
    /** 耗时（分钟） */
    private Integer durationMinutes;
}
