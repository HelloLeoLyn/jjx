package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样品单产品资料转移记录（DEV-505）
 * 样品确认后，把打样成果建档为正式产品/BOM/工艺路线
 */
@Data
@TableName("sales_sample_transfer")
public class SalesSampleTransfer {

    @TableId(type = IdType.AUTO)
    private Long transferId;

    /** 样品单ID */
    private Long orderId;

    /** 样品单号 */
    private String orderNo;

    /** 转移单号 */
    private String transferNo;

    /** 建档产品ID */
    private Long productId;

    /** 建档BOM ID */
    private Long bomId;

    /** 建档路线ID */
    private Long routingId;

    /** 产品建档动作 NONE/CREATE/UPDATE */
    private String productAction;

    /** BOM动作 NONE/CREATE/EXISTS/SKIP_NO_PROCESS */
    private String bomAction;

    /** 路线动作 NONE/CREATE/EXISTS/SKIP_NO_PROCESS */
    private String routingAction;

    /** 转移结果 SUCCESS/PARTIAL/FAILED */
    private String status;

    /** 转移明细说明 */
    private String detail;

    private String createBy;

    private LocalDateTime createTime;
}
