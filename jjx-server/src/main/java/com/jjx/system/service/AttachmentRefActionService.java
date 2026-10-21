package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.system.enums.AttachmentRefState;
import com.jjx.system.domain.vo.AttachmentRefActionsVO;
import com.jjx.system.domain.vo.AttachmentRefActionsVO.Action;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 只读识别：业务流水精确引用优先；日志仅补充，不用时间邻近或文件 trace 猜动作。 */
@Service
@RequiredArgsConstructor
public class AttachmentRefActionService {
    private final JdbcTemplate jdbc;
    private final AttachmentSourcePolicy sources;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public AttachmentRefActionsVO detail(Long refId) {
        StpUtil.checkLogin();
        if (refId == null || refId <= 0) throw new BusinessException("附件引用不存在");
        List<Map<String, Object>> refs = jdbc.queryForList("""
            SELECT r.attachment_id,r.source_type,r.source_id,r.ref_state,r.create_by AS ref_creator,
                   r.create_time AS ref_created_at,a.create_by AS uploaded_by,a.create_time AS uploaded_at
            FROM sys_attachment_ref r JOIN sys_attachment a ON a.id=r.attachment_id
            WHERE r.id=? AND r.deleted=? AND a.deleted=?
            """, refId, YesNoEnum.NO.getCode(), YesNoEnum.NO.getCode());
        if (refs.size() != 1) throw new BusinessException("附件引用不存在");
        Map<String, Object> ref = refs.getFirst();
        String creator = text(ref, "ref_creator");
        if (AttachmentRefState.DRAFT.getValue().equals(text(ref, "ref_state"))) {
            if (!Objects.equals(creator, StpUtil.getLoginIdAsString()))
                throw new BusinessException("只能查看本人待保存附件");
            return new AttachmentRefActionsVO(refId, person(text(ref, "uploaded_by")), time(ref, "uploaded_at"),
                    null, null, List.of(), List.of());
        }
        if (!AttachmentRefState.ACTIVE.getValue().equals(text(ref, "ref_state"))) throw new BusinessException("附件引用尚未落定");
        String source = sources.canonical(text(ref, "source_type"));
        Long sourceId = number(ref, "source_id");
        sources.check(source, sourceId, false);
        LocalDateTime createdAt = time(ref, "ref_created_at");
        List<Action> business = businessActions(source, sourceId, refId, createdAt);
        List<Action> references = new ArrayList<>();
        List<Action> fallback = new ArrayList<>();
        // idx_biz 将读取范围限定为当前单据；JSON 在应用侧严格解析，兼容历史无效 oper_param。
        String logSource = "work_spec".equals(source) ? "product" : source;
        List<Map<String, Object>> logs = jdbc.queryForList("""
            SELECT id,action,create_time,real_name,username,oper_url,detail,oper_param
            FROM sys_oper_log WHERE biz_type=? AND biz_id=? AND status=? AND create_time>=?
              AND (detail IS NOT NULL OR oper_param LIKE '%attachmentRefIds%')
            ORDER BY create_time,id
            """, logSource, String.valueOf(sourceId), YesNoEnum.YES.getCode(), createdAt);
        Action joined = null;
        for (Map<String, Object> log : logs) {
            String url = text(log, "oper_url");
            boolean attachmentEvent = url != null && url.startsWith("attachment.ref.");
            boolean inDetail = containsAttachment(text(log, "detail"), refId, number(ref, "attachment_id"));
            boolean inParams = containsRef(read(text(log, "oper_param")).path("attachmentRefIds"), refId);
            if (attachmentEvent ? !inDetail : !(inParams || inDetail)) continue;
            String name = text(log, "action");
            if (name == null || name.isBlank()) continue;
            String operator = person(text(log, "real_name"));
            if (operator == null) operator = person(text(log, "username"));
            Action event = new Action("log:" + number(log, "id"), name, time(log, "create_time"), operator);
            if (attachmentEvent) {
                references.add(event);
                if (joined == null && "附件新增".equals(name)) joined = event;
            } else {
                fallback.add(event);
            }
        }
        // 支持业务流水的来源以其记录为准，避免同一动作在普通日志中再次出现。
        if (business.isEmpty()) business.addAll(fallback);
        business.sort(Comparator.comparing(Action::actionTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Action::eventKey));
        // 草稿转正沿用引用 create_time；只有精确的新增日志才能证明正式加入时间。
        return new AttachmentRefActionsVO(refId, person(text(ref, "uploaded_by")), time(ref, "uploaded_at"),
                joined == null ? null : joined.operatorName(), joined == null ? null : joined.actionTime(),
                List.copyOf(business), List.copyOf(references));
    }

    private List<Action> businessActions(String source, Long sourceId, Long refId, LocalDateTime createdAt) {
        List<Action> result = new ArrayList<>();
        if ("quotation".equals(source)) {
            collect(result, jdbc.queryForList("""
                SELECT flow_id AS event_id,action_name,operator_name,create_time,attachment_ref_ids
                FROM sales_quotation_flow WHERE quotation_id=? AND create_time>=? ORDER BY create_time,flow_id
                """, sourceId, createdAt), "quotation", refId);
        }
        if (List.of("sales_order", "sample", "purchase_order").contains(source)) {
            collect(result, jdbc.queryForList("""
                SELECT flow_id AS event_id,action_name,operator_name,create_time,attachment_ref_ids
                FROM review_flow WHERE biz_type=? AND biz_id=? AND create_time>=? ORDER BY create_time,flow_id
                """, source, sourceId, createdAt), "review", refId);
        }
        if ("sample".equals(source)) {
            collect(result, jdbc.queryForList("""
                SELECT round_id AS event_id,CONCAT('第 ',round_no,' 轮打样资料') AS action_name,
                       NULL AS operator_name,create_time,attachment_ref_ids
                FROM sales_sample_round WHERE order_id=? AND create_time>=? ORDER BY create_time,round_id
                """, sourceId, createdAt), "sample-round", refId);
        }
        return result;
    }

    private void collect(List<Action> result, List<Map<String, Object>> rows, String prefix, Long refId) {
        for (Map<String, Object> row : rows) {
            if (!containsRef(read(text(row, "attachment_ref_ids")), refId)) continue;
            String name = text(row, "action_name");
            if (name == null || name.isBlank()) continue;
            result.add(new Action(prefix + ":" + number(row, "event_id"), name,
                    time(row, "create_time"), person(text(row, "operator_name"))));
        }
    }

    private boolean containsAttachment(String value, Long refId, Long attachmentId) {
        JsonNode items = read(value).path("attachments");
        if (!items.isArray()) return false;
        for (JsonNode item : items) {
            if (sameId(item.path("refId"), refId)
                    && (!item.hasNonNull("id") || sameId(item.path("id"), attachmentId))) return true;
        }
        return false;
    }

    private boolean containsRef(JsonNode items, Long refId) {
        if (!items.isArray()) return false;
        for (JsonNode item : items) if (sameId(item, refId)) return true;
        return false;
    }

    private boolean sameId(JsonNode value, Long id) {
        return id != null && value.isIntegralNumber() && value.canConvertToLong() && value.longValue() == id;
    }

    private JsonNode read(String value) {
        if (value == null || value.isBlank()) return json.nullNode();
        try {
            JsonNode node = json.readTree(value);
            return node == null ? json.nullNode() : node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 历史日志参数可能是普通文本；解析失败不能据此推断附件关联。
            return json.nullNode();
        }
    }

    private static String person(String value) {
        return value == null || value.isBlank() || "系统".equals(value) || "system".equalsIgnoreCase(value)
                ? null : value;
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof byte[] bytes) return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return value == null ? null : value.toString();
    }

    private static Long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : ((Number) value).longValue();
    }

    private static LocalDateTime time(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return value instanceof LocalDateTime local ? local : null;
    }
}
