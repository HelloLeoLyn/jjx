package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.service.IQuotationService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售报价单控制器
 * 提供销售报价单的增删改查接口
 */
@Tag(name = "销售报价单管理")
@RestController
@RequestMapping("/sales/quotation")
@RequiredArgsConstructor
public class QuotationController extends BaseController {

    private final IQuotationService quotationService;

    /**
     * 获取销售报价单分页列表
     */
    @Operation(summary = "获取销售报价单分页列表")
    @SaCheckPermission("sales:quotation:view")
    @GetMapping("/list")
    public Result<PageResult<SalesQuotation>> list(SalesQuotation quotation) {
        return Result.success(quotationService.selectQuotationPage(quotation, getPageNum(), getPageSize()));
    }

    /**
     * 获取销售报价单详细信息
     */
    @Operation(summary = "获取销售报价单详细信息")
    @SaCheckPermission("sales:quotation:view")
    @GetMapping(value = "/{quotationId}")
    public Result<SalesQuotation> getInfo(@PathVariable("quotationId") Long quotationId) {
        return Result.success(quotationService.selectQuotationById(quotationId));
    }

    /**
     * 新增销售报价单
     */
    @Operation(summary = "新增销售报价单")
    @Log(module = "报价单管理", businessType = BusinessType.INSERT, bizType = "'quotation'", bizId = "#quotation.quotationId", bizStatus = 0)
    @SaCheckPermission("sales:quotation:add")
    @PostMapping
    public Result<Void> add(@Validated @RequestBody SalesQuotation quotation) {
        return toAjax(quotationService.insertQuotation(quotation));
    }

    /**
     * 修改销售报价单
     */
    @Operation(summary = "修改销售报价单")
    @Log(module = "报价单管理", businessType = BusinessType.UPDATE, bizType = "'quotation'", bizId = "#quotation.quotationId", bizStatus = 0)
    @SaCheckPermission("sales:quotation:edit")
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody SalesQuotation quotation) {
        return toAjax(quotationService.updateQuotation(quotation));
    }

    /**
     * 删除销售报价单
     */
    @Operation(summary = "删除销售报价单")
    @Log(module = "报价单管理", businessType = BusinessType.DELETE, bizType = "'quotation'", bizStatus = 0)
    @SaCheckPermission("sales:quotation:delete")
    @DeleteMapping("/{quotationIds}")
    public Result<Void> remove(@PathVariable Long[] quotationIds) {
        return toAjax(quotationService.deleteQuotationByIds(quotationIds));
    }

    /**
     * 导出销售报价单列表
     */
    @Operation(summary = "导出销售报价单列表")
    @SaCheckPermission("sales:quotation:export")
    @GetMapping("/export")
    public Result<String> export(SalesQuotation quotation) {
        String filePath = quotationService.exportQuotationList(quotation);
        return Result.success(filePath);
    }

    /**
     * 发送报价单给客户
     */
    @Operation(summary = "发送报价单给客户")
    @Log(module = "报价单管理", businessType = BusinessType.UPDATE, bizType = "'quotation'", bizId = "#quotationId", bizStatus = 1)
    @SaCheckPermission("sales:quotation:edit")
    @PutMapping("/send/{quotationId}")
    public Result<Void> send(@PathVariable Long quotationId,
                             @RequestParam(required = false) String attachmentIds) {
        return toAjax(quotationService.sendQuotation(quotationId, attachmentIds));
    }

    /**
     * 报价单转为订单
     */
    @Operation(summary = "报价单转为订单")
    @Log(module = "报价单管理", businessType = BusinessType.UPDATE, bizType = "'quotation'", bizId = "#quotationId", bizStatus = 9)
    @SaCheckPermission("sales:quotation:edit")
    @PostMapping("/convert/{quotationId}")
    public Result<Object> convert(@PathVariable Long quotationId) {
        return Result.success(quotationService.convertToOrder(quotationId));
    }

    /**
     * 导出报价单PDF
     */
    @Operation(summary = "导出报价单PDF")
    @SaCheckPermission("sales:quotation:export")
    @GetMapping("/export-pdf/{quotationId}")
    public Result<String> exportPdf(@PathVariable Long quotationId) {
        String filePath = quotationService.exportPdf(quotationId);
        return Result.success(filePath);
    }

    /**
     * 复制报价单
     */
    @Operation(summary = "复制报价单")
    @Log(module = "报价单管理", businessType = BusinessType.INSERT, bizType = "'quotation'", bizId = "#quotationId", bizStatus = 0)
    @SaCheckPermission("sales:quotation:add")
    @PostMapping("/copy/{quotationId}")
    public Result<SalesQuotation> copy(@PathVariable Long quotationId) {
        return Result.success(quotationService.copyQuotation(quotationId));
    }

    /**
     * 提交报价单审核
     */
    @Operation(summary = "提交报价单审核")
    @Log(module = "报价单管理", businessType = BusinessType.UPDATE, bizType = "'quotation'", bizId = "#quotationId", bizStatus = 5)
    @SaCheckPermission("sales:quotation:edit")
    @PutMapping("/submit-review/{quotationId}")
    public Result<Void> submitReview(@PathVariable Long quotationId,
                                     @RequestParam(required = false) String attachmentIds) {
        return toAjax(quotationService.submitReview(quotationId, attachmentIds));
    }

    /**
     * 审核报价单
     */
    @Operation(summary = "审核报价单")
    @Log(module = "报价单管理", businessType = BusinessType.APPROVE, bizType = "'quotation'", bizId = "#quotationId", bizStatus = "#approved ? 6 : 3")
    @SaCheckPermission("sales:quotation:approve")
    @PutMapping("/review/{quotationId}")
    public Result<Void> review(@PathVariable Long quotationId,
                               @RequestParam Boolean approved,
                               @RequestParam(required = false) String remark,
                               @RequestParam(required = false) String attachmentIds) {
        return toAjax(quotationService.reviewQuotation(quotationId, approved, remark, attachmentIds));
    }

    /**
     * 获取报价单流转记录
     */
    @Operation(summary = "获取报价单流转记录")
    @SaCheckPermission("sales:quotation:view")
    @GetMapping("/flow/{quotationId}")
    public Result<List<com.jjx.sales.domain.entity.SalesQuotationFlow>> flowRecords(@PathVariable Long quotationId) {
        return Result.success(quotationService.selectFlowRecords(quotationId));
    }

    /**
     * 更新报价单状态
     */
    @Operation(summary = "更新报价单状态")
    @Log(module = "报价单管理", businessType = BusinessType.UPDATE, bizType = "'quotation'", bizId = "#quotationId", bizStatus = "#status")
    @SaCheckPermission("sales:quotation:edit")
    @PutMapping("/status/{quotationId}")
    public Result<Void> changeStatus(@PathVariable Long quotationId,
                                     @RequestParam Integer status,
                                     @RequestParam(required = false) String attachmentIds) {
        // 客户确认/拒绝走独立接口（触发事件），其他状态直接更新
        if (status != null && status == 2) {
            return toAjax(quotationService.confirmQuotation(quotationId, attachmentIds));
        }
        if (status != null && status == 3) {
            return toAjax(quotationService.rejectQuotation(quotationId, attachmentIds));
        }
        return toAjax(quotationService.updateQuotationStatus(quotationId, status, attachmentIds));
    }

    /**
     * 客户确认报价（独立接口，触发 quotation.confirmed 事件）
     */
    @Operation(summary = "客户确认报价")
    @Log(module = "报价单管理", businessType = BusinessType.UPDATE, bizType = "'quotation'", bizId = "#quotationId", bizStatus = 2)
    @SaCheckPermission("sales:quotation:edit")
    @PutMapping("/confirm/{quotationId}")
    public Result<Void> confirm(@PathVariable Long quotationId,
                                @RequestParam(required = false) String attachmentIds) {
        return toAjax(quotationService.confirmQuotation(quotationId, attachmentIds));
    }

    /**
     * 客户拒绝报价（独立接口，触发 quotation.rejected 事件）
     */
    @Operation(summary = "客户拒绝报价")
    @Log(module = "报价单管理", businessType = BusinessType.UPDATE, bizType = "'quotation'", bizId = "#quotationId", bizStatus = 3)
    @SaCheckPermission("sales:quotation:edit")
    @PutMapping("/reject/{quotationId}")
    public Result<Void> reject(@PathVariable Long quotationId,
                               @RequestParam(required = false) String attachmentIds) {
        return toAjax(quotationService.rejectQuotation(quotationId, attachmentIds));
    }

    /**
     * 已完成报价单改单（回到改单状态，可重新编辑）
     */
    @Operation(summary = "已完成报价单改单")
    @Log(module = "报价单管理", businessType = BusinessType.UPDATE, bizType = "'quotation'", bizId = "#quotationId", bizStatus = 8)
    @SaCheckPermission("sales:quotation:edit")
    @PutMapping("/modify/{quotationId}")
    public Result<Void> modify(@PathVariable Long quotationId,
                               @RequestParam(required = false) String attachmentIds) {
        return toAjax(quotationService.modifyQuotation(quotationId, attachmentIds));
    }

    /**
     * 获取报价单状态选项
     */
    @Operation(summary = "获取报价单状态选项")
    @SaCheckPermission("sales:quotation:view")
    @GetMapping("/status-options")
    public Result<List<Object>> getStatusOptions() {
        return Result.success(quotationService.getStatusOptions());
    }

    /**
     * 获取币种选项
     */
    @Operation(summary = "获取币种选项")
    @SaCheckPermission("sales:quotation:view")
    @GetMapping("/currency-options")
    public Result<List<Object>> getCurrencyOptions() {
        return Result.success(quotationService.getCurrencyOptions());
    }

    /**
     * 获取报价模板列表
     */
    @Operation(summary = "获取报价模板列表")
    @SaCheckPermission("sales:quotation:view")
    @GetMapping("/templates")
    public Result<List<Object>> getTemplates() {
        return Result.success(quotationService.getTemplates());
    }

    /**
     * 根据模板创建报价单
     */
    @Operation(summary = "根据模板创建报价单")
    @Log(module = "报价单管理", businessType = BusinessType.INSERT, bizType = "'quotation'", bizStatus = 0)
    @SaCheckPermission("sales:quotation:add")
    @PostMapping("/template/{templateId}")
    public Result<SalesQuotation> createFromTemplate(@PathVariable Long templateId,
                                                     @RequestParam Long customerId) {
        return Result.success(quotationService.createFromTemplate(templateId, customerId));
    }

    /**
     * 快速报价
     */
    @Operation(summary = "快速报价")
    @Log(module = "报价单管理", businessType = BusinessType.INSERT, bizType = "'quotation'")
    @SaCheckPermission("sales:quotation:add")
    @PostMapping("/quick")
    public Result<SalesQuotation> quickQuote(@RequestBody Object quickQuoteRequest) {
        return Result.success(quotationService.quickQuote(quickQuoteRequest));
    }

    /**
     * 获取客户历史报价
     */
    @Operation(summary = "获取客户历史报价")
    @SaCheckPermission("sales:quotation:view")
    @GetMapping("/customer/{customerId}/history")
    public Result<List<SalesQuotation>> getCustomerHistory(@PathVariable Long customerId) {
        return Result.success(quotationService.getCustomerHistory(customerId));
    }

    /**
     * 检查报价单号是否唯一
     */
    @Operation(summary = "检查报价单号是否唯一")
    @SaCheckPermission("sales:quotation:view")
    @GetMapping("/check-quotation-no-unique")
    public Result<Boolean> checkQuotationNoUnique(@RequestParam String quotationNo) {
        return Result.success(quotationService.checkQuotationNoUnique(quotationNo));
    }

    /**
     * 获取报价单统计信息
     */
    @Operation(summary = "获取报价单统计信息")
    @SaCheckPermission("sales:quotation:view")
    @GetMapping("/statistics")
    public Result<Object> statistics() {
        return Result.success(quotationService.getQuotationStatistics());
    }
}
