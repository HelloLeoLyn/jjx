package com.jjx.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.purchase.domain.dto.OrderExportDTO;
import com.jjx.purchase.service.ExcelExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Tag(name = "订单导出")
public class OrderExportController {

    private final ExcelExportService excelExportService;

    @PostMapping("/export")
    @Operation(summary = "导出订购单Excel")
    @SaCheckPermission("purchase:order:export")
    public ResponseEntity<byte[]> exportOrder(@RequestBody OrderExportDTO order) {
        byte[] excelBytes = ExcelExportService.exportOrder(order);

        String fileName = URLEncoder.encode(
            "订购单_" + order.getOrderNo() + ".xlsx",
            StandardCharsets.UTF_8
        ).replaceAll("\\+", "%20");

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(excelBytes);
    }
}
