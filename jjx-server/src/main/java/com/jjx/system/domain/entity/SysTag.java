package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统标签主数据（通用）
 * 对应表：sys_tag（dev-20260911-007）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tag")
public class SysTag extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 标签ID */
    @TableId(type = IdType.AUTO)
    private Long tagId;

    /** 标签编码（分组内唯一） */
    private String tagCode;

    /** 标签名称 */
    private String tagName;

    /** 标签分组（对应字典 sys_tag_group，如 supplier_goods） */
    private String tagGroup;

    /** 父标签ID（二级标签用，空为顶级） */
    private Long parentId;

    /** 排序 */
    private Integer sortOrder;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 逻辑删除：0正常 1删除 */
    @TableLogic(value = "0", delval = "1")
    private String delFlag;
}
