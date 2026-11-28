package com.jjx.engineering.printing.artwork;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 版本内样式与制版来源的唯一规则入口。JSON是聚合子项事实，不复制进配置或第二张主表。 */
@Service
@RequiredArgsConstructor
public class PrintStyleService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JsonNode read(String raw) {
        try { return raw == null || raw.isBlank() ? json.createArrayNode() : json.readTree(raw); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException(422,"样式或制版来源格式无效"); }
    }

    public static String series(Collection<String> codes) {
        Set<String> prefixes=new TreeSet<>();
        for (String code:codes) {
            var m=java.util.regex.Pattern.compile("^([A-Za-z]+[0-9]+)").matcher(code);
            if (!m.find()) return "GY";
            prefixes.add(m.group(1).toUpperCase(Locale.ROOT));
        }
        return prefixes.size()==1 ? prefixes.iterator().next() : "GY";
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public String artworkSeries(long artworkId) {
        String current=jdbc.queryForObject("SELECT number_series FROM print_artwork WHERE artwork_id=? FOR UPDATE",String.class,artworkId);
        if (current!=null) return current;
        String prefix=series(jdbc.queryForList("""
            SELECT p.product_code FROM engineering_resource_product_rel r JOIN product p ON p.product_id=r.product_id
            WHERE r.resource_type='ARTWORK' AND r.resource_id=? AND r.is_active=1 ORDER BY p.product_id
            """,String.class,artworkId));
        jdbc.update("UPDATE print_artwork SET number_series=? WHERE artwork_id=?",prefix,artworkId);
        return prefix;
    }

    /** 持久序列与业务写同事务；同key行锁串行化，业务回滚时序列也回滚。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public String next(String kind,String prefix) {
        if (prefix==null || prefix.isBlank() || prefix.length()>64 || prefix.codePoints().anyMatch(Character::isISOControl)) throw new BusinessException(422,"编码系列无效");
        String key="PRINT_"+kind+":"+prefix;
        jdbc.update("""
            INSERT INTO sys_number_sequence(sequence_key,period_key,current_value,create_time,update_time)
            VALUES(?,'',1,NOW(),NOW()) ON DUPLICATE KEY UPDATE current_value=current_value+1,update_time=NOW()
            """,key);
        Long value=jdbc.queryForObject("SELECT current_value FROM sys_number_sequence WHERE sequence_key=? AND period_key='' FOR UPDATE",Long.class,key);
        return prefix+"-"+kind+"-"+String.format(Locale.ROOT,"%03d",value);
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public ArrayNode saveStyles(long artworkId,String raw,String previous) {
        JsonNode input=read(raw);
        check(input.isArray() && input.size()<=100,"每个菲林版本最多100个样式");
        Set<String> old=new HashSet<>(),used=new HashSet<>();
        for (var style:read(previous)) old.add(style.path("styleNo").asText());
        ArrayNode result=json.createArrayNode();
        String prefix=artworkSeries(artworkId);
        for (var style:input) {
            check(style.isObject(),"样式必须为对象");
            style.fieldNames().forEachRemaining(k -> check(Set.of("styleNo","name","inkColor").contains(k),"未知样式字段："+k));
            String no=style.path("styleNo").asText("").trim();
            if (no.isEmpty()) no=next("YS",prefix);
            else check(old.contains(no),"样式编号由后端生成，不能挪用其它版本的样式编号");
            check(used.add(no),"样式重复");
            check(style.path("name").isTextual() && (!style.has("inkColor") || style.path("inkColor").isTextual()),"样式名称和油墨色号必须为文本");
            String name=style.path("name").asText("").trim(),ink=style.path("inkColor").asText("").trim();
            check(!name.isEmpty() && name.length()<=100 && ink.length()<=100,"样式名称必填，名称和油墨色号最多100字");
            result.addObject().put("styleNo",no).put("name",name).put("inkColor",ink);
        }
        return result;
    }

    /** 先按版本排序加锁；来源中的实体片必须精确属于该版本，样式只能取自该版本。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public ArrayNode captureSources(String raw) {
        var input=read(raw);
        check(input.isArray() && !input.isEmpty() && input.size()<=100,"请选择1至100张实体菲林及样式");
        TreeSet<Long> versions=new TreeSet<>();
        for (var source:input) {
            check(source.isObject() && source.path("versionId").isIntegralNumber() && source.path("versionId").asLong()>0
                && source.path("filmId").isIntegralNumber() && source.path("filmId").asLong()>0,"制版来源缺少确定版本或实体菲林");
            versions.add(source.path("versionId").asLong());
        }
        Map<Long,JsonNode> available=new HashMap<>();
        for (long id:versions) {
            var rows=jdbc.queryForList("SELECT status,styles_json FROM print_artwork_version WHERE version_id=? FOR UPDATE",id);
            check(rows.size()==1 && "RELEASED".equals(rows.getFirst().get("status")),"制版来源版本未发布或已停用");
            available.put(id,read(Objects.toString(rows.getFirst().get("styles_json"),null)));
        }
        Set<Long> films=new HashSet<>();
        ArrayNode result=json.createArrayNode();
        for (var source:input) {
            long version=source.path("versionId").asLong(),film=source.path("filmId").asLong();
            check(films.add(film),"同一实体菲林不能重复加入制版");
            var rows=jdbc.queryForList("SELECT film_no FROM print_film_copy WHERE film_id=? AND version_id=? AND status='AVAILABLE' FOR UPDATE",film,version);
            check(rows.size()==1,"实体菲林不属于指定版本或不可用");
            var nos=source.path("styleNos");
            check(nos.isArray() && !nos.isEmpty(),"每张菲林至少选择一个样式");
            ObjectNode out=result.addObject().put("versionId",version).put("filmId",film).put("filmNo",rows.getFirst().get("film_no").toString());
            var identity=jdbc.queryForMap("SELECT a.artwork_no,v.version_no,v.drawing_file_id FROM print_artwork_version v JOIN print_artwork a ON a.artwork_id=v.artwork_id WHERE v.version_id=?",version);
            out.put("artworkNo",Objects.toString(identity.get("artwork_no"),""));
            out.put("versionNo",Objects.toString(identity.get("version_no"),""));
            out.set("drawingFileId",json.valueToTree(identity.get("drawing_file_id")));
            ArrayNode styles=out.putArray("styles");
            Set<String> selected=new HashSet<>();
            for (var no:nos) {
                check(no.isTextual() && selected.add(version+":"+no.asText()),"制版样式重复或编号无效");
                JsonNode found=null;
                for (var style:available.get(version)) if (style.path("styleNo").asText().equals(no.asText())) found=style;
                check(found!=null,"所选样式不属于该实体菲林的确定版本");
                styles.add(found.deepCopy());
            }
        }
        return result;
    }

    public static void check(boolean ok,String message) { if (!ok) throw new BusinessException(422,message); }
}
