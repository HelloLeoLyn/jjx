package com.jjx.product.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工程录入的规范附加内容；BOM、路线及图纸文件仍使用原始数据源。 */
@Data
public class ProductWorkSpecDTO {
    @Size(max = 64) private String revision;
    @Size(max = 6) private Map<String, String> emboss = new LinkedHashMap<>();
    @Size(max = 2000) private String engineeringRequirements;
    @Size(max = 20) private String requirementsColor;
    @Size(max = 500) private String dieLocation;
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
