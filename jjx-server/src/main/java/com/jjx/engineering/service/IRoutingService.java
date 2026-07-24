package com.jjx.engineering.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.Routing;
public interface IRoutingService extends IService<Routing> {
    PageResult<?> listPage(Object query);
}
