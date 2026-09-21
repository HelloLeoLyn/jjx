package com.jjx.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.service.EngineeringResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/engineering/resources")
@RequiredArgsConstructor
public class EngineeringResourceController {
    private final EngineeringResourceService service;

    @GetMapping("/screen-frames") @SaCheckPermission("engineering:resource:view")
    public Result<Map<String,Object>> frames(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,
            @RequestParam(required=false) Integer pageNum,@RequestParam(required=false) Integer pageSize){
        return Result.success(service.pageFrames(keyword,status,pageNum,pageSize));}
    @PostMapping("/screen-frames") @SaCheckPermission("engineering:resource:edit")
    public Result<Long> saveFrame(@RequestBody Map<String,Object> body){return Result.success(service.saveFrame(body));}
    @PostMapping("/screen-plates") @SaCheckPermission("engineering:resource:edit")
    public Result<Long> plate(@RequestBody Map<String,Object> body){return Result.success(service.createPlate(body));}
    @PostMapping("/screen-frames/{id}/wash") @SaCheckPermission("engineering:resource:maintain")
    public Result<Void> wash(@PathVariable Long id,@RequestBody(required=false) Map<String,Object> body){service.washFrame(id,body==null?null:String.valueOf(body.getOrDefault("description","")));return Result.success();}
    @PostMapping("/screen-frames/{id}/actions") @SaCheckPermission("engineering:resource:maintain")
    public Result<Void> frameAction(@PathVariable Long id,@RequestBody Map<String,Object> body){service.actOnFrame(id,body);return Result.success();}

    @GetMapping("/dies") @SaCheckPermission("engineering:resource:view")
    public Result<Map<String,Object>> dies(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,
            @RequestParam(required=false) Integer pageNum,@RequestParam(required=false) Integer pageSize){
        return Result.success(service.pageDies(keyword,status,pageNum,pageSize));}
    @PostMapping("/dies") @SaCheckPermission("engineering:resource:edit")
    public Result<Long> saveDie(@RequestBody Map<String,Object> body){return Result.success(service.saveDie(body));}
    @PostMapping("/dies/{id}/actions") @SaCheckPermission("engineering:resource:maintain")
    public Result<Long> dieAction(@PathVariable Long id,@RequestBody Map<String,Object> body){return Result.success(service.actOnDie(id,body));}

    @Operation(summary = "下载网版导入模板")
    @SaCheckPermission("engineering:resource:edit")
    @GetMapping("/screen-frames/import-template")
    public void frameImportTemplate(jakarta.servlet.http.HttpServletResponse response) {
        com.jjx.common.utils.ExcelUtils.downloadTemplate(response, com.jjx.engineering.domain.dto.ScreenFrameImportDTO.class, "网版导入模板");
    }

    @Operation(summary = "导入网版（网框 + 当前版面，按网框编号 upsert）")
    @SaCheckPermission("engineering:resource:edit")
    @PostMapping("/screen-frames/import")
    public Result<Map<String,Object>> importFrames(org.springframework.web.multipart.MultipartFile file) throws Exception {
        List<com.jjx.engineering.domain.dto.ScreenFrameImportDTO> rows =
                com.jjx.common.utils.ExcelUtils.importExcel(file, com.jjx.engineering.domain.dto.ScreenFrameImportDTO.class);
        return Result.success(service.importScreenFrames(rows));
    }

    @Operation(summary = "下载刀模导入模板")
    @SaCheckPermission("engineering:die:import")
    @GetMapping("/dies/import-template")
    public void dieImportTemplate(jakarta.servlet.http.HttpServletResponse response) {
        com.jjx.common.utils.ExcelUtils.downloadTemplate(response, com.jjx.engineering.domain.dto.DieImportDTO.class, "刀模导入模板");
    }

    @Operation(summary = "导入刀模（按刀模编号 upsert）")
    @SaCheckPermission("engineering:die:import")
    @PostMapping("/dies/import")
    public Result<Map<String,Object>> importDies(org.springframework.web.multipart.MultipartFile file) throws Exception {
        List<com.jjx.engineering.domain.dto.DieImportDTO> rows =
                com.jjx.common.utils.ExcelUtils.importExcel(file, com.jjx.engineering.domain.dto.DieImportDTO.class);
        return Result.success(service.importDies(rows));
    }

    @GetMapping("/{type}/{id}/products") @SaCheckPermission("engineering:resource:view")
    public Result<List<Map<String,Object>>> products(@PathVariable String type,@PathVariable Long id){return Result.success(service.products(type,id));}
    @PutMapping("/{type}/{id}/products") @SaCheckPermission("engineering:resource:edit")
    public Result<Void> replaceProducts(@PathVariable String type,@PathVariable Long id,@RequestBody Map<String,Object> body){service.replaceProducts(type,id,body);return Result.success();}
    @GetMapping("/{type}/{id}/maintenance") @SaCheckPermission("engineering:resource:view")
    public Result<List<Map<String,Object>>> maintenance(@PathVariable String type,@PathVariable Long id){return Result.success(service.maintenance(type,id));}
}
