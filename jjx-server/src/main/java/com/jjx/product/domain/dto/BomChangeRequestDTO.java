package com.jjx.product.domain.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** BOM 变更单入参（dev-20261112-009）。 */
@Data
public class BomChangeRequestDTO {

    private Long changeId;
    private String title;
    private LocalDate effectiveDate;
    private String remark;
    private List<Item> items;

    @Data
    public static class Item {
        private Long bomId;
        private String bomVersion;
        private LocalDate targetEffectiveDate;
    }
}
