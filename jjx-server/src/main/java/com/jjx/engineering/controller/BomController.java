package com.jjx.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.domain.entity.Bom;
import com.jjx.engineering.service.IBomService;
import com.jjx.product.domain.entity.ProductBomItem;
import com.jjx.product.mapper.ProductBomItemMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "工程BOM管理")
@RestController
@RequestMapping("/engineering/bom")
@RequiredArgsConstructor
public class BomController {

    private final IBomService bomService;
    private final ProductBomItemMapper bomItemMapper;

    @Operation(summary = "获取BOM详情（含明细）")
    @SaCheckPermission("engineering:bom:view")
    @GetMapping("/{bomId}")
    public Result<Map<String, Object>> getBomDetail(@PathVariable Long bomId) {
        Bom bom = bomService.getById(bomId);
        if (bom == null) {
            throw new com.jjx.common.exception.BusinessException("BOM不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("bomId", bom.getBomId());
        result.put("bomCode", bom.getBomCode());
        result.put("bomName", bom.getBomName());
        result.put("bomType", bom.getBomType());
        result.put("bomVersion", bom.getBomVersion());
        result.put("productId", bom.getProductId());
        result.put("approveStatus", bom.getApproveStatus());
        result.put("isCurrent", bom.getIsCurrent());
        result.put("remark", bom.getRemark());
        result.put("createBy", bom.getCreateBy());
        result.put("createTime", bom.getCreateTime());
        List<ProductBomItem> items = bomItemMapper.selectList(
                new LambdaQueryWrapper<ProductBomItem>()
                        .eq(ProductBomItem::getBomId, bomId)
                        .orderByAsc(ProductBomItem::getItemOrder));
        result.put("items", items == null ? List.of() : items);
        return Result.success(result);
    }

    @Operation(summary = "获取BOM列表")
    @GetMapping("/page")
    public Result<?> page() {
        return Result.success(bomService.listPage(null));
    }

    @Operation(summary = "BOM提交审核")
    @PutMapping("/submit/{bomId}")
    public Result<Void> submit(@PathVariable Long bomId) {
        bomService.submitApprove(bomId);
        return Result.success();
    }

    @Operation(summary = "审核BOM")
    @PutMapping("/approve/{bomId}")
    public Result<Void> approve(@PathVariable Long bomId, @RequestBody(required = false) Map<String, Object> dto) {
        String remark = dto != null && dto.get("remark") != null ? String.valueOf(dto.get("remark")) : null;
        bomService.approve(bomId, remark);
        return Result.success();
    }

    @Operation(summary = "驳回BOM")
    @PutMapping("/reject/{bomId}")
    public Result<Void> reject(@PathVariable Long bomId, @RequestBody(required = false) Map<String, Object> dto) {
        String remark = dto != null && dto.get("remark") != null ? String.valueOf(dto.get("remark")) : null;
        bomService.reject(bomId, remark);
        return Result.success();
    }
}
