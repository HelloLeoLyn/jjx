package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 物料导入结果（DEV-702：结构化返回，失败明细可下载）
 */
@Data
public class MaterialImportResultVO {

    /** 成功条数 */
    private int successCount;

    /** 跳过重复条数（文件内重复或与库中已存在重复） */
    private int skipCount;

    /** 失败条数 */
    private int failCount;

    /** 失败明细（行号/物料/原因） */
    private List<FailDetail> failDetails = new ArrayList<>();

    @Data
    public static class FailDetail {
        /** 行号（Excel 行号，从1开始） */
        private Integer rowIndex;
        /** 物料名称 */
        private String materialName;
        /** 失败原因 */
        private String reason;
    }

    public void addFail(Integer rowIndex, String materialName, String reason) {
        FailDetail d = new FailDetail();
        d.setRowIndex(rowIndex);
        d.setMaterialName(materialName);
        d.setReason(reason);
        failDetails.add(d);
        failCount = failDetails.size();
    }
}
