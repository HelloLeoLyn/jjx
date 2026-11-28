package com.jjx.engineering.printing.artwork;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.utils.Sha256Utils;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/** 发布动作与规范明细的受控内容快照；不按当前版本或实物重算历史。 */
public final class ArtworkContentSnapshot {
    static final String ALGORITHM = "film-content-v1";
    private ArtworkContentSnapshot() {}

    static ObjectNode capture(JdbcTemplate jdbc,ObjectMapper json,String uploadBasePath,Map<String,Object> version) {
        String fileHash=null;
        Object attachment=version.get("drawing_file_id");
        if (attachment!=null) {
            var files=jdbc.queryForList("SELECT sha256,file_path FROM sys_attachment WHERE id=? AND deleted=0 FOR SHARE",attachment);
            if (files.size()==1) {
                var file=files.getFirst();
                String stored=file.get("sha256")==null ? null : file.get("sha256").toString();
                if (stored!=null && stored.matches("(?i)[0-9a-f]{64}")) fileHash=stored.toLowerCase(Locale.ROOT);
                else if (file.get("file_path")!=null) fileHash=readHash(uploadBasePath,file.get("file_path").toString());
            }
        }
        String technical=version.get("technical_spec")==null ? "" : version.get("technical_spec").toString();
        // 唯一技术字段目前为规范文本；只统一Unicode/换行/首尾空白，不猜测文本中的单位换算。
        technical=Normalizer.normalize(technical.replace("\r\n","\n").replace('\r','\n').strip(),Normalizer.Form.NFC);
        ObjectNode controlled=json.createObjectNode();
        controlled.put("algorithm",ALGORITHM);
        controlled.put("drawingSha256",fileHash);
        controlled.put("technicalSpec",technical);
        if (version.get("styles_json")!=null) {
            try {
                var contentStyles=controlled.putArray("styles");
                for (var style:json.readTree(version.get("styles_json").toString()))
                    contentStyles.addObject().put("name",style.path("name").asText()).put("inkColor",style.path("inkColor").asText());
                controlled.put("algorithm","film-content-v2");
            } catch (JsonProcessingException e) { throw new IllegalStateException("版本样式格式异常",e); }
        }
        ObjectNode result=controlled.deepCopy();
        result.put("contentDigest",fileHash==null ? null : Sha256Utils.ofText(controlled.toString()));
        result.put("verification",fileHash==null ? "UNVERIFIED" : "VERIFIED");
        if (version.get("styles_json")!=null) {
            try { result.set("styles",json.readTree(version.get("styles_json").toString())); }
            catch (JsonProcessingException e) { throw new IllegalStateException("版本样式格式异常",e); }
        }
        return result;
    }

    private static String readHash(String uploadBasePath,String storedPath) {
        try {
            Path base=Path.of(uploadBasePath).toRealPath();
            Path file=base.resolve(storedPath).normalize();
            if (!file.startsWith(base) || !Files.isRegularFile(file)) return null;
            file=file.toRealPath();
            return file.startsWith(base) ? Sha256Utils.ofFile(file) : null;
        } catch (java.io.IOException | java.nio.file.InvalidPathException error) {
            return null; // 明确产生UNVERIFIED，而非伪造摘要或判定同内容。
        }
    }

    /** 读取发布时的事实；已有版本无记录时只标未核验，不能用当前文件补写旧结论。 */
    public static ObjectNode released(JdbcTemplate jdbc,ObjectMapper json,long versionId) {
        var rows=jdbc.queryForList("""
            SELECT result_snapshot FROM engineering_resource_maintenance
            WHERE resource_type='ARTWORK_VERSION' AND resource_id=? AND action_type='release'
              AND result_snapshot IS NOT NULL ORDER BY maintenance_id DESC LIMIT 1
            """,versionId);
        if (!rows.isEmpty()) {
            try {
                var event=json.readTree(String.valueOf(rows.getFirst().get("result_snapshot")));
                if (event!=null && event.path("controlledContent").isObject())
                    return ((ObjectNode)event.get("controlledContent")).deepCopy();
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("菲林发布内容快照格式异常",error);
            }
        }
        return json.createObjectNode().put("verification","UNVERIFIED");
    }
}
