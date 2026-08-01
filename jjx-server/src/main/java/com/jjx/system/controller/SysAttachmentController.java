package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.service.ISysAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 通用附件控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/attachment")
@Tag(name = "通用附件管理")
public class SysAttachmentController {

    private final ISysAttachmentService attachmentService;

    @Operation(summary = "上传附件")
    @PostMapping("/upload")
    public Result<Long> upload(@RequestParam("file") MultipartFile file,
                               @RequestParam("bizType") String bizType,
                               @RequestParam("bizId") Long bizId,
                               @RequestParam(required = false) String traceId) {
        Long id = attachmentService.uploadAttachment(file, bizType, bizId, traceId);
        return Result.success(id);
    }

    @Operation(summary = "批量上传附件")
    @PostMapping("/batch-upload")
    public Result<List<Long>> batchUpload(@RequestParam("files") List<MultipartFile> files,
                                          @RequestParam("bizType") String bizType,
                                          @RequestParam("bizId") Long bizId) {
        List<Long> ids = attachmentService.batchUploadAttachments(files, bizType, bizId);
        return Result.success(ids);
    }

    @Operation(summary = "获取附件列表")
    @GetMapping("/list")
    public Result<List<SysAttachment>> list(@RequestParam String bizType,
                                            @RequestParam Long bizId) {
        return Result.success(attachmentService.getAttachments(bizType, bizId));
    }

    @Operation(summary = "按链路追踪ID获取附件（含来源单据文档）")
    @GetMapping("/by-trace/{traceId}")
    public Result<List<SysAttachment>> listByTrace(@PathVariable String traceId) {
        return Result.success(attachmentService.getAttachmentsByTraceId(traceId));
    }

    @Operation(summary = "删除附件")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(attachmentService.deleteAttachment(id));
    }

    @Operation(summary = "根据业务关联删除附件")
    @DeleteMapping("/delete-by-biz")
    public Result<Boolean> deleteByBiz(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.success(attachmentService.deleteAttachmentsByBiz(bizType, bizId));
    }

    @Operation(summary = "下载/预览附件")
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        SysAttachment attachment = attachmentService.getById(id);
        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }

        String filePath = attachmentService.getAttachmentFilePath(id);
        File file = new File(filePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        String encodedName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedName)
                .body(resource);
    }
}
