package com.jjx.product.domain.vo;

import com.jjx.engineering.domain.entity.EngineeringBomItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * BOM VO
 */
@Data
public class EngineeringBomVO {

    /** BOM ID */
    private Long bomId;

    /** BOM编码 */
    private String bomCode;

    /** BOM名称，编辑回填必须返回已保存的名称。 */
    private String bomName;

    /** 产品ID */
    private Long productId;

    /** 产出类型：PRODUCT/MATERIAL。 */
    private String outputType;

    /** 产出ID。 */
    private Long outputId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /**
     * 产出对象编码/名称：PRODUCT 取产品，MATERIAL 取物料。
     * 半成品制造方案没有 productId，只靠这两个字段才能在详情里说清"这张方案是给谁做的"。
     */
    private String outputCode;

    private String outputName;

    /**
     * 同一 bomCode 下的版本总数（详情页展示用）。
     * 不复用 versionCount：列表行 BomListRow 已用 long versionCount，同名会导致 getter 返回类型冲突。
     */
    private Integer versionTotal;

    /** 版本号 */
    private String bomVersion;

    /**
     * 版本号（V1.0/V2.0，版本化统一字段）
     */
    private String version;

    /** BOM类型 */
    private String bomType;

    /** 是否当前版本 */
    private Boolean isCurrent;

    /** 生效日期 */
    private Date effectiveDate;

    /** 失效日期 */
    private Date expiryDate;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 创建时间 */
    private String createBy;

    /** 更新时间 */
    private String updateBy;

    /** 审批状态 */
    private Integer approveStatus;

    /** 审批状态 */
    private String approveRemark;

    /** 备注 */
    private String remark;

    /** 明细 */
    private List<EngineeringBomItem> items;

    /** 产品级主方案 JSON 当前版本（产品 BOM 详情返回，供前端回写乐观锁）。 */
    private Integer schemeRevision;

    /** 已保存的产品主方案选择。 */
    private List<com.jjx.product.domain.dto.ManufacturingSchemeChoiceDTO> schemeChoices;

    /**
     * 服务端内容指纹（SHA-256，dev-20261111-018）：由父表 + 全部明细按稳定编码计算。
     * 保存/审批时回传为 expectedContentRevision；与 schemeRevision 语义不同、互不替代。
     */
    private String contentRevision;
}
