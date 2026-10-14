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
    /**
     * 模板文件引用ID（sys_attachment_ref.id，来源 quality_template）。
     * dev-20261014-026：file_id 退役——模板文件只认引用，本字段非落库列，由查询时按引用回填。
     */
    @TableField(exist = false)
    private Long fileRefId;
    @TableField(exist = false)
    private Boolean hasFile;
    private Integer status;
    private String remark;
    private String printComponent;
    private String bizModule;
    private String printMode;
    private Integer qrEnabled;
    private String qrContentRule;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
