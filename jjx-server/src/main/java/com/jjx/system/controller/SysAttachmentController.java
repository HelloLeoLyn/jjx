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
                               @RequestParam(required = false) String traceId,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) String version,
                               @RequestParam(required = false) String remark) {
        Long id = attachmentService.uploadAttachment(file, bizType, bizId, traceId, category, version, remark);
        return Result.success(id);
    }

    @Operation(summary = "批量上传附件")
    @PostMapping("/batch-upload")
    public Result<List<Long>> batchUpload(@RequestParam("files") List<MultipartFile> files,
                                          @RequestParam("bizType") String bizType,
                                          @RequestParam("bizId") Long bizId,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String version) {
        List<Long> ids = attachmentService.batchUploadAttachments(files, bizType, bizId, category, version);
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
    public Result<List<com.jjx.system.domain.vo.AttachmentSourceVO>> listByTrace(@PathVariable String traceId) {
        return Result.success(attachmentService.getAttachmentSourcesByTraceId(traceId));
    }

    @Operation(summary = "上传产品工程文件（产品文件库，DEV-734）")
    @PostMapping("/upload-product")
    public Result<Long> uploadProduct(@RequestParam("file") MultipartFile file,
                                      @RequestParam("productCode") String productCode,
                                      @RequestParam("category") String category,
                                      @RequestParam(required = false) String version) {
        return Result.success(attachmentService.uploadProductFile(file, productCode, category, version));
    }

    @Operation(summary = "获取产品文件库（按产品编码）")
    @GetMapping("/product/{productCode}")
    public Result<List<SysAttachment>> productFiles(@PathVariable String productCode) {
        return Result.success(attachmentService.getProductFiles(productCode));
    }

    @Operation(summary = "删除附件")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(attachmentService.deleteAttachment(id));
    }

    @Operation(summary = "回收站列表（已删除附件）")
    @GetMapping("/recycle-list")
    public Result<List<SysAttachment>> recycleList() {
        return Result.success(attachmentService.getRecycled());
    }

    @Operation(summary = "恢复附件（回收站还原）")
    @PostMapping("/restore/{id}")
    public Result<Boolean> restore(@PathVariable Long id) {
        return Result.success(attachmentService.restoreAttachment(id));
    }

    @Operation(summary = "彻底删除（回收站）")
    @DeleteMapping("/permanent/{id}")
    public Result<Boolean> permanent(@PathVariable Long id) {
        return Result.success(attachmentService.permanentDelete(id));
    }

    @Operation(summary = "清理回收站过期附件（默认30天）")
    @PostMapping("/permanent-expired")
    public Result<Integer> permanentExpired(@RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.success(attachmentService.permanentDeleteExpired(days));
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

        // 图片等可预览类型用对应 MIME，浏览器内联预览；其他保持 octet-stream 下载
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String fileType = attachment.getFileType();
        if (fileType != null && fileType.startsWith("image/")) {
            mediaType = MediaType.parseMediaType(fileType);
        } else if (fileType != null && fileType.startsWith("application/pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedName)
                .body(resource);
    }
}
