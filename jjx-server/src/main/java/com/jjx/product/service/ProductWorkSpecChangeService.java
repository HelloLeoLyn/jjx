package com.jjx.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.constant.LogActions;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.ProductWorkSpecMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 复用已批准工程版本的修改记录，保存引用时保留来源摘要，独立于日志保留周期。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductWorkSpecChangeService {
    private final ProductWorkSpecMapper mapper;
    private final ObjectMapper json;

    public ObjectNode list(Long productId, Long before) {
        if (mapper.exists(productId) == 0) throw new BusinessException("产品不存在");
        List<Map<String, Object>> rows = query(productId, before, null);
        ObjectNode result = json.createObjectNode();
        ArrayNode items = result.putArray("items");
        for (Map<String, Object> row : rows.subList(0, Math.min(50, rows.size()))) {
            ObjectNode source = source(row);
            if (source != null) items.add(source);
        }
        if (rows.size() > 50) result.put("nextBefore", ((Number) rows.get(49).get("id")).longValue());
        return result;
    }

    public void attachSources(Long productId, ObjectNode old, ObjectNode next) {
        Map<Long, JsonNode> known = new HashMap<>();
        for (JsonNode change : old.path("changes"))
            for (JsonNode source : change.path("sources")) known.put(source.path("id").asLong(), source);
        for (JsonNode value : next.path("changes")) {
            ObjectNode change = (ObjectNode) value;
            ArrayNode sources = change.putArray("sources");
            for (JsonNode sourceId : change.path("sourceLogIds")) {
                if (!sourceId.canConvertToLong() || sourceId.asLong() <= 0) throw new BusinessException("变更来源无效");
                long id = sourceId.asLong();
                JsonNode source = known.get(id);
                if (source == null) {
                    List<Map<String, Object>> rows = query(productId, null, id);
                    source = rows.isEmpty() ? null : source(rows.getFirst());
                    if (source == null) throw new BusinessException("引用的工程变更已不可用或版本尚未批准，请重新选择");
                    known.put(id, source);
                }
                sources.add(source.deepCopy());
            }
            if (!change.hasNonNull("print")) change.put("print", true);
        }
    }

    private List<Map<String, Object>> query(Long productId, Long before, Long onlyId) {
        return mapper.changeSources(productId, before, onlyId, ProductEnums.BomStatus.APPROVED.getValue(),
                ApproveStatusEnum.APPROVED.getValue(), LogActions.BOM_EDIT, LogActions.ROUTING_EDIT, YesNoEnum.YES.getCode());
    }

    private ObjectNode source(Map<String, Object> row) {
        try {
            JsonNode detail = json.readTree(String.valueOf(row.get("detail")));
            if (detail == null || !detail.path("changes").isArray()) return null;
            StringBuilder text = new StringBuilder();
            for (JsonNode line : detail.path("changes")) {
                if (!line.isTextual() || line.asText().isBlank()) continue;
                if (!text.isEmpty()) text.append('\n');
                text.append(line.asText());
            }
            if (text.isEmpty()) return null;
            ObjectNode result = json.createObjectNode();
            result.put("id", ((Number) row.get("id")).longValue());
            result.put("date", String.valueOf(row.get("changeDate")));
            result.put("label", String.valueOf(row.get("sourceLabel")));
            result.put("text", text.toString());
            return result;
        } catch (JsonProcessingException e) {
            log.warn("作业规范跳过格式异常的工程修改记录 {}", row.get("id"), e);
            return null;
        }
    }
}
