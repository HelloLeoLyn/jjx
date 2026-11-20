package com.jjx.trace.bom;
import cn.hutool.json.*;
import com.jjx.common.core.page.PageResult;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.trace.domain.vo.UnifiedTraceEventVO;
import com.jjx.trace.service.impl.TraceEventAggregator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
/** 版本族按产出类型+产出ID隔离；所有历史补全仅投影，不改原日志。 */
@Service
@RequiredArgsConstructor
public class BomTraceReader {
    public static final String VERSION_SCOPE="bom_version";
    public static final String ARCHIVE="BOM_VERSION_ARCHIVE",LEGACY="BOM_LEGACY_REQUEST",OPERATION="OPER_LOG";
    private final BomTraceMapper mapper;
    public record Context(String bomCode,List<BomTraceVersion> versions) { }
    public Context context(Long id) {
        var anchor=mapper.anchor(id);
        if(anchor==null) {var historical=mapper.deletedAnchor(String.valueOf(id));anchor=historical==null?null:snapshot(historical);}
        if(anchor==null || anchor.getOutputId()==null)return new Context(null,List.of());
        var versions=new LinkedHashMap<Long,BomTraceVersion>();
        mapper.versions(anchor.getOutputType(),anchor.getOutputId()).forEach(v->versions.put(v.getBomId(),v));
        for(var log:mapper.snapshots(anchor.getOutputType(),anchor.getOutputId())) {
            var v=snapshot(log);if(v!=null && v.getBomId()!=null)versions.putIfAbsent(v.getBomId(),v);
        }
        return new Context(anchor.getBomCode(),new ArrayList<>(versions.values()));
    }
    public PageResult<UnifiedTraceEventVO> events(Long id,String scope,int pageNum,int pageSize,Function<SysOperLog,UnifiedTraceEventVO> convert) {
        var context=context(id);var versions=new LinkedHashMap<String,BomTraceVersion>();
        context.versions().forEach(v->versions.put(v.getBomId().toString(),v));
        if(versions.isEmpty())return PageResult.build(List.of(),0);
        var logs=mapper.logs(new ArrayList<>(versions.keySet()));
        var covered=new HashSet<String>();
        for(var log:logs)if(log.getBizId()!=null && log.getOperGroupId()!=null && object(log)!=null)
            covered.add(log.getOperGroupId()+"|"+log.getAction()+"|"+log.getBizId());
        List<UnifiedTraceEventVO> events=new ArrayList<>();
        var creation=new HashSet<String>();
        for(var log:logs) {
            var event=convert.apply(log);event.setEvidenceSource(OPERATION);
            var related=new LinkedHashSet<String>();
            if(versions.containsKey(log.getBizId()))related.add(log.getBizId());
            var snapshot=object(log);
            if(snapshot!=null) {String from=snapshot.getStr("fromBomId");if(versions.containsKey(from))related.add(from);}
            if(log.getBizId()==null || log.getBizId().isBlank()) {
                related.addAll(legacyRoots(log,versions.keySet()));
                related.removeIf(root->log.getOperGroupId()!=null && covered.contains(log.getOperGroupId()+"|"+log.getAction()+"|"+root));
                if(related.isEmpty())continue;
                event.setEvidenceSource(LEGACY);event.setAction(Objects.toString(log.getAction(),"整套操作")+"（历史请求记录）");
                if(related.size()==1)event.setBizId(related.iterator().next());
                event.setDetail(JSONUtil.toJsonStr(Map.of("changes",List.of("由原请求中的BOM标识关联；原日志未保存逐项业务结果，请结合审核记录核对。"))));
            }
            if(com.jjx.common.constant.LogActions.BOM_SET_DEFAULT.equals(log.getAction()) && log.getDetail()!=null) {
                var match=java.util.regex.Pattern.compile("\\(#(\\d+)\\)").matcher(log.getDetail());
                while(match.find())if(versions.containsKey(match.group(1)))related.add(match.group(1));
            }
            if(related.isEmpty())continue;
            event.setRelatedBomIds(new ArrayList<>(related));
            event.setBomVersion(String.join("、",related.stream().map(key->versions.get(key).getVersion()).filter(Objects::nonNull).distinct().toList()));
            event.setBomCode(context.bomCode());events.add(event);
            if(BomTraceRecorder.COPY.equals(log.getAction()) || "新增BOM".equals(log.getAction()))creation.add(log.getBizId());
        }
        for(var v:context.versions()) {
            String key=v.getBomId().toString();
            if(v.isDeleted() || v.getCreateTime()==null || creation.contains(key))continue;
            var event=new UnifiedTraceEventVO();event.setEventId("bom-archive-"+key);event.setTime(v.getCreateTime());
            event.setAction(v.getParentBomId()!=null?"复制版本（历史档案补全）":"版本建立（历史档案补全）");
            event.setBizType("bom");event.setBizId(key);event.setBomCode(v.getBomCode());event.setBomVersion(v.getVersion());
            event.setEvidenceSource(ARCHIVE);event.setModule("BOM版本档案");event.setOperatorName(v.getCreatorName());
            var related=new ArrayList<String>();related.add(key);var lines=new ArrayList<String>();lines.add("版本档案："+v.getVersion());
            var parent=versions.get(String.valueOf(v.getParentBomId()));
            if(parent!=null){related.add(parent.getBomId().toString());lines.add("来源版本："+parent.getVersion()+" → "+v.getVersion());}
            lines.add("依据：BOM版本档案的创建时间与来源关系；此项为只读补全，原操作日志未留存。历史审核/修改内容不作推断。");
            event.setRelatedBomIds(related);event.setDetail(JSONUtil.toJsonStr(Map.of("changes",lines)));events.add(event);
        }
        if(VERSION_SCOPE.equals(scope))events.removeIf(e->e.getRelatedBomIds()==null || !e.getRelatedBomIds().contains(id.toString()));
        var byVersion=new LinkedHashMap<String,List<UnifiedTraceEventVO>>();
        for(var event:events)byVersion.computeIfAbsent(Objects.toString(event.getBizId(),event.getEventId()),key->new ArrayList<>()).add(event);
        events=new ArrayList<>();
        for(var group:byVersion.values())events.addAll(TraceEventAggregator.mergeAttachmentEvents(group));
        events.sort(Comparator.comparing(UnifiedTraceEventVO::getTime,Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingLong(e->e.getEventId().startsWith("oper-")?Long.parseLong(e.getEventId().substring(5)):Long.MAX_VALUE));
        int size=Math.min(100,Math.max(1,pageSize)),num=Math.max(1,pageNum),from=(int)Math.min((long)(num-1)*size,events.size());
        var page=new PageResult<UnifiedTraceEventVO>();page.setRecords(new ArrayList<>(events.subList(from,Math.min(from+size,events.size()))));
        page.setTotal(events.size());page.setPageNum(num);page.setPageSize(size);page.setTotalPages((events.size()+size-1)/size);return page;
    }
    private static JSONObject object(SysOperLog log) {
        if(log.getDetail()==null || log.getDetail().isBlank())return null;
        return JSONUtil.parseObj(log.getDetail()).getJSONObject("bomObject");
    }
    private static BomTraceVersion snapshot(SysOperLog log) {
        var value=object(log);if(value==null)return null;var v=new BomTraceVersion();
        v.setBomId(value.getLong("bomId"));v.setVersion(value.getStr("version"));v.setBomCode(value.getStr("bomCode"));
        v.setOutputType(value.getStr("outputType"));v.setOutputId(value.getLong("outputId"));v.setDeleted(true);return v;
    }
    private static Set<String> legacyRoots(SysOperLog log,Set<String> allowed) {
        if(log.getOperParam()==null || !JSONUtil.isTypeJSONObject(log.getOperParam()))return Set.of();
        var request=JSONUtil.parseObj(log.getOperParam()).getJSONObject("request");if(request==null)return Set.of();
        var products=request.getJSONArray("products");if(products==null)return Set.of();var result=new LinkedHashSet<String>();
        for(Object value:products) {
            var item=JSONUtil.parseObj(value);String root=item.getStr("rootBomId");
            if(allowed.contains(root))result.add(root);
        }
        return result;
    }
}
