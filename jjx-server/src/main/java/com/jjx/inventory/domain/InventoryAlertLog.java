package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存预警日志表实体类
 * 对应表：inventory_alert_log
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_alert_log")
public class InventoryAlertLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 预警ID */
    @TableId(type = IdType.AUTO)
    private Long alertId;

    /** 预警类型：safe_stock安全库存/max_stock最高库存/expiry保质期/obsolete呆滞料/order_shortage订单缺料 */
    private String alertType;

    /** 关联订单号（订单缺料预警用） */
    private String orderNo;

    /** 预警级别：info提示/warning警告/urgent紧急 */
    private String alertLevel;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 当前库存 */
    private BigDecimal currentStock;

    /** 安全库存 */
    private BigDecimal safeStock;

    /** 最高库存 */
    private BigDecimal maxStock;

    /** 有效期至 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** 最后出库日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastOutboundDate;

    /** 预警消息 */
    private String alertMessage;

    /** 预警时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime alertTime;

    /** 处理状态：new新预警/read已读/processed已处理/ignored已忽略 */
    private Integer status;

    /** 处理人 */
    private String processedBy;

    /** 处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedTime;

    /** 处理备注 */
    private String processRemark;

    /** 处理建议 */
    private String suggestion;

}
