package com.jjx.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 业务异常枚举
 */
@Getter
@RequiredArgsConstructor
public enum BusinessExceptionEnum {

    // ==================== 通用异常 (1000-1999) ====================
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后再试"),

    // 参数校验异常
    INVALID_PARAM(400, "请求参数错误"),
    PARAM_MISSING(400, "缺少必填参数"),
    PARAM_FORMAT_ERROR(400, "请求参数格式错误"),
    VALIDATION_FAILED(400, "参数校验失败"),

    // 权限异常
    UNAUTHORIZED(401, "请先登录"),
    NO_PERMISSION(403, "无权限访问"),

    // ==================== 订单异常 (2000-2999) ====================
    ORDER_NOT_FOUND(2001, "订单不存在"),
    ORDER_STATUS_ERROR(2002, "订单状态错误"),
    ORDER_CANNOT_CANCEL(2003, "订单无法取消"),
    ORDER_CANNOT_EDIT(2004, "订单无法编辑"),
    ORDER_CANNOT_SUBMIT(2005, "订单无法提交审核"),
    ORDER_CANNOT_APPROVE(2006, "订单无法审核通过"),
    ORDER_CANNOT_REJECT(2007, "订单无法审核驳回"),
    ORDER_REVIEW_REQUIRED(2008, "请填写驳回原因"),
    ORDER_ALREADY_COMPLETED(2009, "订单已完成"),
    ORDER_ALREADY_CANCELLED(2010, "订单已取消"),
    ORDER_NUM_DUPLICATE(2011, "订单号重复"),

    // ==================== 客户异常 (3000-3999) ====================
    CUSTOMER_NOT_FOUND(3001, "客户不存在"),
    CUSTOMER_DUPLICATE(3002, "客户已存在"),
    CUSTOMER_DISABLED(3003, "客户已禁用"),
    CUSTOMER_CREDIT_LIMIT_EXCEEDED(3004, "客户信用额度不足"),

    // ==================== 产品异常 (4000-4099) ====================
    PRODUCT_NOT_FOUND(4001, "产品不存在"),
    PRODUCT_OUT_OF_STOCK(4002, "产品库存不足"),
    PRODUCT_DUPLICATE(4003, "产品已存在"),
    PRODUCT_CODE_DUPLICATE(4004, "产品编码已存在"),
    PRODUCT_CATEGORY_NOT_FOUND(4005, "产品分类不存在"),
    PRODUCT_ALREADY_RELEASED(4006, "产品已发布，无法修改"),
    PRODUCT_ALREADY_OBSOLETE(4007, "产品已停产"),
    PRODUCT_CANNOT_DELETE(4008, "产品已被使用，无法删除"),
    PRODUCT_CANNOT_SUBMIT(4009, "产品状态有误，无法提交审核"),
    PRODUCT_CANNOT_APPROVED(4010, "产品状态有误，无法审核通过"),
    PRODUCT_CANNOT_REJECT(4011, "产品状态有误，无法驳回审核"),
    PRODUCT_CANNOT_CANCEL(4012, "产品状态有误，无法取消"),

    // ==================== 产品BOM异常 (4100-4199) ====================
    BOM_NOT_FOUND(4101, "BOM不存在"),
    BOM_DUPLICATE(4102, "BOM已存在"),
    BOM_CODE_DUPLICATE(4103, "BOM编码已存在"),
    BOM_VERSION_DUPLICATE(4104, "BOM版本已存在"),
    BOM_ALREADY_APPROVED(4105, "BOM已批准，无法修改"),
    BOM_ALREADY_OBSOLETE(4106, "BOM已作废，无法操作"),
    BOM_CANNOT_DELETE(4107, "BOM已被使用，无法删除"),
    BOM_CANNOT_EDIT(4108, "BOM当前状态不可编辑"),
    BOM_APPROVE_FAILED(4109, "BOM审批失败"),
    BOM_REJECT_FAILED(4110, "BOM驳回失败"),
    BOM_VERSION_EXISTS(4111, "BOM版本已存在"),
    BOM_NO_CURRENT_VERSION(4112, "BOM没有当前版本"),
    BOM_MATERIAL_NOT_FOUND(4113, "BOM物料不存在"),
    BOM_MATERIAL_DUPLICATE(4114, "BOM物料已存在"),
    BOM_MATERIAL_QUANTITY_INVALID(4115, "BOM物料数量无效"),
    BOM_STRUCTURE_INVALID(4116, "BOM结构无效，存在循环引用"),
    BOM_DEPTH_EXCEEDED(4117, "BOM层级深度超限"),
    BOM_MATERIAL_OUT_OF_STOCK(4118, "BOM物料库存不足"),
    BOM_COST_CALCULATE_FAILED(4119, "BOM成本计算失败"),
    BOM_EXPORT_FAILED(4120, "BOM导出失败"),
    BOM_IMPORT_FAILED(4121, "BOM导入失败"),
    BOM_TEMPLATE_NOT_FOUND(4122, "BOM模板不存在"),
    BOM_REVISION_NOT_ALLOWED(4123, "当前状态不允许修订BOM"),
    BOM_ECO_NOT_FOUND(4124, "工程变更单不存在"),
    BOM_ECO_NOT_APPROVED(4125, "工程变更单未批准"),
    BOM_ECO_ALREADY_APPLIED(4126, "工程变更单已应用"),
    BOM_COMPARE_FAILED(4127, "BOM版本对比失败"),
    BOM_WHERE_USED_NOT_FOUND(4128, "未找到BOM使用位置"),
    BOM_REFERENCE_EXISTS(4129, "BOM存在引用，无法删除"),
    BOM_APPROVAL_IN_PROGRESS(4130, "BOM审批中，无法操作"),
    BOM_REJECT_REASON_REQUIRED(4131, "驳回原因不能为空"),
    BOM_APPROVE_REMARK_REQUIRED(4132, "审批意见不能为空"),
    BOM_VERSION_INVALID(4133, "BOM版本格式无效"),
    BOM_EFFECTIVE_DATE_INVALID(4134, "BOM生效日期无效"),
    BOM_EXPIRY_DATE_INVALID(4135, "BOM失效日期无效"),
    BOM_EFFECTIVE_DATE_EXCEEDED(4136, "BOM生效日期不能大于失效日期"),
    BOM_ALTERNATIVE_NOT_FOUND(4137, "替代料不存在"),
    BOM_ALTERNATIVE_RATIO_INVALID(4138, "替代料比例无效"),
    BOM_SUBSTITUTE_GROUP_NOT_FOUND(4139, "替代组不存在"),
    BOM_PHANTOM_NOT_ALLOWED(4140, "虚拟件不允许在此层级使用"),
    BOM_ITEM_QUANTITY_ZERO(4141, "BOM物料数量不能为0"),
    BOM_ITEM_SCRAP_RATE_INVALID(4142, "BOM物料损耗率无效"),
    BOM_LEAD_TIME_INVALID(4143, "BOM提前期无效"),
    BOM_MATERIAL_TYPE_INVALID(4144, "BOM物料类型无效"),
    BOM_UNIT_CONVERSION_FAILED(4145, "BOM单位转换失败"),
    BOM_SUPPLIER_NOT_FOUND(4146, "BOM供应商不存在"),
    BOM_PURCHASE_PRICE_NOT_FOUND(4147, "BOM采购价格未维护"),
    BOM_MANUFACTURING_COST_NOT_FOUND(4148, "BOM制造成本未维护"),
    BOM_ROUTING_NOT_FOUND(4149, "BOM关联工艺路线不存在"),
    BOM_ROUTING_MISMATCH(4150, "BOM与工艺路线不匹配"),

    // ==================== 产品菲林异常 (4200-4299) ====================
    FILM_NOT_FOUND(4201, "菲林不存在"),
    FILM_CODE_DUPLICATE(4202, "菲林编码已存在"),
    FILM_VERSION_DUPLICATE(4203, "菲林版本已存在"),
    FILM_ALREADY_APPROVED(4204, "菲林已批准，无法修改"),
    FILM_CANNOT_EDIT(4205, "菲林当前状态不可编辑"),
    FILM_CANNOT_DELETE(4206, "菲林已被使用，无法删除"),
    FILM_NO_CURRENT_VERSION(4207, "菲林没有当前版本"),
    FILM_TYPE_NOT_FOUND(4208, "菲林类型不存在"),
    FILM_TYPE_INVALID(4209, "菲林类型无效"),
    FILM_VERSION_INVALID(4210, "菲林版本格式无效"),
    FILM_NOT_RELEASED(4211, "菲林未下发生产"),
    FILM_ALREADY_RELEASED(4212, "菲林已下发生产"),
    FILM_UPLOAD_FAILED(4213, "菲林文件上传失败"),
    FILM_FILE_FORMAT_ERROR(4214, "菲林文件格式错误"),
    FILM_APPROVE_FAILED(4215, "菲林审批失败"),
    FILM_REJECT_FAILED(4216, "菲林驳回失败"),
    FILM_REJECT_REASON_REQUIRED(4217, "菲林驳回原因不能为空"),

    // ==================== 工艺路线异常 (4300-4399) ====================
    ROUTING_NOT_FOUND(4301, "工艺路线不存在"),
    ROUTING_CODE_DUPLICATE(4302, "工艺路线编码已存在"),
    ROUTING_VERSION_DUPLICATE(4303, "工艺路线版本已存在"),
    ROUTING_ALREADY_APPROVED(4304, "工艺路线已批准，无法修改"),
    ROUTING_CANNOT_EDIT(4305, "工艺路线当前状态不可编辑"),
    ROUTING_NO_CURRENT_VERSION(4306, "工艺路线没有当前版本"),
    ROUTING_STRUCTURE_INVALID(4307, "工艺路线结构无效"),
    ROUTING_PROCESS_NOT_FOUND(4308, "工艺路线工序不存在"),
    ROUTING_PROCESS_DISABLED(4309, "工艺路线工序已禁用"),
    ROUTING_PROCESS_ORDER_INVALID(4310, "工艺路线工序顺序无效"),
    ROUTING_CALCULATE_HOURS_FAILED(4311, "工时计算失败"),
    ROUTING_VALIDATE_FAILED(4312, "工艺路线验证失败"),
    ROUTING_ITEM_NOT_FOUND(4313, "工艺路线明细不存在"),
    ROUTING_ITEM_DUPLICATE(4314, "工艺路线明细已存在"),
    ROUTING_PROCESS_ALREADY_EXISTS(4315, "工序已在路线中"),
    ROUTING_DEPTH_EXCEEDED(4316, "工艺路线层级深度超限"),
    ROUTING_CYCLE_DETECTED(4317, "工艺路线存在循环依赖"),

    // ==================== 文件异常 (5000-5999) ====================
    FILE_UPLOAD_FAILED(5001, "文件上传失败"),
    FILE_DOWNLOAD_FAILED(5002, "文件下载失败"),
    FILE_FORMAT_ERROR(5003, "文件格式错误"),
    FILE_SIZE_EXCEEDED(5004, "文件大小超限"),

    // ==================== 数据库异常 (6000-6999) ====================
    DB_INSERT_FAILED(6001, "数据插入失败"),
    DB_UPDATE_FAILED(6002, "数据更新失败"),
    DB_DELETE_FAILED(6003, "数据删除失败"),
    DB_DUPLICATE_KEY(6004, "数据已存在"),

    // ==================== 第三方接口异常 (7000-7999) ====================
    WECHAT_API_ERROR(7001, "企业微信接口调用失败"),
    SMS_SEND_FAILED(7002, "短信发送失败"),
    EMAIL_SEND_FAILED(7003, "邮件发送失败"),
    DINGTALK_API_ERROR(7004, "钉钉接口调用失败"),

    // ==================== 日志异常 (8000-8999) ====================
    LOG_SAVE_FAILED(8001, "日志保存失败"),

    // ==================== 业务异常 (9000-9999) ====================
    BATCH_SIZE_EXCEEDED(9001, "批量操作数量超限"),
    DATA_CONFLICT(9002, "数据冲突"),
    OPERATION_NOT_ALLOWED(9003, "操作不允许"),
    REMOTE_CALL_FAILED(9004, "远程调用失败");

    private final Integer code;
    private final String message;
}
