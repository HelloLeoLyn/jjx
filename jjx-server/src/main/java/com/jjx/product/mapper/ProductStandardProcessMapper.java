package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.ProductStandardProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductStandardProcessMapper extends BaseMapper<ProductStandardProcess> {
    
    /**
     * 查询启用的工序
     */
    @Select("SELECT * FROM engineering_standard_process WHERE is_enabled = 1 ORDER BY display_order")
    List<ProductStandardProcess> selectEnabledProcesses();
    
    /**
     * 根据工序类型查询
     */
    @Select("SELECT * FROM engineering_standard_process WHERE process_type = #{processType} AND is_enabled = 1 ORDER BY display_order")
    List<ProductStandardProcess> selectByProcessType(@Param("processType") String processType);
    
    /**
     * 根据工序类别查询
     */
    @Select("SELECT * FROM engineering_standard_process WHERE process_category = #{processCategory} AND is_enabled = 1")
    List<ProductStandardProcess> selectByProcessCategory(@Param("processCategory") String processCategory);
}