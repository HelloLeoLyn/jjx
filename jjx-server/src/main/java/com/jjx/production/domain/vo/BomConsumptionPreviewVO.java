package com.jjx.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * BOM 明细耗用只读试算出参（dev-20261112-007）。
 *
 * <p>语义边界：本接口只给试算展示值，不保存 BOM、不写库存、不预留、不产生流水；
 * 参考米数/参考卷数只是展示参考，不表示物理卷、批次余量或库存可供量。</p>
 *
 * <p>字段名与主会话前端契约一致，新增/改名/嵌套包装都会破坏前端按名取用。</p>
 */
@Data
public class BomConsumptionPreviewVO {

    /** 只读标记；本接口恒为 true。 */
    private boolean readOnly = true;

    /** 与请求 rows 顺序一一对应的试算结果。 */
    private List<Row> rows;

    /**
     * 单行试算结果；valid=false 时只说明该行为何不能试算，不影响其余行。
     */
    @Data
    public static class Row {

        /** 回显请求行 key。 */
        private String key;

        /** 该行是否可试算；false 时计算字段为 null（不把异常当 0）。 */
        private boolean valid;

        /** valid=false 时的可读中文原因。 */
        private String message;

        /** 计算单位（请求行 unit 优先，空则由档案单位兜底），已归一。 */
        private String unit;

        /** 每件净耗展示值（4 位小数 HALF_UP 的普通十进制文本）。 */
        private String unitConsumption;

        /** 总需求（含取整/4 位小数）展示文本。 */
        private String totalConsumption;

        /** 总需求权威精确文本（如 "105/2"；整数时为整数字符串）。 */
        private String exactConsumption;

        /** 参考米数（连续计量且档案宽度可用时才有值，4 位小数文本）。 */
        private String metres;

        /** 参考卷数 = ceil(米数 / 参考卷长)，整数字符串；缺米数或卷长时为 null。 */
        private String referenceRollCount;

        /** 实际采用的参考卷长（米）；请求显式值优先，否则档案 lengthMm/1000。 */
        private BigDecimal referenceRollLengthM;

        /** 物料档案基本单位，仅供展示；null 表示无有效 materialId。 */
        private String archiveUnit;

        /** 物料档案宽 mm；null 表示没有可信宽度（不猜尺寸）。 */
        private BigDecimal widthMm;

        /** 物料档案长 mm；null 表示没有可信长度。 */
        private BigDecimal lengthMm;

        /** 是否可调用 004 跳距 GET 接口做辅助（仅能力标记，不代表物料形态分类）。 */
        private boolean pitchSupported;

        /** 实际采用的来源方式（dev-20261112-008）；null 表示走历史比例口径。 */
        private String consumptionMode;

        /** 卷材裁切/连续耗材的面积（㎡）展示文本；不适用时为 null。 */
        private String areaM2;
    }
}
