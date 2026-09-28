package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryIqcScrapOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryIqcScrapOrderMapper extends BaseMapper<InventoryIqcScrapOrder> {
    /** 报废审批串行化：重复点击/并发审批只能有一个请求取得待审批状态。 */
    @Select("SELECT * FROM inventory_iqc_scrap_order WHERE scrap_id = #{scrapId} FOR UPDATE")
    InventoryIqcScrapOrder selectByIdForUpdate(@Param("scrapId") Long scrapId);
}
