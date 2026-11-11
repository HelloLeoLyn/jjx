package com.jjx.production.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.vo.ManufacturingSnapshotNodeVO;
import com.jjx.production.domain.vo.ManufacturingSnapshotVO;
import com.jjx.production.service.ManufacturingHistorySnapshotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工单制造历史快照（dev-20261111-009 G1）。
 *
 * <p>只读：按工单上持久化的 bom_id / routing_id / work_spec_version_id 组装，
 * 不用“最新版本”回退；任一来源缺失返回 SNAPSHOT_PARTIAL 并写 warnings。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManufacturingHistorySnapshotServiceImpl implements ManufacturingHistorySnapshotService {

    private static final int MAX_DEPTH = 32;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public ManufacturingSnapshotVO snapshot(Long orderId, Integer depth) {
        if (orderId == null) {
            throw new BusinessException("工单ID不能为空");
        }
        int effectiveDepth = depth == null ? MAX_DEPTH : depth;
        if (effectiveDepth < 1 || effectiveDepth > MAX_DEPTH) {
            throw new BusinessException("BOM_DEPTH_EXCEEDED：depth 必须在 1~" + MAX_DEPTH + " 之间");
        }

        Map<String, Object> order = queryOne("SELECT * FROM production_order WHERE order_id = ?", orderId);
        if (order == null) {
            throw new BusinessException("生产工单不存在：" + orderId);
        }

        ManufacturingSnapshotVO vo = new ManufacturingSnapshotVO();
        vo.setOrderId(orderId);
        vo.setOrderNo(text(order.get("order_no")));
        vo.setOutputType(text(order.get("output_type")));
        vo.setOutputId(number(order.get("output_id")));
        vo.setProductCode(text(order.get("product_code")));
        vo.setProductName(text(order.get("product_name")));
        vo.setPlannedQuantity(decimal(order.get("planned_quantity")));
        vo.setBomId(number(order.get("bom_id")));
        vo.setBomCode(text(order.get("bom_code")));
        vo.setRoutingId(number(order.get("routing_id")));
        vo.setWorkSpecVersionId(number(order.get("work_spec_version_id")));
        vo.setDepth(effectiveDepth);

        loadBom(vo, vo.getBomId(), effectiveDepth);
        loadRouting(vo);
        loadExecutions(vo);
        loadWorkSpec(vo);

        vo.setSnapshotStatus(vo.getWarnings().isEmpty() ? "COMPLETE" : "SNAPSHOT_PARTIAL");
        return vo;
    }

    /** 递归读取 BOM 行；sub_bom 缺失或为空时记为 SNAPSHOT_PARTIAL，不改历史。 */
    private void loadBom(ManufacturingSnapshotVO vo, Long bomId, int maxDepth) {
        if (bomId == null) {
            vo.getWarnings().add("工单未绑定制造BOM，无法展示材料耗用");
            return;
        }
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT * FROM engineering_bom_item WHERE bom_id = ? ORDER BY item_order, item_id", bomId);
        if (items.isEmpty()) {
            vo.getWarnings().add("BOM[" + bomId + "]没有明细行");
            return;
        }
        vo.getNodes().addAll(buildNodes(items, 1, maxDepth));
    }

    private List<ManufacturingSnapshotNodeVO> buildNodes(List<Map<String, Object>> items, int level, int maxDepth) {
        List<ManufacturingSnapshotNodeVO> nodes = new ArrayList<>();
        for (Map<String, Object> row : items) {
            ManufacturingSnapshotNodeVO node = new ManufacturingSnapshotNodeVO();
            node.setLevel(level);
            node.setItemOrder(integer(row.get("item_order")));
            node.setParentMaterialId(number(row.get("parent_material_id")));
            node.setMaterialId(number(row.get("material_id")));
            node.setMaterialCode(text(row.get("material_code")));
            node.setMaterialName(text(row.get("material_name")));
            node.setUnit(text(row.get("unit")));
            node.setQuantity(decimal(row.get("quantity")));
            node.setModuleQty(decimal(row.get("module_qty")));
            node.setBaseQty(decimal(row.get("base_qty")));
            node.setLossRate(decimal(row.get("loss_rate")));
            node.setSubBomId(number(row.get("sub_bom_id")));
            node.setProcessId(number(row.get("process_id")));
            node.setProcessName(text(row.get("process_name")));
            node.setSourceType(text(row.get("source_type")));
            node.setSnapshotSource("BOM_ITEM");
            Long subBomId = node.getSubBomId();
            if (subBomId != null && level < maxDepth) {
                List<Map<String, Object>> children = jdbcTemplate.queryForList(
                        "SELECT * FROM engineering_bom_item WHERE bom_id = ? ORDER BY item_order, item_id", subBomId);
                if (children.isEmpty()) {
                    node.setSnapshotSource("SUB_BOM_MISSING");
                } else {
                    node.setChildren(buildNodes(children, level + 1, maxDepth));
                }
            }
            nodes.add(node);
        }
        return nodes;
    }

    private void loadRouting(ManufacturingSnapshotVO vo) {
        Long routingId = vo.getRoutingId();
        if (routingId == null) {
            vo.getWarnings().add("工单未绑定工艺路线");
            return;
        }
        Map<String, Object> routing = queryOne("SELECT * FROM engineering_routing WHERE routing_id = ?", routingId);
        if (routing == null) {
            vo.getWarnings().add("工艺路线[" + routingId + "]不存在");
            return;
        }
        vo.setRoutingCode(text(routing.get("routing_code")));
        vo.setRoutingVersion(text(routing.get("routing_version")));
        List<Map<String, Object>> steps = jdbcTemplate.queryForList(
                "SELECT process_order, process_name, process_id, workflow_seq, work_instruction "
                        + "FROM engineering_routing_item WHERE routing_id = ? ORDER BY workflow_seq, process_order", routingId);
        for (Map<String, Object> row : steps) {
            ManufacturingSnapshotVO.RoutingStep step = new ManufacturingSnapshotVO.RoutingStep();
            step.setProcessOrder(integer(row.get("process_order")));
            step.setProcessName(text(row.get("process_name")));
            step.setProcessId(number(row.get("process_id")));
            step.setWorkflowSeq(integer(row.get("workflow_seq")));
            step.setWorkInstruction(text(row.get("work_instruction")));
            vo.getRoutingSteps().add(step);
        }
        if (steps.isEmpty()) {
            vo.getWarnings().add("工艺路线[" + routingId + "]没有工序行");
        }
    }

    private void loadExecutions(ManufacturingSnapshotVO vo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT execution_id, process_order, process_name, execution_status "
                        + "FROM production_operation_execution WHERE order_id = ? ORDER BY process_order, task_seq",
                vo.getOrderId());
        for (Map<String, Object> row : rows) {
            ManufacturingSnapshotVO.ExecutionStep step = new ManufacturingSnapshotVO.ExecutionStep();
            step.setExecutionId(number(row.get("execution_id")));
            step.setProcessOrder(integer(row.get("process_order")));
            step.setProcessName(text(row.get("process_name")));
            step.setExecutionStatus(integer(row.get("execution_status")));
            vo.getExecutionSteps().add(step);
        }
        if (!vo.getRoutingSteps().isEmpty() && vo.getExecutionSteps().size() != vo.getRoutingSteps().size()) {
            vo.getWarnings().add("工单实际工序快照与路线行数不一致（路线 " + vo.getRoutingSteps().size()
                    + " 行 / 工单 " + vo.getExecutionSteps().size() + " 行），按 SNAPSHOT_PARTIAL 展示");
        }
    }

    private void loadWorkSpec(ManufacturingSnapshotVO vo) {
        Long versionId = vo.getWorkSpecVersionId();
        if (versionId == null) {
            vo.getWarnings().add("工单未绑定作业规范版本");
            return;
        }
        Map<String, Object> version = queryOne("SELECT * FROM product_work_spec_version WHERE id = ?", versionId);
        if (version == null) {
            vo.getWarnings().add("作业规范版本[" + versionId + "]不存在");
            return;
        }
        vo.setWorkSpecVersionNo(text(version.get("version_no")));
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long number(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer integer(Object value) {
        Long num = number(value);
        return num == null ? null : num.intValue();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
