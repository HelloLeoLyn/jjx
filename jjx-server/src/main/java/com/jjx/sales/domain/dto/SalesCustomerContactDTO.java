package com.jjx.sales.domain.dto;

import com.jjx.common.validation.ValidContactPhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalesCustomerContactDTO {
    @NotBlank(message = "请输入联系人姓名")
    @Size(max = 50, message = "联系人姓名长度不能超过50个字符")
    private String contactPerson;
    @ValidContactPhone
    private String contactPhone;
    @Size(max = 255, message = "备注长度不能超过255个字符")
    private String remark;
}
