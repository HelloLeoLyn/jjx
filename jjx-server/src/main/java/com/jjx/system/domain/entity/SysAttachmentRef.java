package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_attachment_ref")
public class SysAttachmentRef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long attachmentId;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private String productCode;
    private Long productId;
    private String documentType;
    private String displayName;
    private String remark;
    private Integer sortOrder;
    @TableLogic
    private Boolean deleted;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String deletedBy;
    private LocalDateTime deletedAt;

    /**
     * 文件元数据（非落库列：仅由 refs 查询链路回填，供列表直接展示，避免前端二次请求）
     * 只读展示字段，不参与引用唯一性、权限判定与任何写入。
     */
    @TableField(exist = false)
    private String fileName;
    @TableField(exist = false)
    private Long fileSize;
    @TableField(exist = false)
    private String fileType;

    // ===== 图纸元数据（非落库列：refs 查询链路从 sys_attachment 回填，供工程图纸库图种分组/版本/现行/受控/下发展示）=====
    // 同样是只读展示字段，不参与引用唯一性、权限判定与任何写入。（dev-20261014-016 第3项）
    @TableField(exist = false)
    private String category;
    @TableField(exist = false)
    private String version;
    @TableField(exist = false)
    private String drawingNo;
    @TableField(exist = false)
    private String drawingName;
    @TableField(exist = false)
    private String fileRole;
    @TableField(exist = false)
    private Integer isCurrent;
    @TableField(exist = false)
    private Integer isControlled;
    @TableField(exist = false)
    private Integer released;
}
