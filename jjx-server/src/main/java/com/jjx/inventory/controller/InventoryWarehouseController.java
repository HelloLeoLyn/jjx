package com.jjx.inventory.controller;

import com.jjx.common.constant.LogActions;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.inventory.domain.InventoryWarehouse;
import com.jjx.inventory.dto.query.WarehouseQueryDTO;
import com.jjx.inventory.dto.save.WarehouseSaveDTO;
import com.jjx.inventory.dto.update.WarehouseUpdateDTO;
import com.jjx.inventory.dto.vo.WarehouseVO;
import com.jjx.inventory.service.InventoryWarehouseService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仓库管理Controller
 */
@Slf4j
@RestController
@RequestMapping("/inventory/warehouse")
@RequiredArgsConstructor
public class InventoryWarehouseController extends BaseController {

    private final InventoryWarehouseService warehouseService;

    /**
     * 分页查询仓库列表
     */
    @GetMapping("/page")
    @SaCheckPermission("inventory:warehouse:view")
    public Result<PageResult<WarehouseVO>> page(WarehouseQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<InventoryWarehouse> wrapper = buildQueryWrapper(queryDTO);

        // 构建分页参数
        Page<InventoryWarehouse> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());

        // 调用Service分页查询
        IPage<InventoryWarehouse> result = warehouseService.page(page, wrapper);

        // 转换为VO列表
        List<WarehouseVO> voList = convertToVOList(result.getRecords());

        // 返回分页数据
        return Result.success(PageResult.of(result, voList));
    }

    /**
     * 查询仓库简单列表（用于下拉框）
     */
    @GetMapping("/list")
    @SaCheckPermission("inventory:warehouse:view")
    public Result<List<WarehouseVO>> list() {
        List<InventoryWarehouse> options = warehouseService.list();
        return Result.success(convertToVOList(options));
    }

    /**
     * 获取仓库详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("inventory:warehouse:view")
    public Result<WarehouseVO> getById(@PathVariable Long id) {
        InventoryWarehouse warehouse = warehouseService.getById(id);
        if (warehouse == null) {
            throw new BusinessException("仓库不存在");
        }

        WarehouseVO vo = convertToVO(warehouse);
        return Result.success(vo);
    }

    /**
     * 新增仓库
     */
    @PostMapping
    @Log(module = "仓库管理", businessType = BusinessType.INSERT, bizType = "'warehouse'", bizId = "#dto.warehouseId", action = LogActions.WAREHOUSE_CREATE)
    @SaCheckPermission("inventory:warehouse:add")
    public Result<Void> add(@RequestBody WarehouseSaveDTO dto) {
        // 检查仓库编码是否已存在
        if (warehouseService.existsByCode(dto.getWarehouseCode())) {
            throw new BusinessException("仓库编码已存在");
        }

        // DTO转换为Entity
        InventoryWarehouse warehouse = new InventoryWarehouse();
        BeanUtils.copyProperties(dto, warehouse);

        // 调用Service创建
        warehouseService.save(warehouse);
        return Result.success();
    }

    /**
     * 修改仓库
     */
    @PutMapping
    @Log(module = "仓库管理", businessType = BusinessType.UPDATE, bizType = "'warehouse'", bizId = "#dto.warehouseId", action = LogActions.WAREHOUSE_EDIT)
    @SaCheckPermission("inventory:warehouse:edit")
    public Result<Void> update(@RequestBody WarehouseUpdateDTO dto) {
        if (dto.getWarehouseId() == null) {
            throw new BusinessException("仓库ID不能为空");
        }

        // 检查仓库是否存在
        InventoryWarehouse existingWarehouse = warehouseService.getById(dto.getWarehouseId());
        if (existingWarehouse == null) {
            throw new BusinessException("仓库不存在");
        }

        // DTO转换为Entity
        InventoryWarehouse warehouse = new InventoryWarehouse();
        BeanUtils.copyProperties(dto, warehouse);

        // 调用Service更新
        warehouseService.updateById(warehouse);
        return Result.success();
    }

    /**
     * 删除仓库
     */
    @DeleteMapping("/{id}")
    @Log(module = "仓库管理", businessType = BusinessType.DELETE, bizType = "'warehouse'", bizId = "#id", action = LogActions.WAREHOUSE_DELETE)
    @SaCheckPermission("inventory:warehouse:delete")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.deleteWithCheck(id);
        return Result.success();
    }

    /**
     * 更新仓库状态
     */
    @PutMapping("/{id}/status")
    @Log(module = "仓库管理", businessType = BusinessType.UPDATE, bizType = "'warehouse'", bizId = "#id", action = LogActions.WAREHOUSE_STATUS)
    @SaCheckPermission("inventory:warehouse:edit")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        // 验证状态值
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new BusinessException("状态值无效，只能为0（启用）或1（停用）");
        }

        // 调用Service更新状态
        warehouseService.updateStatus(id, status);
        return Result.success();
    }

    /**
     * 批量更新仓库状态
     */
    @PutMapping("/batch-status")
    @Log(module = "仓库管理", businessType = BusinessType.UPDATE, bizType = "'warehouse'", bizId = "#ids[0]", action = LogActions.WAREHOUSE_BATCH_STATUS)
    @SaCheckPermission("inventory:warehouse:edit")
    public Result<Void> batchUpdateStatus(@RequestParam List<Long> ids, @RequestParam String status) {
        // 验证状态值
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new BusinessException("状态值无效，只能为0（启用）或1（停用）");
        }

        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要更新的仓库");
        }

        // 批量更新状态
        for (Long id : ids) {
            warehouseService.updateStatus(id, status);
        }
        return Result.success();
    }

    /**
     * 检查仓库编码是否重复
     */
    @GetMapping("/check-code")
    @SaCheckPermission("inventory:warehouse:view")
    public Result<Boolean> checkCode(@RequestParam String warehouseCode) {
        boolean exists = warehouseService.existsByCode(warehouseCode);
        return Result.success(!exists);
    }

    /**
     * 获取所有启用的仓库
     */
    @GetMapping("/enabled")
    @SaCheckPermission("inventory:warehouse:view")
    public Result<List<WarehouseVO>> getAllEnabled() {
        List<InventoryWarehouse> warehouses = warehouseService.getAllEnabled();
        List<WarehouseVO> voList = convertToVOList(warehouses);
        return Result.success(voList);
    }

    /**
     * 根据仓库类型获取仓库
     */
    @GetMapping("/by-type")
    @SaCheckPermission("inventory:warehouse:view")
    public Result<List<WarehouseVO>> getByType(@RequestParam String warehouseType) {
        List<InventoryWarehouse> warehouses = warehouseService.getByType(warehouseType);
        List<WarehouseVO> voList = convertToVOList(warehouses);
        return Result.success(voList);
    }

    /**
     * 获取仓库下拉选项
     */
    @GetMapping("/options")
    @SaCheckPermission("inventory:warehouse:view")
    public Result<List<WarehouseVO>> getOptions() {
        List<InventoryWarehouse> options = warehouseService.getOptions();
        return Result.success(convertToVOList(options));
    }


    /**
     * 将InventoryWarehouse列表转换为WarehouseVO列表
     */
    private static List<WarehouseVO> convertToVOList(List<InventoryWarehouse> warehouses) {
        return warehouses.stream()
                .map(InventoryWarehouseController::convertToVO)
                .toList();
    }

    /**
     * 将InventoryWarehouse转换为WarehouseVO
     */
    private static WarehouseVO convertToVO(InventoryWarehouse warehouse) {
        if (warehouse == null) {
            return null;
        }

        WarehouseVO vo = new WarehouseVO();
        BeanUtils.copyProperties(warehouse, vo);

        return vo;
    }

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<InventoryWarehouse> buildQueryWrapper(WarehouseQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryWarehouse> wrapper = new LambdaQueryWrapper<>();

        // 查询条件
        if (queryDTO.getWarehouseId() != null) {
            wrapper.eq(InventoryWarehouse::getWarehouseId, queryDTO.getWarehouseId());
        }
        if (queryDTO.getWarehouseCode() != null && !queryDTO.getWarehouseCode().isEmpty()) {
            wrapper.like(InventoryWarehouse::getWarehouseCode, queryDTO.getWarehouseCode());
        }
        if (queryDTO.getWarehouseName() != null && !queryDTO.getWarehouseName().isEmpty()) {
            wrapper.like(InventoryWarehouse::getWarehouseName, queryDTO.getWarehouseName());
        }
        if (queryDTO.getWarehouseType() != null && !queryDTO.getWarehouseType().isEmpty()) {
            wrapper.eq(InventoryWarehouse::getWarehouseType, queryDTO.getWarehouseType());
        }
        if (queryDTO.getLocation() != null && !queryDTO.getLocation().isEmpty()) {
            wrapper.like(InventoryWarehouse::getLocation, queryDTO.getLocation());
        }
        if (queryDTO.getManager() != null && !queryDTO.getManager().isEmpty()) {
            wrapper.like(InventoryWarehouse::getManager, queryDTO.getManager());
        }
        if (queryDTO.getContactPhone() != null && !queryDTO.getContactPhone().isEmpty()) {
            wrapper.like(InventoryWarehouse::getContactPhone, queryDTO.getContactPhone());
        }
        if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
            wrapper.eq(InventoryWarehouse::getStatus, queryDTO.getStatus());
        }

        // 排序
        wrapper.orderByDesc(InventoryWarehouse::getCreateTime).orderByDesc(InventoryWarehouse::getWarehouseId);

        return wrapper;
    }
}
