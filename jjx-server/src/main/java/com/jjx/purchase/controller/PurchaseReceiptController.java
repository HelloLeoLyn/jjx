package com.jjx.purchase.controller;

import com.jjx.common.constant.LogActions;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.purchase.domain.dto.ReceiptBatchCheckItemDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderReceiveDTO;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO;
import com.jjx.purchase.domain.vo.PurchaseOrderItemVO;
import com.jjx.purchase.domain.vo.PurchaseOrderVO;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.mapper.PurchaseReceiptInboundMapper;
import com.jjx.purchase.service.IPurchaseDocumentService;
import com.jjx.purchase.service.IPurchaseOrderService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 采购收货Controller
 * 收货操作直接关联采购订单，更新订单明细的收货状态
 */
@Slf4j
@RestController
@RequestMapping("/purchase/receipt")
@RequiredArgsConstructor
public class PurchaseReceiptController extends BaseController {

    private final IPurchaseOrderService purchaseOrderService;
    private final PurchaseOrderItemMapper orderItemMapper;
    /** 收货票据（独立权限域 purchase:receipt:doc:*，dev-20260923-004）。 */
    private final IPurchaseDocumentService purchaseDocumentService;
    /** 只读：本批收货生成的入库单（收货页展示 + 引导 IQC）。 */
    private final PurchaseReceiptInboundMapper receiptInboundMapper;

    /**
     * 查询采购收货列表（已批准/待收货的订单）
     */
    @GetMapping("/list")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderVO>> list() {
        List<PurchaseOrderVO> list = purchaseOrderService.selectPendingReceiptOrders();
        return Result.success(list);
    }

    /**
     * 查询采购收货详细
     */
    @GetMapping("/{receiptId}")
    @SaCheckPermission("purchase:receipt:view")
    public Result<PurchaseOrderVO> getInfo(@PathVariable Long receiptId) {
        return Result.success(purchaseOrderService.selectOrderById(receiptId));
    }

    /**
     * 新增采购收货（确认收货）
     */
    @PostMapping
    @Log(module = "采购收货管理", businessType = BusinessType.INSERT, bizType = "'purchase_receipt'", bizId = "#orderId", action = LogActions.PUR_RECEIPT_CREATE)
    @SaCheckPermission("purchase:receipt:add")
    public Result<Void> add(@RequestParam Long orderId,
                            @RequestParam Long itemId,
                            @RequestParam BigDecimal receivedQuantity) {
        purchaseOrderService.receiveOrderItem(orderId, itemId, receivedQuantity);
        return Result.success();
    }

    /**
     * 修改采购收货
     */
    @PutMapping
    @Log(module = "采购收货管理", businessType = BusinessType.UPDATE, bizType = "'purchase_receipt'", bizId = "#orderId", action = LogActions.PUR_RECEIPT_EDIT)
    @SaCheckPermission("purchase:receipt:edit")
    public Result<Void> edit(@RequestParam Long orderId,
                             @RequestParam Long itemId,
                             @RequestParam BigDecimal receivedQuantity) {
        purchaseOrderService.receiveOrderItem(orderId, itemId, receivedQuantity);
        return Result.success();
    }

    /**
     * 删除采购收货
     */
    @DeleteMapping("/{receiptIds}")
    @Log(module = "采购收货管理", businessType = BusinessType.DELETE, bizType = "'purchase_receipt'", bizId = "#receiptIds[0]", action = LogActions.PUR_RECEIPT_DELETE)
    @SaCheckPermission("purchase:receipt:delete")
    public Result<Void> remove(@PathVariable Long[] receiptIds) {
        throw new BusinessException("收货记录不可删除");
    }

    /**
     * 导出采购收货列表
     */
    @GetMapping("/export")
    @SaCheckPermission("purchase:receipt:export")
    public Result<String> export() {
        return Result.success("导出功能待实现");
    }

    /**
     * 确认收货
     */
    @PutMapping("/confirm/{receiptId}")
    @Log(module = "采购收货管理", businessType = BusinessType.UPDATE, bizType = "'purchase_receipt'", bizId = "#receiptId", action = LogActions.PUR_RECEIPT_CONFIRM)
    @SaCheckPermission("purchase:receipt:edit")
    public Result<Void> confirm(@PathVariable Long receiptId,
                                @RequestParam BigDecimal receivedQuantity,
                                @RequestParam String receiverName,
                                @RequestParam String receiptDate,
                                @RequestParam(required = false) String remark) {
        PurchaseOrderItem item = orderItemMapper.selectById(receiptId);
        if (item == null) {
            throw new BusinessException("订单明细不存在");
        }
        purchaseOrderService.receiveOrderItem(item.getOrderId(), receiptId, receivedQuantity);
        return Result.success();
    }

    /**
     * 查询待收货的订单列表
     */
    @GetMapping("/pending-orders")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderVO>> pendingOrders() {
        return Result.success(purchaseOrderService.selectPendingReceiptOrders());
    }

    /**
     * 根据订单ID查询收货明细
     */
    @GetMapping("/order/{orderId}")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderItemVO>> getByOrder(@PathVariable Long orderId) {
        return Result.success(purchaseOrderService.selectOrderItemList(orderId));
    }

    /**
     * 根据物料ID查询收货记录
     */
    @GetMapping("/material/{materialId}")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderItemVO>> getByMaterial(@PathVariable Long materialId) {
        List<PurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>()
                        .eq(PurchaseOrderItem::getMaterialId, materialId)
                        .orderByDesc(PurchaseOrderItem::getItemId)
        );
        return Result.success(
                items.stream()
                        .map(item -> {
                            PurchaseOrderItemVO vo = new PurchaseOrderItemVO();
                            vo.setItemId(item.getItemId());
                            vo.setMaterialId(item.getMaterialId());
                            vo.setMaterialCode(item.getMaterialCode());
                            vo.setMaterialName(item.getMaterialName());
                            vo.setMaterialSpec(item.getMaterialSpec());
                            vo.setUnit(item.getUnit());
                            vo.setQuantity(item.getQuantity());
                            vo.setUnitPrice(item.getUnitPrice());
                            vo.setAmount(item.getAmount());
                            vo.setReceivedQuantity(item.getReceivedQuantity());
                            vo.setReceiptStatus(item.getReceiptStatus());
                            vo.setInspectionResult(item.getInspectionResult());
                            vo.setInspectionRemark(item.getInspectionRemark());
                            return vo;
                        })
                        .toList()
        );
    }

    /**
     * 根据供应商ID查询收货记录
     */
    @GetMapping("/supplier/{supplierId}")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderVO>> getBySupplier(@PathVariable Long supplierId) {
        return Result.success(purchaseOrderService.selectOrdersBySupplierId(supplierId));
    }

    /**
     * 查询待检验的收货列表
     */
    @GetMapping("/pending-inspection")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderVO>> pendingInspection() {
        List<PurchaseOrderVO> orders = purchaseOrderService.selectPendingReceiptOrders();
        return Result.success(orders);
    }

    /**
     * 查询已检验的收货列表
     */
    @GetMapping("/inspected")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderVO>> inspected() {
        List<PurchaseOrderVO> orders = purchaseOrderService.selectOrdersByStatus(4);
        return Result.success(orders);
    }

    /**
     * 查询今日收货记录
     */
    @GetMapping("/today")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderVO>> today() {
        LocalDate today = LocalDate.now();
        return Result.success(purchaseOrderService.selectOrdersByDateRange(today, today));
    }

    /**
     * 查询本周收货记录
     */
    @GetMapping("/week")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderVO>> week() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        return Result.success(purchaseOrderService.selectOrdersByDateRange(weekStart, today));
    }

    /**
     * 查询本月收货记录
     */
    @GetMapping("/month")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<PurchaseOrderVO>> month() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        return Result.success(purchaseOrderService.selectOrdersByDateRange(monthStart, today));
    }

    /**
     * 获取收货统计信息
     */
    @GetMapping("/statistics")
    @SaCheckPermission("purchase:receipt:view")
    public Result<Object> statistics() {
        return Result.success(purchaseOrderService.getOrderStatistics());
    }

    /**
     * 批量收货
     */
    @PostMapping("/batch")
    @Log(module = "采购收货管理", businessType = BusinessType.INSERT, bizType = "'purchase_receipt'", bizId = "#batchData[0]['orderId']", action = LogActions.PUR_RECEIPT_BATCH_RECEIVE)
    @SaCheckPermission("purchase:receipt:add")
    public Result<Void> batchReceive(@RequestBody List<Map<String, Object>> batchData) {
        // 2026-09-23（dev-20260923-004）修复：原实现逐条调 receiveOrderItem，而它每次都调 createInboundRecordFromPurchase
        // → 一次批量收 N 行会生成 N 张入库单。改为按订单分组、每单只提交一次（入库单粒度=一次收货批次）。
        for (Map.Entry<Long, List<PurchaseOrderReceiveDTO.ReceiveItemDTO>> entry : groupReceiptRowsByOrder(batchData).entrySet()) {
            PurchaseOrderReceiveDTO dto = new PurchaseOrderReceiveDTO();
            dto.setOrderId(entry.getKey());
            dto.setItems(entry.getValue());
            purchaseOrderService.batchReceiveOrderItems(dto);
        }
        return Result.success();
    }

    /** 把 [{orderId,itemId,receivedQuantity}] 按 orderId 分组（dev-20260923-004） */
    private Map<Long, List<PurchaseOrderReceiveDTO.ReceiveItemDTO>> groupReceiptRowsByOrder(
            List<Map<String, Object>> rows) {
        Map<Long, List<PurchaseOrderReceiveDTO.ReceiveItemDTO>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long orderId = Long.valueOf(row.get("orderId").toString());
            PurchaseOrderReceiveDTO.ReceiveItemDTO item = new PurchaseOrderReceiveDTO.ReceiveItemDTO();
            item.setItemId(Long.valueOf(row.get("itemId").toString()));
            item.setReceivedQuantity(new BigDecimal(row.get("receivedQuantity").toString()));
            grouped.computeIfAbsent(orderId, k -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    /**
     * 导入收货数据
     */
    @PostMapping("/import")
    @Log(module = "采购收货管理", businessType = BusinessType.IMPORT, bizType = "'purchase_receipt'", bizId = "#importData[0]['orderId']", action = LogActions.PUR_RECEIPT_IMPORT)
    @SaCheckPermission("purchase:receipt:import")
    public Result<Void> importReceipt(@RequestBody List<Map<String, Object>> importData) {
        // 2026-09-23（dev-20260923-004）：同样改为按订单分组一次提交，避免导入一次生成 N 张入库单
        for (Map.Entry<Long, List<PurchaseOrderReceiveDTO.ReceiveItemDTO>> entry : groupReceiptRowsByOrder(importData).entrySet()) {
            PurchaseOrderReceiveDTO dto = new PurchaseOrderReceiveDTO();
            dto.setOrderId(entry.getKey());
            dto.setItems(entry.getValue());
            purchaseOrderService.batchReceiveOrderItems(dto);
        }
        return Result.success();
    }

    /**
     * 批量校验收货导入数据（DEV-726：不落库，逐行返回校验结果，防止裸插污染数据）
     */
    @PostMapping("/batch-check")
    @SaCheckPermission("purchase:receipt:import")
    public Result<List<PurchaseBatchCheckItemVO>> batchCheck(@RequestBody List<ReceiptBatchCheckItemDTO> items) {
        List<PurchaseBatchCheckItemVO> results = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return Result.success(results);
        }
        for (ReceiptBatchCheckItemDTO item : items) {
            PurchaseBatchCheckItemVO vo = new PurchaseBatchCheckItemVO();
            vo.setRowIndex(item.getRowIndex());
            vo.setStatus("ok");

            // 1. 订单存在性 + 状态可收货
            if (item.getOrderId() == null) {
                vo.setStatus("error");
                vo.setErrorType("MISSING_REQUIRED");
                addFieldError(vo, "orderId", "MISSING_REQUIRED", "采购订单ID不能为空");
            } else {
                PurchaseOrder order = purchaseOrderService.getById(item.getOrderId());
                if (order == null) {
                    vo.setStatus("error");
                    vo.setErrorType("NOT_FOUND");
                    addFieldError(vo, "orderId", "NOT_FOUND", "采购订单不存在: " + item.getOrderId());
                } else {
                    Integer status = order.getApprovalStatus();
                    if (!Objects.equals(ApproveStatusEnum.PENDING.getValue(), status)
                            && !Objects.equals(ApproveStatusEnum.APPROVED.getValue(), status)) {
                        vo.setStatus("error");
                        vo.setErrorType("INVALID");
                        addFieldError(vo, "orderId", "INVALID", "订单当前状态不可收货（需待审批或已批准）");
                    }
                }
            }

            // 2. 明细存在性
            if (vo.getStatus().equals("ok")) {
                if (item.getItemId() == null) {
                    vo.setStatus("error");
                    vo.setErrorType("MISSING_REQUIRED");
                    addFieldError(vo, "itemId", "MISSING_REQUIRED", "订单明细ID不能为空");
                } else if (orderItemMapper.selectById(item.getItemId()) == null) {
                    vo.setStatus("error");
                    vo.setErrorType("NOT_FOUND");
                    addFieldError(vo, "itemId", "NOT_FOUND", "订单明细不存在: " + item.getItemId());
                }
            }

            // 3. 数量校验
            if (vo.getStatus().equals("ok")) {
                if (item.getReceivedQuantity() == null || item.getReceivedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    vo.setStatus("error");
                    vo.setErrorType("INVALID");
                    addFieldError(vo, "receivedQuantity", "INVALID", "收货数量必须大于0");
                }
            }

            results.add(vo);
        }
        return Result.success(results);
    }

    private void addFieldError(PurchaseBatchCheckItemVO vo, String field, String type, String message) {
        PurchaseBatchCheckItemVO.FieldError fe = new PurchaseBatchCheckItemVO.FieldError();
        fe.setField(field);
        fe.setType(type);
        fe.setMessage(message);
        vo.getErrors().add(fe);
    }

    /**
     * 下载收货导入模板
     */
    @GetMapping("/import-template")
    @SaCheckPermission("purchase:receipt:import")
    public Result<String> importTemplate() {
        return Result.success("导入模板生成功能待实现");
    }

    // ==================== 2026-09-23（dev-20260923-004）方案 A2：收货域入口 ====================

    /**
     * 批量收货（收货域主入口，A2）
     *
     * <p>与订单域 {@code POST /purchase/order/{orderId}/receive} 同一 service（batchReceiveOrderItems），
     * 但权限/日志归收货域（{@code purchase:receipt:add}），采购订单页与收货页统一走这里。
     * 一次请求可同时收多个明细，合计不超订单剩余；入库单按"一次收货"粒度生成一张（DEV-624）。
     */
    @PostMapping("/confirm-batch")
    @Log(module = "采购收货管理", businessType = BusinessType.UPDATE, bizType = "'purchase_receipt'", bizId = "#orderId", action = LogActions.PUR_RECEIPT_CONFIRM_BATCH)
    @SaCheckPermission("purchase:receipt:add")
    public Result<Void> confirmBatch(@RequestParam Long orderId, @Valid @RequestBody PurchaseOrderReceiveDTO dto) {
        dto.setOrderId(orderId);
        purchaseOrderService.batchReceiveOrderItems(dto);
        return Result.success();
    }

    /**
     * 本订单收货生成的入库单（只读）
     *
     * <p>用于收货成功后提示"已生成入库单 PO…-N（待来料检验）"，并作为 质量管理→来料检验 的跳转参数。
     */
    @GetMapping("/inbound-orders/{orderId}")
    @SaCheckPermission("purchase:receipt:view")
    public Result<List<Map<String, Object>>> inboundOrders(@PathVariable Long orderId) {
        return Result.success(receiptInboundMapper.selectByPurchaseOrder(orderId));
    }

    // ==================== 2026-09-23（dev-20260923-004）收货票据（独立权限 purchase:receipt:doc:*） ====================

    /**
     * 上传收货票据（只落磁盘临时目录，不入库；确认时由 batch-confirm 落库）
     */
    @PostMapping("/doc/upload-temp/{orderId}")
    @Log(module = "采购收货管理", businessType = BusinessType.UPDATE, bizType = "'purchase_receipt'", bizId = "#orderId", action = LogActions.PUR_RECEIPT_DOC_UPLOAD_TEMP)
    @SaCheckPermission("purchase:receipt:doc:add")
    public Result<Map<String, Object>> uploadReceiptDoc(@PathVariable Long orderId,
                                                       @RequestParam("file") MultipartFile file) {
        return Result.success(purchaseDocumentService.uploadTempFile(orderId, file));
    }

    /**
     * 查询订单的收货票据临时文件（扫描磁盘目录）
     */
    @GetMapping("/doc/disk-files/{orderId}")
    @SaCheckPermission("purchase:receipt:doc:view")
    public Result<List<Map<String, Object>>> receiptDocDiskFiles(@PathVariable Long orderId) {
        return Result.success(purchaseDocumentService.selectDiskFilesByOrderId(orderId));
    }

    /**
     * 删除收货票据临时文件
     */
    @DeleteMapping("/doc/temp-file")
    @Log(module = "采购收货管理", businessType = BusinessType.DELETE, bizType = "'purchase_receipt'", bizId = "#fileUrl", action = LogActions.PUR_RECEIPT_DOC_DELETE_TEMP)
    @SaCheckPermission("purchase:receipt:doc:delete")
    public Result<Void> deleteReceiptDocTemp(@RequestParam String fileUrl) {
        purchaseDocumentService.deleteTempFile(fileUrl);
        return Result.success();
    }

    /**
     * 收货票据落库（{"orderId":1,"files":[{fileName,fileUrl,fileSize}]}）
     *
     * <p>supplierId 由服务端从订单解析（不再信任前端传值），documentType 固定 receipt —— 与采购发票票据区分。
     */
    @PostMapping("/doc/batch-confirm")
    @Log(module = "采购收货管理", businessType = BusinessType.INSERT, bizType = "'purchase_receipt'", bizId = "#params['orderId']", action = LogActions.PUR_RECEIPT_DOC_BATCH_CONFIRM)
    @SaCheckPermission("purchase:receipt:doc:add")
    public Result<Integer> confirmReceiptDocs(@RequestBody Map<String, Object> params) {
        if (params == null || params.get("orderId") == null) {
            throw new BusinessException("orderId 不能为空");
        }
        Long orderId = Long.valueOf(params.get("orderId").toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) params.get("files");
        PurchaseOrder order = purchaseOrderService.getById(orderId);
        if (order == null) {
            throw new BusinessException("采购订单不存在: " + orderId);
        }
        return Result.success(purchaseDocumentService.batchConfirmDocuments(orderId, order.getSupplierId(), files, "receipt"));
    }
}
