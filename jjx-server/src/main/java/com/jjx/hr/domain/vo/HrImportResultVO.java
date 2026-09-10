package com.jjx.hr.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 员工档案导入结果。 */
@Data
public class HrImportResultVO {

    /** 解析到的总行数 */
    private int total;

    private int successCount;

    private int failCount;

    /** 行级错误明细（不影响其他行入库） */
    private List<RowError> errors = new ArrayList<>();

    @Data
    public static class RowError {
        /** Excel 行号（含表头，从 1 开始） */
        private int rowNum;
        private String message;

        public RowError() {
        }

        public RowError(int rowNum, String message) {
            this.rowNum = rowNum;
            this.message = message;
        }
    }

    public void addError(int rowNum, String message) {
        this.errors.add(new RowError(rowNum, message));
    }
}
