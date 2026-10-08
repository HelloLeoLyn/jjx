package com.jjx.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.domain.*;
import com.jjx.inventory.enums.InventoryItemTypeEnum;
import com.jjx.inventory.enums.InventoryOrderStatusEnum;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.OrderStockReserveService;
import com.jjx.quality.domain.entity.QualityLot;
import com.jjx.quality.dto.QualityLotCreateDTO;
import com.jjx.quality.enums.QualityLotStatusEnum;
import com.jjx.quality.mapper.QualityLotMapper;
import com.jjx.quality.service.QualityLotService;
import com.jjx.quality.service.QualityOqcService;
import com.jjx.production.enums.QualityInspectionResultEnum;
import com.jjx.production.enums.QualityReviewStatusEnum;
import com.jjx.sales.domain.dto.DeliveryArrangeQueryDTO;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.domain.vo.DeliveryArrangeLineVO;
import com.jjx.sales.enums.*;
import com.jjx.sales.mapper.*;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** 合并和单订单发货共用数量真源、来源关系及事务；不依赖出库服务，避免服务循环依赖。 */
@Service
@RequiredArgsConstructor
public class SalesDeliveryWorkflowService {
    public static final String OUTBOUND_SOURCE = "SALES_DELIVERY";
    private final DeliveryCapacityService capacityService;
    private final CustomerMapper customerMapper;
    private final OrderMapper orderMapper;
    private final SalesOrderProductMapper productMapper;
    private final SalesDeliveryMapper deliveryMapper;
    private final SalesDeliveryItemMapper itemMapper;
    private final DeliveryArrangeMapper arrangeMapper;
    private final RedisSequenceService sequenceService;
    private final QualityLotService lotService;
    private final QualityOqcService oqcService;
    private final QualityLotMapper lotMapper;
    private final InventoryOutboundOrderMapper outboundMapper;
    private final InventoryOutboundItemMapper outboundItemMapper;
    private final InventoryItemMapper inventoryItemMapper;
    private final OrderStockReserveService reserveService;
    private final com.jjx.event.EventPublisher eventPublisher;

    public IPage<DeliveryArrangeLineVO> available(DeliveryArrangeQueryDTO q) {
        IPage<DeliveryArrangeLineVO> page = arrangeMapper.page(new Page<>(q.getPageNum(), q.getPageSize()), q,
            SalesOrderStatusEnum.IN_PRODUCTION.getValue(), SalesDeliveryStatusEnum.PENDING.getValue(),
            SalesDeliveryStatusEnum.SHIPPED.getValue(), SalesDeliveryStatusEnum.RECEIVED.getValue(),
            InventoryItemTypeEnum.PRODUCT.getCode());
        List<SalesOrderProduct> sources = page.getRecords().stream().map(line -> {
            SalesOrderProduct source = new SalesOrderProduct(); source.setId(line.getId());
            source.setOrderId(line.getOrderId()); source.setProductId(line.getProductId()); return source;
        }).toList();
        Map<Long, DeliveryCapacityService.Capacity> capacities = capacityService.capacities(sources, false);
        for (DeliveryArrangeLineVO line : page.getRecords()) {
            DeliveryCapacityService.Capacity capacity = capacities.get(line.getProductId());
            BigDecimal stock = capacity == null ? BigDecimal.ZERO : capacity.forOrder(line.getOrderId());
            line.setStockAvailable(stock);
            line.setOwnStockAvailable(capacity == null ? BigDecimal.ZERO : capacity.owned().getOrDefault(line.getOrderId(), BigDecimal.ZERO));
            line.setSharedStockAvailable(capacity == null ? BigDecimal.ZERO : capacity.shared());
            line.setProductStockAvailable(capacity == null ? BigDecimal.ZERO : capacity.productAvailable());
            line.setAvailableQuantity(Math.min(number(line.getOrderRemainingQuantity()), integer(stock)));
            line.setShortageQuantity(Math.max(0, number(line.getOrderRemainingQuantity()) - line.getAvailableQuantity()));
            SalesOrder order = orderMapper.selectById(line.getOrderId());
            if (line.getProductId() == null) line.setBlockedReason("订单明细缺少产品身份");
            else if (order == null || nz(order.getTotalAmount()).signum() <= 0) line.setBlockedReason("订单金额未完善");
            else if (line.getAvailableQuantity() <= 0) line.setBlockedReason("暂无可用成品");
        }
        return page;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Long create(SalesDelivery request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
            throw new BusinessException("请选择本次发货明细");
        if (request.getItems().size() > 200) throw new BusinessException("一次最多安排200条明细");
        if (blank(request.getDeliveryMethod())) throw new BusinessException("请选择本次交货方式");
        Set<Long> ids = new LinkedHashSet<>();
        for (SalesDeliveryItem item : request.getItems()) {
            if (item == null) throw new BusinessException("发货明细不能为空");
            if (item.getOrderProductId() == null || !ids.add(item.getOrderProductId()))
                throw new BusinessException("订单明细不能为空或重复");
            if (item.getQuantity() == null || item.getQuantity() <= 0)
                throw new BusinessException("本次发货数量必须为正整数");
        }
        List<SalesOrderProduct> products = productMapper.selectBatchIds(ids);
        if (products.size() != ids.size()) throw new BusinessException("部分订单明细不存在，请刷新");
        Map<Long, SalesOrder> orders = lockOrders(products.stream().map(SalesOrderProduct::getOrderId).toList());
        SalesOrder first = orders.values().iterator().next();
        SalesCustomer customer = first.getCustomerId() == null ? null : customerMapper.selectById(first.getCustomerId());
        String customerAddress = customer == null ? null : customer.getAddress();
        String address = blank(request.getDeliveryAddress()) ? address(first, customerAddress) : request.getDeliveryAddress();
        if (blank(address)) throw new BusinessException("请填写收货地址");
        for (SalesOrder order : orders.values()) {
            if (!SalesOrderStatusEnum.IN_PRODUCTION.getValue().equals(order.getOrderStatus()))
                throw new BusinessException("订单 " + order.getOrderNo() + " 当前状态不能安排发货");
            if (!Objects.equals(first.getCustomerId(), order.getCustomerId()) || !currency(first).equals(currency(order)))
                throw new BusinessException("只能合并同客户、同币种订单");
            String sourceAddress = address(order, customerAddress);
            if (orders.size() > 1 && !blank(sourceAddress) && !text(address).equals(text(sourceAddress)))
                throw new BusinessException("合并订单的收货地址必须相同，请先完善订单资料");
            if (nz(order.getTotalAmount()).signum() <= 0) throw new BusinessException("订单 " + order.getOrderNo() + " 金额为0，不能发货");
        }
        Map<Long, Integer> allocated = quantities(ids, List.of(SalesDeliveryStatusEnum.PENDING,
            SalesDeliveryStatusEnum.SHIPPED, SalesDeliveryStatusEnum.RECEIVED));
        Map<Long, SalesOrderProduct> byId = products.stream().collect(Collectors.toMap(SalesOrderProduct::getId, p -> p));
        if (products.stream().anyMatch(p -> p.getProductId() == null)) throw new BusinessException("订单明细缺少产品身份");
        Map<Long, DeliveryCapacityService.Capacity> capacities = capacityService.capacities(products, true);
        capacityService.validate(request.getItems(), byId, capacities);
        List<SalesDeliveryItem> lines = new ArrayList<>();
        int total = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (SalesDeliveryItem input : request.getItems()) {
            SalesOrderProduct product = byId.get(input.getOrderProductId());
            int remaining = Math.max(0, number(product.getQuantity()) - allocated.getOrDefault(product.getId(), 0));
            if (input.getQuantity() > remaining) throw new BusinessException("产品[" + product.getProductCode() + "]本次量超过可安排量 " + remaining);
            if (product.getProductId() == null) throw new BusinessException("订单明细缺少产品身份");
            SalesDeliveryItem line = new SalesDeliveryItem();
            line.setOrderProductId(product.getId()); line.setProductId(product.getProductId());
            line.setProductCode(product.getProductCode()); line.setProductName(product.getProductName());
            line.setSpecification(product.getSpecification()); line.setUnit(blank(product.getUnit()) ? "PCS" : product.getUnit());
            line.setQuantity(input.getQuantity()); line.setUnitPrice(product.getUnitPrice());
            line.setAmount(nz(product.getUnitPrice()).multiply(BigDecimal.valueOf(input.getQuantity())));
            line.setRemark(product.getLineRemark()); lines.add(line);
            total = Math.addExact(total, input.getQuantity()); amount = amount.add(line.getAmount());
        }
        if (amount.signum() <= 0) throw new BusinessException("本次发货金额为0，请先完善订单单价");
        SalesDelivery record = new SalesDelivery();
        record.setDeliveryNo(sequenceService.generateBusinessNumberByType("sales_delivery", "DL", "yyMMdd", 3));
        // 兼容历史单据表头；完整关联始终以 order_product_id 为准。
        record.setOrderId(first.getOrderId()); record.setCustomerId(first.getCustomerId()); record.setCustomerName(first.getCustomerName());
        record.setDeliveryAddress(address.trim()); record.setDeliveryMethod(request.getDeliveryMethod().trim());
        record.setContactPerson(blank(request.getContactPerson()) ? (blank(first.getContactPerson()) && customer != null ? customer.getContactPerson() : first.getContactPerson()) : request.getContactPerson());
        record.setContactPhone(blank(request.getContactPhone()) ? (blank(first.getContactPhone()) && customer != null ? customer.getContactPhone() : first.getContactPhone()) : request.getContactPhone());
        record.setDeliveryDate(request.getDeliveryDate() == null ? new Date() : request.getDeliveryDate());
        record.setRemark(request.getRemark()); record.setCarrier(request.getCarrier()); record.setTrackingNo(request.getTrackingNo());
        record.setFreightAmount(nonnegative(request.getFreightAmount())); record.setInsuranceAmount(nonnegative(request.getInsuranceAmount()));
        record.setOtherCharges(nonnegative(request.getOtherCharges()));
        record.setTotalQuantity(total); record.setTotalAmount(amount);
        record.setDeliveryStatus(SalesDeliveryStatusEnum.PENDING.getValue());
        record.setDeliveryPersonId(SecurityUtils.getUserId()); record.setDeliveryPersonName(SecurityUtils.getRealName());
        if (deliveryMapper.insert(record) != 1) throw new BusinessException("发货单创建失败");
        for (SalesDeliveryItem line : lines) {
            line.setDeliveryId(record.getDeliveryId());
            if (itemMapper.insert(line) != 1) throw new BusinessException("发货明细创建失败");
            QualityLotCreateDTO lot = new QualityLotCreateDTO();
            lot.setLotType("OQC"); lot.setSourceType(OUTBOUND_SOURCE); lot.setSourceId(record.getDeliveryId());
            lot.setSourceItemId(line.getItemId()); lot.setOrderId(byId.get(line.getOrderProductId()).getOrderId());
            lot.setProductId(line.getProductId()); lot.setProductCode(line.getProductCode()); lot.setProductName(line.getProductName());
            lot.setBatchNo(record.getDeliveryNo()); lot.setLotQuantity(BigDecimal.valueOf(line.getQuantity()));
            lot.setRemark("发货单 " + record.getDeliveryNo() + " 出货检验");
            oqcService.prepareLot(lot); lotService.createLot(lot);
        }
        return record.getDeliveryId();
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void createSingle(Long orderId, SalesDelivery request) {
        SalesOrder order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw new BusinessException("销售订单不存在");
        SalesDelivery input = request == null ? new SalesDelivery() : request;
        List<SalesOrderProduct> products = productMapper.selectList(new LambdaQueryWrapper<SalesOrderProduct>().eq(SalesOrderProduct::getOrderId, orderId));
        Set<Long> ids = products.stream().map(SalesOrderProduct::getId).collect(Collectors.toSet());
        if (input.getItems() == null || input.getItems().isEmpty()) {
            Map<Long, Integer> occupied = quantities(ids, List.of(SalesDeliveryStatusEnum.PENDING, SalesDeliveryStatusEnum.SHIPPED, SalesDeliveryStatusEnum.RECEIVED));
            Map<Long, DeliveryCapacityService.Capacity> capacities = capacityService.capacities(products, true);
            Map<Long,Integer> stockRemaining = new HashMap<>();
            input.setItems(products.stream().map(p -> {
                SalesDeliveryItem i = new SalesDeliveryItem(); i.setOrderProductId(p.getId());
                int available = stockRemaining.computeIfAbsent(p.getProductId(), key -> capacities.containsKey(key) ? integer(capacities.get(key).forOrder(orderId)) : 0);
                int quantity = Math.min(available, Math.max(0, number(p.getQuantity()) - occupied.getOrDefault(p.getId(), 0)));
                stockRemaining.put(p.getProductId(), available - quantity); i.setQuantity(quantity); return i;
            }).filter(i -> i.getQuantity() > 0).toList());
        } else {
            for (SalesDeliveryItem i : input.getItems()) {
                if (i.getOrderProductId() == null && i.getProductId() != null) {
                    List<SalesOrderProduct> hits = products.stream().filter(p -> i.getProductId().equals(p.getProductId())).toList();
                    if (hits.size() != 1) throw new BusinessException("请明确选择订单明细，不能仅按产品发货");
                    i.setOrderProductId(hits.get(0).getId());
                }
                if (!ids.contains(i.getOrderProductId())) throw new BusinessException("发货明细不属于当前订单");
            }
            input.setItems(input.getItems().stream().filter(i -> i.getQuantity() == null || i.getQuantity() != 0).toList());
        }
        if (blank(input.getDeliveryMethod())) input.setDeliveryMethod("快递");
        create(input);
    }

    public List<SalesDeliveryItem> lines(Long deliveryId) {
        return itemMapper.selectList(new LambdaQueryWrapper<SalesDeliveryItem>().eq(SalesDeliveryItem::getDeliveryId, deliveryId).orderByAsc(SalesDeliveryItem::getItemId));
    }
    public Map<Long, SalesOrder> lockSourceOrders(List<SalesDeliveryItem> lines) {
        List<Long> productIds = lines.stream().map(SalesDeliveryItem::getOrderProductId).filter(Objects::nonNull).distinct().toList();
        if (lines.stream().anyMatch(line -> line.getOrderProductId() == null)) throw new BusinessException("发货明细缺少来源订单");
        if (productIds.isEmpty()) throw new BusinessException("发货明细缺少来源订单");
        List<SalesOrderProduct> products = productMapper.selectBatchIds(productIds);
        if (products.size() != productIds.size()) throw new BusinessException("来源订单明细缺失");
        Map<Long, SalesOrderProduct> byId = products.stream().collect(Collectors.toMap(SalesOrderProduct::getId, p -> p));
        for (SalesDeliveryItem line : lines) if (!Objects.equals(line.getProductId(), byId.get(line.getOrderProductId()).getProductId())) throw new BusinessException("发货产品与来源订单不一致");
        return lockOrders(products.stream().map(SalesOrderProduct::getOrderId).toList());
    }
    private Map<Long, SalesOrder> lockOrders(Collection<Long> ids) {
        Map<Long, SalesOrder> result = new LinkedHashMap<>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            SalesOrder order = orderMapper.selectByIdForUpdate(id);
            if (order == null) throw new BusinessException("来源订单不存在"); result.put(id, order);
        }
        return result;
    }
    public Map<Long, Integer> quantities(Collection<Long> productIds, List<SalesDeliveryStatusEnum> statuses) {
        if (productIds.isEmpty()) return Map.of();
        List<Long> orderIds = productMapper.selectBatchIds(productIds).stream().map(SalesOrderProduct::getOrderId).filter(Objects::nonNull).distinct().toList();
        if (!orderIds.isEmpty()) {
            List<SalesDelivery> previous = deliveryMapper.selectActiveBySourceOrders(orderIds, statuses.stream().map(SalesDeliveryStatusEnum::getValue).toList());
            if (!previous.isEmpty()) {
                List<SalesDeliveryItem> allLines = itemMapper.selectList(new LambdaQueryWrapper<SalesDeliveryItem>().in(SalesDeliveryItem::getDeliveryId, previous.stream().map(SalesDelivery::getDeliveryId).toList()));
                for (SalesDelivery d : previous) {
                    List<SalesDeliveryItem> historical = allLines.stream().filter(line -> Objects.equals(d.getDeliveryId(),line.getDeliveryId())).toList();
                    if (historical.isEmpty() || historical.stream().anyMatch(line -> line.getOrderProductId()==null || line.getQuantity()==null || line.getQuantity()<=0)) throw new BusinessException("历史发货明细不完整，请先核对发货数量");
                    int total = historical.stream().map(SalesDeliveryItem::getQuantity).reduce(0, Math::addExact);
                    if (d.getTotalQuantity()!=null && total!=d.getTotalQuantity()) throw new BusinessException("历史发货单明细数量与单据不一致，请先核对");
                }
            }
        }
        List<SalesDeliveryItem> items = itemMapper.selectList(new LambdaQueryWrapper<SalesDeliveryItem>().in(SalesDeliveryItem::getOrderProductId, productIds));
        if (items.isEmpty()) return Map.of();
        Set<Long> active = deliveryMapper.selectBatchIds(items.stream().map(SalesDeliveryItem::getDeliveryId).distinct().toList()).stream()
            .filter(d -> statuses.stream().anyMatch(s -> s.getValue().equals(d.getDeliveryStatus())))
            .map(SalesDelivery::getDeliveryId).collect(Collectors.toSet());
        Map<Long, Integer> result = new HashMap<>();
        for (SalesDeliveryItem line : items) if (active.contains(line.getDeliveryId())) {
            if (line.getQuantity() == null || line.getQuantity() <= 0) throw new BusinessException("历史发货明细量不完整，请先核对");
            result.merge(line.getOrderProductId(), line.getQuantity(), Math::addExact);
        }
        return result;
    }

    public boolean oqcPassed(Long deliveryId, List<SalesDeliveryItem> lines) {
        if (lines.isEmpty()) return false;
        List<QualityLot> lots = lotMapper.selectList(new LambdaQueryWrapper<QualityLot>().eq(QualityLot::getSourceType, OUTBOUND_SOURCE)
            .eq(QualityLot::getSourceId, deliveryId).eq(QualityLot::getLotType,"OQC")
            .orderByDesc(QualityLot::getVersion).orderByDesc(QualityLot::getLotId));
        return lines.stream().allMatch(line -> lots.stream().filter(lot -> Objects.equals(lot.getSourceItemId(), line.getItemId())).findFirst()
            .map(lot -> QualityInspectionResultEnum.isPass(lot.getResult())
                && (QualityLotStatusEnum.JUDGED.getCode().equals(lot.getStatus()) || QualityLotStatusEnum.CLOSED.getCode().equals(lot.getStatus()))
                && nz(lot.getInspectedQuantity()).signum() > 0 && nz(lot.getLotQuantity()).compareTo(BigDecimal.valueOf(number(line.getQuantity()))) >= 0
                && (blank(lot.getReviewStatus()) || QualityReviewStatusEnum.APPROVED.getCode().equals(lot.getReviewStatus())))
            .orElse(false));
    }
    /** delivery锁 -> 来源订单按ID排序锁；出库确认再次校验，防等待期间复检或改量。 */
    public List<SalesDeliveryItem> prepareShipment(Long deliveryId) {
        SalesDelivery d = deliveryMapper.selectByIdForUpdate(deliveryId);
        if (d == null || !SalesDeliveryStatusEnum.PENDING.getValue().equals(d.getDeliveryStatus())) throw new BusinessException("仅待发货单可安排出库");
        List<SalesDeliveryItem> lines = lines(deliveryId);
        Map<Long, SalesOrder> orders = lockSourceOrders(lines);
        for (SalesOrder order : orders.values()) if (!SalesOrderStatusEnum.IN_PRODUCTION.getValue().equals(order.getOrderStatus())) throw new BusinessException("来源订单当前状态不能发货");
        if (lines.stream().anyMatch(i -> i.getQuantity() == null || i.getQuantity() <= 0) ||
            lines.stream().map(SalesDeliveryItem::getQuantity).reduce(0,Math::addExact) != number(d.getTotalQuantity())) throw new BusinessException("发货单数量与明细不一致");
        lotMapper.selectList(new LambdaQueryWrapper<QualityLot>().eq(QualityLot::getSourceType, OUTBOUND_SOURCE).eq(QualityLot::getSourceId, deliveryId).orderByAsc(QualityLot::getLotId).last("FOR UPDATE"));
        if (!oqcPassed(deliveryId, lines)) throw new BusinessException("本次发货明细尚未全部通过OQC放行");
        Set<Long> ids = lines.stream().map(SalesDeliveryItem::getOrderProductId).collect(Collectors.toSet());
        Map<Long,Integer> allocated = quantities(ids,List.of(SalesDeliveryStatusEnum.PENDING,SalesDeliveryStatusEnum.SHIPPED,SalesDeliveryStatusEnum.RECEIVED));
        for (SalesOrderProduct p : productMapper.selectBatchIds(ids)) if (allocated.getOrDefault(p.getId(),0) > number(p.getQuantity())) throw new BusinessException("来源订单已安排数量超过订单量");
        return lines;
    }
    public void verifyOutbound(Long deliveryId, List<InventoryOutboundItem> items) {
        List<SalesDeliveryItem> lines = prepareShipment(deliveryId);
        Map<Long, SalesDeliveryItem> source = lines.stream().collect(Collectors.toMap(SalesDeliveryItem::getItemId,i -> i));
        if (items.size() != source.size()) throw new BusinessException("出库与发货明细不一致");
        Set<Long> seen = new HashSet<>();
        for (InventoryOutboundItem out : items) {
            SalesDeliveryItem line = source.get(out.getDeliveryItemId());
            InventoryItem identity = out.getInventoryItemId() == null ? null : inventoryItemMapper.selectById(out.getInventoryItemId());
            if (line == null || !seen.add(line.getItemId()) || out.getQuantity() == null ||
                out.getQuantity().compareTo(BigDecimal.valueOf(line.getQuantity())) != 0 || identity == null ||
                !InventoryItemTypeEnum.PRODUCT.getCode().equals(identity.getItemType()) || !Objects.equals(identity.getSourceId(),line.getProductId()))
                throw new BusinessException("出库明细已偏离原始发货凭证，不能出库");
        }
    }
    public void releaseOwnedReservations(List<InventoryOutboundItem> items) {
        for (InventoryOutboundItem item : items) {
            SalesDeliveryItem line = itemMapper.selectById(item.getDeliveryItemId());
            SalesOrderProduct source = line == null ? null : productMapper.selectById(line.getOrderProductId());
            if (source == null) throw new BusinessException("出库来源订单明细缺失");
            reserveService.releaseForOutbound(source.getOrderId(), item.getInventoryItemId(), item.getQuantity());
        }
    }
    public void completeShipment(Long deliveryId) {
        SalesDelivery update = new SalesDelivery(); update.setDeliveryId(deliveryId); update.setDeliveryStatus(SalesDeliveryStatusEnum.SHIPPED.getValue());
        if (deliveryMapper.updateById(update) != 1) throw new BusinessException("发货状态更新失败");
        List<SalesDeliveryItem> deliveryLines = lines(deliveryId);
        Map<Long, SalesOrder> orders = lockSourceOrders(deliveryLines);
        recomputeOrders(orders);
        SalesDelivery delivery = deliveryMapper.selectById(deliveryId);
        for (SalesOrder order : orders.values()) {
            int quantity = deliveryLines.stream().filter(line -> {
                SalesOrderProduct product = productMapper.selectById(line.getOrderProductId());
                return product != null && order.getOrderId().equals(product.getOrderId());
            }).mapToInt(SalesDeliveryItem::getQuantity).sum();
            Map<String,Object> payload = com.jjx.event.EventPublishSupport.payload("order", order.getOrderId(), order.getOrderNo());
            payload.put("salesOrderId",order.getOrderId()); payload.put("orderNo",order.getOrderNo());
            payload.put("deliveryId",deliveryId); payload.put("deliveryNo",delivery.getDeliveryNo());
            payload.put("customerName",order.getCustomerName()); payload.put("deliverQuantity",quantity);
            payload.put("inventoryPosted",true);
            payload.put("orderStatus",orderMapper.selectById(order.getOrderId()).getOrderStatus());
            com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher,"order.delivering",payload);
        }
    }
    public void recomputeOrders(Map<Long,SalesOrder> orders) {
        for (SalesOrder order : orders.values()) {
            List<SalesOrderProduct> products = productMapper.selectList(new LambdaQueryWrapper<SalesOrderProduct>().eq(SalesOrderProduct::getOrderId,order.getOrderId()));
            Map<Long,Integer> sent = quantities(products.stream().map(SalesOrderProduct::getId).toList(),List.of(SalesDeliveryStatusEnum.SHIPPED,SalesDeliveryStatusEnum.RECEIVED));
            int total = 0; boolean complete = !products.isEmpty();
            for (SalesOrderProduct product : products) {
                int quantity = sent.getOrDefault(product.getId(),0);
                if (quantity > number(product.getQuantity())) throw new BusinessException("累计发货量超过来源订单量");
                complete &= quantity == number(product.getQuantity()); total = Math.addExact(total,quantity);
            }
            SalesOrder patch = new SalesOrder(); patch.setOrderId(order.getOrderId()); patch.setShippedQuantity(total);
            if (complete) patch.setOrderStatus(SalesOrderStatusEnum.SHIPPED.getValue());
            else if (SalesOrderStatusEnum.SHIPPED.getValue().equals(order.getOrderStatus())) patch.setOrderStatus(SalesOrderStatusEnum.IN_PRODUCTION.getValue());
            if (orderMapper.updateById(patch) != 1) throw new BusinessException("来源订单已发量更新失败");
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public void voidPending(Long deliveryId,String reason) {
        if (blank(reason) || reason.length() > 200) throw new BusinessException("请填写200字以内的作废原因");
        SalesDelivery d = deliveryMapper.selectByIdForUpdate(deliveryId);
        if (d == null || !SalesDeliveryStatusEnum.PENDING.getValue().equals(d.getDeliveryStatus())) throw new BusinessException("仅待发货单可作废");
        lockSourceOrders(lines(deliveryId));
        List<InventoryOutboundOrder> outbounds = outboundMapper.selectList(new LambdaQueryWrapper<InventoryOutboundOrder>().eq(InventoryOutboundOrder::getSourceType,OUTBOUND_SOURCE).eq(InventoryOutboundOrder::getSourceId,deliveryId));
        for (InventoryOutboundOrder out : outbounds) {
            out = outboundMapper.selectByIdForUpdate(out.getOutboundId());
            if (InventoryOrderStatusEnum.COMPLETED.getValue().equals(out.getOrderStatus())) throw new BusinessException("已出库单据不能作废，请走拒收或红冲");
            out.setOrderStatus(InventoryOrderStatusEnum.CANCELLED.getValue());
            if (outboundMapper.updateById(out) != 1) throw new BusinessException("关联出库单作废失败");
        }
        List<QualityLot> lots = lotMapper.selectList(new LambdaQueryWrapper<QualityLot>().eq(QualityLot::getSourceType,OUTBOUND_SOURCE).eq(QualityLot::getSourceId,deliveryId).last("FOR UPDATE"));
        for (QualityLot lot : lots) {
            lot.setStatus(QualityLotStatusEnum.CLOSED.getCode());
            lot.setRemark(appendRemark(lot.getRemark(), "；来源发货单作废："+reason));
            if (lotMapper.updateById(lot) != 1) throw new BusinessException("OQC关闭失败");
        }
        d.setDeliveryStatus(SalesDeliveryStatusEnum.VOIDED.getValue());
        d.setRemark(appendRemark(d.getRemark(), "；作废原因："+reason));
        if (deliveryMapper.updateById(d) != 1) throw new BusinessException("发货单作废失败");
    }
    private static String appendRemark(String original, String appended) { String result=text(original)+appended; if(result.length()>500) throw new BusinessException("原备注与作废原因合计超过500字，请缩短作废原因"); return result; }
    public static String currency(SalesOrder order) { return blank(order.getCurrency()) ? "CNY" : order.getCurrency().trim(); }
    private static String address(SalesOrder order, String fallback) { return blank(order.getDeliveryAddress()) ? fallback : order.getDeliveryAddress(); }
    private static int integer(BigDecimal quantity) { return quantity.max(BigDecimal.ZERO).min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue(); }
    private static BigDecimal nonnegative(BigDecimal value) { if (nz(value).signum()<0) throw new BusinessException("费用不能为负数"); return nz(value); }
    private static BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static int number(Integer value) { return value == null ? 0 : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String text(String value) { return value == null ? "" : value.trim(); }
}
