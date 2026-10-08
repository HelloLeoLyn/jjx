package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.QualityTemplatePrintLog;
import com.jjx.production.domain.entity.QualityTemplateRegistry;
import com.jjx.production.mapper.QualityTemplatePrintLogMapper;
import com.jjx.production.mapper.QualityTemplateRegistryMapper;
import com.jjx.sales.domain.dto.SalesDeliveryQueryDTO;
import com.jjx.sales.domain.entity.SalesDelivery;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.enums.SalesOrderStatusEnum;
import com.jjx.sales.enums.SalesDeliveryStatusEnum;
import com.jjx.quality.enums.QualityLotStatusEnum;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityReviewStatusEnum;
import com.jjx.sales.domain.entity.SalesOrderProduct;
import java.math.BigDecimal;
import java.util.HashMap;
import com.jjx.sales.domain.entity.SalesDeliveryItem;
import com.jjx.sales.domain.vo.SalesDeliveryVO;
import com.jjx.sales.mapper.SalesDeliveryItemMapper;
import com.jjx.sales.mapper.SalesDeliveryMapper;
import com.jjx.sales.service.ISalesDeliveryService;
import com.jjx.system.annotation.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjx.system.utils.SecurityUtils;

import java.util.List;
import java.util.Date;
import java.util.Map;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 销售发货单服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j 
public class SalesDeliveryServiceImpl implements ISalesDeliveryService {

    private final com.jjx.sales.service.SalesDeliveryWorkflowService deliveryWorkflow;
    private final com.jjx.inventory.mapper.InventoryOutboundOrderMapper outboundMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    /** 2026-09-21 dev-20260921-039（分批发货）：发货明细 */
    private final SalesDeliveryItemMapper salesDeliveryItemMapper;
    /** 2026-09-21 dev-20260921-039（拒收回流）：回冲库存 + 重算订单已发数量。 */
    private final com.jjx.sales.mapper.OrderMapper orderMapper;
    private final com.jjx.inventory.service.InventoryInboundService inboundService;
    /** 2026-09-21（dev-20260921-013）：签收改手写 payload（带 deliveryNo）。 */
    private final com.jjx.event.EventPublisher eventPublisher;

    /** 打印留痕（口径 D3）：复用 production 包既有实体/Mapper，不另建表映射 */
    private final QualityTemplatePrintLogMapper printLogMapper;
    private final QualityTemplateRegistryMapper templateRegistryMapper;
    private final com.jjx.quality.mapper.QualityLotMapper qualityLotMapper;
    private final com.jjx.inventory.service.InventoryOutboundService outboundService;
    private final com.jjx.sales.mapper.SalesOrderProductMapper orderProductMapper;

    private static final String PRINT_BIZ_TYPE = "sales_delivery";
    /** quality_template_registry.status：1=生效 */
    private static final Integer TEMPLATE_STATUS_ACTIVE = 1;

    @Override
    public Page<SalesDeliveryVO> pageQuery(SalesDeliveryQueryDTO dto) {
        LambdaQueryWrapper<SalesDelivery> wrapper = new LambdaQueryWrapper<>();
        if (dto.getOrderId() != null) {
            wrapper.and(w -> w.eq(SalesDelivery::getOrderId, dto.getOrderId()).or().apply(
                "EXISTS (SELECT 1 FROM sales_delivery_item i JOIN sales_order_product p ON p.id=i.order_product_id " +
                "WHERE i.delivery_id=sales_delivery.delivery_id AND p.order_id={0})", dto.getOrderId()));
        }
        if (dto.getDeliveryNo() != null && !dto.getDeliveryNo().isEmpty()) {
            wrapper.like(SalesDelivery::getDeliveryNo, dto.getDeliveryNo());
        }
        if (dto.getCustomerName() != null && !dto.getCustomerName().isEmpty()) {
            wrapper.like(SalesDelivery::getCustomerName, dto.getCustomerName());
        }
        if (dto.getDeliveryStatus() != null) {
            wrapper.eq(SalesDelivery::getDeliveryStatus, dto.getDeliveryStatus());
        }
        if (dto.getDeliveryDateStart() != null) {
            wrapper.ge(SalesDelivery::getDeliveryDate, dto.getDeliveryDateStart());
        }
        if (dto.getDeliveryDateEnd() != null) {
            wrapper.le(SalesDelivery::getDeliveryDate, dto.getDeliveryDateEnd());
        }
        wrapper.orderByDesc(SalesDelivery::getCreateTime).orderByDesc(SalesDelivery::getDeliveryId);

        Page<SalesDelivery> page = salesDeliveryMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        Page<SalesDeliveryVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<SalesDeliveryVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        fillPrintInfo(records);
        fillItems(records);
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public SalesDeliveryVO getById(Long deliveryId) {
        SalesDelivery entity = salesDeliveryMapper.selectById(deliveryId);
        if (entity == null) {
            return null;
        }
        SalesDeliveryVO vo = toVO(entity);
        fillItems(List.of(vo));
        return vo;
    }

    @Override
    public List<SalesDeliveryVO> listByOrderId(Long orderId) {
        List<SalesDeliveryVO> vos = salesDeliveryMapper.selectByOrder(orderId).stream()
                .map(this::toVO).collect(Collectors.toList());
        fillItems(vos);
        return vos;
    }

    /** OQC放行后生成仓库待确认出库单；实际扣库和已发回写发生在仓库确认。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmShipment(Long deliveryId) {
        deliveryWorkflow.prepareShipment(deliveryId);
        if (outboundService.createFromSalesByDelivery(deliveryId) == null)
            throw new BusinessException("销售出库单创建失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 口径 D6：签收后发事件（收件角色与账期规则由 sys_event_config 配置，代码不写死）
    public void receive(Long deliveryId, SalesDelivery receiveInfo) {
        SalesDelivery current = salesDeliveryMapper.selectByIdForUpdate(deliveryId);
        if (current == null) {
            throw new BusinessException("发货单不存在");
        }
        if (!SalesDeliveryStatusEnum.SHIPPED.getValue().equals(current.getDeliveryStatus())) {
            throw new BusinessException("仅已发货单可登记客户签收");
        }
        SalesDelivery update = new SalesDelivery();
        update.setDeliveryId(deliveryId);
        update.setReceiverName(receiveInfo == null ? null : receiveInfo.getReceiverName());
        update.setReceiverPhone(receiveInfo == null ? null : receiveInfo.getReceiverPhone());
        update.setReceiveRemark(receiveInfo == null ? null : receiveInfo.getReceiveRemark());
        update.setCustomerReceiveDate(receiveInfo == null ? null : receiveInfo.getCustomerReceiveDate());
        update.setReceiveTime(new Date());
        update.setReceiveBy(SecurityUtils.getUserId());
        String receiveName = SecurityUtils.getRealName();
        update.setReceiveName(receiveName == null || receiveName.isBlank()
                ? SecurityUtils.getUsername() : receiveName);
        update.setDeliveryStatus(SalesDeliveryStatusEnum.RECEIVED.getValue());
        if (salesDeliveryMapper.updateById(update) <= 0) {
            throw new BusinessException("签收失败，请刷新后重试");
        }
        // 2026-09-21（dev-20260921-013）：签收发事件（手写 payload 带 deliveryNo）
        SalesDelivery received = salesDeliveryMapper.selectById(deliveryId);
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "sales_delivery", deliveryId, received == null ? null : received.getDeliveryNo());
        if (received != null) {
            payload.put("customerName", received.getCustomerName());
            payload.put("orderId", received.getOrderId());
        }
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, "sales.delivery.received", payload);
    }

    /**
     * 客户拒收登记（2026-09-21 dev-20260921-039，拒收回流）。
     *
     * <p>自动完成（尽量少人工填写）：
     * ① 发货单置 已拒收(5) + 记录原因/时间/经办人；
     * ② 按发货明细自动生成「拒收回库单」（REJECT-{发货单号}）并过账，库存回冲成品库存；
     * ③ 重算订单已发数量，若因此不满发则把订单从「已发货」回退为「生产中」，允许重新发货；
     * ④ 发 sales.delivery.rejected 事件（通知销售跟进 + 派待办任务）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long deliveryId, String reason) {
        SalesDelivery current = salesDeliveryMapper.selectByIdForUpdate(deliveryId);
        if (current == null) {
            throw new BusinessException("发货单不存在");
        }
        if (SalesDeliveryStatusEnum.REJECTED.getValue().equals(current.getDeliveryStatus())) {
            throw new BusinessException("发货单已是拒收状态，请勿重复操作");
        }
        if (!SalesDeliveryStatusEnum.SHIPPED.getValue().equals(current.getDeliveryStatus())) {
            String label = SalesDeliveryStatusEnum.labelOf(current.getDeliveryStatus());
            throw new BusinessException("发货单当前状态[" + label + "]不能登记拒收（仅已发货(2)可拒收；已签收请走销售退货流程）");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("请填写拒收原因");
        }

        var sourceOrders = deliveryWorkflow.lockSourceOrders(deliveryWorkflow.lines(deliveryId));
        SalesDelivery update = new SalesDelivery();
        update.setDeliveryId(deliveryId);
        update.setDeliveryStatus(SalesDeliveryStatusEnum.REJECTED.getValue());
        update.setRejectReason(reason);
        update.setRejectTime(new Date());
        update.setRejectBy(SecurityUtils.getUserId());
        String realName = SecurityUtils.getRealName();
        update.setRejectName(realName == null || realName.isBlank() ? SecurityUtils.getUsername() : realName);
        if (salesDeliveryMapper.updateById(update) <= 0) {
            throw new BusinessException("拒收登记失败，请刷新后重试");
        }

        // ② 库存回冲（按发货明细自动生成拒收回库单并过账）
        try {
            Long inboundId = inboundService.createSalesRejectInbound(deliveryId);
            if (inboundId == null) {
                log.warn("发货单{}无明细，拒收库存未自动回冲（历史数据，需人工处理）", current.getDeliveryNo());
            }
        } catch (Exception e) {
            log.error("拒收回库失败: deliveryId={}, err={}", deliveryId, e.getMessage());
            throw new BusinessException("拒收回库失败：" + e.getMessage());
        }

        // 按所有来源订单重算，不把合并单整单数量记到表头兼容订单。
        deliveryWorkflow.recomputeOrders(sourceOrders);

        // ④ 事件：通知销售跟进（重发/退货）
        try {
            java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                    "sales", deliveryId, current.getDeliveryNo());
            payload.put("deliveryNo", current.getDeliveryNo());
            payload.put("orderId", current.getOrderId());
            payload.put("customerName", current.getCustomerName());
            payload.put("rejectReason", reason);
            com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, "sales.delivery.rejected", payload);
        } catch (Exception e) {
            log.warn("拒收事件发布失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPrintLog(Long deliveryId) {
        SalesDelivery delivery = salesDeliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException("发货单不存在");
        }
        // 口径 D3：走 1237 的打印留痕机制（quality_template_print_log 按 biz_type+biz_id 记账）。
        // 该表 template_id 为 NOT NULL，所以先按 biz_type 解析生效模板；没有登记模板则不留痕（不阻断打印）。
        LambdaQueryWrapper<QualityTemplateRegistry> tw = new LambdaQueryWrapper<>();
        tw.eq(QualityTemplateRegistry::getBizType, PRINT_BIZ_TYPE)
          .eq(QualityTemplateRegistry::getStatus, TEMPLATE_STATUS_ACTIVE)
          .orderByAsc(QualityTemplateRegistry::getId)
          .last("LIMIT 1");
        QualityTemplateRegistry template = templateRegistryMapper.selectOne(tw);
        if (template == null) {
            return;
        }
        QualityTemplatePrintLog log = new QualityTemplatePrintLog();
        log.setTemplateId(template.getId());
        log.setRecordNo(template.getRecordNo());
        log.setBizType(PRINT_BIZ_TYPE);
        log.setBizId(deliveryId);
        log.setOperatorId(SecurityUtils.getUserId());
        String realName = SecurityUtils.getRealName();
        log.setOperatorName(realName == null || realName.isBlank() ? SecurityUtils.getUsername() : realName);
        log.setPrintTime(LocalDateTime.now());
        printLogMapper.insert(log);
    }

    /** 批量回填打印次数/最近打印人（一次查询，避免逐行查） */
    private void fillPrintInfo(List<SalesDeliveryVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> ids = vos.stream().map(SalesDeliveryVO::getDeliveryId)
                .filter(id -> id != null).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<QualityTemplatePrintLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QualityTemplatePrintLog::getBizType, PRINT_BIZ_TYPE)
               .in(QualityTemplatePrintLog::getBizId, ids)
               .orderByAsc(QualityTemplatePrintLog::getPrintTime);
        Map<Long, List<QualityTemplatePrintLog>> grouped = printLogMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(QualityTemplatePrintLog::getBizId));
        for (SalesDeliveryVO vo : vos) {
            List<QualityTemplatePrintLog> logs = grouped.get(vo.getDeliveryId());
            if (logs == null || logs.isEmpty()) {
                vo.setPrintCount(0);
                continue;
            }
            vo.setPrintCount(logs.size());
            QualityTemplatePrintLog last = logs.get(logs.size() - 1);
            vo.setLastPrintBy(last.getOperatorName());
            vo.setLastPrintTime(last.getPrintTime() == null ? null : Timestamp.valueOf(last.getPrintTime()));
        }
    }

    /**
     * 回填发货明细（2026-09-21 dev-20260921-039，分批发货）：
     * 详情/打印/拒收回冲都要知道「本次发了哪些行、各多少」，否则只能从订单全量带出（比实际发货多）。
     */
    private void fillItems(List<SalesDeliveryVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> ids = vos.stream().map(SalesDeliveryVO::getDeliveryId)
                .filter(id -> id != null).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<SalesDeliveryItem>> grouped = salesDeliveryItemMapper.selectList(
                new LambdaQueryWrapper<SalesDeliveryItem>()
                        .in(SalesDeliveryItem::getDeliveryId, ids)
                        .orderByAsc(SalesDeliveryItem::getItemId))
                .stream().collect(Collectors.groupingBy(SalesDeliveryItem::getDeliveryId));
        for (SalesDeliveryVO vo : vos) {
            List<SalesDeliveryItem> lines = grouped.getOrDefault(vo.getDeliveryId(), List.of());
            for (SalesDeliveryItem line : lines) {
                SalesOrderProduct product = line.getOrderProductId() == null ? null : orderProductMapper.selectById(line.getOrderProductId());
                if (product != null) {
                    line.setOrderId(product.getOrderId()); line.setCustomerMaterialNo(product.getCustomerMaterialNo() == null || product.getCustomerMaterialNo().isBlank() ? product.getProductName() : product.getCustomerMaterialNo());
                    SalesOrder order = orderMapper.selectById(product.getOrderId());
                    if (order != null) { line.setOrderNo(order.getOrderNo()); vo.setCurrency(com.jjx.sales.service.SalesDeliveryWorkflowService.currency(order)); }
                }
            }
            vo.setItems(lines);
            vo.setOrderNos(lines.stream().map(SalesDeliveryItem::getOrderNo).filter(java.util.Objects::nonNull).distinct().toList());
            vo.setOqcPassed(deliveryWorkflow.oqcPassed(vo.getDeliveryId(), lines));
            List<com.jjx.inventory.domain.InventoryOutboundOrder> outbounds = outboundMapper.selectList(
                new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryOutboundOrder>()
                .eq(com.jjx.inventory.domain.InventoryOutboundOrder::getSourceType, com.jjx.sales.service.SalesDeliveryWorkflowService.OUTBOUND_SOURCE)
                .eq(com.jjx.inventory.domain.InventoryOutboundOrder::getSourceId, vo.getDeliveryId())
                .ne(com.jjx.inventory.domain.InventoryOutboundOrder::getOrderStatus, com.jjx.inventory.enums.InventoryOrderStatusEnum.CANCELLED.getValue())
                .orderByDesc(com.jjx.inventory.domain.InventoryOutboundOrder::getOutboundId));
            if (!outbounds.isEmpty()) {
                var out = outbounds.get(0); vo.setOutboundId(out.getOutboundId()); vo.setOutboundNo(out.getOutboundNo()); vo.setOutboundStatus(out.getOrderStatus());
            }
        }
    }

    private SalesDeliveryVO toVO(SalesDelivery entity) {
        SalesDeliveryVO vo = new SalesDeliveryVO();
        BeanUtils.copyProperties(entity, vo);
        if (entity.getDeliveryStatus() != null) {
            vo.setDeliveryStatusDesc(SalesDeliveryStatusEnum.labelOf(entity.getDeliveryStatus()));
        }
        return vo;
    }

}
