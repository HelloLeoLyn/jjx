package com.jjx.inventory.dto.update;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 物料规格属性保存参数（按品类属性生成规格文本）。dev-20261023-022
 * <p>
 * materialCode 优先用于新增后保存（新增接口不返回主键）；materialId 用于编辑。
 */
@Data
public class MaterialSpecValuesDTO {

    private Long materialId;

    private String materialCode;

    /** 品类编码；缺省取物料当前品类 */
    private String categoryCode;

    /** 生成文本之外的补充说明，≤30 字 */
    private String supplement;

    /** true = 新增场景，按属性定义的 required 校验必填 */
    private Boolean createMode;

    private List<Item> attributes = new ArrayList<>();

    /** 单个属性取值：数值型用 value；区间型用 value + value2；下拉/文本用 value */
    @Data
    public static class Item {
        private String attrCode;
        private String value;
        private String value2;
    }
}
