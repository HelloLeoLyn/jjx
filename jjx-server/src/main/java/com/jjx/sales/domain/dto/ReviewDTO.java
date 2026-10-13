package com.jjx.sales.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;



@Data
@Schema(description = "审核DTO")
public class ReviewDTO {
    
    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderId;
    
    @Size(max = 500, message = "审核备注不能超过500个字符")
    @Schema(description = "审核备注")
    private String remark;

    @Schema(description = "附件ID列表（逗号分隔，选填——审核通过时上传确认书等资料）")
    private String attachments;

    @Schema(description = "本次订单审核附件引用 ID")
    private List<Long> attachmentRefIds;
}
