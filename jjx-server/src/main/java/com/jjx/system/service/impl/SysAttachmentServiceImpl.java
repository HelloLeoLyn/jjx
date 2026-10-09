package com.jjx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.service.ISysAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通用附件服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAttachmentServiceImpl extends ServiceImpl<SysAttachmentMapper, SysAttachment>
        implements ISysAttachmentService {

    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;

    private final SysAttachmentMapper attachmentMapper;
    private final ProductMapper productMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final SysOperLogMapper sysOperLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadAttachment(MultipartFile file, String bizType, Long bizId, String traceId) {
        return uploadAttachment(file, bizType, bizId, traceId, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadAttachment(MultipartFile file, String bizType, Long bizId, String traceId,
                                 String category, String version) {
        return uploadAttachment(file, bizType, bizId, traceId, category, version, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadAttachment(MultipartFile file, String bizType, Long bizId, String traceId,
                                 String category, String version, String remark) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        // 生成存储路径：upload/{bizType}/{yyyy-MM-dd}/{uuid}.{ext}
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedName = UUID.randomUUID().toString() + ext;
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String relativePath = bizType + File.separator + dateDir + File.separator + storedName;
        String fullPath = uploadBasePath + File.separator + relativePath;

        try {
            Path dir = Paths.get(fullPath).getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            file.transferTo(Paths.get(fullPath));
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }

        // 保存记录
        SysAttachment attachment = new SysAttachment();
        attachment.setBizType(bizType);
        attachment.setBizId(bizId);
        attachment.setTraceId(traceId);
        attachment.setCategory(category);
        attachment.setVersion(version);
        attachment.setRemark(remark);
        attachment.setFileName(originalName != null ? originalName : "unknown");
        attachment.setFilePath(relativePath);
        attachment.setFileSize(file.getSize());
        attachment.setFileType(file.getContentType());

        save(attachment);
        // 附件回填日志 detail：同事务提交，保证流水可见（2026-08-28）
        attachToLatestOperLog(traceId, attachment);
        log.info("附件上传成功: id={}, bizType={}, bizId={}, file={}", attachment.getId(), bizType, bizId, originalName);
        return attachment.getId();
    }

    /**
     * 上传成功后把附件回填进同 traceId 最近一条操作日志的 detail.attachments，
     * 流水行即可直接解析显示附件（前端零改动，各模块上传都走这里自动生效）。
     * 与附件入库同事务：异常抛出则上传一起回滚；查不到日志（该 traceId 无操作记录）则跳过。
     */
    private void attachToLatestOperLog(String traceId, SysAttachment attachment) {
        if (attachment == null || attachment.getId() == null
                || traceId == null || traceId.isBlank()) {
            return;
        }
        try {
            SysOperLog latest = sysOperLogMapper.selectOne(
                    Wrappers.<SysOperLog>lambdaQuery()
                            .eq(SysOperLog::getTraceId, traceId)
                            .orderByDesc(SysOperLog::getCreateTime)
                            .last("LIMIT 1"));
            if (latest == null) {
                return; // 该 traceId 还没有操作日志（如纯附件场景），跳过回填
            }
            ObjectNode node = (ObjectNode) objectMapper.readTree(
                    latest.getDetail() == null || latest.getDetail().isBlank()
                            ? "{}" : latest.getDetail());
            if (!node.has("attachments") || !node.get("attachments").isArray()) {
                node.set("attachments", objectMapper.createArrayNode());
            }
            node.withArray("attachments").add(objectMapper.createObjectNode()
                    .put("id", attachment.getId())
                    .put("fileName", attachment.getFileName() == null
                            ? "附件" + attachment.getId() : attachment.getFileName()));
            latest.setDetail(objectMapper.writeValueAsString(node));
            sysOperLogMapper.updateById(latest);
        } catch (Exception e) {
            // 事务内抛出：附件入库一起回滚，保证 detail 与附件表一致
            log.error("附件回填日志detail失败: traceId={}, attachmentId={}, err={}",
                    traceId, attachment.getId(), e.getMessage());
            throw new BusinessException("附件上传失败：日志回填异常");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchUploadAttachments(List<MultipartFile> files, String bizType, Long bizId) {
        return batchUploadAttachments(files, bizType, bizId, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchUploadAttachments(List<MultipartFile> files, String bizType, Long bizId,
                                             String category, String version) {
        List<Long> ids = new ArrayList<>();
        if (files == null) return ids;
        for (MultipartFile file : files) {
            ids.add(uploadAttachment(file, bizType, bizId, null, category, version));
        }
        return ids;
    }

    @Override
    public List<SysAttachment> getAttachmentsByTraceId(String traceId) {
        if (traceId == null || traceId.isEmpty()) return new ArrayList<>();
        LambdaQueryWrapper<SysAttachment> wrapper = new LambdaQueryWrapper<SysAttachment>()
                .eq(SysAttachment::getTraceId, traceId)
                .orderByAsc(SysAttachment::getCreateTime);
        return list(wrapper);
    }

    @Override
    public List<com.jjx.system.domain.vo.AttachmentSourceVO> getAttachmentSourcesByTraceId(String traceId) {
        List<com.jjx.system.domain.vo.AttachmentSourceVO> result = new ArrayList<>();
        if (traceId == null || traceId.isEmpty()) return result;

        List<SysAttachment> attachments = getAttachmentsByTraceId(traceId);
        for (SysAttachment att : attachments) {
            com.jjx.system.domain.vo.AttachmentSourceVO vo = new com.jjx.system.domain.vo.AttachmentSourceVO();
            vo.setId(att.getId());
            vo.setBizType(att.getBizType());
            vo.setBizId(att.getBizId());
            vo.setRemark(att.getRemark());
            vo.setCategory(att.getCategory());
            vo.setFileName(att.getFileName());
            vo.setFileSize(att.getFileSize());
            vo.setFileType(att.getFileType());
            vo.setCreateBy(att.getCreateBy());
            vo.setCreateTime(att.getCreateTime());
            vo.setTraceId(att.getTraceId());

            // 来源单据类型名 + 单号反查
            vo.setBizTypeName(resolveBizTypeName(att.getBizType()));
            vo.setSourceNo(resolveSourceNo(att.getBizType(), att.getBizId()));
            result.add(vo);
        }
        return result;
    }

    /** 业务类型 → 中文名 + 对应主表单号字段 */
    private String resolveBizTypeName(String bizType) {
        if (bizType == null) return "附件";
        switch (bizType) {
            case "quotation": return "报价单";
            case "inquiry": return "询价单";
            case "order":
            case "sales_order": return "销售订单";
            case "sample":
            case "sample_order": return "样品单";
            case "purchase": return "采购订单";
            case "production": return "生产工单";
            case "product": return "产品文件";
            default: return bizType;
        }
    }

    /** 反查来源单号：按业务类型查对应主表的单号字段 */
    private String resolveSourceNo(String bizType, Long bizId) {
        if (bizType == null || bizId == null) return null;
        String table;
        String col;
        switch (bizType) {
            case "quotation": table = "sales_quotation"; col = "quotation_no"; break;
            case "inquiry": table = "sales_inquiry"; col = "inquiry_no"; break;
            case "order":
            case "sales_order": table = "sales_order"; col = "order_no"; break;
            case "sample":
            case "sample_order": table = "sales_order"; col = "order_no"; break;
            case "purchase": table = "purchase_order"; col = "purchase_no"; break;
            case "production": table = "production_order"; col = "order_no"; break;
            case "product": table = "product"; col = "product_code"; break;
            default: return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT " + col + " FROM " + table + " WHERE id = ?", String.class, bizId);
        } catch (Exception e) {
            log.warn("反查来源单号失败: bizType={}, bizId={}, err={}", bizType, bizId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<SysAttachment> getAttachments(String bizType, Long bizId) {
        LambdaQueryWrapper<SysAttachment> wrapper = new LambdaQueryWrapper<SysAttachment>()
                .eq(SysAttachment::getBizType, bizType)
                .eq(SysAttachment::getBizId, bizId)
                .orderByAsc(SysAttachment::getSortOrder)
                .orderByAsc(SysAttachment::getCreateTime);
        return list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadProductFile(MultipartFile file, String productCode, String category, String version) {
        return uploadProductFile(file, productCode, category, version, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadProductFile(MultipartFile file, String productCode, String category, String version,
                                  String drawingNo, String drawingName, String fileRole) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (productCode == null || productCode.trim().isEmpty()) {
            throw new BusinessException("产品编码不能为空");
        }
        if (category == null || category.trim().isEmpty()) {
            throw new BusinessException("文件类别不能为空");
        }
        // 校验产品存在
        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>().eq(Product::getProductCode, productCode.trim()));
        if (product == null) {
            throw new BusinessException("产品不存在: " + productCode);
        }

        lockProduct(product.getProductId());
        drawingNo = optionalText(drawingNo, 80, "图纸编号");
        drawingName = optionalText(drawingName, 120, "图纸名称");
        version = optionalText(version, 50, "版本");
        fileRole = optionalText(fileRole, 20, "文件用途");
        List<SysAttachment> drawingFiles = new ArrayList<>();
        if (drawingNo != null) {
            drawingNo = drawingNo.toUpperCase(java.util.Locale.ROOT);
            version = requiredText(version, 50, "版本").toUpperCase(java.util.Locale.ROOT);
            fileRole = validateRole(fileRole, file);
            drawingFiles = drawingFiles(product.getProductId(), drawingNo);
            validateDrawingFile(drawingFiles, null, category.trim(), version, fileRole);
            if (drawingName == null) drawingName = drawingFiles.isEmpty() ? drawingNo : drawingFiles.get(0).getDrawingName();
        } else if (fileRole != null || drawingName != null) {
            throw new BusinessException("填写图纸编号后才能关联文件用途及图纸名称");
        }

        // 存储：upload/product/{产品编码}/{类别}/{yyyy-MM-dd}/{原始文件名}（工程部习惯保留原名，重名加序号）
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.trim().isEmpty()) {
            originalName = "unknown";
        }
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String relativePath = "product" + File.separator + productCode.trim() + File.separator
                + category.trim() + File.separator + dateDir + File.separator + originalName;
        String fullPath = uploadBasePath + File.separator + relativePath;

        // 重名处理：追加序号 (1)/(2)...
        Path target = Paths.get(fullPath);
        if (Files.exists(target)) {
            int dot = originalName.lastIndexOf('.');
            String stem = dot > 0 ? originalName.substring(0, dot) : originalName;
            String ext = dot > 0 ? originalName.substring(dot) : "";
            int idx = 1;
            while (Files.exists(target)) {
                relativePath = "product" + File.separator + productCode.trim() + File.separator
                        + category.trim() + File.separator + dateDir + File.separator
                        + stem + "(" + idx + ")" + ext;
                target = Paths.get(uploadBasePath + File.separator + relativePath);
                idx++;
            }
        }

        try {
            Path dir = target.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            file.transferTo(target);
        } catch (IOException e) {
            log.error("产品文件上传失败: {}", e.getMessage(), e);
            throw new BusinessException("产品文件上传失败: " + e.getMessage());
        }

        SysAttachment attachment = new SysAttachment();
        attachment.setBizType("product");
        attachment.setBizId(product.getProductId());
        attachment.setCategory(category.trim());
        attachment.setVersion(version);
        attachment.setDrawingNo(drawingNo);
        attachment.setDrawingName(drawingName);
        attachment.setFileRole(fileRole);
        final String uploadedVersion = version;
        attachment.setIsCurrent(drawingFiles.stream().anyMatch(a -> sameVersion(a, uploadedVersion) && yes(a.getIsCurrent()))
                ? YesNoEnum.YES.getCode() : YesNoEnum.NO.getCode());
        attachment.setFileName(originalName);
        attachment.setFilePath(relativePath);
        attachment.setFileSize(file.getSize());
        attachment.setFileType(file.getContentType());

        save(attachment);
        log.info("产品文件上传成功: id={}, productCode={}, category={}, file={}",
                attachment.getId(), productCode, category, originalName);
        return attachment.getId();
    }

    @Override
    public List<SysAttachment> getProductFiles(String productCode) {
        if (productCode == null || productCode.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>().eq(Product::getProductCode, productCode.trim()));
        if (product == null) {
            return new ArrayList<>();
        }
        return list(new LambdaQueryWrapper<SysAttachment>()
                .eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, product.getProductId())
                .orderByAsc(SysAttachment::getCategory)
                .orderByDesc(SysAttachment::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAttachment(Long id) {
        SysAttachment attachment = getById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在: " + id);
        }
        if ("product".equals(attachment.getBizType()) && attachment.getDrawingNo() != null) {
            attachment = lockedProductFile(id);
            if (yes(attachment.getIsCurrent()) || yes(attachment.getReleased())) {
                throw new BusinessException("现行或已下发图纸不能删除，请先切换版本并撤回下发");
            }
        }
        // 软删除（DEV-737）：进回收站，保留物理文件，update_time 记录删除时间
        return attachmentMapper.logicalDelete(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAttachmentsByBiz(String bizType, Long bizId) {
        List<SysAttachment> list = getAttachments(bizType, bizId);
        for (SysAttachment attachment : list) {
            deleteAttachment(attachment.getId());
        }
        return true;
    }

    @Override
    public List<SysAttachment> getRecycled() {
        return attachmentMapper.selectRecycled();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreAttachment(Long id) {
        SysAttachment recycled = attachmentMapper.selectRecycled().stream()
                .filter(a -> a.getId().equals(id)).findFirst().orElse(null);
        if (recycled == null) {
            throw new BusinessException("回收站中不存在该附件: " + id);
        }
        if ("product".equals(recycled.getBizType()) && recycled.getDrawingNo() != null) {
            lockProduct(recycled.getBizId());
            validateDrawingFile(drawingFiles(recycled.getBizId(), recycled.getDrawingNo()), null,
                    recycled.getCategory(), recycled.getVersion(), recycled.getFileRole());
            if (attachmentMapper.restore(id) <= 0) return false;
            // 恢复文件不恢复现行/下发资格，需重新明确版本并下发。
            return update(Wrappers.<SysAttachment>lambdaUpdate().eq(SysAttachment::getId, id)
                    .set(SysAttachment::getIsCurrent, YesNoEnum.NO.getCode()).set(SysAttachment::getReleased, YesNoEnum.NO.getCode())
                    .set(SysAttachment::getReleasedAt, null).set(SysAttachment::getReleasedBy, null));
        }
        return attachmentMapper.restore(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean permanentDelete(Long id) {
        SysAttachment recycled = attachmentMapper.selectRecycled().stream()
                .filter(a -> a.getId().equals(id)).findFirst().orElse(null);
        if (recycled == null) {
            throw new BusinessException("回收站中不存在该附件: " + id);
        }
        // 删物理文件 + 真删记录
        String fullPath = uploadBasePath + File.separator + recycled.getFilePath();
        try {
            Files.deleteIfExists(Paths.get(fullPath));
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", fullPath);
        }
        return attachmentMapper.physicalDelete(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int permanentDeleteExpired(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        List<SysAttachment> expired = attachmentMapper.selectRecycledBefore(cutoff);
        int count = 0;
        for (SysAttachment attachment : expired) {
            String fullPath = uploadBasePath + File.separator + attachment.getFilePath();
            try {
                Files.deleteIfExists(Paths.get(fullPath));
            } catch (IOException e) {
                log.warn("清理回收站物理文件失败: {}", fullPath);
            }
            attachmentMapper.physicalDelete(attachment.getId());
            count++;
        }
        if (count > 0) {
            log.info("[回收站] 清理过期附件 {} 个（{} 天前）", count, days);
        }
        return count;
    }

    /**
     * 客供资料归集：某产品关联的询价/报价附件（只读引用；dev-20260929-023）
     */
    public List<Map<String, Object>> customerDocs(Long productId) {
        if (productId == null) return List.of();
        String sql = """
                SELECT a.id,a.file_name,a.file_path,a.file_size,a.file_type,a.version,a.category,a.create_by,a.create_time,
                       '询价单' AS sourceType,i.inquiry_no AS sourceNo
                  FROM sales_inquiry i
                  JOIN sys_attachment a ON a.biz_type='inquiry' AND a.biz_id=i.inquiry_id AND a.deleted=0
                 WHERE i.product_id=?
                UNION ALL
                SELECT a.id,a.file_name,a.file_path,a.file_size,a.file_type,a.version,a.category,a.create_by,a.create_time,
                       '报价单' AS sourceType,q.quotation_no AS sourceNo
                  FROM sales_quotation_item qi
                  JOIN sales_quotation q ON q.quotation_id=qi.quotation_id
                  JOIN sys_attachment a ON a.biz_type='quotation' AND a.biz_id=q.quotation_id AND a.deleted=0
                 WHERE qi.product_id=?
                 ORDER BY create_time DESC
                """;
        return jdbcTemplate.queryForList(sql, productId, productId);
    }

    @Override
    public String getAttachmentFilePath(Long id) {
        SysAttachment attachment = getById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在: " + id);
        }
        return uploadBasePath + File.separator + attachment.getFilePath();
    }

    /** 产品行锁统一串行化同一产品的版本操作，避免并发产生两个现行版本。 */
    private void lockProduct(Long productId) {
        jdbcTemplate.queryForObject("SELECT product_id FROM product WHERE product_id = ? FOR UPDATE", Long.class, productId);
    }

    private SysAttachment lockedProductFile(Long id) {
        SysAttachment file = getById(id);
        if (file == null || !"product".equals(file.getBizType())) throw new BusinessException("产品文件不存在");
        lockProduct(file.getBizId());
        file = attachmentMapper.selectActiveForUpdate(id);
        if (file == null) throw new BusinessException("产品文件已删除，请刷新");
        return file;
    }

    private List<SysAttachment> drawingFiles(Long productId, String drawingNo) {
        return attachmentMapper.selectDrawingForUpdate(productId, drawingNo);
    }

    private boolean yes(Integer value) { return YesNoEnum.YES.getCode().equals(value); }
    private boolean sameVersion(SysAttachment file, String version) { return java.util.Objects.equals(file.getVersion(), version); }
    private String optionalText(String value, int max, String label) {
        if (value == null || value.trim().isEmpty()) return null;
        String text = value.trim();
        if (text.length() > max) throw new BusinessException(label + "不能超过" + max + "个字符");
        return text;
    }
    private String requiredText(String value, int max, String label) {
        String text = optionalText(value, max, label);
        if (text == null) throw new BusinessException(label + "不能为空");
        return text;
    }
    private String validateRole(String value, MultipartFile file) {
        if (!"ORIGINAL".equals(value) && !"PRINT".equals(value)) throw new BusinessException("请选择原稿或预览打印件");
        if ("PRINT".equals(value)) {
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
            String mime = file.getContentType() == null ? "" : file.getContentType();
            if (!(mime.startsWith("image/") || mime.equals("application/pdf") || name.matches(".*\\.(pdf|png|jpg|jpeg|gif|bmp|webp|svg)$"))) {
                throw new BusinessException("预览打印件仅支持PDF或图片；其他格式请选择工程原稿");
            }
        }
        return value;
    }
    private void validateDrawingFile(List<SysAttachment> files, Long excludeId, String category, String version, String role) {
        for (SysAttachment file : files) {
            // 已下发版本不可追加、改关联，避免无需再次下发便改变生产资料。
            if (sameVersion(file, version) && yes(file.getReleased())) throw new BusinessException("该版本已下发，请先撤回或上传新版本");
            if (file.getId().equals(excludeId)) continue;
            if (!java.util.Objects.equals(file.getCategory(), category)) throw new BusinessException("同一图纸编号的文件类别必须一致");
            if (sameVersion(file, version) && java.util.Objects.equals(file.getFileRole(), role)) {
                throw new BusinessException("该图纸版本已存在相同用途的文件，请使用新版本或先删除未下发的重复文件");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDrawingMetadata(Long id, String drawingNo, String drawingName, String version, String fileRole, String category) {
        SysAttachment file = lockedProductFile(id);
        category = optionalText(category, 50, "文件类别");
        if (category == null) category = file.getCategory();
        drawingNo = requiredText(drawingNo, 80, "图纸编号").toUpperCase(java.util.Locale.ROOT);
        drawingName = optionalText(drawingName, 120, "图纸名称");
        version = requiredText(version, 50, "版本").toUpperCase(java.util.Locale.ROOT);
        if (!"ORIGINAL".equals(fileRole) && !"PRINT".equals(fileRole)) throw new BusinessException("请选择文件用途");
        if ("PRINT".equals(fileRole)) {
            String name = file.getFileName().toLowerCase(java.util.Locale.ROOT);
            String mime = file.getFileType() == null ? "" : file.getFileType();
            if (!(mime.startsWith("image/") || mime.equals("application/pdf") || name.matches(".*\\.(pdf|png|jpg|jpeg|gif|bmp|webp|svg)$"))) {
                throw new BusinessException("预览打印件仅支持PDF或图片");
            }
        }
        if (yes(file.getReleased())) throw new BusinessException("已下发文件请先撤回，再修改图纸关联");
        if (file.getDrawingNo() != null && yes(file.getIsCurrent()) &&
                (!file.getDrawingNo().equals(drawingNo) || !sameVersion(file, version) || !java.util.Objects.equals(file.getFileRole(), fileRole) || !java.util.Objects.equals(file.getCategory(), category))) {
            throw new BusinessException("现行版本请上传新版本，不直接修改图纸关联");
        }
        List<SysAttachment> files = drawingFiles(file.getBizId(), drawingNo);
        validateDrawingFile(files, id, category, version, fileRole);
        final String targetVersion = version;
        boolean otherVersionCurrent = files.stream().anyMatch(a -> !a.getId().equals(id) && yes(a.getIsCurrent()) && !sameVersion(a, targetVersion));
        // 归集旧文件时，以目标图纸既有现行版本为准，不把旧文件的独立标记带入另一个版本。
        boolean current = (yes(file.getIsCurrent()) && !otherVersionCurrent)
                || files.stream().anyMatch(a -> sameVersion(a, targetVersion) && yes(a.getIsCurrent()));
        // 同一版本的配套文件同步现行，避免绑定后只有原稿或只有PDF现行。
        if (current) update(Wrappers.<SysAttachment>lambdaUpdate().eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, file.getBizId()).eq(SysAttachment::getDrawingNo, drawingNo)
                .eq(SysAttachment::getVersion, version).set(SysAttachment::getIsCurrent, YesNoEnum.YES.getCode()));
        return update(Wrappers.<SysAttachment>lambdaUpdate().eq(SysAttachment::getId, id)
                .set(SysAttachment::getDrawingNo, drawingNo).set(SysAttachment::getDrawingName, drawingName == null ? drawingNo : drawingName)
                .set(SysAttachment::getCategory, category)
                .set(SysAttachment::getVersion, version).set(SysAttachment::getFileRole, fileRole)
                .set(SysAttachment::getIsCurrent, current ? YesNoEnum.YES.getCode() : YesNoEnum.NO.getCode()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setCurrentAttachment(Long id) {
        SysAttachment file = lockedProductFile(id);
        if (file.getDrawingNo() == null || file.getDrawingNo().isBlank()) {
            // 未归集历史文件各自独立，不能再按类别清除其他图纸。
            return update(Wrappers.<SysAttachment>lambdaUpdate().eq(SysAttachment::getId, id)
                    .set(SysAttachment::getIsCurrent, YesNoEnum.YES.getCode()));
        }
        List<SysAttachment> files = drawingFiles(file.getBizId(), file.getDrawingNo());
        boolean alreadyCurrent = files.stream().filter(a -> sameVersion(a, file.getVersion())).allMatch(a -> yes(a.getIsCurrent()));
        if (alreadyCurrent) return true;
        update(Wrappers.<SysAttachment>lambdaUpdate().eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, file.getBizId()).eq(SysAttachment::getDrawingNo, file.getDrawingNo())
                .set(SysAttachment::getIsCurrent, YesNoEnum.NO.getCode()));
        // 恢复历史版本为现行也必须重新下发；其他历史版本保留原下发信息。
        return update(Wrappers.<SysAttachment>lambdaUpdate().eq(SysAttachment::getBizType, "product")
                .eq(SysAttachment::getBizId, file.getBizId()).eq(SysAttachment::getDrawingNo, file.getDrawingNo())
                .eq(SysAttachment::getVersion, file.getVersion()).set(SysAttachment::getIsCurrent, YesNoEnum.YES.getCode())
                .set(SysAttachment::getReleased, YesNoEnum.NO.getCode()).set(SysAttachment::getReleasedAt, null)
                .set(SysAttachment::getReleasedBy, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setReleased(Long id, boolean released) {
        SysAttachment file = lockedProductFile(id);
        List<SysAttachment> versionFiles = file.getDrawingNo() == null || file.getDrawingNo().isBlank()
                ? java.util.Collections.singletonList(file)
                : drawingFiles(file.getBizId(), file.getDrawingNo()).stream().filter(a -> sameVersion(a, file.getVersion())).toList();
        if (released && versionFiles.stream().anyMatch(a -> !yes(a.getIsCurrent()))) {
            throw new BusinessException("只有现行版本可以下发，请先设为现行");
        }
        String operator = released ? String.valueOf(cn.dev33.satoken.stp.StpUtil.getLoginId()) : null;
        List<Long> ids = versionFiles.stream().map(SysAttachment::getId).toList();
        return update(Wrappers.<SysAttachment>lambdaUpdate().in(SysAttachment::getId, ids)
                .set(SysAttachment::getReleased, released ? YesNoEnum.YES.getCode() : YesNoEnum.NO.getCode())
                .set(SysAttachment::getIsControlled, YesNoEnum.YES.getCode())
                .set(SysAttachment::getReleasedAt, released ? LocalDateTime.now() : null)
                .set(SysAttachment::getReleasedBy, operator));
    }
}
