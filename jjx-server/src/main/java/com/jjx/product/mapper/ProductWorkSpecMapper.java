package com.jjx.product.mapper;

import org.apache.ibatis.annotations.*;
import java.util.Map;

/** 独立字段更新，普通产品编辑不会覆写工程规范。 */
@Mapper
public interface ProductWorkSpecMapper {
    @Select("SELECT COUNT(*) FROM product WHERE product_id = #{id}")
    int exists(Long id);

    @Select("SELECT CAST(work_spec_json AS CHAR) FROM product WHERE product_id = #{id}")
    String read(Long id);

    @Select("SELECT product_id, work_spec_json FROM product WHERE product_id = #{id} FOR UPDATE")
    Map<String, Object> lock(Long id);

    @Update("UPDATE product SET work_spec_json = #{json} WHERE product_id = #{id}")
    int write(@Param("id") Long id, @Param("json") String json);
}
