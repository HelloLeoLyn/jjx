package com.jjx.trace.bom;
import lombok.Data;
import java.time.LocalDateTime;
/** BOM版本档案/日志快照的只读投影，不增加持久字段。 */
@Data
public class BomTraceVersion {
    private Long bomId;
    private String bomCode;
    private String version;
    private String outputType;
    private Long outputId;
    private Long parentBomId;
    private LocalDateTime createTime;
    private String creatorName;
    private boolean deleted;
}
