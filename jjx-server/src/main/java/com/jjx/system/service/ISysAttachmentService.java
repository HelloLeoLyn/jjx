package com.jjx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.system.domain.entity.SysAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 通用附件服务接口
 */
public interface ISysAttachmentService extends IService<SysAttachment> {

    /**
     * 上传附件
     *
     * @param file    上传的文件
     * @param bizType 业务类型
     * @param bizId   业务记录ID
     * @return 附件ID
     */
    Long uploadAttachment(MultipartFile file, String bizType, Long bizId, String traceId);

    /**
     * 批量上传附件
     */
    List<Long> batchUploadAttachments(List<MultipartFile> files, String bizType, Long bizId);

    /**
     * 获取附件列表
     */
    List<SysAttachment> getAttachments(String bizType, Long bizId);

    /**
     * 按链路追踪ID查询所有关联附件（含来源单据的文档）
     */
    List<SysAttachment> getAttachmentsByTraceId(String traceId);

    /**
     * 删除附件（含物理文件）
     */
    boolean deleteAttachment(Long id);

    /**
     * 批量删除附件（按业务关联）
     */
    boolean deleteAttachmentsByBiz(String bizType, Long bizId);

    /**
     * 获取附件文件路径
     */
    String getAttachmentFilePath(Long id);
}
