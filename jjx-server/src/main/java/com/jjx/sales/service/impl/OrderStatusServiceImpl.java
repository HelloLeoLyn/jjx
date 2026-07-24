package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.production.domain.dto.ProductionOrderCreateDTO;
import com.jjx.production.service.ProductionOrderService;
import com.jjx.product.domain.entity.ProductBom;
import com.jjx.product.mapper.ProductBomMapper;
import com.jjx.sales.domain.dto.ODRSendToCustomerDTO;
import com.jjx.sales.domain.dto.ReviewDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import com.jjx.sales.domain.vo.ReviewHistoryVO;
import com.jjx.sales.domain.vo.ReviewStatusVO;
import com.jjx.sales.enums.OperationResultEnum;
import com.jjx.sales.enums.OperationTypeEnum;
import com.jjx.sales.enums.OrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import com.jjx.sales.service.IOrderStatusService;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.sales.service.SalesLogService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusServiceImpl implements IOrderStatusService {

    private final OrderMapper salesOrderMapper;
    private final SalesLogService salesLogService;
    private final ISalesOrderProductService orderProductService;
    private final ProductionOrderService productionOrderService;
    private final SalesOrderProductMapper salesOrderProductMapper;
    private final RedisSequenceService redisSequenceService;
    private final ProductBomMapper productBomMapper;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long orderId) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        if(!orderProductService.isExists(orderId)){
            throw new BusinessException("订单产品不存在");
        }

        // 1.5 检查负责人权限（超级管理员除外）
        Long currentUserId = SecurityUtils.getUserId();
        if (order.getSalesManagerId() != null && !order.getSalesManagerId().equals(currentUserId)) {
            if (!SecurityUtils.hasPermission("*:*:*")) {
                throw new BusinessException("只能提交本人负责的订单");
            }
        }

        // 2. 检查状态是否可提交审核
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (!currentStatus.isSubmittable()) {
            throw new BusinessException("当前状态不可提交审核，当前状态：" + currentStatus.getName());
        }

        // 3. 更新状态
        final OrderStatusEnum targetStatus = OrderStatusEnum.PENDING_REVIEW;
        order.setOrderStatus(targetStatus.getCode());


        // 4. 保存
        int result = salesOrderMapper.updateStatusWithCheck(
            orderId, targetStatus.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }

        // 5. 记录成功日志
        String desc = getOperationDescription(currentStatus,targetStatus);
        salesLogService.log(orderId,order.getOrderNo(), OperationTypeEnum.SUBMIT_REVIEW,desc,"", OperationResultEnum.SUCCESS);
        log.info("订单{}提交审核，操作人：{}", orderId, SecurityUtils.getUsername());
    }
    private static String getOperationDescription(OrderStatusEnum current, OrderStatusEnum target){
        return String.format("%s => %s (%d -> %d)",
                current.getName(), target.getName(), current.getCode(), target.getCode());
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startReview(Long orderId) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 检查状态是否为待审核
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (currentStatus != OrderStatusEnum.PENDING_REVIEW) {
            throw new BusinessException("只有待审核状态的订单才能开始审核，当前状态：" + currentStatus.getName());
        }

        // 3. 检查审核权限
        if (!SecurityUtils.hasPermission("order:status:review")) {
            throw new BusinessException("无审核权限");
        }

        // 4. 更新状态
        final OrderStatusEnum targetStatus = OrderStatusEnum.REVIEWING;
        order.setOrderStatus(targetStatus.getCode());

        // 5. 保存
        int result = salesOrderMapper.updateStatusWithCheck(
            orderId, targetStatus.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }

        // 6. 记录日志
        String desc = getOperationDescription(currentStatus,targetStatus);
        salesLogService.log(orderId,order.getOrderNo(), OperationTypeEnum.START_REVIEW,desc,"", OperationResultEnum.SUCCESS);
        log.info("订单{}开始审核，审核人：{}", orderId, SecurityUtils.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveOrder(ReviewDTO reviewDTO) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(reviewDTO.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 检查状态是否为审核中
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (currentStatus != OrderStatusEnum.REVIEWING) {
            throw new BusinessException("只有审核中的订单才能审核通过，当前状态：" + currentStatus.getName());
        }

        // 4. 更新状态
        final OrderStatusEnum targetStatus = OrderStatusEnum.APPROVED;
        order.setOrderStatus(targetStatus.getCode());


        // 5. 保存
        int result = salesOrderMapper.updateStatusWithCheck(
            reviewDTO.getOrderId(), targetStatus.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }

        // 6. 记录日志
        String desc = getOperationDescription(currentStatus,targetStatus);
        salesLogService.log(order.getOrderId(),order.getOrderNo(), OperationTypeEnum.APPROVE,desc,reviewDTO.getRemark(), OperationResultEnum.SUCCESS);
        log.info("订单{}审核通过，审核人：{}", reviewDTO.getOrderId(), SecurityUtils.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectOrder(ReviewDTO reviewDTO) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(reviewDTO.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 检查状态是否为审核中
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (currentStatus != OrderStatusEnum.REVIEWING) {
            throw new BusinessException("只有审核中的订单才能审核驳回，当前状态：" + currentStatus.getName());
        }


        // 4. 检查驳回原因
        if (reviewDTO.getRemark() == null || reviewDTO.getRemark().trim().isEmpty()) {
            throw new BusinessException("驳回时必须填写驳回原因");
        }

        // 5. 更新状态
        final OrderStatusEnum targetStatus = OrderStatusEnum.REJECTED;
        order.setOrderStatus(targetStatus.getCode());

        // 6. 保存
        int result = salesOrderMapper.updateStatusWithCheck(
            reviewDTO.getOrderId(), targetStatus.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }

        // 7. 记录日志
        String desc = getOperationDescription(currentStatus,targetStatus);
        salesLogService.log(order.getOrderId(),order.getOrderNo(), OperationTypeEnum.REJECT,desc,reviewDTO.getRemark(), OperationResultEnum.SUCCESS);
        log.info("订单{}审核驳回，审核人：{}，原因：{}",
                 reviewDTO.getOrderId(), SecurityUtils.getUsername(), reviewDTO.getRemark());
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long orderId) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 1.5 检查负责人权限（超级管理员除外）
        Long currentUserId = SecurityUtils.getUserId();
        if (order.getSalesManagerId() != null && !order.getSalesManagerId().equals(currentUserId)) {
            if (!SecurityUtils.hasPermission("*:*:*")) {
                throw new BusinessException("只能重新提交本人负责的订单");
            }
        }

        // 2. 检查状态是否为已驳回
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (currentStatus != OrderStatusEnum.REJECTED) {
            throw new BusinessException("只有已驳回的订单才能重新提交");
        }

        // 3. 更新状态
        final OrderStatusEnum targetStatus = OrderStatusEnum.PENDING_REVIEW;
        order.setOrderStatus(targetStatus.getCode());

        // 4. 保存
        int result = salesOrderMapper.updateStatusWithCheck(
            orderId, targetStatus.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }

        // 5. 记录日志
        String desc = getOperationDescription(currentStatus,targetStatus);
        salesLogService.log(order.getOrderId(),order.getOrderNo(), OperationTypeEnum.SUBMIT_REVIEW,desc,"重新提交审核", OperationResultEnum.SUCCESS);
        log.info("订单{}重新提交审核，操作人：{}", orderId, SecurityUtils.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, String reason) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 1.5 检查负责人权限（超级管理员除外）
        Long currentUserId = SecurityUtils.getUserId();
        if (order.getSalesManagerId() != null && !order.getSalesManagerId().equals(currentUserId)) {
            if (!SecurityUtils.hasPermission("*:*:*")) {
                throw new BusinessException("只能取消本人负责的订单");
            }
        }

        // 2. 检查是否可取消
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (currentStatus.isTerminal()) {
            throw new BusinessException("订单已完成或已取消，无法再次取消");
        }

        // 3. 更新状态
        final OrderStatusEnum targetStatus = OrderStatusEnum.CANCELLED;
        order.setOrderStatus(targetStatus.getCode());

        // 4. 保存
        int result = salesOrderMapper.updateStatusWithCheck(
            orderId, targetStatus.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }

        // 5. 记录日志
        String desc = getOperationDescription(currentStatus,targetStatus);
        salesLogService.log(order.getOrderId(),order.getOrderNo(), OperationTypeEnum.CANCEL,desc,"", OperationResultEnum.SUCCESS);
        log.info("订单{}已取消，操作人：{}，原因：{}", orderId, SecurityUtils.getUsername(), reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendToCustomer(ODRSendToCustomerDTO dto) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 检查是否可取消
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (!currentStatus.canTransitionTo(OrderStatusEnum.CONFIRMED)) {
            throw new BusinessException("订单不是"+currentStatus.getName()+"，无法发送客户确认");
        }

        // 3. 更新状态
        final OrderStatusEnum targetStatus = OrderStatusEnum.CONFIRMED;
        order.setOrderStatus(targetStatus.getCode());

        // 4. 保存
        int result = salesOrderMapper.updateStatusWithCheck(
                order.getOrderId(), targetStatus.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }

        // 5. 记录日志
        String desc = getOperationDescription(currentStatus,targetStatus);
        salesLogService.log(order.getOrderId(),order.getOrderNo(), OperationTypeEnum.CANCEL,desc,"", OperationResultEnum.SUCCESS);
        log.info("订单{}已发送客户确认，操作人：{}，原因：{}", order.getOrderId(), SecurityUtils.getUsername(), "");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startProduction(Long orderId) {
        // 1. 查询销售订单
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 检查状态是否为已确认
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (currentStatus != OrderStatusEnum.CONFIRMED) {
            throw new BusinessException("只有已确认的订单才能开始生产，当前状态：" + currentStatus.getName());
        }

        // 3. 检查订单产品是否存在
        if (!orderProductService.isExists(orderId)) {
            throw new BusinessException("订单产品不存在，无法开始生产");
        }

        // 4. 查询订单产品明细
        List<SalesOrderProduct> products = salesOrderProductMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SalesOrderProduct>()
                        .eq(SalesOrderProduct::getOrderId, orderId)
        );

        // 5. 检查每个产品是否有BOM（提醒但不阻止）
        for (SalesOrderProduct product : products) {
            if (product.getProductId() == null) {
                log.warn("订单{}产品{}无productId（样品单），跳过BOM检查", orderId, product.getProductCode());
                continue;
            }
            long bomCount = productBomMapper.selectCount(
                    new LambdaQueryWrapper<ProductBom>()
                            .eq(ProductBom::getProductId, product.getProductId())
                            .eq(ProductBom::getIsCurrent, 1)
            );
            if (bomCount == 0) {
                log.warn("⚠️ 产品[{}] {} 没有当前生效的BOM，生产时将无法自动计算材料需求",
                        product.getProductCode(), product.getProductName());
            }
        }

        // 6. 为每个产品创建生产工单
        for (SalesOrderProduct product : products) {
            ProductionOrderCreateDTO createDTO = new ProductionOrderCreateDTO();
            createDTO.setOrderType("WORK_ORDER");
            createDTO.setSalesOrderId(orderId);
            createDTO.setSalesOrderNo(order.getOrderNo());
            createDTO.setProductId(product.getProductId());
            createDTO.setProductCode(product.getProductCode());
            createDTO.setProductName(product.getProductName());
            createDTO.setProductSpec("");
            createDTO.setProductUnit(product.getUnit());
            createDTO.setPlannedQuantity(BigDecimal.valueOf(product.getQuantity()));
            // 计划开始日期使用销售订单的交货日期作为参考，默认从当天开始
            createDTO.setPlanStartDate(LocalDate.now());
            // 计划结束日期使用销售订单的交货日期
            if (order.getDeliveryDate() != null) {
                createDTO.setPlanEndDate(order.getDeliveryDate().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate());
            } else {
                createDTO.setPlanEndDate(LocalDate.now().plusDays(7));
            }
            // 优先级：急单设为HIGH，否则为MEDIUM
            createDTO.setPriority(order.getIsUrgent() != null && order.getIsUrgent() == 1 ? "HIGH" : "MEDIUM");
            createDTO.setRemark("由销售订单[" + order.getOrderNo() + "]自动生成");
            createDTO.setOrderNo(redisSequenceService.generateBusinessNumber("WO","YYMMDD"));
            // 调用生产模块创建生产工单
            Long productionOrderId = productionOrderService.createOrder(createDTO);
            log.info("为销售订单{}创建生产工单{}，产品：{}，数量：{}",
                    orderId, productionOrderId, product.getProductName(), product.getQuantity());
        }

        // 6. 更新销售订单状态为生产中
        final OrderStatusEnum targetStatus = OrderStatusEnum.IN_PRODUCTION;
        int result = salesOrderMapper.updateStatusWithCheck(
                orderId, targetStatus.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }

        // 7. 更新销售订单的生产状态为"全部生产中"
        SalesOrder updateOrder = new SalesOrder();
        updateOrder.setOrderId(orderId);
        updateOrder.setProdStatus(3); // 3=全部生产中
        salesOrderMapper.updateById(updateOrder);

        // 8. 记录日志
        String desc = getOperationDescription(currentStatus, targetStatus);
        salesLogService.log(orderId, order.getOrderNo(), OperationTypeEnum.START_PRODUCTION,
                desc, "开始生产，共创建" + products.size() + "个生产工单", OperationResultEnum.SUCCESS);
        log.info("订单{}开始生产，操作人：{}，创建生产工单数：{}",
                orderId, SecurityUtils.getUsername(), products.size());
    }

    @Override
    public ReviewStatusVO getReviewStatus(Long orderId) {
        return null;
    }

    @Override
    public List<ReviewHistoryVO> getReviewHistory(Long orderId) {
        return List.of();
    }


}
