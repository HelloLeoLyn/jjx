package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesQuotationItem;
import com.jjx.sales.domain.entity.SalesQuotationFlow;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.sales.mapper.SalesQuotationItemMapper;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.service.IOrderService;
import com.jjx.sales.service.IQuotationService;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.system.annotation.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 销售报价单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationServiceImpl implements IQuotationService {

    private final QuotationMapper quotationMapper;
    private final QuotationFlowMapper quotationFlowMapper;
    private final SalesQuotationItemMapper quotationItemMapper;
    private final IOrderService orderService;

    /**
     * 查询销售报价单列表
     */
    @Override
    public List<SalesQuotation> selectQuotationList(SalesQuotation quotation) {
        LambdaQueryWrapper<SalesQuotation> wrapper = buildQueryWrapper(quotation);
        return quotationMapper.selectList(wrapper);
    }

    @Override
    public PageResult<SalesQuotation> selectQuotationPage(SalesQuotation quotation, Integer pageNum, Integer pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SalesQuotation> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SalesQuotation> wrapper = buildQueryWrapper(quotation);
        com.baomidou.mybatisplus.core.metadata.IPage<SalesQuotation> result = quotationMapper.selectPage(page, wrapper);
        return com.jjx.common.core.page.PageResult.build(result.getRecords(), result.getTotal());
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SalesQuotation> buildQueryWrapper(SalesQuotation quotation) {
        LambdaQueryWrapper<SalesQuotation> wrapper = Wrappers.lambdaQuery();

        // 根据报价单号查询
        if (quotation.getQuotationNo() != null && !quotation.getQuotationNo().isEmpty()) {
            wrapper.like(SalesQuotation::getQuotationNo, quotation.getQuotationNo());
        }

        // 根据客户名称查询
        if (quotation.getCustomerName() != null && !quotation.getCustomerName().isEmpty()) {
            wrapper.like(SalesQuotation::getCustomerName, quotation.getCustomerName());
        }

        // 根据报价状态查询
        if (quotation.getQuotationStatus() != null) {
            wrapper.eq(SalesQuotation::getQuotationStatus, quotation.getQuotationStatus());
        }

        // 根据销售员查询
        if (quotation.getSalesPersonId() != null) {
            wrapper.eq(SalesQuotation::getSalesPersonId, quotation.getSalesPersonId());
        }

        // 未删除的数据
        wrapper.eq(SalesQuotation::getDeleted, 0);

        // 按创建时间倒序排序
        wrapper.orderByDesc(SalesQuotation::getCreateTime);

        return wrapper;
    }

    /**
     * 根据ID查询销售报价单
     */
    @Override
    public SalesQuotation selectQuotationById(Long quotationId) {
        SalesQuotation quotation = quotationMapper.selectById(quotationId);
        if (quotation == null || quotation.getDeleted() == 1) {
            throw new BusinessException("报价单不存在或已被删除");
        }
        fillQuotationItems(quotation);
        return quotation;
    }

    /**
     * 填充报价单明细
     */
    private void fillQuotationItems(SalesQuotation quotation) {
        if (quotation == null) return;
        List<SalesQuotationItem> items = quotationItemMapper.selectList(
                new LambdaQueryWrapper<SalesQuotationItem>()
                        .eq(SalesQuotationItem::getQuotationId, quotation.getQuotationId())
                        .orderByAsc(SalesQuotationItem::getItemOrder)
        );
        quotation.setItems(items);
    }

    /**
     * 新增销售报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertQuotation(SalesQuotation quotation) {
        // 检查报价单号是否唯一
        if (!checkQuotationNoUnique(quotation.getQuotationNo())) {
            throw new BusinessException("报价单号已存在");
        }

        // 自动生成链路追踪ID（未传入时）
        if (quotation.getTraceId() == null || quotation.getTraceId().isEmpty()) {
            quotation.setTraceId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        }

        // 设置默认值
        if (quotation.getQuotationStatus() == null) {
            quotation.setQuotationStatus(QuotationStatus.DRAFT.getCode()); // 草稿状态
        }

        if (quotation.getCurrency() == null || quotation.getCurrency().isEmpty()) {
            quotation.setCurrency("CNY");
        }

        if (quotation.getExchangeRate() == null) {
            quotation.setExchangeRate(java.math.BigDecimal.valueOf(1.0000));
        }

        if (quotation.getSubtotalAmount() == null) {
            quotation.setSubtotalAmount(java.math.BigDecimal.ZERO);
        }

        if (quotation.getTotalAmount() == null) {
            quotation.setTotalAmount(java.math.BigDecimal.ZERO);
        }

        if (quotation.getFinalAmount() == null) {
            quotation.setFinalAmount(java.math.BigDecimal.ZERO);
        }

        if (quotation.getTaxRate() == null) {
            quotation.setTaxRate(java.math.BigDecimal.ZERO);
        }

        if (quotation.getTaxAmount() == null) {
            quotation.setTaxAmount(java.math.BigDecimal.ZERO);
        }

        if (quotation.getDiscountAmount() == null) {
            quotation.setDiscountAmount(java.math.BigDecimal.ZERO);
        }

        int rows = quotationMapper.insert(quotation);
        // 保存报价单明细
        saveQuotationItems(quotation.getQuotationId(), quotation.getItems());
        return rows;
    }

    /**
     * 修改销售报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateQuotation(SalesQuotation quotation) {
        // 检查报价单是否存在
        SalesQuotation existingQuotation = selectQuotationById(quotation.getQuotationId());
        if (existingQuotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 检查报价单号是否唯一（排除自身）
        if (quotation.getQuotationNo() != null && !quotation.getQuotationNo().equals(existingQuotation.getQuotationNo())) {
            if (!checkQuotationNoUnique(quotation.getQuotationNo())) {
                throw new BusinessException("报价单号已存在");
            }
        }

        int rows = quotationMapper.updateById(quotation);
        // 先删后插，更新报价单明细
        quotationItemMapper.delete(new LambdaQueryWrapper<SalesQuotationItem>()
                .eq(SalesQuotationItem::getQuotationId, quotation.getQuotationId()));
        saveQuotationItems(quotation.getQuotationId(), quotation.getItems());
        return rows;
    }

    /**
     * 批量保存报价单明细
     */
    private void saveQuotationItems(Long quotationId, List<SalesQuotationItem> items) {
        if (quotationId == null || items == null || items.isEmpty()) {
            return;
        }
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (SalesQuotationItem item : items) {
            item.setItemId(null);
            item.setQuotationId(quotationId);
            if (item.getQuantity() == null) item.setQuantity(1);
            if (item.getUnitPrice() == null) item.setUnitPrice(java.math.BigDecimal.ZERO);
            // 自动计算行金额 = 数量 × 单价
            if (item.getAmount() == null) {
                item.setAmount(java.math.BigDecimal.valueOf(item.getQuantity().longValue())
                        .multiply(item.getUnitPrice()));
            }
            if (item.getUnit() == null) item.setUnit("PCS");
            if (item.getItemOrder() == null) item.setItemOrder(0);
            quotationItemMapper.insert(item);
            total = total.add(item.getAmount() != null ? item.getAmount() : java.math.BigDecimal.ZERO);
        }
        // 自动汇总报价单金额
        try {
            SalesQuotation q = new SalesQuotation();
            q.setQuotationId(quotationId);
            q.setSubtotalAmount(total);
            q.setTotalAmount(total);
            q.setFinalAmount(total);
            quotationMapper.updateById(q);
        } catch (Exception e) {
            log.warn("汇总报价单金额失败: {}", e.getMessage());
        }
    }

    /**
     * 删除销售报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteQuotationById(Long quotationId) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 检查报价单状态，已发送/已确认/已完成/改单中的报价单不能删除
        if (QuotationStatus.SENT.getCode().equals(quotation.getQuotationStatus())
                || QuotationStatus.ACCEPTED.getCode().equals(quotation.getQuotationStatus())
                || QuotationStatus.COMPLETED.getCode().equals(quotation.getQuotationStatus())
                || QuotationStatus.MODIFYING.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("已发送、已确认、已完成或改单中的报价单不能删除");
        }

        // 使用逻辑删除
        SalesQuotation deleteQuotation = new SalesQuotation();
        deleteQuotation.setQuotationId(quotationId);
        deleteQuotation.setDeleted(1);
        return quotationMapper.updateById(deleteQuotation);
    }

    /**
     * 批量删除销售报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteQuotationByIds(Long[] quotationIds) {
        int count = 0;
        for (Long quotationId : quotationIds) {
            count += deleteQuotationById(quotationId);
        }
        return count;
    }

    /**
     * 检查报价单号是否存在
     */
    @Override
    public boolean checkQuotationNoUnique(String quotationNo) {
        LambdaQueryWrapper<SalesQuotation> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SalesQuotation::getQuotationNo, quotationNo)
               .eq(SalesQuotation::getDeleted, 0);
        Long count = quotationMapper.selectCount(wrapper);
        return count == 0;
    }

    /**
     * 记录状态流转
     */
    private void recordFlow(SalesQuotation quotation, String actionCode, String actionName,
                            Integer fromStatus, Integer toStatus, String remark, String attachmentIds) {
        try {
            SalesQuotationFlow flow = new SalesQuotationFlow();
            flow.setQuotationId(quotation.getQuotationId());
            flow.setActionCode(actionCode);
            flow.setActionName(actionName);
            flow.setFromStatus(fromStatus);
            flow.setToStatus(toStatus);
            flow.setOperatorName("系统");
            flow.setRemark(remark);
            flow.setAttachmentIds(attachmentIds);
            flow.setCreateTime(LocalDateTime.now());
            quotationFlowMapper.insert(flow);
        } catch (Exception e) {
            log.error("记录报价单流转失败: quotationId={}, action={}, {}", quotation.getQuotationId(), actionCode, e.getMessage());
        }
    }

    /**
     * 更新报价单状态（客户确认/拒绝，兼容旧调用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateQuotationStatus(Long quotationId, Integer status, String attachmentIds) {
        if (status != null && status == 2) {
            return confirmQuotation(quotationId, attachmentIds);
        }
        if (status != null && status == 3) {
            return rejectQuotation(quotationId, attachmentIds);
        }
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }
        validateStatusTransition(quotation.getQuotationStatus(), status);
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(status);
        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, "STATUS_CHANGE", "状态变更", from, status, null, attachmentIds);
        return rows;
    }

    /**
     * 客户确认报价
     */
    @Override
    @Event(value = "quotation.confirmed", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int confirmQuotation(Long quotationId, String attachmentIds) {
        return changeToStatus(quotationId, QuotationStatus.ACCEPTED.getCode(), "CUSTOMER_CONFIRM", "客户确认报价", attachmentIds);
    }

    /**
     * 客户拒绝报价
     */
    @Override
    @Event(value = "quotation.rejected", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int rejectQuotation(Long quotationId, String attachmentIds) {
        return changeToStatus(quotationId, QuotationStatus.REJECTED.getCode(), "CUSTOMER_REJECT", "客户拒绝报价", attachmentIds);
    }

    /**
     * 已完成报价单改单（回到改单状态，可重新编辑流转）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int modifyQuotation(Long quotationId, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }
        // 只有已完成状态可以改单
        if (!QuotationStatus.COMPLETED.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有已完成的报价单可以改单");
        }
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(QuotationStatus.MODIFYING.getCode());
        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, "MODIFY", "改单", from, QuotationStatus.MODIFYING.getCode(), null, attachmentIds);
        return rows;
    }

    /**
     * 通用状态变更 + 流转记录
     */
    private int changeToStatus(Long quotationId, Integer targetStatus, String actionCode, String actionName, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }
        validateStatusTransition(quotation.getQuotationStatus(), targetStatus);
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(targetStatus);
        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, actionCode, actionName, from, targetStatus, null, attachmentIds);
        return rows;
    }

    /**
     * 发送报价单给客户
     */
    @Override
    @Event(value = "quotation.sent", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int sendQuotation(Long quotationId, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 只有审核通过(6)状态的报价单可以发送（规则：仅审核通过的报价单才能上传/发送报价）
        if (!QuotationStatus.APPROVED.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有审核通过的报价单可以发送");
        }

        // 发送前校验报价单信息完整性（客户/日期/金额，与提交审核一致）
        validateQuotationForReview(quotation);

        // 更新发送信息
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(QuotationStatus.SENT.getCode());
        quotation.setSendTime(LocalDateTime.now());
        quotation.setSendMethod("email"); // 默认邮件发送

        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, "SEND", "发送报价", from, QuotationStatus.SENT.getCode(), null, attachmentIds);
        return rows;
    }

    /**
     * 报价单转为订单
     */
    @Override
    @Event(value = "quotation.converted", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public Object convertToOrder(Long quotationId) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 只有已接受的报价单可以转为订单
        if (!QuotationStatus.ACCEPTED.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有已接受的报价单可以转为订单");
        }

        // 从报价单创建订单
        SalesOrderAddDTO orderDTO = new SalesOrderAddDTO();
        orderDTO.setOrderNo(orderService.generateOrderNo());
        orderDTO.setCustomerId(quotation.getCustomerId());
        orderDTO.setCustomerName(quotation.getCustomerName());
        orderDTO.setOrderDate(new java.util.Date());
        orderDTO.setOrderType(1);
        orderDTO.setRemark("由报价单[" + quotation.getQuotationNo() + "]转换");
        // 透传链路追踪ID
        orderDTO.setTraceId(quotation.getTraceId());

        // 报价单明细 → 订单明细
        List<SalesQuotationItem> quotationItems = quotationItemMapper.selectList(
                new LambdaQueryWrapper<SalesQuotationItem>().eq(SalesQuotationItem::getQuotationId, quotationId));
        if (quotationItems != null && !quotationItems.isEmpty()) {
            List<com.jjx.sales.domain.dto.SalesOrderProductDTO> items = new ArrayList<>();
            for (SalesQuotationItem qi : quotationItems) {
                com.jjx.sales.domain.dto.SalesOrderProductDTO item = new com.jjx.sales.domain.dto.SalesOrderProductDTO();
                item.setProductId(qi.getProductId());
                item.setProductCode(qi.getProductCode());
                item.setProductName(qi.getProductName());
                item.setQuantity(qi.getQuantity());
                item.setUnitPrice(qi.getUnitPrice());
                item.setUnit(qi.getUnit());
                item.setAmount(qi.getAmount());
                item.setRemark(qi.getCustomRequirements());
                items.add(item);
            }
            orderDTO.setItems(items);
        }

        Long orderId = orderService.insertOrder(orderDTO);

        // 转订单成功后：报价单状态改为已完成(9)
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(QuotationStatus.COMPLETED.getCode());
        quotationMapper.updateById(quotation);
        recordFlow(quotation, "CONVERT_ORDER", "转订单完成", from, QuotationStatus.COMPLETED.getCode(), "转为销售订单，订单号:" + orderId, null);

        log.info("报价单{}已转为订单: orderId={}", quotationId, orderId);
        return orderId;
    }

    /**
     * 导出报价单PDF
     */
    @Override
    public String exportPdf(Long quotationId) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 这里应该实现PDF导出逻辑
        // 暂时返回一个占位符路径
        return "/exports/quotations/" + quotation.getQuotationNo() + ".pdf";
    }

    /**
     * 复制报价单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesQuotation copyQuotation(Long quotationId) {
        SalesQuotation original = selectQuotationById(quotationId);
        if (original == null) {
            throw new BusinessException("报价单不存在");
        }

        // 创建副本
        SalesQuotation copy = new SalesQuotation();
        copy.setQuotationNo("COPY_" + original.getQuotationNo());
        copy.setCustomerId(original.getCustomerId());
        copy.setCustomerName(original.getCustomerName());
        copy.setContactPerson(original.getContactPerson());
        copy.setContactPhone(original.getContactPhone());
        copy.setQuotationDate(original.getQuotationDate());
        copy.setValidUntil(original.getValidUntil());
        copy.setCurrency(original.getCurrency());
        copy.setExchangeRate(original.getExchangeRate());
        copy.setQuotationStatus(QuotationStatus.DRAFT.getCode());
        copy.setSubtotalAmount(original.getSubtotalAmount());
        copy.setTaxRate(original.getTaxRate());
        copy.setTaxAmount(original.getTaxAmount());
        copy.setTotalAmount(original.getTotalAmount());
        copy.setDiscountAmount(original.getDiscountAmount());
        copy.setFinalAmount(original.getFinalAmount());
        copy.setRemark("复制自报价单：" + original.getQuotationNo() + "\n" + original.getRemark());
        copy.setSalesPersonId(original.getSalesPersonId());
        copy.setSalesPersonName(original.getSalesPersonName());

        // 保存副本
        insertQuotation(copy);
        return copy;
    }

    /**
     * 提交报价单审核
     */
    @Override
    @Event(value = "quotation.submitted", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int submitReview(Long quotationId, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 草稿(0)或改单(8)状态的报价单可以提交审核（改单后重新流转）
        if (!QuotationStatus.DRAFT.getCode().equals(quotation.getQuotationStatus())
                && !QuotationStatus.MODIFYING.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有草稿或改单状态的报价单可以提交审核");
        }

        // 检查报价单信息是否完整
        validateQuotationForReview(quotation);

        // 更新状态为待审核
        Integer from = quotation.getQuotationStatus();
        quotation.setQuotationStatus(QuotationStatus.PENDING_REVIEW.getCode());
        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, "SUBMIT_REVIEW", "提交审核", from, QuotationStatus.PENDING_REVIEW.getCode(), null, attachmentIds);
        return rows;
    }

    /**
     * 审核报价单
     */
    @Override
    @Event(value = "quotation.reviewed", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int reviewQuotation(Long quotationId, Boolean approved, String remark, String attachmentIds) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 只有待审核状态的报价单可以审核
        if (!QuotationStatus.PENDING_REVIEW.getCode().equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有待审核状态的报价单可以审核");
        }

        // 更新审核信息
        quotation.setApproverId(1L); // 这里应该从上下文中获取审核人ID
        quotation.setApproverName("系统管理员"); // 这里应该从上下文中获取审核人姓名
        quotation.setApproveTime(LocalDateTime.now());
        quotation.setApproveRemark(remark);

        Integer from = quotation.getQuotationStatus();
        if (approved) {
            quotation.setQuotationStatus(QuotationStatus.APPROVED.getCode());
        } else {
            quotation.setQuotationStatus(QuotationStatus.REJECTED.getCode());
        }

        int rows = quotationMapper.updateById(quotation);
        recordFlow(quotation, approved ? "APPROVE" : "REJECT", approved ? "审核通过" : "审核驳回",
                from, quotation.getQuotationStatus(), remark, attachmentIds);
        return rows;
    }

    /**
     * 查询报价单流转记录
     */
    @Override
    public List<SalesQuotationFlow> selectFlowRecords(Long quotationId) {
        return quotationFlowMapper.selectByQuotationId(quotationId);
    }

    /**
     * 导出报价单列表
     */
    @Override
    public String exportQuotationList(SalesQuotation quotation) {
        // 这里应该实现Excel导出逻辑
        // 暂时返回一个占位符路径
        return "/exports/quotation-list.xlsx";
    }

    /**
     * 获取报价单状态选项
     */
    @Override
    public List<Object> getStatusOptions() {
        List<Object> options = new ArrayList<>();
        options.add(new Object() {
            public final Integer value = QuotationStatus.DRAFT.getCode();
            public final String label = "草稿";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.SENT.getCode();
            public final String label = "已发送";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.ACCEPTED.getCode();
            public final String label = "已接受";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.REJECTED.getCode();
            public final String label = "已拒绝";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.EXPIRED.getCode();
            public final String label = "已过期";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.PENDING_REVIEW.getCode();
            public final String label = "待审核";
        });
        options.add(new Object() {
            public final Integer value = QuotationStatus.APPROVED.getCode();
            public final String label = "已审核";
        });
        return options;
    }

    /**
     * 获取币种选项
     */
    @Override
    public List<Object> getCurrencyOptions() {
        List<Object> options = new ArrayList<>();
        options.add(new Object() {
            public final String value = "CNY";
            public final String label = "人民币";
        });
        options.add(new Object() {
            public final String value = "USD";
            public final String label = "美元";
        });
        options.add(new Object() {
            public final String value = "EUR";
            public final String label = "欧元";
        });
        options.add(new Object() {
            public final String value = "JPY";
            public final String label = "日元";
        });
        options.add(new Object() {
            public final String value = "HKD";
            public final String label = "港币";
        });
        return options;
    }

    /**
     * 获取报价模板列表
     */
    @Override
    public List<Object> getTemplates() {
        // 暂时返回空列表
        return new ArrayList<>();
    }

    /**
     * 根据模板创建报价单
     */
    @Override
    public SalesQuotation createFromTemplate(Long templateId, Long customerId) {
        // 暂时返回一个空的报价单
        SalesQuotation quotation = new SalesQuotation();
        quotation.setQuotationNo("TEMP_" + System.currentTimeMillis());
        quotation.setCustomerId(customerId);
        quotation.setQuotationStatus(QuotationStatus.DRAFT.getCode());
        quotation.setCurrency("CNY");
        quotation.setExchangeRate(java.math.BigDecimal.valueOf(1.0000));
        return quotation;
    }

    /**
     * 快速报价
     */
    @Override
    public SalesQuotation quickQuote(Object quickQuoteRequest) {
        // 暂时返回一个空的报价单
        SalesQuotation quotation = new SalesQuotation();
        quotation.setQuotationNo("QUICK_" + System.currentTimeMillis());
        quotation.setQuotationStatus(QuotationStatus.DRAFT.getCode());
        quotation.setCurrency("CNY");
        quotation.setExchangeRate(java.math.BigDecimal.valueOf(1.0000));
        return quotation;
    }

    /**
     * 获取客户历史报价
     */
    @Override
    public List<SalesQuotation> getCustomerHistory(Long customerId) {
        LambdaQueryWrapper<SalesQuotation> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SalesQuotation::getCustomerId, customerId)
               .eq(SalesQuotation::getDeleted, 0)
               .orderByDesc(SalesQuotation::getCreateTime);
        return quotationMapper.selectList(wrapper);
    }

    /**
     * 获取报价单统计信息
     */
    @Override
    public Object getQuotationStatistics() {
        // 暂时返回空对象
        Map<String, Object> stats = new HashMap<>();
        List<SalesQuotation> all = quotationMapper.selectList(Wrappers.emptyWrapper());
        stats.put("totalCount", (long) all.size());
        stats.put("totalAmount", all.stream().filter(q -> q.getTotalAmount() != null).mapToDouble(q -> q.getTotalAmount().doubleValue()).sum());
        long draftCount = all.stream().filter(q -> QuotationStatus.DRAFT.getCode().equals(q.getQuotationStatus())).count();
        long sentCount = all.stream().filter(q -> QuotationStatus.SENT.getCode().equals(q.getQuotationStatus())).count();
        long acceptedCount = all.stream().filter(q -> QuotationStatus.ACCEPTED.getCode().equals(q.getQuotationStatus())).count();
        long rejectedCount = all.stream().filter(q -> QuotationStatus.REJECTED.getCode().equals(q.getQuotationStatus()) || QuotationStatus.EXPIRED.getCode().equals(q.getQuotationStatus())).count();
        stats.put("draftCount", draftCount);
        stats.put("sentCount", sentCount);
        stats.put("acceptedCount", acceptedCount);
        stats.put("rejectedCount", rejectedCount);
        return stats;
    }

    /**
     * 验证状态转换是否合法
     */
    private static void validateStatusTransition(Integer currentStatus, Integer newStatus) {
        // 状态转换规则：
        // draft -> pending_review, sent
        // pending_review -> approved, rejected
        // approved -> sent
        // sent -> accepted, rejected, expired
        // accepted -> completed (转订单) / modifying (改单)
        // completed -> modifying (改单)
        // modifying -> draft (编辑后重新提交)
        // rejected, expired -> (不能转换)

        if (QuotationStatus.DRAFT.getCode().equals(currentStatus) && (QuotationStatus.PENDING_REVIEW.getCode().equals(newStatus) || QuotationStatus.SENT.getCode().equals(newStatus))) {
            return;
        }

        if (QuotationStatus.PENDING_REVIEW.getCode().equals(currentStatus) && (QuotationStatus.APPROVED.getCode().equals(newStatus) || QuotationStatus.REJECTED.getCode().equals(newStatus))) {
            return;
        }

        if (QuotationStatus.APPROVED.getCode().equals(currentStatus) && QuotationStatus.SENT.getCode().equals(newStatus)) {
            return;
        }

        if (QuotationStatus.SENT.getCode().equals(currentStatus) && (QuotationStatus.ACCEPTED.getCode().equals(newStatus) || QuotationStatus.REJECTED.getCode().equals(newStatus) || QuotationStatus.EXPIRED.getCode().equals(newStatus))) {
            return;
        }

        if (QuotationStatus.ACCEPTED.getCode().equals(currentStatus) && QuotationStatus.COMPLETED.getCode().equals(newStatus)) {
            return;
        }

        if (QuotationStatus.COMPLETED.getCode().equals(currentStatus) && QuotationStatus.MODIFYING.getCode().equals(newStatus)) {
            return;
        }

        if (QuotationStatus.MODIFYING.getCode().equals(currentStatus) && QuotationStatus.DRAFT.getCode().equals(newStatus)) {
            return;
        }

        throw new BusinessException("状态转换不合法：从状态" + currentStatus + "转换到状态" + newStatus);
    }

    /**
     * 验证报价单信息是否完整（提交审核/发送报价共用）
     */
    private void validateQuotationForReview(SalesQuotation quotation) {
        if (quotation.getCustomerId() == null) {
            throw new BusinessException("客户信息不能为空");
        }

        if (quotation.getQuotationDate() == null) {
            throw new BusinessException("报价日期不能为空");
        }

        if (quotation.getTotalAmount() == null || quotation.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException("报价金额必须大于0");
        }

        if (quotation.getFinalAmount() == null || quotation.getFinalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException("最终金额必须大于0");
        }

        // 报价明细不能为空（没有明细的报价单无法发送/审核）
        Long itemCount = quotationItemMapper.selectCount(
                new LambdaQueryWrapper<SalesQuotationItem>()
                        .eq(SalesQuotationItem::getQuotationId, quotation.getQuotationId()));
        if (itemCount == null || itemCount == 0) {
            throw new BusinessException("报价明细不能为空，请先添加报价明细");
        }
    }
}
