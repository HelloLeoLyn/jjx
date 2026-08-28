package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.QualityTemplateRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QualityTemplateRegistryMapper extends BaseMapper<QualityTemplateRegistry> {
    @Select("SELECT DISTINCT owner_dept FROM quality_template_registry " +
            "WHERE owner_dept IS NOT NULL AND owner_dept <> '' ORDER BY owner_dept")
    List<String> selectDistinctOwnerDepts();
}
