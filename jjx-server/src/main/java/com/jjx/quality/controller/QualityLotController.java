package com.jjx.quality.controller;

import com.jjx.common.core.result.Result;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.domain.entity.QualityLotItem;
import com.jjx.quality.dto.QualityLotCreateDTO;
import com.jjx.quality.dto.QualityLotItemDTO;
import com.jjx.quality.dto.QualityLotQueryDTO;
import com.jjx.quality.service.QualityLotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 检验批接口 —— dev-20260917-001
 * 说明：本任务只提供模型与基础读写；判定/入库/处置的流程接入见 005~008。
 */
@Tag(name = "质量管理-检验批")
@RestController
@RequestMapping("/quality/lot")
@RequiredArgsConstructor
public class QualityLotController {

    private final QualityLotService qualityLotService;

    @Operation(summary = "分页查询检验批（类型/状态/来源过滤）")
    @GetMapping("/page")
    public Result<Object> page(QualityLotQueryDTO query) {
        return Result.success(qualityLotService.pageLots(query));
    }

    @Operation(summary = "检验批详情")
    @GetMapping("/{lotId}")
    public Result<QualityLot> detail(@PathVariable Long lotId) {
        return Result.success(qualityLotService.getLot(lotId));
    }

    @Operation(summary = "检验项列表")
    @GetMapping("/{lotId}/items")
    public Result<List<QualityLotItem>> items(@PathVariable Long lotId) {
        return Result.success(qualityLotService.listItems(lotId));
    }

    @Operation(summary = "同一来源的所有批次（分批/复检历史）")
    @GetMapping("/by-source")
    public Result<List<QualityLot>> bySource(@RequestParam String sourceType, @RequestParam Long sourceId) {
        return Result.success(qualityLotService.listBySource(sourceType, sourceId));
    }

    @Operation(summary = "工单/工序下的检验批")
    @GetMapping("/by-order")
    public Result<List<QualityLot>> byOrder(@RequestParam Long orderId,
                                            @RequestParam(required = false) Long executionId) {
        return Result.success(qualityLotService.listByOrder(orderId, executionId));
    }

    @Operation(summary = "创建检验批（分批：同来源可多次；复检：传 parentLotId + version）")
    @PostMapping
    public Result<QualityLot> create(@RequestBody QualityLotCreateDTO dto) {
        return Result.success(qualityLotService.createLot(dto));
    }

    @Operation(summary = "保存检验项（覆盖式）")
    @PutMapping("/{lotId}/items")
    public Result<Boolean> saveItems(@PathVariable Long lotId, @RequestBody List<QualityLotItemDTO> items) {
        qualityLotService.saveItems(lotId, items);
        return Result.success(true);
    }
}
