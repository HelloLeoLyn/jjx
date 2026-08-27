package com.jjx.production.domain.vo;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生产订单导出视图对象（DEV-683：Excel 导出用）
 */
@Data
public class ProductionOrderExportVO {

    @ExcelColumn(value = "订单编号", order = 1)
    private String orderNo;

    @ExcelColumn(value = "订单类型", order = 2)
    private String orderTypeDesc;

    @ExcelColumn(value = "来源销售单", order = 3)
    private String salesOrderNo;

    @ExcelColumn(value = "产品编码", order = 4)
    private String productCode;

    @ExcelColumn(value = "产品名称", order = 5)
    private String productName;

    @ExcelColumn(value = "产品规格", order = 6)
    private String productSpec;

    @ExcelColumn(value = "计划数量", order = 7)
    private BigDecimal plannedQuantity;

    @ExcelColumn(value = "已完成数量", order = 8)
    private BigDecimal completedQuantity;

    @ExcelColumn(value = "剩余数量", order = 9)
    private BigDecimal remainingQuantity;

    @ExcelColumn(value = "完成率(%)", order = 10)
    private BigDecimal completionPercentage;

    @ExcelColumn(value = "计划开始", order = 11)
    private LocalDate planStartDate;

    @ExcelColumn(value = "计划结束", order = 12)
    private LocalDate planEndDate;

    @ExcelColumn(value = "订单状态", order = 13)
    private String orderStatusDesc;

    @ExcelColumn(value = "审批状态", order = 14)
    private String approvalStatusDesc;

    @ExcelColumn(value = "优先级", order = 15)
    private String priorityDesc;

    @ExcelColumn(value = "负责部门", order = 16)
    private String departmentName;

    @ExcelColumn(value = "材料状态", order = 17)
    private String materialStatusDesc;

    @ExcelColumn(value = "工艺路线", order = 18)
    private String routingName;

    @ExcelColumn(value = "创建人", order = 19)
    private String createBy;

    @ExcelColumn(value = "创建时间", order = 20)
    private LocalDateTime createTime;

    @ExcelColumn(value = "备注", order = 21)
    private String remark;
}
