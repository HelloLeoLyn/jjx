package com.jjx.event;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 事件模板变量目录，数据来源为 common/manual/collected；collected 来自累积表 sys_event_var（二期）。 */
public final class EventVariableRegistry {

    public record Variable(String key, String description, String example, String source, String lastSeenAt) {
        public Variable(String key, String description, String example) {
            this(key, description, example, null, null);
        }
    }

    private static final List<Variable> COMMON = List.of(
            new Variable("bizNo", "业务单号", "SO260921001"),
            new Variable("bizId", "业务对象 ID", "1001"),
            new Variable("bizType", "业务类型", "order"),
            new Variable("triggerUserName", "触发账号", "zhangsan"),
            new Variable("triggerRealName", "触发人姓名", "张三"));

    private static final Map<String, String> MANUAL_DESCRIPTIONS = Map.ofEntries(
            Map.entry("orderNo", "销售订单号"), Map.entry("customerName", "客户名称"),
            Map.entry("sourceNo", "来源单号"), Map.entry("reportId", "报工单 ID"),
            Map.entry("productCode", "产品编码"), Map.entry("productName", "产品名称"),
            Map.entry("inboundNo", "入库单号"), Map.entry("supplierName", "供应商名称"),
            Map.entry("operatorName", "操作人"), Map.entry("warehouseId", "仓库 ID"),
            Map.entry("message", "消息"), Map.entry("count", "数量"),
            Map.entry("quantity", "数量"), Map.entry("code", "编码"), Map.entry("name", "名称"));

    private static final Map<String, List<Variable>> EVENT_VARIABLES = Map.ofEntries(
            Map.entry("order.delivering", List.of(
                    new Variable("orderNo", "销售订单号", "SO260921001"),
                    new Variable("customerName", "客户名称", "示例客户"),
                    new Variable("deliveryId", "发货单 ID", "2001"),
                    new Variable("deliveryNo", "发货单号", "DO260921001"),
                    new Variable("deliverQuantity", "本次发货数量", "100"))),
            Map.entry("purchase.arrived", List.of(
                    new Variable("sourceNo", "采购单号", "PO260921001"),
                    new Variable("inboundId", "入库单 ID", "3001"))),
            Map.entry("quality.iqc.item.approved", iqcVariables()),
            Map.entry("quality.iqc.item.rejected", iqcVariables()),
            Map.entry("quality.iqc.reinspection.created", iqcVariables()),
            Map.entry("quality.iqc.approved", iqcVariables()),
            Map.entry("quality.iqc.quarantine.created", iqcVariables()),
            Map.entry("quality.iqc.submitted", iqcVariables()));

    private EventVariableRegistry() {}

    public static List<Variable> variables(String eventCode) {
        return merge(eventCode, List.of(), null, null);
    }

    public static List<Variable> merge(String eventCode, Map<String, Object> lastPayload, String lastPayloadTime) {
        return merge(eventCode, List.of(), lastPayload, lastPayloadTime);
    }

    public static List<Variable> merge(String eventCode, List<Variable> collected,
                                       Map<String, Object> lastPayload, String lastPayloadTime) {
        Map<String, Variable> merged = new LinkedHashMap<>();
        COMMON.forEach(variable -> merged.put(variable.key(), new Variable(
                variable.key(), variable.description(), variable.example(), "common", null)));
        EVENT_VARIABLES.getOrDefault(eventCode, List.of()).forEach(variable -> merged.put(variable.key(), new Variable(
                variable.key(), variable.description(), variable.example(), "manual", null)));
        if (collected != null) {
            collected.forEach(variable -> {
                if (variable != null && variable.key() != null && !merged.containsKey(variable.key())) {
                    merged.put(variable.key(), new Variable(variable.key(), variable.description(), variable.example(),
                            variable.source() == null ? "collected" : variable.source(), variable.lastSeenAt()));
                }
            });
        }
        if (lastPayload != null) {
            lastPayload.forEach((key, value) -> {
                if (!merged.containsKey(key)) {
                    merged.put(key, new Variable(key,
                            descriptionOf(key) == null ? "（自动采集，暂无中文描述）" : descriptionOf(key),
                            value == null || value instanceof String ? (String) value : String.valueOf(value),
                            "collected", lastPayloadTime));
                }
            });
        }
        return merged.values().stream()
                .sorted(Comparator.comparingInt((Variable variable) -> sourceOrder(variable.source()))
                        .thenComparing(Variable::key))
                .toList();
    }

    public static String descriptionOf(String key) {
        String description = MANUAL_DESCRIPTIONS.get(key);
        if (description != null) {
            return description;
        }
        return EVENT_VARIABLES.values().stream()
                .flatMap(List::stream)
                .filter(variable -> variable.key().equals(key))
                .map(Variable::description)
                .findFirst()
                .orElse(null);
    }

    private static int sourceOrder(String source) {
        return switch (source) {
            case "common" -> 0;
            case "manual" -> 1;
            case "collected" -> 2;
            default -> 3;
        };
    }

    private static List<Variable> iqcVariables() {
        return List.of(
                new Variable("inboundId", "入库单 ID", "3001"),
                new Variable("inboundNo", "入库单号", "IN260921001"),
                new Variable("sourceNo", "来源采购/生产单号", "PO260921001"),
                new Variable("sourceDesc", "来源说明", "采购单 PO260921001，供应商 示例供应商"),
                new Variable("supplierName", "供应商名称", "示例供应商"),
                new Variable("itemId", "入库明细 ID", "4001"),
                new Variable("materialCode", "物料编码", "RM0001"),
                new Variable("materialName", "物料名称", "示例物料"),
                new Variable("quarantineCount", "隔离项数", "2"));
    }
}
