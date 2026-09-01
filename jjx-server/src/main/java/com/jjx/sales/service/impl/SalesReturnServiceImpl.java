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
import com.jjx.sales.enums.SalesReturnStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
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
        salesReturn.setTotalQuantity(params.get("totalQuantity") == null ? 0 : Integer.valueOf(params.get("totalQuantity").toString()));
        salesReturn.setTotalAmount(params.get("totalAmount") == null ? BigDecimal.ZERO : new BigDecimal(params.get("totalAmount").toString()));
        salesReturn.setRemark(params.get("remark") == null ? null : params.get("remark").toString());
        returnMapper.insert(salesReturn);

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
            Long inboundId = inboundService.create(inboundParams);
            if (inboundId != null) {
                inboundService.confirm(inboundId, SecurityUtils.getUserId(), receiverName == null ? "system" : receiverName);
                log.info("退货入库联动成功: returnNo={}, inboundId={}", salesReturn.getReturnNo(), inboundId);
            }
        } catch (Exception e) {
            log.error("退货入库联动失败: returnId={}, err={}", returnId, e.getMessage());
            throw new BusinessException("退货单已收货，但自动入库失败：" + e.getMessage());
        }
    }

    private java.util.Date parseDate(String value) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(value);
        } catch (Exception e) {
            return new Date();
        }
    }
}
