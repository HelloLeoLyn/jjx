package com.jjx.product.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class EngineeringRoutingDTO {

    private Long routingId;

    /**
     * 路线编码
     */
    private String routingCode;

    /**
     * 路线名称
     */
    private String routingName;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品编码
     */
    private String productCode;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 版本号
     */
    private String routingVersion;

    /**
     * 版本号（V1.0/V2.0，版本化改造新增）
     */
    private String version;

    /**
     * 父版本路线ID（冗余，保存时忽略）
     */
    private Long parentRoutingId;

    /**
     * 来源打样单ID（冗余，保存时忽略）
     */
    private Long sourceSampleId;

    /**
     * 路线说明
     */
    private String description;

    /**
     * 备注
     */
    private String remark;

    /**
     * 工序明细列表
     */
    private List<EngineeringRoutingItemDTO> items;

    /**
     * 保存时是否自动升版（true=内容变更，生成新版本，旧版本失效）
     */
    private Boolean bumpVersion;

    /**
     * 变更说明（自动升版时记录到新版本 remark）
     */
    private String changeNote;
}
