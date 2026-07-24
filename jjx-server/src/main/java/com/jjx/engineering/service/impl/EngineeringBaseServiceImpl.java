package com.jjx.engineering.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.EngineeringBase;
import com.jjx.engineering.mapper.EngineeringBaseMapper;
import com.jjx.engineering.service.EngineeringBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EngineeringBaseServiceImpl extends ServiceImpl<EngineeringBaseMapper, EngineeringBase>
        implements EngineeringBaseService {

    private final EngineeringBaseMapper baseMapper;

    @Override
    public PageResult<EngineeringBase> pageQuery(int pageNum, int pageSize) {
        Page<EngineeringBase> page = baseMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<EngineeringBase>().orderByDesc(EngineeringBase::getCreateTime)
        );
        PageResult<EngineeringBase> result = new PageResult<>();
        result.setRows(page.getRecords());
        result.setTotal(page.getTotal());
        return result;
    }
}
