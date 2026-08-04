package com.jjx.production.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.EngineeringBom;
import com.jjx.product.domain.entity.EngineeringBomItem;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.mapper.ProductionOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Tag(name = "成本核算")
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/cost")
public class ProductionCostController {

    private final ProductionOrderMapper productionOrderMapper;
    private final ProductMapper productMapper;
    private final EngineeringBomMapper productBomMapper;
    private final EngineeringBomItemMapper productBomItemMapper;

    @Operation(summary = "工单成本列表")
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<ProductionOrder>()
                .eq(ProductionOrder::getOrderType, "WORK_ORDER")
                .eq(productId != null, ProductionOrder::getProductId, productId)
                .ge(startDate != null, ProductionOrder::getPlanStartDate, startDate)
                .le(endDate != null, ProductionOrder::getPlanEndDate, endDate)
                .orderByDesc(ProductionOrder::getCreateTime)
                .last("LIMIT 100");

        List<ProductionOrder> orders = productionOrderMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ProductionOrder order : orders) {
            Map<String, Object> item = new HashMap<>();
            item.put("orderId", order.getOrderId());
            item.put("orderNo", order.getOrderNo());
            item.put("productName", order.getProductName());
            item.put("productCode", order.getProductCode());
            item.put("plannedQuantity", order.getPlannedQuantity());
            item.put("completedQuantity", order.getCompletedQuantity());
            item.put("materialCost", order.getMaterialCost() != null ? order.getMaterialCost() : BigDecimal.ZERO);
            item.put("laborCost", order.getLaborCost() != null ? order.getLaborCost() : BigDecimal.ZERO);
            item.put("totalCost", order.getTotalCost() != null ? order.getTotalCost() : BigDecimal.ZERO);

            // 单位成本
            BigDecimal qty = order.getCompletedQuantity() != null && order.getCompletedQuantity().compareTo(BigDecimal.ZERO) > 0
                    ? order.getCompletedQuantity() : order.getPlannedQuantity();
            BigDecimal unitCost = qty.compareTo(BigDecimal.ZERO) > 0
                    ? (order.getTotalCost() != null ? order.getTotalCost() : BigDecimal.ZERO).divide(qty, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            item.put("unitCost", unitCost);

            // BOM标准成本
            Product product = productMapper.selectById(order.getProductId());
            BigDecimal bomCost = BigDecimal.ZERO;
            if (order.getProductId() != null) {
                EngineeringBom bom = productBomMapper.selectOne(
                        new LambdaQueryWrapper<EngineeringBom>()
                                .eq(EngineeringBom::getProductId, order.getProductId())
                                .eq(EngineeringBom::getIsCurrent, 1)
                                .last("LIMIT 1"));
                if (bom != null) {
                    List<EngineeringBomItem> bomItems = productBomItemMapper.selectList(
                            new LambdaQueryWrapper<EngineeringBomItem>()
                                    .eq(EngineeringBomItem::getBomId, bom.getBomId()));
                    for (EngineeringBomItem bi : bomItems) {
                        BigDecimal biQty = bi.getQuantity() != null ? bi.getQuantity() : BigDecimal.ZERO;
                        bomCost = bomCost.add(biQty);
                    }
                }
            }
            item.put("bomMaterialCost", bomCost);
            item.put("standardCost", product != null && product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO);
            item.put("costDiff", product != null && product.getCostPrice() != null && unitCost.compareTo(BigDecimal.ZERO) > 0
                    ? unitCost.subtract(product.getCostPrice()) : BigDecimal.ZERO);

            item.put("orderStatus", order.getOrderStatus());
            item.put("planStartDate", order.getPlanStartDate());
            item.put("planEndDate", order.getPlanEndDate());
            item.put("createTime", order.getCreateTime());

            result.add(item);
        }

        return Result.success(result);
    }

    @Operation(summary = "成本统计汇总")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        List<ProductionOrder> orders = productionOrderMapper.selectList(
                new LambdaQueryWrapper<ProductionOrder>()
                        .eq(ProductionOrder::getOrderType, "WORK_ORDER")
                        .last("LIMIT 1000"));

        BigDecimal totalMaterial = BigDecimal.ZERO;
        BigDecimal totalLabor = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        int count = 0;

        for (ProductionOrder order : orders) {
            if (order.getTotalCost() != null) {
                totalMaterial = totalMaterial.add(order.getMaterialCost() != null ? order.getMaterialCost() : BigDecimal.ZERO);
                totalLabor = totalLabor.add(order.getLaborCost() != null ? order.getLaborCost() : BigDecimal.ZERO);
                totalCost = totalCost.add(order.getTotalCost());
                count++;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalOrders", count);
        summary.put("totalMaterialCost", totalMaterial);
        summary.put("totalLaborCost", totalLabor);
        summary.put("totalCost", totalCost);
        summary.put("avgOrderCost", count > 0 ? totalCost.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        return Result.success(summary);
    }
}
