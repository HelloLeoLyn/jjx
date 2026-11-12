package com.jjx.production.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * BOM 明细耗用只读试算入参（dev-20261112-007）。
 *
 * <p>数值一律用 {@link BigDecimal} 接参，由服务层统一拒绝空值、非正数与超精度，
 * 不让 Spring 绑定层先抛类型错误；本接口只读，不保存 BOM、不写库存、不产生任何流水。</p>
 *
 * <p>精度口径：产出数量 outputQuantity 最多 4 位小数；baseQty / moduleQty / minIssueQty
 * 对齐 decimal(12,4)；lossRate 为百分比，最多 2 位小数。尺寸（宽/长）只信物料档案，
 * 请求不接收任何尺寸字段。</p>
 */
@Data
public class BomConsumptionPreviewRequest {

    /** 产出数量 Q（必须 >0，最多 4 位小数）。 */
    private BigDecimal outputQuantity;

    /** 产出单位；空/缺省按 PCS（仅用于判断是否可调用跳距接口，不参与耗用换算）。 */
    private String outputUnit;

    /** 明细行；null/空返回空 rows，不报错。最多 500 行。 */
    private List<Row> rows;

    /**
     * 单行 BOM 明细快照（前端按当前编辑态传入，不落库）。
     */
    @Data
    public static class Row {

        /** 前端行标识：必填、非空、≤64 字符、同请求内唯一（大小写敏感）。 */
        private String key;

        /** 物料 ID；空表示缺料号的快速草稿行，允许只按自填单位试算。 */
        private Long materialId;

        /** 行单位；空则用物料档案基本单位兜底。 */
        private String unit;

        /** BOM 原始基数（每 moduleQty 产出所需量），必须 >0。 */
        private BigDecimal baseQty;

        /** BOM 原始模数（每 baseQty 对应的产出数），必须 >0。 */
        private BigDecimal moduleQty;

        /** 损耗率百分比（0..100，最多 2 位小数）；null 视为 0。 */
        private BigDecimal lossRate;

        /** 最低投料量（≥0，最多 4 位小数）；null 视为 0。 */
        private BigDecimal minIssueQty;

        /** 显式参考卷长（米，>0 时优先于档案名义长度）；空表示按档案 lengthMm 兜底。 */
        private BigDecimal referenceRollLengthM;
    }
}
