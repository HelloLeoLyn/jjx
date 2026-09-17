package com.jjx.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.quality.domain.entity.QualityLot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 检验批 Mapper —— dev-20260917-001
 */
@Mapper
public interface QualityLotMapper extends BaseMapper<QualityLot> {

    /** 取某前缀下已存在的数量（用于生成批号流水；避免依赖额外序列服务） */
    @Select("SELECT COUNT(*) FROM quality_lot WHERE lot_no LIKE CONCAT(#{prefix}, '%')")
    Long countByLotNoPrefix(@Param("prefix") String prefix);

    /** 判断批号是否已被占用 */
    @Select("SELECT COUNT(*) FROM quality_lot WHERE lot_no = #{lotNo}")
    Long countByLotNo(@Param("lotNo") String lotNo);
}
