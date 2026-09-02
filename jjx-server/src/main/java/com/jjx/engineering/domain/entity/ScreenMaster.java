package com.jjx.engineering.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 网版主数据实体类（jjx_screen_master）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jjx_screen_master")
public class ScreenMaster extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long screenId;

    /** 网版编号（如 A0001） */
    private String screenNo;

    /** 框型：A/B/C/F/G/H */
    private String frameType;

    /** 网版内容记录 */
    private String content;

    /** 目数（可选） */
    private String mesh;

    /** 状态：1在用 0停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
