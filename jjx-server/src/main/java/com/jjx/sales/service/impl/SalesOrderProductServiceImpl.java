package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.product.domain.vo.ProductValidationVO;
import com.jjx.sales.domain.converter.SalesOrderProductConverter;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.dto.SalesOrderProductQueryDTO;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import com.jjx.sales.service.ISalesOrderProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单产品明细服务实现类
 */
@Service
@RequiredArgsConstructor
public class SalesOrderProductServiceImpl extends ServiceImpl<SalesOrderProductMapper, SalesOrderProduct>
        implements ISalesOrderProductService {

    private final SalesOrderProductConverter orderProductConverter;
    private final com.jjx.product.service.WorkSpecBindingService workSpecBindingService;


    @Override
    public SalesOrderProductVO getById(Long id) {
        SalesOrderProduct entity = baseMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        return orderProductConverter.toVO(entity);
    }

    @Override
    public PageResult<SalesOrderProductVO> getPageList(SalesOrderProductQueryDTO queryDTO) {
        LambdaQueryWrapper<SalesOrderProduct> wrapper = buildQueryWrapper(queryDTO);

        Page<SalesOrderProduct> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        IPage<SalesOrderProduct> entityPage = baseMapper.selectPage(page, wrapper);

        List<SalesOrderProductVO> voList = orderProductConverter.toVOList(entityPage.getRecords());

        return PageResult.of(entityPage, voList);
    }

    @Override
    public List<SalesOrderProductVO> getListByOrderId(Long orderId) {
        LambdaQueryWrapper<SalesOrderProduct> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SalesOrderProduct::getOrderId, orderId);

        List<SalesOrderProduct> entityList = baseMapper.selectList(wrapper);
        return orderProductConverter.toVOList(entityList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean add(SalesOrderProductDTO addDTO) {
        SalesOrderProduct entity = orderProductConverter.toEntity(addDTO);
        entity.setWorkSpecVersionId(workSpecBindingService.latestPublishedId(entity.getProductId()));
        return save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchAdd(List<SalesOrderProductDTO> addDTOList) {
        List<SalesOrderProduct> entityList = addDTOList.stream()
                .map(orderProductConverter::toEntity).toList();
        entityList.forEach(entity -> entity.setWorkSpecVersionId(workSpecBindingService.latestPublishedId(entity.getProductId())));
        return saveBatch(entityList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceItems(Long orderId, List<SalesOrderProductDTO> items) {
        List<SalesOrderProduct> previous = baseMapper.selectList(Wrappers.<SalesOrderProduct>lambdaQuery()
                .eq(SalesOrderProduct::getOrderId, orderId).orderByAsc(SalesOrderProduct::getId));
        java.util.Map<Long, java.util.LinkedList<SalesOrderProduct>> byProduct = new java.util.HashMap<>();
        previous.forEach(line -> byProduct.computeIfAbsent(line.getProductId(), k -> new java.util.LinkedList<>()).add(line));
        List<SalesOrderProduct> replacements = new java.util.ArrayList<>();
        for (SalesOrderProductDTO item : items) {
            SalesOrderProduct entity = orderProductConverter.toEntity(item);
            entity.setOrderId(orderId);
            java.util.LinkedList<SalesOrderProduct> matches = byProduct.get(entity.getProductId());
            SalesOrderProduct original = null;
            if (item.getId() != null) {
                original = previous.stream().filter(line -> item.getId().equals(line.getId())).findFirst()
                        .orElseThrow(() -> new com.jjx.common.exception.BusinessException("原订单明细不存在，不能继承采用版本"));
                if (!java.util.Objects.equals(original.getProductId(), entity.getProductId())) original = null;
                if (original != null && (matches == null || !matches.remove(original))) {
                    throw new com.jjx.common.exception.BusinessException("订单明细ID重复");
                }
            } else if (matches != null && !matches.isEmpty()) {
                if (matches.stream().map(SalesOrderProduct::getWorkSpecVersionId).distinct().count() > 1) {
                    throw new com.jjx.common.exception.BusinessException("同产品存在不同采用版本，请保留原明细ID后重试");
                }
                original = matches.removeFirst();
            }
            entity.setWorkSpecVersionId(original == null
                    ? workSpecBindingService.latestPublishedId(entity.getProductId()) : original.getWorkSpecVersionId());
            replacements.add(entity);
        }
        deleteByOrderId(orderId);
        return replacements.isEmpty() || saveBatch(replacements);
    }

    @Override
    public void validateAdoptedVersions(Long orderId) {
        for (SalesOrderProduct line : baseMapper.selectList(Wrappers.<SalesOrderProduct>lambdaQuery()
                .eq(SalesOrderProduct::getOrderId, orderId))) {
            if (line.getWorkSpecVersionId() != null) {
                workSpecBindingService.requirePublished(line.getProductId(), line.getWorkSpecVersionId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SalesOrderProductDTO editDTO) {
        SalesOrderProduct entity = orderProductConverter.toEntity(editDTO);
        return updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        return removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDelete(List<Long> ids) {
        return removeByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByOrderId(Long orderId) {
        LambdaQueryWrapper<SalesOrderProduct> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SalesOrderProduct::getOrderId, orderId);
        return remove(wrapper);
    }

    @Override
    public boolean isExists(Long orderId) {
        SalesOrderProductQueryDTO dto = new SalesOrderProductQueryDTO();
        dto.setOrderId(orderId);
        Wrapper<SalesOrderProduct> queryWrapper = buildQueryWrapper(dto);
        return baseMapper.exists(queryWrapper);
    }

    @Override
    public List<ProductValidationVO> validation(Long orderId) {
        return baseMapper.selectProductValidationByOrderId(orderId);
    }

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<SalesOrderProduct> buildQueryWrapper(SalesOrderProductQueryDTO queryDTO) {
        LambdaQueryWrapper<SalesOrderProduct> wrapper = Wrappers.lambdaQuery();

        if (queryDTO.getId() != null) {
            wrapper.eq(SalesOrderProduct::getId, queryDTO.getId());
        }
        if (queryDTO.getOrderId() != null) {
            wrapper.eq(SalesOrderProduct::getOrderId, queryDTO.getOrderId());
        }
        if (queryDTO.getProductId() != null) {
            wrapper.eq(SalesOrderProduct::getProductId, queryDTO.getProductId());
        }
        if (queryDTO.getProductCode() != null && !queryDTO.getProductCode().isEmpty()) {
            wrapper.like(SalesOrderProduct::getProductCode, queryDTO.getProductCode());
        }
        if (queryDTO.getProductName() != null && !queryDTO.getProductName().isEmpty()) {
            wrapper.like(SalesOrderProduct::getProductName, queryDTO.getProductName());
        }
        if (queryDTO.getMinQuantity() != null) {
            wrapper.ge(SalesOrderProduct::getQuantity, queryDTO.getMinQuantity());
        }
        if (queryDTO.getMaxQuantity() != null) {
            wrapper.le(SalesOrderProduct::getQuantity, queryDTO.getMaxQuantity());
        }

        // 排序
        if (queryDTO.getOrderByColumn() != null && !queryDTO.getOrderByColumn().isEmpty()) {
            String orderByColumn = queryDTO.getOrderByColumn();
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getIsAsc());

            switch (orderByColumn) {
                case "id":
                    wrapper.orderBy(true, isAsc, SalesOrderProduct::getId);
                    break;
                case "quantity":
                    wrapper.orderBy(true, isAsc, SalesOrderProduct::getQuantity);
                    break;
                case "amount":
                    wrapper.orderBy(true, isAsc, SalesOrderProduct::getAmount);
                    break;
                default:
                    wrapper.orderByDesc(SalesOrderProduct::getId);
            }
        }
        return wrapper;
    }
}
