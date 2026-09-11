package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统标签关联（通用多对多）
 * 对应表：sys_tag_rel（dev-20260911-007）
 *
 * <p>biz_type + biz_id 指向任意业务主键，如 ('purchase_supplier', 12)。</p>
 */
@Data
@TableName("sys_tag_rel")
public class SysTagRel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联ID */
    @TableId(type = IdType.AUTO)
    private Long relId;

    /** 标签ID */
    private Long tagId;

    /** 业务类型，如 purchase_supplier */
    private String bizType;

    /** 业务主键 */
    private Long bizId;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
