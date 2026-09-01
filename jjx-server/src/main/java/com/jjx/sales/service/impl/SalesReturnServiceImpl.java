package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.service.InventoryInboundService;
import com.jjx.sales.domain.dto.SalesReturnQueryDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesReturn;
import com.jjx.sales.domain.entity.SalesReturnItem;
import com.jjx.sales.enums.SalesReturnStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesReturnItemMapper;
import com.jjx.sales.mapper.SalesReturnMapper;
import com.jjx.sales.service.ISalesReturnService;
import com.jjx.system.service.ReviewFlowService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 销售退货单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesReturnServiceImpl extends ServiceImpl<SalesReturnMapper, SalesReturn> implements ISalesReturnService {

    private final SalesReturnMapper returnMapper;
    private final SalesReturnItemMapper returnItemMapper;
    private final OrderMapper orderMapper;
    private final RedisSequenceService redisSequenceService;
    private final ReviewFlowService reviewFlowService;
    private final InventoryInboundService inboundService;

    @Override
    public PageResult<SalesReturn> page(SalesReturnQueryDTO query) {
        LambdaQueryWrapper<SalesReturn> wrapper = new LambdaQueryWrapper<SalesReturn>()
                .like(query.getReturnNo() != null && !query.getReturnNo().isBlank(),
                        SalesReturn::getReturnNo, query.getReturnNo())
                .eq(query.getOrderId() != null, SalesReturn::getOrderId, query.getOrderId())
                .like(query.getCustomerName() != null && !query.getCustomerName().isBlank(),
                        SalesReturn::getCustomerName, query.getCustomerName())
                .eq(query.getReturnStatus() != null, SalesReturn::getReturnStatus, query.getReturnStatus())
                .ge(query.getReturnDateStart() != null, SalesReturn::getReturnDate, query.getReturnDateStart())
                .le(query.getReturnDateEnd() != null, SalesReturn::getReturnDate, query.getReturnDateEnd())
                .orderByDesc(SalesReturn::getCreateTime).orderByDesc(SalesReturn::getReturnId);
        Page<SalesReturn> p = returnMapper.selectPage(
                new Page<>(query.getPageNum() == null ? 1 : query.getPageNum(),
                        query.getPageSize() == null ? 10 : query.getPageSize()),
                wrapper);
        return PageResult.of(p, p.getRecords());
    }

    @Override
    public SalesReturn getById(Long returnId) {
        SalesReturn salesReturn = returnMapper.selectById(returnId);
        if (salesReturn == null) {
            throw new BusinessException("退货单不存在");
        }
        return salesReturn;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> params) {
        Long orderId = params.get("orderId") == null ? null : Long.valueOf(params.get("orderId").toString());
        if (orderId == null) {
            throw new BusinessException("请选择销售订单");
        }
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("销售订单不存在");
        }

        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setReturnNo(redisSequenceService.generateBusinessNumberByType("sales_return", "RTN", "yyMMdd", 3));
        salesReturn.setOrderId(orderId);
        salesReturn.setCustomerId(order.getCustomerId());
        salesReturn.setCustomerName(order.getCustomerName());
        salesReturn.setReturnDate(params.get("returnDate") == null ? new Date() : parseDate(params.get("returnDate").toString()));
        salesReturn.setReturnReason(params.get("returnReason") == null ? null : params.get("returnReason").toString());
        salesReturn.setReturnType(params.get("returnType") == null ? 5 : Integer.valueOf(params.get("returnType").toString()));
        salesReturn.setReturnStatus(SalesReturnStatusEnum.APPLYING.getValue());
        returnMapper.insert(salesReturn);
        // 汇总：优先按明细计算，无明细时用传入值
        List<Map<String, Object>> items = parseItems(params.get("items"));
        if (!items.isEmpty()) {
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmt = BigDecimal.ZERO;
            int sort = 1;
            for (Map<String, Object> m : items) {
                BigDecimal qty = m.get("quantity") == null ? BigDecimal.ZERO : new BigDecimal(m.get("quantity").toString());
                BigDecimal price = m.get("unitPrice") == null ? BigDecimal.ZERO : new BigDecimal(m.get("unitPrice").toString());
                SalesReturnItem item = new SalesReturnItem();
                item.setReturnId(salesReturn.getReturnId());
                if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
                item.setMaterialCode((String) m.get("materialCode"));
                item.setMaterialName((String) m.get("materialName"));
                item.setMaterialSpec((String) m.get("materialSpec"));
                item.setUnit((String) m.get("unit"));
                item.setQuantity(qty);
                item.setUnitPrice(price);
                item.setAmount(qty.multiply(price));
                item.setRemark((String) m.get("remark"));
                returnItemMapper.insert(item);
                totalQty = totalQty.add(qty);
                totalAmt = totalAmt.add(item.getAmount());
            }
            salesReturn.setTotalQuantity(totalQty.intValue());
            salesReturn.setTotalAmount(totalAmt);
        } else {
            salesReturn.setTotalQuantity(params.get("totalQuantity") == null ? 0 : Integer.valueOf(params.get("totalQuantity").toString()));
            salesReturn.setTotalAmount(params.get("totalAmount") == null ? BigDecimal.ZERO : new BigDecimal(params.get("totalAmount").toString()));
        }
        salesReturn.setRemark(params.get("remark") == null ? null : params.get("remark").toString());
        returnMapper.updateById(salesReturn);

        reviewFlowService.record("sales_return", salesReturn.getReturnId(), "SUBMIT", "提交退货申请",
                SalesReturnStatusEnum.APPLYING.getValue(), SalesReturnStatusEnum.APPLYING.getValue(),
                "创建退货单", null);
        log.info("创建退货单: returnNo={}, orderId={}", salesReturn.getReturnNo(), orderId);
        return salesReturn.getReturnId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long returnId, String approverName, String approveRemark) {
        SalesReturn salesReturn = getById(returnId);
        if (!SalesReturnStatusEnum.APPLYING.getValue().equals(salesReturn.getReturnStatus())) {
            throw new BusinessException("仅申请中的退货单可审核通过");
        }
        salesReturn.setReturnStatus(SalesReturnStatusEnum.APPROVED.getValue());
        salesReturn.setApproverId(SecurityUtils.getUserId());
        salesReturn.setApproverName(approverName == null || approverName.isBlank()
                ? SecurityUtils.getRealName() : approverName);
        salesReturn.setApproveTime(new Date());
        salesReturn.setApproveRemark(approveRemark);
        returnMapper.updateById(salesReturn);
        reviewFlowService.record("sales_return", returnId, "APPROVE", "审核通过",
                SalesReturnStatusEnum.APPLYING.getValue(), SalesReturnStatusEnum.APPROVED.getValue(),
                approveRemark, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long returnId, String approverName, String approveRemark) {
        SalesReturn salesReturn = getById(returnId);
        if (!SalesReturnStatusEnum.APPLYING.getValue().equals(salesReturn.getReturnStatus())) {
            throw new BusinessException("仅申请中的退货单可驳回");
        }
        salesReturn.setApproverId(SecurityUtils.getUserId());
        salesReturn.setApproverName(approverName == null || approverName.isBlank()
                ? SecurityUtils.getRealName() : approverName);
        salesReturn.setApproveTime(new Date());
        salesReturn.setApproveRemark(approveRemark);
        returnMapper.updateById(salesReturn);
        reviewFlowService.record("sales_return", returnId, "REJECT", "审核驳回",
                SalesReturnStatusEnum.APPLYING.getValue(), SalesReturnStatusEnum.APPLYING.getValue(),
                approveRemark, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long returnId, String receiverName, String remark) {
        SalesReturn salesReturn = getById(returnId);
        if (!SalesReturnStatusEnum.APPROVED.getValue().equals(salesReturn.getReturnStatus())) {
            throw new BusinessException("仅已审核的退货单可收货");
        }
        salesReturn.setReturnStatus(SalesReturnStatusEnum.RECEIVED.getValue());
        salesReturn.setReceiveTime(new Date());
        salesReturn.setReceiveBy(SecurityUtils.getUserId());
        salesReturn.setReceiveName(receiverName == null || receiverName.isBlank()
                ? SecurityUtils.getRealName() : receiverName);
        if (remark != null && !remark.isBlank()) {
            salesReturn.setRemark(remark);
        }
        returnMapper.updateById(salesReturn);

        // 联动创建退货入库单（填实 sales_return 入库来源），确认入库加回库存
        try {
            Map<String, Object> inboundParams = new java.util.HashMap<>();
            inboundParams.put("sourceType", "sales_return");
            inboundParams.put("sourceId", returnId);
            inboundParams.put("sourceNo", salesReturn.getReturnNo());
            inboundParams.put("inboundType", "sales_return");
            inboundParams.put("customerId", salesReturn.getCustomerId());
            inboundParams.put("customerName", salesReturn.getCustomerName());
            inboundParams.put("inboundDate", salesReturn.getReturnDate() == null
                    ? java.time.LocalDate.now().toString() : new java.text.SimpleDateFormat("yyyy-MM-dd").format(salesReturn.getReturnDate()));
            inboundParams.put("remark", "销售退货自动入库：" + salesReturn.getReturnNo());
            // 明细行：退货明细 → 入库明细（确认入库时加回库存）
            List<SalesReturnItem> returnItems = returnItemMapper.selectList(
                    new LambdaQueryWrapper<SalesReturnItem>().eq(SalesReturnItem::getReturnId, returnId));
            if (!returnItems.isEmpty()) {
                List<Map<String, Object>> inboundItems = new java.util.ArrayList<>();
                for (SalesReturnItem ri : returnItems) {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("materialId", ri.getMaterialId());
                    m.put("materialCode", ri.getMaterialCode());
                    m.put("materialName", ri.getMaterialName());
                    m.put("specification", ri.getMaterialSpec());
                    m.put("unit", ri.getUnit());
                    m.put("quantity", ri.getQuantity());
                    m.put("unitPrice", ri.getUnitPrice());
                    inboundItems.add(m);
                }
                inboundParams.put("items", inboundItems);
            }
            Long inboundId = inboundService.create(inboundParams);
            if (inboundId != null) {
                inboundService.confirm(inboundId, SecurityUtils.getUserId(), receiverName == null ? "system" : receiverName);
                log.info("退货入库联动成功: returnNo={}, inboundId={}, items={}", salesReturn.getReturnNo(), inboundId, returnItems.size());
            }
        } catch (Exception e) {
            log.error("退货入库联动失败: returnId={}, err={}", returnId, e.getMessage());
            throw new BusinessException("退货单已收货，但自动入库失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long returnId, BigDecimal refundAmount, String refundName) {
        SalesReturn salesReturn = getById(returnId);
        if (!SalesReturnStatusEnum.RECEIVED.getValue().equals(salesReturn.getReturnStatus())) {
            throw new BusinessException("仅已收货的退货单可退款");
        }
        salesReturn.setReturnStatus(SalesReturnStatusEnum.REFUNDED.getValue());
        salesReturn.setRefundTime(new Date());
        salesReturn.setRefundBy(SecurityUtils.getUserId());
        salesReturn.setRefundName(refundName == null || refundName.isBlank()
                ? SecurityUtils.getRealName() : refundName);
        if (refundAmount != null) {
            salesReturn.setRefundAmount(refundAmount);
        }
        returnMapper.updateById(salesReturn);
        reviewFlowService.record("sales_return", returnId, "REFUND", "退款",
                SalesReturnStatusEnum.RECEIVED.getValue(), SalesReturnStatusEnum.REFUNDED.getValue(),
                refundAmount == null ? null : "退款金额：" + refundAmount.toPlainString(), null);

        // 回写订单付款状态：已收扣减退款，重算未收与支付状态
        if (salesReturn.getOrderId() != null) {
            writebackOrderPayment(salesReturn.getOrderId(), refundAmount == null ? BigDecimal.ZERO : refundAmount);
        }
    }

    /** 退款回写订单付款状态（对照收款回写 052 口径：paid_amount 扣减退款） */
    private void writebackOrderPayment(Long orderId, BigDecimal refundAmount) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("退货退款回写的订单不存在: orderId={}", orderId);
            return;
        }
        BigDecimal paid = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        BigDecimal newPaid = paid.subtract(refundAmount).max(BigDecimal.ZERO);
        BigDecimal target = order.getFinalAmount() != null
                ? order.getFinalAmount()
                : (order.getTotalAmountWithTax() != null ? order.getTotalAmountWithTax() : order.getTotalAmount());
        Integer paymentStatus = newPaid.compareTo(BigDecimal.ZERO) <= 0
                ? com.jjx.sales.enums.SalesPaymentStatusEnum.UNPAID.getValue()
                : target != null && newPaid.compareTo(target) >= 0
                        ? com.jjx.sales.enums.SalesPaymentStatusEnum.PAID.getValue()
                        : com.jjx.sales.enums.SalesPaymentStatusEnum.PARTIAL_PAID.getValue();
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setPaymentStatus(paymentStatus);
        update.setPaidAmount(newPaid);
        if (target != null) {
            update.setUnpaidAmount(target.subtract(newPaid).max(BigDecimal.ZERO));
        }
        orderMapper.updateById(update);
        log.info("退货退款回写订单付款状态: orderId={}, newPaid={}, paymentStatus={}", orderId, newPaid, paymentStatus);
    }

    /** 解析 items 参数（List<Map>） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseItems(Object itemsObj) {
        if (itemsObj instanceof List<?> list) {
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) {
                    result.add((Map<String, Object>) m);
                }
            }
            return result;
        }
        return new java.util.ArrayList<>();
    }

    private java.util.Date parseDate(String value) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(value);
        } catch (Exception e) {
            return new Date();
        }
    }
}
