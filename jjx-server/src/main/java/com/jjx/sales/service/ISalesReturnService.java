package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.dto.SalesReturnQueryDTO;
import com.jjx.sales.domain.entity.SalesReturn;

import java.util.List;
import java.util.Map;

public interface ISalesReturnService {

    /** 分页查询退货单 */
    PageResult<SalesReturn> page(SalesReturnQueryDTO query);

    /** 退货单详情 */
    SalesReturn getById(Long returnId);

    /**
     * 创建退货单（申请中）
     * params: orderId/returnDate/returnReason/returnType/totalQuantity/totalAmount/remark/items
     */
    Long create(Map<String, Object> params);

    /** 审核通过：申请中 → 已审核 */
    void approve(Long returnId, String approverName, String approveRemark);

    /** 审核驳回：申请中 → 申请中（记录驳回原因） */
    void reject(Long returnId, String approverName, String approveRemark);

    /** 收货确认：已审核 → 已收货，联动创建退货入库单（含明细） */
    void receive(Long returnId, String receiverName, String remark);

    /** 退款：已收货 → 已退款，回写订单付款状态 */
    void refund(Long returnId, java.math.BigDecimal refundAmount, String refundName);
}
