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

@Slf4j
@Service
@RequiredArgsConstructor
public class LogSaveService {

    private final SysOperLogMapper operLogMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysErrorLogMapper errorLogMapper;
    // DEV-1023：血缘反查 traceId 用（BOM/工艺路线/库存单据 → 所属订单）
    private final com.jjx.product.mapper.EngineeringBomMapper engineeringBomMapper;
    private final com.jjx.product.mapper.EngineeringRoutingMapper engineeringRoutingMapper;
    private final com.jjx.inventory.mapper.InventoryInboundOrderMapper inboundOrderMapper;
    private final com.jjx.inventory.mapper.InventoryOutboundOrderMapper outboundOrderMapper;
    private final com.jjx.sales.mapper.OrderMapper orderMapper;

    @Async("logExecutor")
    public void saveOperLog(SysOperLog operLog) {
        try {
            operLogMapper.insert(operLog);
        } catch (Exception e) {
            log.error("保存操作日志失败: {}", e.getMessage());
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
     * 血缘反查 traceId（DEV-1023）：BOM/工艺路线/库存单据等子单据通过 source 字段
     * 反查所属订单的 traceId，使打样/转标准/库存等环节操作并入订单统一流水链路
     */
    public String findTraceIdBySource(String bizType, String bizId) {
        try {
            Long id = Long.valueOf(bizId);
            Long orderId = null;
            if ("bom".equals(bizType)) {
                com.jjx.engineering.domain.entity.EngineeringBom bom = engineeringBomMapper.selectById(id);
                if (bom != null) orderId = bom.getSourceSampleId();
            } else if ("routing".equals(bizType)) {
                com.jjx.engineering.domain.entity.EngineeringRouting routing = engineeringRoutingMapper.selectById(id);
                if (routing != null) orderId = routing.getSourceSampleId();
            } else if ("inbound".equals(bizType)) {
                com.jjx.inventory.domain.InventoryInboundOrder inbound = inboundOrderMapper.selectById(id);
                if (inbound != null && inbound.getSourceId() != null) orderId = inbound.getSourceId();
            } else if ("outbound".equals(bizType)) {
                com.jjx.inventory.domain.InventoryOutboundOrder outbound = outboundOrderMapper.selectById(id);
                if (outbound != null && outbound.getSourceId() != null) orderId = outbound.getSourceId();
            }
            if (orderId == null) return null;
            com.jjx.sales.domain.entity.SalesOrder order = orderMapper.selectById(orderId);
            return order != null ? order.getTraceId() : null;
        } catch (Exception e) {
            log.debug("血缘反查traceId失败: {}", e.getMessage());
            return null;
        }
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
