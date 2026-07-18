package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存导入结果VO
 */
@Data
public class StockImportResultVO {

    /** 成功导入数量 */
    private int successCount;

    /** 失败数量 */
    private int failCount;

    /** 失败详情列表 */
    private List<FailDetail> failDetails = new ArrayList<>();

    /**
     * 添加失败记录
     */
    public void addFail(int rowIndex, String materialName, String reason) {
        FailDetail detail = new FailDetail();
        detail.setRowIndex(rowIndex);
        detail.setMaterialName(materialName);
        detail.setReason(reason);
        failDetails.add(detail);
        failCount++;
    }

    /**
     * 添加成功记录
     */
    public void addSuccess() {
        successCount++;
    }

    @Data
    public static class FailDetail {
        /** 行号（从1开始） */
        private int rowIndex;
        /** 物料名称 */
        private String materialName;
        /** 失败原因 */
        private String reason;
    }
}
