package com.jjx.engineering.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("engineering_film")
public class Film {
    @TableId(type = IdType.AUTO)
    private Long filmId;
    private String filmCode;
    private String filmName;
    private String filmType;
    private Long productId;
    private Integer status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
