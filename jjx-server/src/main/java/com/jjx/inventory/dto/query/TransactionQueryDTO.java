package com.jjx.inventory.dto.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 库存流水查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransactionQueryDTO extends Page<Object> {

    private Long transactionId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private Long warehouseId;
    private String transactionType;
    private String sourceType;
    private String sourceNo;
    private String batchNo;
    private LocalDateTime transactionTimeStart;
    private LocalDateTime transactionTimeEnd;
    private String orderBy;
    private String orderDirection;
}
