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

/**
 * 采购票据服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseDocumentServiceImpl extends ServiceImpl<PurchaseDocumentMapper, PurchaseDocument> implements IPurchaseDocumentService {

    private final PurchaseDocumentMapper documentMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;

    @Override
    public List<PurchaseDocument> selectDocumentList(PurchaseDocumentDTO dto) {
        LambdaQueryWrapper<PurchaseDocument> wrapper = Wrappers.lambdaQuery();
        if (dto != null) {
            if (StringUtils.isNotEmpty(dto.getDocumentNo())) {
                wrapper.like(PurchaseDocument::getDocumentNo, dto.getDocumentNo());
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
        }
        wrapper.orderByDesc(PurchaseDocument::getCreateTime);
        return documentMapper.selectList(wrapper);
    }

    @Override
    public PurchaseDocument selectDocumentById(Long documentId) {
        PurchaseDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("票据不存在");
        }
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertDocument(PurchaseDocumentDTO dto) {
        // 检查票据编号是否唯一
        if (checkDocumentNoUnique(dto.getDocumentNo())) {
            throw new BusinessException("票据编号已存在");
        }

        PurchaseDocument document = new PurchaseDocument();
        copyProperties(dto, document);

        // 设置默认状态
        if (document.getDocumentStatus() == null) {
            document.setDocumentStatus(DocumentStatus.PENDING.getCode());
        }

        return documentMapper.insert(document);
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
        return documentMapper.deleteById(documentId);
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
        PurchaseDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("票据不存在");
        }

        if (!Objects.equals(DocumentStatus.PENDING.getCode(), document.getDocumentStatus())) {
            throw new BusinessException("只有待处理状态的票据可以核验");
        }

        document.setDocumentStatus(DocumentStatus.VERIFIED.getCode());
        document.setVerificationDate(verificationDate != null ? LocalDate.parse(verificationDate) : LocalDate.now());
        if (StringUtils.isNotEmpty(verificationRemark)) {
            document.setRemark(verificationRemark);
        }
        document.setUpdateTime(LocalDateTime.now());

        return documentMapper.updateById(document);
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
                .filter(d -> Objects.equals(DocumentStatus.PENDING.getCode(), d.getDocumentStatus()))
                .count();
        long verifiedCount = allDocs.stream()
                .filter(d -> Objects.equals(DocumentStatus.VERIFIED.getCode(), d.getDocumentStatus()))
                .count();
        long archivedCount = allDocs.stream()
                .filter(d -> Objects.equals(DocumentStatus.ARCHIVED.getCode(), d.getDocumentStatus()))
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
    public int batchConfirmDocuments(Long orderId, Long supplierId, List<Map<String, Object>> files) {
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
            document.setDocumentNo(generateDocumentNo("receipt"));
            document.setDocumentType("receipt");
            document.setDocumentDate(LocalDate.now());
            document.setDocumentAmount(java.math.BigDecimal.ZERO);
            document.setCurrency("CNY");
            document.setDocumentStatus(DocumentStatus.PENDING.getCode());
            document.setFileName(fileName);
            document.setFileUrl(fileUrl);
            document.setFileSize(fileSize);

            documentMapper.insert(document);
            count++;
        }

        log.info("批量确认票据成功: orderId={}, count={}", orderId, count);
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
}
