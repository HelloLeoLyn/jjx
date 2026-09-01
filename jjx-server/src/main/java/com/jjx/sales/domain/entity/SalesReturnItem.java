package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 销售退货单明细实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_return_item")
public class SalesReturnItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long itemId;

    /** 退货单ID */
    private Long returnId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格 */
    private String materialSpec;

    /** 单位 */
    private String unit;

    /** 退货数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;
}
