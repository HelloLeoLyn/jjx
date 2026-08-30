package com.jjx.production.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.OrderStatusEnum;
import com.jjx.production.mapper.ProductionOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 工单超期判定定时任务（043）
 * 扫描待开始(5)/进行中(6)/已暂停(7) 且 planEndDate < 今天 → 置 OVERDUE(11)
 * 超期仅警示，允许开工/取消/完工（列表标红提示）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionOrderTimeoutTask {

    private final ProductionOrderMapper productionOrderMapper;

    /** 每小时整点执行一次 */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkOverdue() {
        try {
            log.info("[定时任务] 开始工单超期扫描");
            List<ProductionOrder> overdueOrders = productionOrderMapper.selectList(
                    new LambdaQueryWrapper<ProductionOrder>()
                            .in(ProductionOrder::getOrderStatus,
                                    OrderStatusEnum.PENDING_START.getValue(),
                                    OrderStatusEnum.IN_PROGRESS.getValue(),
                                    OrderStatusEnum.PAUSED.getValue())
                            .lt(ProductionOrder::getPlanEndDate, LocalDate.now()));
            int updated = 0;
            for (ProductionOrder order : overdueOrders) {
                order.setOrderStatus(OrderStatusEnum.OVERDUE.getValue());
                productionOrderMapper.updateById(order);
                updated++;
                log.info("工单{}已超期（计划完成{}），置为已超期(11)", order.getOrderNo(), order.getPlanEndDate());
            }
            log.info("[定时任务] 工单超期扫描完成，超期{}条", updated);
        } catch (Exception e) {
            log.error("[定时任务] 工单超期扫描异常: {}", e.getMessage(), e);
        }
    }
}
