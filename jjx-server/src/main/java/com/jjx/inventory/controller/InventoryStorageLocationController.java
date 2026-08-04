package com.jjx.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.inventory.domain.InventoryStorageLocation;
import com.jjx.inventory.dto.imports.StorageLocationImportDTO;
import com.jjx.inventory.dto.query.StorageLocationQueryDTO;
import com.jjx.inventory.dto.save.StorageLocationSaveDTO;
import com.jjx.inventory.dto.update.StorageLocationUpdateDTO;
import com.jjx.inventory.dto.vo.StorageLocationVO;
import com.jjx.inventory.service.InventoryStorageLocationService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 库位管理Controller
 */
@Slf4j
@RestController
@RequestMapping("/inventory/storage-location")
@RequiredArgsConstructor
public class InventoryStorageLocationController extends BaseController {

    private final InventoryStorageLocationService storageLocationService;

    /**
     * 分页查询库位列表
     */
    @GetMapping("/page")
    @SaCheckPermission("inventory:storage-location:view")
    public Result<PageResult<StorageLocationVO>> page(StorageLocationQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<InventoryStorageLocation> wrapper = buildQueryWrapper(queryDTO);

        // 构建分页参数
        Page<InventoryStorageLocation> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());

        // 调用Service分页查询
        IPage<InventoryStorageLocation> result = storageLocationService.page(page, wrapper);

        // 转换为VO列表
        List<StorageLocationVO> voList = convertToVOList(result.getRecords());

        // 返回分页数据
        return Result.success(PageResult.build(voList, result.getTotal()));
    }

    /**
     * 查询指定仓库下的库位列表
     */
    @GetMapping("/warehouse/{warehouseId}")
    @SaCheckPermission("inventory:storage-location:view")
    public Result<List<StorageLocationVO>> getByWarehouseId(@PathVariable Long warehouseId) {
        List<InventoryStorageLocation> locations = storageLocationService.getByWarehouseId(warehouseId);
        List<StorageLocationVO> voList = convertToVOList(locations);
        return Result.success(voList);
    }

    /**
     * 获取库位详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("inventory:storage-location:view")
    public Result<StorageLocationVO> getById(@PathVariable Long id) {
        InventoryStorageLocation location = storageLocationService.getById(id);
        if (location == null) {
            throw new BusinessException("库位不存在");
        }

        StorageLocationVO vo = convertToVO(location);
        return Result.success(vo);
    }

    /**
     * 新增库位
     */
    @PostMapping
    @Log(module = "库位管理", businessType = BusinessType.INSERT, bizType = "'storage_location'", bizId = "#dto.locationId")
    @SaCheckPermission("inventory:storage-location:add")
    public Result<Void> add(@RequestBody StorageLocationSaveDTO dto) {
        // 检查库位编码是否已存在
        if (storageLocationService.existsByCode(dto.getLocationCode())) {
            throw new BusinessException("库位编码已存在");
        }

        // DTO转换为Entity
        InventoryStorageLocation location = new InventoryStorageLocation();
        BeanUtils.copyProperties(dto, location);

        // 初始化已使用容量为0
        location.setUsedCapacity(new BigDecimal("0"));

        // 调用Service创建
        storageLocationService.save(location);
        return Result.success();
    }

    /**
     * 修改库位
     */
    @PutMapping
    @Log(module = "库位管理", businessType = BusinessType.UPDATE, bizType = "'storage_location'", bizId = "#dto.locationId")
    @SaCheckPermission("inventory:storage-location:edit")
    public Result<Void> update(@RequestBody StorageLocationUpdateDTO dto) {
        if (dto.getLocationId() == null) {
            throw new BusinessException("库位ID不能为空");
        }

        // 检查库位是否存在
        InventoryStorageLocation existingLocation = storageLocationService.getById(dto.getLocationId());
        if (existingLocation == null) {
            throw new BusinessException("库位不存在");
        }

        // DTO转换为Entity
        InventoryStorageLocation location = new InventoryStorageLocation();
        BeanUtils.copyProperties(dto, location);

        // 保留已使用容量
        location.setUsedCapacity(existingLocation.getUsedCapacity());

        // 调用Service更新
        storageLocationService.updateById(location);
        return Result.success();
    }

    /**
     * 删除库位
     */
    @DeleteMapping("/{id}")
    @Log(module = "库位管理", businessType = BusinessType.DELETE, bizType = "'storage_location'", bizId = "#id")
    @SaCheckPermission("inventory:storage-location:delete")
    public Result<Void> delete(@PathVariable Long id) {
        storageLocationService.deleteWithCheck(id);
        return Result.success();
    }

    /**
     * 更新库位状态
     */
    @PutMapping("/{id}/status")
    @Log(module = "库位管理", businessType = BusinessType.UPDATE, bizType = "'storage_location'", bizId = "#id")
    @SaCheckPermission("inventory:storage-location:edit")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        // 验证状态值
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new BusinessException("状态值无效，只能为0（启用）或1（停用）");
        }

        // 调用Service更新状态
        storageLocationService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 批量更新库位状态
     */
    @PutMapping("/batch-status")
    @Log(module = "库位管理", businessType = BusinessType.UPDATE, bizType = "'storage_location'", bizId = "#ids[0]")
    @SaCheckPermission("inventory:storage-location:edit")
    public Result<Void> batchUpdateStatus(@RequestParam List<Long> ids, @RequestParam String status) {
        // 验证状态值
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new BusinessException("状态值无效，只能为0（启用）或1（停用）");
        }

        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要更新的库位");
        }

        // 批量更新状态
        for (Long id : ids) {
            storageLocationService.updateStatus(id, status);
        }
        return Result.success();
    }

    /**
     * 检查库位编码是否重复
     */
    @GetMapping("/check-code")
    @SaCheckPermission("inventory:storage-location:view")
    public Result<Boolean> checkCode(@RequestParam String locationCode) {
        boolean exists = storageLocationService.existsByCode(locationCode);
        return Result.success(!exists);
    }

    /**
     * 获取库位下拉选项
     */
    @GetMapping("/options")
    @SaCheckPermission("inventory:storage-location:view")
    public Result<List<Map<String, Object>>> getOptions(@RequestParam(required = false) Long warehouseId) {
        List<Map<String, Object>> options = storageLocationService.getOptions(warehouseId);
        return Result.success(options);
    }

    /**
     * 检查库位容量是否充足
     */
    @GetMapping("/check-capacity")
    @SaCheckPermission("inventory:storage-location:view")
    public Result<Boolean> checkCapacity(@RequestParam Long locationId, @RequestParam BigDecimal quantity) {
        boolean hasCapacity = storageLocationService.hasCapacity(locationId, quantity);
        return Result.success(hasCapacity);
    }

    /**
     * 推荐入库库位
     */
    @GetMapping("/recommend")
    @SaCheckPermission("inventory:storage-location:view")
    public Result<StorageLocationVO> recommendLocation(@RequestParam Long warehouseId,
                                                       @RequestParam Long materialId,
                                                       @RequestParam BigDecimal quantity) {
        InventoryStorageLocation location = storageLocationService.recommendLocation(warehouseId, materialId, quantity);
        if (location == null) {
            throw new BusinessException("没有合适的库位");
        }

        StorageLocationVO vo = convertToVO(location);
        return Result.success(vo);
    }

    /**
     * 获取所有启用的库位
     */
    @GetMapping("/enabled")
    @SaCheckPermission("inventory:storage-location:view")
    public Result<List<StorageLocationVO>> getAllEnabled() {
        LambdaQueryWrapper<InventoryStorageLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryStorageLocation::getStatus, "0");
        wrapper.orderByAsc(InventoryStorageLocation::getSortOrder);

        List<InventoryStorageLocation> locations = storageLocationService.list(wrapper);
        List<StorageLocationVO> voList = convertToVOList(locations);
        return Result.success(voList);
    }

    /**
     * 根据库位类型获取库位
     */
    @GetMapping("/by-type")
    @SaCheckPermission("inventory:storage-location:view")
    public Result<List<StorageLocationVO>> getByType(@RequestParam String locationType) {
        LambdaQueryWrapper<InventoryStorageLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryStorageLocation::getLocationType, locationType);
        wrapper.eq(InventoryStorageLocation::getStatus, "0");
        wrapper.orderByAsc(InventoryStorageLocation::getSortOrder);

        List<InventoryStorageLocation> locations = storageLocationService.list(wrapper);
        List<StorageLocationVO> voList = convertToVOList(locations);
        return Result.success(voList);
    }

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<InventoryStorageLocation> buildQueryWrapper(StorageLocationQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryStorageLocation> wrapper = new LambdaQueryWrapper<>();

        // 查询条件
        if (queryDTO.getLocationId() != null) {
            wrapper.eq(InventoryStorageLocation::getLocationId, queryDTO.getLocationId());
        }
        if (queryDTO.getWarehouseId() != null) {
            wrapper.eq(InventoryStorageLocation::getWarehouseId, queryDTO.getWarehouseId());
        }
        if (queryDTO.getLocationCode() != null && !queryDTO.getLocationCode().isEmpty()) {
            wrapper.like(InventoryStorageLocation::getLocationCode, queryDTO.getLocationCode());
        }
        if (queryDTO.getLocationName() != null && !queryDTO.getLocationName().isEmpty()) {
            wrapper.like(InventoryStorageLocation::getLocationName, queryDTO.getLocationName());
        }
        if (queryDTO.getLocationType() != null && !queryDTO.getLocationType().isEmpty()) {
            wrapper.eq(InventoryStorageLocation::getLocationType, queryDTO.getLocationType());
        }
        if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
            wrapper.eq(InventoryStorageLocation::getStatus, queryDTO.getStatus());
        }

        // 排序
        wrapper.orderByDesc(InventoryStorageLocation::getCreateTime);

        return wrapper;
    }

    /**
     * 将StorageLocationQueryDTO转换为Map参数
     */
    private static Map<String, Object> convertQueryDTOToMap(StorageLocationQueryDTO queryDTO) {
        Map<String, Object> params = new HashMap<>();

        // 分页参数
        params.put("pageNum", queryDTO.getCurrent());
        params.put("pageSize", queryDTO.getSize());

        // 查询条件
        if (queryDTO.getLocationId() != null) {
            params.put("locationId", queryDTO.getLocationId());
        }
        if (queryDTO.getWarehouseId() != null) {
            params.put("warehouseId", queryDTO.getWarehouseId());
        }
        if (queryDTO.getLocationCode() != null && !queryDTO.getLocationCode().isEmpty()) {
            params.put("locationCode", queryDTO.getLocationCode());
        }
        if (queryDTO.getLocationName() != null && !queryDTO.getLocationName().isEmpty()) {
            params.put("locationName", queryDTO.getLocationName());
        }
        if (queryDTO.getLocationType() != null && !queryDTO.getLocationType().isEmpty()) {
            params.put("locationType", queryDTO.getLocationType());
        }
        if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
            params.put("status", queryDTO.getStatus());
        }
        if (queryDTO.getCreateTimeStart() != null && !queryDTO.getCreateTimeStart().isEmpty()) {
            params.put("createTimeStart", queryDTO.getCreateTimeStart());
        }
        if (queryDTO.getCreateTimeEnd() != null && !queryDTO.getCreateTimeEnd().isEmpty()) {
            params.put("createTimeEnd", queryDTO.getCreateTimeEnd());
        }

        return params;
    }

    /**
     * 将InventoryStorageLocation列表转换为StorageLocationVO列表
     */
    private static List<StorageLocationVO> convertToVOList(List<InventoryStorageLocation> locations) {
        return locations.stream()
                .map(InventoryStorageLocationController::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 将InventoryStorageLocation转换为StorageLocationVO
     */
    private static StorageLocationVO convertToVO(InventoryStorageLocation location) {
        if (location == null) {
            return null;
        }

        StorageLocationVO vo = new StorageLocationVO();
        BeanUtils.copyProperties(location, vo);

        // TODO: 这里需要查询仓库信息并设置warehouseCode和warehouseName
        // 目前先设置为null，后续可以添加仓库服务调用

        return vo;
    }

    /**
     * 导入库位数据
     */
    @PostMapping("/import")
    @Log(module = "库位管理", businessType = BusinessType.IMPORT, bizType = "'storage_location'")
    @SaCheckPermission("inventory:storage-location:add")
    public Result<String> importStorageLocation(@RequestBody List<StorageLocationImportDTO> importList, @RequestParam Long warehouseId) {
        String operName = getUsername();
        String message = storageLocationService.importStorageLocation(importList, warehouseId, operName);
        return Result.success(message);
    }

    /**
     * 下载库位导入模板
     */
    @GetMapping("/importTemplate")
    @SaCheckPermission("inventory:storage-location:add")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtils.downloadTemplate(response, StorageLocationImportDTO.class, "库位导入模板");
    }
}
