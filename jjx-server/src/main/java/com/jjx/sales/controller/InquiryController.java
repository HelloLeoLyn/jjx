package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.entity.SalesInquiry;
import com.jjx.sales.domain.vo.InquiryToQuotationVO;
import com.jjx.sales.service.IInquiryService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 销售询价单控制器
 * 客户询价需求录入与管理
 */
@Tag(name = "销售询价单管理")
@RestController
@RequestMapping("/sales/inquiry")
@RequiredArgsConstructor
public class InquiryController extends BaseController {

    private final IInquiryService inquiryService;

    /**
     * 获取询价单分页列表
     */
    @Operation(summary = "获取询价单分页列表")
    @SaCheckPermission("sales:inquiry:view")
    @GetMapping("/list")
    public Result<PageResult<SalesInquiry>> list(SalesInquiry inquiry) {
        return Result.success(inquiryService.selectInquiryPage(inquiry, getPageNum(), getPageSize()));
    }

    /**
     * 获取询价单详细信息
     */
    @Operation(summary = "获取询价单详细信息")
    @SaCheckPermission("sales:inquiry:view")
    @GetMapping("/{inquiryId}")
    public Result<SalesInquiry> getInfo(@PathVariable("inquiryId") Long inquiryId) {
        return Result.success(inquiryService.selectInquiryById(inquiryId));
    }

    /**
     * 新增询价单
     */
    @Operation(summary = "新增询价单")
    @Log(module = "询价单管理", businessType = BusinessType.INSERT, traceId = "#inquiry.traceId")
    @SaCheckPermission("sales:inquiry:add")
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SalesInquiry inquiry) {

        return toAjax(inquiryService.insertInquiry(inquiry));
    }

    /**
     * 修改询价单
     */
    @Operation(summary = "修改询价单")
    @Log(module = "询价单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("sales:inquiry:edit")
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SalesInquiry inquiry) {
        return toAjax(inquiryService.updateInquiry(inquiry));
    }

    /**
     * 删除询价单
     */
    @Operation(summary = "删除询价单")
    @Log(module = "询价单管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("sales:inquiry:delete")
    @DeleteMapping("/{inquiryIds}")
    public Result<Void> remove(@PathVariable Long[] inquiryIds) {
        return toAjax(inquiryService.deleteInquiryByIds(inquiryIds));
    }

    /**
     * 询价转报价
     */
    @Operation(summary = "询价转报价")
    @Log(module = "询价单管理", businessType = BusinessType.UPDATE, bizStatus = 3,
         traceId = "#result.data.traceId")
    @SaCheckPermission("sales:inquiry:convert")
    @PostMapping("/convert/{inquiryId}")
    public Result<InquiryToQuotationVO> convert(@PathVariable Long inquiryId) {
        return Result.success(inquiryService.convertToQuotation(inquiryId));
    }

    /**
     * 获取询价单状态选项
     */
    @Operation(summary = "获取询价单状态选项")
    @SaCheckPermission("sales:inquiry:view")
    @GetMapping("/status-options")
    public Result<List<Object>> getStatusOptions() {
        return Result.success(inquiryService.getStatusOptions());
    }

    /**
     * 导出询价单列表
     */
    @Operation(summary = "导出询价单列表")
    @SaCheckPermission("sales:inquiry:export")
    @GetMapping("/export")
    public Result<String> export(SalesInquiry inquiry) {
        // 简版导出提示，后续可完善为真实导出
        return Result.success("导出功能待完善");
    }

}
