package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.ProductValidationVO;
import com.jjx.product.mapper.ProductMapper;
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
import com.jjx.sales.enums.OrderStatus;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.service.ICustomerService;
import com.jjx.sales.service.IOrderService;
import com.jjx.sales.service.ISalesOrderProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

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
    private final ISalesOrderProductService orderProductService;
    private final ICustomerService customerService;
    private final ProductMapper productMapper;

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
        return PageResult.build(voList, orderPage.getTotal());
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
        int insert = orderMapper.insert(entity);
        if(insert>0){
            // 校验并处理产品明细
            validateOrderItems(dto.getItems(), dto.getOrderType());
            ensureProductIds(dto.getItems(), dto.getOrderType());
            dto.getItems().forEach(i -> i.setOrderId(entity.getOrderId()));
            orderProductService.batchAdd(dto.getItems());
            return entity.getOrderId();
        }
        throw new BusinessException(BusinessExceptionEnum.DB_INSERT_FAILED);
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
        SalesOrder entity = orderConverter.toEntity(dto);

        int insert = orderMapper.updateById(entity);
        orderProductService.deleteByOrderId(dto.getOrderId());
        if(insert>0){
            // 校验并处理产品明细
            validateOrderItems(dto.getItems(), dto.getOrderType());
            ensureProductIds(dto.getItems(), dto.getOrderType());
            return orderProductService.batchAdd(dto.getItems());
        }
        throw new BusinessException(BusinessExceptionEnum.DB_UPDATE_FAILED);
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

        // 检查订单状态，已确认或生产中的订单不能删除
        if (order.getOrderStatus() >= 2) {
            throw new BusinessException("已确认或生产中的订单不能删除");
        }

        // 使用逻辑删除
        SalesOrder deleteOrder = new SalesOrder();
        deleteOrder.setOrderId(orderId);
        deleteOrder.setDeleted(1);
        return orderMapper.updateById(deleteOrder);
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
     * 生成订单号
     */
    @Override
    public String generateOrderNo() {
        // 使用Redis序列号服务生成订单号
        return redisSequenceService.generateSalesOrderNumber();
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
        if (order.getOrderStatus() != 1) {
            throw new BusinessException("只有草稿状态的订单可以审核");
        }

        // 更新审核信息
        order.setOrderStatus(2); // 已确认状态

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
        if (order.getOrderStatus() != 1) {
            throw new BusinessException("只有草稿状态的订单可以提交审核");
        }

        // 检查订单信息是否完整
        validateOrderForReview(order);

        // 更新状态为待审核（这里假设状态2是待审核）
        return orderMapper.updateOrderStatus(orderId, OrderStatus.PENDING_REVIEW.getCode());
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
        if (order.getOrderStatus() != 2) {
            throw new BusinessException("只有已审核的订单可以客户确认");
        }

        // 更新状态为已确认
        order.setOrderStatus(3);
        // 这里可以添加确认人信息到备注中
        String newRemark = order.getRemark() + "\n客户确认人：" + confirmedBy + "，确认时间：" + LocalDateTime.now();
        order.setRemark(newRemark);

        return orderMapper.updateById(order);
    }

    /**
     * 创建产品实例
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createInstances(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 只有已确认的订单可以创建产品实例
        if (order.getOrderStatus() != 3) {
            throw new BusinessException("只有已确认的订单可以创建产品实例");
        }

        // 这里应该调用生产模块的接口创建产品实例
        // 暂时只更新订单状态为生产中
        order.setOrderStatus(4);

        return orderMapper.updateById(order);
    }

    /**
     * 导出PDF
     */
    @Override
    public String exportPdf(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 这里应该实现PDF导出逻辑
        // 暂时返回一个占位符路径
        return "/exports/orders/" + order.getOrderNo() + ".pdf";
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
        // 这里应该实现订单统计逻辑
        // 暂时返回空对象
        return new Object();
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
        return vo;
    }

    /**
     * 校验订单产品明细（区分样品单与标准单）
     * 样品单(orderType=2): productId 允许为空，productCode 允许自定义
     * 标准单(orderType=1): productId 和 productCode 需要完整
     */
    private void validateOrderItems(List<SalesOrderProductDTO> items, Integer orderType) {
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
        // 状态转换规则：
        // 1: 草稿 -> 2: 已确认
        // 2: 已确认 -> 3: 生产中
        // 3: 生产中 -> 4: 已发货
        // 4: 已发货 -> 5: 已完成
        // 任何状态 -> 6: 已取消

        if (newStatus == 6) {
            // 任何状态都可以取消
            return;
        }

        if (currentStatus == 1 && newStatus == 2) {
            return; // 草稿 -> 已确认
        }

        if (currentStatus == 2 && newStatus == 3) {
            return; // 已确认 -> 生产中
        }

        if (currentStatus == 3 && newStatus == 4) {
            return; // 生产中 -> 已发货
        }

        if (currentStatus == 4 && newStatus == 5) {
            return; // 已发货 -> 已完成
        }

        throw new BusinessException("状态转换不合法：从状态" + currentStatus + "转换到状态" + newStatus);
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

        // 如果排序字段为空，使用默认排序
        if (!StringUtils.hasText(orderByColumn)) {
            wrapper.orderByDesc(SalesOrder::getCreateTime);
            return;
        }

        // 白名单校验，防止 SQL 注入
        if (!ALLOWED_SORT_COLUMNS.contains(orderByColumn)) {
            // 非法排序字段，使用默认排序
            wrapper.orderByDesc(SalesOrder::getCreateTime);
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
                    wrapper.orderByAsc(SalesOrder::getCreateTime);
                } else {
                    wrapper.orderByDesc(SalesOrder::getCreateTime);
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
                // 默认按创建时间降序
                wrapper.orderByDesc(SalesOrder::getCreateTime);
                break;
        }
    }
}
