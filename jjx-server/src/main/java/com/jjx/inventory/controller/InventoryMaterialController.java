package com.jjx.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;

import com.jjx.common.exception.BusinessException;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.dto.imports.MaterialImportDTO;
import com.jjx.inventory.dto.query.MaterialCheckDTO;
import com.jjx.inventory.dto.query.MaterialQueryDTO;
import com.jjx.inventory.dto.save.MaterialSaveDTO;
import com.jjx.inventory.dto.update.MaterialUpdateDTO;
import com.jjx.inventory.dto.vo.MaterialVO;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/code")
    public Result<String> code() {
        return Result.success(materialService.generateCode());
    }

    /**
     * 获取物料详情
     */
    @GetMapping("/{id}")
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
    @Log(module = "物料管理", businessType = BusinessType.INSERT, bizType = "'material'", bizId = "#dto.materialId")
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
        materialService.create(material);
        return Result.success();
    }

    /**
     * 修改物料
     */
    @PutMapping
    @Log(module = "物料管理", businessType = BusinessType.UPDATE, bizType = "'material'", bizId = "#dto.materialId")
    @SaCheckPermission("inventory:material:edit")
    public Result<Void> update(@RequestBody MaterialUpdateDTO dto) {
        if (dto.getMaterialId() == null) {
            throw new BusinessException("物料ID不能为空");
        }

        // DTO转换为Entity
        InventoryMaterial material = new InventoryMaterial();
        BeanUtils.copyProperties(dto, material);

        // 调用Service更新
        materialService.update(material);
        return Result.success();
    }

    /**
     * 删除物料
     */
    @DeleteMapping("/{id}")
    @Log(module = "物料管理", businessType = BusinessType.DELETE, bizType = "'material'", bizId = "#id")
    @SaCheckPermission("inventory:material:delete")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.deleteWithCheck(id);
        return Result.success();
    }

    /**
     * 更新物料状态
     */
    @PutMapping("/{id}/status")
    @Log(module = "物料管理", businessType = BusinessType.UPDATE, bizType = "'material'", bizId = "#id")
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
    @Log(module = "物料管理", businessType = BusinessType.UPDATE, bizType = "'material'", bizId = "#ids[0]")
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
     * 导入物料数据
     */
    @PostMapping("/import")
    @Log(module = "物料管理", businessType = BusinessType.IMPORT, bizType = "'material'", bizId = "'batch'")
    @SaCheckPermission("inventory:material:add")
    public Result<String> importMaterial(MultipartFile file) {
        List<MaterialImportDTO> importList = ExcelUtils.importExcel(file, MaterialImportDTO.class);
        String operName = getUsername();
        String message = materialService.importMaterial(importList, operName);
        return Result.success(message);
    }

    /**
     * 下载物料导入模板
     */
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtils.downloadTemplate(response, MaterialImportDTO.class, "物料导入模板");
    }


}
