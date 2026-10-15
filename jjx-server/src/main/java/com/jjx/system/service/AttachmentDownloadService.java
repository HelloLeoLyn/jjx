package com.jjx.system.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttachmentDownloadService {
    private final SysAttachmentMapper files;
    private final AttachmentAccessService access;
    private final AttachmentRefService refs;
    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;

    public ResponseEntity<Resource> byAttachment(Long id) {
        SysAttachment file = files.selectById(id);
        access.checkRead(file);
        ensureBytesPresent(file);
        return response(file, null, true);
    }

    public ResponseEntity<Resource> byRef(Long id, boolean preview) {
        var ref = refs.readable(id); // Authorization is tied to this ref, never another visible ref.
        SysAttachment file = files.selectById(ref.getAttachmentId());
        if (file == null) throw new BusinessException("附件不存在");
        ensureBytesPresent(file);
        return response(file, ref.getDisplayName(), preview);
    }

    /** 留痕式释放的行：文件已删、记录保留，给出明确说明而不是 404/空响应。 */
    private void ensureBytesPresent(SysAttachment file) {
        if (file.getUploadState() == AttachmentUploadState.PURGED)
            throw new BusinessException("文件已释放，仅保留上传记录（文件名：" + file.getFileName()
                    + "，大小：" + file.getFileSize() + " 字节，sha256：" + file.getSha256() + "）");
    }

    private ResponseEntity<Resource> response(SysAttachment file, String displayName, boolean preview) {
        try {
            Path root = Paths.get(uploadBasePath).toRealPath();
            Path path = root.resolve(file.getFilePath()).normalize().toRealPath();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new BusinessException("附件路径无效");
            String mime = file.getFileType();
            boolean safeIconSvg = ProcessIconService.isIcon(file) && "image/svg+xml".equals(mime);
            boolean inline = preview && mime != null && (safeIconSvg || Set.of("application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp").contains(mime));
            String name = displayName == null || displayName.isBlank() ? file.getFileName() : displayName;
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok().contentType(inline ? MediaType.parseMediaType(mime) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded)
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy",
                    safeIconSvg ? "sandbox; default-src 'none'; style-src 'none'" : "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new FileSystemResource(path));
        } catch (java.io.IOException ex) { return ResponseEntity.notFound().build(); }
    }
}
