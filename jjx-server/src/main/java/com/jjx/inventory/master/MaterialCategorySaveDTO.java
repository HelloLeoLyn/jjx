package com.jjx.inventory.master;

import java.util.Map;

/** 品类基础信息与公共字段必填配置由同一事务保存。未传必填配置时保留原配置。 */
public record MaterialCategorySaveDTO(Long categoryId, String categoryCode, String categoryName, Long parentId,
                                      Integer sortOrder, Integer status, String modelPrefix, String remark,
                                      Map<String, Object> requiredFields) {
    public MaterialMasterConfigurationService.CategorySave category() {
        return new MaterialMasterConfigurationService.CategorySave(categoryId, categoryCode, categoryName,
                parentId, sortOrder, status, modelPrefix);
    }
}
