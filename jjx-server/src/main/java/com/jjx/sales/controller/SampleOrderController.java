package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.service.ISampleOrderService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 样品单控制器
 * 独立于标准订单的样品单生命周期管理
 */
@Tag(name = "样品单管理")
@RestController
@RequestMapping("/sales/sample-order")
@RequiredArgsConstructor
public class SampleOrderController extends BaseController {

    private final ISampleOrderService sampleOrderService;
    private final com.jjx.sales.service.ISalesOrderProductService orderProductService;

    @Operation(summary = "新增样品单（直接选客户+产品明细，报价单可选）")
    @Log(module = "样品单管理", businessType = BusinessType.INSERT, bizType = "'sample'", bizId = "#result.data.orderId", traceId = "#result.data.traceId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).CREATED.getLabel()")
    @SaCheckPermission("sales:sample:add")
    @PostMapping
    public Result<SalesOrder> create(@Valid @RequestBody com.jjx.sales.domain.dto.SampleOrderCreateDTO dto) {
        return Result.success(sampleOrderService.createSample(dto));
    }

    @Operation(summary = "更新样品单（驳回后编辑：仅样品需求已创建状态可编辑，明细全量替换）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", traceId = "#result.data.traceId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).CREATED.getLabel()", detail = "#result.data.detailMessage")
    @SaCheckPermission("sales:sample:edit")
    @PutMapping("/{orderId}")
    public Result<SalesOrder> update(@PathVariable Long orderId,
                                     @Valid @RequestBody com.jjx.sales.domain.dto.SampleOrderUpdateDTO dto) {
        return Result.success(sampleOrderService.updateSampleOrder(orderId, dto));
    }

    @Operation(summary = "从报价单创建样品单")
    @Log(module = "样品单管理", businessType = BusinessType.INSERT, bizType = "'sample'", bizId = "#result.data.orderId", traceId = "#result.data.traceId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).CREATED.getLabel()")
    @SaCheckPermission("sales:sample:add")
    @PostMapping("/create-from-quotation/{quotationId}")
    public Result<SalesOrder> createFromQuotation(
            @PathVariable Long quotationId,
            @RequestParam(required = false) Integer sampleQty,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) String deliveryDate,
            @RequestParam(required = false) String contactPerson,
            @RequestParam(required = false) String contactPhone,
            @RequestParam(required = false) String techRequirement) {
        return Result.success(sampleOrderService.createFromQuotation(
                quotationId, sampleQty, remark, deliveryDate, contactPerson, contactPhone, techRequirement));
    }

    @Operation(summary = "复制样品单（仅已完成/已取消终态单，一键生成新草稿单）")
    @Log(module = "样品单管理", businessType = BusinessType.INSERT, bizType = "'sample'", bizId = "#result.data.orderId", traceId = "#result.data.traceId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).CREATED.getLabel()")
    @SaCheckPermission("sales:sample:add")
    @PostMapping("/copy/{orderId}")
    public Result<SalesOrder> copy(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.copySampleOrder(orderId));
    }

    @Operation(summary = "样品单详情（含明细）")
    @SaCheckPermission(value = {"sales:sample:view", "engineering:sample:workbench"}, mode = SaMode.OR)
    @GetMapping("/{orderId}")
    public Result<SalesOrder> getInfo(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.selectById(orderId));
    }

    @Operation(summary = "样品单明细（转量产标准化窗口用）")
    @SaCheckPermission(value = {"sales:sample:view", "engineering:sample:workbench"}, mode = SaMode.OR)
    @GetMapping("/products/{orderId}")
    public Result<java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO>> getProducts(@PathVariable Long orderId) {
        return Result.success(orderProductService.getListByOrderId(orderId));
    }

    @Operation(summary = "转量产就绪检查（产品/BOM/工艺路线/菲林清单）")
    @SaCheckPermission(value = {"sales:sample:view", "engineering:sample:workbench"}, mode = SaMode.OR)
    @GetMapping("/convert-check/{orderId}")
    public Result<com.jjx.sales.domain.vo.SampleConvertCheckVO> convertCheck(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.checkConvertReady(orderId));
    }

    @Operation(summary = "样品单列表")
    @SaCheckPermission(value = {"sales:sample:view", "engineering:sample:workbench"}, mode = SaMode.OR)
    @GetMapping("/list")
    public Result<List<SalesOrder>> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer sampleStatus,
            @RequestParam(required = false) Long salesPersonId,
            @RequestParam(required = false) Boolean hasAcceptor) {
        return Result.success(sampleOrderService.selectSampleList(customerId, sampleStatus, salesPersonId, hasAcceptor));
    }

    @Operation(summary = "印刷工序历史输入联想（1225：印刷名称/色号/油墨）")
    @SaCheckPermission(value = {"sales:sample:view", "engineering:sample:workbench"}, mode = SaMode.OR)
    @GetMapping("/process/history")
    public Result<java.util.Map<String, java.util.List<String>>> processHistory() {
        return Result.success(sampleOrderService.processHistory());
    }

    @Operation(summary = "样品单申请打样")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).REQUEST.getLabel()", detail = "#attachmentIds")
    @SaCheckPermission("sales:sample:edit")
    @PutMapping("/submit-request/{orderId}")
    public Result<SalesOrder> submitRequest(@PathVariable Long orderId,
                                           @RequestParam(required = false) String attachmentIds) {
        return Result.success(sampleOrderService.submitRequest(orderId));
    }

    @Operation(summary = "样品单审核通过（进入工程打样）")
    @Log(module = "样品单管理", businessType = BusinessType.APPROVE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).ENGINEERING.getLabel()", detail = "#attachmentIds")
    @SaCheckPermission("sales:sample:approve")
    @PutMapping("/approve/{orderId}")
    public Result<SalesOrder> approve(@PathVariable Long orderId,
                                      @RequestParam(required = false) String remark,
                                      @RequestParam(required = false) String attachmentIds) {
        return Result.success(sampleOrderService.approveReview(orderId, remark));
    }

    @Operation(summary = "样品单审核驳回")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).REJECTED.getLabel()", detail = "#attachmentIds")
    @SaCheckPermission("sales:sample:approve")
    @PutMapping("/reject-review/{orderId}")
    public Result<SalesOrder> rejectReview(@PathVariable Long orderId,
                                           @RequestParam(required = false) String remark,
                                           @RequestParam(required = false) String attachmentIds) {
        return Result.success(sampleOrderService.rejectReview(orderId, remark));
    }

    @Operation(summary = "工程接单（记录工程备注）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).ENGINEERING.getLabel()")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/start-engineering/{orderId}")
    public Result<SalesOrder> startEngineering(@PathVariable Long orderId,
                                               @RequestParam(required = false) String engineeringNote) {
        return Result.success(sampleOrderService.startEngineering(orderId, engineeringNote));
    }

    @Operation(summary = "工程标记样品完成（待送样）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).SAMPLE_READY.getLabel()")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/mark-ready/{orderId}")
    public Result<SalesOrder> markReady(@PathVariable Long orderId,
                                        @RequestParam(required = false) Integer sampleQty) {
        return Result.success(sampleOrderService.markSampleReady(orderId, sampleQty));
    }

    @Operation(summary = "销售送样登记")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).SAMPLE_SENT.getLabel()", detail = "#attachmentIds")
    @SaCheckPermission("sales:sample:deliver")
    @PutMapping("/send-sample/{orderId}")
    public Result<SalesOrder> sendSample(@PathVariable Long orderId,
                                         @RequestParam(required = false) String trackingNo,
                                         // 仅供 @Log SpEL 取值，业务方法无需使用
                                         @RequestParam(required = false) String attachmentIds) {
        return Result.success(sampleOrderService.sendSample(orderId, trackingNo));
    }

    @Operation(summary = "客户确认样品OK")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).CONFIRMED.getLabel()", detail = "#attachmentIds")
    @SaCheckPermission("sales:sample:confirm")
    @PutMapping("/confirm/{orderId}")
    public Result<SalesOrder> confirm(@PathVariable Long orderId,
                                      @RequestParam(required = false) String clientName,
                                      // 仅供 @Log SpEL 取值，业务方法无需使用
                                      @RequestParam(required = false) String attachmentIds) {
        return Result.success(sampleOrderService.confirmSample(orderId, clientName));
    }

    @Operation(summary = "客户退回样品（多轮迭代）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).REJECTED.getLabel()", detail = "#attachmentIds")
    @SaCheckPermission("sales:sample:confirm")
    @PutMapping("/reject-sample/{orderId}")
    public Result<SalesOrder> rejectSample(@PathVariable Long orderId,
                                           @RequestParam(required = false) String rejectReason,
                                           // 仅供 @Log SpEL 取值，业务方法无需使用
                                           @RequestParam(required = false) String attachmentIds) {
        return Result.success(sampleOrderService.rejectSample(orderId, rejectReason));
    }

    @Operation(summary = "样品转量产（生成标准订单，可传产品标准化items）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).TRANSFERRED.getLabel()")
    @SaCheckPermission("sales:sample:convert")
    @PutMapping("/convert-to-production/{orderId}")
    public Result<SalesOrder> convertToProduction(@PathVariable Long orderId,
            @RequestBody(required = false) java.util.List<com.jjx.sales.domain.dto.SampleConvertItemDTO> items) {
        return Result.success(sampleOrderService.convertToProduction(orderId, items));
    }

    /**
     * 样品单作废
     */
    @Operation(summary = "样品单作废")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).CANCELLED.getLabel()")
    @SaCheckPermission("sales:sample:edit")
    @PutMapping("/cancel/{orderId}")
    public Result<SalesOrder> cancel(@PathVariable Long orderId,
                                     @RequestParam(required = false) String cancelReason) {
        return Result.success(sampleOrderService.cancelSample(orderId, cancelReason));
    }

    /**
     * 退回后重新打样（REJECTED → ENGINEERING）
     */
    @Operation(summary = "退回后重新打样")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).ENGINEERING.getLabel()")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/restart-engineering/{orderId}")
    public Result<SalesOrder> restartEngineering(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.restartEngineering(orderId));
    }

    /**
     * 工程接单确认
     */
    @Operation(summary = "工程接单确认")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).ENGINEERING.getLabel()")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/accept-engineering/{orderId}")
    public Result<SalesOrder> acceptEngineering(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.acceptEngineering(orderId));
    }

    /**
     * 工程拒单
     */
    @Operation(summary = "工程拒单")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).REQUEST.getLabel()", detail = "#attachmentIds")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/reject-engineering/{orderId}")
    public Result<SalesOrder> rejectEngineering(@PathVariable Long orderId,
                                                @RequestParam String rejectReason) {
        return Result.success(sampleOrderService.rejectEngineering(orderId, rejectReason));
    }

    /**
     * 更新打样当前工序
     */
    @Operation(summary = "更新打样当前工序（材料JSON走body，避免长URL，8-03改DTO）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/update-process/{orderId}")
    public Result<SalesOrder> updateProcess(@PathVariable Long orderId,
                                            @RequestBody(required = false) com.jjx.sales.dto.save.SampleProcessDTO dto) {
        return Result.success(sampleOrderService.updateSampleProcess(orderId,
                dto != null ? dto.getProcess() : null,
                dto != null ? dto.getMaterials() : null,
                dto != null ? dto.getProcessNote() : null,
                dto != null ? dto.getDurationMinutes() : null));
    }

    /**
     * 保存打样工序计划（方案A：多选作业项目形成计划，整单覆盖当前轮次）
     */
    @Operation(summary = "保存打样工序计划（多选作业项目，整单覆盖当前轮次）")
    // @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/processes/{orderId}/plan")
    public Result<List<com.jjx.sales.domain.entity.SalesSampleProcess>> saveProcessPlan(
            @PathVariable Long orderId,
            @RequestBody com.jjx.sales.dto.save.SampleProcessPlanDTO dto) {
        return Result.success(sampleOrderService.saveProcessPlan(orderId, dto));
    }

    /**
     * 推进打样工序状态（开始/完成）
     */
    @Operation(summary = "推进打样工序状态（开始/完成，可带耗时/说明/材料）")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/processes/{orderId}/item/{processId}/status")
    public Result<com.jjx.sales.domain.entity.SalesSampleProcess> updateProcessItemStatus(
            @PathVariable Long orderId,
            @PathVariable Long processId,
            @RequestBody(required = false) com.jjx.sales.dto.save.SampleProcessItemStatusDTO dto) {
        return Result.success(sampleOrderService.updateProcessItemStatus(orderId, processId,
                dto != null ? dto : new com.jjx.sales.dto.save.SampleProcessItemStatusDTO()));
    }

    /**
     * 查询打样工序历史
     */
    @Operation(summary = "查询打样工序历史（可传 roundNo 按轮次过滤，DEV-500）")
    @GetMapping("/processes/{orderId}")
    public Result<List<com.jjx.sales.domain.entity.SalesSampleProcess>> listProcesses(
            @PathVariable Long orderId,
            @RequestParam(required = false) Integer roundNo) {
        return Result.success(sampleOrderService.listSampleProcesses(orderId, roundNo));
    }

    @Operation(summary = "打样汇总（总工时+材料成本估算）")
    @GetMapping("/summary/{orderId}")
    public Result<java.util.Map<String, Object>> summary(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.getSampleSummary(orderId));
    }

    /**
     * 查询打样BOM物料清单
     */
    @Operation(summary = "查询打样BOM物料清单")
    @GetMapping("/bom/{orderId}")
    public Result<List<com.jjx.sales.domain.entity.SalesSampleBom>> listBom(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.listSampleBom(orderId));
    }

    /**
     * 保存打样BOM物料清单
     */
    @Operation(summary = "保存打样BOM物料清单")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/bom/{orderId}")
    public Result<List<com.jjx.sales.domain.entity.SalesSampleBom>> saveBom(@PathVariable Long orderId,
                                                                            @RequestParam(required = false) Integer roundNo,
                                                                            @RequestBody List<com.jjx.sales.domain.entity.SalesSampleBom> items) {
        return Result.success(sampleOrderService.saveSampleBom(orderId, roundNo, items));
    }

    /**
     * 删除单条打样BOM
     */
    @Operation(summary = "删除单条打样BOM")
    @DeleteMapping("/bom/{bomId}")
    public Result<Boolean> deleteBomItem(@PathVariable Long bomId) {
        return Result.success(sampleOrderService.deleteSampleBomItem(bomId));
    }

    /**
     * 录入打样成本/工时
     */
    @Operation(summary = "录入打样成本/工时")
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId")
    @SaCheckPermission(value = {"sales:sample:engineering", "engineering:sample:workbench"}, mode = SaMode.OR)
    @PutMapping("/record-cost/{orderId}")
    public Result<SalesOrder> recordCost(@PathVariable Long orderId,
                                         @RequestParam(required = false) java.math.BigDecimal cost,
                                         @RequestParam(required = false) java.math.BigDecimal workHours) {
        return Result.success(sampleOrderService.recordSampleCost(orderId, cost, workHours));
    }

    /**
     * 查询打样轮次快照列表
     */
    @Operation(summary = "产品资料转移（DEV-505：建档产品/BOM/工艺路线，状态初始化，事件通知+派任务）")
    @Deprecated // 2026-08-10 DEV-764：资料转移统一入口已改为 /sample/transfer/*（轻量版弹窗+对照版），此接口保留兼容，二期移除
    @Log(module = "样品单管理", businessType = BusinessType.UPDATE, bizType = "'sample'", bizId = "#orderId", bizStatus = "T(com.jjx.sales.enums.SampleOrderStatusEnum).CONFIRMED.getLabel()")
    @SaCheckPermission("sales:sample:convert")
    @PostMapping("/transfer/{orderId}")
    public Result<java.util.Map<String, Object>> transfer(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.transferMaterials(orderId));
    }

    @Operation(summary = "查询打样轮次快照")
    @SaCheckPermission(value = {"sales:sample:view", "engineering:sample:workbench"}, mode = SaMode.OR)
    @GetMapping("/rounds/{orderId}")
    public Result<List<com.jjx.sales.domain.entity.SalesSampleRound>> rounds(@PathVariable Long orderId) {
        return Result.success(sampleOrderService.listSampleRounds(orderId));
    }

    @Operation(summary = "获取样品单状态选项")
    @SaCheckPermission(value = {"sales:sample:view", "engineering:sample:workbench"}, mode = SaMode.OR)
    @GetMapping("/status-options")
    public Result<List<Map<String, Object>>> getStatusOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        for (SampleOrderStatusEnum status : SampleOrderStatusEnum.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("value", status.getValue());
            item.put("label", status.getLabel());
            item.put("description", status.getDescription());
            item.put("terminal", status.isTerminal());
            options.add(item);
        }
        return Result.success(options);
    }
}
