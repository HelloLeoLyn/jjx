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

import java.util.List;

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
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public EngineeringBase getById(Long id) {
        return baseMapper.selectById(id);
    }

    @Override
    public boolean save(EngineeringBase entity) {
        return baseMapper.insert(entity) > 0;
    }

    @Override
    public boolean update(EngineeringBase entity) {
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return baseMapper.deleteById(id) > 0;
    }
}
