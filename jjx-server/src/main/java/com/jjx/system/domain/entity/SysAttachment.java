package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用附件实体
 * 对应表：sys_attachment
 */
@Data
@TableName("sys_attachment")
@Schema(description = "通用附件")
public class SysAttachment {

    @Schema(description = "附件ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "业务类型（如: sales_order, product等）")
    private String bizType;

    @Schema(description = "业务记录ID")
    private Long bizId;

    @Schema(description = "文件类别（产品文件：客供稿/模具/确认图/菲林/规范等，业务附件可空）")
    private String category;

    @Schema(description = "版本号（产品文件用）")
    private String version;

    @Schema(description = "链路追踪ID（关联单据链路）")
    private String traceId;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "存储路径")
    private String filePath;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "MIME类型")
    private String fileType;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建者")
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新者")
    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "删除标记")
    @TableLogic
    private Boolean deleted;
}
