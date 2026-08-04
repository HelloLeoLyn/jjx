package com.jjx.engineering.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("engineering_film")
public class EngineeringFilm {
    
    @TableId(type = IdType.AUTO)
    private Long filmId;
    
    /**
     * 菲林编码
     */
    private String filmCode;
    
    /**
     * 菲林名称
     */
    private String filmName;
    
    /**
     * 菲林类型：OVERLAY/UPPER_CIRCUIT/SPACER/LOWER_CIRCUIT/BACK_ADHESIVE
     */
    private String filmType;
    
    /**
     * 关联产品ID
     */
    private Long productId;
    
    /**
     * 产品编码
     */
    private String productCode;
    
    /**
     * 产品名称
     */
    private String productName;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 是否当前版本：0-否,1-是
     */
    private Integer isCurrent;
    
    /**
     * 父菲林ID
     */
    private Long parentFilmId;
    
    /**
     * 菲林尺寸
     */
    private String filmSize;
    
    /**
     * 菲林厚度(mm)
     */
    private BigDecimal filmThickness;
    
    /**
     * 菲林材料
     */
    private String filmMaterial;
    
    /**
     * 颜色
     */
    private String color;
    
    /**
     * 文件ID
     */
    private Long fileId;
    
    /**
     * 文件路径
     */
    private String filePath;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 技术规格
     */
    private String technicalSpec;
    
    /**
     * 设计说明
     */
    private String designNotes;
    
    /**
     * 关联工序ID
     */
    private Long processId;
    
    /**
     * 关联工序编码
     */
    private String processCode;
    
    /**
     * 审核状态：1-草稿,2-待审核,3-已通过,4-已驳回
     */
    private Integer approveStatus;
    
    /**
     * 审核人ID
     */
    private Long approverId;
    
    /**
     * 审核人姓名
     */
    private String approverName;
    
    /**
     * 审核时间
     */
    private LocalDateTime approveTime;
    
    /**
     * 审核意见
     */
    private String approveRemark;
    
    /**
     * 设计人员ID
     */
    private Long designerId;
    
    /**
     * 设计人员姓名
     */
    private String designerName;
    
    /**
     * 设计完成时间
     */
    private LocalDateTime designTime;
    
    /**
     * 是否下发生产：0-否,1-是
     */
    private Integer isReleased;
    
    /**
     * 下发生产时间
     */
    private LocalDateTime releaseTime;
    
    /**
     * 下发人
     */
    private String releaseBy;
    
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    private String remark;
    
    @TableLogic
    private Integer deleted;
}