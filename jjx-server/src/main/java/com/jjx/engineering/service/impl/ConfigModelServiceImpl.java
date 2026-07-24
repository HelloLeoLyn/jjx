package com.jjx.engineering.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.ConfigModel;
import com.jjx.engineering.mapper.ConfigModelMapper;
import com.jjx.engineering.service.IConfigModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections;
@Service
@RequiredArgsConstructor
public class ConfigModelServiceImpl extends ServiceImpl<ConfigModelMapper, ConfigModel> implements IConfigModelService {
    @Override
    public PageResult<?> listPage(Object query) { return PageResult.build(Collections.emptyList(), 0); }
}
