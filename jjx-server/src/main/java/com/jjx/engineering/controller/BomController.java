package com.jjx.engineering.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.UpdateBomStatusDTO;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.domain.query.EngineeringBomQuery;
import com.jjx.product.domain.vo.EngineeringBomExportVO;
import com.jjx.product.domain.vo.EngineeringBomVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * BOM管理（engineering 统一入口）
 * 前端 api/product/bom.ts 全部走 /engineering/bom/*
 */
@RestController
@RequestMapping("/engineering/bom")
@RequiredArgsConstructor
public class BomController extends BaseController {

    private final IEngineeringBomService productBomService;
    private final EngineeringBomMapper productBomMapper;

    /**
     * 获取BOM列表
     */
    @GetMapping("/page")
    public Result<PageResult<EngineeringBomVO>> listPage(EngineeringBomQuery query) {
        return Result.success(productBomService.listPage(query));
    }

    /**
     * 导出BOM列表Excel
     */
    @GetMapping("/export")
    @Operation(summary = "导出BOM列表")
    @SaCheckPermission("engineering:bom:view")
    public void exportBom(EngineeringBomQuery query, HttpServletResponse response) {
        PageResult<EngineeringBomVO> page = productBomService.listPage(query);
        List<EngineeringBomExportVO> rows = new ArrayList<>();
        if (page != null && page.getRecords() != null) {
            for (EngineeringBomVO vo : page.getRecords()) {
                EngineeringBomExportVO row = new EngineeringBomExportVO();
                row.setBomCode(vo.getBomCode());
                row.setBomVersion(vo.getBomVersion());
                row.setProductCode(vo.getProductCode());
                row.setProductName(vo.getProductName());
                row.setBomTypeName(vo.getBomType());
                row.setApproveStatusName(approveStatusText(vo.getApproveStatus()));
                row.setIsCurrentName(Boolean.TRUE.equals(vo.getIsCurrent()) ? "是" : "否");
                row.setEffectiveDate(vo.getEffectiveDate());
                row.setExpiryDate(vo.getExpiryDate());
                row.setRemark(vo.getRemark());
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            throw new com.jjx.common.exception.BusinessException("导出数据为空");
        }
        ExcelUtils.export(response, rows, EngineeringBomExportVO.class, "BOM列表");
    }

    private String approveStatusText(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 1 -> "草稿";
            case 2 -> "待审批";
            case 3 -> "已批准";
            case 4 -> "已驳回";
            default -> String.valueOf(status);
        };
    }

    /**
     * 获取BOM详情
     */
    @GetMapping("/{bomId}")
    public Result<EngineeringBomVO> getInfo(@PathVariable Long bomId) {
        return Result.success(productBomService.getBomDetail(bomId));
    }

    /**
     * 获取BOM明细列表
     */
    @GetMapping("/items/{bomId}")
    public Result<List<EngineeringBomItem>> getBomItems(@PathVariable Long bomId) {
        return Result.success(productBomService.getBomItems(bomId));
    }

    /**
     * 获取产品已审批的BOM列表（用于产品新增/编辑时选择）
     */
    @GetMapping("/approved/{productId}")
    public Result<List<EngineeringBomVO>> getApprovedBomList(@PathVariable Long productId) {
        EngineeringBomQuery query = new EngineeringBomQuery();
        query.setProductId(productId);
        query.setApproveStatus(String.valueOf(ProductEnums.BomStatus.APPROVED.getValue()));
        return Result.success(productBomService.getBomList(query));
    }

    /**
     * 获取产品的默认BOM
     */
    @GetMapping("/default/{productId}")
    public Result<EngineeringBom> getDefaultBom(@PathVariable Long productId) {
        return Result.success(productBomService.getDefaultBomByProductId(productId));
    }

    /**
     * 新增BOM
     */
    @PostMapping
    @Log(module = "产品BOM管理", businessType = BusinessType.INSERT, bizType = "'bom'", bizId = "#dto.bomId")
    @SaCheckPermission("engineering:bom:add")
    public Result<Void> add(@Validated @RequestBody EngineeringBomDTO dto) {
        return productBomService.createBom(dto) ? Result.success() : Result.error();
    }

    /**
     * 修改BOM
     */
    @PutMapping
    @Log(module = "产品BOM管理", businessType = BusinessType.UPDATE, bizType = "'bom'", bizId = "#dto.bomId")
    @SaCheckPermission("engineering:bom:edit")
    public Result<Void> edit(@Validated @RequestBody EngineeringBomDTO dto) {
        return productBomService.updateBom(dto) ? Result.success() : Result.error();
    }

    /**
     * 删除BOM
     */
    @DeleteMapping("/{bomId}")
    @Log(module = "产品BOM管理", businessType = BusinessType.DELETE, bizType = "'bom'", bizId = "#bomId")
    @SaCheckPermission("engineering:bom:delete")
    public Result<Void> remove(@PathVariable Long bomId) {
        return productBomService.removeBomWithItems(bomId) ? Result.success() : Result.error();
    }

    /**
     * 提交BOM审批
     */
    @PutMapping("/submit/{bomId}")
    @Log(module = "产品BOM管理", businessType = BusinessType.UPDATE, bizType = "'bom'", bizId = "#bomId")
    @SaCheckPermission("engineering:bom:edit")
    public Result<Void> submit(@PathVariable Long bomId) {
        return productBomService.submitApprove(bomId) ? Result.success() : Result.error();
    }

    /**
     * 审批BOM
     */
    @PutMapping("/approve/{bomId}")
    @Log(module = "产品BOM管理", businessType = BusinessType.APPROVE, bizType = "'bom'", bizId = "#bomId")
    @SaCheckPermission("engineering:bom:approve")
    public Result<Void> approve(@PathVariable Long bomId, @Validated @RequestBody UpdateBomStatusDTO dto) {
        return productBomService.approve(dto) ? Result.success() : Result.error();
    }

    /**
     * 驳回BOM
     */
    @PutMapping("/reject/{bomId}")
    @Log(module = "产品BOM管理", businessType = BusinessType.APPROVE, bizType = "'bom'", bizId = "#bomId")
    @SaCheckPermission("engineering:bom:reject")
    public Result<Void> reject(@PathVariable Long bomId, @Validated @RequestBody UpdateBomStatusDTO dto) {
        return productBomService.reject(dto) ? Result.success() : Result.error();
    }

    /**
     * 设置默认BOM
     */
    @PutMapping("/setDefault/{bomId}")
    @Log(module = "产品BOM管理", businessType = BusinessType.UPDATE, bizType = "'bom'", bizId = "#bomId")
    @SaCheckPermission("engineering:bom:edit")
    public Result<Void> setDefault(@PathVariable Long bomId) {
        return productBomService.setDefaultBom(bomId) ? Result.success() : Result.error();
    }

    /**
     * 计算BOM成本
     */
    @PostMapping("/calculateCost/{bomId}")
    public Result<Void> calculateCost(@PathVariable Long bomId) {
        productBomService.calculateBomCost(bomId);
        return Result.success();
    }
}
