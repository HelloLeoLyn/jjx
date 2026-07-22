package com.jjx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadAttachment(MultipartFile file, String bizType, Long bizId) {
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
        attachment.setFileName(originalName != null ? originalName : "unknown");
        attachment.setFilePath(relativePath);
        attachment.setFileSize(file.getSize());
        attachment.setFileType(file.getContentType());

        save(attachment);
        log.info("附件上传成功: id={}, bizType={}, bizId={}, file={}", attachment.getId(), bizType, bizId, originalName);
        return attachment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchUploadAttachments(List<MultipartFile> files, String bizType, Long bizId) {
        List<Long> ids = new ArrayList<>();
        if (files == null) return ids;
        for (MultipartFile file : files) {
            ids.add(uploadAttachment(file, bizType, bizId));
        }
        return ids;
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
    public boolean deleteAttachment(Long id) {
        SysAttachment attachment = getById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在: " + id);
        }

        // 删除物理文件
        String fullPath = uploadBasePath + File.separator + attachment.getFilePath();
        try {
            Files.deleteIfExists(Paths.get(fullPath));
        } catch (IOException e) {
            log.warn("删除物理文件失败: {}", fullPath, e);
        }

        // 删除记录
        return removeById(id);
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
    public String getAttachmentFilePath(Long id) {
        SysAttachment attachment = getById(id);
        if (attachment == null) {
            throw new BusinessException("附件不存在: " + id);
        }
        return uploadBasePath + File.separator + attachment.getFilePath();
    }
}
