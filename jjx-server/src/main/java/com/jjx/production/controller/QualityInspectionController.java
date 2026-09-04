package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityInspectionQueryDTO;
import com.jjx.production.domain.dto.QualityInspectionUpdateDTO;
import com.jjx.production.domain.vo.QualityInspectionVO;
import com.jjx.production.service.QualityInspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "质量检验")
@RestController
@RequestMapping("/production/quality")
@RequiredArgsConstructor
public class QualityInspectionController {

    private final QualityInspectionService qualityService;
    /** P3-C：正式质量动作（判定/复检） */
    private final com.jjx.production.service.QualityActionService qualityActionService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<PageResult<QualityInspectionVO>> page(QualityInspectionQueryDTO query) {
        return Result.success(qualityService.page(query));
    }

    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public Result<QualityInspectionVO> getById(@PathVariable Long id) {
        return Result.success(qualityService.getById(id));
    }

    @Operation(summary = "创建检验单")
    @PostMapping
    public Result<Long> create(@RequestBody QualityInspectionCreateDTO dto) {
        return Result.success(qualityService.create(dto));
    }

    @Operation(summary = "更新检验结果")
    @PutMapping
    public Result<Void> update(@RequestBody QualityInspectionUpdateDTO dto) {
        qualityService.update(dto);
        return Result.success();
    }

    @Operation(summary = "删除检验单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        qualityService.delete(id);
        return Result.success();
    }

    @Operation(summary = "判定 PASS/FAIL（正式质量动作，P3-C；已判定不可修改）")
    @SaCheckPermission("production:quality:judge")
    @PostMapping("/{id}/judge")
    public Result<com.jjx.production.domain.vo.QualityInspectionVO> judge(@PathVariable Long id,
                                                                          @RequestBody com.jjx.production.domain.dto.QualityJudgeDTO dto) {
        return Result.success(qualityActionService.judge(id, dto));
    }

    @Operation(summary = "复检（新建一条 PENDING 质检，P3-C；不覆盖历史）")
    @SaCheckPermission("production:quality:judge")
    @PostMapping("/{id}/reinspect")
    public Result<Long> reinspect(@PathVariable Long id) {
        return Result.success(qualityActionService.reinspect(id));
    }

    @Operation(summary = "创建质检（IPQC 可带 workReportId，P3-C；后端反查校验一致性）")
    @PostMapping("/inspection")
    public Result<Long> createInspection(@RequestBody QualityInspectionCreateDTO dto) {
        return Result.success(qualityActionService.createInspection(dto));
    }

    @Operation(summary = "质量检验统计")
    @SaCheckPermission("production:quality:view")
    @GetMapping("/statistics")
    public Result<?> statistics() {
        return Result.success(qualityService.getStatistics());
    }



    @Operation(summary = "导出质检报告Excel（给客户看）")
    @SaCheckPermission("production:quality:view")
    @GetMapping("/export-excel/{id}")
    public void exportExcel(@PathVariable Long id, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        QualityInspectionVO vo = qualityService.getById(id);
        byte[] bytes = qualityService.exportExcel(id);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode("质检报告_" + vo.getInspectionNo() + ".xlsx", java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().write(bytes);
    }
}
