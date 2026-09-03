package com.jjx.biz.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.biz.domain.entity.BizRequirement;
import com.jjx.biz.domain.query.BizRequirementQuery;
import com.jjx.biz.enums.RequirementStatusEnum;
import com.jjx.biz.enums.RequirementTypeEnum;
import com.jjx.biz.service.IBizRequirementService;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 业务需求单控制器（2026-09-02 新建）
 * 通用需求载体：变更(ECN)/新增/改善/问题
 */
@Tag(name = "业务需求管理")
@RestController
@RequestMapping("/biz/requirement")
@RequiredArgsConstructor
public class BizRequirementController extends BaseController {

    private final IBizRequirementService requirementService;

    @Operation(summary = "分页查询需求单")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/page")
    public Result<PageResult<BizRequirement>> page(BizRequirementQuery query) {
        IPage<BizRequirement> p = requirementService.page(query, getPageNum(), getPageSize());
        PageResult<BizRequirement> pr = new PageResult<>();
        pr.setRecords(p.getRecords());
        pr.setTotal(p.getTotal());
        return Result.success(pr);
    }

    @Operation(summary = "导出变更记录表")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(BizRequirementQuery query) {
        query.setRequirementType(RequirementTypeEnum.CHANGE.getValue());
        List<BizRequirement> list = requirementService.page(query, 1, Integer.MAX_VALUE).getRecords();
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("变更记录表");
            String[] headers = {"变更日期", "单号", "变更机种", "变更内容", "变更类型", "版本", "状态", "申请人"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int rowIndex = 1;
            for (BizRequirement requirement : list) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(requirement.getApplyTime() == null ? "" : requirement.getApplyTime().format(DateTimeFormatter.ISO_LOCAL_DATE));
                row.createCell(1).setCellValue(value(requirement.getRequirementNo()));
                row.createCell(2).setCellValue(value(requirement.getBizNo()));
                row.createCell(3).setCellValue(value(requirement.getDescription()));
                row.createCell(4).setCellValue(changeTypeLabel(requirement.getChangeType()));
                row.createCell(5).setCellValue(versionLabel(requirement));
                RequirementStatusEnum status = RequirementStatusEnum.getByValue(requirement.getRequirementStatus());
                row.createCell(6).setCellValue(status == null ? "" : status.getLabel());
                row.createCell(7).setCellValue(value(requirement.getApplicantName()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, (i == 3 ? 36 : 16) * 256);
            }
            wb.write(out);
            String fileName = URLEncoder.encode("变更记录表-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx", StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(out.toByteArray());
        } catch (Exception e) {
            throw new BusinessException("变更记录表导出失败: " + e.getMessage(), e);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String versionLabel(BizRequirement requirement) {
        String before = value(requirement.getVersionBefore());
        String after = value(requirement.getVersionAfter());
        return before.isEmpty() && after.isEmpty() ? "" : before + "→" + after;
    }

    private static String changeTypeLabel(String changeType) {
        if (changeType == null) return "";
        return switch (changeType) {
            case "DESIGN" -> "设计改版";
            case "PROCESS" -> "工艺调整";
            case "MATERIAL" -> "材料变更";
            case "DRAWING" -> "图纸更新";
            case "OTHER" -> "其他";
            default -> changeType;
        };
    }

    @Operation(summary = "需求单详情")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/{requirementId}")
    public Result<BizRequirement> getInfo(@PathVariable Long requirementId) {
        return Result.success(requirementService.getById(requirementId));
    }

    @Operation(summary = "新增需求单")
    @Log(module = "业务需求管理", businessType = BusinessType.INSERT, bizId = "#result.data")
    @SaCheckPermission("biz:requirement:add")
    @PostMapping
    public Result<Long> add(@RequestBody BizRequirement requirement) {
        return Result.success(requirementService.create(requirement));
    }

    @Operation(summary = "修改需求单")
    @Log(module = "业务需求管理", businessType = BusinessType.UPDATE, bizId = "#requirement.requirementId")
    @SaCheckPermission("biz:requirement:edit")
    @PutMapping
    public Result<Void> edit(@RequestBody BizRequirement requirement) {
        requirementService.update(requirement);
        return Result.success();
    }

    @Operation(summary = "删除需求单")
    @Log(module = "业务需求管理", businessType = BusinessType.DELETE, bizId = "#requirementIds")
    @SaCheckPermission("biz:requirement:remove")
    @DeleteMapping("/{requirementIds}")
    public Result<Void> remove(@PathVariable Long[] requirementIds) {
        requirementService.remove(requirementIds);
        return Result.success();
    }

    @Operation(summary = "提交评审")
    @Log(module = "业务需求管理", businessType = BusinessType.UPDATE, bizId = "#requirementId")
    @SaCheckPermission("biz:requirement:edit")
    @PutMapping("/submit/{requirementId}")
    public Result<Void> submit(@PathVariable Long requirementId) {
        requirementService.submit(requirementId);
        return Result.success();
    }

    @Operation(summary = "四部门会签（同意/不同意+意见）")
    @Log(module = "业务需求管理", businessType = BusinessType.UPDATE, bizId = "#requirementId", detail = "#approved ? '同意' : '不同意'")
    @SaCheckPermission("biz:requirement:approve")
    @PutMapping("/approval/{requirementId}")
    public Result<com.jjx.biz.domain.entity.BizRequirement> approval(@PathVariable Long requirementId,
                                                                     @RequestParam String role,
                                                                     @RequestParam Boolean approved,
                                                                     @RequestParam(required = false) String comment) {
        return Result.success(requirementService.signApproval(requirementId, role, approved, comment));
    }

    @Operation(summary = "会签记录（全部轮次）")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/approvals/{requirementId}")
    public Result<java.util.List<com.jjx.biz.domain.entity.BizRequirementApproval>> approvals(@PathVariable Long requirementId) {
        return Result.success(requirementService.listApprovals(requirementId));
    }

    @Operation(summary = "变更升版（关联产品→复制 BOM/工艺路线新版本）")
    @Log(module = "业务需求管理", businessType = BusinessType.UPDATE, bizId = "#requirementId", detail = "#newVersion")
    @SaCheckPermission("biz:requirement:edit")
    @PutMapping("/upgrade/{requirementId}")
    public Result<java.util.Map<String, Object>> upgrade(@PathVariable Long requirementId,
                                                        @RequestParam String newVersion) {
        return Result.success(requirementService.upgradeRelated(requirementId, newVersion));
    }

    @Operation(summary = "开始执行（审核通过后）")
    @Log(module = "业务需求管理", businessType = BusinessType.UPDATE, bizId = "#requirementId")
    @SaCheckPermission("biz:requirement:edit")
    @PutMapping("/execute/{requirementId}")
    public Result<Void> execute(@PathVariable Long requirementId) {
        requirementService.startExecute(requirementId);
        return Result.success();
    }

    @Operation(summary = "关闭（登记执行结果）")
    @Log(module = "业务需求管理", businessType = BusinessType.UPDATE, bizId = "#requirementId", detail = "#result")
    @SaCheckPermission("biz:requirement:edit")
    @PutMapping("/close/{requirementId}")
    public Result<Void> close(@PathVariable Long requirementId,
                              @RequestParam(required = false) String result) {
        requirementService.closeRequirement(requirementId, result);
        return Result.success();
    }

    @Operation(summary = "需求类型选项")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/type-options")
    public Result<List<Map<String, Object>>> typeOptions() {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (RequirementTypeEnum t : RequirementTypeEnum.values()) {
            list.add(java.util.Map.of("value", t.getValue(), "label", t.getLabel()));
        }
        return Result.success(list);
    }

    @Operation(summary = "状态选项")
    @SaCheckPermission("biz:requirement:view")
    @GetMapping("/status-options")
    public Result<List<Map<String, Object>>> statusOptions() {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (RequirementStatusEnum s : RequirementStatusEnum.values()) {
            list.add(java.util.Map.of("value", s.getValue(), "label", s.getLabel()));
        }
        return Result.success(list);
    }
}
