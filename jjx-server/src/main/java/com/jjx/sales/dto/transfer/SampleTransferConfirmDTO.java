package com.jjx.sales.dto.transfer;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 打样转标准-确认转移DTO
 * 接收前端编辑后的标准数据（工序映射 + 物料映射），直接落库生成新版本 BOM/Routing
 */
@Data
public class SampleTransferConfirmDTO {

    /** 样品单ID */
    private Long orderId;

    /** 工序映射列表（前端编辑后的标准工序选择，含顺序/组合信息） */
    private List<ProcessMapping> processMappings;

    /** 物料映射列表（前端编辑后的标准物料选择，每物料一行） */
    private List<MaterialMapping> materialMappings;

    /**
     * 工序映射项
     */
    @Data
    public static class ProcessMapping {
        /** 打样工序记录ID（新增工序为 null） */
        private Long sampleProcessId;
        /** 选择的标准工序ID（必填） */
        private Long stdProcessId;
        /** 工序名称（冗余展示） */
        private String processName;
        /** 全局顺序（前端排好） */
        private Integer processOrder;
        /** 组合ID（前端临时负数，同一组合共享；null=独立工序） */
        private Long groupId;
        /** 组合序号 */
        private Integer groupOrder;
        /** 组合名称（如"面板组"） */
        private String groupName;
        /** 工序类别（MAIN/PREPARATION/FINISHING/QUALITY） */
        private String processCategory;
        /** 工艺说明 */
        private String processNote;
        /** 耗时（分钟） */
        private Integer durationMinutes;
    }

    /**
     * 物料映射项
     */
    @Data
    public static class MaterialMapping {
        /** 前端行标识：sourceProcessId_index */
        private String rowKey;
        /** 来源打样工序ID */
        private Long sourceProcessId;
        /** 来源打样工序名称（BOM 明细 layer 用） */
        private String sourceProcessName;
        /** 选择的标准物料ID（必填） */
        private Long materialId;
        /** 物料名称（冗余展示） */
        private String materialName;
        /** 规格 */
        private String spec;
        /** 用量 */
        private BigDecimal qty;
        /** 单位 */
        private String unit;
    }
}
