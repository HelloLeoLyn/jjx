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
        // 一致性校验（dev-20261014-031）：字典来源清单必须取自后端白名单，防两处漂移。
        for (JsonNode source : ext.path("sources"))
            if (!AttachmentSourcePolicy.knownSourceTypes().contains(source.asText()))
                throw new BusinessException("资料类型来源未纳入附件来源白名单：" + source.asText());
        for (JsonNode source : ext.path("sources")) if (sourceType.equals(source.asText())) return;
        throw new BusinessException("资料类型不适用于当前来源");
    }

    /**
     * 只读判断：该资料类型是否允许用于目标来源（dev-20261019-001）。
     * 空类型=普通附件，允许；类型不存在/停用/未配置 sources 均视为不允许。
     * 不改变 validate 现有语义。
     */
    public boolean supports(String type, String sourceType) {
        if (type == null || type.isBlank()) return true;
        JsonNode ext = readExt(type);
        if (ext == null || !ext.path("sources").isArray()) return false;
        for (JsonNode source : ext.path("sources")) if (sourceType.equals(source.asText())) return true;
        return false;
    }

    /** 只读取显示名（类型缺失/停用时回退为编码），用于转换页提示。 */
    public String labelOf(String type) {
        if (type == null || type.isBlank()) return type;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT i.label FROM sys_dict_item i JOIN sys_dict d ON d.dict_code=i.dict_code AND d.deleted=0 AND d.is_active=1 WHERE i.dict_code=? AND i.item_key=? AND i.deleted=0 AND i.is_active=1",
                    TYPE, type);
            if (rows.size() == 1 && rows.get(0).get("label") != null) {
                return String.valueOf(rows.get(0).get("label"));
            }
        } catch (Exception ignored) {
            // 只读提示用，取不到回退编码
        }
        return type;
    }

    private JsonNode readExt(String type) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT i.ext_data FROM sys_dict_item i JOIN sys_dict d ON d.dict_code=i.dict_code AND d.deleted=0 AND d.is_active=1 WHERE i.dict_code=? AND i.item_key=? AND i.deleted=0 AND i.is_active=1",
                    TYPE, type);
            if (rows.size() != 1) return null;
            return json.readTree(String.valueOf(rows.get(0).get("ext_data")));
        } catch (Exception e) {
            return null;
        }
    }

    /** Disabled labels remain visible when reading historical references. */
    public List<Map<String, Object>> list() {
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        return jdbc.queryForList("SELECT dict_code,item_key,label,is_active,ext_data,sort_order FROM sys_dict_item WHERE dict_code IN (?,?) AND deleted=0 ORDER BY dict_code,sort_order,item_id", CATEGORY, TYPE);
    }
}
