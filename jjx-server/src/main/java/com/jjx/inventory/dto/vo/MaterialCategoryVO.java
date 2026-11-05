package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物料分类视图对象VO
 */
@Data
public class MaterialCategoryVO {

    private Long categoryId;
    private String categoryCode;
    /** 型号前缀，保存时必填（2～8位大写字母或数字）。 */
    private String modelPrefix;
    private String categoryName;
    private Long parentId;
    private String parentName;
    private Integer categoryLevel;
    /** 中文全路径，如 原材料/辅材/保护膜（dev-20261024-006） */
    private String namePath;
    private Integer sortOrder;
    private String status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private List<MaterialCategoryVO> children;
}
