package com.jjx.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.constant.LogActions;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.inventory.dto.update.MaterialSpecValuesDTO;
import com.jjx.inventory.dto.vo.MaterialSpecAttrDefVO;
import com.jjx.inventory.dto.vo.MaterialSpecValuesVO;
import com.jjx.inventory.service.InventoryMaterialSpecService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物料规格属性Controller（dev-20261023-022）。
 * <p>
 * 规格按品类属性填写、组合成文本；路径挂在 /inventory/material/spec 下，
 * 不复用主数据 save 入口，避免与并行任务改同一段代码。
 */
@Slf4j
@RestController
@RequestMapping("/inventory/material/spec")
@RequiredArgsConstructor
public class InventoryMaterialSpecController extends BaseController {

    private final InventoryMaterialSpecService specService;

    /** 按品类取规格属性定义（表单渲染输入项）。 */
    @Operation(summary = "品类规格属性定义")
    @GetMapping("/attr-defs")
    @SaCheckPermission(value = {"inventory:material:view", "engineering:bom:add", "engineering:bom:edit"}, mode = SaMode.OR)
    public Result<List<MaterialSpecAttrDefVO>> attrDefs(@RequestParam(required = false) String categoryCode) {
        return Result.success(specService.listDefs(categoryCode));
    }

    /** 取物料当前规格属性值（编辑回显）。 */
    @Operation(summary = "物料规格属性值")
    @GetMapping("/values")
    @SaCheckPermission(value = {"inventory:material:view", "engineering:bom:add", "engineering:bom:edit"}, mode = SaMode.OR)
    public Result<MaterialSpecValuesVO> values(@RequestParam Long materialId) {
        return Result.success(specService.getByMaterialId(materialId));
    }

    /** 保存物料规格属性并重算规格文本。 */
    @Operation(summary = "保存物料规格属性")
    @PutMapping("/values")
    @Log(module = "物料管理", businessType = BusinessType.UPDATE, bizType = "'material'", bizId = "#dto.materialId", action = LogActions.MATERIAL_EDIT)
    @SaCheckPermission(value = {"inventory:material:add", "inventory:material:edit"}, mode = SaMode.OR)
    public Result<MaterialSpecValuesVO> save(@RequestBody MaterialSpecValuesDTO dto) {
        if (dto.getMaterialId() == null && (dto.getMaterialCode() == null || dto.getMaterialCode().isBlank())) {
            return Result.error("物料ID或物料编码不能为空");
        }
        return Result.success(specService.save(dto));
    }
}
