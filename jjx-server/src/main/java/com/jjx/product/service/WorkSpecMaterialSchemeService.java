package com.jjx.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.domain.vo.EngineeringBomVO;
import com.jjx.product.enums.ManufacturingOutputType;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/** 作业规范的只读制造方案来源。这里只选来源，展示转换由前端统一完成，不受 buy/make 限制。 */
@Service
@RequiredArgsConstructor
public class WorkSpecMaterialSchemeService {
    private final EngineeringBomMapper mapper;
    private final IEngineeringBomService bomService;
    private final ObjectMapper json;

    public ObjectNode resolve(EngineeringBomVO root) {
        ObjectNode result = json.createObjectNode();
        ObjectNode byItem = result.putObject("byItem");
        ObjectNode byMaterial = result.putObject("byMaterial");
        if (root == null || root.getItems() == null) return result;
        List<JsonNode> rows = new ArrayList<>();
        collect(json.valueToTree(root.getItems()), rows);
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode row : rows) if (row.hasNonNull("materialId")) ids.add(row.path("materialId").asLong());
        if (ids.isEmpty()) return result;
        List<EngineeringBom> candidates = mapper.selectList(new LambdaQueryWrapper<EngineeringBom>()
                .eq(EngineeringBom::getOutputType, ManufacturingOutputType.MATERIAL.name())
                .in(EngineeringBom::getOutputId, ids).orderByAsc(EngineeringBom::getBomId));
        Map<Long, Long> primary = new HashMap<>();
        if (root.getSchemeChoices() != null) root.getSchemeChoices().forEach(choice -> {
            if (Boolean.TRUE.equals(choice.getIsPrimary())) primary.put(choice.getMaterialId(), choice.getBomId());
        });
        Map<Long, ObjectNode> details = new HashMap<>();
        for (JsonNode row : rows) {
            if (!row.hasNonNull("materialId")) continue;
            long materialId = row.path("materialId").asLong();
            String name = row.path("materialName").asText("材料");
            Long requested = row.hasNonNull("subBomId") ? Long.valueOf(row.path("subBomId").asLong()) : primary.get(materialId);
            EngineeringBom selected = null;
            String warning = null;
            if (requested != null) {
                final Long chosen = requested;
                selected = candidates.stream().filter(b -> Objects.equals(b.getBomId(), chosen)).findFirst().orElse(null);
                if (selected == null || !Objects.equals(selected.getOutputId(), materialId))
                    warning = name + "：所选生产BOM不存在或产出不匹配，暂按原模式显示，请检查生产BOM";
            } else {
                List<EngineeringBom> current = candidates.stream().filter(b -> Objects.equals(b.getOutputId(), materialId)
                        && Boolean.TRUE.equals(b.getIsCurrent())
                        && Objects.equals(b.getApproveStatus(), ProductEnums.BomStatus.APPROVED.getValue())).toList();
                if (current.size() == 1) selected = current.get(0);
                else if (current.size() > 1) warning = name + "：存在多个当前生产BOM，暂按原模式显示，请确认采用的方案";
                else if (candidates.stream().anyMatch(b -> Objects.equals(b.getOutputId(), materialId)))
                    warning = name + "：暂无已批准的当前生产BOM，暂按原模式显示";
            }
            ObjectNode entry = json.createObjectNode();
            if (selected != null && !Objects.equals(selected.getApproveStatus(), ProductEnums.BomStatus.APPROVED.getValue()))
                warning = name + "：生产BOM尚未批准，暂按原模式显示";
            if (warning != null) entry.put("warning", warning);
            else if (selected != null) {
                long bomId = selected.getBomId();
                entry = details.get(bomId);
                if (entry == null) {
                    entry = json.createObjectNode();
                    entry.put("bomId", bomId);
                    entry.put("bomCode", selected.getBomCode());
                    entry.put("bomVersion", selected.getBomVersion());
                    var inputs = bomService.getBomItems(bomId);
                    if (inputs == null || inputs.isEmpty()) entry.put("warning", name + "：生产BOM没有投入材料，暂按原模式显示");
                    else entry.set("items", json.valueToTree(inputs));
                    details.put(bomId, entry);
                }
            }
            if (row.hasNonNull("itemId")) byItem.set(row.path("itemId").asText(), entry.deepCopy());
            String key = String.valueOf(materialId);
            if (byMaterial.has(key) && !byMaterial.get(key).equals(entry)) {
                byMaterial.set(key, json.createObjectNode().put("warning", name + "：同一材料采用不同生产BOM，请从 BOM 明确来源行后查看"));
            } else byMaterial.set(key, entry.deepCopy());
        }
        return result;
    }

    private void collect(JsonNode nodes, List<JsonNode> rows) {
        for (JsonNode node : nodes) {
            rows.add(node);
            collect(node.path("children"), rows);
        }
    }
}
