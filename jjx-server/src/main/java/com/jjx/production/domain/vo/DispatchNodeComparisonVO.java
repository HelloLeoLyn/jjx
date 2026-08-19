package com.jjx.production.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * Node vs legacy operators 一致性诊断结果（P1-E cutover 前检查工具）
 * 非业务 API，仅供 migration/helper/测试使用。
 */
@Data
public class DispatchNodeComparisonVO {

    /** MATCH / MISMATCH / NODE_ONLY / LEGACY_ONLY / EMPTY */
    private String result;

    private Long dispatchId;

    /** Node 链人数 */
    private int nodeAssigneeCount;

    /** legacy operators 人数 */
    private int legacyAssigneeCount;

    /** 差异说明（MISMATCH 时列出） */
    private String detail;

    /** Node 链 assignee 列表（userId, 按责任顺序） */
    private List<Long> nodeAssigneeIds;

    /** legacy assignee 列表（userId, 按责任顺序） */
    private List<Long> legacyAssigneeIds;
}
