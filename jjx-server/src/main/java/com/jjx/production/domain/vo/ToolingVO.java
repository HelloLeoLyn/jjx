package com.jjx.production.domain.vo;

import com.jjx.production.enums.ToolingStatusEnum;
import com.jjx.production.enums.ToolingTypeEnum;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工装模具出参 VO
 * 含类型/状态中文名 + 实物照片附件ID
 */
@Data
public class ToolingVO {

    private Long toolingId;
    private String toolingNo;
    private String toolingName;
    private String toolingType;
    private String typeLabel;
    private String spec;

    // 刀模专属
    private Integer lifeLimit;
    private Integer currentCount;

    // 公共
    private Integer status;
    private String statusLabel;
    private String location;
    private String department;
    private String responsible;
    private String customer;
    private LocalDate enableDate;
    private LocalDateTime lastUseTime;
    private Integer useCount;
    private String remark;

    // 审计
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;

    /** 实物照片附件ID（取 sys_attachment bizType=tooling 下第一条），前端拼下载地址 */
    private Long photoId;

    public static ToolingVO fromEntity(com.jjx.production.domain.entity.ProductionTooling e) {
        if (e == null) return null;
        ToolingVO vo = new ToolingVO();
        vo.setToolingId(e.getToolingId());
        vo.setToolingNo(e.getToolingNo());
        vo.setToolingName(e.getToolingName());
        vo.setToolingType(e.getToolingType());
        vo.setTypeLabel(ToolingTypeEnum.fromCode(e.getToolingType()) == null ? e.getToolingType()
                : ToolingTypeEnum.fromCode(e.getToolingType()).getLabel());
        vo.setSpec(e.getSpec());
        vo.setLifeLimit(e.getLifeLimit());
        vo.setCurrentCount(e.getCurrentCount());
        vo.setStatus(e.getStatus());
        vo.setStatusLabel(ToolingStatusEnum.labelOf(e.getStatus()));
        vo.setLocation(e.getLocation());
        vo.setDepartment(e.getDepartment());
        vo.setResponsible(e.getResponsible());
        vo.setCustomer(e.getCustomer());
        vo.setEnableDate(e.getEnableDate());
        vo.setLastUseTime(e.getLastUseTime());
        vo.setUseCount(e.getUseCount());
        vo.setRemark(e.getRemark());
        vo.setCreateBy(e.getCreateBy());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateBy(e.getUpdateBy());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }
}
