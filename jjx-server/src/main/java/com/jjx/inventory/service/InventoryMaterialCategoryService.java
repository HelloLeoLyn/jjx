package com.jjx.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.dto.query.CategoryQueryDTO;
import com.jjx.inventory.dto.vo.CategoryTreeVO;
import com.jjx.inventory.dto.vo.MaterialCategoryVO;

import java.util.List;

/**
 * 物料分类服务接口
 */
public interface InventoryMaterialCategoryService extends IService<InventoryMaterialCategory> {

    /**
     * 获取分类树
     */
    List<MaterialCategoryVO> getCategoryTree(CategoryQueryDTO queryDTO);

    /**
     * 检查分类编码是否存在
     */
    boolean existsByCode(String categoryCode);

    /**
     * 启用/停用分类
     */
    boolean updateStatus(Long categoryId, String status);

    /**
     * 删除分类（检查是否有子分类或关联物料）
     */
    boolean deleteWithCheck(Long categoryId);

    /**
     * 获取分类树
     */
    CategoryTreeVO getTreeById(Long categoryId);

    List<CategoryTreeVO> listTree(CategoryQueryDTO queryDTO);
}
