package com.jjx.engineering.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.Bom;

public interface IBomService extends IService<Bom> {
    PageResult<?> listPage(Object query);
}
