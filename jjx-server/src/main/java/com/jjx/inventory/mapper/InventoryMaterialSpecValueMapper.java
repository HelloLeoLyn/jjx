package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryMaterialSpecValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

/**
 * 物料规格属性值 Mapper（dev-20261023-022）。
 */
/** @deprecated 302历史表仅保留归档，生产服务不再调用。 */
@Deprecated
@Mapper
public interface InventoryMaterialSpecValueMapper extends BaseMapper<InventoryMaterialSpecValue> {

    /** 取物料当前的规格文本与补充说明（只读，不经过实体，避免主数据实体随本任务扩张）。 */
    @Select("SELECT specification, spec_supplement FROM inventory_material WHERE material_id = #{materialId}")
    Map<String, Object> selectSpecText(@Param("materialId") Long materialId);

    /**
     * 写回生成文本 + 补充说明。规格文本是下游（BOM/打印/采购/来料检验）唯一读取口径，
     * 因此由属性组合后统一覆盖，不再二次手改。
     */
    @Update("UPDATE inventory_material SET specification = #{specification}, spec_supplement = #{supplement}, "
            + "update_time = NOW() WHERE material_id = #{materialId}")
    int updateSpecText(@Param("materialId") Long materialId,
                       @Param("specification") String specification,
                       @Param("supplement") String supplement);
}
