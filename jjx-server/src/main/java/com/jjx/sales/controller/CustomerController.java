package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.dto.CustomerAddDTO;
import com.jjx.sales.domain.dto.CustomerEditDTO;
import com.jjx.sales.domain.dto.CustomerQueryDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.vo.CustomerVO;
import com.jjx.sales.service.ICustomerService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售模块-客户管理控制器
 * 提供客户信息的增删改查接口
 */
@Tag(name = "销售模块 - 客户管理")
@RestController
@RequestMapping("/sales/customers")
@RequiredArgsConstructor
public class CustomerController extends BaseController {

    private final ICustomerService customerService;

    /**
     * 获取客户列表（分页）
     */
    @Operation(summary = "获取客户列表（分页）")
    @SaCheckPermission("sales:customer:view")
    @GetMapping
    public Result<PageResult<CustomerVO>> getCustomers(CustomerQueryDTO queryDTO) {
        PageResult<CustomerVO> page = customerService.page(queryDTO);
        return Result.success(page);
    }

    /**
     * 根据关键词搜索客户
     */
    @Operation(summary = "根据关键词搜索客户")
    @SaCheckPermission("sales:customer:view")
    @GetMapping("/search")
    public Result<List<CustomerVO>> searchCustomers(@RequestParam String keyword) {
        return Result.success(customerService.search(keyword));
    }

    /**
     * 获取客户详细信息
     */
    @Operation(summary = "获取客户详细信息")
    @SaCheckPermission("sales:customer:detail")
    @GetMapping("/{customerId}")
    public Result<CustomerVO> getCustomer(@PathVariable Long customerId) {
        return Result.success(customerService.selectCustomerById(customerId));
    }

    /**
     * 生成客户编码
     */
    @Operation(summary = "生成客户编码")
    @SaCheckPermission("sales:customer:add")
    @GetMapping("/generate-code")
    public Result<String> generateCustomerCode() {
        return Result.success(customerService.generateCustomerCode());
    }

    /**
     * 新增客户
     */
    @Operation(summary = "新增客户")
    @Log(module = "客户管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("sales:customer:add")
    @PostMapping
    public Result<Void> addCustomer(@Validated @RequestBody CustomerAddDTO dto) {
        return toAjax(customerService.insertCustomer(dto));
    }

    /**
     * 根据客户编码获取客户信息
     */
    @Operation(summary = "根据客户编码获取客户信息")
    @SaCheckPermission("sales:customer:add")
    @GetMapping("/code/{customerCode}")
    public Result<CustomerVO> getCustomerByCode(@PathVariable String customerCode) {
        return Result.success(customerService.selectCustomerByCode(customerCode));
    }

    /**
     * 修改客户
     */
    @Operation(summary = "修改客户")
    @Log(module = "客户管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("sales:customer:edit")
    @PutMapping("/{customerId}")
    public Result<Void> updateCustomer(@PathVariable Long customerId, @Validated @RequestBody CustomerEditDTO dto) {
        dto.setCustomerId(customerId); // 设置客户ID
        return toAjax(customerService.updateCustomer(dto));
    }

    /**
     * 删除客户
     */
    @Operation(summary = "删除客户")
    @Log(module = "客户管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("sales:customer:delete")
    @DeleteMapping("/{customerIds}")
    public Result<Void> deleteCustomers(@PathVariable Long[] customerIds) {
        return toAjax(customerService.deleteCustomerByIds(customerIds));
    }

    /**
     * 导出客户列表
     */
    @Operation(summary = "导出客户列表")
    @SaCheckPermission("sales:customer:export")
    @GetMapping("/export")
    public Result<String> exportCustomers(SalesCustomer customer) {
        String filePath = customerService.exportCustomerList(customer);
        return Result.success(filePath);
    }

    /**
     * 获取客户下拉列表
     */
    @Operation(summary = "获取客户下拉列表")
    @SaCheckPermission("sales:customer:view")
    @GetMapping("/dropdown")
    public Result<List<SalesCustomer>> getCustomerDropdown() {
        return Result.success(customerService.selectCustomerDropdown());
    }

    /**
     * 变更客户状态
     */
    @Operation(summary = "变更客户状态")
    @Log(module = "客户管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("sales:customer:edit")
    @PutMapping("/{customerId}/status")
    public Result<Void> changeCustomerStatus(@PathVariable Long customerId, @RequestParam Integer status) {
        return toAjax(customerService.changeCustomerStatus(customerId, status));
    }

    /**
     * 批量审核客户
     */
    @Operation(summary = "批量审核客户")
    @Log(module = "客户管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("sales:customer:edit")
    @PutMapping("/approve")
    public Result<Void> approveCustomers(@RequestBody Long[] customerIds) {
        return toAjax(customerService.approveCustomers(customerIds));
    }

    /**
     * 获取客户统计信息
     */
    @Operation(summary = "获取客户统计信息")
    @SaCheckPermission("sales:customer:view")
    @GetMapping("/statistics")
    public Result<Object> getCustomerStatistics() {
        return Result.success(customerService.getCustomerStatistics());
    }



    /**
     * 更新客户信用额度
     */
    @Operation(summary = "更新客户信用额度")
    @Log(module = "客户管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("sales:customer:edit")
    @PutMapping("/{customerId}/credit")
    public Result<Void> updateCustomerCreditLimit(@PathVariable Long customerId, @RequestParam Double creditLimit) {
        return toAjax(customerService.updateCustomerCreditLimit(customerId, creditLimit));
    }

}
