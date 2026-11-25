package com.jjx.inventory.dto.save;

import lombok.Data;

import java.util.List;

/** 确认入库入参（dev-20261125-002）：支持按明细行指定库位，locationId 为整单默认值。 */
@Data
public class InboundConfirmDTO {
    /** 整单默认库位；明细未指定时使用 */
    private Long locationId;
    /** 行级库位；优先于默认库位 */
    private List<ItemLocation> items;

    @Data
    public static class ItemLocation {
        private Long itemId;
        private Long locationId;
    }
}
