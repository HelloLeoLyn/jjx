package com.jjx.engineering.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.StandardProcess;
public interface IStandardProcessService extends IService<StandardProcess> {
    PageResult<?> listPage(Object query);
}
