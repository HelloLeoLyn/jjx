package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 销售订单操作日志实体类
 */
@Data
@TableName("sales_log")
public class SalesLog {
    
    @TableId(type = IdType.AUTO)
    private Long logId;
    
    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderNo;
    
    /**
     * 操作类型: OperationTypeEnum
     */
    private Integer operationType;
    
    /**
     * 操作描述
     */
    private String operationDescription;
    
    /**
     * 操作人ID
     */
    private Long operatorId;
    
    /**
     * 操作人姓名
     */
    private String operatorName;
    
    /**
     * 操作时间
     */
    private LocalDateTime operationTime;
    
    /**
     * 操作结果: 1success, 2failure
     */
    private Integer operationResult;
    
    /**
     * 备注
     */
    private String remark;
}