package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** BOM 变更单（ECN-lite，dev-20261112-009）：多层 BOM 打包提审与统一生效。 */
@Data
@TableName("bom_change_request")
public class BomChangeRequest {

    @TableId(value = "change_id", type = IdType.AUTO)
    private Long changeId;
    private String changeNo;
    private String title;
    private LocalDate effectiveDate;
    /** DRAFT/PENDING/APPROVED/EFFECTIVE/CANCELLED */
    private String status;
    private String createdBy;
    private LocalDateTime createdTime;
    private String approvedBy;
    private LocalDateTime approvedTime;
    private String activatedBy;
    private LocalDateTime activatedTime;
    private String remark;
}
