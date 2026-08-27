package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品配置模型选项
 */
@Data
@TableName("product_config_option")
public class ConfigOption {

    /** 选项ID */
    @TableId(type = IdType.AUTO)
    private Long optionId;

    /** 所属配置模型ID */
    private Long modelId;

    /** 选项编码 */
    private String optionCode;

    /** 选项名称 */
    private String optionName;

    /** 选项类型(input/select/radio/checkbox) */
    private String optionType;

    /** 选项值定义(JSON) */
    private String valueJson;

    /** 依赖选项(JSON) */
    private String dependsOn;

    /** 互斥选项(JSON) */
    private String excludes;

    /** 是否必填 */
    private Integer isRequired;

    /** 排序 */
    private Integer sortOrder;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
