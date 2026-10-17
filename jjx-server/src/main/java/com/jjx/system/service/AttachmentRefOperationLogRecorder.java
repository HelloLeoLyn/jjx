package com.jjx.system.service;

import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.utils.OperLogDetailBuilder;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes attachment-reference changes into the owning business document's operation timeline. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentRefOperationLogRecorder {
    private final LogSaveService logSaveService;

    /** Collects multiple attachment changes in one business transaction into one timeline event per document/action. */
    public void record(String sourceType, Long sourceId, String traceId, int businessType,
                       String action, Long refId, Long attachmentId, String fileName,
                       boolean includeAttachment, List<String> changes) {
        if (sourceType == null || sourceType.isBlank() || sourceId == null || attachmentId == null) return;

        SysOperLog event = new SysOperLog();
        event.setModule(moduleOf(sourceType));
        event.setBusinessType(businessType);
        event.setOperUrl("attachment.ref." + action);
        event.setBizType(sourceType);
        event.setBizId(String.valueOf(sourceId));
        event.setTraceId(traceId);
        event.setAction(action);
        event.setOperParam(changes == null || changes.isEmpty() ? action : String.join("；", changes));
        event.setDetail(OperLogDetailBuilder.build(changes,
            includeAttachment && refId != null ? List.of(attachmentItem(refId, attachmentId, fileName)) : List.of()));
        event.setStatus(1);
        event.setCreateTime(LocalDateTime.now());
        try {
            event.setUsername(SecurityUtils.getUsername());
            event.setUserId(SecurityUtils.getUserId());
            event.setRealName(SecurityUtils.getRealName());
        } catch (Exception ignored) {
            // A background/system operation may not have a request security context.
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            persist(List.of(event));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, SysOperLog> pending = (Map<String, SysOperLog>) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            Map<String, SysOperLog> newPending = new LinkedHashMap<>();
            TransactionSynchronizationManager.bindResource(this, newPending);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (!newPending.isEmpty()) persist(new ArrayList<>(newPending.values()));
                }

                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.hasResource(AttachmentRefOperationLogRecorder.this)) {
                        TransactionSynchronizationManager.unbindResource(AttachmentRefOperationLogRecorder.this);
                    }
                }
            });
            pending = newPending;
        }

        String key = sourceType + ":" + sourceId + ":" + action;
        SysOperLog accumulated = pending.get(key);
        if (accumulated == null) {
            pending.put(key, event);
            return;
        }
        List<Map<String, Object>> attachments = readAttachments(accumulated);
        if (includeAttachment && refId != null
                && attachments.stream().noneMatch(item -> String.valueOf(refId).equals(String.valueOf(item.get("refId"))))) {
            attachments.add(attachmentItem(refId, attachmentId, fileName));
        }
        List<String> mergedChanges = readChanges(accumulated);
        if (changes != null) mergedChanges.addAll(changes);
        accumulated.setDetail(OperLogDetailBuilder.build(mergedChanges, attachments));
        accumulated.setOperParam(String.join("；", mergedChanges));
    }

    private void persist(List<SysOperLog> events) {
        try {
            logSaveService.saveOperLogsInOrder(events);
        } catch (Exception e) {
            log.error("保存附件业务流水失败（附件已按原事务落库）: {}", e.getMessage(), e);
        }
    }

    private Map<String, Object> attachmentItem(Long refId, Long attachmentId, String fileName) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("refId", refId);
        item.put("id", attachmentId);
        item.put("fileName", fileName == null ? "附件" + attachmentId : fileName);
        return item;
    }

    private List<Map<String, Object>> readAttachments(SysOperLog event) {
        try {
            Map<?, ?> detail = cn.hutool.json.JSONUtil.parseObj(event.getDetail());
            List<Map<String, Object>> values = new ArrayList<>();
            for (Object value : cn.hutool.json.JSONUtil.parseArray(detail.get("attachments"))) {
                if (value instanceof Map<?, ?> map) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    map.forEach((key, field) -> item.put(String.valueOf(key), field));
                    values.add(item);
                }
            }
            return values;
        } catch (Exception e) {
            log.warn("读取附件流水暂存内容失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> readChanges(SysOperLog event) {
        try {
            return new ArrayList<>(cn.hutool.json.JSONUtil.parseObj(event.getDetail()).getJSONArray("changes").toList(String.class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String moduleOf(String sourceType) {
        return switch (sourceType) {
            case "inquiry" -> "询价单管理";
            case "quotation" -> "报价单管理";
            case "sales_order" -> "销售订单管理";
            case "sample" -> "样品单管理";
            case "product" -> "产品管理";
            case "purchase_order" -> "采购订单管理";
            case "purchase_payment" -> "采购付款管理";
            case "purchase_receipt" -> "采购收货管理";
            case "purchase_invoice" -> "采购发票管理";
            case "production_order" -> "生产订单管理";
            case "material" -> "库存物料管理";
            default -> "附件管理";
        };
    }
}
