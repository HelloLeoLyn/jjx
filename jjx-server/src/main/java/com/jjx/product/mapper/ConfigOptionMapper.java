package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.ConfigOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 产品配置模型选项Mapper
 */
@Mapper
public interface ConfigOptionMapper extends BaseMapper<ConfigOption> {

    /**
     * 查询配置模型的全部选项（按排序）
     */
    @Select("SELECT * FROM product_config_option WHERE model_id = #{modelId} ORDER BY sort_order ASC, option_id ASC")
    List<ConfigOption> selectByModelId(@Param("modelId") Long modelId);
}
