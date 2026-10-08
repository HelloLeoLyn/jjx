package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.dto.SalesCustomerAddressDTO;
import com.jjx.sales.domain.entity.SalesCustomerAddress;
import com.jjx.sales.service.ISalesCustomerAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售模块 - 客户收货地址簿（dev-20261008-029）
 * 一个客户维护多个收货地址；权限沿用 sales:customer:*。
 */
@Tag(name = "销售模块 - 客户收货地址")
@RestController
@RequestMapping("/sales/customers/{customerId}/addresses")
@RequiredArgsConstructor
public class SalesCustomerAddressController extends BaseController {

    private final ISalesCustomerAddressService addressService;

    @Operation(summary = "查询客户收货地址列表")
    @SaCheckPermission("sales:customer:view")
    @GetMapping
    public Result<List<SalesCustomerAddress>> list(@PathVariable Long customerId) {
        return Result.success(addressService.listByCustomer(customerId));
    }

    @Operation(summary = "新增客户收货地址")
    @SaCheckPermission("sales:customer:edit")
    @PostMapping
    public Result<Long> add(@PathVariable Long customerId, @Validated @RequestBody SalesCustomerAddressDTO dto) {
        return Result.success(addressService.add(customerId, dto));
    }

    @Operation(summary = "修改客户收货地址")
    @SaCheckPermission("sales:customer:edit")
    @PutMapping("/{addressId}")
    public Result<Void> update(@PathVariable Long customerId, @PathVariable Long addressId,
                               @Validated @RequestBody SalesCustomerAddressDTO dto) {
        dto.setAddressId(addressId);
        return toAjax(addressService.update(customerId, dto));
    }

    @Operation(summary = "删除客户收货地址")
    @SaCheckPermission("sales:customer:edit")
    @DeleteMapping("/{addressId}")
    public Result<Void> delete(@PathVariable Long customerId, @PathVariable Long addressId) {
        return toAjax(addressService.delete(customerId, addressId));
    }

    @Operation(summary = "设为默认收货地址")
    @SaCheckPermission("sales:customer:edit")
    @PutMapping("/{addressId}/default")
    public Result<Void> setDefault(@PathVariable Long customerId, @PathVariable Long addressId) {
        return toAjax(addressService.setDefault(customerId, addressId));
    }
}
