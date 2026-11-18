package com.jjx.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.service.IPurchasePaymentService;
import com.jjx.system.service.AttachmentDownloadService;
import com.jjx.system.service.AttachmentRefService;
import com.jjx.system.service.ISysAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 只读回单导出；逐文件复用附件鉴权与路径检查，任一失败不交付残缺包。 */
@RestController
@RequestMapping("/purchase/payment")
@RequiredArgsConstructor
public class PurchasePaymentDocumentController {
    private final IPurchasePaymentService payments;
    private final AttachmentRefService refs;
    private final AttachmentDownloadService downloads;
    private final ISysAttachmentService attachments;
    private static final int MAX_BYTES = 50 * 1024 * 1024;

    @GetMapping("/{paymentId}/vouchers")
    @SaCheckPermission("purchase:payment:view")
    public ResponseEntity<byte[]> vouchers(@PathVariable Long paymentId) {
        payments.selectPaymentById(paymentId); // 先验证单据存在。
        var seen = new HashSet<Long>();
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output)) {
            int remaining = MAX_BYTES;
            for (var ref : refs.list("purchase_payment", paymentId)) {
                if ("draft".equals(ref.getRefState()) || Boolean.TRUE.equals(ref.getDeleted())) continue;
                if (seen.add(ref.getAttachmentId())) {
                    remaining -= append(zip, ref.getAttachmentId(), ref.getFileName(), downloads.byRef(ref.getId(), false), remaining);
                }
            }
            // 兼容 upload-voucher 的历史直挂附件，不依赖 URL，也不导出其它业务附件。
            for (var file : attachments.getAttachments("purchase_payment", paymentId)) {
                if (seen.add(file.getId())) remaining -= append(zip, file.getId(), file.getFileName(), downloads.byAttachment(file.getId()), remaining);
            }
            if (seen.isEmpty()) throw new BusinessException("本付款单暂无可下载回单");
            zip.finish();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment-" + paymentId + "-vouchers.zip")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(output.toByteArray());
        } catch (IOException error) {
            throw new BusinessException("回单打包失败，请重试");
        }
    }

    private int append(ZipOutputStream zip, Long id, String name,
                       ResponseEntity<org.springframework.core.io.Resource> response, int remaining) throws IOException {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null)
            throw new BusinessException("部分回单文件不可读取，请核查后重试");
        var resource = response.getBody();
        if (resource.contentLength() > remaining) throw new BusinessException("回单合计超过50MB，请逐个下载");
        try (var input = resource.getInputStream()) {
            byte[] bytes = input.readNBytes(remaining + 1);
            if (bytes.length > remaining) throw new BusinessException("回单合计超过50MB，请逐个下载");
            String safe = (name == null || name.isBlank() ? "voucher" : name).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
            zip.putNextEntry(new ZipEntry(id + "-" + safe));
            zip.write(bytes);
            zip.closeEntry();
            return bytes.length;
        }
    }
}
