package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.product.domain.converter.ProductCategoryConverter;
import com.jjx.product.domain.entity.ProductCategory;
import com.jjx.product.domain.query.ProductCategoryQuery;
import com.jjx.product.domain.vo.ProductCategoryTreeVo;
import com.jjx.product.domain.vo.ProductCategoryVO;
import com.jjx.product.mapper.ProductCategoryMapper;
import com.jjx.product.service.IProductCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import com.jjx.system.annotation.Event;

/**
 * 产品分类Service实现
 */
@Service
public class ProductCategoryServiceImpl extends ServiceImpl<ProductCategoryMapper,ProductCategory> implements IProductCategoryService {
    private static final Logger log = LoggerFactory.getLogger(ProductCategoryServiceImpl.class);
    private final ProductCategoryMapper productCategoryMapper;
    private final ProductCategoryConverter categoryConverter;

    public ProductCategoryServiceImpl(ProductCategoryMapper productCategoryMapper, ProductCategoryConverter categoryConverter) {
        this.productCategoryMapper = productCategoryMapper;
        this.categoryConverter = categoryConverter;
    }

    @Override
    public List<ProductCategoryTreeVo> getCategoryTree(ProductCategoryQuery query) {
        // 获取所有分类
        List<ProductCategory> allCategories = productCategoryMapper.selectList(new LambdaQueryWrapper<>());

        // 应用查询条件过滤
        List<ProductCategory> filteredCategories = new ArrayList<>();
        for (ProductCategory category : allCategories) {
            if (query.getCategoryCode() != null && !query.getCategoryCode().isEmpty()) {
                if (!category.getCategoryCode().contains(query.getCategoryCode())) {
                    continue;
                }
            }
            if (query.getCategoryName() != null && !query.getCategoryName().isEmpty()) {
                if (!category.getCategoryName().contains(query.getCategoryName())) {
                    continue;
                }
            }
            if (query.getStatus() != null && !query.getStatus().isEmpty()) {
                if (!category.getStatus().equals(query.getStatus())) {
                    continue;
                }
            }
            filteredCategories.add(category);
        }

        // 构建树形结构
        return buildCategoryTree(filteredCategories, 0L);
    }

    @Override
    public ProductCategoryVO getCategory(Long categoryId) {
        ProductCategory category = productCategoryMapper.selectById(categoryId);
        return categoryConverter.toVO(category);
    }

    @Override
    public List<ProductCategory> getChildrenByParentId(Long parentId) {
        LambdaQueryWrapper<ProductCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductCategory::getParentId, parentId);
        queryWrapper.orderByAsc(ProductCategory::getSortOrder);
        return productCategoryMapper.selectList(queryWrapper);
    }

    @Override
    public List<ProductCategory> getCategoryList(ProductCategoryQuery query) {
        LambdaQueryWrapper<ProductCategory> queryWrapper = new LambdaQueryWrapper<>();

        // 应用查询条件
        if (query.getCategoryCode() != null && !query.getCategoryCode().isEmpty()) {
            queryWrapper.like(ProductCategory::getCategoryCode, query.getCategoryCode());
        }
        if (query.getCategoryName() != null && !query.getCategoryName().isEmpty()) {
            queryWrapper.like(ProductCategory::getCategoryName, query.getCategoryName());
        }
        if (query.getParentId() != null) {
            queryWrapper.eq(ProductCategory::getParentId, query.getParentId());
        }
        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            queryWrapper.eq(ProductCategory::getStatus, query.getStatus());
        }

        // 按排序号排序
        queryWrapper.orderByAsc(ProductCategory::getSortOrder);

        return productCategoryMapper.selectList(queryWrapper);
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    @Event(value = "product.category.created", bizId = "#category", bizType = "'product'")
    public boolean createCategory(ProductCategory category) {
        // 检查分类编码是否唯一
        if (!checkCategoryCodeUnique(category.getCategoryCode())) {
            throw new BusinessException(BusinessExceptionEnum.DB_DUPLICATE_KEY);
        }
        // 设置层级
        if (category.getParentId() == null || category.getParentId() == 0) {
            category.setParentId(0L);
            category.setCategoryLevel(1);
        } else {
            ProductCategory parent = productCategoryMapper.selectById(category.getParentId());
            if (parent == null) {
                throw new BusinessException(BusinessExceptionEnum.PRODUCT_CATEGORY_NOT_FOUND);
            }
            category.setCategoryLevel(parent.getCategoryLevel() + 1);
        }
        return productCategoryMapper.insert(category) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    @Event(value = "product.category.updated", bizId = "#category", bizType = "'product'")
    public boolean updateCategory(ProductCategory category) {
        // 检查分类编码是否唯一
        if (!checkCategoryCodeUnique(category.getCategoryCode())) {
            throw new BusinessException(BusinessExceptionEnum.DB_DUPLICATE_KEY);
        }

        // 如果修改了父分类，需要更新层级
        ProductCategory oldCategory = productCategoryMapper.selectById(category.getCategoryId());
        if (oldCategory == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_CATEGORY_NOT_FOUND);
        }

        return productCategoryMapper.updateById(category) > 0;
    }

    @Override
    @Event(value = "product.category.deleted", bizId = "#categoryId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCategory(Long categoryId) {
        // 检查是否有子分类
        List<ProductCategory> children = getChildrenByParentId(categoryId);
        if (!children.isEmpty()) {
            throw new BusinessException("存在子分类，无法删除。请使用removeCategoryWithChildren方法删除分类及其子分类");
        }

        // 检查是否有产品使用该分类
        // 这里需要调用产品服务检查，暂时跳过
        return productCategoryMapper.deleteById(categoryId) > 0;
    }

    @Override
    public boolean checkCategoryCodeUnique(String categoryCode) {
        ProductCategory one = lambdaQuery().eq(ProductCategory::getCategoryCode, categoryCode).one();
        return one == null;
    }

    @Override
    public boolean checkCategoryNameUnique(String categoryName) {
        ProductCategory one = lambdaQuery().eq(ProductCategory::getCategoryName, categoryName).one();
        return one == null;
    }

    /**
     * 递归获取所有子分类ID
     */
    private List<Long> getAllChildCategoryIds(Long parentId) {
        List<Long> allChildIds = new ArrayList<>();
        collectChildCategoryIds(parentId, allChildIds);
        return allChildIds;
    }

    /**
     * 递归收集子分类ID
     */
    private void collectChildCategoryIds(Long parentId, List<Long> result) {
        List<ProductCategory> children = getChildrenByParentId(parentId);
        for (ProductCategory child : children) {
            result.add(child.getCategoryId());
            // 递归收集孙子分类
            collectChildCategoryIds(child.getCategoryId(), result);
        }
    }

    @Override
    @Event(value = "product.category.deleted_children", bizId = "#categoryId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public boolean removeCategoryWithChildren(Long categoryId) {
        // 1. 验证分类是否存在
        ProductCategory category = productCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_CATEGORY_NOT_FOUND);
        }

        // 2. 检查是否有产品使用该分类或子分类
        // TODO: 这里需要调用产品服务检查，暂时跳过

        // 3. 获取所有子分类ID（包括孙子分类等）
        List<Long> allChildIds = getAllChildCategoryIds(categoryId);

        // 4. 添加当前分类ID到删除列表
        allChildIds.add(categoryId);

        // 5. 批量删除所有分类
        int deletedCount = productCategoryMapper.deleteBatchIds(allChildIds);

        // 6. 记录删除日志
        log.info("删除分类及其子分类，父分类ID: {}, 删除数量: {}", categoryId, deletedCount);

        return deletedCount > 0;
    }


    /**
     * 构建分类树
     */
    private static List<ProductCategoryTreeVo> buildCategoryTree(List<ProductCategory> categories, Long parentId) {
        List<ProductCategoryTreeVo> tree = new ArrayList<>();

        for (ProductCategory category : categories) {
            if (category.getParentId().equals(parentId)) {
                ProductCategoryTreeVo treeVo = new ProductCategoryTreeVo();
                treeVo.setCategoryId(category.getCategoryId());
                treeVo.setCategoryCode(category.getCategoryCode());
                treeVo.setCategoryName(category.getCategoryName());
                treeVo.setParentId(category.getParentId());
                treeVo.setCategoryLevel(category.getCategoryLevel());
                treeVo.setSortOrder(category.getSortOrder());
                treeVo.setStatus(category.getStatus());

                // 递归构建子节点
                List<ProductCategoryTreeVo> children = buildCategoryTree(categories, category.getCategoryId());
                treeVo.setChildren(children);

                tree.add(treeVo);
            }
        }

        // 按排序号排序
        tree.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        return tree;
    }
}
