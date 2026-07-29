package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.service.IOrderService;
import com.jjx.sales.service.IQuotationService;
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
        if (quotation.getQuotationStatus() != null && !quotation.getQuotationStatus().isEmpty()) {
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
        return quotation;
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

        // 设置默认值
        if (quotation.getQuotationStatus() == null || quotation.getQuotationStatus().isEmpty()) {
            quotation.setQuotationStatus("draft"); // 草稿状态
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

        return quotationMapper.insert(quotation);
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

        return quotationMapper.updateById(quotation);
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

        // 检查报价单状态，已发送或已接受的报价单不能删除
        if ("sent".equals(quotation.getQuotationStatus()) || "accepted".equals(quotation.getQuotationStatus())) {
            throw new BusinessException("已发送或已接受的报价单不能删除");
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
     * 更新报价单状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateQuotationStatus(Long quotationId, String status) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 状态转换验证
        validateStatusTransition(quotation.getQuotationStatus(), status);

        quotation.setQuotationStatus(status);
        return quotationMapper.updateById(quotation);
    }

    /**
     * 发送报价单给客户
     */
    @Override
    @Event(value = "quotation.sent", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int sendQuotation(Long quotationId) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 只有草稿状态的报价单可以发送
        if (!"draft".equalsIgnoreCase(quotation.getQuotationStatus())) {
            throw new BusinessException("只有草稿状态的报价单可以发送");
        }

        // 更新发送信息
        quotation.setQuotationStatus("sent");
        quotation.setSendTime(LocalDateTime.now());
        quotation.setSendMethod("email"); // 默认邮件发送

        return quotationMapper.updateById(quotation);
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
        if (!"accepted".equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有已接受的报价单可以转为订单");
        }

        // 从报价单创建订单
        SalesOrderAddDTO orderDTO = new SalesOrderAddDTO();
        orderDTO.setCustomerId(quotation.getCustomerId());
        orderDTO.setCustomerName(quotation.getCustomerName());
        orderDTO.setRemark("由报价单[" + quotation.getQuotationNo() + "]转换");
        // 透传链路追踪ID
        orderDTO.setTraceId(quotation.getTraceId());

        Long orderId = orderService.insertOrder(orderDTO);
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
        copy.setQuotationStatus("draft");
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
    public int submitReview(Long quotationId) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 只有草稿状态的报价单可以提交审核
        if (!"draft".equalsIgnoreCase(quotation.getQuotationStatus())) {
            throw new BusinessException("只有草稿状态的报价单可以提交审核");
        }

        // 检查报价单信息是否完整
        validateQuotationForReview(quotation);

        // 更新状态为待审核
        quotation.setQuotationStatus("pending_review");
        return quotationMapper.updateById(quotation);
    }

    /**
     * 审核报价单
     */
    @Override
    @Event(value = "quotation.reviewed", bizId = "#quotationId", bizType = "'quotation'")
    @Transactional(rollbackFor = Exception.class)
    public int reviewQuotation(Long quotationId, Boolean approved, String remark) {
        SalesQuotation quotation = selectQuotationById(quotationId);
        if (quotation == null) {
            throw new BusinessException("报价单不存在");
        }

        // 只有待审核状态的报价单可以审核
        if (!"pending_review".equals(quotation.getQuotationStatus())) {
            throw new BusinessException("只有待审核状态的报价单可以审核");
        }

        // 更新审核信息
        quotation.setApproverId(1L); // 这里应该从上下文中获取审核人ID
        quotation.setApproverName("系统管理员"); // 这里应该从上下文中获取审核人姓名
        quotation.setApproveTime(LocalDateTime.now());
        quotation.setApproveRemark(remark);

        if (approved) {
            quotation.setQuotationStatus("approved");
        } else {
            quotation.setQuotationStatus("rejected");
        }

        return quotationMapper.updateById(quotation);
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
            public final String value = "draft";
            public final String label = "草稿";
        });
        options.add(new Object() {
            public final String value = "sent";
            public final String label = "已发送";
        });
        options.add(new Object() {
            public final String value = "accepted";
            public final String label = "已接受";
        });
        options.add(new Object() {
            public final String value = "rejected";
            public final String label = "已拒绝";
        });
        options.add(new Object() {
            public final String value = "expired";
            public final String label = "已过期";
        });
        options.add(new Object() {
            public final String value = "pending_review";
            public final String label = "待审核";
        });
        options.add(new Object() {
            public final String value = "approved";
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
        quotation.setQuotationStatus("draft");
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
        quotation.setQuotationStatus("draft");
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
        long draftCount = all.stream().filter(q -> "draft".equals(q.getQuotationStatus())).count();
        long sentCount = all.stream().filter(q -> "sent".equals(q.getQuotationStatus())).count();
        long acceptedCount = all.stream().filter(q -> "accepted".equals(q.getQuotationStatus())).count();
        long rejectedCount = all.stream().filter(q -> "rejected".equals(q.getQuotationStatus()) || "expired".equals(q.getQuotationStatus())).count();
        stats.put("draftCount", draftCount);
        stats.put("sentCount", sentCount);
        stats.put("acceptedCount", acceptedCount);
        stats.put("rejectedCount", rejectedCount);
        return stats;
    }

    /**
     * 验证状态转换是否合法
     */
    private static void validateStatusTransition(String currentStatus, String newStatus) {
        // 状态转换规则：
        // draft -> pending_review, sent
        // pending_review -> approved, rejected
        // approved -> sent
        // sent -> accepted, rejected, expired
        // accepted -> (可以转为订单)
        // rejected, expired -> (不能转换)

        if ("draft".equals(currentStatus) && ("pending_review".equals(newStatus) || "sent".equals(newStatus))) {
            return;
        }

        if ("pending_review".equals(currentStatus) && ("approved".equals(newStatus) || "rejected".equals(newStatus))) {
            return;
        }

        if ("approved".equals(currentStatus) && "sent".equals(newStatus)) {
            return;
        }

        if ("sent".equals(currentStatus) && ("accepted".equals(newStatus) || "rejected".equals(newStatus) || "expired".equals(newStatus))) {
            return;
        }

        throw new BusinessException("状态转换不合法：从状态" + currentStatus + "转换到状态" + newStatus);
    }

    /**
     * 验证报价单信息是否完整（用于提交审核）
     */
    private static void validateQuotationForReview(SalesQuotation quotation) {
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

        // 这里可以添加更多验证规则
    }
}
