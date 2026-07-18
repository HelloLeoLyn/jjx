package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.converter.SalesLogConverter;
import com.jjx.sales.domain.dto.SalesLogQueryDTO;
import com.jjx.sales.domain.entity.SalesLog;
import com.jjx.sales.domain.vo.SalesLogVO;
import com.jjx.sales.enums.OperationResultEnum;
import com.jjx.sales.enums.OperationTypeEnum;
import com.jjx.sales.mapper.SalesLogMapper;
import com.jjx.sales.service.SalesLogService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 销售订单操作日志服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesLogServiceImpl extends ServiceImpl<SalesLogMapper, SalesLog> implements SalesLogService {

    private final SalesLogMapper salesLogMapper;
    private final SalesLogConverter salesLogConverter;

    @Override
    public PageResult<SalesLogVO> pageQuery(SalesLogQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<SalesLog> wrapper = buildQueryWrapper(queryDTO);

        // 排序 - 使用 Lambda 表达式
        if (StringUtils.isNotBlank(queryDTO.getOrderByColumn())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getIsAsc());
            switch (queryDTO.getOrderByColumn()) {
                case "logId":
                    if (isAsc) {
                        wrapper.orderByAsc(SalesLog::getLogId);
                    } else {
                        wrapper.orderByDesc(SalesLog::getLogId);
                    }
                    break;
                case "orderId":
                    if (isAsc) {
                        wrapper.orderByAsc(SalesLog::getOrderId);
                    } else {
                        wrapper.orderByDesc(SalesLog::getOrderId);
                    }
                    break;
                case "orderNo":
                    if (isAsc) {
                        wrapper.orderByAsc(SalesLog::getOrderNo);
                    } else {
                        wrapper.orderByDesc(SalesLog::getOrderNo);
                    }
                    break;
                case "operationType":
                    if (isAsc) {
                        wrapper.orderByAsc(SalesLog::getOperationType);
                    } else {
                        wrapper.orderByDesc(SalesLog::getOperationType);
                    }
                    break;
                case "operatorId":
                    if (isAsc) {
                        wrapper.orderByAsc(SalesLog::getOperatorId);
                    } else {
                        wrapper.orderByDesc(SalesLog::getOperatorId);
                    }
                    break;
                case "operatorName":
                    if (isAsc) {
                        wrapper.orderByAsc(SalesLog::getOperatorName);
                    } else {
                        wrapper.orderByDesc(SalesLog::getOperatorName);
                    }
                    break;
                case "operationTime":
                    if (isAsc) {
                        wrapper.orderByAsc(SalesLog::getOperationTime);
                    } else {
                        wrapper.orderByDesc(SalesLog::getOperationTime);
                    }
                    break;
                case "operationResult":
                    if (isAsc) {
                        wrapper.orderByAsc(SalesLog::getOperationResult);
                    } else {
                        wrapper.orderByDesc(SalesLog::getOperationResult);
                    }
                    break;
                default:
                    wrapper.orderByDesc(SalesLog::getOperationTime);
                    break;
            }
        } else {
            wrapper.orderByDesc(SalesLog::getOperationTime);
        }

        // 分页查询
        Page<SalesLog> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<SalesLog> logPage = salesLogMapper.selectPage(page, wrapper);

        List<SalesLogVO> voList = salesLogConverter.toVOList(logPage.getRecords());

        return PageResult.build(voList,logPage.getTotal());
    }

    @Override
    public SalesLogVO getById(Long logId) {
        SalesLog salesLog = salesLogMapper.selectById(logId);
        if (salesLog == null) {
            return null;
        }
        return convertToVO(salesLog);
    }

    @Override
    public List<SalesLogVO> getByOrderId(Long orderId) {
        LambdaQueryWrapper<SalesLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SalesLog::getOrderId, orderId)
                .orderByDesc(SalesLog::getOperationTime);

        List<SalesLog> logs = salesLogMapper.selectList(wrapper);
        return logs.stream()
                .map(SalesLogServiceImpl::convertToVO).toList()
                ;
    }

    @Override
    public List<SalesLogVO> getByOrderNo(String orderNo) {
        LambdaQueryWrapper<SalesLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SalesLog::getOrderNo, orderNo)
                .orderByDesc(SalesLog::getOperationTime);

        List<SalesLog> logs = salesLogMapper.selectList(wrapper);
        return logs.stream()
                .map(SalesLogServiceImpl::convertToVO).toList();
    }

    @Override
    public SalesLogVO getLatestByOrderId(Long orderId) {
        LambdaQueryWrapper<SalesLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SalesLog::getOrderId, orderId)
                .orderByDesc(SalesLog::getOperationTime)
                .last("LIMIT 1");

        SalesLog salesLog = salesLogMapper.selectOne(wrapper);
        return salesLog != null ? convertToVO(salesLog) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long logId) {
        salesLogMapper.deleteById(logId);
        log.info("删除操作日志成功，日志ID: {}", logId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByOrderId(Long orderId) {
        LambdaQueryWrapper<SalesLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SalesLog::getOrderId, orderId);
        int count = salesLogMapper.delete(wrapper);
        log.info("删除订单{}的操作日志成功，共{}条", orderId, count);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteByIds(List<Long> logIds) {
        if (logIds == null || logIds.isEmpty()) {
            return;
        }
        salesLogMapper.deleteBatchIds(logIds);
        log.info("批量删除操作日志成功，数量: {}", logIds.size());
    }

    @Override
    public byte[] exportLogs(SalesLogQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<SalesLog> wrapper = buildQueryWrapper(queryDTO);
        wrapper.orderByDesc(SalesLog::getOperationTime);

        // 查询数据
        List<SalesLog> logs = salesLogMapper.selectList(wrapper);

        // 转换为VO
        List<SalesLogVO> voList = logs.stream()
                .map(SalesLogServiceImpl::convertToVO).toList();

        // 生成Excel
        return generateExcel(voList);
    }

    @Override
    public List<Map<String, Object>> getOperationTypeStats(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SalesLog> wrapper = new LambdaQueryWrapper<>();
        if (startTime != null) {
            wrapper.ge(SalesLog::getOperationTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SalesLog::getOperationTime, endTime);
        }

        List<SalesLog> logs = salesLogMapper.selectList(wrapper);

        // 按操作类型分组统计
        Map<Integer, Long> stats = logs.stream()
                .collect(Collectors.groupingBy(
                        SalesLog::getOperationType,
                        Collectors.counting()
                ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : stats.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("operationType", entry.getKey());
            item.put("operationTypeName", getOperationTypeName(entry.getKey()));
            item.put("count", entry.getValue());
            result.add(item);
        }

        // 按数量降序排序
        result.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));

        return result;
    }

    @Override
    public List<Map<String, Object>> getOperatorStats(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SalesLog> wrapper = new LambdaQueryWrapper<>();
        if (startTime != null) {
            wrapper.ge(SalesLog::getOperationTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SalesLog::getOperationTime, endTime);
        }

        List<SalesLog> logs = salesLogMapper.selectList(wrapper);

        // 按操作人分组统计
        Map<String, Map<String, Object>> stats = new LinkedHashMap<>();
        for (SalesLog log : logs) {
            String key = log.getOperatorId() + "_" + log.getOperatorName();
            if (!stats.containsKey(key)) {
                Map<String, Object> item = new HashMap<>();
                item.put("operatorId", log.getOperatorId());
                item.put("operatorName", log.getOperatorName());
                item.put("count", 0L);
                stats.put(key, item);
            }
            Map<String, Object> item = stats.get(key);
            item.put("count", (Long) item.get("count") + 1);
        }

        List<Map<String, Object>> result = new ArrayList<>(stats.values());
        // 按数量降序排序
        result.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));

        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建查询条件
     */
    private static LambdaQueryWrapper<SalesLog> buildQueryWrapper(SalesLogQueryDTO queryDTO) {
        LambdaQueryWrapper<SalesLog> wrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getOrderId() != null) {
            wrapper.eq(SalesLog::getOrderId, queryDTO.getOrderId());
        }
        if (StringUtils.isNotBlank(queryDTO.getOrderNo())) {
            wrapper.eq(SalesLog::getOrderNo, queryDTO.getOrderNo());
        }
        if (StringUtils.isNotBlank(queryDTO.getOperationType())) {
            wrapper.eq(SalesLog::getOperationType, queryDTO.getOperationType());
        }
        if (queryDTO.getOperatorId() != null) {
            wrapper.eq(SalesLog::getOperatorId, queryDTO.getOperatorId());
        }
        if (StringUtils.isNotBlank(queryDTO.getOperatorName())) {
            wrapper.like(SalesLog::getOperatorName, queryDTO.getOperatorName());
        }
        if (StringUtils.isNotBlank(queryDTO.getOperationResult())) {
            wrapper.eq(SalesLog::getOperationResult, queryDTO.getOperationResult());
        }
        if (queryDTO.getStartTime() != null) {
            wrapper.ge(SalesLog::getOperationTime, queryDTO.getStartTime());
        }
        if (queryDTO.getEndTime() != null) {
            wrapper.le(SalesLog::getOperationTime, queryDTO.getEndTime());
        }

        return wrapper;
    }

    /**
     * 实体转VO
     */
    private static SalesLogVO convertToVO(SalesLog salesLog) {
        if (salesLog == null) {
            return null;
        }

        SalesLogVO vo = new SalesLogVO();
        BeanUtils.copyProperties(salesLog, vo);

        // 设置操作类型名称
        vo.setOperationTypeName(getOperationTypeName(salesLog.getOperationType()));

        // 设置操作结果名称
        vo.setOperationResultName(getOperationResultName(salesLog.getOperationResult()));

        return vo;
    }

    /**
     * 获取操作类型名称
     */
    private static String getOperationTypeName(Integer operationType) {
        try {
            return OperationTypeEnum.getByCode(operationType).getName();
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 获取操作结果名称
     */
    private static String getOperationResultName(Integer operationResult) {
        try {
            return OperationResultEnum.getByCode(operationResult).getName();
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 生成Excel文件
     */
    private static byte[] generateExcel(List<SalesLogVO> logs) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("操作日志");

            // 创建标题行样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // 创建数据行样式
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 创建标题行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"日志ID", "订单号", "操作类型", "操作描述", "操作人", "操作时间", "操作结果", "备注"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 填充数据
            int rowIndex = 1;
            for (SalesLogVO log : logs) {
                Row row = sheet.createRow(rowIndex++);

                row.createCell(0).setCellValue(log.getLogId());
                row.createCell(1).setCellValue(log.getOrderNo() != null ? log.getOrderNo() : "");
                row.createCell(2).setCellValue(log.getOperationTypeName());
                row.createCell(3).setCellValue(log.getOperationDescription() != null ? log.getOperationDescription() : "");
                row.createCell(4).setCellValue(log.getOperatorName());
                row.createCell(5).setCellValue(log.getOperationTime() != null ?
                        log.getOperationTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
                row.createCell(6).setCellValue(log.getOperationResultName());
                row.createCell(7).setCellValue(log.getRemark() != null ? log.getRemark() : "");

                // 应用样式
                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // 设置最小宽度
                if (sheet.getColumnWidth(i) < 3000) {
                    sheet.setColumnWidth(i, 3000);
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("生成Excel文件失败", e);
            throw new RuntimeException("导出失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void log(Long orderId, String orderNo, OperationTypeEnum operationType,
                    String description, String remark, OperationResultEnum result) {
        try {
            SalesLog salesLog = new SalesLog();
            salesLog.setOrderId(orderId);
            salesLog.setOrderNo(orderNo);
            salesLog.setOperationType(operationType.getCode());
            salesLog.setOperationDescription(description);
            salesLog.setOperatorId(SecurityUtils.getUserId());
            salesLog.setOperatorName(SecurityUtils.getUsername());
            salesLog.setOperationTime(LocalDateTime.now());
            salesLog.setOperationResult(result.getCode());
            salesLog.setRemark(remark);

            salesLogMapper.insert(salesLog);

            log.debug("操作日志记录成功 - 订单ID: {}, 订单号: {}, 操作类型: {}, 结果: {}",
                    orderId, orderNo, operationType.getName(), result.getName());
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
            // 不抛出异常，避免影响主业务
        }
    }
}