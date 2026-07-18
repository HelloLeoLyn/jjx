package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.product.domain.entity.ProductCategory;
import com.jjx.product.domain.query.ProductCategoryQuery;
import com.jjx.product.domain.vo.ProductCategoryTreeVo;
import com.jjx.product.domain.vo.ProductCategoryVO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 产品分类Service接口
 */
public interface IProductCategoryService extends IService<ProductCategory> {

    /**
     * 获取分类树形结构
     */
    List<ProductCategoryTreeVo> getCategoryTree(ProductCategoryQuery query);

    /**
     * 获取分类树形结构
     */
    ProductCategoryVO getCategory(Long categoryId);

    /**
     * 根据父ID获取子分类列表
     */
    List<ProductCategory> getChildrenByParentId(Long parentId);

    /**
     * 检查分类编码是否唯一
     */
    boolean checkCategoryCodeUnique(String categoryCode);

    /**
     * 检查分类名称是否唯一
     */
    boolean checkCategoryNameUnique(String categoryName);

    /**
     * 删除分类（包含子分类）
     */
    boolean removeCategoryWithChildren(Long categoryId);

    /**
     * 获取分类列表
     */
    List<ProductCategory> getCategoryList(ProductCategoryQuery query);

    @Transactional(rollbackFor = Exception.class)
    boolean createCategory(ProductCategory category);

    @Transactional(rollbackFor = Exception.class)
    boolean updateCategory(ProductCategory category);

    /**
     * 删除分类（不包含子分类）
     */
    boolean deleteCategory(Long categoryId);


}
