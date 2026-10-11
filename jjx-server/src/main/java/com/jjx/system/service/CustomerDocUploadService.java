package com.jjx.system.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Business attachment intake. A file belongs to exactly one product row. */
@Service
@RequiredArgsConstructor
public class CustomerDocUploadService {
    private static final Map<String, String> TYPE_NAMES = Map.of(
            "customer_drawing", "客户图纸", "specification", "技术规格书", "approval_sheet", "承认书",
            "inspection_standard", "检验标准", "change_notice", "变更通知", "golden_sample", "标准样品资料",
            "packaging_spec", "包装规范", "compliance_report", "认证报告", "other", "其他资料");

    private final ISysAttachmentService attachments;
    private final JdbcTemplate jdbc;

    @Transactional(rollbackFor = Exception.class)
    public SysAttachment upload(MultipartFile file, String bizType, Long bizId, String productCode,
                                String docType, String traceId) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择文件");
        if (bizId == null || bizId <= 0 || bizType == null
                || !java.util.Set.of("inquiry", "quotation", "sales_order", "sample").contains(bizType))
            throw new BusinessException("不支持的来源单据");
        if (productCode == null || productCode.isBlank() || productCode.length() > 100)
            throw new BusinessException("请选择文件所属产品");
        if (!TYPE_NAMES.containsKey(docType)) throw new BusinessException("请选择资料类型");

        String product = productCode.trim();
        String headerSql = switch (bizType) {
            case "inquiry" -> "SELECT inquiry_no AS source_no FROM sales_inquiry WHERE inquiry_id=? FOR UPDATE";
            case "quotation" -> "SELECT quotation_no AS source_no FROM sales_quotation WHERE quotation_id=? FOR UPDATE";
            case "sales_order" -> "SELECT order_no AS source_no FROM sales_order WHERE order_id=? FOR UPDATE";
            default -> "SELECT o.order_no AS source_no FROM sales_sample_order s JOIN sales_order o ON o.order_id=s.order_id WHERE s.order_id=? FOR UPDATE";
        };
        var header = jdbc.queryForList(headerSql, bizId);
        if (header.isEmpty()) throw new BusinessException("来源单据不存在");
        String sourceNo = (String) header.get(0).get("source_no");
        if (sourceNo == null || sourceNo.isBlank()) throw new BusinessException("来源单号为空，无法整理文件名");
        String membershipSql = switch (bizType) {
            case "inquiry" -> "SELECT COUNT(*) FROM sales_inquiry WHERE inquiry_id=? AND product_code=?";
            case "quotation" -> "SELECT COUNT(*) FROM sales_quotation_item WHERE quotation_id=? AND product_code=?";
            case "sales_order" -> "SELECT COUNT(*) FROM sales_order_product WHERE order_id=? AND product_code=?";
            default -> "SELECT COUNT(*) FROM sales_sample_order WHERE order_id=? AND product_code=?";
        };
        Integer matches = jdbc.queryForObject(membershipSql, Integer.class, bizId, product);
        if (matches == null || matches == 0) throw new BusinessException("所选产品不属于来源单据");

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot).toLowerCase(java.util.Locale.ROOT);
        if (!extension.matches("\\.(pdf|dwg|dxf|jpg|jpeg|png|gif|webp|doc|docx|xls|xlsx|zip)"))
            throw new BusinessException("不支持的文件格式");
        Integer previous = jdbc.queryForObject("SELECT COALESCE(MAX(customer_doc_seq),0) FROM sys_attachment "
                + "WHERE biz_type=? AND biz_id=? AND customer_product_code=? AND customer_doc_type=?",
                Integer.class, bizType, bizId, product, docType);
        int sequence = (previous == null ? 0 : previous) + 1;
        String displayName = sourceNo + "-" + product + "-" + TYPE_NAMES.get(docType)
                + "-" + String.format("%02d", sequence) + extension;

        Long id = attachments.uploadAttachment(file, bizType, bizId, traceId, null, null, null);
        SysAttachment attachment = attachments.getById(id);
        attachment.setCustomerProductCode(product);
        attachment.setCustomerDocType(docType);
        attachment.setCustomerDocSeq(sequence);
        attachment.setDisplayName(displayName);
        if (!attachments.updateById(attachment)) throw new BusinessException("客供资料登记失败");
        return attachment;
    }
}
