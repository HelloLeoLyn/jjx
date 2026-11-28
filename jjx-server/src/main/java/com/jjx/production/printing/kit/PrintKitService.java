package com.jjx.production.printing.kit;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.printing.screen.ScreenService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多资源齐套与缺口闭环（dev-20261126-002）。
 * 口径：物料沿用现有缺料预检；网版按 P04 统一可用性（同版本已放行、未洗版、框未报废）。
 * 缺口按「工单/产品 + 工序行 + 资源」去重，复查仍缺才保留，满足即关闭。
 */
@Service
@RequiredArgsConstructor
public class PrintKitService {

    private final JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final ScreenService screenService;
    private final com.jjx.inventory.service.InventoryAlertService alertService;
    private final com.jjx.event.EventPublisher eventPublisher;

    /** 产品级预检：产品关联图案的生效版本是否有可用网版。不冒充工单确认。 */
    public Map<String, Object> productPrecheck(long productId) {
        var rows = jdbc.queryForList("""
            SELECT a.artwork_id AS artworkId, a.artwork_no AS artworkNo, a.name AS artworkName,
                   a.default_version_id AS versionId, v.version_no AS versionNo
            FROM engineering_resource_product_rel r
            JOIN print_artwork a ON a.artwork_id = r.resource_id
            LEFT JOIN print_artwork_version v ON v.version_id = a.default_version_id
            WHERE r.resource_type='ARTWORK' AND r.product_id=? AND r.is_active=1
            ORDER BY a.artwork_no
            """, productId);
        List<Map<String, Object>> items = new ArrayList<>();
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (var row : rows) {
            long versionId = row.get("versionId") == null ? 0 : ((Number) row.get("versionId")).longValue();
            int usable = versionId > 0 ? screenService.countUsablePlate(versionId) : 0;
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("resourceType", "SCREEN_PLATE");
            item.put("need", 1);
            item.put("available", usable);
            item.put("gap", usable > 0 ? 0 : 1);
            items.add(item);
            if (usable == 0) gaps.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("productId", productId);
        result.put("items", items);
        result.put("gaps", gaps);
        result.put("verified", !items.isEmpty());
        result.put("verificationMessage", items.isEmpty() ? "尚未关联菲林版本，网版需求未核验" : null);
        result.put("warnings", dieWarnings(productId));
        result.put("ok", !items.isEmpty() && gaps.isEmpty());
        return result;
    }

    /** 工单级齐套：按工单绑定的规范快照逐工序核对网版；物料附现有预检结果。 */
    public Map<String, Object> orderKit(long orderId) {
        var orders = jdbc.queryForList("""
            SELECT o.order_id AS orderId, o.order_no AS orderNo, o.product_id AS productId,
                   o.work_spec_version_id AS specVersionId
            FROM production_order o WHERE o.order_id=?
            """, orderId);
        if (orders.isEmpty()) throw new BusinessException(404, "工单不存在");
        var order = orders.get(0);
        Long specVersionId = order.get("specVersionId") == null ? null : ((Number) order.get("specVersionId")).longValue();

        List<Map<String, Object>> checks = new ArrayList<>();
        List<Map<String, Object>> gaps = new ArrayList<>();
        boolean verified = false;
        if (specVersionId != null) {
            var snapshots = jdbc.queryForList("""
                SELECT snapshot_json FROM product_work_spec_item
                WHERE spec_version_id=? AND resource_type='DOCSET_JSON'
                """, specVersionId);
            if (snapshots.size() == 1) {
                try {
                    var snapshot = json.readTree(String.valueOf(snapshots.getFirst().get("snapshot_json")));
                    var printing = snapshot == null ? null : snapshot.get("printingItems");
                    verified = printing != null && printing.isArray();
                    if (verified) for (var row : printing) {
                        long versionId = row.path("artworkVersionId").asLong();
                        long routingItemId = row.path("routingItemId").asLong();
                        if (versionId <= 0 || routingItemId <= 0 || !row.path("requirements").isObject()) {
                            verified = false;
                            continue;
                        }
                        int usable = screenService.countUsablePlate(versionId, row.get("requirements").toString());
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("resourceType", "SCREEN_PLATE");
                        item.put("routingItemId", routingItemId);
                        item.put("processName", row.path("processName").asText(""));
                        item.put("resourceKey", String.valueOf(versionId) + (row.get("requirements").has("styles")
                            ? ":"+com.jjx.common.utils.Sha256Utils.ofText(row.get("requirements").path("styles").toString()).substring(0,16) : ""));
                        item.put("resourceCode", row.path("artworkNo").asText(""));
                        item.put("resourceName", row.path("artworkVersionNo").asText(""));
                        item.put("need", 1);
                        item.put("available", usable);
                        item.put("gap", usable > 0 ? 0 : 1);
                        item.put("unit", "张");
                        checks.add(item);
                        if (usable == 0) gaps.add(item);
                    }
                } catch (com.fasterxml.jackson.core.JsonProcessingException | BusinessException error) {
                    verified = false;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", order.get("orderNo"));
        result.put("productId", order.get("productId"));
        result.put("checks", checks);
        result.put("gaps", gaps);
        result.put("verified", verified);
        result.put("verificationMessage", verified ? null : "规范印刷快照缺失或不完整，网版需求未核验");
        result.put("warnings", dieWarnings(((Number) order.get("productId")).longValue()));
        result.put("ok", verified && gaps.isEmpty());
        try {
            result.put("materials", alertService.previewOrderShortage(orderId));
        } catch (Exception error) {
            result.put("materials", null);
            result.put("materialsWarning", "物料预检失败，物料齐套未核验");
        }
        return result;
    }

    /** 刀模只提供产品关联资料提示，不把未关联直接认定为必需刀模缺失。 */
    private List<String> dieWarnings(long productId) {
        var dies = jdbc.queryForList("""
            SELECT d.die_no,d.status FROM engineering_resource_product_rel r
            JOIN engineering_die d ON d.die_id=r.resource_id AND d.del_flag='0'
            WHERE r.resource_type='DIE' AND r.product_id=? AND r.is_active=1 ORDER BY d.die_no
            """, productId);
        if (dies.isEmpty()) return List.of();
        return dies.stream().filter(die -> !"AVAILABLE".equals(die.get("status")))
                .map(die -> "刀模 " + die.get("die_no") + " 当前不可用，请核实；此提示不拦截网版齐套").toList();
    }

    /** 复查：重算并同步缺口台账（仍缺保留、满足关闭）。 */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> recheck(long orderId) {
        Map<String, Object> kit = orderKit(orderId);
        if (!Boolean.TRUE.equals(kit.get("verified")))
            throw new BusinessException(422, "规范印刷资料未核验，不能更新或关闭缺口台账");
        String operator = SecurityUtils.getUsername();
        List<Map<String, Object>> gaps = (List<Map<String, Object>>) kit.getOrDefault("gaps", List.of());
        List<String> keys = new ArrayList<>();
        for (var gap : gaps) {
            String resourceKey = String.valueOf(gap.get("resourceKey"));
            keys.add(gap.get("routingItemId") + ":" + resourceKey);
            jdbc.update("""
                INSERT INTO print_kit_shortage(scope, order_id, product_id, routing_item_id, process_name,
                       resource_type, resource_key, resource_code, resource_name, need_qty, available_qty, gap_qty,
                       unit, status, first_found_at, last_check_at, create_by, create_time)
                VALUES('ORDER', ?, ?, ?, ?, 'SCREEN_PLATE', ?, ?, ?, ?, ?, ?, ?, 0, NOW(3), NOW(3), ?, NOW(3))
                ON DUPLICATE KEY UPDATE need_qty=VALUES(need_qty), available_qty=VALUES(available_qty),
                       gap_qty=VALUES(gap_qty), last_check_at=NOW(3), update_by=VALUES(create_by), update_time=NOW(3)
                """, orderId, kit.get("productId"),
                gap.get("routingItemId") == null ? 0 : gap.get("routingItemId"),
                gap.get("processName"), resourceKey, gap.get("resourceCode"), gap.get("resourceName"),
                1, gap.get("available"), gap.get("gap"), gap.get("unit"), operator);
        }
        if (keys.isEmpty()) {
            jdbc.update("""
                UPDATE print_kit_shortage SET status=1, closed_reason='复查已满足', closed_by=?, closed_at=NOW(3),
                       last_check_at=NOW(3), update_time=NOW(3)
                WHERE scope='ORDER' AND order_id=? AND status=0 AND resource_type='SCREEN_PLATE'
                """, operator, orderId);
        } else {
            String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
            List<Object> args = new ArrayList<>();
            args.add(operator);
            args.add(orderId);
            args.addAll(keys);
            jdbc.update("""
                UPDATE print_kit_shortage SET status=1, closed_reason='复查已满足', closed_by=?, closed_at=NOW(3),
                       last_check_at=NOW(3), update_time=NOW(3)
                WHERE scope='ORDER' AND order_id=? AND status=0 AND resource_type='SCREEN_PLATE'
                  AND CONCAT(routing_item_id,':',resource_key) NOT IN (""" + placeholders + ")",
                    args.toArray());
        }
        // 通知：同一缺口只在首次（last_notified_at 为空）通知一次，重复复查不重复打扰
        var pending = jdbc.queryForList("""
            SELECT id FROM print_kit_shortage
            WHERE scope='ORDER' AND order_id=? AND status=0 AND last_notified_at IS NULL
            """, orderId);
        if (!pending.isEmpty()) {
            try {
                eventPublisher.fire("printing.kit.shortage", Map.of(
                        "bizNo", String.valueOf(kit.get("orderNo")),
                        "gapCount", String.valueOf(pending.size()),
                        "orderId", String.valueOf(orderId)));
                jdbc.update("""
                    UPDATE print_kit_shortage SET last_notified_at=NOW(3), update_time=NOW(3)
                    WHERE scope='ORDER' AND order_id=? AND status=0 AND last_notified_at IS NULL
                    """, orderId);
            } catch (Exception ignore) {
                // 通知失败不影响齐套计算结果
            }
        }
        return kit;
    }

    public List<Map<String, Object>> shortages(Long orderId, Integer status) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, scope, order_id AS orderId, order_no AS orderNo, product_id AS productId,
                   routing_item_id AS routingItemId, process_name AS processName, resource_type AS resourceType,
                   resource_key AS resourceKey, resource_code AS resourceCode, resource_name AS resourceName,
                   need_qty AS needQty, available_qty AS availableQty, gap_qty AS gapQty, unit, status,
                   owner, first_found_at AS firstFoundAt, last_check_at AS lastCheckAt,
                   closed_reason AS closedReason
            FROM print_kit_shortage WHERE 1=1
            """);
        List<Object> args = new ArrayList<>();
        if (orderId != null) {
            sql.append(" AND order_id=?");
            args.add(orderId);
        }
        if (status != null) {
            sql.append(" AND status=?");
            args.add(status);
        }
        sql.append(" ORDER BY status, last_check_at DESC, id DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(long id, String reason) {
        String operator = SecurityUtils.getUsername();
        int rows = jdbc.update("""
            UPDATE print_kit_shortage SET status=1, closed_reason=?, closed_by=?, closed_at=NOW(3), update_time=NOW(3)
            WHERE id=? AND status=0
            """, reason == null ? "人工关闭" : reason, operator, id);
        if (rows == 0) throw new BusinessException(404, "缺口不存在或已关闭");
    }
}
