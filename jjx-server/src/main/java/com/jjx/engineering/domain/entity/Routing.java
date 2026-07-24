package com.jjx.engineering.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("engineering_routing")
public class Routing {
    @TableId(type = IdType.AUTO)
    private Long routingId;
    private String routingCode;
    private String routingName;
    private Long productId;
    private String routingType;
    private Integer status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
