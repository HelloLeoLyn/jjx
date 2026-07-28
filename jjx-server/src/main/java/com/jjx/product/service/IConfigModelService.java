package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.product.domain.entity.ConfigModel;

public interface IConfigModelService extends IService<ConfigModel> {
    Object listPage(Object query);
}
