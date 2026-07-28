package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.product.domain.entity.ConfigModel;
import com.jjx.product.mapper.ConfigModelMapper;
import com.jjx.product.service.IConfigModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConfigModelServiceImpl extends ServiceImpl<ConfigModelMapper, ConfigModel> implements IConfigModelService {
    @Override
    public Object listPage(Object query) {
        return list();
    }
}
