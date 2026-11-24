package com.jjx.inventory.dto.save;

import lombok.Data;

import java.util.List;

/** 批量 IQC 审核入参（dev-20261124-007）：只做批量通过，驳回仍逐行。 */
@Data
public class IqcBatchReviewDTO {
    private List<Long> itemIds;
    private String remark;
}
