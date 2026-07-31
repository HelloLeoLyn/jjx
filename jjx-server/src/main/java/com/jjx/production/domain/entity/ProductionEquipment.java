package com.jjx.production.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("production_equipment")
public class ProductionEquipment {
    @TableId(type = IdType.AUTO)
    private Long equipmentId;
    private String equipmentNo;
    private String equipmentName;
    private String equipmentType;
    private String model;
    private String department;
    private String location;
    private Integer status;
    private java.math.BigDecimal utilization;
    private LocalDateTime lastMaintenance;
    private LocalDateTime nextMaintenance;
    private String remark;
    @TableLogic private String delFlag;
    private String createBy;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.UPDATE) private LocalDateTime updateTime;
}
