package com.jjx.product.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工程录入的规范附加内容；BOM、路线及图纸文件仍使用原始数据源。 */
@Data
public class ProductWorkSpecDTO {
    @Size(max = 64) private String revision;
    /** null 保留历史 BOM 回退；空数组是操作员明确选择的空清单。 */
    @Valid @Size(max = 1000) private List<ProjectMaterial> projectMaterials;
    @Size(max = 64) private String projectMaterialsAcknowledgement;

    @Data
    public static class ProjectMaterial {
        private Long materialId;
        @Size(max = 100) private String materialCode;
        @Size(max = 200) private String materialName;
        private Long processId;
        @Size(max = 200) private String processName;
        @Size(max = 500) private String specification;
        /** 空值使用自动计算，非空为人工覆盖的纯文本。 */
        @Size(max = 500) private String params;
        private java.math.BigDecimal quantity;
        @Size(max = 30) private String unit;
        @Size(max = 100) private String dieLocation;
        private Integer sortOrder;
        private java.math.BigDecimal moduleQty;
        private java.math.BigDecimal baseQty;
        @Size(max = 100) private String split;
        private java.math.BigDecimal widthMm;
        private java.math.BigDecimal lengthMm;
        /** 导入时的 BOM 内容，仅用于差异提醒，不作关联键。 */
        private JsonNode sourceBom;
    }
    @Size(max = 6) private Map<String, String> emboss = new LinkedHashMap<>();
    @Size(max = 2000) private String engineeringRequirements;
    @Size(max = 20) private String requirementsColor;
    /** Legacy text or BOM-row keyed object; both must remain readable. */
    private JsonNode dieLocation;
    private Long structureFileId;
    @Valid @Size(max = 100) private List<Change> changes = new ArrayList<>();
    @Size(max = 100) private String issueUnit;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") private LocalDate issueDate;

    @Data
    public static class Change {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") private LocalDate date;
        @Size(max = 1000) private String text;
        @Size(max = 20) private String color;
        /** 旧记录未设置时保持原打印行为；新记录由前端显式传false。 */
        private Boolean print;
        @Size(max = 1000) private String reason;
        @Size(max = 100) private List<Long> sourceLogIds = new ArrayList<>();
    }
}
