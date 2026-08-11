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
     * 上传附件（含类别/版本，产品文件库用）
     */
    Long uploadAttachment(MultipartFile file, String bizType, Long bizId, String traceId,
                          String category, String version);

    /**
     * 上传附件（含类别/版本/备注，产品文件库 + 类型标签用）
     */
    Long uploadAttachment(MultipartFile file, String bizType, Long bizId, String traceId,
                          String category, String version, String remark);

    /**
     * 批量上传附件
     */
    List<Long> batchUploadAttachments(List<MultipartFile> files, String bizType, Long bizId);

    /**
     * 批量上传附件（含类别/版本）
     */
    List<Long> batchUploadAttachments(List<MultipartFile> files, String bizType, Long bizId,
                                      String category, String version);

    /**
     * 上传产品工程文件（产品文件库）
     * 存储：upload/product/{产品编码}/{类别}/{日期}/{原始文件名}
     *
     * @param file        文件
     * @param productCode 产品编码（校验存在）
     * @param category    文件类别（客供稿/模具/确认图/菲林/规范等）
     * @param version     版本号（可空）
     * @return 附件ID
     */
    Long uploadProductFile(MultipartFile file, String productCode, String category, String version);

    /**
     * 获取产品文件库（按产品编码）
     */
    List<SysAttachment> getProductFiles(String productCode);

    /**
     * 获取附件列表
     */
    List<SysAttachment> getAttachments(String bizType, Long bizId);

    /**
     * 按链路追踪ID查询所有关联附件（含来源单据的文档）
     */
    List<SysAttachment> getAttachmentsByTraceId(String traceId);

    /**
     * 按链路追踪ID获取附件增强版（含来源单据类型名+单号，2026-08-11）
     */
    List<com.jjx.system.domain.vo.AttachmentSourceVO> getAttachmentSourcesByTraceId(String traceId);

    /**
     * 删除附件（软删除：进回收站，保留物理文件，DEV-737）
     */
    boolean deleteAttachment(Long id);

    /**
     * 批量删除附件（按业务关联，软删除）
     */
    boolean deleteAttachmentsByBiz(String bizType, Long bizId);

    /**
     * 回收站列表
     */
    List<SysAttachment> getRecycled();

    /**
     * 恢复附件（回收站还原）
     */
    boolean restoreAttachment(Long id);

    /**
     * 彻底删除（删物理文件 + 真删记录）
     */
    boolean permanentDelete(Long id);

    /**
     * 清理回收站中删除超过指定天数的附件，返回清理数量
     */
    int permanentDeleteExpired(int days);

    /**
     * 获取附件文件路径
     */
    String getAttachmentFilePath(Long id);
}
