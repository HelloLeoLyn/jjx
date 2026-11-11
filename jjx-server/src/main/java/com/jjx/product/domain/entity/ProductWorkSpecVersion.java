package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品作业规范发布版本（dev-20261011-008）
 * 不可变制造资料基线；一张产品多条版本，生产只认它，不认正在编辑的当前资料。
 */
@Data
@TableName("product_work_spec_version")
public class ProductWorkSpecVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /** 产出身份：PRODUCT 产品 / MATERIAL 物料（历史空值按 PRODUCT 解释，dev-20261111-004）。 */
    private String outputType;

    /** 产出ID：PRODUCT 时为 product_id，MATERIAL 时为 inventory_material.material_id。 */
    private Long outputId;

    /** 产品内版本号，如 V1.0 */
    private String versionNo;

    /** PUBLISHED / RETIRED */
    private String status;

    /** 本次修订说明 */
    private String changeSummary;

    /** 基于哪个版本（可空） */
    private Long basedOnVersionId;

    /** 发布清单校验值 */
    private String manifestHash;

    /** 计划生效时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime effectiveFrom;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedAt;

    private String publishedBy;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
