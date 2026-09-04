package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.product.service.IEngineeringRoutingService;
import com.jjx.system.annotation.Event;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.service.LogSaveService;
import com.jjx.system.service.OperLogChangeRecorder;
import com.jjx.system.utils.SecurityUtils;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.ProductValidationVO;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.production.domain.dto.ProductionOrderCreateDTO;
import com.jjx.production.service.ProductionOrderService;
import com.jjx.sales.domain.converter.SalesOrderConverter;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.domain.dto.SalesOrderEditDTO;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.dto.SalesOrderQueryDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.vo.CustomerVO;
import com.jjx.sales.domain.vo.OrderReferValidationVO;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.sales.domain.vo.SalesOrderVO;
import com.jjx.common.utils.pdf.PdfDocBuilder;
import com.jjx.sales.enums.SalesOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.service.ICustomerService;
import com.jjx.sales.service.IOrderService;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.jjx.product.enums.ProductEnums;

import java.time.LocalDateTime;
import java.text.DecimalFormat;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 销售订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements IOrderService {

    private final OrderMapper orderMapper;
    private final RedisSequenceService redisSequenceService;
    private final SalesOrderConverter orderConverter;
    private final com.jjx.product.service.ProductCustomerValidator productCustomerValidator;
    private final ISalesOrderProductService orderProductService;
    private final ICustomerService customerService;
    private final ProductMapper productMapper;
    private final ProductionOrderService productionOrderService;
    private final IEngineeringBomService bomService;
    private final IEngineeringRoutingService routingService;
    private final com.jjx.common.utils.pdf.PdfConfigLoader pdfConfigLoader;
    private final LogSaveService logSaveService;
    private final OperLogChangeRecorder changeRecorder;

    /**
     * 查询销售订单列表
     */
    @Override
    public PageResult<SalesOrderVO> pageQuery(SalesOrderQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<SalesOrder> wrapper = buildQueryWrapper(queryDTO);

        // 分页查询
        Page<SalesOrder> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<SalesOrder> orderPage = orderMapper.selectPage(page, wrapper);

        // Entity 转 VO
        List<SalesOrderVO> voList = orderConverter.toVOList(orderPage.getRecords());

        // 返回分页结果
        return PageResult.of(orderPage, voList);
    }
    /**
     * 列表查询（不分页）
     */
    @Override
    public List<SalesOrderVO> getOrderList(SalesOrderQueryDTO queryDTO) {
        LambdaQueryWrapper<SalesOrder> wrapper = buildQueryWrapper(queryDTO);
        List<SalesOrder> orderList = orderMapper.selectList(wrapper);
        return orderConverter.toVOList(orderList);
    }
    /**
     * 根据ID查询销售订单
     */
    @Override
    public SalesOrderVO selectOrderById(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        SalesOrderVO vo = orderConverter.toVO(order);
        List<SalesOrderProductVO> items = orderProductService.getListByOrderId(orderId);
        vo.setItems(items);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException("订单不存在或已被删除");
        }
        return vo;
    }

    /**
     * 新增销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long insertOrder(SalesOrderAddDTO dto) {
        // 自动生成订单编号（使用Redis）
//        String orderNo = redisSequenceService.generateSalesOrderNumber();
//        log.info("使用Redis生成订单编号：{}", orderNo);

        // 检查订单号是否唯一（理论上不会重复，但做双重检查）
        String orderNo = dto.getOrderNo();

        if (!checkOrderNoUnique(orderNo)) {
            log.error("订单号重复异常：{}，Redis生成的编号应该唯一", orderNo);
            throw new BusinessException(BusinessExceptionEnum.ORDER_ALREADY_CANCELLED);
        }
        SalesOrder entity = orderConverter.toEntity(dto);
        entity.setOrderNo(orderNo);
        // 链路追踪（DEV-568）：无上游 traceId 则生成 UUID
        String traceId = dto.getTraceId() != null && !dto.getTraceId().isEmpty()
                ? dto.getTraceId() : java.util.UUID.randomUUID().toString().replace("-", "");
        dto.setTraceId(traceId);
        entity.setTraceId(traceId);
        int insert = orderMapper.insert(entity);
        if(insert>0){
            // 校验并处理产品明细
            validateOrderItems(dto.getItems(), dto.getOrderType(), dto.getCustomerId());
            ensureProductIds(dto.getItems(), dto.getOrderType());
            dto.getItems().forEach(i -> i.setOrderId(entity.getOrderId()));
            orderProductService.batchAdd(dto.getItems());
            // 2026-08-18 task 1047：写创建日志（带新单 traceId）——链路出现"创建"节点，
            // 且后续编辑/删除的 @Log 可回退继承 traceId，草稿期操作全部进链路
            saveOrderCreateLog(entity);
            return entity.getOrderId();
        }
        throw new BusinessException(BusinessExceptionEnum.DB_INSERT_FAILED);
    }

    /**
     * 2026-08-18 task 1047：订单创建日志（带 traceId，仿 copyOrder 写法）
     */
    private void saveOrderCreateLog(SalesOrder order) {
        try {
            SysOperLog log = new SysOperLog();
            log.setModule("销售订单");
            log.setBusinessType(1); // 新增
            log.setOperUrl("order.create");
            log.setBizType("order");
            log.setBizId(String.valueOf(order.getOrderId()));
            log.setTraceId(order.getTraceId());
            log.setBizStatus(SalesOrderStatusEnum.getByValue(order.getOrderStatus()).getLabel());
            log.setOperParam("创建销售订单 " + order.getOrderNo() + "（" + order.getCustomerName() + "）");
            log.setStatus(1);
            log.setCreateTime(LocalDateTime.now());
            try {
                log.setUsername(SecurityUtils.getUsername());
                log.setUserId(SecurityUtils.getUserId());
                log.setRealName(SecurityUtils.getRealName());
            } catch (Exception ignored) {
            }
            logSaveService.saveOperLog(log);
        } catch (Exception e) {
            log.warn("记录订单创建日志失败: {}", e.getMessage());
        }
    }

    /**
     * 修改销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrder(SalesOrderEditDTO dto) {
        // 检查订单是否存在
        SalesOrder existingOrder = orderMapper.selectById(dto.getOrderId());
        if (existingOrder == null) {
            throw new BusinessException(BusinessExceptionEnum.ORDER_NOT_FOUND);
        }

        // 检查负责人权限（超级管理员除外）
        Long currentUserId = SecurityUtils.getUserId();
        if (existingOrder.getSalesManagerId() != null && !existingOrder.getSalesManagerId().equals(currentUserId)) {
            if (!SecurityUtils.hasPermission("*:*:*")) {
                throw new BusinessException("只能编辑本人负责的订单");
            }
        }

        SalesOrder entity = orderConverter.toEntity(dto);

        // 2026-08-18 L3：修改前抓旧明细（用于字段级变更对比）
        java.util.List<SalesOrderProductVO> oldItemVOs = null;
        try {
            oldItemVOs = orderProductService.getListByOrderId(dto.getOrderId());
        } catch (Exception ignored) {
        }

        int insert = orderMapper.updateById(entity);
        orderProductService.deleteByOrderId(dto.getOrderId());
        if(insert>0){
            // 校验并处理产品明细
            validateOrderItems(dto.getItems(), dto.getOrderType(), dto.getCustomerId());
            ensureProductIds(dto.getItems(), dto.getOrderType());
            dto.getItems().forEach(i -> i.setOrderId(dto.getOrderId()));
            boolean ok = orderProductService.batchAdd(dto.getItems());
            // 2026-08-18 L3：字段级变更对比日志（谁/何时/改了什么）
            saveOrderUpdateChangeLog(existingOrder, oldItemVOs, dto);
            return ok;
        }
        throw new BusinessException(BusinessExceptionEnum.DB_UPDATE_FAILED);
    }

    /**
     * 2026-08-18 L3：销售订单编辑的字段级变更对比，写入操作日志（detail=变更JSON，operParam=摘要文本）
     */
    private void saveOrderUpdateChangeLog(SalesOrder oldOrder, java.util.List<SalesOrderProductVO> oldItems,
                                          SalesOrderEditDTO dto) {
        java.util.List<String> changes = new java.util.ArrayList<>();
        diffMainFields(changes, oldOrder, dto);
        diffItemFields(changes, oldItems, dto.getItems());
        changeRecorder.recordUpdate("销售订单", "order.update", "order",
            String.valueOf(dto.getOrderId()), oldOrder.getTraceId(),
            SalesOrderStatusEnum.getByValue(oldOrder.getOrderStatus()).getLabel(), changes);
    }

    /** 主表字段对比（白名单，排除 createTime 等系统字段） */
    private void diffMainFields(java.util.List<String> changes, SalesOrder oldOrder, SalesOrderEditDTO dto) {
        changeRecorder.diff(changes, "客户", oldOrder.getCustomerName(), dto.getCustomerName());
        changeRecorder.diff(changes, "联系人", oldOrder.getContactPerson(), dto.getContactPerson());
        changeRecorder.diff(changes, "联系电话", oldOrder.getContactPhone(), dto.getContactPhone());
        changeRecorder.diff(changes, "下单日期",
            changeRecorder.fmtDate(oldOrder.getOrderDate()), changeRecorder.fmtDate(dto.getOrderDate()));
        changeRecorder.diff(changes, "交货日期",
            changeRecorder.fmtDate(oldOrder.getDeliveryDate()), changeRecorder.fmtDate(dto.getDeliveryDate()));
        changeRecorder.diff(changes, "订单类型", oldOrder.getOrderType(), dto.getOrderType());
        changeRecorder.diff(changes, "加急", oldOrder.getIsUrgent(), dto.getIsUrgent());
        changeRecorder.diff(changes, "币种", oldOrder.getCurrency(), dto.getCurrency());
        changeRecorder.diff(changes, "汇率", oldOrder.getExchangeRate(), dto.getExchangeRate());
        changeRecorder.diff(changes, "付款条款", oldOrder.getPaymentTerms(), dto.getPaymentTerms());
        changeRecorder.diff(changes, "交货条款", oldOrder.getDeliveryTerms(), dto.getDeliveryTerms());
        changeRecorder.diff(changes, "交货地址", oldOrder.getDeliveryAddress(), dto.getDeliveryAddress());
        changeRecorder.diff(changes, "总金额", oldOrder.getTotalAmount(), dto.getTotalAmount());
        changeRecorder.diff(changes, "税率", oldOrder.getTaxRate(), dto.getTaxRate());
        changeRecorder.diff(changes, "折扣率", oldOrder.getDiscountRate(), dto.getDiscountRate());
        changeRecorder.diff(changes, "总数量", oldOrder.getTotalQuantity(), dto.getTotalQuantity());
        changeRecorder.diff(changes, "销售员", oldOrder.getSalesManagerName(), dto.getSalesManagerName());
        changeRecorder.diff(changes, "备注", oldOrder.getRemark(), dto.getRemark());
    }

    /** 明细对比（旧明细 vs 新明细：按 productId 匹配 新增/删除/修改数量单价） */
    private void diffItemFields(java.util.List<String> changes, java.util.List<SalesOrderProductVO> oldItems,
                                java.util.List<SalesOrderProductDTO> newItems) {
        if (oldItems == null) return;
        java.util.Map<Long, SalesOrderProductVO> oldMap = new java.util.HashMap<>();
        for (SalesOrderProductVO it : oldItems) {
            if (it.getProductId() != null) oldMap.put(it.getProductId(), it);
        }
        java.util.Set<Long> newIds = new java.util.HashSet<>();
        for (SalesOrderProductDTO ni : newItems) {
            if (ni.getProductId() == null) continue;
            newIds.add(ni.getProductId());
            String label = (ni.getProductCode() != null ? ni.getProductCode() : "")
                    + (ni.getProductName() != null ? " " + ni.getProductName() : "");
            SalesOrderProductVO oi = oldMap.get(ni.getProductId());
            if (oi == null) {
                changes.add("新增明细:" + label + " 数量" + ni.getQuantity());
            } else {
                if (!java.util.Objects.equals(oi.getQuantity(), ni.getQuantity())) {
                    changes.add("明细" + label + " 数量:" + oi.getQuantity() + "→" + ni.getQuantity());
                }
                if (!java.util.Objects.equals(oi.getUnitPrice(), ni.getUnitPrice())) {
                    changes.add("明细" + label + " 单价:" + oi.getUnitPrice() + "→" + ni.getUnitPrice());
                }
            }
        }
        for (SalesOrderProductVO oi : oldItems) {
            if (oi.getProductId() != null && !newIds.contains(oi.getProductId())) {
                changes.add("删除明细:" + (oi.getProductCode() != null ? oi.getProductCode() + " " : "")
                        + (oi.getProductName() != null ? oi.getProductName() : ""));
            }
        }
    }

    /**
     * 删除销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteOrderById(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 检查负责人权限（超级管理员除外）
        Long currentUserId = SecurityUtils.getUserId();
        if (order.getSalesManagerId() != null && !order.getSalesManagerId().equals(currentUserId)) {
            if (!SecurityUtils.hasPermission("*:*:*")) {
                throw new BusinessException("只能删除本人负责的订单");
            }
        }

        // 检查订单状态，已确认或生产中的订单不能删除
        if (order.getOrderStatus() >= SalesOrderStatusEnum.PENDING_REVIEW.getValue()) {
            throw new BusinessException("已确认或生产中的订单不能删除");
        }

        // 逻辑删除（MP @TableLogic：deleteById 自动 SET deleted=1 WHERE id AND deleted=0）
        // 注意：不能 setDeleted(1)+updateById —— MP 逻辑删除字段不参与 UPDATE SET，那样 deleted 不会变（同报价单删除修复）
        return orderMapper.deleteById(orderId);
    }

    /**
     * 批量删除销售订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteOrderByIds(Long[] orderIds) {
        int count = 0;
        for (Long orderId : orderIds) {
            count += deleteOrderById(orderId);
        }
        return count;
    }

    /**
     * 复制订单：已取消/已完成等终态订单一键重新生成新草稿单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyOrder(Long orderId) {
        SalesOrder source = orderMapper.selectById(orderId);
        if (source == null) {
            throw new BusinessException(BusinessExceptionEnum.ORDER_NOT_FOUND);
        }

        SalesOrder copy = new SalesOrder();
        copy.setOrderNo(generateOrderNo());
        copy.setCustomerId(source.getCustomerId());
        copy.setCustomerName(source.getCustomerName());
        copy.setContactPerson(source.getContactPerson());
        copy.setContactPhone(source.getContactPhone());
        copy.setOrderDate(new Date());
        copy.setDeliveryDate(source.getDeliveryDate());
        copy.setOrderType(source.getOrderType());
        copy.setCurrency(source.getCurrency());
        copy.setExchangeRate(source.getExchangeRate());
        copy.setPaymentTerms(source.getPaymentTerms());
        copy.setDeliveryTerms(source.getDeliveryTerms());
        copy.setDeliveryAddress(source.getDeliveryAddress());
        copy.setTotalAmount(source.getTotalAmount());
        copy.setTaxRate(source.getTaxRate());
        copy.setTaxAmount(source.getTaxAmount());
        copy.setDiscountRate(source.getDiscountRate());
        copy.setDiscountAmount(source.getDiscountAmount());
        copy.setFinalAmount(source.getFinalAmount());
        copy.setTotalQuantity(source.getTotalQuantity());
        copy.setIsUrgent(source.getIsUrgent());
        copy.setUrgentReason(source.getUrgentReason());
        copy.setSalesManagerId(source.getSalesManagerId());
        copy.setSalesManagerName(source.getSalesManagerName());
        // 新单草稿状态，独立链路追踪
        copy.setOrderStatus(SalesOrderStatusEnum.DRAFT.getValue());
        copy.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));
        copy.setRemark("复制自订单[" + source.getOrderNo() + "]"
                + (source.getRemark() != null ? "\n" + source.getRemark() : ""));
        int insert = orderMapper.insert(copy);
        if (insert <= 0) {
            throw new BusinessException(BusinessExceptionEnum.DB_INSERT_FAILED);
        }

        // 复制产品明细
        List<SalesOrderProductVO> items = orderProductService.getListByOrderId(orderId);
        if (items != null && !items.isEmpty()) {
            List<SalesOrderProductDTO> dtos = new ArrayList<>();
            for (SalesOrderProductVO it : items) {
                SalesOrderProductDTO dto = new SalesOrderProductDTO();
                dto.setOrderId(copy.getOrderId());
                dto.setProductId(it.getProductId());
                dto.setProductCode(it.getProductCode());
                dto.setProductName(it.getProductName());
                dto.setQuantity(it.getQuantity());
                dto.setUnit(it.getUnit());
                dto.setUnitPrice(it.getUnitPrice());
                dto.setAmount(it.getAmount());
                dto.setSpecification(it.getSpecification());
                dto.setCustomerMaterialNo(it.getCustomerMaterialNo());
                dto.setLineRemark(it.getLineRemark());
                dto.setRemark(it.getRemark());
                dtos.add(dto);
            }
            orderProductService.batchAdd(dtos);
        }

        // DEV：给新单写操作日志（带新单自身 traceId，供新单查看流水链路）
        try {
            SysOperLog newLog = new SysOperLog();
            newLog.setModule("销售订单管理");
            newLog.setBusinessType(1); // 新增
            newLog.setOperUrl("/sales/orders/" + orderId + "/copy");
            newLog.setBizType("order");
            newLog.setBizId(String.valueOf(copy.getOrderId()));
            newLog.setTraceId(copy.getTraceId());
            newLog.setBizStatus(SalesOrderStatusEnum.getByValue(copy.getOrderStatus()).getLabel());
            newLog.setStatus(1);
            newLog.setOperParam("{\"action\":\"copy\",\"sourceOrderNo\":\"" + source.getOrderNo() + "\"}");
            newLog.setCreateTime(LocalDateTime.now());
            try {
                newLog.setUserId(SecurityUtils.getUserId());
                newLog.setUsername(SecurityUtils.getUsername());
                newLog.setRealName(SecurityUtils.getRealName());
            } catch (Exception ignored) {
            }
            logSaveService.saveOperLog(newLog);
        } catch (Exception e) {
            log.warn("记录新单复制日志失败: {}", e.getMessage());
        }

        // DEV：给原单写“被复制”操作日志（原单流水可追溯复制来源，双向可查）
        try {
            SysOperLog operLog = new SysOperLog();
            operLog.setModule("销售订单管理");
            operLog.setBusinessType(2); // 修改
            operLog.setOperUrl("/sales/orders/" + orderId + "/copy");
            operLog.setBizType("order");
            operLog.setBizId(String.valueOf(orderId));
            operLog.setTraceId(source.getTraceId());
            operLog.setBizStatus(SalesOrderStatusEnum.getByValue(source.getOrderStatus()).getLabel());
            operLog.setStatus(1);
            operLog.setOperParam("{\"action\":\"copy\",\"newOrderNo\":\"" + copy.getOrderNo()
                    + "\",\"newOrderId\":" + copy.getOrderId() + "}");
            operLog.setCreateTime(LocalDateTime.now());
            try {
                operLog.setUserId(SecurityUtils.getUserId());
                operLog.setUsername(SecurityUtils.getUsername());
                operLog.setRealName(SecurityUtils.getRealName());
            } catch (Exception ignored) {
            }
            logSaveService.saveOperLog(operLog);
        } catch (Exception e) {
            log.warn("记录原单复制日志失败: {}", e.getMessage());
        }

        log.info("订单[{}]复制成功，生成新订单[{}](orderId={})", source.getOrderNo(), copy.getOrderNo(), copy.getOrderId());
        return copy.getOrderId();
    }

    /**
     * 生成订单号
     */
    @Override
    public String generateOrderNo() {
        return redisSequenceService.generateBusinessNumberByType("sales_order", "SO", "yyMMdd", 3);
    }

    /**
     * 检查订单号是否存在
     */
    @Override
    public boolean checkOrderNoUnique(String orderNo) {
        int count = orderMapper.checkOrderNoUnique(orderNo);
        return count == 0;
    }

    /**
     * 更新订单状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOrderStatus(Long orderId, Integer status) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 状态转换验证
        validateStatusTransition(order.getOrderStatus(), status);

        return orderMapper.updateOrderStatus(orderId, status);
    }

    /**
     * 审核订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approveOrder(Long orderId, Long approverId, String approverName, String approveRemark) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 只有草稿状态的订单可以审核
        if (order.getOrderStatus() != SalesOrderStatusEnum.DRAFT.getValue()) {
            throw new BusinessException("只有草稿状态的订单可以审核");
        }

        // 更新审核信息
        order.setOrderStatus(SalesOrderStatusEnum.PENDING_REVIEW.getValue()); // 待审核

        return orderMapper.updateById(order);
    }

    /**
     * 更新付款信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePaymentInfo(Long orderId, Double paidAmount) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 计算新的已付金额和未付金额
        java.math.BigDecimal newPaidAmount = order.getPaidAmount().add(java.math.BigDecimal.valueOf(paidAmount));
        java.math.BigDecimal newUnpaidAmount = order.getFinalAmount().subtract(newPaidAmount);

        if (newUnpaidAmount.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessException("已付金额不能超过最终金额");
        }

        order.setPaidAmount(newPaidAmount);
        order.setUnpaidAmount(newUnpaidAmount);

        return orderMapper.updateById(order);
    }

    /**
     * 根据客户ID查询订单列表
     */
    @Override
    public List<SalesOrder> selectOrdersByCustomerId(Long customerId) {
        LambdaQueryWrapper<SalesOrder> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SalesOrder::getCustomerId, customerId)
               .eq(SalesOrder::getDeleted, 0)
               .orderByDesc(SalesOrder::getCreateTime);
        return orderMapper.selectList(wrapper);
    }

    /**
     * 根据报价单ID查询订单
     */
    @Override
    public SalesOrder selectOrderByQuotationId(Long quotationId) {
        LambdaQueryWrapper<SalesOrder> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SalesOrder::getQuotationId, quotationId)
               .eq(SalesOrder::getDeleted, 0);
        return orderMapper.selectOne(wrapper);
    }

    /**
     * 提交审核
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitReview(Long orderId) {
        SalesOrder order =orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 只有草稿状态的订单可以提交审核
        if (order.getOrderStatus() != SalesOrderStatusEnum.DRAFT.getValue()) {
            throw new BusinessException("只有草稿状态的订单可以提交审核");
        }

        // 检查订单信息是否完整
        validateOrderForReview(order);

        // 更新状态为待审核（这里假设状态2是待审核）
        return orderMapper.updateOrderStatus(orderId, SalesOrderStatusEnum.PENDING_REVIEW.getValue());
    }

    /**
     * 客户确认订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmOrder(Long orderId, String confirmedBy) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 只有已审核的订单可以客户确认
        if (order.getOrderStatus() != SalesOrderStatusEnum.APPROVED.getValue()) {
            throw new BusinessException("只有已审核的订单可以客户确认");
        }

        // 更新状态为已确认
        order.setOrderStatus(SalesOrderStatusEnum.CONFIRMED.getValue());
        // 这里可以添加确认人信息到备注中
        String newRemark = order.getRemark() + "\n客户确认人：" + confirmedBy + "，确认时间：" + LocalDateTime.now();
        order.setRemark(newRemark);

        return orderMapper.updateById(order);
    }

    /**
     * 创建产品实例
     */
    @Override
    @Event(value = "order.production_started", bizId = "#orderId", bizType = "'order'")
    @Transactional(rollbackFor = Exception.class)
    public int createInstances(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 检查负责人权限（超级管理员除外）
        Long currentUserId = SecurityUtils.getUserId();
        if (order.getSalesManagerId() != null && !order.getSalesManagerId().equals(currentUserId)) {
            if (!SecurityUtils.hasPermission("*:*:*")) {
                throw new BusinessException("只能为本人负责的订单创建实例");
            }
        }

        // 只有已确认的订单可以创建产品实例
        if (order.getOrderStatus() != SalesOrderStatusEnum.CONFIRMED.getValue()) {
            throw new BusinessException("只有已确认的订单可以创建产品实例");
        }

        // 查询订单明细（产品列表）
        List<SalesOrderProductVO> productList = orderProductService.getListByOrderId(orderId);
        if (productList == null || productList.isEmpty()) {
            throw new BusinessException("订单没有产品明细，无法创建生产工单");
        }

        // 为每个产品创建生产工单
        for (SalesOrderProductVO product : productList) {
            Long productId = product.getProductId();
            if (productId == null) {
                throw new BusinessException("订单明细缺少产品ID");
            }

            // 1. 校验产品已发布
            Product prod = productMapper.selectById(productId);
            if (prod == null) {
                throw new BusinessException("产品不存在: " + product.getProductCode());
            }
            if (prod.getProductStatus() != null && prod.getProductStatus() != 6) {
                throw new BusinessException("产品[" + prod.getProductCode() + "]未发布(状态=" + prod.getProductStatus() + ")，不能提交生产");
            }

            // 2. 校验 BOM 已批准（当前生效版本）
            EngineeringBom bom = bomService.getOne(new LambdaQueryWrapper<EngineeringBom>()
                    .eq(EngineeringBom::getProductId, productId)
                    .eq(EngineeringBom::getIsCurrent, true)
                    .eq(EngineeringBom::getApproveStatus, 3)
                    .orderByDesc(EngineeringBom::getBomId)
                    .last("LIMIT 1"));
            if (bom == null) {
                throw new BusinessException("产品[" + prod.getProductCode() + "]没有已批准的BOM，不能提交生产");
            }

            // 3. 校验工艺路线已批准（当前生效版本）
            EngineeringRouting routing = routingService.getOne(new LambdaQueryWrapper<EngineeringRouting>()
                    .eq(EngineeringRouting::getProductId, productId)
                    .eq(EngineeringRouting::getApproveStatus, 3)
                    .orderByDesc(EngineeringRouting::getRoutingId)
                    .last("LIMIT 1"));
            if (routing == null) {
                throw new BusinessException("产品[" + prod.getProductCode() + "]没有已批准的工艺路线，不能提交生产");
            }

            // 4. 创建生产工单
            ProductionOrderCreateDTO dto = new ProductionOrderCreateDTO();
            dto.setOrderNo(redisSequenceService.generateBizNumber(RedisSequenceService.BizCode.WPO));
            dto.setOrderType("WORK_ORDER");
            dto.setSalesOrderId(orderId);
            dto.setSalesOrderNo(order.getOrderNo());
            dto.setProductId(productId);
            dto.setProductCode(product.getProductCode());
            dto.setProductName(product.getProductName());
            dto.setProductSpec(product.getSpecification());
            dto.setProductUnit(product.getUnit() != null ? product.getUnit() : "PCS");
            dto.setBomId(bom.getBomId());
            dto.setBomCode(bom.getBomCode());
            dto.setRoutingId(routing.getRoutingId());
            dto.setRoutingCode(routing.getRoutingCode());
            dto.setPlannedQuantity(product.getQuantity() != null ?
                    java.math.BigDecimal.valueOf(product.getQuantity().longValue()) : java.math.BigDecimal.ONE);
            dto.setPlanStartDate(java.time.LocalDate.now());
            dto.setPlanEndDate(java.time.LocalDate.now().plusDays(7));
            dto.setRemark("由销售订单[" + order.getOrderNo() + "]提交生产生成");
            productionOrderService.createOrder(dto);
        }

        // 更新订单状态为生产中(4)
        order.setOrderStatus(SalesOrderStatusEnum.IN_PRODUCTION.getValue());
        return orderMapper.updateById(order);
    }


    @Override
    public byte[] exportExcel(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        List<SalesOrderProductVO> items = orderProductService.getListByOrderId(orderId);
        return buildOrderExcel(order, items);
    }

    /** 订单 PDF 变量 */

    /** 订单 Excel（单张表单） */
    private byte[] buildOrderExcel(SalesOrder o, List<SalesOrderProductVO> items) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("销售订单");
            int r = 0;
            org.apache.poi.ss.usermodel.Row title = sheet.createRow(r++);
            title.createCell(0).setCellValue("销售订单 " + (o.getOrderNo() == null ? "" : o.getOrderNo()));
            String[][] info = {
                    {"客户名称", o.getCustomerName()}, {"下单日期", o.getOrderDate() == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(o.getOrderDate())},
                    {"联系人", o.getContactPerson()}, {"交货日期", o.getDeliveryDate() == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(o.getDeliveryDate())},
                    {"币种", o.getCurrency() == null ? "CNY" : o.getCurrency()}, {"来源报价", o.getQuotationId() == null ? "-" : String.valueOf(o.getQuotationId())},
                    {"销售负责人", o.getCreateBy() == null ? "-" : o.getCreateBy()}, {"备注", o.getRemark()},
            };
            for (int i = 0; i < info.length; i += 2) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(info[i][0]);
                row.createCell(1).setCellValue(safe(info[i][1]));
                row.createCell(3).setCellValue(info[i + 1][0]);
                row.createCell(4).setCellValue(safe(info[i + 1][1]));
            }
            r++;
            String[] headers = {"序号", "产品编码", "产品名称/规格", "数量", "单位", "单价", "金额"};
            org.apache.poi.ss.usermodel.Row head = sheet.createRow(r++);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
            }
            int idx = 0;
            for (SalesOrderProductVO item : items) {
                String spec = item.getProductName() == null ? "" : item.getProductName();
                if (item.getSpecification() != null && !item.getSpecification().isBlank()) {
                    spec = spec.isBlank() ? item.getSpecification() : spec + " / " + item.getSpecification();
                }
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(++idx);
                row.createCell(1).setCellValue(safe(item.getProductCode()));
                row.createCell(2).setCellValue(spec);
                row.createCell(3).setCellValue(item.getQuantity() == null ? 0 : item.getQuantity().doubleValue());
                row.createCell(4).setCellValue(safe(item.getUnit()));
                row.createCell(5).setCellValue(item.getUnitPrice() == null ? 0 : item.getUnitPrice().doubleValue());
                row.createCell(6).setCellValue(item.getAmount() == null ? 0 : item.getAmount().doubleValue());
            }
            r++;
            String[][] sums = {
                    {"小计(未税)", fmt(o.getTotalAmount(), df)}, {"税率(%)", o.getTaxRate() == null ? "" : df.format(o.getTaxRate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros())},
                    {"税额", fmt(o.getTaxAmount(), df)}, {"折扣", fmt(o.getDiscountAmount(), df)},
                    {"合计(含税)", fmt(o.getFinalAmount(), df)},
            };
            for (String[] s : sums) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r++);
                row.createCell(5).setCellValue(s[0]);
                row.createCell(6).setCellValue(s[1]);
            }
            int[] widths = {6, 14, 36, 10, 8, 14, 16};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            wb.write(os);
            return os.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("订单Excel生成失败: " + e.getMessage());
        }
    }

    /**
     * 导出订单确认书PDF（DEV-343/314：确认声明 + 签字区）
     */
    @Override
    public byte[] exportConfirmationPdf(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        List<SalesOrderProductVO> items = orderProductService.getListByOrderId(orderId);
        DecimalFormat df = new DecimalFormat("#,##0.00");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");

        Map<String, String> info = new LinkedHashMap<>();
        info.put("订单号", order.getOrderNo());
        info.put("下单日期", order.getOrderDate() == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(order.getOrderDate()));
        info.put("客户名称", order.getCustomerName());
        info.put("币种", order.getCurrency() == null ? "CNY" : order.getCurrency());
        info.put("确认人", order.getConfirmBy() == null ? "-" : order.getConfirmBy());
        info.put("确认方式", order.getConfirmMethod() == null ? "-" : order.getConfirmMethod());
        info.put("确认时间", order.getConfirmTime() == null ? "-" : sdf.format(java.sql.Timestamp.valueOf(order.getConfirmTime())));
        info.put("销售负责人", order.getCreateBy() == null ? "-" : order.getCreateBy());

        java.util.List<String[]> rows = new ArrayList<>();
        for (SalesOrderProductVO item : items) {
            String spec = item.getProductName() == null ? "" : item.getProductName();
            if (item.getSpecification() != null && !item.getSpecification().isBlank()) {
                spec = spec.isBlank() ? item.getSpecification() : spec + " / " + item.getSpecification();
            }
            rows.add(new String[]{
                    String.valueOf(rows.size() + 1),
                    item.getProductCode(),
                    spec,
                    item.getQuantity() == null ? "" : String.valueOf(item.getQuantity()),
                    item.getUnit(),
                    item.getUnitPrice() == null ? "" : df.format(item.getUnitPrice()),
                    item.getAmount() == null ? "" : df.format(item.getAmount()),
            });
        }

        PdfDocBuilder builder = PdfDocBuilder.create()
                .withConfig(pdfConfigLoader.load())
                .title("订  单  确  认  书")
                .info(info)
                .items(new String[]{"序号", "产品编码", "产品名称/规格", "数量", "单位", "单价", "金额"}, rows)
                .amounts(new String[][]{
                        {"小计(未税)", fmt(order.getTotalAmount(), df)},
                        {"税额", fmt(order.getTaxAmount(), df)},
                        {"合计(含税)", fmt(order.getFinalAmount(), df)},
                })
                .remark(order.getRemark())
                .signatures("客户签字：", "确认日期：");
        return builder.toBytes();
    }

    private String joinContact(String person, String phone) {
        if (person == null || person.isBlank()) {
            return phone == null ? "" : phone;
        }
        return phone == null || phone.isBlank() ? person : person + " " + phone;
    }

    private String fmt(BigDecimal v, DecimalFormat df) {
        return v == null ? "" : df.format(v);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 导出订单列表
     */
    @Override
    public String exportOrderList(SalesOrder order) {
        // 这里应该实现Excel导出逻辑
        // 暂时返回一个占位符路径
        return "/exports/order-list.xlsx";
    }

    /**
     * 获取订单统计信息
     */
    @Override
    public Object getOrderStatistics() {
        Map<String, Object> stats = new HashMap<>();
        List<SalesOrder> allOrders = orderMapper.selectList(Wrappers.emptyWrapper());
        long totalCount = allOrders.size();
        stats.put("totalCount", totalCount);
        stats.put("totalAmount", allOrders.stream().filter(o -> o.getTotalAmount() != null).mapToDouble(o -> o.getTotalAmount().doubleValue()).sum());
        stats.put("draftCount", allOrders.stream().filter(o -> o.getOrderStatus() != null && o.getOrderStatus() == SalesOrderStatusEnum.DRAFT.getValue()).count());
        stats.put("pendingCount", allOrders.stream().filter(o -> o.getOrderStatus() != null && o.getOrderStatus() == SalesOrderStatusEnum.PENDING_REVIEW.getValue()).count());
        stats.put("approvedCount", allOrders.stream().filter(o -> o.getOrderStatus() != null && o.getOrderStatus() == SalesOrderStatusEnum.APPROVED.getValue()).count());
        stats.put("completedCount", allOrders.stream().filter(o -> o.getOrderStatus() != null && o.getOrderStatus() == SalesOrderStatusEnum.COMPLETED.getValue()).count());
        stats.put("cancelledCount", allOrders.stream().filter(o -> o.getOrderStatus() != null && o.getOrderStatus() == SalesOrderStatusEnum.CANCELLED.getValue()).count());
        return stats;
    }

    @Override
    public OrderReferValidationVO validation(Long orderId) {
        OrderReferValidationVO vo = new OrderReferValidationVO();

        SalesOrder salesOrder = orderMapper.selectById(orderId);
        vo.setOrderId(salesOrder.getOrderId());
        vo.setOrderNo(salesOrder.getOrderNo());
        CustomerVO customer = customerService.selectCustomerById(salesOrder.getCustomerId());
        vo.setCustomerVO(customer);

        List<ProductValidationVO> items = orderProductService.validation(orderId);
        vo.setItems(items);

        // 2026-08-11 增强：汇总校验结果（产品/BOM/工艺路线完整性）
        int errorCount = 0;
        int warningCount = 0;
        if (items != null) {
            for (ProductValidationVO item : items) {
                // 产品未发布 → 错误
                if (item.getProductStatus() == null
                        || !com.jjx.product.enums.ProductEnums.Status.RELEASED.getValue().equals(item.getProductStatus())) {
                    errorCount++;
                    continue;
                }
                // BOM 缺失或未审核 → 错误
                if (item.getBomId() == null || item.getBomStatus() == null
                        || item.getBomStatus() != com.jjx.common.enums.ApproveStatusEnum.APPROVED.getValue()) {
                    errorCount++;
                    continue;
                }
                // 工艺路线缺失或未审核 → 错误
                if (item.getRoutingId() == null || item.getRoutingStatus() == null
                        || item.getRoutingStatus() != com.jjx.common.enums.ApproveStatusEnum.APPROVED.getValue()) {
                    errorCount++;
                    continue;
                }
                // 非当前版本 BOM/工艺路线 → 警告
                if (Boolean.FALSE.equals(item.getIsBomCurrentVersion())
                        || Boolean.FALSE.equals(item.getIsRoutingCurrentVersion())) {
                    warningCount++;
                }
            }
        }
        vo.setErrorCount(errorCount);
        vo.setWarningCount(warningCount);
        vo.setInfoCount(items == null ? 0 : items.size());
        vo.setCanSubmit(errorCount == 0);
        return vo;
    }

    /**
     * 校验订单产品明细（区分样品单与标准单）
     * 样品单(orderType=2): productId 允许为空，productCode 允许自定义
     * 标准单(orderType=1): productId 和 productCode 需要完整
     */
    private void validateOrderItems(List<SalesOrderProductDTO> items, Integer orderType, Long customerId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("产品明细不能为空");
        }

        boolean isSample = Integer.valueOf(2).equals(orderType);

        for (int i = 0; i < items.size(); i++) {
            SalesOrderProductDTO item = items.get(i);

            if (isSample) {
                // 样品单：productId 可为空，但 productCode 必须有
                if (item.getProductId() == null && !StringUtils.hasText(item.getProductCode())) {
                    throw new BusinessException("第" + (i + 1) + "行产品：样品单未关联产品时，产品编码不能为空");
                }
                // 样品单 productCode 为空时自动生成
                if (!StringUtils.hasText(item.getProductCode())) {
                    item.setProductCode("SAMPLE-" + System.currentTimeMillis() % 1000000);
                }
            } else {
                // 标准单：productId 不能为空
                if (item.getProductId() == null) {
                    throw new BusinessException("第" + (i + 1) + "行产品：标准单必须关联产品");
                }
                if (!StringUtils.hasText(item.getProductCode())) {
                    throw new BusinessException("第" + (i + 1) + "行产品：产品编码不能为空");
                }
                // 校验产品是否已发布(RELEASED)
                Product product = productMapper.selectById(item.getProductId());
                if (product == null) {
                    throw new BusinessException("第" + (i + 1) + "行产品：产品不存在");
                }
                if (!ProductEnums.Status.RELEASED.getValue().equals(product.getProductStatus())) {
                    throw new BusinessException("第" + (i + 1) + "行产品：产品[" + product.getProductName() + "]尚未发布，请先发布产品后再下单");
                }
                productCustomerValidator.validateBelongsToCustomer(item.getProductId(), customerId);
            }

            if (!StringUtils.hasText(item.getProductName())) {
                throw new BusinessException("第" + (i + 1) + "行产品：产品名称不能为空");
            }
        }
    }

    /**
     * 确保产品明细都有 productId
     * 样品单中手动输入的产品编码，自动创建产品记录
     */
    private void ensureProductIds(List<SalesOrderProductDTO> items, Integer orderType) {
        boolean isSample = Integer.valueOf(2).equals(orderType);
        if (!isSample) {
            // 标准单的 productId 由 validateOrderItems 保证不为空
            return;
        }

        String currentUser = String.valueOf(cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());

        for (SalesOrderProductDTO item : items) {
            if (item.getProductId() != null) {
                continue;
            }

            // 按 productCode 查找是否已存在
            if (StringUtils.hasText(item.getProductCode())) {
                Product existing = productMapper.selectOne(
                        new LambdaQueryWrapper<Product>()
                                .eq(Product::getProductCode, item.getProductCode().trim())
                );
                if (existing != null) {
                    item.setProductId(existing.getProductId());
                    continue;
                }
            }

            // 不存在则创建新产品
            Product newProduct = new Product();
            newProduct.setProductCode(item.getProductCode());
            newProduct.setProductName(item.getProductName());
            newProduct.setUnit(item.getUnit());
            newProduct.setSpecJson("{\"specification\":\"" + (item.getSpecification() != null ? item.getSpecification() : "") + "\"}");
            newProduct.setProductStatus(1); // 已发布
            newProduct.setCreateBy(currentUser);
            newProduct.setUpdateBy(currentUser);
            productMapper.insert(newProduct);
            item.setProductId(newProduct.getProductId());

            log.info("样品单自动创建产品: code={}, name={}, productId={}",
                    newProduct.getProductCode(), newProduct.getProductName(), newProduct.getProductId());
        }
    }

    /**
     * 验证状态转换是否合法
     */
    private static void validateStatusTransition(Integer currentStatus, Integer newStatus) {
        // 统一使用 SalesOrderStatusEnum 状态机校验（DEV-024 两套枚举统一后）
        SalesOrderStatusEnum current = SalesOrderStatusEnum.getByValueSafe(currentStatus).orElse(null);
        SalesOrderStatusEnum target = SalesOrderStatusEnum.getByValueSafe(newStatus).orElse(null);
        if (current == null || target == null) {
            throw new BusinessException("无效的订单状态：当前=" + currentStatus + " 目标=" + newStatus);
        }
        // 任何状态都可以取消
        if (target == SalesOrderStatusEnum.CANCELLED) {
            return;
        }
        if (!current.canTransitionTo(target)) {
            throw new BusinessException("状态转换不合法：从[" + current.getLabel() + "]转换到[" + target.getLabel() + "]");
        }
    }

    /**
     * 验证订单信息是否完整（用于提交审核）
     */
    private static void validateOrderForReview(SalesOrder order) {
        if (order.getCustomerId() == null) {
            throw new BusinessException("客户信息不能为空");
        }

        if (order.getOrderDate() == null) {
            throw new BusinessException("订单日期不能为空");
        }

        if (order.getDeliveryDate() == null) {
            throw new BusinessException("交货日期不能为空");
        }

        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException("订单金额必须大于0");
        }

        if (order.getFinalAmount() == null || order.getFinalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException("最终金额必须大于0");
        }

        // 这里可以添加更多验证规则
    }
    /**
     * 允许排序的字段白名单（防止 SQL 注入）
     */
    private static final List<String> ALLOWED_SORT_COLUMNS = Arrays.asList(
            "orderId", "orderNo", "orderDate", "deliveryDate",
            "totalAmount", "finalAmount", "createTime", "updateTime"
    );

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<SalesOrder> buildQueryWrapper(SalesOrderQueryDTO queryDTO) {
        LambdaQueryWrapper<SalesOrder> wrapper = new LambdaQueryWrapper<>();

        // 订单编号（精确匹配）
        if (StringUtils.hasText(queryDTO.getOrderNo())) {
            wrapper.eq(SalesOrder::getOrderNo, queryDTO.getOrderNo());
        }

        // 客户ID（精确匹配）
        if (queryDTO.getCustomerId() != null) {
            wrapper.eq(SalesOrder::getCustomerId, queryDTO.getCustomerId());
        }

        // 客户名称（模糊查询）
        if (StringUtils.hasText(queryDTO.getCustomerName())) {
            wrapper.like(SalesOrder::getCustomerName, queryDTO.getCustomerName());
        }

        // 订单类型
        if (queryDTO.getOrderType() != null) {
            wrapper.eq(SalesOrder::getOrderType, queryDTO.getOrderType());
        }

        // 订单状态
        if (queryDTO.getOrderStatus() != null) {
            wrapper.eq(SalesOrder::getOrderStatus, queryDTO.getOrderStatus());
        }

        // 生产状态
        if (queryDTO.getProdStatus() != null) {
            wrapper.eq(SalesOrder::getProdStatus, queryDTO.getProdStatus());
        }

        // 支付状态
        if (queryDTO.getPaymentStatus() != null) {
            wrapper.eq(SalesOrder::getPaymentStatus, queryDTO.getPaymentStatus());
        }

        // 是否急单
        if (queryDTO.getIsUrgent() != null) {
            wrapper.eq(SalesOrder::getIsUrgent, queryDTO.getIsUrgent());
        }

        // 销售负责人ID
        if (queryDTO.getSalesManagerId() != null) {
            wrapper.eq(SalesOrder::getSalesManagerId, queryDTO.getSalesManagerId());
        }

        // 订单日期范围查询
        if (queryDTO.getOrderDateStart() != null) {
            wrapper.ge(SalesOrder::getOrderDate, queryDTO.getOrderDateStart());
        }
        if (queryDTO.getOrderDateEnd() != null) {
            wrapper.le(SalesOrder::getOrderDate, queryDTO.getOrderDateEnd());
        }

        // 交货日期范围查询
        if (queryDTO.getDeliveryDateStart() != null) {
            wrapper.ge(SalesOrder::getDeliveryDate, queryDTO.getDeliveryDateStart());
        }
        if (queryDTO.getDeliveryDateEnd() != null) {
            wrapper.le(SalesOrder::getDeliveryDate, queryDTO.getDeliveryDateEnd());
        }

        // 创建时间范围查询
        if (queryDTO.getCreateTimeStart() != null) {
            wrapper.ge(SalesOrder::getCreateTime, queryDTO.getCreateTimeStart());
        }
        if (queryDTO.getCreateTimeEnd() != null) {
            wrapper.le(SalesOrder::getCreateTime, queryDTO.getCreateTimeEnd());
        }

        // ========== 动态排序 ==========
        applySorting(wrapper, queryDTO);

        return wrapper;
    }
    /**
     * 应用排序条件（带 SQL 注入防护）
     */
    private static void applySorting(LambdaQueryWrapper<SalesOrder> wrapper, SalesOrderQueryDTO queryDTO) {
        String orderByColumn = queryDTO.getOrderByColumn();
        String isAsc = queryDTO.getIsAsc();

        // 如果排序字段为空，使用默认排序（createTime 降序 + 主键降序保证分页稳定）
        if (!StringUtils.hasText(orderByColumn)) {
            wrapper.orderByDesc(SalesOrder::getCreateTime).orderByDesc(SalesOrder::getOrderId);
            return;
        }

        // 白名单校验，防止 SQL 注入
        if (!ALLOWED_SORT_COLUMNS.contains(orderByColumn)) {
            // 非法排序字段，使用默认排序
            wrapper.orderByDesc(SalesOrder::getCreateTime).orderByDesc(SalesOrder::getOrderId);
            return;
        }

        // 判断排序方式
        boolean isAscending = "asc".equalsIgnoreCase(isAsc);

        // 根据排序字段动态排序
        switch (orderByColumn) {
            case "orderId":
                if (isAscending) {
                    wrapper.orderByAsc(SalesOrder::getOrderId);
                } else {
                    wrapper.orderByDesc(SalesOrder::getOrderId);
                }
                break;
            case "orderNo":
                if (isAscending) {
                    wrapper.orderByAsc(SalesOrder::getOrderNo);
                } else {
                    wrapper.orderByDesc(SalesOrder::getOrderNo);
                }
                break;
            case "orderDate":
                if (isAscending) {
                    wrapper.orderByAsc(SalesOrder::getOrderDate);
                } else {
                    wrapper.orderByDesc(SalesOrder::getOrderDate);
                }
                break;
            case "deliveryDate":
                if (isAscending) {
                    wrapper.orderByAsc(SalesOrder::getDeliveryDate);
                } else {
                    wrapper.orderByDesc(SalesOrder::getDeliveryDate);
                }
                break;
            case "totalAmount":
                if (isAscending) {
                    wrapper.orderByAsc(SalesOrder::getTotalAmount);
                } else {
                    wrapper.orderByDesc(SalesOrder::getTotalAmount);
                }
                break;
            case "finalAmount":
                if (isAscending) {
                    wrapper.orderByAsc(SalesOrder::getFinalAmount);
                } else {
                    wrapper.orderByDesc(SalesOrder::getFinalAmount);
                }
                break;
            case "createTime":
                if (isAscending) {
                    wrapper.orderByAsc(SalesOrder::getCreateTime).orderByAsc(SalesOrder::getOrderId);
                } else {
                    wrapper.orderByDesc(SalesOrder::getCreateTime).orderByDesc(SalesOrder::getOrderId);
                }
                break;
            case "updateTime":
                if (isAscending) {
                    wrapper.orderByAsc(SalesOrder::getUpdateTime);
                } else {
                    wrapper.orderByDesc(SalesOrder::getUpdateTime);
                }
                break;
            default:
                // 默认按创建时间降序（+主键稳定）
                wrapper.orderByDesc(SalesOrder::getCreateTime).orderByDesc(SalesOrder::getOrderId);
                break;
        }
    }
}
