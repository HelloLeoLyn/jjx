package com.jjx.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典类型DTO
 */
@Data
@Schema(description = "字典类型DTO")
public class SysDictDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "字典ID", example = "1")
    private Long dictId;

    @NotBlank(message = "字典编码不能为空")
    @Size(max = 50, message = "字典编码长度不能超过50个字符")
    @Schema(description = "字典编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "order_status")
    private String dictCode;

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称长度不能超过100个字符")
    @Schema(description = "字典名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "订单状态")
    private String dictName;

    @Schema(description = "分组", example = "sales")
    private String dictGroup;

    @Size(max = 500, message = "备注长度不能超过500个字符")
    @Schema(description = "备注", example = "订单状态字典")
    private String remark;

    @Schema(description = "排序", example = "0")
    private Integer sortOrder;

    @Schema(description = "是否启用", example = "1")
    private Integer isActive;
}
