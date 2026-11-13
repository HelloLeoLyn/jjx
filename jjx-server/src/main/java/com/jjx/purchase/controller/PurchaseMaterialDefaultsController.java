package com.jjx.purchase.controller;
import cn.dev33.satoken.annotation.*;
import com.jjx.common.core.result.Result;
import com.jjx.common.constant.LogActions;
import com.jjx.purchase.domain.dto.PurchaseMaterialDefaultDTO;
import com.jjx.purchase.service.impl.PurchaseMaterialDefaultService;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/purchase/order")
@RequiredArgsConstructor
public class PurchaseMaterialDefaultsController {
    private final PurchaseMaterialDefaultService service;
    @GetMapping("/unit-options")
    @SaCheckPermission(value={"purchase:order:add","purchase:order:edit","purchase:order:view"},mode=SaMode.OR)
    public Result<List<Map<String,String>>> units() { return Result.success(service.units()); }
    @GetMapping("/material-default")
    @SaCheckPermission(value={"purchase:order:add","purchase:order:edit"},mode=SaMode.OR)
    public Result<Map<String,Object>> read(@RequestParam Long supplierId,@RequestParam Long materialId) {
        return Result.success(service.read(supplierId,materialId));
    }
    @PutMapping("/material-default")
    @SaCheckPermission("purchase:supplier:edit")
    @Log(module="供应商管理",businessType=BusinessType.UPDATE,bizType="'purchase_supplier'",bizId="#dto.supplierId",action=LogActions.SUPPLIER_EDIT)
    public Result<Void> save(@Valid @RequestBody PurchaseMaterialDefaultDTO dto) {
        service.save(dto,SecurityUtils.getUsername()); return Result.success();
    }
}
