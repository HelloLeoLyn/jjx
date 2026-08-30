package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.dto.SalesInquiryEditDTO;
import com.jjx.sales.domain.entity.SalesInquiry;
import com.jjx.sales.domain.vo.InquiryToQuotationVO;
import com.jjx.sales.domain.vo.SalesInquiryEditVO;
import com.jjx.sales.service.IInquiryService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
    @Operation(summary = "编码生成器：按客户简称取下一个流水号（产品编码第4-6位）")
    @GetMapping("/next-serial")
    public Result<String> nextSerial(@RequestParam String customerShort) {
        return Result.success(inquiryService.nextProductSerial(customerShort));
    }

    @Operation(summary = "新增询价单")
    @Log(module = "询价单管理", businessType = BusinessType.INSERT, bizType = "'inquiry'", bizId = "#inquiry.inquiryId", traceId = "#inquiry.traceId", bizStatus = "T(com.jjx.sales.enums.InquiryStatus).DRAFT.getLabel()")
    @SaCheckPermission("sales:inquiry:add")
    @PostMapping
    public Result<java.util.Map<String, Object>> add(@Validated @RequestBody SalesInquiry inquiry) {
        int rows = inquiryService.insertInquiry(inquiry);
        if (rows > 0) {
            // 返回新询价单ID和traceId（前端用于上传附件/流水）
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("inquiryId", inquiry.getInquiryId());
            data.put("traceId", inquiry.getTraceId());
            return Result.success(data);
        }
        return Result.error("新增失败");
    }

    /**
     * 修改询价单
     */
    @Operation(summary = "修改询价单")
    @Log(module = "询价单管理", businessType = BusinessType.UPDATE, bizType = "'inquiry'", bizId = "#inquiry.inquiryId", detail = "#result.data.detailMessage", bizStatus = "T(com.jjx.sales.enums.InquiryStatus).DRAFT.getLabel()")
    @SaCheckPermission("sales:inquiry:edit")
    @PutMapping
    public Result<SalesInquiryEditVO> edit(@Validated @RequestBody SalesInquiryEditDTO inquiry) {
        return Result.success(inquiryService.updateInquiry(inquiry));
    }

    /**
     * 删除询价单
     */
    @Operation(summary = "删除询价单")
    @Log(module = "询价单管理", businessType = BusinessType.DELETE, bizType = "'inquiry'")
    @SaCheckPermission("sales:inquiry:delete")
    @DeleteMapping("/{inquiryIds}")
    public Result<Void> remove(@PathVariable Long[] inquiryIds) {
        return toAjax(inquiryService.deleteInquiryByIds(inquiryIds));
    }

    /**
     * 询价转报价
     */
    @Operation(summary = "询价转报价")
    @SaCheckPermission("sales:inquiry:convert")
    @PostMapping("/convert/{inquiryId}")
    public Result<InquiryToQuotationVO> convert(@PathVariable Long inquiryId) {
        return Result.success(inquiryService.convertToQuotation(inquiryId));
    }

    /**
     * 发送询价（发给客户）
     */
    @Operation(summary = "发送询价（草稿/待处理 → 已发送）")
    @Log(module = "询价单管理", businessType = BusinessType.UPDATE, bizType = "'inquiry'", bizId = "#inquiryId", bizStatus = "T(com.jjx.sales.enums.InquiryStatus).SENT.getLabel()")
    @SaCheckPermission("sales:inquiry:edit")
    @PutMapping("/send/{inquiryId}")
    public Result<Void> send(@PathVariable Long inquiryId) {
        return toAjax(inquiryService.sendInquiry(inquiryId));
    }

    /**
     * 客户确认询价
     */
    @Operation(summary = "客户确认询价（已发送 → 已确认）")
    @Log(module = "询价单管理", businessType = BusinessType.UPDATE, bizType = "'inquiry'", bizId = "#inquiryId", bizStatus = "T(com.jjx.sales.enums.InquiryStatus).ACCEPTED.getLabel()")
    @SaCheckPermission("sales:inquiry:edit")
    @PutMapping("/accept/{inquiryId}")
    public Result<Void> accept(@PathVariable Long inquiryId) {
        return toAjax(inquiryService.acceptInquiry(inquiryId));
    }

    /**
     * 客户拒绝询价
     */
    @Operation(summary = "客户拒绝询价（已发送 → 已拒绝）")
    @Log(module = "询价单管理", businessType = BusinessType.UPDATE, bizType = "'inquiry'", bizId = "#inquiryId", bizStatus = "T(com.jjx.sales.enums.InquiryStatus).REJECTED.getLabel()")
    @SaCheckPermission("sales:inquiry:edit")
    @PutMapping("/reject/{inquiryId}")
    public Result<Void> reject(@PathVariable Long inquiryId) {
        return toAjax(inquiryService.rejectInquiry(inquiryId));
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
    public ResponseEntity<byte[]> export(SalesInquiry inquiry) {
        byte[] bytes = inquiryService.exportInquiryList(inquiry);
        String fileName = URLEncoder.encode("询价单列表.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(bytes);
    }

}
