package com.jjx.product.service;

import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.domain.vo.EngineeringRoutingValidationVO;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import com.jjx.product.mapper.EngineeringRoutingMapper;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class EngineeringRoutingValidator {
    private final EngineeringRoutingMapper routingMapper;
    private final EngineeringRoutingItemMapper routingItemMapper;
    private final ProductStandardProcessMapper standardProcessMapper;

    // ==================== 验证方法 ====================

    public boolean validateRouting(Long routingId) {
        EngineeringRoutingValidationVO result = validateRoutingWithDetail(routingId);
        return result.isValid();
    }

    public EngineeringRoutingValidationVO validateRoutingWithDetail(Long routingId) {
        long startTime = System.currentTimeMillis();

        EngineeringRoutingValidationVO.ValidationError.ValidationErrorBuilder errorBuilder = EngineeringRoutingValidationVO.ValidationError.builder();
        List<EngineeringRoutingValidationVO.ValidationError> errors = new ArrayList<>();
        List<EngineeringRoutingValidationVO.ValidationWarning> warnings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        try {
            // 1. 查询工艺路线
            EngineeringRouting routing = routingMapper.selectById(routingId);
            if (routing == null) {
                errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                        .code("ROUTING_001")
                        .message("工艺路线不存在")
                        .field("routingId")
                        .suggestion("请检查路线ID是否正确")
                        .build());

                return buildValidationResult(false, errors, warnings, suggestions, startTime);
            }

            // 2. 验证基础信息
            validateBasicInfo(routing, errors, warnings, suggestions);

            // 3. 查询明细
            List<EngineeringRoutingItem> items = routingItemMapper.selectByRoutingId(routingId);

            if (items == null || items.isEmpty()) {
                errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                        .code("ROUTING_002")
                        .message("工艺路线明细不能为空")
                        .field("items")
                        .suggestion("请添加至少一个工序")
                        .build());

                return buildValidationResult(false, errors, warnings, suggestions, startTime);
            }

            // 4. 验证明细
            validateItems(items, routing.getRoutingCode(), errors, warnings, suggestions);

            // 5. 验证工序顺序
            validateProcessOrder(items, errors, warnings, suggestions);

            // 6. 验证工时
            validateHours(items, errors, warnings, suggestions);

            // 7. 验证是否有循环依赖
            validateCircularDependency(routingId, errors, warnings, suggestions);

        } catch (Exception e) {
            log.error("验证工艺路线异常: routingId={}", routingId, e);
            errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                    .code("ROUTING_999")
                    .message("验证过程发生异常: " + e.getMessage())
                    .field("system")
                    .suggestion("请联系系统管理员")
                    .build());
        }

        boolean isValid = errors.isEmpty();
        return buildValidationResult(isValid, errors, warnings, suggestions, startTime);
    }

    /**
     * 构建验证结果
     */
    private static EngineeringRoutingValidationVO buildValidationResult(
            boolean isValid,
            List<EngineeringRoutingValidationVO.ValidationError> errors,
            List<EngineeringRoutingValidationVO.ValidationWarning> warnings,
            List<String> suggestions,
            long startTime) {

        long duration = System.currentTimeMillis() - startTime;
        String status;
        if (errors.isEmpty() && warnings.isEmpty()) {
            status = "SUCCESS";
        } else if (errors.isEmpty()) {
            status = "WARNING";
        } else {
            status = "ERROR";
        }

        return EngineeringRoutingValidationVO.builder()
                .valid(isValid)
                .status(status)
                .errors(errors)
                .warnings(warnings)
                .suggestions(suggestions)
                .validateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .duration(duration)
                .build();
    }

    /**
     * 验证基础信息
     */
    private static void validateBasicInfo(
            EngineeringRouting routing,
            List<EngineeringRoutingValidationVO.ValidationError> errors,
            List<EngineeringRoutingValidationVO.ValidationWarning> warnings,
            List<String> suggestions) {

        // 验证路线编码
        if (routing.getRoutingCode() == null || routing.getRoutingCode().trim().isEmpty()) {
            errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                    .code("ROUTING_003")
                    .message("路线编码不能为空")
                    .field("routingCode")
                    .suggestion("请输入路线编码")
                    .build());
        } else if (routing.getRoutingCode().length() > 50) {
            errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                    .code("ROUTING_004")
                    .message("路线编码长度不能超过50个字符")
                    .field("routingCode")
                    .suggestion("请缩短路线编码长度")
                    .build());
        }

        // 验证路线名称
        if (routing.getRoutingName() == null || routing.getRoutingName().trim().isEmpty()) {
            errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                    .code("ROUTING_005")
                    .message("路线名称不能为空")
                    .field("routingName")
                    .suggestion("请输入路线名称")
                    .build());
        }

        // 验证产品关联
        if (routing.getProductId() == null) {
            errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                    .code("ROUTING_006")
                    .message("关联产品不能为空")
                    .field("productId")
                    .suggestion("请选择关联产品")
                    .build());
        }

        // 验证版本号
        if (routing.getRoutingVersion() == null || routing.getRoutingVersion().trim().isEmpty()) {
            errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                    .code("ROUTING_007")
                    .message("版本号不能为空")
                    .field("routingVersion")
                    .suggestion("请输入版本号，如 v1.0")
                    .build());
        }

        // 验证审核状态
        if (routing.getApproveStatus() == null) {
            warnings.add(EngineeringRoutingValidationVO.ValidationWarning.builder()
                    .code("ROUTING_W001")
                    .message("审核状态未设置，将默认为草稿")
                    .field("approveStatus")
                    .suggestion("请提交审批")
                    .build());
        }
    }

    /**
     * 验证明细
     */
    private void validateItems(
            List<EngineeringRoutingItem> items,
            String routingCode,
            List<EngineeringRoutingValidationVO.ValidationError> errors,
            List<EngineeringRoutingValidationVO.ValidationWarning> warnings,
            List<String> suggestions) {

        // 批量查询标准工序
        List<Long> processIds = items.stream()
                .map(EngineeringRoutingItem::getProcessId)
                .distinct()
                .collect(Collectors.toList());

        List<ProductStandardProcess> processes = standardProcessMapper.selectBatchIds(processIds);
        Map<Long, ProductStandardProcess> processMap = processes.stream()
                .collect(Collectors.toMap(ProductStandardProcess::getProcessId, p -> p));

        // 检查重复工序
        Set<Long> processIdSet = new HashSet<>();
        List<Long> duplicateProcessIds = new ArrayList<>();

        for (EngineeringRoutingItem item : items) {
            // 验证工序是否存在
            if (item.getProcessId() == null) {
                errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                        .code("ROUTING_008")
                        .message("工序ID不能为空")
                        .field("processId")
                        .suggestion("请选择标准工序")
                        .build());
                continue;
            }

            ProductStandardProcess process = processMap.get(item.getProcessId());
            if (process == null) {
                errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                        .code("ROUTING_009")
                        .message("工序不存在: processId=" + item.getProcessId())
                        .field("processId")
                        .suggestion("请选择有效的标准工序")
                        .build());
                continue;
            }

            // 检查工序是否启用
            if (process.getIsEnabled() == null || process.getIsEnabled() != 1) {
                warnings.add(EngineeringRoutingValidationVO.ValidationWarning.builder()
                        .code("ROUTING_W002")
                        .message("工序已禁用: " + process.getProcessCode() + " - " + process.getProcessName())
                        .field("processId")
                        .suggestion("请启用该工序或更换其他工序")
                        .build());
            }

            // 检查重复工序
            if (!processIdSet.add(item.getProcessId())) {
                duplicateProcessIds.add(item.getProcessId());
            }

            // 验证定制工时
            if (item.getCustomLaborHours() != null && item.getCustomLaborHours().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                        .code("ROUTING_010")
                        .message("定制人工工时不能为负数")
                        .field("customLaborHours")
                        .suggestion("请输入非负数")
                        .build());
            }

            if (item.getCustomMachineHours() != null && item.getCustomMachineHours().compareTo(BigDecimal.ZERO) < 0) {
                errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                        .code("ROUTING_011")
                        .message("定制机器工时不能为负数")
                        .field("customMachineHours")
                        .suggestion("请输入非负数")
                        .build());
            }
        }

        // 处理重复工序警告
        if (!duplicateProcessIds.isEmpty()) {
            warnings.add(EngineeringRoutingValidationVO.ValidationWarning.builder()
                    .code("ROUTING_W003")
                    .message("存在重复的工序: " + duplicateProcessIds)
                    .field("processId")
                    .suggestion("请检查是否重复添加了相同工序")
                    .build());
        }
    }

    /**
     * 验证工序顺序
     */
    private void validateProcessOrder(
            List<EngineeringRoutingItem> items,
            List<EngineeringRoutingValidationVO.ValidationError> errors,
            List<EngineeringRoutingValidationVO.ValidationWarning> warnings,
            List<String> suggestions) {

        // 检查工序顺序是否连续
        for (int i = 0; i < items.size(); i++) {
            EngineeringRoutingItem item = items.get(i);
            int expectedOrder = i + 1;

            if (item.getProcessOrder() == null) {
                errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                        .code("ROUTING_012")
                        .message("工序顺序不能为空")
                        .field("processOrder")
                        .suggestion("请设置工序顺序")
                        .build());
                continue;
            }

            if (item.getProcessOrder() != expectedOrder) {
                errors.add(EngineeringRoutingValidationVO.ValidationError.builder()
                        .code("ROUTING_013")
                        .message(String.format("工序顺序不正确: 期望 %d，实际 %d", expectedOrder, item.getProcessOrder()))
                        .field("processOrder")
                        .suggestion("请按顺序排列工序")
                        .build());
                break;
            }
        }

        // 检查是否有明显的顺序问题（如印刷在组装之后）
        List<String> processTypes = items.stream()
                .map(item -> {
                    ProductStandardProcess process = standardProcessMapper.selectById(item.getProcessId());
                    return process != null ? process.getProcessType() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int printingIndex = processTypes.indexOf("PRINTING");
        int packagingIndex = processTypes.indexOf("PACKAGING");

        if (printingIndex > packagingIndex && packagingIndex >= 0 && printingIndex >= 0) {
            warnings.add(EngineeringRoutingValidationVO.ValidationWarning.builder()
                    .code("ROUTING_W004")
                    .message("印刷工序在包装工序之后，请确认顺序是否正确")
                    .field("processOrder")
                    .suggestion("通常印刷应该在包装之前")
                    .build());
        }
    }

    /**
     * 验证工时
     */
    private static void validateHours(
            List<EngineeringRoutingItem> items,
            List<EngineeringRoutingValidationVO.ValidationError> errors,
            List<EngineeringRoutingValidationVO.ValidationWarning> warnings,
            List<String> suggestions) {

        BigDecimal totalLaborHours = BigDecimal.ZERO;
        BigDecimal totalMachineHours = BigDecimal.ZERO;

        for (EngineeringRoutingItem item : items) {
            if (item.getCustomLaborHours() != null) {
                totalLaborHours = totalLaborHours.add(item.getCustomLaborHours());
            }
            if (item.getCustomMachineHours() != null) {
                totalMachineHours = totalMachineHours.add(item.getCustomMachineHours());
            }
        }

        // 验证总工时是否合理
        if (totalLaborHours.compareTo(BigDecimal.valueOf(24)) > 0) {
            warnings.add(EngineeringRoutingValidationVO.ValidationWarning.builder()
                    .code("ROUTING_W005")
                    .message("总人工工时超过24小时，可能影响交货期")
                    .field("totalLaborHours")
                    .suggestion("请评估是否需要优化工序或增加人手")
                    .build());
        }

        if (totalMachineHours.compareTo(BigDecimal.valueOf(48)) > 0) {
            warnings.add(EngineeringRoutingValidationVO.ValidationWarning.builder()
                    .code("ROUTING_W006")
                    .message("总机器工时超过48小时，可能影响设备排期")
                    .field("totalMachineHours")
                    .suggestion("请评估是否需要优化工序或增加设备")
                    .build());
        }

        // 验证工时是否为0
        if (totalLaborHours.compareTo(BigDecimal.ZERO) == 0) {
            warnings.add(EngineeringRoutingValidationVO.ValidationWarning.builder()
                    .code("ROUTING_W007")
                    .message("总人工工时为0，请确认是否正确")
                    .field("totalLaborHours")
                    .suggestion("请检查各工序的人工工时设置")
                    .build());
        }

        if (totalMachineHours.compareTo(BigDecimal.ZERO) == 0) {
            warnings.add(EngineeringRoutingValidationVO.ValidationWarning.builder()
                    .code("ROUTING_W008")
                    .message("总机器工时为0，请确认是否正确")
                    .field("totalMachineHours")
                    .suggestion("请检查各工序的机器工时设置")
                    .build());
        }
    }

    /**
     * 验证循环依赖
     */
    private void validateCircularDependency(
            Long routingId,
            List<EngineeringRoutingValidationVO.ValidationError> errors,
            List<EngineeringRoutingValidationVO.ValidationWarning> warnings,
            List<String> suggestions) {

        // TODO: 如果需要支持子路线，实现循环依赖检测
        // 当前版本不支持子路线，无需检测
    }
}
