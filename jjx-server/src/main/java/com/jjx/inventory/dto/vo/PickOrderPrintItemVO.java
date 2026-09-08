package com.jjx.inventory.dto.vo;

import lombok.Data;
import java.math.BigDecimal;

/** JJX-QR-031 领料单纸版明细。 */
@Data
public class PickOrderPrintItemVO {
    private Integer sequence;
    private String materialName;
    private String projectName;
    private String specification;
    private String unit;
    private BigDecimal moduleQty;
    private BigDecimal issuedQuantity;
    private BigDecimal stockQuantity;
    private String remark;
}
