package com.jjx.sales.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增打样不良原因及改善记录 DTO（dev-20261010-028）
 */
@Data
public class SampleDefectRecordDTO {

    /** 制样类别: PRINT印刷/PUNCH加工冲型 */
    @NotBlank(message = "制样类别不能为空")
    private String craftType;

    /** 不良原因 */
    @NotBlank(message = "不良原因不能为空")
    @Size(max = 500, message = "不良原因不能超过500字")
    private String defectReason;

    /** 改善措施 */
    @Size(max = 500, message = "改善措施不能超过500字")
    private String improvement;

    /** 记录日期 yyyy-MM-dd（可选，默认当天） */
    @Schema(description = "记录日期 yyyy-MM-dd")
    private String recordDate;

    /** 轮次（可选，默认样品单当前轮次） */
    private Integer roundNo;
}
