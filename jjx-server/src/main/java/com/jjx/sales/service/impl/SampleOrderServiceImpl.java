package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesSampleProcess;
import com.jjx.sales.domain.entity.SalesSampleBom;
import com.jjx.sales.enums.OrderTypeEnum;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesSampleRoundMapper;
import com.jjx.sales.mapper.SalesSampleProcessMapper;
import com.jjx.sales.mapper.SalesSampleBomMapper;
import com.jjx.sales.service.ISampleOrderService;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.system.annotation.Event;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.service.LogSaveService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 样品单服务实现类
 * 独立于标准订单的样品单生命周期管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SampleOrderServiceImpl implements ISampleOrderService {

    private final OrderMapper orderMapper;
    private final QuotationMapper quotationMapper;
    private final com.jjx.sales.mapper.SalesQuotationItemMapper quotationItemMapper;
    private final com.jjx.product.mapper.ProductMapper productMapper;
    private final com.jjx.sales.mapper.SalesSampleTransferMapper sampleTransferMapper;
    private final com.jjx.sales.mapper.SalesOrderProductMapper orderProductMapper;
    private final com.jjx.system.mapper.SysTaskMapper sysTaskMapper;
    private final RedisSequenceService redisSequenceService;
    private final ISalesOrderProductService orderProductService;
    private final SalesSampleRoundMapper sampleRoundMapper;
    private final SalesSampleProcessMapper sampleProcessMapper;
    private final SalesSampleBomMapper sampleBomMapper;
    private final com.jjx.system.service.ISysAttachmentService attachmentService;
    private final com.jjx.product.service.IEngineeringBomService bomService;
    private final com.jjx.product.service.IEngineeringRoutingService routingService;
    private final com.jjx.product.mapper.EngineeringBomItemMapper bomItemMapper;
    private final com.jjx.inventory.mapper.InventoryMaterialMapper inventoryMaterialMapper;
    private final com.jjx.engineering.mapper.RoutingItemMapper routingItemMapper;
    private final com.jjx.product.mapper.ProductStandardProcessMapper standardProcessMapper;
    private final com.jjx.product.mapper.EngineeringFilmMapper engineeringFilmMapper;
    private final LogSaveService logSaveService;
    private final com.jjx.sales.mapper.CustomerMapper customerMapper;

    // ============ 状态更新辅助 ============

    /**
     * 安全更新样品单状态（带旧状态校验防并发）
     */
    private void safeTransition(Long orderId, SampleOrderStatusEnum from, SampleOrderStatusEnum to, String actionDesc) {
        int affected = orderMapper.updateSampleStatus(orderId, from.getCode(), to.getCode());
        if (affected == 0) {
            SalesOrder current = orderMapper.selectById(orderId);
            String statusName = current != null && current.getSampleStatus() != null
                    ? SampleOrderStatusEnum.getByCodeSafe(current.getSampleStatus()).map(SampleOrderStatusEnum::getName).orElse("未知")
                    : "未知";
            throw new BusinessException(String.format("样品单状态已变更(当前:%s)，无法%s，请刷新后重试", statusName, actionDesc));
        }
        log.info("样品单[{}] 状态 {} → {}", orderId, from.getName(), to.getName());
    }

    // ============ 核心业务流程 ============

    @Override
    @Event(value = "sample.created", bizId = "#result.orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder createFromQuotation(Long quotationId, Integer sampleQty, String remark,
                                          String deliveryDate, String contactPerson, String contactPhone, String techRequirement) {
        SalesQuotation quotation = quotationMapper.selectById(quotationId);
        if (quotation == null || quotation.getDeleted() == 1) {
            throw new BusinessException("报价单不存在");
        }

        // 防重复转换：已完成(9)的报价单不可再转样品
        if (quotation.getQuotationStatus() != null
                && quotation.getQuotationStatus() == com.jjx.sales.enums.QuotationStatus.COMPLETED.getCode()) {
            throw new BusinessException("报价单已完成，不可重复转样品单");
        }
        // 只有客户已确认(2)的报价单可转样品单（8-03 规则）
        if (quotation.getQuotationStatus() == null
                || quotation.getQuotationStatus() != com.jjx.sales.enums.QuotationStatus.ACCEPTED.getCode()) {
            throw new BusinessException("只有客户已确认的报价单可以转为样品单");
        }

        // 生成订单编号
        String orderNo = redisSequenceService.generateBusinessNumber("SP", "样品单号");

        SalesOrder order = new SalesOrder();
        order.setOrderNo(orderNo);
        order.setQuotationId(quotationId);
        order.setCustomerId(quotation.getCustomerId());
        order.setCustomerName(quotation.getCustomerName());
        // 联系人/电话：创建时传入则覆盖报价单默认值
        order.setContactPerson(contactPerson != null && !contactPerson.isEmpty()
                ? contactPerson : quotation.getContactPerson());
        order.setContactPhone(contactPhone != null && !contactPhone.isEmpty()
                ? contactPhone : quotation.getContactPhone());
        order.setOrderDate(new Date());
        order.setOrderType(OrderTypeEnum.SAMPLE.getCode());
        order.setSampleStatus(SampleOrderStatusEnum.CREATED.getCode());
        order.setSampleRound(1);
        // DEV-1111：打样数量前端传入优先；未传时按报价单明细数量求和默认（与 total_quantity 口径一致），无明细兜底 1
        if (sampleQty == null) {
            java.util.List<com.jjx.sales.domain.entity.SalesQuotationItem> qItems = quotationItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesQuotationItem>()
                            .eq(com.jjx.sales.domain.entity.SalesQuotationItem::getQuotationId, quotationId));
            int sum = 0;
            if (qItems != null) {
                for (com.jjx.sales.domain.entity.SalesQuotationItem it : qItems) {
                    sum += it.getQuantity() != null ? it.getQuantity() : 0;
                }
            }
            sampleQty = sum > 0 ? sum : 1;
        }
        order.setSampleQty(sampleQty);
        order.setCurrency(quotation.getCurrency());
        order.setExchangeRate(quotation.getExchangeRate());
        order.setSalesManagerId(quotation.getSalesPersonId());
        order.setSalesManagerName(quotation.getSalesPersonName());
        order.setRemark(remark);
        // DEV-806：total_quantity 不再用打样数量 sampleQty，明细复制后按明细求和
        order.setTotalQuantity(0);
        order.setTotalAmount(quotation.getTotalAmount());
        order.setFinalAmount(quotation.getFinalAmount());
        // 期望交样日期：创建时传入优先，否则继承报价单有效期
        if (deliveryDate != null && !deliveryDate.isEmpty()) {
            try {
                order.setDeliveryDate(Date.from(java.time.LocalDate.parse(deliveryDate)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant()));
            } catch (Exception e) {
                log.warn("解析期望交样日期失败: {}", deliveryDate);
            }
        } else if (quotation.getValidUntil() != null) {
            order.setDeliveryDate(Date.from(quotation.getValidUntil().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
        // 技术要求（工程打样要求）写入工程备注，打样工作台/转量产可继承
        if (techRequirement != null && !techRequirement.isEmpty()) {
            order.setEngineeringNote(techRequirement);
        }
        // 继承报价单链路追踪ID（同一业务链路）
        order.setTraceId(quotation.getTraceId());

        orderMapper.insert(order);
        log.info("从报价单[{}]创建样品单[{}] orderId={}", quotation.getQuotationNo(), orderNo, order.getOrderId());

        // 复制报价单明细到样品单（产品资料转移/转量产依赖明细，源头修复）
        copyQuotationItemsToOrder(quotationId, order.getOrderId());
        // DEV-806：统一打样聚合口径——total_quantity 按明细求和
        updateTotalQuantityByItems(order.getOrderId());

        // 报价单状态更新：已确认(2) → 已完成(9)（报价已转化为样品打样，不可重复转），并回写转换结果（DEV-594，与转订单对齐）
        try {
            SalesQuotation update = new SalesQuotation();
            update.setQuotationId(quotationId);
            update.setQuotationStatus(com.jjx.sales.enums.QuotationStatus.COMPLETED.getCode());
            update.setConvertedOrderId(order.getOrderId());
            update.setConvertTime(LocalDateTime.now());
            quotationMapper.updateById(update);
            log.info("报价单[{}] 转样品后状态 → 已完成(9)，样品单ID={}", quotation.getQuotationNo(), order.getOrderId());
        } catch (Exception e) {
            log.warn("更新报价单状态失败: {}", e.getMessage());
        }

        return order;
    }

    /**
     * 复制样品单（DEV-1114）：仅已完成/已取消终态单（已转量产7/已关闭8/已取消10）可复制，
     * 一键生成新草稿单（CREATED），复制明细，双向写操作日志（对齐 copyOrder）
     */
    @Override
    @Event(value = "sample.created", bizId = "#result.orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder copySampleOrder(Long orderId) {
        SalesOrder source = orderMapper.selectById(orderId);
        if (source == null) {
            throw new BusinessException("样品单不存在");
        }
        if (!OrderTypeEnum.SAMPLE.getCode().equals(source.getOrderType())) {
            throw new BusinessException("该单据不是样品单，不可复制");
        }
        if (source.getSampleStatus() == null
                || !java.util.Set.of(
                SampleOrderStatusEnum.TRANSFERRED.getCode(),
                SampleOrderStatusEnum.CLOSED.getCode(),
                SampleOrderStatusEnum.CANCELLED.getCode()).contains(source.getSampleStatus())) {
            throw new BusinessException("仅已完成或已取消的样品单可复制");
        }

        // 生成新样品单号
        String orderNo = redisSequenceService.generateBusinessNumber("SP", "样品单号");

        SalesOrder copy = new SalesOrder();
        copy.setOrderNo(orderNo);
        copy.setQuotationId(null);
        copy.setCustomerId(source.getCustomerId());
        copy.setCustomerName(source.getCustomerName());
        copy.setContactPerson(source.getContactPerson());
        copy.setContactPhone(source.getContactPhone());
        copy.setOrderDate(new Date());
        copy.setDeliveryDate(source.getDeliveryDate());
        copy.setOrderType(OrderTypeEnum.SAMPLE.getCode());
        copy.setSampleStatus(SampleOrderStatusEnum.CREATED.getCode());
        copy.setSampleRound(1);
        copy.setSampleQty(0);
        // 技术要求（工程打样要求）继承原单，新单可继续传承
        copy.setEngineeringNote(source.getEngineeringNote());
        copy.setCurrency(source.getCurrency());
        copy.setExchangeRate(source.getExchangeRate());
        // 销售负责人：复制人（当前登录用户），与 createSample 一致
        try {
            copy.setSalesManagerId(SecurityUtils.getUserId());
            copy.setSalesManagerName(SecurityUtils.getUsername());
        } catch (Exception ignored) {
        }
        copy.setRemark("复制自样品单[" + source.getOrderNo() + "]"
                + (source.getRemark() != null ? "\n" + source.getRemark() : ""));
        copy.setTotalQuantity(0);
        copy.setTotalAmount(source.getTotalAmount());
        copy.setFinalAmount(source.getFinalAmount());
        // 独立链路追踪（不复用原单链路）
        copy.setTraceId(java.util.UUID.randomUUID().toString().replace("-", ""));

        orderMapper.insert(copy);
        log.info("复制样品单[{}]生成新样品单[{}] orderId={}", source.getOrderNo(), orderNo, copy.getOrderId());

        // 复制产品明细（全字段）
        java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> items =
                orderProductService.getListByOrderId(orderId);
        if (items != null && !items.isEmpty()) {
            java.util.List<com.jjx.sales.domain.dto.SalesOrderProductDTO> dtos = new java.util.ArrayList<>();
            for (com.jjx.sales.domain.vo.SalesOrderProductVO it : items) {
                com.jjx.sales.domain.dto.SalesOrderProductDTO dto = new com.jjx.sales.domain.dto.SalesOrderProductDTO();
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

        // DEV-806 口径：total_quantity 按明细求和，sample_qty 同步
        updateTotalQuantityByItems(copy.getOrderId());
        try {
            int sum = 0;
            for (com.jjx.sales.domain.vo.SalesOrderProductVO v : orderProductService.getListByOrderId(copy.getOrderId())) {
                sum += v.getQuantity() != null ? v.getQuantity() : 0;
            }
            SalesOrder u = new SalesOrder();
            u.setOrderId(copy.getOrderId());
            u.setSampleQty(sum);
            orderMapper.updateById(u);
        } catch (Exception e) {
            log.warn("同步 sample_qty 失败: {}", e.getMessage());
        }

        // 双向操作日志：新单一条（带新单 traceId）+ 原单一条（带原单 traceId），双向可查
        try {
            SysOperLog newLog = new SysOperLog();
            newLog.setModule("样品单管理");
            newLog.setBusinessType(1); // 新增
            newLog.setOperUrl("/sales/sample-order/copy/" + orderId);
            newLog.setBizType("sample");
            newLog.setBizId(String.valueOf(copy.getOrderId()));
            newLog.setTraceId(copy.getTraceId());
            newLog.setBizStatus(copy.getSampleStatus());
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
        try {
            SysOperLog operLog = new SysOperLog();
            operLog.setModule("样品单管理");
            operLog.setBusinessType(2); // 修改
            operLog.setOperUrl("/sales/sample-order/copy/" + orderId);
            operLog.setBizType("sample");
            operLog.setBizId(String.valueOf(orderId));
            operLog.setTraceId(source.getTraceId());
            operLog.setBizStatus(source.getSampleStatus());
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

        log.info("样品单[{}]复制成功，生成新样品单[{}](orderId={})", source.getOrderNo(), copy.getOrderNo(), copy.getOrderId());
        return copy;
    }

    @Override
    @Event(value = "sample.created", bizId = "#result.orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder createSample(com.jjx.sales.domain.dto.SampleOrderCreateDTO dto) {
        if (dto == null || dto.getCustomerId() == null) {
            throw new BusinessException("客户不能为空");
        }
        com.jjx.sales.domain.entity.SalesCustomer customer = customerMapper.selectById(dto.getCustomerId());
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }

        // 可选来源报价单校验
        SalesQuotation quotation = null;
        if (dto.getQuotationId() != null) {
            quotation = quotationMapper.selectById(dto.getQuotationId());
            if (quotation == null || quotation.getDeleted() == 1) {
                throw new BusinessException("报价单不存在");
            }
            if (quotation.getQuotationStatus() != null
                    && quotation.getQuotationStatus() == com.jjx.sales.enums.QuotationStatus.COMPLETED.getCode()) {
                throw new BusinessException("报价单已完成，不可重复转样品单");
            }
            if (quotation.getQuotationStatus() == null
                    || quotation.getQuotationStatus() != com.jjx.sales.enums.QuotationStatus.ACCEPTED.getCode()) {
                throw new BusinessException("只有客户已确认的报价单可以转为样品单");
            }
        }

        // 生成样品单号
        String orderNo = redisSequenceService.generateBusinessNumber("SP", "样品单号");

        SalesOrder order = new SalesOrder();
        order.setOrderNo(orderNo);
        order.setQuotationId(dto.getQuotationId());
        order.setCustomerId(customer.getCustomerId());
        order.setCustomerName(customer.getCustomerName());
        // 联系人/电话：前端传入 > 报价单 > 客户档案
        order.setContactPerson(dto.getContactPerson() != null && !dto.getContactPerson().isEmpty()
                ? dto.getContactPerson()
                : (quotation != null && quotation.getContactPerson() != null ? quotation.getContactPerson() : customer.getContactPerson()));
        order.setContactPhone(dto.getContactPhone() != null && !dto.getContactPhone().isEmpty()
                ? dto.getContactPhone()
                : (quotation != null && quotation.getContactPhone() != null ? quotation.getContactPhone() : customer.getContactPhone()));
        order.setOrderDate(new Date());
        order.setOrderType(OrderTypeEnum.SAMPLE.getCode());
        order.setSampleStatus(SampleOrderStatusEnum.CREATED.getCode());
        order.setSampleRound(1);
        order.setCurrency(quotation != null ? quotation.getCurrency() : "CNY");
        order.setExchangeRate(quotation != null ? quotation.getExchangeRate() : java.math.BigDecimal.ONE);
        try {
            order.setSalesManagerId(SecurityUtils.getUserId());
            order.setSalesManagerName(SecurityUtils.getUsername());
        } catch (Exception ignored) {
        }
        order.setRemark(dto.getRemark());
        order.setTotalQuantity(0);
        order.setTotalAmount(quotation != null ? quotation.getTotalAmount() : null);
        order.setFinalAmount(quotation != null ? quotation.getFinalAmount() : null);
        // 期望交样日期：前端传入 > 报价单有效期
        if (dto.getDeliveryDate() != null && !dto.getDeliveryDate().isEmpty()) {
            try {
                order.setDeliveryDate(Date.from(java.time.LocalDate.parse(dto.getDeliveryDate())
                        .atStartOfDay(ZoneId.systemDefault()).toInstant()));
            } catch (Exception e) {
                log.warn("解析期望交样日期失败: {}", dto.getDeliveryDate());
            }
        } else if (quotation != null && quotation.getValidUntil() != null) {
            order.setDeliveryDate(Date.from(quotation.getValidUntil().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
        // 技术要求写入工程备注（传承打样工作台/转量产）
        if (dto.getTechRequirement() != null && !dto.getTechRequirement().isEmpty()) {
            order.setEngineeringNote(dto.getTechRequirement());
        }
        // 链路：带报价单继承报价单 trace_id，否则新建独立链路
        order.setTraceId(quotation != null && quotation.getTraceId() != null
                ? quotation.getTraceId()
                : java.util.UUID.randomUUID().toString().replace("-", ""));

        orderMapper.insert(order);
        log.info("新增样品单[{}] orderId={}，客户={}", orderNo, order.getOrderId(), customer.getCustomerName());

        // 明细：前端传 items 优先；带报价单且无 items 时从报价单复制
        java.util.List<com.jjx.sales.domain.dto.SampleOrderCreateDTO.Item> items = dto.getItems();
        if (items != null && !items.isEmpty()) {
            java.util.List<com.jjx.sales.domain.dto.SalesOrderProductDTO> addList = new java.util.ArrayList<>();
            for (com.jjx.sales.domain.dto.SampleOrderCreateDTO.Item it : items) {
                com.jjx.sales.domain.dto.SalesOrderProductDTO d = new com.jjx.sales.domain.dto.SalesOrderProductDTO();
                d.setOrderId(order.getOrderId());
                d.setProductId(it.getProductId());
                d.setProductCode(it.getProductCode());
                d.setProductName(it.getProductName());
                d.setQuantity(it.getQuantity());
                d.setUnit(it.getUnit() != null ? it.getUnit() : "PCS");
                d.setUnitPrice(java.math.BigDecimal.ZERO);
                d.setAmount(java.math.BigDecimal.ZERO);
                addList.add(d);
            }
            orderProductService.batchAdd(addList);
        } else if (quotation != null) {
            copyQuotationItemsToOrder(quotation.getQuotationId(), order.getOrderId());
        }
        // DEV-806：total_quantity = 明细求和；sample_qty 同步
        updateTotalQuantityByItems(order.getOrderId());
        try {
            int sum = 0;
            for (com.jjx.sales.domain.vo.SalesOrderProductVO v : orderProductService.getListByOrderId(order.getOrderId())) {
                sum += v.getQuantity() != null ? v.getQuantity() : 0;
            }
            SalesOrder u = new SalesOrder();
            u.setOrderId(order.getOrderId());
            u.setSampleQty(sum);
            orderMapper.updateById(u);
        } catch (Exception e) {
            log.warn("同步 sample_qty 失败: {}", e.getMessage());
        }

        // 带报价单：状态已确认(2)→已完成(9) + 回写 convertedOrderId
        if (quotation != null) {
            try {
                SalesQuotation update = new SalesQuotation();
                update.setQuotationId(quotation.getQuotationId());
                update.setQuotationStatus(com.jjx.sales.enums.QuotationStatus.COMPLETED.getCode());
                update.setConvertedOrderId(order.getOrderId());
                update.setConvertTime(LocalDateTime.now());
                quotationMapper.updateById(update);
                log.info("报价单[{}] 转样品后状态 → 已完成(9)，样品单ID={}", quotation.getQuotationNo(), order.getOrderId());
            } catch (Exception e) {
                log.warn("更新报价单状态失败: {}", e.getMessage());
            }
        }

        return order;
    }

    @Override
    @Event(value = "sample.submitted", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder submitReview(Long orderId) {
        safeTransition(orderId,
                SampleOrderStatusEnum.CREATED,
                SampleOrderStatusEnum.PENDING_REVIEW,
                "提交审核");
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.approved", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder approveReview(Long orderId, String remark) {
        safeTransition(orderId,
                SampleOrderStatusEnum.PENDING_REVIEW,
                SampleOrderStatusEnum.ENGINEERING,
                "审核通过");

        // 更新审核信息
        try {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setRemark(remark);
            orderMapper.updateById(update);
        } catch (Exception e) {
            log.warn("更新样品单审核备注失败: {}", e.getMessage());
        }

        log.info("样品单[{}] 审核通过，进入工程打样阶段", orderId);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.rejected", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder rejectReview(Long orderId, String remark) {
        // 审核驳回：回到创建状态(1)，销售可改单后重新提交（语义与客户退回9区分）
        safeTransition(orderId,
                SampleOrderStatusEnum.PENDING_REVIEW,
                SampleOrderStatusEnum.CREATED,
                "审核驳回");
        // 记录驳回原因
        try {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setRemark("[审核驳回] " + (remark != null ? remark : ""));
            orderMapper.updateById(update);
        } catch (Exception e) {
            log.warn("记录审核驳回原因失败: {}", e.getMessage());
        }
        return orderMapper.selectById(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder startEngineering(Long orderId, String engineeringNote) {
        // 只有 ENGINEERING 状态能设置工程备注
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(order.getSampleStatus())) {
            throw new BusinessException("当前状态不可进行工程接单操作");
        }

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setEngineeringNote(engineeringNote);
        orderMapper.updateById(update);

        log.info("样品单[{}] 工程接单，工程备注已记录", orderId);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.ready", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder markSampleReady(Long orderId, Integer sampleQty) {
        // 前置校验（DEV-491）：① 必须已工程接单
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (current.getEngineeringAcceptor() == null || current.getEngineeringAcceptor().isEmpty()) {
            throw new BusinessException("请先工程接单，再进行样品完成");
        }
        // ② 当前轮次至少 1 条工序记录（DEV-500 轮次语义对齐）
        Integer roundNo = current.getSampleRound() != null ? current.getSampleRound() : 1;
        java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> roundProcs =
                sampleProcessMapper.selectByOrderAndRound(orderId, roundNo);
        if (roundProcs == null || roundProcs.isEmpty()) {
            throw new BusinessException("当前轮次(Round" + roundNo + ")无工序记录，请先录入工序再标记完成");
        }

        safeTransition(orderId,
                SampleOrderStatusEnum.ENGINEERING,
                SampleOrderStatusEnum.SAMPLE_READY,
                "标记样品完成");

        if (sampleQty != null) {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setSampleQty(sampleQty);
            orderMapper.updateById(update);
        }

        // 归档本轮快照（工艺参数 + 图纸附件 + BOM + 工序，DEV-456 补全）
        try {
            SalesOrder full = orderMapper.selectById(orderId);
            com.jjx.sales.domain.entity.SalesSampleRound round = new com.jjx.sales.domain.entity.SalesSampleRound();
            round.setOrderId(orderId);
            round.setRoundNo(full.getSampleRound() != null ? full.getSampleRound() : 1);
            round.setEngineeringNote(full.getEngineeringNote());
            round.setResult("pending");
            round.setCreateTime(java.time.LocalDateTime.now());

            // ① 图纸附件ID：按 traceId 查附件，写入 attachmentIds(JSON数组)
            if (full.getTraceId() != null) {
                try {
                    java.util.List<com.jjx.system.domain.entity.SysAttachment> atts =
                            attachmentService.getAttachmentsByTraceId(full.getTraceId());
                    if (atts != null && !atts.isEmpty()) {
                        java.util.List<Long> ids = new java.util.ArrayList<>();
                        for (com.jjx.system.domain.entity.SysAttachment a : atts) {
                            if (a.getId() != null) ids.add(a.getId());
                        }
                        if (!ids.isEmpty()) {
                            round.setAttachmentIds(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ids));
                        }
                    }
                } catch (Exception ae) {
                    log.warn("归档图纸附件失败: {}", ae.getMessage());
                }
            }

            // ② BOM 物料快照（从工序单元材料聚合）
            try {
                java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> procs =
                        sampleProcessMapper.selectByOrderId(orderId);
                java.util.List<java.util.Map<String, Object>> aggMats = new java.util.ArrayList<>();
                if (procs != null) {
                    for (com.jjx.sales.domain.entity.SalesSampleProcess sp : procs) {
                        if (sp.getMaterials() == null || sp.getMaterials().isEmpty()) continue;
                        try {
                            java.util.List<java.util.Map<String, Object>> mats =
                                    new com.fasterxml.jackson.databind.ObjectMapper().readValue(sp.getMaterials(),
                                            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                            for (java.util.Map<String, Object> m : mats) {
                                java.util.Map<String, Object> agg = new java.util.LinkedHashMap<>();
                                agg.put("process", sp.getProcessName());
                                agg.putAll(m);
                                aggMats.add(agg);
                            }
                        } catch (Exception pe) {
                            log.warn("解析工序材料失败: {}", pe.getMessage());
                        }
                    }
                }
                if (!aggMats.isEmpty()) {
                    round.setBomSnapshot(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(aggMats));
                }
            } catch (Exception be) {
                log.warn("归档BOM快照失败: {}", be.getMessage());
            }

            // ③ 工序记录快照
            try {
                java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> procs = sampleProcessMapper.selectByOrderId(orderId);
                if (procs != null && !procs.isEmpty()) {
                    round.setProcessSnapshot(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(procs));
                }
            } catch (Exception pe) {
                log.warn("归档工序快照失败: {}", pe.getMessage());
            }

            sampleRoundMapper.insert(round);
            log.info("样品单[{}] 归档轮次快照 round={} (附件{}个/BOM{}条/工序{}条)",
                    orderId, round.getRoundNo(),
                    round.getAttachmentIds() != null ? "有" : "无",
                    round.getBomSnapshot() != null ? "有" : "无",
                    round.getProcessSnapshot() != null ? "有" : "无");
        } catch (Exception e) {
            log.warn("归档样品轮次快照失败: {}", e.getMessage());
        }

        log.info("样品单[{}] 样品制作完成，待送样", orderId);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.sent", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder sendSample(Long orderId, String trackingNo) {
        safeTransition(orderId,
                SampleOrderStatusEnum.SAMPLE_READY,
                SampleOrderStatusEnum.SAMPLE_SENT,
                "送样");

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setSampleTrackingNo(trackingNo);
        update.setSampleSendDate(new Date());
        orderMapper.updateById(update);

        log.info("样品单[{}] 已送样，快递单号:{}", orderId, trackingNo);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.confirmed", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder confirmSample(Long orderId, String clientName) {
        safeTransition(orderId,
                SampleOrderStatusEnum.SAMPLE_SENT,
                SampleOrderStatusEnum.CONFIRMED,
                "客户确认样品");

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setSampleClientName(clientName);
        update.setSampleConfirmDate(new Date());
        orderMapper.updateById(update);

        // 更新本轮快照结果：confirmed
        try {
            List<com.jjx.sales.domain.entity.SalesSampleRound> rounds = sampleRoundMapper.selectByOrderId(orderId);
            if (!rounds.isEmpty()) {
                com.jjx.sales.domain.entity.SalesSampleRound latest = rounds.get(rounds.size() - 1);
                if ("pending".equals(latest.getResult())) {
                    latest.setResult("confirmed");
                    sampleRoundMapper.updateById(latest);
                }
            }
        } catch (Exception e) {
            log.warn("更新轮次快照确认结果失败: {}", e.getMessage());
        }

        log.info("样品单[{}] 客户[{}]已确认样品OK", orderId, clientName);
        return orderMapper.selectById(orderId);
    }

    @Override
    @Event(value = "sample.rejected_by_customer", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder rejectSample(Long orderId, String rejectReason) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        // 只能从 SAMPLE_SENT 退回（已送样的状态下退回）
        SampleOrderStatusEnum currentStatus = SampleOrderStatusEnum.getByCodeSafe(current.getSampleStatus())
                .orElseThrow(() -> new BusinessException("当前状态不可退回"));

        if (currentStatus != SampleOrderStatusEnum.SAMPLE_SENT) {
            throw new BusinessException("当前状态不可退回，仅已送样待确认的样品可退回");
        }

        orderMapper.updateSampleStatus(orderId, SampleOrderStatusEnum.SAMPLE_SENT.getCode(),
                SampleOrderStatusEnum.REJECTED.getCode());

        // 退回轮次+1，并退回工程阶段
        int nextRound = (current.getSampleRound() != null ? current.getSampleRound() : 1) + 1;
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setSampleRound(nextRound);
        update.setRemark(rejectReason);
        orderMapper.updateById(update);

        // 更新本轮快照结果：rejected + 退回原因
        try {
            List<com.jjx.sales.domain.entity.SalesSampleRound> rounds = sampleRoundMapper.selectByOrderId(orderId);
            if (!rounds.isEmpty()) {
                com.jjx.sales.domain.entity.SalesSampleRound latest = rounds.get(rounds.size() - 1);
                if ("pending".equals(latest.getResult())) {
                    latest.setResult("rejected");
                    latest.setRejectReason(rejectReason);
                    sampleRoundMapper.updateById(latest);
                }
            }
        } catch (Exception e) {
            log.warn("更新轮次快照结果失败: {}", e.getMessage());
        }

        log.info("样品单[{}] 客户退回(Round{})，退回原因:{}", orderId, nextRound, rejectReason);
        return orderMapper.selectById(orderId);
    }

    /**
     * 退回后重新打样（REJECTED → ENGINEERING，轮次已+1）
     */
    @Override
    @Event(value = "sample.restarted", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder restartEngineering(Long orderId) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        // 只有客户退回(9)状态可以重新打样
        if (!SampleOrderStatusEnum.REJECTED.getCode().equals(current.getSampleStatus())) {
            String name = SampleOrderStatusEnum.getByCodeSafe(current.getSampleStatus())
                    .map(SampleOrderStatusEnum::getName).orElse("未知");
            throw new BusinessException("当前状态[" + name + "]不可重新打样，仅客户退回状态可重新打样");
        }

        int affected = orderMapper.updateSampleStatus(orderId, SampleOrderStatusEnum.REJECTED.getCode(),
                SampleOrderStatusEnum.ENGINEERING.getCode());
        if (affected == 0) {
            throw new BusinessException("样品单状态已变更，无法重新打样，请刷新后重试");
        }

        log.info("样品单[{}] 重新打样(Round{})，回到工程打样阶段", current.getOrderNo(),
                current.getSampleRound() != null ? current.getSampleRound() : 1);
        return orderMapper.selectById(orderId);
    }

    /**
     * 工程接单确认
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder acceptEngineering(Long orderId, String acceptorName) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(current.getSampleStatus())) {
            throw new BusinessException("当前状态不可接单，仅工程打样中状态可接单");
        }

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setEngineeringAcceptor(acceptorName);
        update.setEngineeringAcceptTime(new Date());
        orderMapper.updateById(update);

        log.info("样品单[{}] 工程接单: {}", current.getOrderNo(), acceptorName);
        return orderMapper.selectById(orderId);
    }

    /**
     * 工程拒单（回退到待审核，销售可改单重提）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder rejectEngineering(Long orderId, String rejectReason) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(current.getSampleStatus())) {
            throw new BusinessException("当前状态不可拒单，仅工程打样中状态可拒单");
        }
        if (rejectReason == null || rejectReason.trim().isEmpty()) {
            throw new BusinessException("拒单原因不能为空");
        }

        int affected = orderMapper.updateSampleStatus(orderId, SampleOrderStatusEnum.ENGINEERING.getCode(),
                SampleOrderStatusEnum.PENDING_REVIEW.getCode());
        if (affected == 0) {
            throw new BusinessException("样品单状态已变更，无法拒单，请刷新后重试");
        }

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setRejectReason(rejectReason);
        orderMapper.updateById(update);

        log.info("样品单[{}] 工程拒单: {}", current.getOrderNo(), rejectReason);
        return orderMapper.selectById(orderId);
    }

    /**
     * 更新打样当前工序
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder updateSampleProcess(Long orderId, String process, String materials, String processNote, Integer durationMinutes) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(current.getSampleStatus())
                && !SampleOrderStatusEnum.SAMPLE_READY.getCode().equals(current.getSampleStatus())
                && !SampleOrderStatusEnum.SAMPLE_SENT.getCode().equals(current.getSampleStatus())) {
            throw new BusinessException("当前状态不可更新工序进度");
        }

        // 记录工序历史（追加，不覆盖）— 工序单元化：材料+工艺说明+耗时
        SalesSampleProcess record = new SalesSampleProcess();
        record.setOrderId(orderId);
        record.setRoundNo(current.getSampleRound() != null ? current.getSampleRound() : 1);
        record.setProcessName(process);
        record.setMaterials(materials);
        record.setProcessNote(processNote);
        record.setOperator(SecurityUtils.getUsername());
        record.setStartTime(LocalDateTime.now());
        record.setDurationMinutes(durationMinutes);
        record.setRemark("工序进度更新");
        sampleProcessMapper.insert(record);

        // 更新当前工序字段（用于列表展示当前进度）
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setCurrentProcess(process);
        orderMapper.updateById(update);

        log.info("样品单[{}] 更新当前工序: {} (已记录历史)", current.getOrderNo(), process);
        return orderMapper.selectById(orderId);
    }

    /**
     * 查询打样工序历史
     */
    @Override
    @Transactional(readOnly = true)
    public List<SalesSampleProcess> listSampleProcesses(Long orderId) {
        return sampleProcessMapper.selectByOrderId(orderId);
    }

    /**
     * 按轮次查询打样工序（DEV-500）
     */
    @Override
    @Transactional(readOnly = true)
    public List<SalesSampleProcess> listSampleProcesses(Long orderId, Integer roundNo) {
        if (roundNo == null) {
            return sampleProcessMapper.selectByOrderId(orderId);
        }
        return sampleProcessMapper.selectByOrderAndRound(orderId, roundNo);
    }

    /**
     * 保存打样工序计划（方案A：多选作业项目形成计划，整单覆盖当前轮次）
     */
    @Override
    @Transactional
    public List<SalesSampleProcess> saveProcessPlan(Long orderId, com.jjx.sales.dto.save.SampleProcessPlanDTO dto) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (!SampleOrderStatusEnum.ENGINEERING.getCode().equals(current.getSampleStatus())
                && !SampleOrderStatusEnum.SAMPLE_READY.getCode().equals(current.getSampleStatus())
                && !SampleOrderStatusEnum.SAMPLE_SENT.getCode().equals(current.getSampleStatus())
                && !SampleOrderStatusEnum.CONFIRMED.getCode().equals(current.getSampleStatus())
                && !SampleOrderStatusEnum.TRANSFERRED.getCode().equals(current.getSampleStatus())) {
            throw new BusinessException("当前状态不可保存工序计划");
        }
        Integer roundNo = dto.getRoundNo() != null ? dto.getRoundNo()
                : (current.getSampleRound() != null ? current.getSampleRound() : 1);

        // 覆盖式保存：删除该轮次旧计划后整单重插
        sampleProcessMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SalesSampleProcess>()
                .eq(SalesSampleProcess::getOrderId, orderId)
                .eq(SalesSampleProcess::getRoundNo, roundNo));

        if (dto.getItems() != null) {
            int order = 1;
            for (com.jjx.sales.dto.save.SampleProcessPlanDTO.Item item : dto.getItems()) {
                if (item.getProcessName() == null || item.getProcessName().isEmpty()) {
                    continue;
                }
                SalesSampleProcess record = new SalesSampleProcess();
                record.setOrderId(orderId);
                record.setRoundNo(roundNo);
                record.setProcessName(item.getProcessName());
                record.setStdProcessId(item.getStdProcessId());
                // 一级大类（dev-20260811-009）：缺省按标准工序归属 ASSEMBLY，印刷工序传 PRINT
                record.setMajorCategory(item.getMajorCategory() != null ? item.getMajorCategory() : "ASSEMBLY");
                // 定制工艺参数 JSON（dev-20260811-009）：印刷 {printName,colorNo,inkNo,screenNo}
                record.setCustomProcessParams(item.getCustomProcessParams());
                // 下标（DEV-777）：index_number 前端直传；has_index 按 std_process_id 关联标准工序取
                record.setIndexNumber(item.getIndexNumber());
                if (item.getStdProcessId() != null) {
                    try {
                        com.jjx.product.domain.entity.ProductStandardProcess stdProc =
                                standardProcessMapper.selectById(item.getStdProcessId());
                        record.setHasIndex(stdProc != null && stdProc.getHasIndex() != null ? stdProc.getHasIndex() : 0);
                    } catch (Exception ignored) {
                        record.setHasIndex(0);
                    }
                } else {
                    record.setHasIndex(0);
                }
                // 卡片组合：同一卡片多个作业项目传相同 processOrder（缺省按列表顺序）
                record.setProcessOrder(item.getProcessOrder() != null && item.getProcessOrder() > 0
                        ? item.getProcessOrder() : order++);
                // 卡片项目结构（卡片级主结构）
                record.setProcessCategory(item.getProcessCategory());
                record.setStatus(item.getStatus() != null ? item.getStatus() : 0);
                record.setMaterials(item.getMaterials());
                record.setProcessNote(item.getProcessNote());
                record.setOperator(SecurityUtils.getUsername());
                record.setRemark("工序计划");
                sampleProcessMapper.insert(record);
            }
        }

        // 同步当前工序字段：优先进行中，其次第一个待做，全完成则最后一道
        List<SalesSampleProcess> plan = sampleProcessMapper.selectByOrderAndRound(orderId, roundNo);
        syncCurrentProcess(orderId, plan);

        log.info("样品单[{}] 保存打样工序计划: Round{} 共{}道", orderId, roundNo, plan.size());
        return plan;
    }

    /**
     * 推进打样工序状态（方案A：逐项开始/完成）
     */
    @Override
    @Transactional
    public SalesSampleProcess updateProcessItemStatus(Long orderId, Long processId, com.jjx.sales.dto.save.SampleProcessItemStatusDTO dto) {
        SalesSampleProcess record = sampleProcessMapper.selectById(processId);
        if (record == null || !orderId.equals(record.getOrderId())) {
            throw new BusinessException("工序记录不存在");
        }
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        Integer status = dto.getStatus() != null ? dto.getStatus() : 2;
        if (status < 0 || status > 2) {
            throw new BusinessException("无效的工序状态");
        }

        if (status == 1 && record.getStartTime() == null) {
            record.setStartTime(LocalDateTime.now());
        }
        if (status == 2) {
            if (record.getStartTime() == null) {
                record.setStartTime(LocalDateTime.now());
            }
            record.setEndTime(LocalDateTime.now());
            if (dto.getDurationMinutes() != null) {
                record.setDurationMinutes(dto.getDurationMinutes());
            } else if (record.getDurationMinutes() == null) {
                long mins = java.time.Duration.between(record.getStartTime(), LocalDateTime.now()).toMinutes();
                record.setDurationMinutes((int) Math.max(1, mins));
            }
        }
        record.setStatus(status);
        if (dto.getProcessNote() != null) {
            record.setProcessNote(dto.getProcessNote());
        }
        if (dto.getMaterials() != null) {
            record.setMaterials(dto.getMaterials());
        }
        record.setOperator(SecurityUtils.getUsername());
        record.setRemark("工序状态推进");
        sampleProcessMapper.updateById(record);

        // 完成后同步当前工序：指向下一个待做工序
        if (status == 2) {
            List<SalesSampleProcess> plan = sampleProcessMapper.selectByOrderAndRound(orderId, record.getRoundNo());
            syncCurrentProcess(orderId, plan);
        }
        return sampleProcessMapper.selectById(processId);
    }

    /**
     * 同步当前工序字段：优先进行中，其次第一个待做，全完成则最后一道
     */
    private void syncCurrentProcess(Long orderId, List<SalesSampleProcess> plan) {
        String current = null;
        if (plan != null && !plan.isEmpty()) {
            current = plan.stream()
                    .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                    .min(java.util.Comparator.comparing(p -> p.getProcessOrder() != null ? p.getProcessOrder() : 0))
                    .map(SalesSampleProcess::getProcessName)
                    .orElseGet(() -> plan.stream()
                            .filter(p -> p.getStatus() == null || p.getStatus() == 0)
                            .min(java.util.Comparator.comparing(p -> p.getProcessOrder() != null ? p.getProcessOrder() : 0))
                            .map(SalesSampleProcess::getProcessName)
                            .orElseGet(() -> plan.stream()
                                    .max(java.util.Comparator.comparing(p -> p.getProcessOrder() != null ? p.getProcessOrder() : 0))
                                    .map(SalesSampleProcess::getProcessName)
                                    .orElse(null)));
        }
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setCurrentProcess(current);
        orderMapper.updateById(update);
    }

    /**
     * 打样汇总（DEV-454 增强）：总工时 + 材料成本估算（自动计算，不手填）
     */
    @Override
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getSampleSummary(Long orderId) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        java.util.List<SalesSampleProcess> processes = sampleProcessMapper.selectByOrderId(orderId);

        // 总工时（各工序 durationMinutes 之和，分钟→小时）
        int totalMinutes = 0;
        java.util.Map<String, java.util.Map<String, Object>> materialAgg = new java.util.LinkedHashMap<>();
        for (SalesSampleProcess sp : processes) {
            if (sp.getDurationMinutes() != null) totalMinutes += sp.getDurationMinutes();
            if (sp.getMaterials() == null || sp.getMaterials().isEmpty()) continue;
            try {
                java.util.List<java.util.Map<String, Object>> mats =
                        new com.fasterxml.jackson.databind.ObjectMapper().readValue(sp.getMaterials(),
                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                for (java.util.Map<String, Object> m : mats) {
                    String name = m.get("name") != null ? m.get("name").toString() : null;
                    if (name == null) continue;
                    String key = name + "|" + (m.get("spec") != null ? m.get("spec") : "");
                    java.util.Map<String, Object> agg = materialAgg.get(key);
                    if (agg == null) {
                        agg = new java.util.LinkedHashMap<>();
                        agg.put("name", name);
                        agg.put("spec", m.get("spec"));
                        agg.put("unit", m.get("unit") != null ? m.get("unit") : "PCS");
                        agg.put("qty", java.math.BigDecimal.ZERO);
                        // 匹配物料标准价
                        try {
                            com.jjx.inventory.domain.InventoryMaterial mat = inventoryMaterialMapper.selectOne(
                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                                            .eq(com.jjx.inventory.domain.InventoryMaterial::getMaterialName, name)
                                            .last("LIMIT 1"));
                            if (mat != null && mat.getStandardPrice() != null) {
                                agg.put("unitPrice", mat.getStandardPrice());
                            }
                        } catch (Exception me) { /* ignore */ }
                        materialAgg.put(key, agg);
                    }
                    if (m.get("qty") != null) {
                        try {
                            java.math.BigDecimal qty = (java.math.BigDecimal) agg.get("qty");
                            agg.put("qty", qty.add(new java.math.BigDecimal(m.get("qty").toString())));
                        } catch (Exception qe) { /* ignore */ }
                    }
                }
            } catch (Exception pe) {
                log.warn("解析工序材料失败: {}", pe.getMessage());
            }
        }

        // 材料成本估算
        java.math.BigDecimal materialCost = java.math.BigDecimal.ZERO;
        for (java.util.Map<String, Object> agg : materialAgg.values()) {
            if (agg.get("unitPrice") != null && agg.get("qty") != null) {
                try {
                    materialCost = materialCost.add(
                            ((java.math.BigDecimal) agg.get("qty")).multiply((java.math.BigDecimal) agg.get("unitPrice")));
                } catch (Exception e) { /* ignore */ }
            }
        }

        result.put("totalMinutes", totalMinutes);
        result.put("totalHours", java.math.BigDecimal.valueOf(totalMinutes).divide(java.math.BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP));
        result.put("materialCount", materialAgg.size());
        result.put("materials", new java.util.ArrayList<>(materialAgg.values()));
        result.put("materialCost", materialCost.setScale(2, java.math.RoundingMode.HALF_UP));
        result.put("processCount", processes.size());
        return result;
    }

    /**
     * 查询打样BOM物料清单
     */
    @Override
    @Transactional(readOnly = true)
    public List<SalesSampleBom> listSampleBom(Long orderId) {
        return sampleBomMapper.selectByOrderId(orderId);
    }

    /**
     * 保存打样BOM物料（覆盖当前轮次：先删后插，保持结构化一致）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SalesSampleBom> saveSampleBom(Long orderId, Integer roundNo, List<SalesSampleBom> items) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("物料清单不能为空");
        }

        int round = roundNo != null ? roundNo : (current.getSampleRound() != null ? current.getSampleRound() : 1);
        // 覆盖当前轮次：先删旧记录
        sampleBomMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SalesSampleBom>()
                .eq(SalesSampleBom::getOrderId, orderId)
                .eq(SalesSampleBom::getRoundNo, round));

        String username = SecurityUtils.getUsername();
        for (SalesSampleBom item : items) {
            if (item.getLayerName() == null || item.getMaterialName() == null) {
                throw new BusinessException("层结构和物料名称必填");
            }
            item.setBomId(null);
            item.setOrderId(orderId);
            item.setRoundNo(round);
            item.setCreateBy(username);
            if (item.getQuantity() == null) item.setQuantity(java.math.BigDecimal.ONE);
            if (item.getUnit() == null) item.setUnit("PCS");
            sampleBomMapper.insert(item);
        }
        log.info("样品单[{}] 保存打样BOM {} 条 (round={})", orderId, items.size(), round);
        return sampleBomMapper.selectByOrderId(orderId);
    }

    /**
     * 删除单条打样BOM
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteSampleBomItem(Long bomId) {
        return sampleBomMapper.deleteById(bomId) > 0;
    }

    /**
     * 录入打样成本/工时
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder recordSampleCost(Long orderId, java.math.BigDecimal cost, java.math.BigDecimal workHours) {
        SalesOrder current = orderMapper.selectById(orderId);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        if (cost != null) update.setSampleCost(cost);
        if (workHours != null) update.setSampleWorkHours(workHours);
        orderMapper.updateById(update);

        log.info("样品单[{}] 录入打样成本: {}, 工时: {}h", current.getOrderNo(), cost, workHours);
        return orderMapper.selectById(orderId);
    }

    /**
     * 查询打样轮次快照列表
     */
    @Override
    public List<com.jjx.sales.domain.entity.SalesSampleRound> listSampleRounds(Long orderId) {
        return sampleRoundMapper.selectByOrderId(orderId);
    }

    /**
     * 产品资料转移（DEV-505）：样品确认后，把打样成果建档为正式产品/BOM/工艺路线
     * 状态全部初始化（产品=待审核，BOM/路线=草稿），事件通知+派任务由工程完善后提交审核
     */
    @Override
    @Event(value = "sample.transferred", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public java.util.Map<String, Object> transferMaterials(Long orderId) {
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        if (sampleOrder == null || sampleOrder.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        // 已确认(6)或已转量产(7)可转移（转量产后可补转移）
        Integer st = sampleOrder.getSampleStatus();
        if (!SampleOrderStatusEnum.CONFIRMED.getCode().equals(st)
                && !SampleOrderStatusEnum.TRANSFERRED.getCode().equals(st)) {
            throw new BusinessException("仅已确认或已转量产的样品单可进行资料转移");
        }

        String transferNo = redisSequenceService.generateBusinessNumber("TF", "资料转移单号");
        String transferredVersion = null; // 本次转移生成的版本号（回填打样单）
        java.util.List<String> details = new java.util.ArrayList<>();
        String productAction = "NONE", bomAction = "NONE", routingAction = "NONE";
        Long builtProductId = null, builtBomId = null, builtRoutingId = null;

        // 明细：样品单若无明细则从报价单补复制（数据断链兜底）
        java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> prodList =
                orderProductService.getListByOrderId(orderId);
        if (prodList == null || prodList.isEmpty()) {
            if (sampleOrder.getQuotationId() != null) {
                copyQuotationItemsToOrder(sampleOrder.getQuotationId(), orderId);
                updateTotalQuantityByItems(orderId);
                prodList = orderProductService.getListByOrderId(orderId);
            }
        }
        // DEV-500 联动：聚合源用最新轮次工序（避免旧轮次试错工序混入量产BOM/路线）
        // 2026-08-10 DEV-766：聚合口径统一为「最新轮次全量工序」（与 previewTransfer/confirmTransfer 一致），
        // 不再过滤状态（旧逻辑：有已完成工序则只取已完成）——由用户在对照版/轻量版弹窗中自行删减
        java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> allProcesses =
                sampleProcessMapper.selectByOrderId(orderId);
        java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> processes = allProcesses;
        if (allProcesses != null && !allProcesses.isEmpty()) {
            Integer latestRound = allProcesses.stream()
                    .map(com.jjx.sales.domain.entity.SalesSampleProcess::getRoundNo)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo).orElse(null);
            if (latestRound != null) {
                processes = allProcesses.stream()
                        .filter(p -> latestRound.equals(p.getRoundNo()))
                        .collect(java.util.stream.Collectors.toList());
                // 按工序顺序排序（计划模式），无顺序的旧数据按记录ID排尾部
                processes.sort(java.util.Comparator.comparing(
                                (com.jjx.sales.domain.entity.SalesSampleProcess p) ->
                                        p.getProcessOrder() != null && p.getProcessOrder() > 0 ? p.getProcessOrder() : 999999)
                        .thenComparing(p -> p.getProcessId() != null ? p.getProcessId() : 0L));
                log.info("样品单[{}] 资料转移聚合源：最新轮次Round{} (全量{}条)",
                        orderId, latestRound, processes.size());
            }
        }

        if (prodList != null) {
            for (com.jjx.sales.domain.vo.SalesOrderProductVO prod : prodList) {
                Long pid = prod.getProductId();

                // ===== ① 产品建档：无则建草稿（待审核），有则核对 =====
                com.jjx.product.domain.entity.Product product = pid != null ? productMapper.selectById(pid) : null;
                if (product == null) {
                    com.jjx.product.domain.entity.Product np = new com.jjx.product.domain.entity.Product();
                    np.setProductCode(prod.getProductCode());
                    np.setProductName(prod.getProductName());
                    np.setProductStatus(2); // 待审核
                    np.setUnit(prod.getUnit());
                    np.setCreateBy(SecurityUtils.getUsername());
                    productMapper.insert(np);
                    pid = np.getProductId();
                    productAction = "CREATE";
                    details.add("产品[" + prod.getProductCode() + "]新建建档(待审核)");
                    // 回写明细 productId
                    try {
                        com.jjx.sales.domain.entity.SalesOrderProduct op = new com.jjx.sales.domain.entity.SalesOrderProduct();
                        op.setId(prod.getId());
                        op.setProductId(pid);
                        orderProductMapper.updateById(op);
                    } catch (Exception e) {
                        log.warn("样品单明细回写productId失败: {}", e.getMessage());
                    }
                } else {
                    productAction = "UPDATE";
                    details.add("产品[" + prod.getProductCode() + "]已存在，档案核对");
                }
                if (builtProductId == null) builtProductId = pid;
                if (pid == null) continue;

                // ===== ② BOM 建档：版本化（每次转移生成新版本，旧版本失效）=====
                // 旧版本（当前生效）
                com.jjx.engineering.domain.entity.EngineeringBom existBom = bomService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom>()
                                .eq(com.jjx.engineering.domain.entity.EngineeringBom::getProductId, pid)
                                .eq(com.jjx.engineering.domain.entity.EngineeringBom::getIsCurrent, true)
                                .last("LIMIT 1"));
                // 新版本号：该产品所有BOM版本(version/bom_version)最大整数部分+1
                java.util.List<String> bomVersions = bomService.list(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom>()
                                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getProductId, pid))
                        .stream()
                        .map(b -> b.getVersion() != null ? b.getVersion() : b.getBomVersion())
                        .collect(java.util.stream.Collectors.toList());
                String newBomVersion = computeNextVersion(bomVersions);
                if (processes != null && !processes.isEmpty()) {
                    // 旧版本失效
                    if (existBom != null) {
                        existBom.setIsCurrent(false);
                        bomService.updateById(existBom);
                    }
                        com.jjx.engineering.domain.entity.EngineeringBom newBom = new com.jjx.engineering.domain.entity.EngineeringBom();
                        newBom.setBomCode("BOM-" + prod.getProductCode() + "-SAMPLE");
                        newBom.setBomName(prod.getProductName() + "（打样传承BOM）");
                        newBom.setProductId(pid);
                        newBom.setBomVersion(newBomVersion);
                        newBom.setVersion(newBomVersion);
                        newBom.setBomType("manufacturing");
                        newBom.setIsCurrent(true);
                        newBom.setSourceSampleId(orderId);
                        newBom.setParentBomId(existBom != null ? existBom.getBomId() : null);
                        newBom.setApproveStatus(1); // 草稿
                        newBom.setRemark("由样品单[" + sampleOrder.getOrderNo() + "]资料转移生成V" + newBomVersion + "，请工程确认后批准");
                        newBom.setCreateBy(SecurityUtils.getUsername());
                        bomService.save(newBom);

                        int order = 1;
                        java.util.Map<String, com.jjx.engineering.domain.entity.EngineeringBomItem> aggMap = new java.util.LinkedHashMap<>();
                        for (com.jjx.sales.domain.entity.SalesSampleProcess sp : processes) {
                            if (sp.getMaterials() == null || sp.getMaterials().isEmpty()) continue;
                            try {
                                java.util.List<java.util.Map<String, Object>> mats =
                                        new com.fasterxml.jackson.databind.ObjectMapper().readValue(sp.getMaterials(),
                                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                                for (java.util.Map<String, Object> m : mats) {
                                    String name = m.get("name") != null ? m.get("name").toString() : null;
                                    if (name == null || name.isEmpty()) continue;
                                    String key = name + "|" + (m.get("spec") != null ? m.get("spec") : "");
                                    com.jjx.engineering.domain.entity.EngineeringBomItem item = aggMap.get(key);
                                    if (item == null) {
                                        item = new com.jjx.engineering.domain.entity.EngineeringBomItem();
                                        item.setBomId(newBom.getBomId());
                                        item.setMaterialName(name);
                                        item.setSpecification(m.get("spec") != null ? m.get("spec").toString() : null);
                                        item.setUnit(m.get("unit") != null ? m.get("unit").toString() : "PCS");
                                        item.setLayer(sp.getProcessName());
                                        item.setItemOrder(order++);
                                        item.setQuantity(java.math.BigDecimal.ZERO);
                                        aggMap.put(key, item);
                                        try {
                                            com.jjx.inventory.domain.InventoryMaterial mat = inventoryMaterialMapper.selectOne(
                                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                                                            .eq(com.jjx.inventory.domain.InventoryMaterial::getMaterialName, name)
                                                            .last("LIMIT 1"));
                                            if (mat != null) {
                                                item.setMaterialId(mat.getMaterialId());
                                                item.setMaterialCode(mat.getMaterialCode());
                                            }
                                        } catch (Exception me) {
                                            log.warn("工序材料匹配物料失败: {}", me.getMessage());
                                        }
                                    }
                                    if (m.get("qty") != null) {
                                        try {
                                            item.setQuantity(item.getQuantity().add(new java.math.BigDecimal(m.get("qty").toString())));
                                        } catch (Exception qe) { /* ignore */ }
                                    }
                                }
                            } catch (Exception pe) {
                                log.warn("解析工序材料失败: {}", pe.getMessage());
                            }
                        }
                        for (com.jjx.engineering.domain.entity.EngineeringBomItem item : aggMap.values()) {
                            bomItemMapper.insert(item);
                        }
                        builtBomId = newBom.getBomId();
                        bomAction = "CREATE";
                        details.add("BOM[" + newBom.getBomCode() + "]生成草稿(" + aggMap.size() + "条明细)");
                        // 回填产品当前BOM版本号+指针（DEV-771：发布校验用 current_bom_id）
                        if (pid != null) {
                            com.jjx.product.domain.entity.Product bomVerUpdate = new com.jjx.product.domain.entity.Product();
                            bomVerUpdate.setProductId(pid);
                            bomVerUpdate.setCurrentBomVersion(newBomVersion);
                            bomVerUpdate.setCurrentBomId(newBom.getBomId());
                            productMapper.updateById(bomVerUpdate);
                        }
                        transferredVersion = newBomVersion;
                    } else {
                        bomAction = "SKIP_NO_PROCESS";
                        details.add("产品[" + prod.getProductCode() + "]无打样工序，未生成BOM");
                    }

                // ===== ③ 工艺路线建档：版本化（每次转移生成新版本，旧版本失效）=====
                // 旧版本（当前生效）
                com.jjx.engineering.domain.entity.EngineeringRouting existRouting = routingService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringRouting>()
                                .eq(com.jjx.engineering.domain.entity.EngineeringRouting::getProductId, pid)
                                .eq(com.jjx.engineering.domain.entity.EngineeringRouting::getIsCurrent, 1)
                                .last("LIMIT 1"));
                // 新版本号：该产品所有Routing版本(version/routing_version)最大整数部分+1
                java.util.List<String> routingVersions = routingService.list(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringRouting>()
                                        .eq(com.jjx.engineering.domain.entity.EngineeringRouting::getProductId, pid))
                        .stream()
                        .map(r -> r.getVersion() != null ? r.getVersion() : r.getRoutingVersion())
                        .collect(java.util.stream.Collectors.toList());
                String newRoutingVersion = computeNextVersion(routingVersions);
                if (processes != null && !processes.isEmpty()) {
                    // 旧版本失效
                    if (existRouting != null) {
                        existRouting.setIsCurrent(0);
                        routingService.updateById(existRouting);
                    }
                        com.jjx.engineering.domain.entity.EngineeringRouting newRouting = new com.jjx.engineering.domain.entity.EngineeringRouting();
                        newRouting.setRoutingCode("RTE-" + prod.getProductCode() + "-SAMPLE");
                        newRouting.setRoutingName(prod.getProductName() + "（打样传承工艺路线）");
                        newRouting.setProductId(pid);
                        newRouting.setProductCode(prod.getProductCode());
                        newRouting.setProductName(prod.getProductName());
                        newRouting.setRoutingVersion(newRoutingVersion);
                        newRouting.setVersion(newRoutingVersion);
                        newRouting.setIsCurrent(1);
                        newRouting.setSourceSampleId(orderId);
                        newRouting.setParentRoutingId(existRouting != null ? existRouting.getRoutingId() : null);
                        newRouting.setApproveStatus(1); // 草稿
                        newRouting.setCreateBy(SecurityUtils.getUsername());
                        routingService.save(newRouting);

                        int stepOrder = 1;
                        java.math.BigDecimal totalLabor = java.math.BigDecimal.ZERO;
                        java.math.BigDecimal totalMachine = java.math.BigDecimal.ZERO;
                        // 组合工序分组：process_order 相同 → 共享 group_id
                        java.util.Map<Integer, Long> orderGroupMap = new java.util.HashMap<>();
                        // 组合顺序号（2026-08-11：修复转移后编辑页组合排序）
                        java.util.Map<Integer, Integer> orderGroupSeqMap = new java.util.HashMap<>();
                        for (com.jjx.sales.domain.entity.SalesSampleProcess sp : processes) {
                            // 方案A适配：优先用 std_process_id 精确关联作业项目，无则按名称匹配兑底（兼容旧数据/自定义工序）
                            com.jjx.product.domain.entity.ProductStandardProcess std = null;
                            if (sp.getStdProcessId() != null) {
                                std = standardProcessMapper.selectById(sp.getStdProcessId());
                            }
                            if (std == null && sp.getProcessName() != null) {
                                std = standardProcessMapper.selectOne(
                                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.product.domain.entity.ProductStandardProcess>()
                                                .eq(com.jjx.product.domain.entity.ProductStandardProcess::getProcessName, sp.getProcessName())
                                                .last("LIMIT 1"));
                            }
                            Long stdProcessId = std != null ? std.getProcessId() : null;
                            // 2026-08-12：类别优先标准工序，无则保留打样子结构（PANEL/UP_LINE/DOWN_LINE），再兑底 OTHER
                            String category = std != null && std.getProcessCategory() != null
                                    ? std.getProcessCategory()
                                    : (sp.getProcessCategory() != null && !sp.getProcessCategory().isEmpty()
                                        ? sp.getProcessCategory() : "OTHER");
                            java.math.BigDecimal laborHours = sp.getDurationMinutes() != null
                                    ? java.math.BigDecimal.valueOf(sp.getDurationMinutes()).divide(java.math.BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP)
                                    : (std != null && std.getStandardLaborHours() != null ? std.getStandardLaborHours() : java.math.BigDecimal.ZERO);
                            java.math.BigDecimal machineHours = std != null && std.getStandardMachineHours() != null
                                    ? std.getStandardMachineHours() : laborHours;
                            totalLabor = totalLabor.add(laborHours);
                            totalMachine = totalMachine.add(machineHours);
                            // 组合工序：同一 process_order 共享 group_id（独立工序为 null）
                            Integer po = sp.getProcessOrder();
                            Long groupId = null;
                            String groupName = null;
                            Integer groupOrder = null;
                            if (po != null && po > 0) {
                                groupId = orderGroupMap.computeIfAbsent(po, k -> generateGroupId());
                                groupName = processCategoryToGroupName(sp.getProcessCategory());
                                // 组合顺序号：按组合首次出现顺序 1,2,3...（2026-08-11 修复编辑页排序）
                                groupOrder = orderGroupSeqMap.computeIfAbsent(po, k -> orderGroupSeqMap.size() + 1);
                            }
                            // 定制工艺参数 JSON 直通（dev-20260811-009）：印刷工序优先传 custom_process_params，否则文本兑底
                            String processParams = (sp.getCustomProcessParams() != null && !sp.getCustomProcessParams().isEmpty())
                                    ? sp.getCustomProcessParams()
                                    : sp.getProcessNote();
                            routingItemMapper.insertItem(newRouting.getRoutingId(),
                                    stdProcessId != null ? stdProcessId : null,
                                    // 2026-08-12：大类透传（PRINT印刷/ASSEMBLY组装）
                                    sp.getMajorCategory() != null ? sp.getMajorCategory() : "ASSEMBLY",
                                    sp.getProcessName(),
                                    stepOrder++, laborHours, machineHours,
                                    processParams,
                                    "打样传承: " + (sp.getProcessNote() != null ? sp.getProcessNote() : sp.getProcessName()),
                                    category, groupId, groupName, groupOrder,
                                    sp.getIndexNumber()); // DEV-777：打样下标透传到工艺路线
                        }
                        newRouting.setTotalLaborHours(totalLabor);
                        newRouting.setTotalMachineHours(totalMachine);
                        newRouting.setProcessCount(processes.size());
                        routingService.updateById(newRouting);
                        builtRoutingId = newRouting.getRoutingId();
                        routingAction = "CREATE";
                        details.add("工艺路线[" + newRouting.getRoutingCode() + "]生成草稿(" + processes.size() + "道工序)");
                        // 回填产品当前Routing版本号
                        if (pid != null) {
                            com.jjx.product.domain.entity.Product routingVerUpdate = new com.jjx.product.domain.entity.Product();
                            routingVerUpdate.setProductId(pid);
                            routingVerUpdate.setCurrentRoutingVersion(newRoutingVersion);
                            routingVerUpdate.setCurrentRouteId(newRouting.getRoutingId());
                            productMapper.updateById(routingVerUpdate);
                        }
                        transferredVersion = newRoutingVersion;
                    } else {
                        routingAction = "SKIP_NO_PROCESS";
                        details.add("产品[" + prod.getProductCode() + "]无工序记录，未生成工艺路线");
                    }
            }
        }

        // 写转移记录
        com.jjx.sales.domain.entity.SalesSampleTransfer transfer = new com.jjx.sales.domain.entity.SalesSampleTransfer();
        transfer.setOrderId(orderId);
        transfer.setOrderNo(sampleOrder.getOrderNo());
        transfer.setTransferNo(transferNo);
        transfer.setProductId(builtProductId);
        transfer.setBomId(builtBomId);
        transfer.setRoutingId(builtRoutingId);
        transfer.setProductAction(productAction);
        transfer.setBomAction(bomAction);
        transfer.setRoutingAction(routingAction);
        transfer.setStatus("SUCCESS");
        transfer.setDetail(String.join("\n", details));
        transfer.setCreateBy(SecurityUtils.getUsername());
        sampleTransferMapper.insert(transfer);

        // 版本化回填：打样单记录最近转移版本号 + 时间
        if (transferredVersion != null) {
            com.jjx.sales.domain.entity.SalesOrder formalUpdate = new com.jjx.sales.domain.entity.SalesOrder();
            formalUpdate.setOrderId(orderId);
            formalUpdate.setFormalVersion(transferredVersion);
            formalUpdate.setLastTransferTime(java.time.LocalDateTime.now());
            orderMapper.updateById(formalUpdate);
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("transferNo", transferNo);
        result.put("transferId", transfer.getTransferId());
        result.put("productAction", productAction);
        result.put("bomAction", bomAction);
        result.put("routingAction", routingAction);
        result.put("productId", builtProductId);
        result.put("bomId", builtBomId);
        result.put("routingId", builtRoutingId);
        result.put("detail", details);
        log.info("样品单[{}] 资料转移完成[{}] 产品={} BOM={} 路线={}",
                sampleOrder.getOrderNo(), transferNo, productAction, bomAction, routingAction);
        return result;
    }

    /**
     * 打样转标准-预览：读取打样数据（工序+物料JSON），自动匹配标准工序/物料
     * 匹配逻辑：取括号前内容（如“丝印（临时）” → “丝印”）模糊匹配，匹配不上返回 null
     */
    @Override
    @Transactional(readOnly = true)
    public com.jjx.sales.domain.vo.SampleTransferPreviewVO previewTransfer(Long orderId) {
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        if (sampleOrder == null || sampleOrder.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        com.jjx.sales.domain.vo.SampleTransferPreviewVO vo = new com.jjx.sales.domain.vo.SampleTransferPreviewVO();
        vo.setOrderId(orderId);
        vo.setOrderNo(sampleOrder.getOrderNo());

        // ===== 取最新轮次打样工序（与转移一致，不过滤状态，让用户可删多余）=====
        java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> allProcesses =
                sampleProcessMapper.selectByOrderId(orderId);
        java.util.List<com.jjx.sales.domain.entity.SalesSampleProcess> processes = allProcesses;
        if (allProcesses != null && !allProcesses.isEmpty()) {
            Integer latestRound = allProcesses.stream()
                    .map(com.jjx.sales.domain.entity.SalesSampleProcess::getRoundNo)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo).orElse(null);
            if (latestRound != null) {
                processes = allProcesses.stream()
                        .filter(p -> latestRound.equals(p.getRoundNo()))
                        .collect(java.util.stream.Collectors.toList());
            }
            // 按工序顺序排序
            processes.sort(java.util.Comparator.comparing(
                            (com.jjx.sales.domain.entity.SalesSampleProcess p) ->
                                    p.getProcessOrder() != null && p.getProcessOrder() > 0 ? p.getProcessOrder() : 999999)
                    .thenComparing(p -> p.getProcessId() != null ? p.getProcessId() : 0L));
        }

        // ===== 标准工序库（启用）+ 标准物料库（启用）=====
        java.util.List<com.jjx.product.domain.entity.ProductStandardProcess> stdProcesses =
                standardProcessMapper.selectEnabledProcesses();
        java.util.List<com.jjx.inventory.domain.InventoryMaterial> stdMaterials =
                inventoryMaterialMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                                .eq(com.jjx.inventory.domain.InventoryMaterial::getStatus, 1));

        // ===== 打样工序列表 + 匹配推荐 =====
        java.util.List<com.jjx.sales.domain.vo.SampleTransferPreviewVO.SampleProcessItem> processItems =
                new java.util.ArrayList<>();
        if (processes != null) {
            for (com.jjx.sales.domain.entity.SalesSampleProcess sp : processes) {
                com.jjx.sales.domain.vo.SampleTransferPreviewVO.SampleProcessItem item =
                        new com.jjx.sales.domain.vo.SampleTransferPreviewVO.SampleProcessItem();
                item.setProcessId(sp.getProcessId());
                item.setProcessName(sp.getProcessName());
                item.setProcessOrder(sp.getProcessOrder());
                item.setProcessCategory(sp.getProcessCategory());
                item.setProcessNote(sp.getProcessNote());
                item.setCustomProcessParams(sp.getCustomProcessParams());
                item.setDurationMinutes(sp.getDurationMinutes());
                // 匹配推荐：优先用打样已关联的标准工序，其次按名称模糊匹配
                com.jjx.product.domain.entity.ProductStandardProcess match = null;
                if (sp.getStdProcessId() != null) {
                    match = standardProcessMapper.selectById(sp.getStdProcessId());
                }
                if (match == null) {
                    match = matchStandardProcess(sp.getProcessName(), stdProcesses);
                }
                if (match != null) {
                    item.setMatchedStdProcessId(match.getProcessId());
                    item.setMatchedStdProcessName(match.getProcessName());
                    item.setMatched(true);
                    // 下标：hasIndex 取匹配标准工序的 has_index；indexNumber 优先打样工序真实下标，无则回退顺序号
                    item.setHasIndex(match.getHasIndex() != null ? match.getHasIndex() : 0);
                    if (match.getHasIndex() != null && match.getHasIndex() == 1) {
                        item.setIndexNumber(sp.getIndexNumber() != null ? sp.getIndexNumber() : sp.getProcessOrder());
                    }
                } else {
                    item.setMatched(false);
                }
                processItems.add(item);
            }
        }
        vo.setSampleProcesses(processItems);

        // ===== 打样物料列表（materials JSON 展开）+ 匹配推荐 =====
        java.util.List<com.jjx.sales.domain.vo.SampleTransferPreviewVO.SampleMaterialItem> materialItems =
                new java.util.ArrayList<>();
        if (processes != null) {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            for (com.jjx.sales.domain.entity.SalesSampleProcess sp : processes) {
                if (sp.getMaterials() == null || sp.getMaterials().isEmpty()) continue;
                try {
                    java.util.List<java.util.Map<String, Object>> mats = om.readValue(sp.getMaterials(),
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});
                    for (int i = 0; i < mats.size(); i++) {
                        java.util.Map<String, Object> m = mats.get(i);
                        com.jjx.sales.domain.vo.SampleTransferPreviewVO.SampleMaterialItem item =
                                new com.jjx.sales.domain.vo.SampleTransferPreviewVO.SampleMaterialItem();
                        item.setRowKey(sp.getProcessId() + "_" + i);
                        item.setSourceProcessId(sp.getProcessId());
                        item.setSourceProcessName(sp.getProcessName());
                        item.setName(m.get("name") != null ? m.get("name").toString() : null);
                        item.setSpec(m.get("spec") != null ? m.get("spec").toString() : null);
                        if (m.get("qty") != null) {
                            try { item.setQty(new java.math.BigDecimal(m.get("qty").toString())); }
                            catch (Exception ignored) { }
                        }
                        item.setUnit(m.get("unit") != null ? m.get("unit").toString() : null);
                        if (m.get("materialId") != null) {
                            try { item.setMaterialId(Long.valueOf(m.get("materialId").toString())); }
                            catch (Exception ignored) { }
                        }
                        item.setMaterialCode(m.get("materialCode") != null ? m.get("materialCode").toString() : null);
                        // 匹配推荐：优先用打样 JSON 里的物料ID，其次按名称模糊匹配
                        com.jjx.inventory.domain.InventoryMaterial matMatch = null;
                        if (item.getMaterialId() != null) {
                            matMatch = inventoryMaterialMapper.selectById(item.getMaterialId());
                        }
                        if (matMatch == null) {
                            matMatch = matchStandardMaterial(item.getName(), stdMaterials);
                        }
                        if (matMatch != null) {
                            item.setMatchedMaterialId(matMatch.getMaterialId());
                            item.setMatchedMaterialCode(matMatch.getMaterialCode());
                            item.setMatchedMaterialName(matMatch.getMaterialName());
                            item.setMatched(true);
                        } else {
                            item.setMatched(false);
                        }
                        materialItems.add(item);
                    }
                } catch (Exception pe) {
                    log.warn("解析打样工序材料失败: {}", pe.getMessage());
                }
            }
        }
        vo.setSampleMaterials(materialItems);

        // ===== 标准工序库 / 标准物料库（下拉选项）=====
        if (stdProcesses != null) {
            vo.setStandardProcesses(stdProcesses.stream().map(p -> {
                com.jjx.sales.domain.vo.SampleTransferPreviewVO.StandardProcessOption opt =
                        new com.jjx.sales.domain.vo.SampleTransferPreviewVO.StandardProcessOption();
                opt.setProcessId(p.getProcessId());
                opt.setProcessCode(p.getProcessCode());
                opt.setProcessName(p.getProcessName());
                opt.setProcessType(p.getProcessType());
                opt.setProcessCategory(p.getProcessCategory());
                opt.setIcon(p.getIcon());
                opt.setHasIndex(p.getHasIndex() != null ? p.getHasIndex() : 0);
                return opt;
            }).collect(java.util.stream.Collectors.toList()));
        }
        if (stdMaterials != null) {
            vo.setStandardMaterials(stdMaterials.stream().map(m -> {
                com.jjx.sales.domain.vo.SampleTransferPreviewVO.StandardMaterialOption opt =
                        new com.jjx.sales.domain.vo.SampleTransferPreviewVO.StandardMaterialOption();
                opt.setMaterialId(m.getMaterialId());
                opt.setMaterialCode(m.getMaterialCode());
                opt.setMaterialName(m.getMaterialName());
                opt.setSpecification(m.getSpecification());
                opt.setUnit(m.getUnit());
                return opt;
            }).collect(java.util.stream.Collectors.toList()));
        }

        log.info("样品单[{}] 打样转标准预览：工序{}道 物料{}行", orderId, processItems.size(), materialItems.size());
        return vo;
    }

    /**
     * 打样转标准-确认转移：接收前端编辑后的标准数据（工序映射+物料映射）
     * 生成新版本BOM/Routing，旧版本失效，回填打样单和产品表
     * 复用版本化逻辑（版本递增、parent/source关联），数据来源为前端传入
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public java.util.Map<String, Object> confirmTransfer(com.jjx.sales.dto.transfer.SampleTransferConfirmDTO dto) {
        Long orderId = dto.getOrderId();
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        if (sampleOrder == null || sampleOrder.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }
        // 已确认(6)或已转量产(7)可转移
        Integer st = sampleOrder.getSampleStatus();
        if (!SampleOrderStatusEnum.CONFIRMED.getCode().equals(st)
                && !SampleOrderStatusEnum.TRANSFERRED.getCode().equals(st)) {
            throw new BusinessException("仅已确认或已转量产的样品单可进行资料转移");
        }
        java.util.List<com.jjx.sales.dto.transfer.SampleTransferConfirmDTO.ProcessMapping> processMappings =
                dto.getProcessMappings();
        java.util.List<com.jjx.sales.dto.transfer.SampleTransferConfirmDTO.MaterialMapping> materialMappings =
                dto.getMaterialMappings();
        if (processMappings == null || processMappings.isEmpty()) {
            throw new BusinessException("工序映射不能为空");
        }
        // 必填校验：工序必须选择标准工序（2026-08-12 豁免：带自定义参数的印刷工序可不选，原样转入路线）
        for (com.jjx.sales.dto.transfer.SampleTransferConfirmDTO.ProcessMapping pm : processMappings) {
            boolean hasCustomParams = pm.getCustomProcessParams() != null && !pm.getCustomProcessParams().isBlank();
            if (pm.getStdProcessId() == null && !hasCustomParams) {
                throw new BusinessException("存在未选择标准工序的工序：" + pm.getProcessName());
            }
        }

        String transferNo = redisSequenceService.generateBusinessNumber("TF", "资料转移单号");
        String transferredVersion = null;
        java.util.List<String> details = new java.util.ArrayList<>();
        String productAction = "NONE", bomAction = "NONE", routingAction = "NONE";
        Long builtProductId = null, builtBomId = null, builtRoutingId = null;

        // 产品明细：样品单若无明细则从报价单补复制（数据断链兜底）
        java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> prodList =
                orderProductService.getListByOrderId(orderId);
        if (prodList == null || prodList.isEmpty()) {
            if (sampleOrder.getQuotationId() != null) {
                copyQuotationItemsToOrder(sampleOrder.getQuotationId(), orderId);
                updateTotalQuantityByItems(orderId);
                prodList = orderProductService.getListByOrderId(orderId);
            }
        }

        if (prodList != null) {
            for (com.jjx.sales.domain.vo.SalesOrderProductVO prod : prodList) {
                Long pid = prod.getProductId();

                // ===== ① 产品建档：无则建草稿（待审核），有则核对 =====
                com.jjx.product.domain.entity.Product product = pid != null ? productMapper.selectById(pid) : null;
                if (product == null) {
                    com.jjx.product.domain.entity.Product np = new com.jjx.product.domain.entity.Product();
                    np.setProductCode(prod.getProductCode());
                    np.setProductName(prod.getProductName());
                    np.setProductStatus(2); // 待审核
                    np.setUnit(prod.getUnit());
                    np.setCreateBy(SecurityUtils.getUsername());
                    productMapper.insert(np);
                    pid = np.getProductId();
                    productAction = "CREATE";
                    details.add("产品[" + prod.getProductCode() + "]新建建档(待审核)");
                    try {
                        com.jjx.sales.domain.entity.SalesOrderProduct op = new com.jjx.sales.domain.entity.SalesOrderProduct();
                        op.setId(prod.getId());
                        op.setProductId(pid);
                        orderProductMapper.updateById(op);
                    } catch (Exception e) {
                        log.warn("样品单明细回写productId失败: {}", e.getMessage());
                    }
                } else {
                    productAction = "UPDATE";
                    details.add("产品[" + prod.getProductCode() + "]已存在，档案核对");
                }
                if (builtProductId == null) builtProductId = pid;
                if (pid == null) continue;

                // ===== ② BOM 建档：版本化，明细来自前端物料映射 =====
                com.jjx.engineering.domain.entity.EngineeringBom existBom = bomService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom>()
                                .eq(com.jjx.engineering.domain.entity.EngineeringBom::getProductId, pid)
                                .eq(com.jjx.engineering.domain.entity.EngineeringBom::getIsCurrent, true)
                                .last("LIMIT 1"));
                java.util.List<String> bomVersions = bomService.list(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom>()
                                        .eq(com.jjx.engineering.domain.entity.EngineeringBom::getProductId, pid))
                        .stream()
                        .map(b -> b.getVersion() != null ? b.getVersion() : b.getBomVersion())
                        .collect(java.util.stream.Collectors.toList());
                String newBomVersion = computeNextVersion(bomVersions);
                boolean hasMaterials = materialMappings != null && !materialMappings.isEmpty();
                if (hasMaterials) {
                    // 旧版本失效
                    if (existBom != null) {
                        existBom.setIsCurrent(false);
                        bomService.updateById(existBom);
                    }
                    com.jjx.engineering.domain.entity.EngineeringBom newBom = new com.jjx.engineering.domain.entity.EngineeringBom();
                    newBom.setBomCode("BOM-" + prod.getProductCode() + "-SAMPLE");
                    newBom.setBomName(prod.getProductName() + "（打样传承BOM）");
                    newBom.setProductId(pid);
                    newBom.setBomVersion(newBomVersion);
                    newBom.setVersion(newBomVersion);
                    newBom.setBomType("manufacturing");
                    newBom.setIsCurrent(true);
                    newBom.setSourceSampleId(orderId);
                    newBom.setParentBomId(existBom != null ? existBom.getBomId() : null);
                    newBom.setApproveStatus(1); // 草稿
                    newBom.setRemark("由样品单[" + sampleOrder.getOrderNo() + "]打样转标准确认生成V" + newBomVersion + "，请工程确认后批准");
                    newBom.setCreateBy(SecurityUtils.getUsername());
                    bomService.save(newBom);

                    // 明细：物料映射每行一个物料
                    int itemOrder = 1;
                    for (com.jjx.sales.dto.transfer.SampleTransferConfirmDTO.MaterialMapping mm : materialMappings) {
                        if (mm.getMaterialId() == null) continue;
                        com.jjx.engineering.domain.entity.EngineeringBomItem item = new com.jjx.engineering.domain.entity.EngineeringBomItem();
                        item.setBomId(newBom.getBomId());
                        item.setMaterialId(mm.getMaterialId());
                        com.jjx.inventory.domain.InventoryMaterial mat = inventoryMaterialMapper.selectById(mm.getMaterialId());
                        item.setMaterialCode(mat != null ? mat.getMaterialCode() : null);
                        item.setMaterialName(mm.getMaterialName() != null ? mm.getMaterialName()
                                : (mat != null ? mat.getMaterialName() : null));
                        item.setSpecification(mm.getSpec() != null ? mm.getSpec()
                                : (mat != null ? mat.getSpecification() : null));
                        item.setUnit(mm.getUnit() != null ? mm.getUnit() : (mat != null ? mat.getUnit() : "PCS"));
                        item.setQuantity(mm.getQty() != null ? mm.getQty() : java.math.BigDecimal.ONE);
                        item.setLayer(mm.getSourceProcessName());
                        item.setItemOrder(itemOrder++);
                        bomItemMapper.insert(item);
                    }
                    builtBomId = newBom.getBomId();
                    bomAction = "CREATE";
                    details.add("BOM[" + newBom.getBomCode() + "]生成草稿(" + materialMappings.size() + "条明细)");
                    if (pid != null) {
                        com.jjx.product.domain.entity.Product bomVerUpdate = new com.jjx.product.domain.entity.Product();
                        bomVerUpdate.setProductId(pid);
                        bomVerUpdate.setCurrentBomVersion(newBomVersion);
                        bomVerUpdate.setCurrentBomId(newBom.getBomId());
                        productMapper.updateById(bomVerUpdate);
                    }
                    transferredVersion = newBomVersion;
                } else {
                    bomAction = "SKIP_NO_PROCESS";
                    details.add("产品[" + prod.getProductCode() + "]无物料映射，未生成BOM");
                }

                // ===== ③ Routing 建档：版本化，明细来自前端工序映射 =====
                com.jjx.engineering.domain.entity.EngineeringRouting existRouting = routingService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringRouting>()
                                .eq(com.jjx.engineering.domain.entity.EngineeringRouting::getProductId, pid)
                                .eq(com.jjx.engineering.domain.entity.EngineeringRouting::getIsCurrent, 1)
                                .last("LIMIT 1"));
                java.util.List<String> routingVersions = routingService.list(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringRouting>()
                                        .eq(com.jjx.engineering.domain.entity.EngineeringRouting::getProductId, pid))
                        .stream()
                        .map(r -> r.getVersion() != null ? r.getVersion() : r.getRoutingVersion())
                        .collect(java.util.stream.Collectors.toList());
                String newRoutingVersion = computeNextVersion(routingVersions);
                if (processMappings != null && !processMappings.isEmpty()) {
                    // 旧版本失效
                    if (existRouting != null) {
                        existRouting.setIsCurrent(0);
                        routingService.updateById(existRouting);
                    }
                    com.jjx.engineering.domain.entity.EngineeringRouting newRouting = new com.jjx.engineering.domain.entity.EngineeringRouting();
                    newRouting.setRoutingCode("RTE-" + prod.getProductCode() + "-SAMPLE");
                    newRouting.setRoutingName(prod.getProductName() + "（打样传承工艺路线）");
                    newRouting.setProductId(pid);
                    newRouting.setProductCode(prod.getProductCode());
                    newRouting.setProductName(prod.getProductName());
                    newRouting.setRoutingVersion(newRoutingVersion);
                    newRouting.setVersion(newRoutingVersion);
                    newRouting.setIsCurrent(1);
                    newRouting.setSourceSampleId(orderId);
                    newRouting.setParentRoutingId(existRouting != null ? existRouting.getRoutingId() : null);
                    newRouting.setApproveStatus(1); // 草稿
                    newRouting.setCreateBy(SecurityUtils.getUsername());
                    routingService.save(newRouting);

                    int stepOrder = 1;
                    java.math.BigDecimal totalLabor = java.math.BigDecimal.ZERO;
                    java.math.BigDecimal totalMachine = java.math.BigDecimal.ZERO;
                    // 组合工序：前端临时负数 groupId → 真实 groupId（同组合共享）
                    java.util.Map<Long, Long> tempToRealGroupId = new java.util.HashMap<>();
                    // 组合顺序号（2026-08-11：修复转移后编辑页组合排序）
                    java.util.Map<Long, Integer> tempGroupSeqMap = new java.util.HashMap<>();
                    for (com.jjx.sales.dto.transfer.SampleTransferConfirmDTO.ProcessMapping pm : processMappings) {
                        // 2026-08-12：印刷工序（无标准工序但有自定义参数）也写入路线，参数原样保留
                        boolean hasCustomParams = pm.getCustomProcessParams() != null && !pm.getCustomProcessParams().isBlank();
                        if (pm.getStdProcessId() == null && !hasCustomParams) continue;
                        com.jjx.product.domain.entity.ProductStandardProcess std = pm.getStdProcessId() != null
                                ? standardProcessMapper.selectById(pm.getStdProcessId()) : null;
                        Long stdProcessId = std != null ? std.getProcessId() : pm.getStdProcessId();
                        String category = pm.getProcessCategory() != null && !pm.getProcessCategory().isEmpty()
                                ? pm.getProcessCategory()
                                : (std != null && std.getProcessCategory() != null ? std.getProcessCategory() : "OTHER");
                        java.math.BigDecimal laborHours = pm.getDurationMinutes() != null
                                ? java.math.BigDecimal.valueOf(pm.getDurationMinutes()).divide(java.math.BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP)
                                : (std != null && std.getStandardLaborHours() != null ? std.getStandardLaborHours() : java.math.BigDecimal.ZERO);
                        java.math.BigDecimal machineHours = std != null && std.getStandardMachineHours() != null
                                ? std.getStandardMachineHours() : laborHours;
                        totalLabor = totalLabor.add(laborHours);
                        totalMachine = totalMachine.add(machineHours);
                        // 组合工序：同一组合共享真实 groupId
                        Long groupId = null;
                        String groupName = null;
                        Integer groupOrder = null;
                        if (pm.getGroupId() != null) {
                            groupId = tempToRealGroupId.computeIfAbsent(pm.getGroupId(), k -> generateGroupId());
                            groupName = pm.getGroupName() != null ? pm.getGroupName()
                                    : processCategoryToGroupName(category);
                            groupOrder = tempGroupSeqMap.computeIfAbsent(pm.getGroupId(), k -> tempGroupSeqMap.size() + 1);
                        }
                        routingItemMapper.insertItem(newRouting.getRoutingId(),
                                stdProcessId,
                                // 2026-08-12：大类透传（印刷工序=PRINT，其余组装）
                                hasCustomParams ? "PRINT" : "ASSEMBLY",
                                pm.getProcessName(),
                                stepOrder++,
                                laborHours,
                                machineHours,
                                // 2026-08-12：透传印刷自定义参数（色号/油墨/网框）到工艺路线
                                hasCustomParams ? pm.getCustomProcessParams() : null,
                                pm.getProcessNote() != null ? pm.getProcessNote() : "打样传承: " + pm.getProcessName(),
                                category,
                                groupId,
                                groupName,
                                groupOrder,
                                pm.getIndexNumber()); // DEV-777：对照版下标透传到工艺路线
                    }
                    newRouting.setTotalLaborHours(totalLabor);
                    newRouting.setTotalMachineHours(totalMachine);
                    newRouting.setProcessCount(processMappings.size());
                    routingService.updateById(newRouting);
                    builtRoutingId = newRouting.getRoutingId();
                    routingAction = "CREATE";
                    details.add("工艺路线[" + newRouting.getRoutingCode() + "]生成草稿(" + processMappings.size() + "道工序)");
                    if (pid != null) {
                        com.jjx.product.domain.entity.Product routingVerUpdate = new com.jjx.product.domain.entity.Product();
                        routingVerUpdate.setProductId(pid);
                        routingVerUpdate.setCurrentRoutingVersion(newRoutingVersion);
                        routingVerUpdate.setCurrentRouteId(newRouting.getRoutingId());
                        productMapper.updateById(routingVerUpdate);
                    }
                    transferredVersion = newRoutingVersion;
                } else {
                    routingAction = "SKIP_NO_PROCESS";
                    details.add("产品[" + prod.getProductCode() + "]无工序映射，未生成工艺路线");
                }
            }
        }

        // 写转移记录
        com.jjx.sales.domain.entity.SalesSampleTransfer transfer = new com.jjx.sales.domain.entity.SalesSampleTransfer();
        transfer.setOrderId(orderId);
        transfer.setOrderNo(sampleOrder.getOrderNo());
        transfer.setTransferNo(transferNo);
        transfer.setProductId(builtProductId);
        transfer.setBomId(builtBomId);
        transfer.setRoutingId(builtRoutingId);
        transfer.setProductAction(productAction);
        transfer.setBomAction(bomAction);
        transfer.setRoutingAction(routingAction);
        transfer.setStatus("SUCCESS");
        transfer.setDetail(String.join("\n", details));
        transfer.setCreateBy(SecurityUtils.getUsername());
        sampleTransferMapper.insert(transfer);

        // 版本化回填：打样单记录最近转移版本号 + 时间
        if (transferredVersion != null) {
            com.jjx.sales.domain.entity.SalesOrder formalUpdate = new com.jjx.sales.domain.entity.SalesOrder();
            formalUpdate.setOrderId(orderId);
            formalUpdate.setFormalVersion(transferredVersion);
            formalUpdate.setLastTransferTime(java.time.LocalDateTime.now());
            orderMapper.updateById(formalUpdate);
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("transferNo", transferNo);
        result.put("transferId", transfer.getTransferId());
        result.put("productAction", productAction);
        result.put("bomAction", bomAction);
        result.put("routingAction", routingAction);
        result.put("productId", builtProductId);
        result.put("bomId", builtBomId);
        result.put("routingId", builtRoutingId);
        result.put("version", transferredVersion);
        result.put("detail", details);
        log.info("样品单[{}] 打样转标准确认完成[{}] 产品={} BOM={} 路线={} 版本={}",
                sampleOrder.getOrderNo(), transferNo, productAction, bomAction, routingAction, transferredVersion);
        return result;
    }

    /**
     * 取括号前内容（“丝印（临时）” → “丝印”），用于模糊匹配
     */
    private String stripBracketSuffix(String name) {
        if (name == null) return null;
        String s = name.trim();
        int idx = s.indexOf('（');
        int idx2 = s.indexOf('(');
        int cut = -1;
        if (idx >= 0 && idx2 >= 0) cut = Math.min(idx, idx2);
        else if (idx >= 0) cut = idx;
        else if (idx2 >= 0) cut = idx2;
        return cut >= 0 ? s.substring(0, cut).trim() : s;
    }

    /**
     * 打样工序名 → 标准工序库模糊匹配（优先精确，其次括号前内容包含）
     */
    private com.jjx.product.domain.entity.ProductStandardProcess matchStandardProcess(String name,
            java.util.List<com.jjx.product.domain.entity.ProductStandardProcess> stdProcesses) {
        if (name == null || stdProcesses == null || stdProcesses.isEmpty()) return null;
        String base = stripBracketSuffix(name);
        if (base.isEmpty()) return null;
        // 1. 精确匹配
        for (com.jjx.product.domain.entity.ProductStandardProcess p : stdProcesses) {
            if (base.equals(p.getProcessName())) return p;
        }
        // 2. 包含匹配（标准工序名包含基础名）
        for (com.jjx.product.domain.entity.ProductStandardProcess p : stdProcesses) {
            if (p.getProcessName() != null && p.getProcessName().contains(base)) return p;
        }
        return null;
    }

    /**
     * 打样物料名 → 标准物料库模糊匹配（优先精确，其次括号前内容包含）
     */
    private com.jjx.inventory.domain.InventoryMaterial matchStandardMaterial(String name,
            java.util.List<com.jjx.inventory.domain.InventoryMaterial> stdMaterials) {
        if (name == null || stdMaterials == null || stdMaterials.isEmpty()) return null;
        String base = stripBracketSuffix(name);
        if (base.isEmpty()) return null;
        // 1. 精确匹配
        for (com.jjx.inventory.domain.InventoryMaterial m : stdMaterials) {
            if (base.equals(m.getMaterialName())) return m;
        }
        // 2. 包含匹配（标准物料名包含基础名）
        for (com.jjx.inventory.domain.InventoryMaterial m : stdMaterials) {
            if (m.getMaterialName() != null && m.getMaterialName().contains(base)) return m;
        }
        return null;
    }

    /**
     * 计算下一个版本号（V1.0 → V2.0）：取现有版本号整数部分最大值+1
     * 兼容 V1 / V1.0 / v2.0 格式
     */
    private String computeNextVersion(java.util.List<String> existingVersions) {
        // 2026-08-10 DEV-765：统一走公共工具类
        return com.jjx.common.utils.VersionUtils.next(existingVersions);
    }

    /**
     * 生成组合工序ID（时间戳+随机数，避免冲突，与工程路线保存逻辑一致）
     */
    private Long generateGroupId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    /**
     * 打样工序卡片结构 → 组合名称（PANEL→面板组等），NULL/空返回 null
     */
    private String processCategoryToGroupName(String category) {
        if (category == null || category.isEmpty()) return null;
        switch (category) {
            case "PANEL": return "面板组";
            case "UP_LINE": return "上线组";
            case "DOWN_LINE": return "下线组";
            case "OTHER": return "其他组";
            default: return category;
        }
    }

    @Override
    public com.jjx.sales.domain.vo.SampleConvertCheckVO checkConvertReady(Long orderId) {
        return buildConvertCheck(orderId);
    }

    /**
     * 转量产就绪检查：产品/BOM/工艺路线/菲林/资料转移（check 接口与 convert 共用）
     */
    private com.jjx.sales.domain.vo.SampleConvertCheckVO buildConvertCheck(Long orderId) {
        com.jjx.sales.domain.vo.SampleConvertCheckVO vo = new com.jjx.sales.domain.vo.SampleConvertCheckVO();
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        vo.setOrderId(orderId);
        vo.setOrderNo(sampleOrder != null ? sampleOrder.getOrderNo() : null);
        java.util.List<com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem> items = new java.util.ArrayList<>();

        java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> prodList =
                orderProductService.getListByOrderId(orderId);
        // 去重产品集合
        java.util.LinkedHashSet<Long> productIds = new java.util.LinkedHashSet<>();
        java.util.Map<Long, String> prodCodeMap = new java.util.HashMap<>();

        // ===== ① 产品：建档 + 已发布 =====
        boolean productPass = true;
        if (prodList == null || prodList.isEmpty()) {
            productPass = false;
        } else {
            for (com.jjx.sales.domain.vo.SalesOrderProductVO prod : prodList) {
                Long pid = prod.getProductId();
                if (pid == null) {
                    productPass = false;
                    continue;
                }
                com.jjx.product.domain.entity.Product product = productMapper.selectById(pid);
                if (product == null) {
                    productPass = false;
                    continue;
                }
                productIds.add(pid);
                prodCodeMap.putIfAbsent(pid, product.getProductCode());
                if (product.getProductStatus() == null || product.getProductStatus() != 6) {
                    productPass = false;
                }
            }
        }
        com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem productItem = new com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem();
        productItem.setCode("product");
        productItem.setName("产品建档");
        productItem.setLevel("required");
        productItem.setPass(productPass);
        if (productPass) {
            productItem.setStatus("ready");
            productItem.setMessage("明细产品已建档并发布");
        } else {
            productItem.setStatus("missing");
            productItem.setAction("list-product");
            // 找出第一个未建档/未发布产品
            com.jjx.sales.domain.vo.SalesOrderProductVO bad = null;
            if (prodList != null) {
                for (com.jjx.sales.domain.vo.SalesOrderProductVO prod : prodList) {
                    if (prod.getProductId() == null) {
                        bad = prod;
                        break;
                    }
                    com.jjx.product.domain.entity.Product p = productMapper.selectById(prod.getProductId());
                    if (p == null || p.getProductStatus() == null || p.getProductStatus() != 6) {
                        bad = prod;
                        break;
                    }
                }
            }
            if (bad != null && bad.getProductId() == null) {
                productItem.setMessage("明细产品[" + (bad.getProductCode() != null ? bad.getProductCode() : "未编码") + "]未建档，请先资料转移建档或在产品列表新建");
                productItem.setAction("list-product");
            } else if (bad != null) {
                productItem.setMessage("产品[" + bad.getProductCode() + "]未发布（状态:" + (productMapper.selectById(bad.getProductId()) != null ? productMapper.selectById(bad.getProductId()).getProductStatus() : "?") + "），请到产品编辑页发布");
                productItem.setAction("edit-product");
                productItem.setProductId(bad.getProductId());
            } else {
                productItem.setMessage("请先完善样品单明细产品");
            }
        }
        items.add(productItem);

        // ===== ② BOM：每个产品有已批准BOM =====
        boolean bomPass = true;
        String bomMsg = "产品已有已批准BOM";
        Long bomBadPid = null;
        if (productIds.isEmpty()) {
            bomPass = false;
            bomMsg = "产品未建档，无法校验BOM";
        } else {
            for (Long pid : productIds) {
                com.jjx.engineering.domain.entity.EngineeringBom bom = bomService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringBom>()
                                .eq(com.jjx.engineering.domain.entity.EngineeringBom::getProductId, pid)
                                .eq(com.jjx.engineering.domain.entity.EngineeringBom::getIsCurrent, true)
                                .last("LIMIT 1"));
                if (bom == null || bom.getApproveStatus() == null || bom.getApproveStatus() != 3) {
                    bomPass = false;
                    bomBadPid = pid;
                    bomMsg = "产品[" + prodCodeMap.getOrDefault(pid, String.valueOf(pid)) + "]无已批准BOM（请资料转移建档后走工程审核批准）";
                    break;
                }
            }
        }
        com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem bomItem = new com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem();
        bomItem.setCode("bom");
        bomItem.setName("BOM");
        bomItem.setLevel("required");
        bomItem.setPass(bomPass);
        bomItem.setStatus(bomPass ? "ready" : "missing");
        bomItem.setMessage(bomMsg);
        if (!bomPass) {
            bomItem.setAction("transfer");
            bomItem.setProductId(bomBadPid);
        }
        items.add(bomItem);

        // ===== ③ 工艺路线：每个产品有已批准路线 =====
        boolean routingPass = true;
        String routingMsg = "产品已有已批准工艺路线";
        Long routingBadPid = null;
        if (productIds.isEmpty()) {
            routingPass = false;
            routingMsg = "产品未建档，无法校验工艺路线";
        } else {
            for (Long pid : productIds) {
                com.jjx.engineering.domain.entity.EngineeringRouting routing = routingService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringRouting>()
                                .eq(com.jjx.engineering.domain.entity.EngineeringRouting::getProductId, pid)
                                .eq(com.jjx.engineering.domain.entity.EngineeringRouting::getIsCurrent, true)
                                .last("LIMIT 1"));
                if (routing == null || routing.getApproveStatus() == null || routing.getApproveStatus() != 3) {
                    routingPass = false;
                    routingBadPid = pid;
                    routingMsg = "产品[" + prodCodeMap.getOrDefault(pid, String.valueOf(pid)) + "]无已批准工艺路线（请资料转移建档后走工程审核批准）";
                    break;
                }
            }
        }
        com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem routingItem = new com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem();
        routingItem.setCode("routing");
        routingItem.setName("工艺路线");
        routingItem.setLevel("required");
        routingItem.setPass(routingPass);
        routingItem.setStatus(routingPass ? "ready" : "missing");
        routingItem.setMessage(routingMsg);
        if (!routingPass) {
            routingItem.setAction("transfer");
            routingItem.setProductId(routingBadPid);
        }
        items.add(routingItem);

        // ===== ④ 菲林：建议项 =====
        boolean filmPass = true;
        String filmMsg = "产品已有菲林档案";
        if (productIds.isEmpty()) {
            filmPass = false;
            filmMsg = "产品未建档，无法校验菲林";
        } else {
            for (Long pid : productIds) {
                Long cnt = engineeringFilmMapper.selectCount(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.engineering.domain.entity.EngineeringFilm>()
                                .eq(com.jjx.engineering.domain.entity.EngineeringFilm::getProductId, pid));
                if (cnt == null || cnt == 0) {
                    filmPass = false;
                    filmMsg = "产品[" + prodCodeMap.getOrDefault(pid, String.valueOf(pid)) + "]无菲林档案（建议在产品档案补全，不阻塞转量产）";
                    break;
                }
            }
        }
        com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem filmItem = new com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem();
        filmItem.setCode("film");
        filmItem.setName("菲林");
        filmItem.setLevel("suggest");
        filmItem.setPass(filmPass);
        filmItem.setStatus(filmPass ? "ready" : "missing");
        filmItem.setMessage(filmMsg);
        items.add(filmItem);

        // ===== ⑤ 资料转移记录（辅助信息） =====
        com.jjx.sales.domain.entity.SalesSampleTransfer transferRec = sampleTransferMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesSampleTransfer>()
                        .eq(com.jjx.sales.domain.entity.SalesSampleTransfer::getOrderId, orderId)
                        .orderByDesc(com.jjx.sales.domain.entity.SalesSampleTransfer::getTransferId)
                        .last("LIMIT 1"));
        com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem transferItem = new com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem();
        transferItem.setCode("transfer");
        transferItem.setName("资料转移");
        transferItem.setLevel("info");
        transferItem.setPass(transferRec != null);
        transferItem.setStatus(transferRec != null ? "done" : "none");
        transferItem.setMessage(transferRec != null
                ? "已生成转移单 " + transferRec.getTransferNo() + "（" + transferRec.getProductAction() + "/" + transferRec.getBomAction() + "/" + transferRec.getRoutingAction() + "）"
                : "未执行资料转移（建档产品/BOM/路线的快捷方式）");
        if (transferRec == null) {
            transferItem.setAction("transfer");
        }
        items.add(transferItem);

        vo.setItems(items);
        vo.setAllPass(items.stream()
                .filter(i -> "required".equals(i.getLevel()))
                .allMatch(com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem::getPass));
        return vo;
    }

    @Override
    @Event(value = "sample.converted", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder convertToProduction(Long orderId) {
        return convertToProduction(orderId, null);
    }

    @Override
    @Event(value = "sample.converted", bizId = "#orderId", bizType = "'sample'")
    @Transactional(rollbackFor = Exception.class)
    public SalesOrder convertToProduction(Long orderId,
                                          java.util.List<com.jjx.sales.domain.dto.SampleConvertItemDTO> items) {
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        if (sampleOrder == null || sampleOrder.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        safeTransition(orderId,
                SampleOrderStatusEnum.CONFIRMED,
                SampleOrderStatusEnum.TRANSFERRED,
                "转量产");

        // ========== 转量产：标准化（可选 items）→ 就绪校验 → 流转生成 ==========
        java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> prodList =
                orderProductService.getListByOrderId(sampleOrder.getOrderId());
        if (prodList != null) {
            // ① 标准化：前端传了 items 则按明细ID替换产品，并以产品档案信息覆盖明细（编码/名称/规格/单位）
            if (items != null && !items.isEmpty()) {
                java.util.Map<Long, Long> itemMap = new java.util.HashMap<>();
                for (com.jjx.sales.domain.dto.SampleConvertItemDTO it : items) {
                    if (it.getOrderProductId() != null) {
                        itemMap.put(it.getOrderProductId(), it.getProductId());
                    }
                }
                for (com.jjx.sales.domain.vo.SalesOrderProductVO prod : prodList) {
                    Long newPid = itemMap.get(prod.getId());
                    if (newPid != null) {
                        com.jjx.product.domain.entity.Product product = productMapper.selectById(newPid);
                        if (product == null) {
                            throw new BusinessException("产品[" + newPid + "]不存在");
                        }
                        com.jjx.sales.domain.entity.SalesOrderProduct op = new com.jjx.sales.domain.entity.SalesOrderProduct();
                        op.setId(prod.getId());
                        op.setProductId(newPid);
                        op.setProductCode(product.getProductCode());
                        op.setProductName(product.getProductName());
                        if (product.getSpecJson() != null) {
                            op.setSpecification(product.getSpecJson());
                        }
                        if (product.getUnit() != null) {
                            op.setUnit(product.getUnit());
                        }
                        orderProductMapper.updateById(op);
                        // 刷新当前 VO 供后续校验
                        prod.setProductId(newPid);
                        prod.setProductCode(product.getProductCode());
                        prod.setProductName(product.getProductName());
                    }
                }
            }
        }

        // ② 就绪校验（产品/BOM/工艺路线/菲林，必需项强制）
        com.jjx.sales.domain.vo.SampleConvertCheckVO check = buildConvertCheck(orderId);
        if (!java.lang.Boolean.TRUE.equals(check.getAllPass())) {
            String miss = check.getItems().stream()
                    .filter(i -> "required".equals(i.getLevel()) && !java.lang.Boolean.TRUE.equals(i.getPass()))
                    .map(com.jjx.sales.domain.vo.SampleConvertCheckVO.CheckItem::getName)
                    .collect(java.util.stream.Collectors.joining("、"));
            throw new BusinessException("转量产资料未就绪：" + miss + "。请先补全（可在转量产就绪检查中查看明细）");
        }

        // 生成标准订单
        String standardOrderNo = redisSequenceService.generateBusinessNumber("SO", "标准订单号");

        SalesOrder standardOrder = new SalesOrder();
        standardOrder.setOrderNo(standardOrderNo);
        standardOrder.setQuotationId(sampleOrder.getQuotationId());
        standardOrder.setCustomerId(sampleOrder.getCustomerId());
        standardOrder.setCustomerName(sampleOrder.getCustomerName());
        standardOrder.setContactPerson(sampleOrder.getContactPerson());
        standardOrder.setContactPhone(sampleOrder.getContactPhone());
        standardOrder.setOrderDate(new Date());
        standardOrder.setDeliveryDate(sampleOrder.getDeliveryDate());
        standardOrder.setOrderType(OrderTypeEnum.STANDARD.getCode());
        standardOrder.setOrderStatus(1); // DRAFT
        standardOrder.setCurrency(sampleOrder.getCurrency());
        standardOrder.setExchangeRate(sampleOrder.getExchangeRate());
        standardOrder.setSalesManagerId(sampleOrder.getSalesManagerId());
        standardOrder.setSalesManagerName(sampleOrder.getSalesManagerName());
        // DEV-806：total_quantity 按样品单明细求和（不依赖样品单 total_quantity 字段，兼容存量脏数据）
        int sumQty = 0;
        java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> sampleItems =
                orderProductService.getListByOrderId(sampleOrder.getOrderId());
        if (sampleItems != null) {
            for (com.jjx.sales.domain.vo.SalesOrderProductVO it : sampleItems) {
                sumQty += it.getQuantity() != null ? it.getQuantity() : 0;
            }
        }
        standardOrder.setTotalQuantity(sumQty);
        standardOrder.setTotalAmount(sampleOrder.getTotalAmount());
        standardOrder.setFinalAmount(sampleOrder.getFinalAmount());
        // 打样汇总自动计算（总工时+材料成本），替代手填 sampleCost/sampleWorkHours
        java.util.Map<String, Object> summary = getSampleSummary(orderId);
        java.math.BigDecimal totalHours = summary.get("totalHours") instanceof java.math.BigDecimal
                ? (java.math.BigDecimal) summary.get("totalHours") : java.math.BigDecimal.ZERO;
        java.math.BigDecimal materialCost = summary.get("materialCost") instanceof java.math.BigDecimal
                ? (java.math.BigDecimal) summary.get("materialCost") : java.math.BigDecimal.ZERO;
        standardOrder.setRemark("由样品单[" + sampleOrder.getOrderNo() + "]转量产生成"
                + (sampleOrder.getEngineeringNote() != null && !sampleOrder.getEngineeringNote().isEmpty()
                    ? "\n【工艺参数传承】" + sampleOrder.getEngineeringNote() : "")
                + (sampleOrder.getCurrentProcess() != null && !sampleOrder.getCurrentProcess().isEmpty()
                    ? "\n【最后工序】" + sampleOrder.getCurrentProcess() : "")
                + (materialCost.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? "\n【材料成本】" + materialCost + "元" : "")
                + (totalHours.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? "\n【打样工时】" + totalHours + "小时" : ""));

        // 继承样品单链路 traceId（转量产生成的订单可查看完整流水/链路追踪）
        standardOrder.setTraceId(sampleOrder.getTraceId());

        orderMapper.insert(standardOrder);

        // 订单首条操作日志（带 traceId，供销售订单“查看流水”链路追踪）
        try {
            SysOperLog operLog = new SysOperLog();
            operLog.setModule("订单管理");
            operLog.setBusinessType(2); // 修改/转换
            operLog.setOperUrl("/sales/sample-order/convert-to-production/" + orderId);
            operLog.setBizType("order");
            operLog.setBizId(String.valueOf(standardOrder.getOrderId()));
            operLog.setTraceId(sampleOrder.getTraceId());
            operLog.setBizStatus(1); // 订单草稿
            operLog.setStatus(1);
            operLog.setOperParam("{\"orderNo\":\"" + standardOrder.getOrderNo()
                    + "\",\"sourceSample\":\"" + sampleOrder.getOrderNo() + "\"}");
            operLog.setCreateTime(LocalDateTime.now());
            try {
                operLog.setUserId(SecurityUtils.getUserId());
                operLog.setUsername(SecurityUtils.getUsername());
                operLog.setRealName(SecurityUtils.getRealName());
            } catch (Exception ignored) {
            }
            logSaveService.saveOperLog(operLog);
        } catch (Exception e) {
            log.warn("记录订单转量产操作日志失败: {}", e.getMessage());
        }

        // 复制产品明细
        copyOrderProducts(sampleOrder.getOrderId(), standardOrder.getOrderId());

        // 更新报价单的 convertedOrderId（如果有报价单关联）
        if (sampleOrder.getQuotationId() != null) {
            try {
                SalesQuotation quotation = quotationMapper.selectById(sampleOrder.getQuotationId());
                if (quotation != null) {
                    quotation.setConvertedOrderId(standardOrder.getOrderId());
                    quotation.setConvertTime(java.time.LocalDateTime.now());
                    quotationMapper.updateById(quotation);
                }
            } catch (Exception e) {
                log.warn("更新报价单转换信息失败: {}", e.getMessage());
            }
        }

        // 回写样品单的 convertedOrderId
        SalesOrder update = new SalesOrder();
        update.setOrderId(orderId);
        update.setConvertedOrderId(standardOrder.getOrderId());
        update.setConvertOrderTime(new Date());
        orderMapper.updateById(update);

        log.info("样品单[{}] 转量产成功，生成标准订单[{}] (orderId={})",
                sampleOrder.getOrderNo(), standardOrderNo, standardOrder.getOrderId());
        return orderMapper.selectById(orderId);
    }

    /**
     * 样品单作废
     * 非终态（未转量产/未关闭/未作废）样品单可作废
     */
    @Override
    @Event(value = "sample.cancelled", bizId = "#orderId", bizType = "'sample'")
    public SalesOrder cancelSample(Long orderId, String cancelReason) {
        SalesOrder sampleOrder = orderMapper.selectById(orderId);
        if (sampleOrder == null || sampleOrder.getDeleted() == 1) {
            throw new BusinessException("样品单不存在");
        }

        // 终态不允许作废：已转量产(7)/已关闭(8)/已作废(10)
        Integer current = sampleOrder.getSampleStatus();
        if (SampleOrderStatusEnum.TRANSFERRED.getCode().equals(current)
                || SampleOrderStatusEnum.CLOSED.getCode().equals(current)
                || SampleOrderStatusEnum.CANCELLED.getCode().equals(current)) {
            String name = SampleOrderStatusEnum.getByCodeSafe(current)
                    .map(SampleOrderStatusEnum::getName).orElse("未知");
            throw new BusinessException("当前状态[" + name + "]不允许作废");
        }

        int affected = orderMapper.updateSampleStatus(orderId, current, SampleOrderStatusEnum.CANCELLED.getCode());
        if (affected == 0) {
            throw new BusinessException("样品单状态已变更，无法作废，请刷新后重试");
        }

        // 记录作废原因
        try {
            SalesOrder update = new SalesOrder();
            update.setOrderId(orderId);
            update.setRemark("[作废] " + (cancelReason != null ? cancelReason : ""));
            orderMapper.updateById(update);
        } catch (Exception e) {
            log.warn("记录样品单作废原因失败: {}", e.getMessage());
        }

        // DEV-527：已接单 → 派任务到接单人（具体人），未接单由事件按角色通知工程管理
        if (sampleOrder.getEngineeringAcceptor() != null && !sampleOrder.getEngineeringAcceptor().isEmpty()) {
            try {
                com.jjx.system.domain.entity.SysTask task = new com.jjx.system.domain.entity.SysTask();
                task.setTaskCode("sample.cancelled-" + System.currentTimeMillis());
                task.setTaskType("general");
                task.setTitle("样品单【" + sampleOrder.getOrderNo() + "】已作废，请停止打样并确认");
                task.setDescription("作废原因：" + (cancelReason != null ? cancelReason : "-")
                        + "\n接单人：" + sampleOrder.getEngineeringAcceptor());
                task.setKanbanModule("office");
                task.setAssignRole(9L);
                task.setAssigneeName(sampleOrder.getEngineeringAcceptor());
                task.setSourceEvent("sample.cancelled");
                task.setBizType("sample");
                task.setBizId(orderId);
                task.setPriority("high");
                task.setStatus(0);
                task.setStartTime(java.time.LocalDateTime.now());
                sysTaskMapper.insert(task);
                log.info("样品单[{}] 作废，已派任务给接单人[{}]", sampleOrder.getOrderNo(), sampleOrder.getEngineeringAcceptor());
            } catch (Exception te) {
                log.warn("派任务给接单人失败: {}", te.getMessage());
            }
        }

        log.info("样品单[{}] 已作废，原因: {}", sampleOrder.getOrderNo(), cancelReason);
        return orderMapper.selectById(orderId);
    }

    /**
     * 复制报价单明细到样品单/订单（产品资料转移与转量产的数据基础）
     */
    private void copyQuotationItemsToOrder(Long quotationId, Long targetOrderId) {
        java.util.List<com.jjx.sales.domain.entity.SalesQuotationItem> items = quotationItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesQuotationItem>()
                        .eq(com.jjx.sales.domain.entity.SalesQuotationItem::getQuotationId, quotationId));
        if (items == null || items.isEmpty()) {
            log.warn("报价单[{}]无明细，未复制到订单[{}]", quotationId, targetOrderId);
            return;
        }
        java.util.List<com.jjx.sales.domain.dto.SalesOrderProductDTO> dtos = new java.util.ArrayList<>();
        for (com.jjx.sales.domain.entity.SalesQuotationItem it : items) {
            com.jjx.sales.domain.dto.SalesOrderProductDTO dto = new com.jjx.sales.domain.dto.SalesOrderProductDTO();
            dto.setOrderId(targetOrderId);
            dto.setProductId(it.getProductId());
            dto.setProductCode(it.getProductCode());
            dto.setProductName(it.getProductName());
            dto.setQuantity(it.getQuantity());
            dto.setUnitPrice(it.getUnitPrice());
            dto.setAmount(it.getAmount());
            dto.setUnit(it.getUnit());
            dto.setSpecification(it.getCustomRequirements());
            dtos.add(dto);
        }
        orderProductService.batchAdd(dtos);
        log.info("报价单[{}]明细已复制到订单[{}] ({}条)", quotationId, targetOrderId, dtos.size());
    }

    /**
     * DEV-806：统一打样聚合口径——total_quantity 按明细 quantity 求和刷新
     */
    private void updateTotalQuantityByItems(Long orderId) {
        try {
            java.util.List<com.jjx.sales.domain.vo.SalesOrderProductVO> items =
                    orderProductService.getListByOrderId(orderId);
            int sum = 0;
            if (items != null) {
                for (com.jjx.sales.domain.vo.SalesOrderProductVO it : items) {
                    sum += it.getQuantity() != null ? it.getQuantity() : 0;
                }
            }
            SalesOrder upd = new SalesOrder();
            upd.setOrderId(orderId);
            upd.setTotalQuantity(sum);
            orderMapper.updateById(upd);
        } catch (Exception e) {
            log.warn("刷新订单[{}] total_quantity 失败: {}", orderId, e.getMessage());
        }
    }

    /**
     * 复制样品单的产品明细到新标准订单
     */
    private void copyOrderProducts(Long sourceOrderId, Long targetOrderId) {
        List<SalesOrderProductDTO> items = new ArrayList<>();
        var productVOs = orderProductService.getListByOrderId(sourceOrderId);

        for (var vo : productVOs) {
            SalesOrderProductDTO dto = new SalesOrderProductDTO();
            dto.setOrderId(targetOrderId);
            dto.setProductId(vo.getProductId());
            dto.setProductCode(vo.getProductCode());
            dto.setProductName(vo.getProductName());
            dto.setQuantity(vo.getQuantity());
            dto.setUnitPrice(vo.getUnitPrice());
            dto.setAmount(vo.getAmount());
            dto.setUnit(vo.getUnit());
            dto.setSpecification(vo.getSpecification());
            dto.setRemark(vo.getRemark());
            items.add(dto);
        }

        if (!items.isEmpty()) {
            orderProductService.batchAdd(items);
            log.info("转量产时复制了 {} 条产品明细到标准订单[{}]", items.size(), targetOrderId);
        }
    }

    @Override
    public SalesOrder selectById(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) {
            throw new BusinessException("样品单不存在或已被删除");
        }
        return order;
    }

    @Override
    public List<SalesOrder> selectSampleList(Long customerId, Integer sampleStatus, Long salesPersonId) {
        return selectSampleList(customerId, sampleStatus, salesPersonId, null);
    }

    @Override
    public List<SalesOrder> selectSampleList(Long customerId, Integer sampleStatus, Long salesPersonId, Boolean hasAcceptor) {
        java.util.List<SalesOrder> list = orderMapper.selectSampleOrders();
        if (list == null || list.isEmpty()) {
            return list;
        }
        java.util.List<SalesOrder> result = list;
        if (customerId != null) {
            result = result.stream().filter(o -> customerId.equals(o.getCustomerId())).collect(java.util.stream.Collectors.toList());
        }
        if (sampleStatus != null) {
            result = result.stream().filter(o -> sampleStatus.equals(o.getSampleStatus())).collect(java.util.stream.Collectors.toList());
        }
        if (hasAcceptor != null && hasAcceptor) {
            result = result.stream().filter(o -> o.getEngineeringAcceptor() != null && !o.getEngineeringAcceptor().isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }
        return result;
    }
}
