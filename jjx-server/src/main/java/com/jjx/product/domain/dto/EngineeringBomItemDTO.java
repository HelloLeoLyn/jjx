package com.jjx.product.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * BOM明细DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EngineeringBomItemDTO {

    /** 明细ID（编辑已有行时必填；新增行不填） */
    private Long itemId;

    /** 工程侧请求移除该行：已审核通过行转“待移除”，由审核员确认后删除（dev-20261020-029） */
    private Boolean removeRequested;

    /** 父节点明细ID（NULL=根节点，树形结构） */
    private Long parentMaterialId;

    /** 物料ID（快速模式可空，服务端按材料名称+规格先入审核） */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 规格型号 */
    private String specification;

    /** 跳距（含单位，如50mm） */
    @jakarta.validation.constraints.Size(max = 50, message = "跳距不能超过50个字符")
    private String split;

    /** 项目（标准工序）引用 engineering_standard_process.process_id */
    private Long processId;

    /** 项目（标准工序）名称冗余 */
    private String processName;

    /** 兼容旧客户端；服务端根据项目是否为空判定，不采用此输入。 */
    private Boolean isCommon;

    /** 单位 */
    private String unit;

    /** 用量（服务端按基数÷模数重算；提交/批准时校验 >0） */
    private BigDecimal quantity;

    /** 应用料（含损耗，由后端按用量和损耗率重算，不采用Excel录入值） */
    private BigDecimal appliedQty;

    /** 实际投料（按最低投料向上取整，后端计算，Excel导入可直读） */
    private BigDecimal actualIssueQty;

    /** 损耗率(%) */
    private Integer lossRate;

    /** 模数：每份材料可产出产品数量 */
    private BigDecimal moduleQty;

    /** 基数：每个产品所需材料份数 */
    private BigDecimal baseQty;

    /** 最低投料量 */
    private BigDecimal minIssueQty;

    /** 规格-宽度(mm) */
    private BigDecimal widthMm;

    /** 规格-长度(mm) */
    private BigDecimal lengthMm;

    /** 层（薄膜开关专用） */
    private String layer;

    /** 位号 */
    private String positionNo;

    /** 来源类型：buy外购/make自制 */
    private String sourceType;

    /** 替代物料列表（JSON） */
    private String substituteJson;

    /** 排序 */
    @JsonAlias(value = {"sortOrder","itemOrder"})
    private Integer itemOrder;

    /** 备注 */
    private String remark;
}
