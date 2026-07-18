package com.jjx.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典项VO
 */
@Data
@Schema(description = "字典项VO")
public class SysDictItemVO {

    @Schema(description = "字典项ID", example = "1")
    private Long itemId;

    @Schema(description = "字典编码", example = "order_status")
    private String dictCode;

    @Schema(description = "字典键(实际存储值)", example = "draft")
    private String itemKey;

    @Schema(description = "字典值(显示文本)", example = "草稿")
    private String itemValue;

    @Schema(description = "标签(扩展)", example = "灰色")
    private String label;

    @Schema(description = "备注", example = "订单初始状态")
    private String remark;

    @Schema(description = "排序值", example = "0")
    private Integer sortOrder;

    @Schema(description = "是否启用", example = "1")
    private Integer isActive;

    @Schema(description = "扩展数据(JSON)")
    private String extData;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
