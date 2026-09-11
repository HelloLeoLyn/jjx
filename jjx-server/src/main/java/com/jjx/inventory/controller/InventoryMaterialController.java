package com.jjx.inventory.controller;

import com.jjx.common.constant.LogActions;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;

import com.jjx.common.exception.BusinessException;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.domain.InventoryWarehouse;
import com.jjx.inventory.dto.imports.MaterialImportDTO;
import com.jjx.inventory.dto.query.MaterialCheckDTO;
import com.jjx.inventory.dto.query.MaterialQueryDTO;
import com.jjx.inventory.dto.save.MaterialSaveDTO;
import com.jjx.inventory.dto.update.MaterialUpdateDTO;
import com.jjx.inventory.dto.vo.MaterialExportVO;
import com.jjx.inventory.dto.vo.MaterialVO;
import com.jjx.inventory.enums.MaterialEnums;
import com.jjx.inventory.service.InventoryMaterialCategoryService;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.inventory.service.InventoryWarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import com.jjx.system.domain.entity.SysTag;
import com.jjx.system.service.ISysTagService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料管理Controller
 */
@Slf4j
@RestController
@RequestMapping("/inventory/material")
@RequiredArgsConstructor
public class InventoryMaterialController extends BaseController {

    private final InventoryMaterialService materialService;

    private final InventoryMaterialCategoryService categoryService;

    private final InventoryWarehouseService warehouseService;

    private final ISysTagService tagService;

    /**
     * 获取物料总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(materialService.count());
    }

    /**
     * 分页查询物料列表
     */
    @GetMapping("/page")
    public Result<PageResult<MaterialVO>> page(MaterialQueryDTO queryDTO) {
        return Result.success(materialService.pageQuery(queryDTO));
    }

    @GetMapping("/search")
    public Result<PageResult<MaterialVO>> search(MaterialQueryDTO queryDTO) {
        return Result.success(materialService.search(queryDTO));
    }

    @GetMapping("/list")
    public Result<List<MaterialVO>> list(MaterialQueryDTO queryDTO) {
        return Result.success(materialService.selectList(queryDTO));
    }

    @GetMapping("/tags")
    @SaCheckPermission("inventory:material:view")
    public Result<List<SysTag>> tags() {
        return Result.success(tagService.listTags("material_attribute", null, StatusEnum.NORMAL.getCode()));
    }

    @GetMapping("/code")
    public Result<String> code(@RequestParam(required = false) String materialType) {
        return Result.success(materialService.generateCode(materialType));
    }

    /**
     * 获取物料详情
     */
    @GetMapping("/{id:\\d+}")
    @SaCheckPermission("inventory:material:view")
    public Result<MaterialVO> getById(@PathVariable Long id) {
        MaterialVO material = materialService.getDetailById(id);
        if (material == null) {
            throw new BusinessException("物料不存在");
        }
        return Result.success(material);
    }

    /**
     * 新增物料
     */
    @PostMapping
    @Log(module = "物料管理", businessType = BusinessType.INSERT, bizType = "'material'", bizId = "#dto.materialId", action = LogActions.MATERIAL_CREATE)
    @SaCheckPermission("inventory:material:add")
    public Result<Void> add(@RequestBody MaterialSaveDTO dto) {
        // 检查物料编码是否已存在
        if (materialService.existsByCode(dto.getMaterialCode())) {
            throw new BusinessException("物料编码已存在");
        }

        // DTO转换为Entity
        InventoryMaterial material = new InventoryMaterial();
        BeanUtils.copyProperties(dto, material);

        // 调用Service创建
        materialService.create(material, dto.getTagIds(), getUsername());
        return Result.success();
    }

    /**
     * 修改物料
     */
    @PutMapping
    @Log(module = "物料管理", businessType = BusinessType.UPDATE, bizType = "'material'", bizId = "#dto.materialId", action = LogActions.MATERIAL_EDIT)
    @SaCheckPermission("inventory:material:edit")
    public Result<Void> update(@RequestBody MaterialUpdateDTO dto) {
        if (dto.getMaterialId() == null) {
            throw new BusinessException("物料ID不能为空");
        }

        // DTO转换为Entity
        InventoryMaterial material = new InventoryMaterial();
        BeanUtils.copyProperties(dto, material);

        // 调用Service更新
        materialService.update(material, dto.getTagIds(), getUsername());
        return Result.success();
    }

    /**
     * 删除物料
     */
    @DeleteMapping("/{id}")
    @Log(module = "物料管理", businessType = BusinessType.DELETE, bizType = "'material'", bizId = "#id", action = LogActions.MATERIAL_DELETE)
    @SaCheckPermission("inventory:material:delete")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.deleteWithCheck(id);
        return Result.success();
    }

    /**
     * 更新物料状态
     */
    @PutMapping("/{id}/status")
    @Log(module = "物料管理", businessType = BusinessType.UPDATE, bizType = "'material'", bizId = "#id", action = LogActions.MATERIAL_STATUS)
    @SaCheckPermission("inventory:material:edit")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        // 调用Service更新状态
        materialService.batchUpdateStatus(List.of(id), status);
        return Result.success();
    }

    /**
     * 批量更新物料状态
     */
    @PutMapping("/batch-status")
    @Log(module = "物料管理", businessType = BusinessType.UPDATE, bizType = "'material'", bizId = "#ids[0]", action = LogActions.MATERIAL_BATCH_STATUS)
    @SaCheckPermission("inventory:material:edit")
    public Result<Void> batchUpdateStatus(@RequestParam List<Long> ids, @RequestParam Integer status) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要更新的物料");
        }

        // 调用Service批量更新状态
        materialService.batchUpdateStatus(ids, status);
        return Result.success();
    }

    /**
     * 检查物料编码是否重复
     */
    @GetMapping("/check-code")
    @SaCheckPermission("inventory:material:view")
    public Result<Boolean> checkCode(@RequestParam String materialCode) {
        boolean exists = materialService.existsByCode(materialCode);
        return Result.success(!exists);
    }

    /**
     * 校验物料是否存在（根据名称、规格等条件）
     * 用于导入时校验物料是否已建档
     */
    @PostMapping("/check")
    @SaCheckPermission("inventory:material:view")
    public Result<MaterialVO> check(@RequestBody MaterialCheckDTO checkDTO) {
        MaterialVO material = materialService.checkMaterial(checkDTO);
        return Result.success(material);
    }

    /**
     * 查询物料简单列表（用于下拉框）
     */
    @GetMapping("/options")
    @SaCheckPermission("inventory:material:view")
    public Result<List<Map<String, Object>>> options(@RequestParam(required = false) String keyword) {
        List<Map<String, Object>> options = materialService.getOptions(keyword);
        return Result.success(options);
    }
    /**
     * 导出物料列表Excel（dev-20260911-001）
     * 权限口径与 /list 一致：inventory:material:view 可见即可导出
     */
    @Operation(summary = "导出物料列表Excel")
    @GetMapping("/export")
    @SaCheckPermission("inventory:material:view")
    public void export(MaterialQueryDTO queryDTO, HttpServletResponse response) {
        List<InventoryMaterial> list = materialService.selectEntities(queryDTO);

        // 列表VO未做关联字段填充，导出时补齐分类名/默认仓库名
        Map<Long, String> categoryMap = new HashMap<>();
        for (InventoryMaterialCategory category : categoryService.list()) {
            categoryMap.put(category.getCategoryId(), category.getCategoryName());
        }
        Map<Long, String> warehouseMap = new HashMap<>();
        for (InventoryWarehouse warehouse : warehouseService.list()) {
            warehouseMap.put(warehouse.getWarehouseId(), warehouse.getWarehouseName());
        }

        List<MaterialExportVO> rows = new ArrayList<>();
        for (InventoryMaterial entity : list) {
            MaterialExportVO row = new MaterialExportVO();
            BeanUtils.copyProperties(entity, row);
            MaterialEnums.Type type = MaterialEnums.Type.fromValue(entity.getMaterialType());
            row.setMaterialTypeDesc(type == null ? entity.getMaterialType() : type.getLabel());
            row.setCategoryName(
                    entity.getCategoryId() == null ? null : categoryMap.get(entity.getCategoryId()));
            row.setDefaultWarehouseName(entity.getDefaultWarehouseId() == null ? null
                    : warehouseMap.get(entity.getDefaultWarehouseId()));
            row.setStatusDesc(materialStatusText(entity.getStatus()));
            row.setBatchControlDesc(Boolean.TRUE.equals(entity.getBatchControl()) ? "是" : "否");
            row.setCreateTime(formatDateTime(entity.getCreateTime()));
            rows.add(row);
        }

        ExcelUtils.export(response, rows, MaterialExportVO.class, "物料列表");
    }

    private static final java.time.format.DateTimeFormatter EXPORT_DATE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 导出用时间格式化
     */
    private static String formatDateTime(java.time.LocalDateTime value) {
        return value == null ? "" : EXPORT_DATE_TIME.format(value);
    }

    /**
     * 物料状态文本（inventory_material.status：1启用 0停用 2废弃）
     */
    private static String materialStatusText(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 1 -> "启用";
            case 0 -> "停用";
            case 2 -> "废弃";
            default -> String.valueOf(status);
        };
    }

    /**
     * 导入物料数据
     */
    @PostMapping("/import")
    @Log(module = "物料管理", businessType = BusinessType.IMPORT, bizType = "'material'", bizId = "'batch'", action = LogActions.MATERIAL_IMPORT)
    @SaCheckPermission("inventory:material:add")
    public Result<com.jjx.inventory.dto.vo.MaterialImportResultVO> importMaterial(MultipartFile file) {
        List<MaterialImportDTO> importList = ExcelUtils.importExcel(file, MaterialImportDTO.class);
        String operName = getUsername();
        return Result.success(materialService.importMaterial(importList, operName));
    }

    /**
     * 下载物料导入模板
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtils.downloadTemplate(response, MaterialImportDTO.class, "物料导入模板");
    }


}
