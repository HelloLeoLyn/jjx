package com.jjx.sales.domain.vo;

import lombok.Data;
import java.util.List;

@Data
public class SalesOrderSampleOptionsVO {
    private Long orderId;
    private String orderNo;
    private Long customerId;
    private String customerName;
    private String contactPerson;
    private String contactPhone;
    private String sourceVersion;
    private String deliveryDate;
    private List<Item> items;

    @Data
    public static class Item {
        private Long itemId;
        private Long productId;
        private String productCode;
        private String productName;
        private String unit;
        private Integer orderQuantity;
        private String remark;
        private List<ActiveSample> activeSamples;
    }

    @Data
    public static class ActiveSample {
        private Long orderId;
        private String orderNo;
        private Integer sampleStatus;
    }
}
