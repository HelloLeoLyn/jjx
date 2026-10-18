package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.sales.domain.dto.SalesCustomerContactDTO;
import com.jjx.sales.domain.entity.SalesCustomerContact;
import com.jjx.sales.service.ISalesCustomerContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "销售模块 - 客户联系人")
@RestController
@RequestMapping("/sales/customers/{customerId}/contacts")
@RequiredArgsConstructor
public class SalesCustomerContactController {
    private final ISalesCustomerContactService contactService;

    @Operation(summary = "查询客户联系人")
    @SaCheckPermission("sales:customer:view")
    @GetMapping
    public Result<List<SalesCustomerContact>> list(@PathVariable Long customerId) {
        return Result.success(contactService.listByCustomer(customerId));
    }

    @Operation(summary = "新增客户联系人")
    @SaCheckPermission("sales:customer:edit")
    @PostMapping
    public Result<Long> add(@PathVariable Long customerId, @Validated @RequestBody SalesCustomerContactDTO dto) {
        return Result.success(contactService.add(customerId, dto));
    }

    @Operation(summary = "修改客户联系人（保留历史快照）")
    @SaCheckPermission("sales:customer:edit")
    @PutMapping("/{contactId}")
    public Result<Void> update(@PathVariable Long customerId, @PathVariable Long contactId,
                               @Validated @RequestBody SalesCustomerContactDTO dto) {
        contactService.update(customerId, contactId, dto);
        return Result.success();
    }

    @Operation(summary = "删除未被地址引用的客户联系人")
    @SaCheckPermission("sales:customer:edit")
    @DeleteMapping("/{contactId}")
    public Result<Void> delete(@PathVariable Long customerId, @PathVariable Long contactId) {
        contactService.delete(customerId, contactId);
        return Result.success();
    }
}
