package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品作业规范发布条目（dev-20261011-008）
 * 一个版本下挂的条目：规范正文快照 / BOM / 工艺路线 / 工程图。
 */
@Data
@TableName("product_work_spec_item")
public class ProductWorkSpecItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long specVersionId;

    /** SPEC_JSON / BOM / ROUTING / DRAWING */
    private String resourceType;

    /** 原始资料记录 ID（追踪来源） */
    private Long sourceId;

    /** 原始资料不可变版本 ID（若支持） */
    private Long sourceVersionId;

    /** 对应工序标识（若适用） */
    private String operationRef;

    /** 发布时完整快照 / 引用描述（JSON） */
    private String snapshotJson;

    /** 内容或文件 SHA-256 */
    private String contentHash;

    private Integer sortOrder;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
