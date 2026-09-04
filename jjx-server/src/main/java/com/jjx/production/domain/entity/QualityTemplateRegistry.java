package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quality_template_registry")
public class QualityTemplateRegistry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String recordNo;
    private String recordName;
    private String version;
    private String ownerDept;
    private Integer retentionYears;
    private String category;
    private String bizType;
    private Long fileId;
    @TableField(exist = false)
    private Boolean hasFile;
    private Integer status;
    private String remark;
    private String printComponent;
    private String bizModule;
    private String printMode;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
