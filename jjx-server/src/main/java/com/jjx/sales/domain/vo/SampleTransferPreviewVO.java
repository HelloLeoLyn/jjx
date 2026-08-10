package com.jjx.sales.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 打样转标准-预览VO
 * 读取打样数据（sales_sample_process + materials JSON），返回带系统匹配推荐的预览数据
 * 前端轻量版弹窗 / 对照版全屏页共用
 */
@Data
public class SampleTransferPreviewVO {

    /** 样品单ID */
    private Long orderId;

    /** 样品单号 */
    private String orderNo;

    /** 打样工序列表（含系统匹配推荐） */
    private List<SampleProcessItem> sampleProcesses;

    /** 打样物料列表（含系统匹配推荐，从 materials JSON 展开） */
    private List<SampleMaterialItem> sampleMaterials;

    /** 标准工序库列表（下拉选项） */
    private List<StandardProcessOption> standardProcesses;

    /** 标准物料库列表（下拉选项） */
    private List<StandardMaterialOption> standardMaterials;

    /**
     * 打样工序项
     */
    @Data
    public static class SampleProcessItem {
        /** 打样工序记录ID（sales_sample_process.process_id） */
        private Long processId;
        /** 打样工序名称（原始，可能含"（临时）"等后缀） */
        private String processName;
        /** 工序顺序（组合分组键） */
        private Integer processOrder;
        /** 卡片项目结构（PANEL/UP_LINE/DOWN_LINE/OTHER） */
        private String processCategory;
        /** 工艺说明 */
        private String processNote;
        /** 耗时（分钟） */
        private Integer durationMinutes;
        /** 系统匹配推荐的标准工序ID（匹配不上为 null） */
        private Long matchedStdProcessId;
        /** 系统匹配推荐的标准工序名称 */
        private String matchedStdProcessName;
        /** 是否匹配到标准工序 */
        private Boolean matched;
    }

    /**
     * 打样物料项（materials JSON 展开，每物料一行）
     */
    @Data
    public static class SampleMaterialItem {
        /** 前端行标识：sourceProcessId_index */
        private String rowKey;
        /** 来源打样工序ID */
        private Long sourceProcessId;
        /** 来源打样工序名称 */
        private String sourceProcessName;
        /** 物料名称（原始） */
        private String name;
        /** 规格 */
        private String spec;
        /** 用量 */
        private BigDecimal qty;
        /** 单位 */
        private String unit;
        /** 打样 JSON 中已有的物料ID（可空） */
        private Long materialId;
        /** 打样 JSON 中已有的物料编码（可空） */
        private String materialCode;
        /** 系统匹配推荐的标准物料ID（匹配不上为 null） */
        private Long matchedMaterialId;
        /** 系统匹配推荐的标准物料编码 */
        private String matchedMaterialCode;
        /** 系统匹配推荐的标准物料名称 */
        private String matchedMaterialName;
        /** 是否匹配到标准物料 */
        private Boolean matched;
    }

    /**
     * 标准工序库选项（下拉用）
     */
    @Data
    public static class StandardProcessOption {
        private Long processId;
        private String processCode;
        private String processName;
        private String processType;
        private String processCategory;
    }

    /**
     * 标准物料库选项（下拉用）
     */
    @Data
    public static class StandardMaterialOption {
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String specification;
        private String unit;
    }
}
