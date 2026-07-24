package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.inventory.domain.InventoryAlertLog;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.mapper.InventoryAlertLogMapper;
import com.jjx.inventory.mapper.InventoryStockItemMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.mapper.InventoryTransactionMapper;
import com.jjx.inventory.service.InventoryReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存报表服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReportServiceImpl implements InventoryReportService {

    private final InventoryTransactionMapper transactionMapper;
    private final InventoryStockMapper stockMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final InventoryAlertLogMapper alertLogMapper;

    @Override
    public Map<String, Object> calculateTurnover(String startDate, String endDate) {
        log.info("计算库存周转率: startDate={}, endDate={}", startDate, endDate);
        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);

        // 统计出库总量
        LambdaQueryWrapper<InventoryTransaction> outWrapper = new LambdaQueryWrapper<InventoryTransaction>()
                .eq(InventoryTransaction::getTransactionType, "OUTBOUND")
                .between(InventoryTransaction::getTransactionTime, start, end);
        List<InventoryTransaction> outboundTx = transactionMapper.selectList(outWrapper);
        BigDecimal totalOutQty = outboundTx.stream()
                .map(tx -> tx.getQuantity() != null ? tx.getQuantity().abs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 统计当前库存总量
        List<InventoryStock> allStock = stockMapper.selectList(null);
        BigDecimal totalStock = allStock.stream()
                .map(s -> s.getTotalQuantity() != null ? s.getTotalQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long days = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.parse(startDate), LocalDate.parse(endDate));
        if (days <= 0) days = 1;

        // 周转率 = 出库量 / 平均库存
        BigDecimal avgInventory = totalStock.compareTo(BigDecimal.ZERO) > 0
                ? totalStock : BigDecimal.ONE;
        BigDecimal turnoverRate = totalOutQty.divide(avgInventory, 4, RoundingMode.HALF_UP);

        return Map.of(
                "turnoverRate", turnoverRate.doubleValue(),
                "totalOutbound", totalOutQty.doubleValue(),
                "averageInventory", avgInventory.doubleValue(),
                "periodDays", days
        );
    }

    @Override
    public Map<String, Object> calculateCost(String date) {
        log.info("计算库存成本: date={}", date);
        List<InventoryStock> stocks = stockMapper.selectList(null);
        BigDecimal totalCost = BigDecimal.ZERO;
        int materialCount = stocks.size();
        for (InventoryStock s : stocks) {
            if (s.getTotalQuantity() != null) {
                // 如果没有单位成本，按平均单价估算
                totalCost = totalCost.add(s.getTotalQuantity());
            }
        }
        return Map.of(
                "totalCost", totalCost.doubleValue(),
                "materialCount", materialCount,
                "averageCost", materialCount > 0 ? totalCost.divide(BigDecimal.valueOf(materialCount), 2, RoundingMode.HALF_UP).doubleValue() : 0,
                "warehouseCount", 1
        );
    }

    @Override
    public Map<String, Object> statInOut(String startDate, String endDate) {
        log.info("统计出入库: startDate={}, endDate={}", startDate, endDate);
        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);

        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<InventoryTransaction>()
                .between(InventoryTransaction::getTransactionTime, start, end);
        List<InventoryTransaction> txs = transactionMapper.selectList(wrapper);

        long inboundCount = txs.stream().filter(t -> "INBOUND".equals(t.getTransactionType())).count();
        long outboundCount = txs.stream().filter(t -> "OUTBOUND".equals(t.getTransactionType())).count();

        BigDecimal inboundQty = txs.stream()
                .filter(t -> "INBOUND".equals(t.getTransactionType()))
                .map(t -> t.getQuantity() != null ? t.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outboundQty = txs.stream()
                .filter(t -> "OUTBOUND".equals(t.getTransactionType()))
                .map(t -> t.getQuantity() != null ? t.getQuantity().abs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "inboundCount", inboundCount,
                "inboundQuantity", inboundQty.doubleValue(),
                "inboundAmount", 0,
                "outboundCount", outboundCount,
                "outboundQuantity", outboundQty.doubleValue(),
                "outboundAmount", 0
        );
    }

    @Override
    public List<Map<String, Object>> abcAnalysis() {
        log.info("执行ABC分析");
        List<InventoryStock> stocks = stockMapper.selectList(null);
        stocks.sort((a, b) -> {
            BigDecimal qtyA = a.getTotalQuantity() != null ? a.getTotalQuantity() : BigDecimal.ZERO;
            BigDecimal qtyB = b.getTotalQuantity() != null ? b.getTotalQuantity() : BigDecimal.ZERO;
            return qtyB.compareTo(qtyA);
        });

        BigDecimal total = stocks.stream()
                .map(s -> s.getTotalQuantity() != null ? s.getTotalQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) <= 0) return List.of();

        BigDecimal cumulative = BigDecimal.ZERO;
        List<Map<String, Object>> result = new ArrayList<>();
        for (InventoryStock s : stocks) {
            BigDecimal qty = s.getTotalQuantity() != null ? s.getTotalQuantity() : BigDecimal.ZERO;
            cumulative = cumulative.add(qty);
            double ratio = cumulative.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            String category = ratio <= 70 ? "A" : ratio <= 90 ? "B" : "C";
            result.add(Map.of(
                    "materialCode", s.getMaterialCode(),
                    "materialName", s.getMaterialName(),
                    "quantity", qty.doubleValue(),
                    "ratio", qty.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100,
                    "cumulativeRatio", ratio,
                    "category", category
            ));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> warehouseStockStat() {
        log.info("统计仓库库存");
        List<InventoryStock> stocks = stockMapper.selectList(null);
        return stocks.stream().map(s -> (Map<String, Object>) Map.of(
                "materialCode", s.getMaterialCode(),
                "materialName", s.getMaterialName(),
                "totalQuantity", s.getTotalQuantity() != null ? s.getTotalQuantity().doubleValue() : 0,
                "availableQuantity", s.getTotalQuantity() != null && s.getTotalReserved() != null
                        ? s.getTotalQuantity().subtract(s.getTotalReserved()).doubleValue() : 0
        )).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> materialTrend(Long materialId, int days) {
        log.info("分析物料库存趋势: materialId={}, days={}", materialId, days);
        // 按天统计出入库
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate d = start; d.isBefore(end); d = d.plusDays(1)) {
            LocalDate dayStart = d;
            LocalDate dayEnd = d.plusDays(1);
            LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<InventoryTransaction>()
                    .eq(InventoryTransaction::getMaterialId, materialId)
                    .between(InventoryTransaction::getTransactionTime, dayStart.atStartOfDay(), dayEnd.atStartOfDay());
            List<InventoryTransaction> dayTx = transactionMapper.selectList(wrapper);
            BigDecimal inQty = dayTx.stream()
                    .filter(t -> "INBOUND".equals(t.getTransactionType()))
                    .map(t -> t.getQuantity() != null ? t.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal outQty = dayTx.stream()
                    .filter(t -> "OUTBOUND".equals(t.getTransactionType()))
                    .map(t -> t.getQuantity() != null ? t.getQuantity().abs() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trend.add(Map.of(
                    "date", d.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    "inbound", inQty.doubleValue(),
                    "outbound", outQty.doubleValue()
            ));
        }
        return trend;
    }

    @Override
    public Map<String, Object> alertStat() {
        log.info("统计库存预警");
        List<InventoryAlertLog> allAlerts = alertLogMapper.selectList(null);
        long unprocessed = allAlerts.stream().filter(a -> "new".equals(a.getStatus())).count();
        long urgent = allAlerts.stream().filter(a -> "urgent".equals(a.getAlertLevel())).count();
        long warning = allAlerts.stream().filter(a -> "warning".equals(a.getAlertLevel())).count();
        long info = allAlerts.stream().filter(a -> "info".equals(a.getAlertLevel())).count();
        return Map.of(
                "totalAlerts", allAlerts.size(),
                "unprocessedAlerts", unprocessed,
                "urgentAlerts", urgent,
                "warningAlerts", warning,
                "infoAlerts", info
        );
    }

    @Override
    public Map<String, Object> stocktakeDiffStat(String startDate, String endDate) {
        return Map.of(
                "totalStocktakes", 0,
                "withDifferences", 0,
                "totalDifferenceAmount", 0,
                "averageDifferenceRate", 0.0
        );
    }

    @Override
    public Map<String, Object> transferStat(String startDate, String endDate) {
        return Map.of(
                "totalTransfers", 0,
                "completedTransfers", 0,
                "pendingTransfers", 0,
                "totalTransferAmount", 0
        );
    }

    @Override
    public List<Map<String, Object>> categoryStockStat() {
        List<InventoryStock> stocks = stockMapper.selectList(null);
        return stocks.stream().limit(10).map(s -> (Map<String, Object>) Map.of(
                "category", "默认",
                "materialCode", s.getMaterialCode(),
                "materialName", s.getMaterialName(),
                "quantity", s.getTotalQuantity() != null ? s.getTotalQuantity().doubleValue() : 0
        )).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> obsoleteAnalysis(int days) {
        log.info("分析呆滞料: days={}", days);
        List<InventoryStock> obsolete = stockMapper.selectObsolete();
        return obsolete.stream().limit(20).map(s -> (Map<String, Object>) Map.of(
                "materialCode", s.getMaterialCode(),
                "materialName", s.getMaterialName(),
                "quantity", s.getTotalQuantity() != null ? s.getTotalQuantity().doubleValue() : 0
        )).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> expiryAnalysis(int days) {
        log.info("分析保质期: days={}", days);
        List<InventoryStock> expiring = stockMapper.selectExpiring();
        return expiring.stream().limit(20).map(s -> (Map<String, Object>) Map.of(
                "materialCode", s.getMaterialCode(),
                "materialName", s.getMaterialName(),
                "quantity", s.getTotalQuantity() != null ? s.getTotalQuantity().doubleValue() : 0
        )).collect(Collectors.toList());
    }
}
