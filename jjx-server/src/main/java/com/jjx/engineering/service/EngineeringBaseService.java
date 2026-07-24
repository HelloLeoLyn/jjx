package com.jjx.engineering.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.domain.entity.EngineeringBase;

import java.util.List;

/**
 * 工程管理服务接口
 */
public interface EngineeringBaseService {
    PageResult<EngineeringBase> pageQuery(int pageNum, int pageSize);
    EngineeringBase getById(Long id);
    boolean save(EngineeringBase entity);
    boolean update(EngineeringBase entity);
    boolean deleteById(Long id);
}
