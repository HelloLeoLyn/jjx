package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产品BOM明细Mapper接口
 */
@Mapper
public interface EngineeringBomItemMapper extends BaseMapper<EngineeringBomItem> {

    @Delete("delete from engineering_bom_item where bom_id=#{bomId}")
    void deleteByBomId(@Param("bomId") Long bomId);

    /**
     * 锁住某 BOM 的全部明细行（dev-20261111-018）。
     * 锁顺序统一为「先父行、再明细」；MySQL RR 下普通 SELECT 会读事务早期快照，
     * 内容指纹必须用当前读，才能识别「加载后被他人保存」。
     */
    @Select("SELECT * FROM engineering_bom_item WHERE bom_id = #{bomId} FOR UPDATE")
    List<EngineeringBomItem> selectByBomIdForUpdate(@Param("bomId") Long bomId);

    /** 行审核按 id 重读当前行（dev-20261111-018）：只改审核相关字段，不用锁前旧实体整行覆盖。 */
    @Select("SELECT * FROM engineering_bom_item WHERE item_id = #{itemId} FOR UPDATE")
    EngineeringBomItem selectByItemIdForUpdate(@Param("itemId") Long itemId);

}
