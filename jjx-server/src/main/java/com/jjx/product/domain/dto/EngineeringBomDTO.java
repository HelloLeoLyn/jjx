package com.jjx.product.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.product.domain.group.BomGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * BOM新增/编辑DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EngineeringBomDTO {

    /** BOM ID（编辑时必填，新增时不填） */
    private Long bomId;

    @NotBlank(message = "BOM编码不能为空")
    private String bomCode;

    /** BOM名称 */
    @NotBlank(message = "BOM名称不能为空")
    private String bomName;

    /** BOM版本 */
    @NotBlank(message = "BOM版本不能为空",groups = BomGroup.Version.class)
    private String bomVersion;

    /** 产品ID（PRODUCT 产出时必填；MATERIAL 产出时为空，由服务端按 outputType/outputId 校验） */
    private Long productId;

    /** 产出类型：PRODUCT/MATERIAL，空值按 PRODUCT 兼容历史调用。 */
    private String outputType;

    /** 产出ID：PRODUCT=productId，MATERIAL=materialId。 */
    private Long outputId;

    /** 产品编码（冗余） */
    private String productCode;

    /** 产品名称（冗余） */
    private String productName;

    /** 兼容历史回传；普通新增/编辑不采信，审核状态由专用流程维护。 */
    private Integer approveStatus;

    /** 兼容历史回传；普通新增/编辑不采信，生效标记由专用流程维护。 */
    private Boolean isCurrent;

    /** 生效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveDate;

    /** 失效日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date expiryDate;

    /** 备注 */
    private String remark;

    /** BOM明细列表 */
    @NotNull(message = "BOM明细不能为空")
    @Size(min = 1, message = "BOM明细至少需要一条")
    @Valid
    private List<EngineeringBomItemDTO> items;

    /** 产品级主方案选择（仅产品 BOM 保存时生效；与父 BOM 同事务持久化）。 */
    @Valid
    private List<ManufacturingSchemeChoiceDTO> schemeChoices;

    /** 前端加载时的 scheme_revision；传入则做乐观锁校验。 */
    private Integer schemeRevision;

    /**
     * 服务端内容指纹（dev-20261111-018）：加载详情时由服务端返回，保存时原样回传。
     * 客户端不得自行计算或改写；与服务端重算值不一致时保存返回 409，避免旧快照覆盖他人保存。
     */
    private String expectedContentRevision;

    /**
     * 编辑占用 token（Redis 租约，dev-20261111-018）。保存时校验，缺失/过期/被替换一律拒绝（fail-closed）。
     */
    private String editToken;
}
