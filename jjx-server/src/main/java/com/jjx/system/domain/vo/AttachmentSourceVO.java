package com.jjx.system.domain.vo;

import java.time.LocalDateTime;

/**
 * 附件链路增强 VO（2026-08-11）
 * 在附件原始字段基础上补充来源单据信息，用于 TraceAttachmentPanel 展示
 * "这个附件是什么"（来源单据类型 + 单号 + 类型标签 remark）
 */
public class AttachmentSourceVO {

    private Long id;

    /** 业务类型（quotation/inquiry/order/purchase/production...） */
    private String bizType;

    /** 业务类型中文名（报价单/询价单/销售订单...） */
    private String bizTypeName;

    /** 来源单据ID */
    private Long bizId;

    /** 来源单据号（如 QT2608110002），查不到则为空 */
    private String sourceNo;

    /** 附件类型标签（图纸/单据/凭证...，存 remark 字段） */
    private String remark;

    /** 分类（产品文件库用） */
    private String category;

    private String fileName;

    private Long fileSize;

    private String fileType;

    private String createBy;

    private LocalDateTime createTime;

    private String traceId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizTypeName() {
        return bizTypeName;
    }

    public void setBizTypeName(String bizTypeName) {
        this.bizTypeName = bizTypeName;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public void setSourceNo(String sourceNo) {
        this.sourceNo = sourceNo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
