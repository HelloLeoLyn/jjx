package com.jjx.engineering.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
@Data @TableName("engineering_standard_process")
public class StandardProcess {
    @TableId(type = IdType.AUTO)
    private Long processId;
    private String processCode;
    private String processName;
    private String processType;
    private String processCategory;
    private BigDecimal standardLaborHours;
    private BigDecimal standardMachineHours;
    private Integer isEnabled;
    private Integer displayOrder;
    private String description;
}
