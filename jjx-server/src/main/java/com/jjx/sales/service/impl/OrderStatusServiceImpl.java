package com.jjx.sales.service.impl;
import com.jjx.engineering.domain.entity.EngineeringRouting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.production.domain.dto.ProductionOrderCreateDTO;
import com.jjx.production.service.ProductionOrderService;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.EngineeringRoutingMapper;
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
import com.jjx.system.annotation.Event;
import com.jjx.sales.enums.OperationResultEnum;
import com.jjx.sales.enums.OperationTypeEnum;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.system.annotation.Event;
import com.jjx.system.service.ReviewFlowService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusServiceImpl implements IOrderStatusService {

    private final OrderMapper salesOrderMapper;
    private final ISalesOrderProductService orderProductService;
    private final ProductionOrderService productionOrderService;
    private final SalesOrderProductMapper salesOrderProductMapper;
    private final RedisSequenceService redisSequenceService;
    private final EngineeringBomMapper productBomMapper;
    private final ProductMapper productMapper;
    private final EngineeringRoutingMapper productRoutingMapper;
    private final EventPublisher eventPublisher;
    private final com.jjx.inventory.service.InventoryAlertService inventoryAlertService;
    private final com.jjx.inventory.service.OrderStockReserveService orderStockReserveService;
    private final com.jjx.inventory.service.OrderMaterialReserveService orderMaterialReserveService;
    private final ReviewFlowService reviewFlowService;
    
    @Event("order.submitted")
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
        reviewFlowService.record("sales_order", orderId, "SUBMIT", "提交审核",
                currentStatus.getCode(), targetStatus.getCode(), null, null);

        log.info("订单{}提交审核，操作人：{}", orderId, SecurityUtils.getUsername());
    }
    @Override
    @Event(value = "order.review_started", bizId = "#orderId", bizType = "'order'")
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

        // 3. 更新状态（接口层已校验 sales:order:review，2026-08-18 移除幽灵权限码 order:status:review）
        final OrderStatusEnum targetStatus = OrderStatusEnum.REVIEWING;
        order.setOrderStatus(targetStatus.getCode());

        // 4. 保存
        int result = salesOrderMapper.updateStatusWithCheck(
            orderId, targetStatus.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }
        log.info("订单{}开始审核，审核人：{}", orderId, SecurityUtils.getUsername());
    }

    @Override
    @Event(value = "order.approved", bizId = "#reviewDTO.orderId", bizType = "'order'")
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
        reviewFlowService.record("sales_order", reviewDTO.getOrderId(), "APPROVE", "审核通过",
                currentStatus.getCode(), targetStatus.getCode(), reviewDTO.getRemark(), reviewDTO.getAttachments());

        // 7. 审核通过联动（原客户确认环节的步骤前移到审核通过，2026-08-12 去掉客户确认后）
        // 齐套检查（DEV-572）：按 BOM 算料，缺口生成 order_shortage 预警
        try {
            inventoryAlertService.checkOrderShortage(order.getOrderId());
        } catch (Exception e) {
            log.error("订单{}审核通过后齐套检查异常（不影响主流程）: {}", order.getOrderId(), e.getMessage());
        }
        // 全局汇总缺料检查（物料维度 demand_shortage 预警）
        try {
            inventoryAlertService.checkGlobalShortage();
        } catch (Exception e) {
            log.error("订单{}审核通过后全局缺料检查异常（不影响主流程）: {}", order.getOrderId(), e.getMessage());
        }
        // 成品库存预留（DEV-578）：库存部分预留，缺货部分进生产
        try {
            orderStockReserveService.reserveForOrder(order.getOrderId());
        } catch (Exception e) {
            log.error("订单{}审核通过后成品库存预留异常（不影响主流程）: {}", order.getOrderId(), e.getMessage());
        }
        // 原料占用（036定稿）：预占转正式占用，防多订单合计超卖
        try {
            orderMaterialReserveService.confirmReserve(order.getOrderId());
        } catch (Exception e) {
            log.error("订单{}审核通过后原料占用异常（不影响主流程）: {}", order.getOrderId(), e.getMessage());
        }

        log.info("订单{}审核通过，审核人：{}", reviewDTO.getOrderId(), SecurityUtils.getUsername());
    }

    @Override
    @Event(value = "order.rejected", bizId = "#reviewDTO.orderId", bizType = "'order'")
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
        reviewFlowService.record("sales_order", reviewDTO.getOrderId(), "REJECT", "审核驳回",
                currentStatus.getCode(), targetStatus.getCode(), reviewDTO.getRemark(), reviewDTO.getAttachments());

        log.info("订单{}审核驳回，审核人：{}，原因：{}",
                 reviewDTO.getOrderId(), SecurityUtils.getUsername(), reviewDTO.getRemark());
    }


    @Override
    @Event(value = "order.resubmitted", bizId = "#orderId", bizType = "'order'")
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
        reviewFlowService.record("sales_order", orderId, "SUBMIT", "重新提交审核",
                currentStatus.getCode(), targetStatus.getCode(), null, null);

        log.info("订单{}重新提交审核，操作人：{}", orderId, SecurityUtils.getUsername());
    }

    @Override
    @Event(value = "order.cancelled", bizId = "#orderId", bizType = "'order'")
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

        // 6. 联动取消关联的生产工单（跳过已完成/已取消/已关闭）
        int[] result2 = productionOrderService.cancelBySalesOrderId(orderId);
        int cancelled = result2[0];
        int skipped = result2[1];
        if (cancelled > 0 || skipped > 0) {
            log.info("订单{}取消联动：取消{}个生产工单，跳过{}个", orderId, cancelled, skipped);
        }

        // 7. 释放成品库存预留（DEV-578）：订单取消 → 预留释放
        try {
            orderStockReserveService.releaseByOrder(orderId);
        } catch (Exception e) {
            log.error("订单{}释放成品库存预留异常（不影响取消主流程）: {}", orderId, e.getMessage());
        }
        // 7.1 094：订单取消 → 释放材料预占
        try {
            orderMaterialReserveService.releaseByOrder(orderId, "订单取消释放材料预占");
        } catch (Exception e) {
            log.error("订单{}释放材料预占异常（不影响取消主流程）: {}", orderId, e.getMessage());
        }

        log.info("订单{}已取消，操作人：{}，原因：{}", orderId, SecurityUtils.getUsername(), reason);
    }

    @Override
    @Event(value = "order.sent_to_customer", bizId = "#dto.orderId", bizType = "'order'")
    @Transactional(rollbackFor = Exception.class)
    public void sendToCustomer(ODRSendToCustomerDTO dto) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 检查是否可发送（DEV-935 2026-08-12：仅已审核可发送，状态保持已审核，由 confirm 完成确认 4→6）
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (currentStatus != OrderStatusEnum.APPROVED) {
            throw new BusinessException("订单不是已审核状态（" + currentStatus.getName() + "），无法发送客户确认");
        }

        // 3. 重新发送：清空上次确认记录，记录本次发送时间（2026-08-12 发送标识）
        order.setConfirmBy(null);
        order.setConfirmMethod(null);
        order.setConfirmTime(null);
        order.setConfirmSentTime(java.time.LocalDateTime.now());
        salesOrderMapper.updateById(order);

        log.info("订单{}已发送客户确认，操作人：{}，原因：{}", order.getOrderId(), SecurityUtils.getUsername(), "");

        // 6. 订单齐套检查（DEV-572 8-04）：按 BOM 算料，缺口生成 order_shortage 预警
        try {
            inventoryAlertService.checkOrderShortage(order.getOrderId());
        } catch (Exception e) {
            log.error("订单{}齐套检查异常（不影响确认主流程）: {}", order.getOrderId(), e.getMessage());
        }
        // 7. 082定稿：订单确认时同步触发全局汇总缺料检查（物料维度 demand_shortage 预警，防忘预占的兜底）
        try {
            inventoryAlertService.checkGlobalShortage();
        } catch (Exception e) {
            log.error("订单{}确认后全局缺料检查异常（不影响确认主流程）: {}", order.getOrderId(), e.getMessage());
        }

        // 7. 成品库存预留（DEV-578 8-04）：确认时检查成品可用库存，库存部分预留，缺货部分进生产
        try {
            orderStockReserveService.reserveForOrder(order.getOrderId());
        } catch (Exception e) {
            log.error("订单{}成品库存预留异常（不影响确认主流程）: {}", order.getOrderId(), e.getMessage());
        }
        // 7.1 036定稿：订单确认时原料占用——预占转正式占用（无预占则自动按BOM占用，防多订单合计超卖）
        try {
            orderMaterialReserveService.confirmReserve(order.getOrderId());
        } catch (Exception e) {
            log.error("订单{}确认原料占用异常（不影响确认主流程）: {}", order.getOrderId(), e.getMessage());
        }
    }

    /**
     * 校验订单产品 BOM/工艺路线（生成生产计划用，2026-08-11 抽取）
     */
    private void checkBomAndRouting(List<SalesOrderProduct> products) {
        for (SalesOrderProduct product : products) {
            if (product.getProductId() == null) {
                log.warn("订单产品{}无productId（样品单），跳过BOM检查", product.getProductCode());
                continue;
            }
            long bomCount = productBomMapper.selectCount(
                    new LambdaQueryWrapper<EngineeringBom>()
                            .eq(EngineeringBom::getProductId, product.getProductId())
                            .eq(EngineeringBom::getIsCurrent, 1)
                            .eq(EngineeringBom::getApproveStatus, 3)
            );
            if (bomCount == 0) {
                long draftBomCount = productBomMapper.selectCount(
                        new LambdaQueryWrapper<EngineeringBom>()
                                .eq(EngineeringBom::getProductId, product.getProductId())
                                .eq(EngineeringBom::getIsCurrent, 1)
                );
                if (draftBomCount > 0) {
                    throw new BusinessException("产品[" + product.getProductCode() + "] " + product.getProductName() + " 当前BOM尚未审批通过，请先完成BOM审批");
                } else {
                    throw new BusinessException("产品[" + product.getProductCode() + "] " + product.getProductName() + " 没有当前生效的BOM，请先配置并审批BOM");
                }
            }
            long routeCount = productRoutingMapper.selectCount(
                    new LambdaQueryWrapper<EngineeringRouting>()
                            .eq(EngineeringRouting::getProductId, product.getProductId())
                            .eq(EngineeringRouting::getIsCurrent, 1)
                            .eq(EngineeringRouting::getApproveStatus, 3)
            );
            if (routeCount == 0) {
                long draftRouteCount = productRoutingMapper.selectCount(
                        new LambdaQueryWrapper<EngineeringRouting>()
                                .eq(EngineeringRouting::getProductId, product.getProductId())
                                .eq(EngineeringRouting::getIsCurrent, 1)
                );
                if (draftRouteCount > 0) {
                    throw new BusinessException("产品[" + product.getProductCode() + "] " + product.getProductName() + " 当前工艺路线尚未审批通过，请先完成路线审批");
                } else {
                    throw new BusinessException("产品[" + product.getProductCode() + "] " + product.getProductName() + " 没有当前工艺路线，请先配置并审批工艺路线");
                }
            }
        }
    }

    /**
     * 生成生产计划（2026-08-13：生成计划=确认动作，SO 已审核(4)→已确认(6)，写确认人/方式/时间）
     * 每个产品生成一张 PLAN（数量=订单需求全量），自动进入待审批，待工单启动时才置生产中(7)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createProductionPlan(Long orderId) {
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        // 2026-08-13：已审核(4)生成计划即确认；已确认(6)兼容历史订单补生成
        if (currentStatus != OrderStatusEnum.APPROVED && currentStatus != OrderStatusEnum.CONFIRMED) {
            throw new BusinessException("只有已审核/已确认的订单才能生成生产计划，当前状态：" + currentStatus.getName());
        }
        // 防重复生成：同一订单已有未关闭的 PLAN 则拦截
        if (productionOrderService.countActivePlanBySalesOrderId(orderId) > 0) {
            throw new BusinessException("该订单已生成过生产计划，请勿重复生成（可在计划视图查看/转工单）");
        }
        if (!orderProductService.isExists(orderId)) {
            throw new BusinessException("订单产品不存在，无法生成生产计划");
        }
        List<SalesOrderProduct> products = salesOrderProductMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SalesOrderProduct>()
                        .eq(SalesOrderProduct::getOrderId, orderId)
        );
        // BOM/路线前置校验
        checkBomAndRouting(products);

        int createdCount = 0;
        for (SalesOrderProduct product : products) {
            if (product.getProductId() == null) {
                log.warn("订单{}产品{}无productId（样品单），跳过生成计划", orderId, product.getProductCode());
                continue;
            }
            ProductionOrderCreateDTO createDTO = new ProductionOrderCreateDTO();
            createDTO.setOrderType("PLAN");
            createDTO.setSalesOrderId(orderId);
            createDTO.setSalesOrderNo(order.getOrderNo());
            createDTO.setProductId(product.getProductId());
            createDTO.setProductCode(product.getProductCode());
            createDTO.setProductName(product.getProductName());
            createDTO.setProductSpec("");
            createDTO.setProductUnit(product.getUnit());
            createDTO.setPlannedQuantity(BigDecimal.valueOf(product.getQuantity() == null ? 0 : product.getQuantity()));
            createDTO.setPlanStartDate(LocalDate.now());
            if (order.getDeliveryDate() != null) {
                createDTO.setPlanEndDate(order.getDeliveryDate().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate());
            } else {
                createDTO.setPlanEndDate(LocalDate.now().plusDays(7));
            }
            createDTO.setPriority(order.getIsUrgent() != null && order.getIsUrgent() == 1 ? "HIGH" : "MEDIUM");
            createDTO.setRemark("由销售订单[" + order.getOrderNo() + "]生成生产计划");
            createDTO.setOrderNo(redisSequenceService.generateBusinessNumber("PL", "YYMMDD"));
            createDTO.setTraceId(order.getTraceId());
            Product productInfo = productMapper.selectById(product.getProductId());
            if (productInfo != null) {
                createDTO.setBomId(productInfo.getCurrentBomId());
                createDTO.setRoutingId(productInfo.getCurrentRouteId());
            }
            Long planId = productionOrderService.createOrder(createDTO);
            // 自动提交审批：计划员在计划视图审批通过后转工单
            productionOrderService.updateOrderStatus(planId, 1, "销售订单生成计划，自动提交审批");
            createdCount++;
            log.info("为销售订单{}生成生产计划{}，产品：{}，数量：{}",
                    orderId, planId, product.getProductName(), createDTO.getPlannedQuantity());
        }
        if (createdCount == 0) {
            throw new BusinessException("订单无可生成计划的产品（均无productId）");
        }
        // 2026-08-13：生成计划=确认动作，SO 已审核(4)→已确认(6)，写确认人/方式/时间（已确认的历史订单跳过）
        if (OrderStatusEnum.APPROVED.equals(currentStatus)) {
            int up = salesOrderMapper.updateStatusWithCheck(
                    orderId, OrderStatusEnum.CONFIRMED.getCode(), OrderStatusEnum.APPROVED.getCode());
            if (up > 0) {
                SalesOrder confirmUpdate = new SalesOrder();
                confirmUpdate.setOrderId(orderId);
                confirmUpdate.setConfirmBy(SecurityUtils.getUsername());
                confirmUpdate.setConfirmMethod("生成生产计划");
                confirmUpdate.setConfirmTime(LocalDateTime.now());
                salesOrderMapper.updateById(confirmUpdate);
                log.info("订单{}生成生产计划后确认：已审核(4)→已确认(6)，确认人：{}",
                        orderId, SecurityUtils.getUsername());
            }
        }
        log.info("订单{}生成生产计划完成：{}张，SO已确认(6)，待工单启动置生产中(7)", orderId, createdCount);
    }

    @Override
    public ReviewStatusVO getReviewStatus(Long orderId) {
        return null;
    }
    @Override
    public List<ReviewHistoryVO> getReviewHistory(Long orderId) {
        return List.of();
    }




            /**
     * 发货（025：IN_PRODUCTION→SHIPPED 触发入口）
     * 触发 order.delivering 事件 → InventoryEventBridge 联动创建销售出库单并自动确认扣产品库存（021/073）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @Event(value = "order.delivering", bizId = "#orderId", bizType = "'order'")
    public void shipOrder(Long orderId) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 2. 校验状态流转（仅生产中可发货）
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (!currentStatus.canTransitionTo(OrderStatusEnum.SHIPPED)) {
            throw new BusinessException("订单当前状态[" + currentStatus.getName() + "]不能发货，仅生产中订单可发货");
        }
        // 3. 更新状态
        int result = salesOrderMapper.updateStatusWithCheck(
                orderId, OrderStatusEnum.SHIPPED.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }
        log.info("订单{}已发货，操作人：{}", orderId, SecurityUtils.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(Long orderId) {
        // 1. 查询订单
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 校验状态流转（仅已发货可完成）
        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (!currentStatus.canTransitionTo(OrderStatusEnum.COMPLETED)) {
            throw new BusinessException("订单当前状态[" + currentStatus.getName() + "]不能直接完成，仅已发货订单可完成");
        }

        // 3. 更新状态
        int result = salesOrderMapper.updateStatusWithCheck(
                orderId, OrderStatusEnum.COMPLETED.getCode(), currentStatus.getCode()
        );
        if (result == 0) {
            throw new BusinessException("订单状态已被修改，请刷新后重试");
        }

        log.info("订单{}完成，操作人：{}", orderId, SecurityUtils.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmOrder(Long orderId, String confirmedBy, String confirmMethod, String remark) {
        SalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");

        OrderStatusEnum currentStatus = OrderStatusEnum.getByCode(order.getOrderStatus());
        if (currentStatus != OrderStatusEnum.APPROVED) {
            throw new BusinessException("只有已审核的订单才能确认，当前状态：" + currentStatus.getName());
        }

        // 更新状态为 CONFIRMED
        order.setOrderStatus(OrderStatusEnum.CONFIRMED.getCode());
        // 确认记录落库（DEV-343/314：人/方式/时间）
        order.setConfirmBy(confirmedBy);
        order.setConfirmMethod(confirmMethod);
        order.setConfirmTime(LocalDateTime.now());
        salesOrderMapper.updateById(order);

        // 二次齐套检查（DEV-640 8-05）：客户确认环节再次按 BOM 算料，缺口生成/刷新 order_shortage 预警
        // 复用 checkOrderShortage 幂等逻辑（先清旧未处理预警再重算），异常不阻断确认主流程
        try {
            inventoryAlertService.checkOrderShortage(orderId);
        } catch (Exception e) {
            log.error("订单{}确认时齐套检查异常（不影响确认主流程）: {}", orderId, e.getMessage());
        }

        // 触发联动事件
        try {
            eventPublisher.fire("order.confirmed", Map.of(
                    "orderNo", order.getOrderNo(),
                    "orderId", String.valueOf(orderId),
                    "confirmedBy", confirmedBy,
                    "confirmMethod", confirmMethod != null ? confirmMethod : ""
            ));
        } catch (Exception e) {
            log.warn("事件联动失败（不影响主流程）: {}", e.getMessage());
        }

        log.info("订单{}客户确认成功，确认人：{}，方式：{}", orderId, confirmedBy, confirmMethod);
    }

}
