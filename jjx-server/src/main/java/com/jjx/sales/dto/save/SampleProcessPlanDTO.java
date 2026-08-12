package com.jjx.sales.dto.save;

import lombok.Data;

import java.util.List;

/**
 * 打样工序计划保存DTO（方案A：多选作业项目形成打样计划）
 * 整单保存：覆盖当前轮次的工序计划
 */
@Data
public class SampleProcessPlanDTO {

    /**
     * 打样轮次（缺省取样品单当前轮次）
     */
    private Integer roundNo;

    /**
     * 工序计划项列表（有序，按列表顺序）
     */
    private List<Item> items;

    /**
     * 单道工序计划项
     */
    @Data
    public static class Item {

        /** 工序记录ID（编辑已有计划时传入，新增为null） */
        private Long processId;

        /**
         * 关联作业项目(标准工序)ID，null=自定义工序
         */
        private Long stdProcessId;

        /**
         * 下标数字（带下标作业项目的下标值，如4=④）
         */
        private Integer indexNumber;

        /**
         * 工序顺序（同一卡片多个作业项目传相同值，缺省按列表顺序）
         */
        private Integer processOrder;

        /**
         * 卡片项目结构（PANEL/UP_LINE/DOWN_LINE/OTHER，卡片级主结构）
         */
        private String processCategory;

        /**
         * 一级大类：ASSEMBLY冲型组装/PRINT印刷（dev-20260811-009）
         */
        private String majorCategory;

        /**
         * 定制工艺参数(JSON, 印刷: {printName,colorNo,inkNo,screenNo})（dev-20260811-009）
         */
        private String customProcessParams;

        /**
         * 工序名称（作业项目名或自定义工序名）
         */
        private String processName;

        /**
         * 材料JSON（[{name,spec,qty,unit,materialId,materialCode}]，可多选）
         */
        private String materials;

        /**
         * 工艺说明/描述
         */
        private String processNote;

        /**
         * 状态：0待做 1进行中 2完成
         */
        private Integer status;
    }
}
