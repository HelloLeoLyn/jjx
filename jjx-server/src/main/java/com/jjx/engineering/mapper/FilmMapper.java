package com.jjx.engineering.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.engineering.domain.entity.Film;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FilmMapper extends BaseMapper<Film> {}
