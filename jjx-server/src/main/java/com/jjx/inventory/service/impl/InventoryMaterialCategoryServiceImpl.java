package com.jjx.inventory.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.dto.query.CategoryQueryDTO;
import com.jjx.inventory.dto.vo.CategoryTreeVO;
import com.jjx.inventory.dto.vo.MaterialCategoryVO;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.service.InventoryMaterialCategoryService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.jjx.system.annotation.Event;

/**
 * 物料分类服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMaterialCategoryServiceImpl
        extends ServiceImpl<InventoryMaterialCategoryMapper, InventoryMaterialCategory>
        implements InventoryMaterialCategoryService {

    private final InventoryMaterialCategoryMapper materialCategoryMapper;
    /** 2026-09-21（dev-20260921-013）：库存事件改手写 payload。 */
    private final com.jjx.event.EventPublisher eventPublisher;
    private final InventoryMaterialMapper materialMapper;

    private static LambdaQueryWrapper<InventoryMaterialCategory> buildQueryWrapper(CategoryQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterialCategory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(StringUtils.isNotBlank(queryDTO.getCategoryCode()), InventoryMaterialCategory::getCategoryCode,
                        queryDTO.getCategoryCode())
                .like(StringUtils.isNotBlank(queryDTO.getCategoryName()), InventoryMaterialCategory::getCategoryName,
                        queryDTO.getCategoryName())
                .eq(StringUtils.isNotBlank(queryDTO.getStatus()), InventoryMaterialCategory::getStatus,
                        queryDTO.getStatus());
        return queryWrapper;
    }

    /**
     * 库存主数据/预警事件统一发布（2026-09-21 dev-20260921-013 库存批 3/3）：
     * 手写 payload，bizNo 取对象编码（删除类由调用方传入删除前取到的编码）。
     */
    private void publishCategoryEvent(String eventCode, Long id, String knownCode) {
        InventoryMaterialCategory m = (id == null || knownCode != null) ? null : materialCategoryMapper.selectById(id);
        String code = knownCode != null ? knownCode : (m == null ? null : m.getCategoryCode());
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "material_category", id, code);
        // 删除路径由调用方传 knownCode，此时 m 为 null —— 不防空会在运行时 NPE
        // （2026-09-21 dev-20260921-023 修复）
        if (m != null) {
            payload.put("categoryName", m.getCategoryName());
        }
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, eventCode, payload);
    }

    @Override
    public List<MaterialCategoryVO> getCategoryTree(CategoryQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterialCategory> queryWrapper = buildQueryWrapper(queryDTO);
        List<InventoryMaterialCategory> categories = materialCategoryMapper
                .selectList(queryWrapper.orderByAsc(InventoryMaterialCategory::getSortOrder));

        // 构建分类映射
        Map<Long, MaterialCategoryVO> categoryMap = new HashMap<>();
        List<MaterialCategoryVO> rootCategories = new ArrayList<>();

        // 第一步：创建所有VO对象
        for (InventoryMaterialCategory category : categories) {
            MaterialCategoryVO vo = getMaterialCategoryVO(category);
            categoryMap.put(category.getCategoryId(), vo);
        }

        // 第二步：构建树形结构
        for (InventoryMaterialCategory category : categories) {
            MaterialCategoryVO vo = categoryMap.get(category.getCategoryId());
            Long parentId = category.getParentId();

            if (parentId == null || parentId == 0L) {
                rootCategories.add(vo);
            } else {
                MaterialCategoryVO parentVo = categoryMap.get(parentId);
                if (parentVo != null) {
                    parentVo.getChildren().add(vo);
                }
            }
        }

        return rootCategories;
    }

    private static @NonNull MaterialCategoryVO getMaterialCategoryVO(InventoryMaterialCategory category) {
        MaterialCategoryVO vo = new MaterialCategoryVO();
        vo.setCategoryId(category.getCategoryId());
        vo.setCategoryCode(category.getCategoryCode());
        vo.setCategoryName(category.getCategoryName());
        vo.setParentId(category.getParentId());
        vo.setCategoryLevel(category.getCategoryLevel());
        vo.setSortOrder(category.getSortOrder());
        vo.setStatus(category.getStatus());
        vo.setRemark(category.getRemark());
        vo.setCreateTime(category.getCreateTime());
        vo.setUpdateTime(category.getUpdateTime());
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    @Override
    public boolean existsByCode(String categoryCode) {
        Integer count = materialCategoryMapper.countByCode(categoryCode);
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long categoryId, String status) {
        InventoryMaterialCategory category = materialCategoryMapper.selectById(categoryId);
        if (category == null) {
            log.error("物料分类不存在: categoryId={}", categoryId);
            return false;
        }

        category.setStatus(status);
        publishCategoryEvent("inventory.material_category.status_updated", categoryId, null);
        return materialCategoryMapper.updateById(category) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWithCheck(Long categoryId) {
        // 检查是否有子分类
        List<InventoryMaterialCategory> children = materialCategoryMapper.selectByParentId(categoryId);
        if (children != null && !children.isEmpty()) {
            log.error("无法删除分类，存在子分类: categoryId={}", categoryId);
            return false;
        }

        // 检查是否有关联的物料
        Long materialCount = materialMapper.selectCount(
                new LambdaQueryWrapper<InventoryMaterial>().eq(InventoryMaterial::getCategoryId, categoryId));
        if (materialCount != null && materialCount > 0) {
            log.error("分类下存在物料，无法删除: categoryId={}, materialCount={}", categoryId, materialCount);
            throw new RuntimeException("分类下存在 " + materialCount + " 个物料，无法删除");
        }

        // 删除前先取到实体：删除事件要带业务编码，删完就查不到了
        // （2026-09-21 dev-20260921-023 修复：此前直接引用未加载的 category 变量）
        InventoryMaterialCategory category = materialCategoryMapper.selectById(categoryId);
        if (category == null) {
            log.error("物料分类不存在: categoryId={}", categoryId);
            return false;
        }

        boolean updated = materialCategoryMapper.deleteById(categoryId) > 0;
        if (updated) {
            publishCategoryEvent("inventory.material_category.deleted", category.getCategoryId(), category.getCategoryCode());
        }
        return updated;
    }

    @Override
    public CategoryTreeVO getTreeById(Long categoryId) {
        return null;
    }

    @Override
    public List<CategoryTreeVO> listTree(CategoryQueryDTO queryDTO) {
        return List.of();
    }
}
