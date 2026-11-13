package com.jjx.purchase.controller;

import cn.dev33.satoken.annotation.*;
import com.jjx.common.core.result.Result;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.service.impl.PurchasePlanEntryService;
import com.jjx.purchase.service.impl.PurchaseMaterialDefaultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/purchase/plan")
@RequiredArgsConstructor
public class PurchasePlanEntryController {
    private final PurchasePlanEntryService service;
    private final PurchaseMaterialDefaultService defaults;
    @GetMapping("/unit-options")
    @SaCheckPermission(value={"purchase:plan:view","purchase:plan:add","purchase:plan:confirm"},mode=SaMode.OR)
    public Result<List<Map<String,String>>> units(){return Result.success(defaults.units());}
    @GetMapping("/material/{id}")
    @SaCheckPermission(value={"purchase:plan:view","purchase:plan:add","purchase:plan:confirm"},mode=SaMode.OR)
    public Result<Map<String,Object>> material(@PathVariable Long id){return Result.success(service.material(id));}
    @PostMapping("/quantity-preview")
    @SaCheckPermission(value={"purchase:plan:view","purchase:plan:add","purchase:plan:confirm"},mode=SaMode.OR)
    public Result<PurchaseOrderItemDTO> quantity(@RequestBody PurchaseOrderItemDTO item){return Result.success(service.quantity(item));}
}
