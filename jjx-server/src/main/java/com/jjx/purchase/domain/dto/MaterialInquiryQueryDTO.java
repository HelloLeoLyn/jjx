package com.jjx.purchase.domain.dto;

import lombok.Data;

import java.util.Date;

/**
 * 材料询价查询DTO
 *
 * @author JJX ERP系统
 * @date 2026-04-02
 */
@Data
public class MaterialInquiryQueryDTO {

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料规格
     */
    private String materialSpec;

    /**
     * 供应商ID
     */
    private Long supplierId;

    /**
     * 供应商名称
     */
    private String supplierName;

    /**
     * 询价状态
     */
    private String inquiryStatus;

    /**
     * 询价人
     */
    private String inquiryPerson;

    /**
     * 询价开始日期
     */
    private Date inquiryDateStart;

    /**
     * 询价结束日期
     */
    private Date inquiryDateEnd;

    /**
     * 最小单价
     */
    private Double minPrice;

    /**
     * 最大单价
     */
    private Double maxPrice;

    /**
     * 币种
     */
    private String currency;

    /**
     * 排序字段
     */
    private String orderByColumn;

    /**
     * 排序方向（asc/desc）
     */
    private String orderDirection = "desc";

    /**
     * 是否只查询有效询价
     */
    private Boolean onlyActive = false;

    /**
     * 是否只查询在有效期内的询价
     */
    private Boolean onlyWithinValidity = false;

    /**
     * 是否包含过期询价
     */
    private Boolean includeExpired = false;

    /**
     * 搜索关键词（模糊搜索物料编码、名称、规格、供应商名称）
     */
    private String keyword;

    /**
     * 获取排序SQL
     */
    public String getOrderBy() {
        if (orderByColumn == null || orderByColumn.trim().isEmpty()) {
            return "inquiry_date desc";
        }

        String column = orderByColumn.trim();
        String direction = "desc".equalsIgnoreCase(orderDirection) ? "desc" : "asc";

        // 字段映射
        switch (column) {
            case "inquiryDate":
                column = "inquiry_date";
                break;
            case "inquiryPrice":
                column = "inquiry_price";
                break;
            case "materialCode":
                column = "material_code";
                break;
            case "materialName":
                column = "material_name";
                break;
            case "supplierName":
                column = "supplier_name";
                break;
            case "createTime":
                column = "create_time";
                break;
            default:
                column = "inquiry_date";
        }

        return column + " " + direction;
    }

    /**
     * 获取分页起始位置
     */
    public Integer getStartRow() {
        if (pageNum == null || pageSize == null) {
            return 0;
        }
        return (pageNum - 1) * pageSize;
    }

    /**
     * 验证查询参数
     */
    public boolean isValid() {
        // 验证分页参数
        if (pageNum != null && pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize != null && (pageSize < 1 || pageSize > 100)) {
            pageSize = 10;
        }

        // 验证日期范围
        if (inquiryDateStart != null && inquiryDateEnd != null) {
            if (inquiryDateStart.after(inquiryDateEnd)) {
                // 交换日期
                Date temp = inquiryDateStart;
                inquiryDateStart = inquiryDateEnd;
                inquiryDateEnd = temp;
            }
        }

        // 验证价格范围
        if (minPrice != null && maxPrice != null) {
            if (minPrice > maxPrice) {
                // 交换价格
                Double temp = minPrice;
                minPrice = maxPrice;
                maxPrice = temp;
            }
        }

        return true;
    }
}
