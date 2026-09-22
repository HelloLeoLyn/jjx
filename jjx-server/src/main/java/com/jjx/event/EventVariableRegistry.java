package com.jjx.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 事件模板变量目录，供配置页提示、校验和试渲染使用。 */
public final class EventVariableRegistry {

    public record Variable(String key, String description, String example) {}

    private static final List<Variable> COMMON = List.of(
            new Variable("bizNo", "业务单号", "SO260921001"),
            new Variable("bizId", "业务对象 ID", "1001"),
            new Variable("bizType", "业务类型", "order"),
            new Variable("triggerUserName", "触发账号", "zhangsan"),
            new Variable("triggerRealName", "触发人姓名", "张三"));

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
        LinkedHashMap<String, Variable> merged = new LinkedHashMap<>();
        COMMON.forEach(variable -> merged.put(variable.key(), variable));
        EVENT_VARIABLES.getOrDefault(eventCode, List.of())
                .forEach(variable -> merged.put(variable.key(), variable));
        return new ArrayList<>(merged.values());
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
