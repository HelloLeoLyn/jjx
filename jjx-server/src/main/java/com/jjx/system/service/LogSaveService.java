package com.jjx.system.service;

import com.jjx.system.domain.entity.SysErrorLog;
import com.jjx.system.domain.entity.SysLoginLog;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.mapper.SysErrorLogMapper;
import com.jjx.system.mapper.SysLoginLogMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogSaveService {

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysErrorLogMapper errorLogMapper;
    // 库存等单据血缘反查 traceId；工程主数据不继承订单链路。
    private final com.jjx.product.mapper.EngineeringBomMapper engineeringBomMapper;
    private final com.jjx.product.mapper.EngineeringRoutingMapper engineeringRoutingMapper;
    private final com.jjx.inventory.mapper.InventoryInboundOrderMapper inboundOrderMapper;
    private final com.jjx.inventory.mapper.InventoryOutboundOrderMapper outboundOrderMapper;
    private final com.jjx.sales.mapper.OrderMapper orderMapper;
    // dev-20260929-008：采购链路（收货/付款/发票/采购来源入库）→ 采购单 trace
    private final com.jjx.purchase.mapper.PurchaseOrderMapper purchaseOrderMapper;
    private final com.jjx.purchase.mapper.PurchasePaymentMapper purchasePaymentMapper;
    private final com.jjx.purchase.mapper.PurchaseDocumentMapper purchaseDocumentMapper;

    @Async("logExecutor")
    public void saveOperLog(SysOperLog operLog) {
        try {
            operLogMapper.insert(operLog);
        } catch (Exception e) {
            log.error("保存操作日志失败: {}", e.getMessage());
        }
    }

    /**
     * 业务事务提交后，在独立事务中按列表顺序原子写入多条操作日志。
     * 不使用异步执行器，以保证同一批次的自增 ID 顺序。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveOperLogsInOrder(List<SysOperLog> operLogs) {
        if (operLogs == null || operLogs.isEmpty()) {
            return;
        }
        for (SysOperLog operLog : operLogs) {
            operLogMapper.insert(operLog);
        }
    }

    @Async("logExecutor")
    public void saveLoginLog(SysLoginLog loginLog) {
        try {
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("保存登录日志失败: {}", e.getMessage());
        }
    }

    /**
     * 按 bizType+bizId 从历史操作日志继承 traceId（同单据所有操作共享链路）
     */
    public String findTraceIdByBiz(String bizType, String bizId) {
        // 旧日志可能已挂到订单 trace，工程主数据也不能从历史日志继续继承。
        if (isProductEngineering(bizType)) return null;
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysOperLog> w =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            if (bizType != null && !bizType.isEmpty()) {
                w.eq(SysOperLog::getBizType, bizType);
            }
            w.eq(SysOperLog::getBizId, bizId)
                    .isNotNull(SysOperLog::getTraceId)
                    .ne(SysOperLog::getTraceId, "")
                    .orderByDesc(SysOperLog::getId)
                    .last("LIMIT 1");
            SysOperLog log = operLogMapper.selectOne(w);
            return log != null ? log.getTraceId() : null;
        } catch (Exception e) {
            log.error("查询历史traceId失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 血缘反查 traceId：库存等子单据通过 source 字段反查所属订单。
     * BOM/工艺路线归产品侧，保留 source_sample_id 数据血缘但不继承订单流水。
     */
    public String findTraceIdBySource(String bizType, String bizId) {
        if (isProductEngineering(bizType)) return null;
        try {
            Long id = Long.valueOf(bizId);
            // dev-20260929-008：采购链路（收货/付款/发票）统一挂到采购单 trace。
            // 收货日志 bizId = 采购单 ID；付款 bizId = 付款单 ID；发票 bizId = 采购票据 ID（purchase_document）。
            if ("purchase_receipt".equals(bizType)) {
                return traceOfPurchaseOrder(id);
            }
            if ("purchase_payment".equals(bizType)) {
                com.jjx.purchase.domain.entity.PurchasePayment payment = purchasePaymentMapper.selectById(id);
                return payment == null ? null : traceOfPurchaseOrder(payment.getOrderId());
            }
            if ("purchase_invoice".equals(bizType)) {
                com.jjx.purchase.domain.entity.PurchaseDocument doc = purchaseDocumentMapper.selectById(id);
                return doc == null ? null : traceOfPurchaseOrder(doc.getOrderId());
            }
            Long orderId = null;
            if ("inbound".equals(bizType)) {
                com.jjx.inventory.domain.InventoryInboundOrder inbound = inboundOrderMapper.selectById(id);
                if (inbound != null && inbound.getSourceId() != null) {
                    // dev-20260929-008：采购来源入库挂到采购单 trace；其余（生产/销售）沿用销售订单反查
                    if ("PURCHASE".equalsIgnoreCase(inbound.getSourceType())
                            || "PURCHASE".equalsIgnoreCase(inbound.getInboundType())) {
                        return traceOfPurchaseOrder(inbound.getSourceId());
                    }
                    orderId = inbound.getSourceId();
                }
            } else if ("outbound".equals(bizType)) {
                com.jjx.inventory.domain.InventoryOutboundOrder outbound = outboundOrderMapper.selectById(id);
                if (outbound != null && outbound.getSourceId() != null) orderId = outbound.getSourceId();
            } else if ("sales_order_review".equals(bizType)) {
                // 合同评审打印以订单 ID 作为 bizId；直接继承销售订单 traceId。
                orderId = id;
            }
            if (orderId == null) return null;
            com.jjx.sales.domain.entity.SalesOrder order = orderMapper.selectById(orderId);
            return order != null ? order.getTraceId() : null;
        } catch (Exception e) {
            log.debug("血缘反查traceId失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean isProductEngineering(String bizType) {
        return "bom".equals(bizType) || "routing".equals(bizType);
    }

    /** 采购单 trace（采购来源单据统一锚点；dev-20260929-008） */
    private String traceOfPurchaseOrder(Long orderId) {
        if (orderId == null) {
            return null;
        }
        com.jjx.purchase.domain.entity.PurchaseOrder po = purchaseOrderMapper.selectById(orderId);
        return po == null ? null : po.getTraceId();
    }

    @Async("logExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveErrorLog(SysErrorLog errorLog) {
        try {
            errorLogMapper.insert(errorLog);
        } catch (Exception e) {
            log.error("保存错误日志失败: {}", e.getMessage());
        }
    }
}
