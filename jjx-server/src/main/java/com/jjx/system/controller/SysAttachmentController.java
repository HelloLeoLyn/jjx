package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.service.ISysAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import cn.dev33.satoken.stp.StpUtil;

import jakarta.servlet.http.HttpServletResponse;
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
    private final com.jjx.system.service.CustomerDocUploadService customerDocUploadService;

    @Operation(summary = "确认上传一行客供资料（一个文件对应一个产品）")
    @PostMapping("/customer-doc-upload")
    @Log(module = "附件", businessType = BusinessType.INSERT, bizType = "'sys_attachment'", bizId = "#result.data.id", action = "上传客供资料")
    public Result<SysAttachment> uploadCustomerDoc(@RequestParam("file") MultipartFile file,
            @RequestParam String bizType, @RequestParam Long bizId,
            @RequestParam String productCode, @RequestParam String docType,
            @RequestParam(required = false) String traceId) {
        String permission = switch (bizType) {
            case "inquiry" -> "sales:inquiry:edit";
            case "quotation" -> "sales:quotation:edit";
            case "sales_order" -> "sales:order:edit";
            case "sample" -> "sales:sample:edit";
            default -> throw new com.jjx.common.exception.BusinessException("不支持的来源单据");
        };
        StpUtil.checkPermission(permission);
        return Result.success(customerDocUploadService.upload(file, bizType, bizId, productCode, docType, traceId));
    }

    @Operation(summary = "上传附件")
    @PostMapping("/upload")
    @Log(module = "附件", businessType = BusinessType.INSERT, bizType = "'sys_attachment'", bizId = "#result.data", action = "上传附件")
    public Result<Long> upload(@RequestParam("file") MultipartFile file,
                               @RequestParam("bizType") String bizType,
                               @RequestParam("bizId") Long bizId,
                               @RequestParam(required = false) String traceId,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) String version,
                               @RequestParam(required = false) String remark,
                               HttpServletResponse response) {
        Long id = attachmentService.uploadAttachment(file, bizType, bizId, traceId, category, version, remark);
        addDuplicateHint(response, List.of(id));
        return Result.success(id);
    }

    @Operation(summary = "批量上传附件")
    @PostMapping("/batch-upload")
    @Log(module = "附件", businessType = BusinessType.INSERT, bizType = "'sys_attachment'", bizId = "#bizId", action = "批量上传附件")
    public Result<List<Long>> batchUpload(@RequestParam("files") List<MultipartFile> files,
                                          @RequestParam("bizType") String bizType,
                                          @RequestParam("bizId") Long bizId,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String version,
                                          HttpServletResponse response) {
        List<Long> ids = attachmentService.batchUploadAttachments(files, bizType, bizId, category, version);
        addDuplicateHint(response, ids);
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
    @Log(module = "附件", businessType = BusinessType.INSERT, bizType = "'sys_attachment'", bizId = "#result.data", action = "上传产品工程文件")
    public Result<Long> uploadProduct(@RequestParam("file") MultipartFile file,
                                      @RequestParam("productCode") String productCode,
                                      @RequestParam("category") String category,
                                      @RequestParam(required = false) String version,
                                      @RequestParam(required = false) String drawingNo,
                                      @RequestParam(required = false) String drawingName,
                                      @RequestParam(required = false) String fileRole,
                                      HttpServletResponse response) {
        Long id = attachmentService.uploadProductFile(file, productCode, category, version,
                drawingNo, drawingName, fileRole);
        addDuplicateHint(response, List.of(id));
        return Result.success(id);
    }

    private void addDuplicateHint(HttpServletResponse response, List<Long> ids) {
        int count = ids.stream().mapToInt(attachmentService::detectUploadedDuplicates).sum();
        response.setHeader("X-Attachment-Duplicate-Count", String.valueOf(count));
    }

    @Operation(summary = "按SHA-256检测同范围同内容附件（只读，不含回收站）")
    @GetMapping("/by-sha256")
    public Result<List<SysAttachment>> bySha256(@RequestParam String sha256,
                                               @RequestParam(required = false) Long productId,
                                               @RequestParam(required = false) String bizType,
                                               @RequestParam(required = false) Long bizId,
                                               @RequestParam(required = false) Long excludeId) {
        if (productId != null) {
            if (bizType != null || bizId != null) {
                throw new com.jjx.common.exception.BusinessException("产品范围和业务范围只能选择一种");
            }
            bizType = "product";
            bizId = productId;
        }
        return Result.success(attachmentService.findBySha256(sha256, bizType, bizId, excludeId));
    }

    @lombok.Data
    public static class DrawingMetadata {
        private String drawingNo;
        private String drawingName;
        private String version;
        private String fileRole;
        private String category;
    }

    @Operation(summary = "补录或修改未下发图纸文件的编号、版本与用途")
    @PutMapping("/{id}/drawing-metadata")
    @Log(module = "附件", businessType = BusinessType.UPDATE, bizType = "'sys_attachment'", bizId = "#id", action = "修改图纸元数据")
    @cn.dev33.satoken.annotation.SaCheckPermission("product:edit")
    public Result<Boolean> drawingMetadata(@PathVariable Long id, @RequestBody DrawingMetadata body) {
        return Result.success(attachmentService.updateDrawingMetadata(id, body.getDrawingNo(),
                body.getDrawingName(), body.getVersion(), body.getFileRole(), body.getCategory()));
    }

    @Operation(summary = "获取产品文件库（按产品编码）")
    @GetMapping("/product/{productCode}")
    public Result<List<SysAttachment>> productFiles(@PathVariable String productCode) {
        return Result.success(attachmentService.getProductFiles(productCode));
    }

    @Operation(summary = "删除附件")
    @DeleteMapping("/{id}")
    @Log(module = "附件", businessType = BusinessType.DELETE, bizType = "'sys_attachment'", bizId = "#id", action = "删除附件")
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
    @Log(module = "附件", businessType = BusinessType.UPDATE, bizType = "'sys_attachment'", bizId = "#id", action = "恢复附件")
    public Result<Boolean> restore(@PathVariable Long id) {
        return Result.success(attachmentService.restoreAttachment(id));
    }

    @Operation(summary = "彻底删除（回收站）")
    @DeleteMapping("/permanent/{id}")
    @Log(module = "附件", businessType = BusinessType.DELETE, bizType = "'sys_attachment'", bizId = "#id", action = "彻底删除附件")
    public Result<Boolean> permanent(@PathVariable Long id) {
        return Result.success(attachmentService.permanentDelete(id));
    }

    @Operation(summary = "清理回收站过期附件（默认30天）")
    @PostMapping("/permanent-expired")
    @Log(module = "附件", businessType = BusinessType.DELETE, bizType = "'sys_attachment'", action = "清理回收站过期附件")
    public Result<Integer> permanentExpired(@RequestParam(required = false, defaultValue = "30") Integer days) {
        return Result.success(attachmentService.permanentDeleteExpired(days));
    }

    @Operation(summary = "根据业务关联删除附件")
    @DeleteMapping("/delete-by-biz")
    @Log(module = "附件", businessType = BusinessType.DELETE, bizType = "'sys_attachment'", bizId = "#bizId", action = "按业务关联删除附件")
    public Result<Boolean> deleteByBiz(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.success(attachmentService.deleteAttachmentsByBiz(bizType, bizId));
    }

    @Operation(summary = "将图纸版本设为现行，同版本原稿与打印件同步")
    @PostMapping("/{id}/set-current")
    @Log(module = "附件", businessType = BusinessType.UPDATE, bizType = "'sys_attachment'", bizId = "#id", action = "设为现行图纸版本")
    public Result<Boolean> setCurrent(@PathVariable Long id) {
        return Result.success(attachmentService.setCurrentAttachment(id));
    }

    @Operation(summary = "图纸/工程文件下发或撤回（受控文件；dev-20261009-023）")
    @PostMapping("/{id}/release")
    @Log(module = "附件", businessType = BusinessType.UPDATE, bizType = "'sys_attachment'", bizId = "#id", action = "下发/撤回受控文件")
    @cn.dev33.satoken.annotation.SaCheckPermission("engineering:drawing:release")
    public Result<Boolean> release(@PathVariable Long id,
                                   @RequestParam(required = false, defaultValue = "true") boolean released) {
        return Result.success(attachmentService.setReleased(id, released));
    }

    @Operation(summary = "客供资料归集：某产品关联的询价/报价附件（只读）")
    @GetMapping("/customer-docs/{productId}")
    public Result<java.util.List<java.util.Map<String, Object>>> customerDocs(@PathVariable Long productId) {
        return Result.success(attachmentService.customerDocs(productId));
    }

    @Operation(summary = "下载/预览附件")
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        SysAttachment attachment = attachmentService.getById(id);
        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }

        // 下载/内联预览鉴权：按 biz_type 映射权限点，未映射退回「仅登录」（dev-20261011-031）
        String[] perms = requiredDownloadPerms(attachment.getBizType());
        if (perms != null && perms.length > 0) {
            boolean allowed = false;
            for (String perm : perms) {
                if (StpUtil.hasPermission(perm)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                log.warn("附件下载被拒绝（无权限）：id={}, bizType={}, need={}",
                        id, attachment.getBizType(), String.join("/", perms));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else {
            if (!StpUtil.isLogin()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            log.warn("附件 biz_type 无下载权限映射，暂仅登录可访问（待补）：bizType={}, id={}",
                    attachment.getBizType(), id);
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

    /**
     * 附件下载/预览的 biz_type → 所需权限点（任一命中即可）；未映射返回 null → 仅登录（dev-20261011-031）。
     * 无映射的 biz_type 在服务端记 warn，并在任务 remark 记「待补权限点」。
     */
    private String[] requiredDownloadPerms(String bizType) {
        if (bizType == null) {
            return null;
        }
        switch (bizType) {
            case "sales_order": return new String[] { "sales:order:view" };
            case "quotation": return new String[] { "sales:quotation:view" };
            case "inquiry": return new String[] { "sales:inquiry:view" };
            case "sample": return new String[] { "sales:sample:view" };
            case "product": return new String[] { "product:view" };
            case "purchase_order": return new String[] { "purchase:order:view" };
            case "production_order": return new String[] { "production:order:view" };
            case "inbound_order": return new String[] { "inventory:inbound:view" };
            case "outbound_order": return new String[] { "inventory:outbound:view" };
            case "material": return new String[] { "inventory:material:view" };
            case "drawing": return new String[] { "engineering:drawing:view" };
            default: return null;
        }
    }
}
