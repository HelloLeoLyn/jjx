package com.jjx.engineering.archive;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.jjx.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/engineering/archive-imports")
@RequiredArgsConstructor
public class EngineeringArchiveImportController {
    private final EngineeringArchiveImportService service;

    @GetMapping
    @SaCheckPermission("engineering:archive:view")
    public Result<Page<EngineeringArchiveImport>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                        @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(service.page(pageNum, pageSize));
    }
    @GetMapping("/{id}")
    @SaCheckPermission("engineering:archive:view")
    public Result<EngineeringArchiveImport> detail(@PathVariable Long id) { return Result.success(service.get(id)); }
    @GetMapping("/image")
    @SaCheckPermission("engineering:archive:view")
    public ResponseEntity<Resource> image(@RequestParam String path) throws Exception {
        Path image = service.resolvePreviewImage(path);
        String contentType = Files.probeContentType(image);
        return ResponseEntity.ok()
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(image));
    }
    @GetMapping("/ocr-health")
    @SaCheckPermission("engineering:archive:view")
    public Result<java.util.Map<String, Object>> ocrHealth() { return Result.success(service.ocrHealth()); }
    @PostMapping("/upload")
    @SaCheckPermission("engineering:archive:import")
    public Result<EngineeringArchiveImport> upload(@RequestParam MultipartFile file) { return Result.success(service.uploadAndRecognize(file)); }
    @PostMapping("/{id}/retry")
    @SaCheckPermission("engineering:archive:import")
    public Result<EngineeringArchiveImport> retry(@PathVariable Long id) { return Result.success(service.retry(id)); }
    @PutMapping("/{id}/result")
    @SaCheckPermission("engineering:archive:import")
    public Result<EngineeringArchiveImport> updateResult(@PathVariable Long id, @RequestBody JsonNode body) { return Result.success(service.updateResult(id, body)); }
    @GetMapping("/icon-samples")
    @SaCheckPermission("engineering:archive:view")
    public Result<List<ProcessIconSample>> samples(@RequestParam(required = false) Long archiveId) { return Result.success(service.samples(archiveId)); }
    @PutMapping("/icon-samples/{sampleId}/confirm")
    @SaCheckPermission("engineering:archive:icon-map")
    public Result<ProcessIconSample> confirm(@PathVariable Long sampleId, @RequestParam Long processId) { return Result.success(service.confirmSample(sampleId, processId)); }
}
