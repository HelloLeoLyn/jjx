package com.jjx.purchase.controller;

import cn.dev33.satoken.annotation.*;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.domain.dto.*;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.service.impl.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 与订单/收货同权限的只读加工校核；采用方案不创建加工单或库存。 */
@RestController
@RequestMapping("/purchase/processing")
@RequiredArgsConstructor
public class PurchaseProcessingController {
    private final PurchaseProcessingService processing;
    private final PurchaseOrderServiceImpl orders;
    private final PurchaseOrderItemMapper items;
    @PostMapping("/preview")
    @SaCheckPermission(value={"purchase:order:add","purchase:order:edit","purchase:plan:add","purchase:plan:confirm"},mode=SaMode.OR)
    public Result<PurchaseOrderItemDTO> preview(@RequestBody PurchaseOrderItemDTO item){
        var order=new PurchaseOrderDTO();order.setItems(List.of(item));return Result.success(orders.previewPricing(order).getItems().get(0));
    }
    @GetMapping("/item/{itemId}")
    @SaCheckPermission("purchase:receipt:view")
    public Result<PurchaseProcessingService.History> history(@PathVariable Long itemId){return Result.success(processing.history(require(itemId)));}
    public record ReceiptRequest(Long itemId,List<PurchaseProcessingPlan.ReceiptGroup> groups) {}
    @PostMapping("/receipt-preview")
    @SaCheckPermission(value={"purchase:receipt:view","purchase:receipt:add"},mode=SaMode.OR)
    public Result<PurchaseProcessingService.ReceiptPreview> receipt(@RequestBody ReceiptRequest request){
        return Result.success(processing.preview(require(request.itemId()),request.groups()));
    }
    private com.jjx.purchase.domain.entity.PurchaseOrderItem require(Long id){
        var item=id==null?null:items.selectById(id);if(item==null)throw new BusinessException("采购明细不存在");return item;
    }
}
