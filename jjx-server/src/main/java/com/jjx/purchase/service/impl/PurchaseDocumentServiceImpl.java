package com.jjx.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.domain.dto.PurchaseDocumentDTO;
import com.jjx.purchase.domain.entity.PurchaseDocument;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.enums.DocumentStatus;
import com.jjx.purchase.mapper.PurchaseDocumentMapper;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.service.IPurchaseDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.jjx.system.annotation.Event;

/**
 * 采购票据服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseDocumentServiceImpl extends ServiceImpl<PurchaseDocumentMapper, PurchaseDocument> implements IPurchaseDocumentService {

    private final PurchaseDocumentMapper documentMapper;
    /** 2026-09-21（dev-20260921-013）：采购事件改手写 payload。 */
    private final com.jjx.event.EventPublisher eventPublisher;
    private final PurchaseOrderMapper purchaseOrderMapper;
    /** 发票文件接入公共附件（dev-20261014-022）：核销事务内建引用。 */
    private final com.jjx.purchase.service.PurchaseInvoiceAttachmentService invoiceAttachments;

    /**
     * 采购类事件统一发布（2026-09-21 dev-20260921-013 采购批）：
     * 手写 payload，bizNo 取业务单号/编码（删除类由调用方传入删除前取到的值）。
     */
    private void publishDocumentEvent(String eventCode, Long id, String knownNo) {
        PurchaseDocument entity = (id == null || knownNo != null) ? null : documentMapper.selectById(id);
        String no = knownNo != null ? knownNo : (entity == null ? null : entity.getDocumentNo());
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "purchase", id, no);
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, eventCode, payload);
    }

    @Override
    public List<PurchaseDocument> selectDocumentList(PurchaseDocumentDTO dto) {
        LambdaQueryWrapper<PurchaseDocument> wrapper = Wrappers.lambdaQuery();
        if (dto != null) {
            if (StringUtils.isNotEmpty(dto.getDocumentNo())) {
                wrapper.like(PurchaseDocument::getDocumentNo, dto.getDocumentNo());
            }
            if (StringUtils.isNotBlank(dto.getInvoiceNo())) {
                wrapper.like(PurchaseDocument::getInvoiceNo, dto.getInvoiceNo().trim());
            }
            if ("invoice".equalsIgnoreCase(dto.getDocumentType())
                    && (dto.getDocumentDateStart() != null || dto.getDocumentDateEnd() != null)) {
                wrapper.isNotNull(PurchaseDocument::getInvoiceNo);
            }
            if (StringUtils.isNotEmpty(dto.getDocumentType())) {
                wrapper.eq(PurchaseDocument::getDocumentType, dto.getDocumentType());
            }
            if (dto.getOrderId() != null) {
                wrapper.eq(PurchaseDocument::getOrderId, dto.getOrderId());
            }
            if (dto.getSupplierId() != null) {
                wrapper.eq(PurchaseDocument::getSupplierId, dto.getSupplierId());
            }
            if (dto.getDocumentStatus() != null) {
                wrapper.eq(PurchaseDocument::getDocumentStatus, dto.getDocumentStatus());
            }
            if (dto.getDocumentDateStart() != null) {
                wrapper.ge(PurchaseDocument::getDocumentDate, dto.getDocumentDateStart());
            }
            if (dto.getDocumentDateEnd() != null) {
                wrapper.le(PurchaseDocument::getDocumentDate, dto.getDocumentDateEnd());
            }
        }
        wrapper.orderByDesc(PurchaseDocument::getCreateTime).orderByDesc(PurchaseDocument::getDocumentId);
        return documentMapper.selectList(wrapper);
    }

    @Override
    public PurchaseDocument selectDocumentById(Long documentId) {
        PurchaseDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("票据不存在");
        }
        if ("invoice".equalsIgnoreCase(document.getDocumentType())) {
            PurchaseOrder order = purchaseOrderMapper.selectById(document.getOrderId());
            if (order != null) {
                document.setOrderNo(order.getOrderNo());
                document.setSupplierName(order.getSupplierName());
                document.setOrderTotalAmount(order.getOrderTotalAmount());
                document.setReceiptStatus(order.getReceiptStatus());
            }
        }
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerInvoice(Long documentId, com.jjx.purchase.domain.dto.PurchaseInvoiceRegistrationDTO dto) {
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        PurchaseOrder order = purchaseOrderMapper.lockById(dto.getOrderId());
        if (order == null) throw new BusinessException("采购订单不存在");
        var partial = com.jjx.purchase.domain.enums.ReceiptStatusEnum.PARTIALLY_RECEIVED.getValue();
        var received = com.jjx.purchase.domain.enums.ReceiptStatusEnum.COMPLETED.getValue();
        if (!Objects.equals(order.getReceiptStatus(), partial) && !Objects.equals(order.getReceiptStatus(), received))
            throw new BusinessException("仅部分收货或已收货订单可以登记发票");
        PurchaseDocument document = documentId == null ? new PurchaseDocument() : documentMapper.lockById(documentId);
        if (document == null || (documentId != null && !"invoice".equalsIgnoreCase(document.getDocumentType())))
            throw new BusinessException("采购发票不存在");
        if (documentId != null && (!Objects.equals(document.getDocumentStatus(), DocumentStatus.PENDING.getValue())
                || !Objects.equals(document.getOrderId(), dto.getOrderId())))
            throw new BusinessException("只能补录待核验发票，且不能更换采购订单");
        org.springframework.beans.BeanUtils.copyProperties(dto, document, "attachments");
        document.setInvoiceNo(StringUtils.trim(dto.getInvoiceNo()));
        document.setCurrency(StringUtils.trim(dto.getCurrency()));
        validateInvoiceFields(document);
        var duplicate = Wrappers.<PurchaseDocument>lambdaQuery().eq(PurchaseDocument::getInvoiceNo, document.getInvoiceNo());
        if (documentId != null) duplicate.ne(PurchaseDocument::getDocumentId, documentId);
        if (documentMapper.selectCount(duplicate) > 0) throw new BusinessException("真实发票号码已登记，请勿重复登记");
        if (!StringUtils.equalsIgnoreCase(order.getCurrency(), document.getCurrency()))
            throw new BusinessException("发票与采购订单币种不同，请核实后登记");
        validateInvoiceAmount(order, document.getDocumentAmount(), documentId);
        document.setSupplierId(order.getSupplierId());
        document.setDocumentType("invoice");
        if (documentId == null) {
            if (dto.getAttachments().isEmpty()) throw new BusinessException("请上传发票原件");
            document.setDocumentNo(generateDocumentNo("invoice"));
            document.setDocumentStatus(DocumentStatus.PENDING.getValue());
            documentMapper.insert(document);
        } else {
            documentMapper.updateById(document);
        }
        invoiceAttachments.bind(document.getDocumentId(), dto.getAttachments());
        // 旧版登记保存在 file_url 的临时件，补录时一并转为正式引用。
        Long carried = invoiceAttachments.carriedAttachmentId(document.getFileUrl());
        if (carried != null && !invoiceAttachments.hasReference(document.getDocumentId(), carried)) {
            invoiceAttachments.prepare(document.getDocumentId(), List.of(carried));
            invoiceAttachments.complete(document.getDocumentId(), List.of(carried));
        }
        if (documentId == null) publishDocumentEvent("purchase.document.created", document.getDocumentId(), document.getDocumentNo());
        return document.getDocumentId();
    }

    private void validateInvoiceFields(PurchaseDocument document) {
        if (StringUtils.isAnyBlank(document.getInvoiceNo(), document.getSellerName(), document.getSellerTaxNo(),
                document.getBuyerName(), document.getBuyerTaxNo(), document.getCurrency())
                || document.getDocumentDate() == null || document.getUntaxedAmount() == null
                || document.getTaxAmount() == null || document.getDocumentAmount() == null)
            throw new BusinessException("请先补录真实发票号码、开票日期、购销双方、税号、金额和币种");
        if (document.getUntaxedAmount().add(document.getTaxAmount()).compareTo(document.getDocumentAmount()) != 0)
            throw new BusinessException("不含税金额加税额必须等于价税合计");
    }

    /** 金额规则唯一实现：保留累计不超订单口径；订单锁串行化登记与补录。 */
    private void validateInvoiceAmount(PurchaseOrder order, java.math.BigDecimal amount, Long excludeId) {
        if (order == null) throw new BusinessException("采购订单不存在，无法登记发票");
        java.math.BigDecimal sum = documentMapper.sumInvoiceAmount(order.getOrderId(), excludeId);
        if (order.getOrderTotalAmount() != null && sum.add(amount).compareTo(order.getOrderTotalAmount()) > 0)
            throw new BusinessException("累计发票金额超过订单金额，请核实");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertDocument(PurchaseDocumentDTO dto) {
        // 检查票据编号是否唯一
        if (checkDocumentNoUnique(dto.getDocumentNo())) {
            throw new BusinessException("票据编号已存在");
        }

        if ("invoice".equalsIgnoreCase(dto.getDocumentType()) && dto.getOrderId() != null && dto.getDocumentAmount() != null) {
            validateInvoiceAmount(purchaseOrderMapper.lockById(dto.getOrderId()), dto.getDocumentAmount(), null);
        }

        PurchaseDocument document = new PurchaseDocument();
        copyProperties(dto, document);

        // 设置默认状态
        if (document.getDocumentStatus() == null) {
            document.setDocumentStatus(DocumentStatus.PENDING.getValue());
        }

        int rows = documentMapper.insert(document);
        if (rows > 0) {
            publishDocumentEvent("purchase.document.created", document.getDocumentId(), document.getDocumentNo());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDocument(PurchaseDocumentDTO dto) {
        if (dto.getDocumentId() == null) {
            throw new BusinessException("票据ID不能为空");
        }

        PurchaseDocument existing = documentMapper.selectById(dto.getDocumentId());
        if (existing == null) {
            throw new BusinessException("票据不存在");
        }

        PurchaseDocument document = new PurchaseDocument();
        copyProperties(dto, document);
        document.setDocumentId(dto.getDocumentId());

        return documentMapper.updateById(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDocumentById(Long documentId) {
        PurchaseDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("票据不存在");
        }
        PurchaseDocument before = documentMapper.selectById(documentId);
        int rows = documentMapper.deleteById(documentId);
        if (rows > 0) {
            publishDocumentEvent("purchase.document.deleted", documentId, before == null ? null : before.getDocumentNo());
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDocumentByIds(Long[] documentIds) {
        int count = 0;
        for (Long documentId : documentIds) {
            count += deleteDocumentById(documentId);
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int verifyDocument(Long documentId, String verifierName, String verificationDate, String verificationRemark) {
        return verifyDocument(documentId, verifierName, verificationDate, verificationRemark, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int verifyDocument(Long documentId, String verifierName, String verificationDate, String verificationRemark, java.util.List<Long> attachmentIds) {
        PurchaseDocument document = documentMapper.lockById(documentId);
        if (document == null) {
            throw new BusinessException("票据不存在");
        }

        if (!Objects.equals(DocumentStatus.PENDING.getValue(), document.getDocumentStatus())) {
            throw new BusinessException("只有待处理状态的票据可以核验");
        }

        // 发票票种：把本次核销携带的临时附件 + 新建时暂存的附件，在同一事务内建成引用（收货/合同等其他票种不涉及）。
        boolean isInvoice = document.getDocumentType() != null && document.getDocumentType().equalsIgnoreCase("invoice");
        java.util.List<Long> docAttachmentIds = new java.util.ArrayList<>();
        if (isInvoice) {
            validateInvoiceFields(document);
            if (attachmentIds != null) docAttachmentIds.addAll(attachmentIds);
            Long carried = invoiceAttachments.carriedAttachmentId(document.getFileUrl());
            if (carried != null && !invoiceAttachments.hasReference(documentId, carried)) docAttachmentIds.add(carried);
            invoiceAttachments.prepare(documentId, docAttachmentIds);
            if (docAttachmentIds.isEmpty() && !invoiceAttachments.hasReference(documentId, null)
                    && StringUtils.isBlank(document.getFileUrl()))
                throw new BusinessException("请先上传发票原件再核验");
            document.setVerifiedBy(cn.dev33.satoken.stp.StpUtil.getLoginIdAsString());
            document.setVerifierName(StringUtils.defaultIfBlank(com.jjx.system.utils.SecurityUtils.getDisplayName(), document.getVerifiedBy()));
            document.setVerifiedTime(LocalDateTime.now());
            document.setVerificationRemark(StringUtils.defaultString(verificationRemark));
        }

        document.setDocumentStatus(DocumentStatus.VERIFIED.getValue());
        document.setVerificationDate(verificationDate != null ? LocalDate.parse(verificationDate) : LocalDate.now());
        if (!isInvoice && StringUtils.isNotEmpty(verificationRemark)) {
            document.setRemark(verificationRemark);
        }
        document.setUpdateTime(LocalDateTime.now());

        int rows = documentMapper.updateById(document);
        if (rows > 0) {
            if (isInvoice && !docAttachmentIds.isEmpty()) invoiceAttachments.complete(documentId, docAttachmentIds);
            publishDocumentEvent("purchase.document.verified", document.getDocumentId(), document.getDocumentNo());
        }
        return rows;
    }

    @Override
    public boolean checkDocumentNoUnique(String documentNo) {
        return documentMapper.checkDocumentNoUnique(documentNo) > 0;
    }

    @Override
    public String generateDocumentNo(String documentType) {
        String prefix = switch (documentType) {
            case "invoice" -> "INV";
            case "receipt" -> "RCP";
            case "contract" -> "CTR";
            case "quotation" -> "QTN";
            case "delivery_note" -> "DN";
            default -> "DOC";
        };

        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String basePrefix = prefix + dateStr;

        LambdaQueryWrapper<PurchaseDocument> wrapper = Wrappers.lambdaQuery();
        wrapper.likeRight(PurchaseDocument::getDocumentNo, basePrefix);
        wrapper.orderByDesc(PurchaseDocument::getDocumentNo);
        wrapper.last("LIMIT 1");
        List<PurchaseDocument> lastDocs = documentMapper.selectList(wrapper);

        int seq = 1;
        if (!lastDocs.isEmpty()) {
            String lastNo = lastDocs.get(0).getDocumentNo();
            String seqStr = lastNo.substring(basePrefix.length());
            try {
                seq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }

        return basePrefix + StringUtils.leftPad(String.valueOf(seq), 4, "0");
    }

    @Override
    public List<PurchaseDocument> selectByOrderId(Long orderId) {
        return documentMapper.selectByOrderId(orderId);
    }

    @Override
    public List<PurchaseDocument> selectBySupplierId(Long supplierId) {
        return documentMapper.selectBySupplierId(supplierId);
    }

    @Override
    public List<PurchaseDocument> selectPendingVerification() {
        return documentMapper.selectPendingVerification();
    }

    @Override
    public List<PurchaseDocument> selectVerified() {
        return documentMapper.selectVerified();
    }

    @Override
    public List<PurchaseDocument> selectToday() {
        return documentMapper.selectToday();
    }

    @Override
    public List<PurchaseDocument> selectWeek() {
        return documentMapper.selectWeek();
    }

    @Override
    public List<PurchaseDocument> selectMonth() {
        return documentMapper.selectMonth();
    }

    @Override
    public Map<String, Object> getDocumentStatistics() {
        List<PurchaseDocument> allDocs = documentMapper.selectList(Wrappers.emptyWrapper());

        long totalCount = allDocs.size();
        long pendingCount = allDocs.stream()
                .filter(d -> Objects.equals(DocumentStatus.PENDING.getValue(), d.getDocumentStatus()))
                .count();
        long verifiedCount = allDocs.stream()
                .filter(d -> Objects.equals(DocumentStatus.VERIFIED.getValue(), d.getDocumentStatus()))
                .count();
        long archivedCount = allDocs.stream()
                .filter(d -> Objects.equals(DocumentStatus.ARCHIVED.getValue(), d.getDocumentStatus()))
                .count();

        return Map.of(
                "totalCount", totalCount,
                "pendingCount", pendingCount,
                "verifiedCount", verifiedCount,
                "archivedCount", archivedCount
        );
    }

    @Override
    public String exportDocumentList(PurchaseDocumentDTO dto) {
        List<PurchaseDocument> list = selectDocumentList(dto);
        if (list.isEmpty()) {
            throw new BusinessException("没有可导出的数据");
        }

        String fileName = "采购票据列表_" + LocalDate.now().toString();
        String filePath = System.getProperty("java.io.tmpdir") + "/purchase_export/" + fileName + "_" + System.currentTimeMillis() + ".xlsx";

        // TODO: 使用POI生成Excel文件
        log.info("导出采购票据列表成功，文件路径: {}", filePath);
        return filePath;
    }

    @Override
    public Map<String, Object> uploadTempFile(Long orderId, MultipartFile file) {
        // 验证文件
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        // 允许的文件类型
        List<String> allowedTypes = List.of(
                "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp",
                "application/pdf",
                "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
            throw new BusinessException("不支持的文件类型，仅支持图片、PDF、Word、Excel格式");
        }

        // 最大文件大小（20MB）
        long maxSize = 20 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException("文件大小不能超过20MB");
        }

        try {
            // 查询订单号
            PurchaseOrder order = purchaseOrderMapper.selectById(orderId);
            if (order == null) {
                throw new BusinessException("订单不存在");
            }
            String orderNo = order.getOrderNo();

            // 创建订单号目录：./uploads/documents/{orderNo}/
            Path uploadDir = Paths.get("./uploads/documents", orderNo);
            Files.createDirectories(uploadDir);

            // 计算当前序号（基于目录中已有文件数）
            int seq = 1;
            try (Stream<Path> files = Files.list(uploadDir)) {
                long count = files.filter(Files::isRegularFile).count();
                seq = (int) count + 1;
            }

            // 生成文件名：{orderNo}-{seq}{ext}
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            String storageName = orderNo + "-" + StringUtils.leftPad(String.valueOf(seq), 3, "0") + extension;

            // 保存文件
            Path targetPath = uploadDir.resolve(storageName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 构建文件URL
            String fileUrl = "/uploads/documents/" + orderNo + "/" + storageName;

            log.info("临时上传票据文件成功: orderId={}, orderNo={}, fileName={}", orderId, orderNo, storageName);

            // 返回文件信息（不插入数据库）
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("fileName", originalName);
            fileInfo.put("storageName", storageName);
            fileInfo.put("fileUrl", fileUrl);
            fileInfo.put("fileSize", file.getSize());
            fileInfo.put("orderNo", orderNo);
            fileInfo.put("bizNo", orderNo);
            return fileInfo;

        } catch (IOException e) {
            log.error("临时上传票据文件失败: orderId={}", orderId, e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> selectDiskFilesByOrderId(Long orderId) {
        // 查询订单号
        PurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        String orderNo = order.getOrderNo();

        // 扫描订单号目录
        Path uploadDir = Paths.get("./uploads/documents", orderNo);
        if (!Files.exists(uploadDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> files = Files.list(uploadDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .sorted()
                    .map(path -> {
                        String fileName = path.getFileName().toString();
                        String fileUrl = "/uploads/documents/" + orderNo + "/" + fileName;
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("fileName", fileName);
                        fileInfo.put("storageName", fileName);
                        fileInfo.put("fileUrl", fileUrl);
                        try {
                            fileInfo.put("fileSize", Files.size(path));
                        } catch (IOException e) {
                            fileInfo.put("fileSize", 0);
                        }
                        fileInfo.put("orderNo", orderNo);
                        fileInfo.put("bizNo", orderNo);
                        return fileInfo;
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("查询磁盘票据文件失败: orderId={}, orderNo={}", orderId, orderNo, e);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchConfirmDocuments(Long orderId, Long supplierId, List<Map<String, Object>> files, String documentType) {
        if ("invoice".equalsIgnoreCase(documentType))
            throw new BusinessException("请使用发票登记填写真实票面信息，一张发票可关联多个文件");
        if (files == null || files.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Map<String, Object> fileInfo : files) {
            String fileName = (String) fileInfo.get("fileName");
            String fileUrl = (String) fileInfo.get("fileUrl");
            Long fileSize = fileInfo.get("fileSize") != null ? ((Number) fileInfo.get("fileSize")).longValue() : 0L;

            PurchaseDocument document = new PurchaseDocument();
            document.setOrderId(orderId);
            document.setSupplierId(supplierId);
            document.setDocumentNo(generateDocumentNo(documentType));
            document.setDocumentType(documentType);
            document.setDocumentDate(LocalDate.now());
            document.setDocumentAmount(java.math.BigDecimal.ZERO);
            document.setCurrency("CNY");
            document.setDocumentStatus(DocumentStatus.PENDING.getValue());
            // 新链路（dev-20261014-022）：files 里带 attachmentId 时，文件是公共附件临时件，
            // 只登记“发票文件=该附件”的关联（file_url 存公共下载地址），核销时随事务建引用；
            // 不写文件名，避免列表把“待核销的文件”误显成可下载文件。
            Long attachmentId = fileInfo.get("attachmentId") != null ? ((Number) fileInfo.get("attachmentId")).longValue() : null;
            if (attachmentId != null) {
                document.setFileUrl("/system/attachment/download/" + attachmentId);
            } else {
                document.setFileName(fileName);
                document.setFileUrl(fileUrl);
                document.setFileSize(fileSize);
            }

            documentMapper.insert(document);
            count++;
        }

        log.info("批量确认票据成功: orderId={}, documentType={}, count={}", orderId, documentType, count);
        return count;
    }

    @Override
    public void deleteTempFile(String fileUrl) {
        if (StringUtils.isEmpty(fileUrl)) {
            return;
        }

        // fileUrl 格式: /uploads/documents/{orderNo}/{fileName}
        // 转换为磁盘路径: ./uploads/documents/{orderNo}/{fileName}
        String relativePath = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;
        Path filePath = Paths.get(relativePath);

        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("删除临时票据文件成功: {}", fileUrl);
            } else {
                log.warn("临时票据文件不存在: {}", fileUrl);
            }
        } catch (IOException e) {
            log.error("删除临时票据文件失败: {}", fileUrl, e);
            throw new BusinessException("删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 复制DTO属性到实体
     */
    private void copyProperties(PurchaseDocumentDTO dto, PurchaseDocument document) {
        document.setDocumentNo(dto.getDocumentNo());
        document.setDocumentType(dto.getDocumentType());
        document.setOrderId(dto.getOrderId());
        document.setSupplierId(dto.getSupplierId());
        document.setDocumentDate(dto.getDocumentDate());
        document.setDocumentAmount(dto.getDocumentAmount());
        document.setCurrency(dto.getCurrency());
        document.setDocumentStatus(dto.getDocumentStatus());
        document.setVerificationDate(dto.getVerificationDate());
        document.setFileName(dto.getFileName());
        document.setFileUrl(dto.getFileUrl());
        document.setFileSize(dto.getFileSize());
        document.setRemark(dto.getRemark());
    }

    @Override
    public java.util.List<com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO> batchCheckDocument(java.util.List<com.jjx.purchase.domain.dto.DocumentBatchCheckItemDTO> items) {
        java.util.List<com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO> results = new java.util.ArrayList<>();
        if (items == null || items.isEmpty()) {
            return results;
        }

        // 文件内重复检测（同发票编号）
        java.util.Map<String, Integer> dupCountMap = new java.util.HashMap<>();
        for (com.jjx.purchase.domain.dto.DocumentBatchCheckItemDTO item : items) {
            String k = item.getDocumentNo() == null ? "" : item.getDocumentNo().trim();
            dupCountMap.merge(k, 1, Integer::sum);
        }

        for (com.jjx.purchase.domain.dto.DocumentBatchCheckItemDTO item : items) {
            com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO vo = new com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO();
            vo.setRowIndex(item.getRowIndex());
            vo.setStatus("ok");

            // 1. 发票编号必填 + 唯一
            String documentNo = item.getDocumentNo() == null ? "" : item.getDocumentNo().trim();
            if (documentNo.isEmpty()) {
                vo.setStatus("error");
                vo.setErrorType("MISSING_REQUIRED");
                addFieldError(vo, "documentNo", "MISSING_REQUIRED", "发票编号不能为空");
            } else {
                Integer dupCount = dupCountMap.get(documentNo);
                if (dupCount != null && dupCount > 1) {
                    vo.setStatus("error");
                    vo.setErrorType("DUPLICATE");
                    addFieldError(vo, "documentNo", "DUPLICATE", "文件内重复行（同一发票编号出现 " + dupCount + " 次），导入会冲突");
                } else if (checkDocumentNoUnique(documentNo)) {
                    vo.setStatus("error");
                    vo.setErrorType("DUPLICATE");
                    addFieldError(vo, "documentNo", "DUPLICATE", "发票编号已存在: " + documentNo);
                }
            }

            // 2. 订单存在性
            if (vo.getStatus().equals("ok")) {
                if (item.getOrderId() == null) {
                    vo.setStatus("error");
                    vo.setErrorType("MISSING_REQUIRED");
                    addFieldError(vo, "orderId", "MISSING_REQUIRED", "采购订单ID不能为空");
                } else if (purchaseOrderMapper.selectById(item.getOrderId()) == null) {
                    vo.setStatus("error");
                    vo.setErrorType("NOT_FOUND");
                    addFieldError(vo, "orderId", "NOT_FOUND", "采购订单不存在: " + item.getOrderId());
                }
            }

            // 3. 金额校验
            if (vo.getStatus().equals("ok")) {
                if (item.getDocumentAmount() == null || item.getDocumentAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    vo.setStatus("error");
                    vo.setErrorType("INVALID");
                    addFieldError(vo, "documentAmount", "INVALID", "发票金额必须大于0");
                }
            }

            // 4. 开票日期
            if (vo.getStatus().equals("ok") && item.getDocumentDate() == null) {
                vo.setStatus("error");
                vo.setErrorType("MISSING_REQUIRED");
                addFieldError(vo, "documentDate", "MISSING_REQUIRED", "开票日期不能为空");
            }

            results.add(vo);
        }
        return results;
    }

    private void addFieldError(com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO vo, String field, String type, String message) {
        com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO.FieldError fe = new com.jjx.purchase.domain.vo.PurchaseBatchCheckItemVO.FieldError();
        fe.setField(field);
        fe.setType(type);
        fe.setMessage(message);
        vo.getErrors().add(fe);
    }
}
