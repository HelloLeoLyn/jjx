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
            wrapper.eq(SalesDelivery::getOrderId, dto.getOrderId());
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
        LambdaQueryWrapper<SalesDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SalesDelivery::getOrderId, orderId)
               .orderByDesc(SalesDelivery::getCreateTime);
        List<SalesDeliveryVO> vos = salesDeliveryMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        fillItems(vos);
        return vos;
    }

    /** OQC放行、库存出库、发货状态与订单已发量同一事务完成。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmShipment(Long deliveryId) {
        SalesDelivery delivery = salesDeliveryMapper.selectByIdForUpdate(deliveryId);
        if (delivery == null) throw new BusinessException("发货单不存在");
        if (!SalesDeliveryStatusEnum.PENDING.getValue().equals(delivery.getDeliveryStatus())) {
            throw new BusinessException("仅待发货单可确认发货，请刷新后重试");
        }
        SalesOrder order = orderMapper.selectByIdForUpdate(delivery.getOrderId());
        if (order == null) throw new BusinessException("销售订单不存在");
        if (!SalesOrderStatusEnum.IN_PRODUCTION.getValue().equals(order.getOrderStatus())) {
            throw new BusinessException("订单当前状态不允许确认发货");
        }
        List<SalesDeliveryItem> lines = assertOqcPassed(deliveryId);
        List<SalesDelivery> shipped = salesDeliveryMapper.selectList(new LambdaQueryWrapper<SalesDelivery>()
                .eq(SalesDelivery::getOrderId, order.getOrderId())
                .in(SalesDelivery::getDeliveryStatus, SalesDeliveryStatusEnum.SHIPPED.getValue(),
                        SalesDeliveryStatusEnum.RECEIVED.getValue()));
        Map<Long, Integer> quantities = new HashMap<>();
        if (!shipped.isEmpty()) {
            List<SalesDeliveryItem> previousLines = salesDeliveryItemMapper.selectList(new LambdaQueryWrapper<SalesDeliveryItem>()
                    .in(SalesDeliveryItem::getDeliveryId, shipped.stream().map(SalesDelivery::getDeliveryId).toList()));
            for (SalesDeliveryItem line : previousLines) {
                if (line.getOrderProductId() == null || line.getQuantity() == null || line.getQuantity() <= 0) {
                    throw new BusinessException("历史发货明细不完整，请先核对发货数量");
                }
                quantities.merge(line.getOrderProductId(), line.getQuantity(), Integer::sum);
            }
            for (SalesDelivery previous : shipped) {
                int quantity = previousLines.stream().filter(line -> previous.getDeliveryId().equals(line.getDeliveryId()))
                        .mapToInt(SalesDeliveryItem::getQuantity).sum();
                if (quantity <= 0 || (previous.getTotalQuantity() != null && quantity != previous.getTotalQuantity())) {
                    throw new BusinessException("历史发货单 " + previous.getDeliveryNo() + " 明细数量不完整，请先核对");
                }
            }
        }
        int currentQuantity = lines.stream().mapToInt(SalesDeliveryItem::getQuantity).sum();
        if (delivery.getTotalQuantity() == null || currentQuantity != delivery.getTotalQuantity()) {
            throw new BusinessException("本次发货明细数量与单据不一致");
        }
        for (SalesDeliveryItem line : lines) quantities.merge(line.getOrderProductId(), line.getQuantity(), Integer::sum);
        List<SalesOrderProduct> products = orderProductMapper.selectList(new LambdaQueryWrapper<SalesOrderProduct>()
                .eq(SalesOrderProduct::getOrderId, order.getOrderId()));
        if (products.isEmpty()) throw new BusinessException("销售订单无明细");
        for (SalesDeliveryItem line : lines) {
            SalesOrderProduct product = products.stream().filter(p -> p.getId().equals(line.getOrderProductId())).findFirst().orElse(null);
            if (product == null || line.getProductId() == null || !line.getProductId().equals(product.getProductId())) {
                throw new BusinessException("发货产品与订单明细不一致");
            }
        }
        boolean allShipped = true;
        int total = 0;
        for (SalesOrderProduct product : products) {
            int quantity = quantities.getOrDefault(product.getId(), 0);
            if (product.getQuantity() == null || quantity > product.getQuantity()) {
                throw new BusinessException("产品[" + product.getProductCode() + "]累计发货数量超过订单数量");
            }
            allShipped &= quantity == product.getQuantity();
            total += quantity;
            quantities.remove(product.getId());
        }
        if (!quantities.isEmpty()) throw new BusinessException("发货明细不属于当前销售订单");
        Long outboundId = outboundService.createFromSalesByDelivery(deliveryId);
        if (outboundId == null) throw new BusinessException("销售出库未成功，不能确认发货");
        SalesDelivery update = new SalesDelivery();
        update.setDeliveryId(deliveryId);
        update.setDeliveryStatus(SalesDeliveryStatusEnum.SHIPPED.getValue());
        if (salesDeliveryMapper.updateById(update) <= 0) throw new BusinessException("发货单状态更新失败");
        SalesOrder patch = new SalesOrder();
        patch.setOrderId(order.getOrderId());
        patch.setShippedQuantity(total);
        if (allShipped) patch.setOrderStatus(SalesOrderStatusEnum.SHIPPED.getValue());
        if (orderMapper.updateById(patch) <= 0) throw new BusinessException("订单已发货量更新失败");
        Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload("order", order.getOrderId(), order.getOrderNo());
        payload.put("salesOrderId", order.getOrderId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("deliveryId", deliveryId);
        payload.put("deliveryNo", delivery.getDeliveryNo());
        payload.put("customerName", order.getCustomerName());
        payload.put("orderStatus", allShipped ? SalesOrderStatusEnum.SHIPPED.getValue() : order.getOrderStatus());
        payload.put("deliverQuantity", delivery.getTotalQuantity());
        payload.put("inventoryPosted", true);
        payload.put("outboundId", outboundId);
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, "order.delivering", payload);
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

    /** 每个发货明细的最新版 OQC 都必须放行，才能确认发货。 */
    private List<SalesDeliveryItem> assertOqcPassed(Long deliveryId) {
        List<SalesDeliveryItem> items = salesDeliveryItemMapper.selectList(
                new LambdaQueryWrapper<SalesDeliveryItem>().eq(SalesDeliveryItem::getDeliveryId, deliveryId));
        if (items.isEmpty()) throw new BusinessException("发货单缺少本次发货明细，不能确认发货");
        List<com.jjx.quality.domain.entity.QualityLot> lots = qualityLotMapper.selectList(
                new LambdaQueryWrapper<com.jjx.quality.domain.entity.QualityLot>()
                        .eq(com.jjx.quality.domain.entity.QualityLot::getLotType, "OQC")
                        .eq(com.jjx.quality.domain.entity.QualityLot::getSourceType, "SALES_DELIVERY")
                        .eq(com.jjx.quality.domain.entity.QualityLot::getSourceId, deliveryId)
                        .orderByDesc(com.jjx.quality.domain.entity.QualityLot::getVersion)
                        .orderByDesc(com.jjx.quality.domain.entity.QualityLot::getLotId).last("FOR UPDATE"));
        for (SalesDeliveryItem item : items) {
            if (item.getItemId() == null || item.getOrderProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException("发货明细不完整，不能确认发货");
            }
            com.jjx.quality.domain.entity.QualityLot latest = lots.stream()
                    .filter(lot -> item.getItemId().equals(lot.getSourceItemId())).findFirst().orElse(null);
            if (latest == null || !QualityInspectionResultEnum.isPass(latest.getResult())
                    || !(QualityLotStatusEnum.JUDGED.getCode().equals(latest.getStatus())
                        || QualityLotStatusEnum.CLOSED.getCode().equals(latest.getStatus()))
                    || latest.getInspectedQuantity() == null || latest.getInspectedQuantity().signum() <= 0
                    || latest.getLotQuantity() == null || latest.getLotQuantity().compareTo(BigDecimal.valueOf(item.getQuantity())) < 0
                    || (latest.getReviewStatus() != null && !latest.getReviewStatus().isBlank()
                        && !QualityReviewStatusEnum.APPROVED.getCode().equals(latest.getReviewStatus()))) {
                throw new BusinessException("发货明细 " + item.getProductCode() + " 尚无已放行的合格 OQC 结果，不能确认发货"
                        + (latest == null ? "" : "（检验批 " + latest.getLotNo() + "）"));
            }
        }
        return items;
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
        SalesDelivery current = salesDeliveryMapper.selectById(deliveryId);
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

        // ③ 重算订单已发数量；不满发则回退到「生产中」允许重新发货
        try {
            SalesOrder order = orderMapper.selectById(current.getOrderId());
            if (order != null) {
                int shipped = 0;
                List<SalesDelivery> remained = salesDeliveryMapper.selectList(
                        new LambdaQueryWrapper<SalesDelivery>()
                                .eq(SalesDelivery::getOrderId, current.getOrderId())
                                .in(SalesDelivery::getDeliveryStatus, SalesDeliveryStatusEnum.SHIPPED.getValue(), SalesDeliveryStatusEnum.RECEIVED.getValue()));
                if (!remained.isEmpty()) {
                    List<Long> ids = remained.stream().map(SalesDelivery::getDeliveryId).toList();
                    for (SalesDeliveryItem item : salesDeliveryItemMapper.selectList(
                            new LambdaQueryWrapper<SalesDeliveryItem>().in(SalesDeliveryItem::getDeliveryId, ids))) {
                        if (item.getQuantity() != null) {
                            shipped += item.getQuantity();
                        }
                    }
                }
                int ordered = order.getTotalQuantity() == null ? 0 : order.getTotalQuantity();
                SalesOrder patch = new SalesOrder();
                patch.setOrderId(order.getOrderId());
                patch.setShippedQuantity(shipped);
                boolean needRevert = Integer.valueOf(SalesOrderStatusEnum.SHIPPED.getValue()).equals(order.getOrderStatus())
                        && shipped < ordered;
                if (needRevert) {
                    patch.setOrderStatus(SalesOrderStatusEnum.IN_PRODUCTION.getValue());
                }
                orderMapper.updateById(patch);
                log.info("拒收后重算订单: orderId={}, shipped={}, ordered={}, 回退生产中={}",
                        order.getOrderId(), shipped, ordered, needRevert);
            }
        } catch (Exception e) {
            log.warn("拒收后重算订单已发数量失败（不影响拒收与回库）: {}", e.getMessage());
        }

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
            vo.setItems(grouped.getOrDefault(vo.getDeliveryId(), List.of()));
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
