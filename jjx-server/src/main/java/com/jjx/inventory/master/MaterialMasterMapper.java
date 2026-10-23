package com.jjx.inventory.master;

import com.jjx.inventory.domain.InventoryMaterial;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MaterialMasterMapper {
    @Select("SELECT category_id FROM inventory_material_category WHERE category_id=#{id} FOR UPDATE")
    Long lockCategory(@Param("id") Long id);

    @Select("SELECT * FROM inventory_attribute_template WHERE category_id=#{id} FOR UPDATE")
    InventoryAttributeTemplate lockTemplate(@Param("id") Long id);

    @Select("SELECT * FROM inventory_material WHERE material_id=#{id} FOR UPDATE")
    InventoryMaterial lockMaterial(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM inventory_transaction WHERE material_id=#{id}")
    long transactionCount(@Param("id") Long id);

    @Select("SELECT material_id FROM inventory_material WHERE category_id=#{id} ORDER BY material_id LIMIT 1 FOR UPDATE")
    Long firstMaterialInCategory(@Param("id") Long id);
}
