package com.jjx.engineering.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.Film;

public interface IFilmService extends IService<Film> {
    PageResult<?> listPage(Object query);
}
