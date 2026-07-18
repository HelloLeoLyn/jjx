package com.jjx.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.purchase.domain.dto.PurchaseSupplierDTO;
import com.jjx.purchase.domain.dto.SupplierEvaluationDTO;
import com.jjx.purchase.domain.dto.SupplierImportDTO;
import com.jjx.purchase.domain.vo.PurchaseSupplierQueryVO;
import com.jjx.purchase.domain.vo.PurchaseSupplierVO;
import com.jjx.purchase.service.IPurchaseSupplierService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 供应商控制器
 * 提供供应商的增删改查接口
 */
@Tag(name = "供应商管理")
@RestController
@RequestMapping("/purchase/supplier")
@RequiredArgsConstructor
public class PurchaseSupplierController extends BaseController {

    private final IPurchaseSupplierService supplierService;

    /**
     * 获取供应商列表
     */
    @Operation(summary = "获取供应商列表")
    @SaCheckPermission("purchase:supplier:view")
    @GetMapping("/list")
    public Result<?> list(PurchaseSupplierQueryVO queryVO) {
        return Result.success(supplierService.selectSupplierList(queryVO));
    }

    /**
     * 获取供应商详细信息
     */
    @Operation(summary = "获取供应商详细信息")
    @SaCheckPermission("purchase:supplier:view")
    @GetMapping(value = "/{supplierId}")
    public Result<PurchaseSupplierVO> getInfo(@PathVariable Long supplierId) {
        return Result.success(supplierService.selectSupplierById(supplierId));
    }

    /**
     * 新增供应商
     */
    @Operation(summary = "新增供应商")
    @Log(module = "供应商管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("purchase:supplier:add")
    @PostMapping
    public Result<Void> add(@Validated @RequestBody PurchaseSupplierDTO supplierDTO) {
        return toAjax(supplierService.insertSupplier(supplierDTO));
    }

    /**
     * 修改供应商
     */
    @Operation(summary = "修改供应商")
    @Log(module = "供应商管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:supplier:edit")
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody PurchaseSupplierDTO supplierDTO) {
        return toAjax(supplierService.updateSupplier(supplierDTO));
    }

    /**
     * 删除供应商
     */
    @Operation(summary = "删除供应商")
    @Log(module = "供应商管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("purchase:supplier:delete")
    @DeleteMapping("/{supplierIds}")
    public Result<Void> remove(@PathVariable Long[] supplierIds) {
        return toAjax(supplierService.deleteSupplierByIds(supplierIds));
    }

    /**
     * 导出供应商列表
     */
    @Operation(summary = "导出供应商列表")
    @SaCheckPermission("purchase:supplier:export")
    @GetMapping("/export")
    public Result<String> export(PurchaseSupplierQueryVO queryVO) {
        String filePath = supplierService.exportSupplierList(queryVO);
        return Result.success(filePath);
    }

    /**
     * 更新供应商状态
     */
    @Operation(summary = "更新供应商状态")
    @Log(module = "供应商管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:supplier:edit")
    @PutMapping("/status/{supplierId}")
    public Result<Void> changeStatus(@PathVariable Long supplierId,
                                     @RequestParam Integer status) {
        if (!StatusEnum.isValid(status)) {
            throw new BusinessException("状态值不正确，必须是" + StatusEnum.NORMAL.getCode() + "（正常）或" + StatusEnum.DISABLE.getCode() + "（停用）");
        }
        return toAjax(supplierService.updateSupplierStatus(supplierId, status));
    }

    /**
     * 更新供应商评估信息
     */
    @Operation(summary = "更新供应商评估信息")
    @Log(module = "供应商管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:supplier:edit")
    @PutMapping("/evaluation/{supplierId}")
    public Result<Void> updateEvaluation(@Validated @RequestBody SupplierEvaluationDTO evaluationDTO) {
        supplierService.updateSupplierEvaluation(evaluationDTO);
        return Result.success();
    }

    /**
     * 根据供应商类型查询供应商列表
     */
    @Operation(summary = "根据供应商类型查询供应商列表")
    @SaCheckPermission("purchase:supplier:view")
    @GetMapping("/type/{supplierType}")
    public Result<List<PurchaseSupplierVO>> getSuppliersByType(@PathVariable String supplierType) {
        return Result.success(supplierService.selectSuppliersByType(supplierType));
    }

    /**
     * 查询活跃供应商列表
     */
    @Operation(summary = "查询活跃供应商列表")
    @SaCheckPermission("purchase:supplier:view")
    @GetMapping("/active")
    public Result<List<PurchaseSupplierVO>> getActiveSuppliers() {
        return Result.success(supplierService.selectActiveSuppliers());
    }

    /**
     * 查询优质供应商列表
     */
    @Operation(summary = "查询优质供应商列表")
    @SaCheckPermission("purchase:supplier:view")
    @GetMapping("/high-quality")
    public Result<List<PurchaseSupplierVO>> getHighQualitySuppliers(@RequestParam(defaultValue = "80.0") Double minScore) {
        return Result.success(supplierService.selectHighQualitySuppliers(minScore));
    }

    /**
     * 检查供应商编码是否唯一
     */
    @Operation(summary = "检查供应商编码是否唯一")
    @SaCheckPermission("purchase:supplier:view")
    @GetMapping("/check-supplier-code-unique")
    public Result<Boolean> checkSupplierCodeUnique(@RequestParam String supplierCode) {
        return Result.success(supplierService.checkSupplierCodeUnique(supplierCode));
    }

    /**
     * 检查供应商名称是否唯一
     */
    @Operation(summary = "检查供应商名称是否唯一")
    @SaCheckPermission("purchase:supplier:view")
    @GetMapping("/check-supplier-name-unique")
    public Result<Boolean> checkSupplierNameUnique(@RequestParam String supplierName) {
        return Result.success(supplierService.checkSupplierNameUnique(supplierName));
    }

    /**
     * 获取供应商统计信息
     */
    @Operation(summary = "获取供应商统计信息")
    @SaCheckPermission("purchase:supplier:view")
    @GetMapping("/statistics")
    public Result<Object> statistics() {
        return Result.success(supplierService.getSupplierStatistics());
    }

    /**
     * 导入供应商数据
     */
    @Operation(summary = "导入供应商数据")
    @Log(module = "供应商管理", businessType = BusinessType.IMPORT)
    @SaCheckPermission("purchase:supplier:import")
    @PostMapping("/import")
    public Result<String> importSuppliers(MultipartFile file) throws Exception {
        List<SupplierImportDTO> importList = ExcelUtils.importExcel(file, SupplierImportDTO.class);
        String operName = getUsername();
        String message = supplierService.importSuppliers(importList, operName);
        return Result.success(message);
    }

    /**
     * 下载导入模板
     */
    @Operation(summary = "下载导入模板")
    @SaCheckPermission("purchase:supplier:import")
    @GetMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtils.downloadTemplate(response, SupplierImportDTO.class, "供应商导入模板");
    }
}
