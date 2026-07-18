package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 物料分类Mapper接口
 */
@Mapper
public interface InventoryMaterialCategoryMapper extends BaseMapper<InventoryMaterialCategory> {

    /**
     * 查询所有启用的分类（用于下拉框）
     */
    @Select("SELECT category_id, category_name FROM inventory_material_category WHERE status = '0' ORDER BY sort_order")
    List<InventoryMaterialCategory> selectAllEnabled();

    /**
     * 根据父分类ID查询子分类列表
     */
    @Select("SELECT * FROM inventory_material_category WHERE parent_id = #{parentId} AND status = '0' ORDER BY sort_order")
    List<InventoryMaterialCategory> selectByParentId(@Param("parentId") Long parentId);

    /**
     * 检查分类编码是否存在
     */
    @Select("SELECT COUNT(*) FROM inventory_material_category WHERE category_code = #{categoryCode}")
    int countByCode(@Param("categoryCode") String categoryCode);

}
