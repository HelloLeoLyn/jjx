package com.jjx.product.domain.dto;

import com.jjx.common.annotation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "菲林DTO")
public class EngineeringFilmDTO {
    
    @Schema(description = "菲林ID")
    @NotNull(message = "菲林ID不能为空", groups = ValidationGroups.Update.class)
    private Long filmId;
    
    @Schema(description = "菲林编码（留空则按 产品编码-菲林类型 自动生成）", example = "FILM_OVERLAY_001")
    private String filmCode;
    
    @NotBlank(message = "菲林名称不能为空")
    @Schema(description = "菲林名称", example = "面板菲林")
    private String filmName;
    
    @NotBlank(message = "菲林类型不能为空")
    @Schema(description = "菲林类型", example = "OVERLAY")
    private String filmType;
    
    @NotNull(message = "产品ID不能为空")
    @Schema(description = "产品ID", example = "1001")
    private Long productId;
    
    @Schema(description = "菲林尺寸", example = "200x150mm")
    private String filmSize;
    
    @Schema(description = "菲林厚度(mm)", example = "0.125")
    private BigDecimal filmThickness;
    
    @Schema(description = "菲林材料", example = "PET")
    private String filmMaterial;
    
    @Schema(description = "颜色", example = "深灰色")
    private String color;
    
    @Schema(description = "技术规格")
    private String technicalSpec;
    
    @Schema(description = "设计说明")
    private String designNotes;
    
    @Schema(description = "关联工序ID")
    private Long processId;
    
    @Schema(description = "图纸文件ID（sys_attachment.id，由 /system/attachment/upload-product 上传后回填）")
    private Long fileId;
    
    @Schema(description = "图纸文件路径")
    private String filePath;
    
    @Schema(description = "图纸文件名")
    private String fileName;
    
    @Schema(description = "备注")
    private String remark;
}