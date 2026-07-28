package com.jjx.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 字典类型VO
 */
@Data
@Schema(description = "字典类型VO")
public class SysDictVO {

    @Schema(description = "字典ID", example = "1")
    private Long dictId;

    @Schema(description = "字典编码", example = "order_status")
    private String dictCode;

    @Schema(description = "字典名称", example = "订单状态")
    private String dictName;

    @Schema(description = "分组", example = "sales")
    private String dictGroup;

    @Schema(description = "备注", example = "订单状态字典")
    private String remark;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "是否启用", example = "1")
    private Integer isActive;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "字典项列表")
    private List<SysDictItemVO> items;
}
