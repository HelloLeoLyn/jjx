package com.jjx.purchase.controller;

import com.jjx.common.constant.LogActions;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.purchase.domain.dto.PurchaseDocumentDTO;
import com.jjx.purchase.domain.entity.PurchaseDocument;
import com.jjx.purchase.domain.enums.DocumentStatus;
import com.jjx.purchase.domain.vo.PurchaseOrderVO;
import com.jjx.purchase.service.IPurchaseDocumentService;
import com.jjx.purchase.service.IPurchaseOrderService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购发票Controller
 * 发票管理基于采购票据（PurchaseDocument）实现，类型为 invoice
 */
@Slf4j
@RestController
@RequestMapping("/purchase/invoice")
@RequiredArgsConstructor
public class PurchaseInvoiceController extends BaseController {

    private final IPurchaseDocumentService documentService;
    private final IPurchaseOrderService purchaseOrderService;

    /**
     * 查询采购发票列表
     */
    @GetMapping("/list")
    @SaCheckPermission("purchase:invoice:view")
    public Result<PageResult<PurchaseDocument>> page(PurchaseDocumentDTO dto) {
        if (dto == null) {
            dto = new PurchaseDocumentDTO();
        }
        dto.setDocumentType("invoice");
        List<PurchaseDocument> list = documentService.selectDocumentList(dto);
        int pageNum = dto.getPageNum() == null || dto.getPageNum() < 1 ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : dto.getPageSize();
        int total = list.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return Result.success(PageResult.build(list.subList(from, to), total));
    }

    /**
     * 查询采购发票详细
     */
    @GetMapping("/{invoiceId}")
    @SaCheckPermission("purchase:invoice:view")
    public Result<PurchaseDocument> getInfo(@PathVariable Long invoiceId) {
        return Result.success(documentService.selectDocumentById(invoiceId));
    }

    /**
     * 新增采购发票
     */
    @PostMapping
    @Log(module = "采购发票管理", businessType = BusinessType.INSERT, bizType = "'purchase_invoice'", bizId = "#dto.documentId", action = LogActions.PUR_INVOICE_CREATE)
    @SaCheckPermission("purchase:invoice:add")
    public Result<Void> add(@Valid PurchaseDocumentDTO dto) {
        dto.setDocumentType("invoice");
        if (dto.getDocumentStatus() == null) {
            dto.setDocumentStatus(DocumentStatus.PENDING.getValue());
        }
        documentService.insertDocument(dto);
        return Result.success();
    }

    /**
     * 修改采购发票
     */
    @PutMapping
    @Log(module = "采购发票管理", businessType = BusinessType.UPDATE, bizType = "'purchase_invoice'", bizId = "#dto.documentId", action = LogActions.PUR_INVOICE_EDIT)
    @SaCheckPermission("purchase:invoice:edit")
    public Result<Void> edit(@Valid PurchaseDocumentDTO dto) {
        dto.setDocumentType("invoice");
        documentService.updateDocument(dto);
        return Result.success();
    }

    /**
     * 删除采购发票
     */
    @DeleteMapping("/{invoiceIds}")
    @Log(module = "采购发票管理", businessType = BusinessType.DELETE, bizType = "'purchase_invoice'", bizId = "#invoiceIds[0]", action = LogActions.PUR_INVOICE_DELETE)
    @SaCheckPermission("purchase:invoice:delete")
    public Result<Void> remove(@PathVariable Long[] invoiceIds) {
        documentService.deleteDocumentByIds(invoiceIds);
        return Result.success();
    }

    /**
     * 导出采购发票列表
     */
    @GetMapping("/export")
    @SaCheckPermission("purchase:invoice:export")
    public Result<String> export(PurchaseDocumentDTO dto) {
        if (dto == null) {
            dto = new PurchaseDocumentDTO();
        }
        dto.setDocumentType("invoice");
        return Result.success(documentService.exportDocumentList(dto));
    }

    /**
     * 核销发票
     */
    @PutMapping("/verify/{invoiceId}")
    @Log(module = "采购发票管理", businessType = BusinessType.UPDATE, bizType = "'purchase_invoice'", bizId = "#invoiceId", action = LogActions.PUR_INVOICE_VERIFY)
    @SaCheckPermission("purchase:invoice:edit")
    public Result<Void> verify(@PathVariable Long invoiceId,
                               @RequestParam String verificationDate,
                               @RequestParam String verifierName,
                               @RequestParam(required = false) String verificationRemark) {
        documentService.verifyDocument(invoiceId, verifierName, verificationDate, verificationRemark);
        return Result.success();
    }

    /**
     * 查询待开票的订单列表
     */
    @GetMapping("/pending-orders")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<PurchaseOrderVO>> pendingOrders() {
        return Result.success(purchaseOrderService.selectPendingReceiptOrders());
    }

    /**
     * 根据订单ID查询发票记录
     */
    @GetMapping("/order/{orderId}")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<PurchaseDocument>> getByOrder(@PathVariable Long orderId) {
        return Result.success(documentService.selectByOrderId(orderId));
    }

    /**
     * 根据供应商ID查询发票记录
     */
    @GetMapping("/supplier/{supplierId}")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<PurchaseDocument>> getBySupplier(@PathVariable Long supplierId) {
        return Result.success(documentService.selectBySupplierId(supplierId));
    }

    /**
     * 查询待核销的发票列表
     */
    @GetMapping("/pending-verification")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<PurchaseDocument>> pendingVerification() {
        return Result.success(documentService.selectPendingVerification());
    }

    /**
     * 查询已核销的发票列表
     */
    @GetMapping("/verified")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<PurchaseDocument>> verified() {
        return Result.success(documentService.selectVerified());
    }

    /**
     * 查询今日开票记录
     */
    @GetMapping("/today")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<PurchaseDocument>> today() {
        return Result.success(documentService.selectToday());
    }

    /**
     * 查询本周开票记录
     */
    @GetMapping("/week")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<PurchaseDocument>> week() {
        return Result.success(documentService.selectWeek());
    }

    /**
     * 查询本月开票记录
     */
    @GetMapping("/month")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<PurchaseDocument>> month() {
        return Result.success(documentService.selectMonth());
    }

    /**
     * 获取发票统计信息
     */
    @GetMapping("/statistics")
    @SaCheckPermission("purchase:invoice:view")
    public Result<Map<String, Object>> statistics() {
        return Result.success(documentService.getDocumentStatistics());
    }

    /**
     * 批量核销
     */
    @PostMapping("/batch-verify")
    @Log(module = "采购发票管理", businessType = BusinessType.UPDATE, bizType = "'purchase_invoice'", bizId = "#batchData[0]['invoiceId']", action = LogActions.PUR_INVOICE_BATCH_VERIFY)
    @SaCheckPermission("purchase:invoice:edit")
    public Result<Void> batchVerify(@RequestBody List<Map<String, Object>> batchData) {
        for (Map<String, Object> data : batchData) {
            Long invoiceId = Long.valueOf(data.get("invoiceId").toString());
            String verificationDate = (String) data.get("verificationDate");
            String verifierName = (String) data.get("verifierName");
            String verificationRemark = (String) data.get("verificationRemark");
            documentService.verifyDocument(invoiceId, verifierName, verificationDate, verificationRemark);
        }
        return Result.success();
    }

    /**
     * 导入发票数据
     */
    @PostMapping("/import")
    @Log(module = "采购发票管理", businessType = BusinessType.IMPORT, bizType = "'purchase_invoice'", bizId = "#importData[0].documentId", action = LogActions.PUR_INVOICE_IMPORT)
    @SaCheckPermission("purchase:invoice:import")
    public Result<Void> importInvoice(@RequestBody List<PurchaseDocumentDTO> importData) {
        for (PurchaseDocumentDTO dto : importData) {
            dto.setDocumentType("invoice");
            if (dto.getDocumentStatus() == null) {
                dto.setDocumentStatus(DocumentStatus.PENDING.getValue());
            }
            documentService.insertDocument(dto);
        }
        return Result.success();
    }

    /**
     * 批量校验发票导入数据（DEV-726：不落库，逐行返回校验结果，防止裸插污染数据）
     */
    @PostMapping("/batch-check")
    @Operation(summary = "批量校验发票导入数据（DEV-726：逐行返回校验结果）")
    @SaCheckPermission("purchase:invoice:import")
    public Result<java.util.List<com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO>> batchCheck(@RequestBody java.util.List<com.jjx.purchase.domain.dto.DocumentBatchCheckItemDTO> items) {
        return Result.success(documentService.batchCheckDocument(items));
    }

    /**
     * 下载发票导入模板
     */
    @GetMapping("/import-template")
    @SaCheckPermission("purchase:invoice:import")
    public Result<String> importTemplate() {
        return Result.success("导入模板生成功能待实现");
    }

    /**
     * 检查发票号码是否唯一
     */
    @GetMapping("/check-invoice-no-unique")
    @SaCheckPermission("purchase:invoice:view")
    public Result<Boolean> checkInvoiceNoUnique(@RequestParam String invoiceNo) {
        return Result.success(!documentService.checkDocumentNoUnique(invoiceNo));
    }

    /**
     * 生成发票号码
     */
    @GetMapping("/generate-invoice-no")
    @SaCheckPermission("purchase:invoice:add")
    public Result<String> generateInvoiceNo() {
        return Result.success(documentService.generateDocumentNo("invoice"));
    }

    /**
     * 获取发票提醒
     */
    @GetMapping("/reminders")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<Map<String, Object>>> reminders() {
        List<PurchaseDocument> pendingDocs = documentService.selectPendingVerification();
        List<Map<String, Object>> reminders = pendingDocs.stream()
                .map(doc -> Map.<String, Object>of(
                        "documentId", doc.getDocumentId(),
                        "documentNo", doc.getDocumentNo(),
                        "documentAmount", doc.getDocumentAmount(),
                        "documentDate", doc.getDocumentDate(),
                        "message", "发票 " + doc.getDocumentNo() + " 待核销"
                ))
                .collect(Collectors.toList());
        return Result.success(reminders);
    }

    /**
     * 获取逾期未开票列表
     */
    @GetMapping("/overdue")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<PurchaseOrderVO>> overdue() {
        List<PurchaseOrderVO> orders = purchaseOrderService.selectOrdersByStatus(4);
        return Result.success(orders);
    }

    /**
     * 获取发票趋势分析
     */
    @GetMapping("/trend-analysis")
    @SaCheckPermission("purchase:invoice:view")
    public Result<Map<String, Object>> trendAnalysis() {
        return Result.success(Map.of(
                "message", "趋势分析功能待实现"
        ));
    }

    /**
     * 获取供应商发票分析
     */
    @GetMapping("/supplier-analysis")
    @SaCheckPermission("purchase:invoice:view")
    public Result<Map<String, Object>> supplierAnalysis(@RequestParam(required = false) Long supplierId) {
        return Result.success(Map.of(
                "message", "供应商发票分析功能待实现"
        ));
    }

    /**
     * 下载发票文件
     */
    @GetMapping("/download/{invoiceId}")
    @SaCheckPermission("purchase:invoice:view")
    public Result<String> downloadFile(@PathVariable Long invoiceId) {
        PurchaseDocument doc = documentService.selectDocumentById(invoiceId);
        if (doc.getFileUrl() == null) {
            throw new BusinessException("文件不存在");
        }
        return Result.success(doc.getFileUrl());
    }

    /**
     * 预览发票文件
     */
    @GetMapping("/preview/{invoiceId}")
    @SaCheckPermission("purchase:invoice:view")
    public Result<String> previewFile(@PathVariable Long invoiceId) {
        PurchaseDocument doc = documentService.selectDocumentById(invoiceId);
        if (doc.getFileUrl() == null) {
            throw new BusinessException("文件不存在");
        }
        return Result.success(doc.getFileUrl());
    }

    /**
     * 批量下载发票文件
     */
    @PostMapping("/batch-download")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<String>> batchDownload(@RequestBody Map<String, List<Long>> request) {
        List<Long> invoiceIds = request.get("invoiceIds");
        List<String> urls = invoiceIds.stream()
                .map(id -> {
                    PurchaseDocument doc = documentService.selectDocumentById(id);
                    return doc.getFileUrl();
                })
                .collect(Collectors.toList());
        return Result.success(urls);
    }

    /**
     * 批量删除发票文件
     */
    @PostMapping("/batch-delete-files")
    @Log(module = "采购发票管理", businessType = BusinessType.UPDATE, bizType = "'purchase_invoice'", bizId = "#request['invoiceIds'][0]", action = LogActions.PUR_INVOICE_DELETE_FILES)
    @SaCheckPermission("purchase:invoice:edit")
    public Result<Void> batchDeleteFiles(@RequestBody Map<String, List<Long>> request) {
        List<Long> invoiceIds = request.get("invoiceIds");
        for (Long id : invoiceIds) {
            PurchaseDocument doc = documentService.selectDocumentById(id);
            doc.setFileName(null);
            doc.setFileUrl(null);
            doc.setFileSize(null);
            documentService.updateDocument(toDTO(doc));
        }
        return Result.success();
    }

    /**
     * 获取发票类型统计
     */
    @GetMapping("/type-statistics")
    @SaCheckPermission("purchase:invoice:view")
    public Result<Map<String, Object>> typeStatistics() {
        return Result.success(documentService.getDocumentStatistics());
    }

    /**
     * 获取发票状态统计
     */
    @GetMapping("/status-statistics")
    @SaCheckPermission("purchase:invoice:view")
    public Result<Map<String, Object>> statusStatistics() {
        return Result.success(documentService.getDocumentStatistics());
    }

    /**
     * 获取月度发票统计
     */
    @GetMapping("/monthly-statistics")
    @SaCheckPermission("purchase:invoice:view")
    public Result<Map<String, Object>> monthlyStatistics(@RequestParam(required = false) Integer year) {
        return Result.success(Map.of("message", "月度统计功能待实现"));
    }

    /**
     * 获取季度发票统计
     */
    @GetMapping("/quarterly-statistics")
    @SaCheckPermission("purchase:invoice:view")
    public Result<Map<String, Object>> quarterlyStatistics(@RequestParam(required = false) Integer year) {
        return Result.success(Map.of("message", "季度统计功能待实现"));
    }

    /**
     * 获取年度发票统计
     */
    @GetMapping("/yearly-statistics")
    @SaCheckPermission("purchase:invoice:view")
    public Result<Map<String, Object>> yearlyStatistics(@RequestParam(required = false) Integer startYear,
                                                        @RequestParam(required = false) Integer endYear) {
        return Result.success(Map.of("message", "年度统计功能待实现"));
    }

    /**
     * 临时上传票据文件（只保存到磁盘，不插入数据库）
     */
    @PostMapping("/upload-temp/{orderId}")
    @Log(module = "采购发票管理", businessType = BusinessType.UPDATE, bizType = "'purchase_invoice'", bizId = "#orderId", action = LogActions.PUR_INVOICE_UPLOAD_TEMP)
    @SaCheckPermission("purchase:invoice:edit")
    public Result<Map<String, Object>> uploadTempFile(@PathVariable Long orderId,
                                                       @RequestParam("file") MultipartFile file) {
        Map<String, Object> fileInfo = documentService.uploadTempFile(orderId, file);
        return Result.success(fileInfo);
    }

    /**
     * 查询订单的磁盘票据文件列表
     */
    @GetMapping("/disk-files/{orderId}")
    @SaCheckPermission("purchase:invoice:view")
    public Result<List<Map<String, Object>>> getDiskFiles(@PathVariable Long orderId) {
        List<Map<String, Object>> files = documentService.selectDiskFilesByOrderId(orderId);
        return Result.success(files);
    }

    /**
     * 批量确认票据（将临时文件插入数据库）
     */
    @PostMapping("/batch-confirm")
    @Log(module = "采购发票管理", businessType = BusinessType.INSERT, bizType = "'purchase_invoice'", bizId = "#params['orderId']", action = LogActions.PUR_INVOICE_BATCH_CONFIRM)
    @SaCheckPermission("purchase:invoice:add")
    public Result<Void> batchConfirm(@RequestBody Map<String, Object> params) {
        Long orderId = Long.valueOf(params.get("orderId").toString());
        Long supplierId = Long.valueOf(params.get("supplierId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) params.get("files");
        documentService.batchConfirmDocuments(orderId, supplierId, files, "invoice");
        return Result.success();
    }

    /**
     * 删除临时票据文件
     */
    @DeleteMapping("/temp-file")
    @Log(module = "采购发票管理", businessType = BusinessType.DELETE, bizType = "'purchase_invoice'", bizId = "#fileUrl", action = LogActions.PUR_INVOICE_DELETE_TEMP)
    @SaCheckPermission("purchase:invoice:delete")
    public Result<Void> deleteTempFile(@RequestParam String fileUrl) {
        documentService.deleteTempFile(fileUrl);
        return Result.success();
    }

    /**
     * 将实体转换为DTO
     */
    private PurchaseDocumentDTO toDTO(PurchaseDocument doc) {
        PurchaseDocumentDTO dto = new PurchaseDocumentDTO();
        dto.setDocumentId(doc.getDocumentId());
        dto.setDocumentNo(doc.getDocumentNo());
        dto.setDocumentType(doc.getDocumentType());
        dto.setOrderId(doc.getOrderId());
        dto.setSupplierId(doc.getSupplierId());
        dto.setDocumentDate(doc.getDocumentDate());
        dto.setDocumentAmount(doc.getDocumentAmount());
        dto.setCurrency(doc.getCurrency());
        dto.setDocumentStatus(doc.getDocumentStatus());
        dto.setVerificationDate(doc.getVerificationDate());
        dto.setFileName(doc.getFileName());
        dto.setFileUrl(doc.getFileUrl());
        dto.setFileSize(doc.getFileSize());
        dto.setRemark(doc.getRemark());
        return dto;
    }
}
