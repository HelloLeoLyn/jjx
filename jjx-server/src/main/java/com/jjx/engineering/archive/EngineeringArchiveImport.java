package com.jjx.engineering.archive;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("engineering_archive_import")
public class EngineeringArchiveImport {
    @TableId(type = IdType.AUTO)
    private Long archiveId;
    private String fileName;
    private String filePath;
    private String fileHash;
    private Integer recognizeStatus;
    private String recognizeMessage;
    private String productName;
    private String productCode;
    private String extractedJson;
    private Long productId;
    private Long bomId;
    private Long routingId;
    @TableField(exist = false)
    private Boolean overwriteAllowed;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
