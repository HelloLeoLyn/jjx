package com.jjx.purchase.domain.dto;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 供应商导入DTO
 */
@Data
public class SupplierImportDTO {

    /**
     * 供应商编码
     */
    @ExcelColumn(value = "供应商编码", order = 1, required = false, comment = "供应商唯一编码，留空由系统自动生成（SUP+5位流水）")
    private String supplierCode;

    /**
     * 供应商名称
     */
    @ExcelColumn(value = "供应商名称", order = 2, required = true, comment = "供应商全称")
    private String supplierName;


    @ExcelColumn(value = "供应商类型", order = 3, required = false, comment = "M=物料, E=设备, O=其他（留空默认M）")
    private String supplierType;

    /**
     * 供应商标签（系统标签，多个用 / 分隔；支持「大类*明细」两级，如 塑料制品*薄膜）
     */
    @ExcelColumn(value = "供应商标签", order = 4, comment = "多个用斜杠分隔；支持 大类*明细 两级，自动建档")
    private String supplierTags;

    /**
     * 联系人
     */
    @ExcelColumn(value = "联系人", order = 5, comment = "供应商联系人姓名")
    private String contactPerson;

    /**
     * 联系电话
     */
    @ExcelColumn(value = "联系电话", order = 6, comment = "供应商联系电话")
    private String phone;

    /**
     * 邮箱
     */
    @ExcelColumn(value = "邮箱", order = 7, comment = "供应商邮箱地址")
    private String email;

    /**
     * 地址
     */
    @ExcelColumn(value = "地址", order = 8, comment = "供应商详细地址")
    private String address;

    /**
     * 税号
     */
    @ExcelColumn(value = "税号", order = 9, comment = "纳税人识别号")
    private String taxNo;


    /**
     * 银行账号
     */
    @ExcelColumn(value = "银行账号", order = 10, comment = "银行账号")
    private String bankAccount;

    /**
     * 备注
     */
    @ExcelColumn(value = "备注", order = 11, comment = "备注信息")
    private String remark;
}
