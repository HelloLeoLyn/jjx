package com.jjx.sales.domain.dto;

import com.jjx.sales.domain.entity.SalesInquiry;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 询价单新建请求（dev-20261015-034）：在询价实体之外，额外携带"随保存一起落定的附件"。
 * 保持与原有 JSON 结构兼容（只是多一个可选字段）。
 */
@Getter
@Setter
public class SalesInquiryCreateDTO extends SalesInquiry {
    /** 本次保存要挂到本单的附件（含资料类型；缺类型则拒绝保存） */
    private List<AttachmentBindDTO> attachments;
}
