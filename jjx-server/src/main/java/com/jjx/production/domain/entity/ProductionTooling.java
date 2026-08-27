package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工装模具档案
 * 对应表：production_tooling
 * SCREEN=网框 DIE=刀模，网框/刀模专属字段按类型使用
 */
@Data
@TableName("production_tooling")
public class ProductionTooling {

    @TableId(type = IdType.AUTO)
    private Long toolingId;

    /** 工装编号（网框编号/刀模编号，唯一） */
    private String toolingNo;

    /** 名称 */
    private String toolingName;

    /** 类型：SCREEN=网框 DIE=刀模 */
    private String toolingType;

    /** 参数（如：材质：xxx\n尺寸：xxx，长度512） */
    private String spec;

    // ===== 刀模专属（DIE） =====
    /** 设计冲切寿命上限(次) */
    private Integer lifeLimit;
    /** 已冲切次数（手工维护+报工累加） */
    private Integer currentCount;

    // ===== 公共管理字段 =====
    /** 状态：0=在库 1=使用中 2=清洗/保养中 3=维修中 4=报废 */
    private Integer status;
    /** 存放位置 */
    private String location;
    /** 使用部门 */
    private String department;
    /** 责任人 */
    private String responsible;
    /** 客户（定制工装所属客户） */
    private String customer;
    /** 启用日期 */
    private LocalDate enableDate;
    /** 最后使用时间 */
    private LocalDateTime lastUseTime;
    /** 累计使用次数 */
    private Integer useCount;
    /** 备注 */
    private String remark;

    // ===== 审计字段 =====
    @TableLogic
    private String delFlag;
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
