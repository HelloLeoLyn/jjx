package com.jjx.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.system.utils.AttachmentRefUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 动作及轮次的附件证据：只按保存的引用 ID 读取，不回退到当前单据附件列表。 */
@Service
@RequiredArgsConstructor
public class AttachmentEvidenceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public String snapshotSampleDrawings(Long orderId) {
        List<Long> ids = jdbc.queryForList("""
            SELECT r.id FROM sys_attachment_ref r JOIN sys_attachment a ON a.id=r.attachment_id
            WHERE r.source_type='sample' AND r.source_id=? AND r.deleted=0 AND a.deleted=0
              AND (r.document_type IN ('CUSTOMER_DRAWING','ENGINEERING_DRAWING','CIRCUIT_DRAWING',
                   'CONFIRM_DRAWING','DIE_DRAWING','OUTLINE_DRAWING','PANEL_DRAWING','STRUCTURE_DRAWING','PRINT_GUIDE','FILM')
                OR ((r.document_type IS NULL OR r.document_type IN ('general_document','OTHER'))
                    AND a.category='ENGINEERING_DRAWING'))
            ORDER BY r.id FOR UPDATE
            """, Long.class, orderId);
        return AttachmentRefUtils.toJson(ids);
    }

    public List<Map<String, Object>> listSnapshot(String refJson, String sourceType, Long sourceId) {
        if (refJson == null || refJson.isBlank()) return List.of();
        final List<Long> ids;
        try {
            ids = json.readValue(refJson, new TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("附件证据引用数据无效", e);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : ids) {
            result.addAll(jdbc.query("""
                SELECT r.id AS refId, COALESCE(r.display_name,a.file_name) AS fileName
                FROM sys_attachment_ref r JOIN sys_attachment a ON a.id=r.attachment_id
                WHERE r.id=? AND r.source_type=? AND r.source_id=? AND r.deleted=0 AND a.deleted=0
                """, (rs, n) -> Map.<String, Object>of("refId", rs.getLong("refId"),
                    "fileName", rs.getString("fileName") == null ? "附件" : rs.getString("fileName")),
                id, sourceType, sourceId));
        }
        return result;
    }
}
