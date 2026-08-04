package com.jjx.product.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EngineeringRoutingVO {
    private Long routingId;
    private String routingCode;
    private String routingName;
    private Long productId;
    private String productCode;
    private String productName;
    private String routingVersion;
    private Integer isCurrent;
    private String isCurrentName;
    private Integer approveStatus;
    private BigDecimal totalLaborHours;
    private BigDecimal totalMachineHours;
    private Integer processCount;
    private String description;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String remark;

    private List<EngineeringRoutingItemVO> items;

    /**
     * 组合汇总信息
     */
    private List<GroupSummary> groupSummaries;

    @Data
    public static class GroupSummary {
        private Long groupId;
        private Integer groupOrder;
        private String groupName;
        private BigDecimal totalLaborHours;
        private BigDecimal totalMachineHours;
        private Integer processCount;
    }
}
