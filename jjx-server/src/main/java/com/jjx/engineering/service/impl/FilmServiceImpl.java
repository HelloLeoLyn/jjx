package com.jjx.engineering.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.Film;
import com.jjx.engineering.mapper.FilmMapper;
import com.jjx.engineering.service.IFilmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilmServiceImpl extends ServiceImpl<FilmMapper, Film> implements IFilmService {
    @Override
    public PageResult<?> listPage(Object query) {
        return PageResult.build(Collections.emptyList(), 0);
    }
}
