package com.jjx.engineering.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.ConfigModel;
public interface IConfigModelService extends IService<ConfigModel> {
    PageResult<?> listPage(Object query);
}
