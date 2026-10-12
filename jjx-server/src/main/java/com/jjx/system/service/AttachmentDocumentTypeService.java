package com.jjx.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/** Public dictionary only; no customer-document version semantics. */
@Service
@RequiredArgsConstructor
public class AttachmentDocumentTypeService {
    public static final String CATEGORY = "attachment_category";
    public static final String TYPE = "attachment_document_type";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public static boolean isAttachmentDictionary(String code) {
        return CATEGORY.equals(code) || TYPE.equals(code);
    }

    public void validate(String type, String sourceType) {
        if (type == null || type.isBlank()) return; // Ordinary attachments may remain unclassified.
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT i.ext_data FROM sys_dict_item i JOIN sys_dict d ON d.dict_code=i.dict_code AND d.deleted=0 AND d.is_active=1 WHERE i.dict_code=? AND i.item_key=? AND i.deleted=0 AND i.is_active=1", TYPE, type);
        if (rows.size() != 1) throw new BusinessException("资料类型不存在或已停用");
        JsonNode ext;
        try { ext = json.readTree(String.valueOf(rows.get(0).get("ext_data"))); }
        catch (java.io.IOException ex) { throw new BusinessException("资料类型范围配置不是有效JSON"); }
        if (ext == null || !ext.path("parent").isTextual() || !ext.path("sources").isArray())
            throw new BusinessException("资料类型必须配置parent及sources");
        Long parents = jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_item i JOIN sys_dict d ON d.dict_code=i.dict_code AND d.deleted=0 AND d.is_active=1 WHERE i.dict_code=? AND i.item_key=? AND i.deleted=0 AND i.is_active=1", Long.class, CATEGORY, ext.path("parent").asText());
        if (parents == null || parents != 1) throw new BusinessException("一级资料分类不存在或已停用");
        for (JsonNode source : ext.path("sources")) if (sourceType.equals(source.asText())) return;
        throw new BusinessException("资料类型不适用于当前来源");
    }

    /** Disabled labels remain visible when reading historical references. */
    public List<Map<String, Object>> list() {
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        return jdbc.queryForList("SELECT dict_code,item_key,label,is_active,ext_data,sort_order FROM sys_dict_item WHERE dict_code IN (?,?) AND deleted=0 ORDER BY dict_code,sort_order,item_id", CATEGORY, TYPE);
    }
}
