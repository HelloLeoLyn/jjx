package com.jjx.purchase;

import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.controller.PurchasePaymentDocumentController;
import com.jjx.purchase.service.IPurchasePaymentService;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.service.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchasePaymentVoucherTest {
    IPurchasePaymentService payments = mock(IPurchasePaymentService.class);
    AttachmentRefService refs = mock(AttachmentRefService.class);
    AttachmentDownloadService downloads = mock(AttachmentDownloadService.class);
    ISysAttachmentService files = mock(ISysAttachmentService.class);
    PurchasePaymentDocumentController controller = new PurchasePaymentDocumentController(payments, refs, downloads, files);
    SysAttachmentRef ref(long id, long file) { var r = new SysAttachmentRef(); r.setId(id); r.setAttachmentId(file); r.setFileName("../../回单.pdf"); r.setRefState("active"); return r; }
    @Test void zipPreservesBytesSanitizesNamesAndDeduplicatesLegacyCopies() throws Exception {
        var file = new SysAttachment(); file.setId(10L); file.setFileName("same.pdf");
        var legacy = new SysAttachment(); legacy.setId(11L); legacy.setFileName("same.pdf");
        when(refs.list("purchase_payment", 1L)).thenReturn(List.of(ref(2,10)));
        when(files.getAttachments("purchase_payment", 1L)).thenReturn(List.of(file, legacy));
        when(downloads.byRef(2L,false)).thenReturn(ResponseEntity.ok(new ByteArrayResource(new byte[]{1,2,3})));
        when(downloads.byAttachment(11L)).thenReturn(ResponseEntity.ok(new ByteArrayResource(new byte[]{4,5})));
        var response = controller.vouchers(1L);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(response.getBody()))) {
            String name = zip.getNextEntry().getName(); assertFalse(name.contains("/")); assertFalse(name.contains("\\"));
            assertArrayEquals(new byte[]{1,2,3},zip.readAllBytes());
            assertEquals("11-same.pdf",zip.getNextEntry().getName()); assertArrayEquals(new byte[]{4,5},zip.readAllBytes()); assertNull(zip.getNextEntry());
        }
        verify(downloads, never()).byAttachment(10L);
        assertEquals("private, no-store",response.getHeaders().getCacheControl());
    }
    @Test void permissionFailureAndMissingBytesDoNotSilentlyDeliverPartialZip() {
        when(refs.list("purchase_payment",1L)).thenReturn(List.of(ref(2,10)));
        when(downloads.byRef(2L,false)).thenThrow(new BusinessException("forbidden"));
        assertThrows(BusinessException.class,()->controller.vouchers(1L));
        doReturn(ResponseEntity.notFound().build()).when(downloads).byRef(2L,false);
        assertThrows(BusinessException.class,()->controller.vouchers(1L));
    }
    @Test void emptyAndDraftOnlyAreNotReportedAsSuccessfulDownloads() {
        var draft=ref(2,10); draft.setRefState("draft");
        when(refs.list("purchase_payment",1L)).thenReturn(List.of(draft));
        assertThrows(BusinessException.class,()->controller.vouchers(1L));
        verifyNoInteractions(downloads);
    }
    @Test void oversizedFileIsRejectedBeforeReadingIt() {
        when(refs.list("purchase_payment",1L)).thenReturn(List.of(ref(2,10)));
        var big = new ByteArrayResource(new byte[0]) { @Override public long contentLength() { return 51L*1024*1024; } };
        when(downloads.byRef(2L,false)).thenReturn(ResponseEntity.ok(big));
        assertThrows(BusinessException.class,()->controller.vouchers(1L));
    }
}
