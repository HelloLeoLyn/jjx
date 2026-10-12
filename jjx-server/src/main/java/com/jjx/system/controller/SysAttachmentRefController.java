package com.jjx.system.controller;

import com.jjx.common.core.result.Result;
import com.jjx.system.annotation.Log;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.dto.*;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/attachment/refs")
@RequiredArgsConstructor
public class SysAttachmentRefController {
    private final AttachmentRefService refs;
    private final AttachmentLifecycleService lifecycle;
    private final AttachmentDownloadService downloads;
    private final AttachmentDocumentTypeService types;

    /** Same endpoint establishes a new ref, binds TEMP, or reuses an existing attachmentId. */
    @PostMapping
    @Log(module = "附件引用", businessType = BusinessType.INSERT, bizType = "'sys_attachment_ref'", bizId = "#result.data", action = "建立附件引用")
    public Result<Long> create(@Valid @RequestBody AttachmentRefDTO body) { return Result.success(refs.create(body)); }

    @GetMapping
    public Result<List<SysAttachmentRef>> list(@RequestParam String sourceType, @RequestParam Long sourceId) {
        return Result.success(refs.list(sourceType, sourceId));
    }

    @GetMapping("/product/{productId}")
    public Result<List<SysAttachmentRef>> product(@PathVariable Long productId) { return Result.success(refs.product(productId)); }

    @PutMapping("/{refId}")
    @Log(module = "附件引用", businessType = BusinessType.UPDATE, bizType = "'sys_attachment_ref'", bizId = "#refId", action = "修改附件引用资料")
    public Result<Boolean> update(@PathVariable Long refId, @Valid @RequestBody AttachmentRefMetadataDTO body) {
        refs.update(refId, body); return Result.success(true);
    }

    @DeleteMapping("/{refId}")
    @Log(module = "附件引用", businessType = BusinessType.DELETE, bizType = "'sys_attachment_ref'", bizId = "#refId", action = "解除附件引用")
    public Result<Boolean> unlink(@PathVariable Long refId) { refs.unlink(refId); return Result.success(true); }

    @GetMapping("/{refId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long refId) { return downloads.byRef(refId, false); }

    @GetMapping("/{refId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long refId) { return downloads.byRef(refId, true); }

    @PostMapping("/temp")
    @Log(module = "附件引用", businessType = BusinessType.INSERT, bizType = "'sys_attachment'", bizId = "#result.data", action = "上传临时附件")
    public Result<Long> temp(@RequestParam MultipartFile file, @RequestParam String requestKey) {
        return Result.success(lifecycle.upload(file, requestKey));
    }

    @PostMapping("/cleanup-preview")
    @Log(module = "附件引用", businessType = BusinessType.OTHER, bizType = "'sys_attachment'", action = "附件清理预演（不删除）")
    public Result<List<Map<String, Object>>> cleanup(@RequestParam(defaultValue = "0") long afterId,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return Result.success(lifecycle.preview(afterId, limit));
    }

    @GetMapping("/by-sha256")
    public Result<List<Map<String, Object>>> duplicates(@RequestParam String sha256,
            @RequestParam String sourceType, @RequestParam Long sourceId) {
        return Result.success(refs.duplicates(sha256, sourceType, sourceId));
    }

    @GetMapping("/document-types")
    public Result<List<Map<String, Object>>> types() { return Result.success(types.list()); }
}
