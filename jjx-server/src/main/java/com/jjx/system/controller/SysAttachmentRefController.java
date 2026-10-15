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
    public Result<List<SysAttachmentRef>> list(@RequestParam String sourceType,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String sourceNo) {
        return Result.success(refs.list(sourceType, sourceId, sourceNo));
    }

    @GetMapping("/product/{productId}")
    public Result<List<SysAttachmentRef>> product(@PathVariable Long productId) { return Result.success(refs.product(productId)); }

    /** 草稿行列表（未保存单据的待保存附件）—— dev-20261016-009 */
    @GetMapping("/drafts")
    public Result<List<SysAttachmentRef>> drafts(@RequestParam String draftKey) {
        return Result.success(refs.drafts(draftKey));
    }

    @PutMapping("/drafts/{id}")
    @Log(module = "附件引用", businessType = BusinessType.UPDATE, bizType = "'sys_attachment_ref'", bizId = "#id", action = "草稿附件改类型")
    public Result<Void> updateDraft(@PathVariable Long id, @RequestParam(required = false) String documentType) {
        refs.updateDraft(id, documentType);
        return Result.success();
    }

    @DeleteMapping("/drafts/{id}")
    @Log(module = "附件引用", businessType = BusinessType.DELETE, bizType = "'sys_attachment_ref'", bizId = "#id", action = "移除草稿附件")
    public Result<Void> removeDraft(@PathVariable Long id) {
        refs.removeDraft(id);
        return Result.success();
    }

    @PutMapping("/{refId}")
    @Log(module = "附件引用", businessType = BusinessType.UPDATE, bizType = "'sys_attachment_ref'", bizId = "#refId", action = "修改附件引用资料")
    public Result<Boolean> update(@PathVariable Long refId, @Valid @RequestBody AttachmentRefMetadataDTO body) {
        refs.update(refId, body); return Result.success(true);
    }

    @DeleteMapping("/{refId}")
    @Log(module = "附件引用", businessType = BusinessType.DELETE, bizType = "'sys_attachment_ref'", bizId = "#refId", action = "解除附件引用")
    public Result<Boolean> unlink(@PathVariable Long refId) { refs.unlink(refId); return Result.success(true); }

    /** 批量修改引用元数据（部分更新：只写显式传入的非 null 字段；一次事务） */
    @PutMapping("/batch")
    @Log(module = "附件引用", businessType = BusinessType.UPDATE, bizType = "'sys_attachment_ref'", action = "批量修改附件引用资料")
    public Result<Integer> batchUpdate(@Valid @RequestBody AttachmentRefBatchDTO body) {
        return Result.success(refs.batchUpdate(body));
    }

    /** 批量解除引用（逗号分隔的 refId 列表；一次事务；不动物理文件） */
    @DeleteMapping("/batch/{refIds}")
    @Log(module = "附件引用", businessType = BusinessType.DELETE, bizType = "'sys_attachment_ref'", action = "批量解除附件引用")
    public Result<Integer> batchUnlink(@PathVariable Long[] refIds) {
        return Result.success(refs.batchUnlink(java.util.Arrays.asList(refIds)));
    }

    @GetMapping("/{refId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long refId) { return downloads.byRef(refId, false); }

    @GetMapping("/{refId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long refId) { return downloads.byRef(refId, true); }

    @PostMapping("/temp")
    @Log(module = "附件引用", businessType = BusinessType.INSERT, bizType = "'sys_attachment'", bizId = "#result.data", action = "上传临时附件")
    public Result<Long> temp(@RequestParam MultipartFile file, @RequestParam String requestKey,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String drawingNo,
                             @RequestParam(required = false) String drawingName,
                             @RequestParam(required = false) String version,
                             @RequestParam(required = false) String fileRole,
                             @RequestParam(required = false) String sourceType,
                             @RequestParam(required = false) Long productId,
                             @RequestParam(required = false) String draftKey) {
        // 可选图纸元数据（dev-20261014-029，019 方案B）：不传=不写图纸列，行为不变。
        AttachmentDrawingMetaDTO drawingMeta = null;
        if (category != null || drawingNo != null || drawingName != null || version != null || fileRole != null) {
            drawingMeta = new AttachmentDrawingMetaDTO();
            drawingMeta.setCategory(category); drawingMeta.setDrawingNo(drawingNo); drawingMeta.setDrawingName(drawingName);
            drawingMeta.setVersion(version); drawingMeta.setFileRole(fileRole);
        }
        return Result.success(lifecycle.upload(file, requestKey, drawingMeta, sourceType, productId, draftKey));
    }

    @PostMapping("/cleanup-preview")
    @Log(module = "附件引用", businessType = BusinessType.OTHER, bizType = "'sys_attachment'", action = "附件清理预演（不删除）")
    public Result<List<Map<String, Object>>> cleanup(@RequestParam(defaultValue = "0") long afterId,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return Result.success(lifecycle.preview(afterId, limit));
    }

    /** 到期、无有效引用、非受控的附件标记删除（进回收站，可恢复）；不做物理删除。 */
    @PostMapping("/cleanup-apply")
    @Log(module = "附件引用", businessType = BusinessType.OTHER, bizType = "'sys_attachment'", action = "过期附件移入回收站（软删除，可恢复）")
    public Result<Map<String, Object>> applyCleanup(@RequestParam(defaultValue = "0") long afterId,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return Result.success(lifecycle.recycleEligible(afterId, limit));
    }

    /** 留痕式释放：删磁盘文件、保留数据库记录（状态 PURGED，不可下载）。 */
    @PostMapping("/purge-apply")
    @Log(module = "附件引用", businessType = BusinessType.DELETE, bizType = "'sys_attachment'", action = "释放附件字节（删文件、保留记录）")
    public Result<Map<String, Object>> applyPurge(@RequestParam(defaultValue = "0") long afterId,
                                                  @RequestParam(defaultValue = "100") int limit) {
        return Result.success(lifecycle.purgeEligible(afterId, limit));
    }

    @GetMapping("/by-sha256")
    public Result<List<Map<String, Object>>> duplicates(@RequestParam String sha256,
            @RequestParam String sourceType, @RequestParam Long sourceId) {
        return Result.success(refs.duplicates(sha256, sourceType, sourceId));
    }

    @GetMapping("/document-types")
    public Result<List<Map<String, Object>>> types() { return Result.success(types.list()); }
}
