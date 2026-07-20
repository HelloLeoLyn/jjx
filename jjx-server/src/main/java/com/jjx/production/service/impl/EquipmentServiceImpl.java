package com.jjx.production.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.EquipmentQueryDTO;
import com.jjx.production.domain.entity.ProductionEquipment;
import com.jjx.production.mapper.ProductionEquipmentMapper;
import com.jjx.production.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentServiceImpl extends ServiceImpl<ProductionEquipmentMapper, ProductionEquipment> implements EquipmentService {
    private final ProductionEquipmentMapper equipmentMapper;

    @Override
    public PageResult<ProductionEquipment> page(EquipmentQueryDTO query) {
        LambdaQueryWrapper<ProductionEquipment> w = buildWrapper(query);
        w.orderByDesc(ProductionEquipment::getCreateTime);
        Page<ProductionEquipment> p = new Page<>(query.getPageNum(), query.getPageSize());
        equipmentMapper.selectPage(p, w);
        return PageResult.build(p.getRecords(), p.getTotal());
    }

    @Override
    public List<ProductionEquipment> list(EquipmentQueryDTO query) {
        return equipmentMapper.selectList(buildWrapper(query));
    }

    @Override
    public ProductionEquipment getById(Long id) {
        ProductionEquipment e = equipmentMapper.selectById(id);
        if (e == null) throw new BusinessException("设备不存在");
        return e;
    }

    @Override
    public Long create(ProductionEquipment entity) {
        equipmentMapper.insert(entity);
        return entity.getEquipmentId();
    }

    @Override
    public void update(ProductionEquipment entity) {
        equipmentMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        equipmentMapper.deleteById(id);
    }

    private LambdaQueryWrapper<ProductionEquipment> buildWrapper(EquipmentQueryDTO q) {
        LambdaQueryWrapper<ProductionEquipment> w = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(q.getEquipmentNo())) w.like(ProductionEquipment::getEquipmentNo, q.getEquipmentNo());
        if (StringUtils.isNotBlank(q.getEquipmentName())) w.like(ProductionEquipment::getEquipmentName, q.getEquipmentName());
        if (StringUtils.isNotBlank(q.getEquipmentType())) w.eq(ProductionEquipment::getEquipmentType, q.getEquipmentType());
        if (StringUtils.isNotBlank(q.getStatus())) w.eq(ProductionEquipment::getStatus, q.getStatus());
        return w;
    }
}
