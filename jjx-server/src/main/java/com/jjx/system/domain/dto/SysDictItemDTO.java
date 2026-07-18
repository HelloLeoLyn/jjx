package com.jjx.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 字典项DTO
 */
@Data
@Schema(description = "字典项DTO")
public class SysDictItemDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "字典项ID", example = "1")
    private Long itemId;

    @NotBlank(message = "字典编码不能为空")
    @Size(max = 50, message = "字典编码长度不能超过50个字符")
    @Schema(description = "字典编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "order_status")
    private String dictCode;

    @NotBlank(message = "字典键不能为空")
    @Size(max = 50, message = "字典键长度不能超过50个字符")
    @Schema(description = "字典键(实际存储值)", requiredMode = Schema.RequiredMode.REQUIRED, example = "draft")
    private String itemKey;

    @NotBlank(message = "字典值不能为空")
    @Size(max = 100, message = "字典值长度不能超过100个字符")
    @Schema(description = "字典值(显示文本)", requiredMode = Schema.RequiredMode.REQUIRED, example = "草稿")
    private String itemValue;

    @Size(max = 50, message = "标签长度不能超过50个字符")
    @Schema(description = "标签(扩展)", example = "灰色")
    private String label;

    @Size(max = 500, message = "备注长度不能超过500个字符")
    @Schema(description = "备注", example = "订单初始状态")
    private String remark;

    @Schema(description = "排序值", example = "0")
    private Integer sortOrder;

    @Schema(description = "是否启用", example = "1")
    private Integer isActive;

    @Schema(description = "扩展数据(JSON)", example = "{\"color\":\"gray\"}")
    private String extData;
}
