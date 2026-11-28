package com.jjx.engineering.printing.artwork;

import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jjx.system.utils.SecurityUtils;
import com.jjx.system.service.AttachmentRefService;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.dto.AttachmentRefBatchDTO;
import com.jjx.system.domain.dto.AttachmentRefBatchItem;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import static com.jjx.engineering.printing.artwork.ArtworkCommands.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 供路线、制版和生产写事务共用。持有版本锁直到调用方业务提交。 */
@Service
@RequiredArgsConstructor
public class ArtworkService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AttachmentRefService attachments;
    @org.springframework.beans.factory.annotation.Autowired private PrintStyleService styles;
    @org.springframework.beans.factory.annotation.Value("${file.upload.path:./upload}")
    private String uploadBasePath = "./upload";

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireUsableVersion(long versionId) {
        var statuses = jdbc.queryForList(
            "SELECT status FROM print_artwork_version WHERE version_id=? FOR UPDATE", String.class, versionId);
        if (statuses.isEmpty()) throw new BusinessException(404, "图案版本不存在");
        if (!"RELEASED".equals(statuses.get(0))) throw new BusinessException(422, "图案版本未发布或已停用");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Map<String,Object> execute(String action,long target,ArtworkCommands c) {
        c.validate();
        String user = SecurityUtils.getUsername();
        if (user == null || user.isBlank()) throw new BusinessException(403,"无法确认当前操作人");
        String type = switch(action) {
            case "createArtwork","editArtwork" -> "ARTWORK";
            case "requestFilms","sendFilm","receiveFilm","archive","destroy" -> "FILM_COPY";
            default -> "ARTWORK_VERSION";
        };
        if (action.equals("activate") && Boolean.TRUE.equals(c.disablePrevious()))
            StpUtil.checkPermission("engineering:film:disable");
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(c);
        // 新增可选字段为空时保持旧命令摘要，部署后重试旧requestId仍可重放。
        if(c.stylesJson()==null) payload.remove("stylesJson");
        if(c.quantity()==null) payload.remove("quantity");
        if(c.dueDate()==null) payload.remove("dueDate");
        if(c.drawingDocumentType()==null) payload.remove("drawingDocumentType");
        String hash = hash(List.of(type,action,target,user,payload));
        try {
            jdbc.update("""
                insert into engineering_resource_maintenance(resource_type,resource_id,action_type,operator,request_id,request_hash)
                values (?,?,?,?,?,?) on duplicate key update maintenance_id=maintenance_id
                """,type,target,action,user,c.requestId(),hash);
            var event = one("select request_hash,result_snapshot from engineering_resource_maintenance where request_id=? for update",c.requestId());
            if (!hash.equals(event.get("request_hash"))) throw new BusinessException(409,"请求号已用于其他动作");
            if (event.get("result_snapshot")!=null)
                return json.readValue(event.get("result_snapshot").toString(),new TypeReference<>() {});
            var result = switch (action) {
                case "createArtwork","editArtwork" -> artwork(target,c,user);
                case "createVersion","editVersion" -> draft(action,target,c,user);
                case "requestFilms","sendFilm","receiveFilm","archive","destroy" -> film(action,target,c,user);
                default -> transition(action,target,c,user);
            };
            String key = switch(type) {
                case "ARTWORK" -> "artworkId";
                case "FILM_COPY" -> "filmId";
                default -> "versionId";
            };
            jdbc.update("""
                update engineering_resource_maintenance set resource_id=?,after_status=?,description=?,result_snapshot=?
                where request_id=?
                """,result.get(key),result.get("status"),c.reason(),json.writeValueAsString(result),c.requestId());
            return result;
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409,"业务编号已存在");
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("动作记录格式异常",ex);
        }
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String,Object> one(String sql,Object... args) {
        var rows=jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new BusinessException(404,"指定记录不存在");
        return rows.getFirst();
    }

    private long inserted() {
        return jdbc.queryForObject("select last_insert_id()",Long.class);
    }

    private Map<String,Object> lockArtwork(long id) {
        return one("select * from print_artwork where artwork_id=? for update",id);
    }

    private Map<String,Object> lockVersion(long id) {
        return one("select * from print_artwork_version where version_id=? for update",id);
    }

    static final class RevisionConflict extends BusinessException {
        final int current;
        RevisionConflict(int actual) {
            super(409,"版本已变化，请刷新");
            current=actual;
        }
    }

    private static void revision(ArtworkCommands c,int actual) {
        if(c.expectedRevision()!=actual) throw new RevisionConflict(actual);
    }

    private Map<String,Object> artwork(long id,ArtworkCommands c,String user) {
        required(c.artworkNo(),64,"图案编号");
        required(c.name(),200,"名称");
        check(c.family()!=null && Set.of("面板","上线","下线").contains(c.family()),"图案类别应为面板、上线或下线");
        check(c.purpose()==null || c.purpose().length()<=200,"用途超长");
        check(c.setCode()==null || c.setCode().length()<=64,"编组超长");
        var products=c.productIds()==null ? List.<Long>of() : c.productIds();
        check(products.stream().noneMatch(Objects::isNull) && (!products.isEmpty() || c.sampleOrderId()!=null),"需要适用产品或来源打样单");
        int rev=0;
        if(id==0) {
            revision(c,0);
            jdbc.update("""
                insert into print_artwork(artwork_no,name,purpose,family,set_code,sample_order_id,create_by,create_time)
                values (?,?,?,?,?,?,?,now(3))
                """,c.artworkNo(),c.name(),c.purpose(),c.family(),c.setCode(),c.sampleOrderId(),user);
            id=inserted();
        } else {
            rev=(int)lockArtwork(id).get("lock_version");
            revision(c,rev++);
            jdbc.update("""
                update print_artwork set artwork_no=?,name=?,purpose=?,family=?,set_code=?,sample_order_id=?,
                lock_version=lock_version+1,update_by=?,update_time=now(3) where artwork_id=?
                """,c.artworkNo(),c.name(),c.purpose(),c.family(),c.setCode(),c.sampleOrderId(),user,id);
        }
        if(c.sampleOrderId()!=null) one("select sample_order_id from sales_sample_order where sample_order_id=? for update",c.sampleOrderId());
        jdbc.update("update engineering_resource_product_rel set is_active=0,update_by=? where resource_type='ARTWORK' and resource_id=?",user,id);
        for(long product:new TreeSet<>(products)) {
            one("select product_id from product where product_id=? for update",product);
            jdbc.update("""
                insert into engineering_resource_product_rel(resource_type,resource_id,product_id,create_by)
                values ('ARTWORK',?,?,?) on duplicate key update is_active=1,update_by=?
                """,id,product,user,user);
        }
        return Map.of("artworkId",id,"revision",rev);
    }

    private Map<String,Object> draft(String action,long id,ArtworkCommands c,String user) {
        required(c.versionNo(),32,"版本号");
        int rev=0;
        Long oldFile=null;
        long artworkId; String previousStyles=null;
        if(action.equals("createVersion")) {
            var a=lockArtwork(id);
            artworkId=id;
            revision(c,(int)a.get("lock_version"));
            jdbc.update("update print_artwork set lock_version=lock_version+1 where artwork_id=?",id);
            jdbc.update("""
                insert into print_artwork_version(artwork_id,version_no,status,drawing_file_id,technical_spec,designer_id,create_by,create_time)
                values (?,?,'DRAFT',?,?,?,?,now(3))
                """,id,c.versionNo(),c.drawingFileId(),c.technicalSpec(),user,user);
            id=inserted();
        } else {
            long parent=((Number)one("SELECT artwork_id FROM print_artwork_version WHERE version_id=?",id).get("artwork_id")).longValue();
            lockArtwork(parent);
            var v=lockVersion(id);
            artworkId=((Number)v.get("artwork_id")).longValue();
            previousStyles=Objects.toString(v.get("styles_json"),null);
            rev=(int)v.get("lock_version");
            revision(c,rev++);
            check("DRAFT".equals(v.get("status")),"仅草稿可编辑");
            oldFile=(Long)v.get("drawing_file_id");
            jdbc.update("""
                update print_artwork_version set version_no=?,drawing_file_id=?,technical_spec=?,designer_id=?,
                update_by=?,update_time=now(3),lock_version=lock_version+1 where version_id=?
                """,c.versionNo(),c.drawingFileId(),c.technicalSpec(),user,user,id);
        }
        if (c.stylesJson()!=null) jdbc.update("UPDATE print_artwork_version SET styles_json=? WHERE version_id=?",
            styles.saveStyles(artworkId,c.stylesJson(),previousStyles).toString(),id);
        if(!Objects.equals(oldFile,c.drawingFileId())) {
            if(oldFile!=null) {
                var refs=jdbc.queryForList("""
                    select id from sys_attachment_ref where source_type='print_artwork_version' and source_id=?
                    and attachment_id=? and deleted=0 and ref_state='active'
                    """,Long.class,id,oldFile);
                for(long ref:refs) attachments.unlink(ref);
            }
            if(c.drawingFileId()!=null) {
                var ref=new AttachmentRefDTO();
                ref.setAttachmentId(c.drawingFileId());
                ref.setSourceType("print_artwork_version");
                ref.setSourceId(id);
                ref.setDocumentType(c.drawingDocumentType());
                attachments.create(ref);
            }
        } else if(c.drawingFileId()!=null && c.drawingDocumentType()!=null) {
            // 同内容上传可能复用原附件ID；只改类型时仍需通过原附件服务校验、审计。
            var refs=jdbc.queryForList("""
                select id from sys_attachment_ref where source_type='print_artwork_version' and source_id=?
                and attachment_id=? and deleted=0 and ref_state='active'
                """,Long.class,id,c.drawingFileId());
            if(!refs.isEmpty()) {
                var batch=new AttachmentRefBatchDTO();
                batch.setItems(refs.stream().map(refId -> {
                    var item=new AttachmentRefBatchItem();
                    item.setRefId(refId);
                    item.setDocumentType(c.drawingDocumentType());
                    return item;
                }).toList());
                attachments.batchUpdate(batch);
            }
        }
        return Map.of("versionId",id,"status","DRAFT","revision",rev);
    }
    private Map<String,Object> transition(String action,long id,ArtworkCommands c,String user) {
        long artwork=(long)one("select artwork_id from print_artwork_version where version_id=?",id).get("artwork_id");
        var a=lockArtwork(artwork);
        var v=lockVersion(id);
        String before=(String)v.get("status");
        Long current=(Long)a.get("default_version_id");
        int rev=(int)v.get("lock_version");
        // Activation changes the default pointer shared by every version of this artwork.
        revision(c,action.equals("activate") ? (int)a.get("lock_version") : rev);
        String next=switch(action) {
            case "submit" -> move(before,"DRAFT","REVIEW");
            case "approve" -> move(before,"REVIEW","APPROVED");
            case "reject" -> move(before,"REVIEW","DRAFT");
            case "release" -> move(before,"APPROVED","RELEASED");
            case "activate" -> move(before,"RELEASED","RELEASED");
            case "disable" -> move(before,"RELEASED","DISABLED");
            default -> throw new BusinessException(422,"未知版本动作");
        };
        if(action.equals("approve") || action.equals("reject")) {
            var submitter=one("""
                select operator from engineering_resource_maintenance where resource_type='ARTWORK_VERSION'
                and resource_id=? and action_type='submit' and result_snapshot is not null
                order by maintenance_id desc limit 1 for update
                """,id).get("operator");
            if(user.equals(submitter)) throw new BusinessException(403,"提交人不能审批本人提交的版本");
        }
        if(action.equals("reject") || action.equals("disable")) required(c.reason(),500,"原因");
        if(action.equals("submit") || action.equals("release")) {
            if(v.get("styles_json")!=null) check(!styles.read(v.get("styles_json").toString()).isEmpty(),"请先定义版本样式再提交或发布");
            check(v.get("drawing_file_id")!=null && !jdbc.queryForList("""
                select id from sys_attachment_ref where source_type='print_artwork_version' and source_id=?
                and attachment_id=? and deleted=0 and ref_state='active' for update
                """,id,v.get("drawing_file_id")).isEmpty(),"需要绑定该版本的有效图纸");
        }
        if(action.equals("activate")) {
            if(Objects.equals(current,id)) throw new BusinessException(409,"版本已生效");
            if(Boolean.TRUE.equals(c.disablePrevious()) && current!=null) {
                required(c.reason(),500,"同步停旧原因");
                lockVersion(current);
                confirmImpact(current,c);
                jdbc.update("""
                    update print_artwork_version set status='DISABLED',lock_version=lock_version+1,
                    update_by=?,update_time=now(3) where version_id=?
                    """,user,current);
                jdbc.update("""
                    insert into engineering_resource_maintenance(resource_type,resource_id,action_type,before_status,
                    after_status,description,operator) values ('ARTWORK_VERSION',?,'disable','RELEASED','DISABLED',?,?)
                    """,current,c.reason(),user);
            }
            // Bump the displaced version so a stale activate command cannot reclaim the default.
            if(current!=null && !Boolean.TRUE.equals(c.disablePrevious()))
                jdbc.update("update print_artwork_version set lock_version=lock_version+1 where version_id=?",current);
            current=id;
        } else if(action.equals("disable")) {
            confirmImpact(id,c);
            if(Objects.equals(current,id)) current=null;
        }
        if(!Objects.equals(current,a.get("default_version_id")))
            jdbc.update("update print_artwork set default_version_id=?,lock_version=lock_version+1,update_by=?,update_time=now(3) where artwork_id=?",current,user,artwork);
        jdbc.update("""
            update print_artwork_version set status=?,lock_version=lock_version+1,update_by=?,update_time=now(3),
            approved_by=if(?='approve',?,approved_by),approved_time=if(?='approve',now(3),approved_time),
            released_by=if(?='release',?,released_by),released_time=if(?='release',now(3),released_time)
            where version_id=?
            """,next,user,action,user,action,action,user,action,id);
        Map<String,Object> result=new LinkedHashMap<>(Map.of("versionId",id,"revision",rev+1,"status",next));
        if(action.equals("release"))
            result.put("controlledContent",ArtworkContentSnapshot.capture(jdbc,json,uploadBasePath,v));
        result.put("defaultVersionId",current);
        result.put("artworkRevision",(int)a.get("lock_version") + (Objects.equals(current,a.get("default_version_id")) ? 0 : 1));
        return result;
    }

    private static String move(String before,String required,String next) {
        if(!required.equals(before)) throw new BusinessException(409,"当前状态 "+before+" 不允许此动作");
        return next;
    }

    private Map<String,Object> film(String action,long id,ArtworkCommands c,String user) {
        if(Set.of("requestFilms","sendFilm","receiveFilm").contains(action))
            throw new BusinessException(410,"旧出片占位流程已退役，请从出片申请办理分次回片");
        required(c.reason(),500,"处置原因");
        var f=one("select version_id,status,lock_version from print_film_copy where film_id=? for update",id);
        int rev=(int)f.get("lock_version");
        revision(c,rev);
        String before=(String)f.get("status");
        String next;
        if(action.equals("archive")) next=move(before,"AVAILABLE","ARCHIVED");
        else {
            if(!Set.of("AVAILABLE","ARCHIVED").contains(before)) throw new BusinessException(409,"仅可用或已封存的菲林片可以销毁");
            next="DESTROYED";
        }
        jdbc.update("""
            update print_film_copy set status=?,destroy_time=if(?='DESTROYED',now(3),null),
            update_by=?,update_time=now(3),lock_version=lock_version+1 where film_id=?
            """,next,next,user,id);
        return Map.of("filmId",id,"versionId",f.get("version_id"),"status",next,"revision",rev+1);
    }

    /** P11 owns the published manifest representation, including superseded specs still bound to orders. */
    public interface SpecBindings {
        List<Long> referencing(long versionId);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private Optional<SpecBindings> specBindings = Optional.empty();

    @Transactional(readOnly=true)
    public Map<String,Object> impact(long id) {
        var v=one("select lock_version from print_artwork_version where version_id=?",id);
        var bindings=specBindings.orElseThrow(() -> new BusinessException(409,"规范印刷绑定尚未接入，无法确认停用影响"));
        List<Long> specs=bindings.referencing(id).stream().distinct().sorted().toList();
        var routes=jdbc.queryForList("select distinct routing_id from engineering_routing_item where artwork_version_id=? OR JSON_CONTAINS(print_requirements,JSON_OBJECT('versionId',?),'$.styles') order by routing_id",Long.class,id,id);
        List<Long> published=List.of(), orders=List.of();
        if(!specs.isEmpty()) {
            String args=String.join(",",Collections.nCopies(specs.size(),"?"));
            published=jdbc.queryForList("select id from product_work_spec_version where deleted=0 and status='PUBLISHED' and id in ("+args+") order by id",Long.class,specs.toArray());
            var params=new ArrayList<Object>(specs);
            for(var terminal:List.of(com.jjx.production.enums.ProductionOrderStatusEnum.COMPLETED,
                    com.jjx.production.enums.ProductionOrderStatusEnum.CANCELLED,com.jjx.production.enums.ProductionOrderStatusEnum.CLOSED))
                params.add(terminal.getValue());
            orders=jdbc.queryForList("select order_id from production_order where work_spec_version_id in ("+args+") and order_status not in (?,?,?) order by order_id",Long.class,params.toArray());
        }
        var executions=jdbc.queryForList("select distinct execution_id from print_use_segment where (version_id=? OR JSON_CONTAINS(snapshot,JSON_OBJECT('versionId',?),'$.requirements.styles')) and end_time is null order by execution_id",Long.class,id,id);
        return Map.of("routingIds",routes,"specVersionIds",published,"orderIds",orders,"executionIds",executions,
            "impactToken",hash(List.of(id,v.get("lock_version"),routes,published,orders,executions)));
    }

    private void confirmImpact(long id,ArtworkCommands c) {
        required(c.impactToken(),64,"影响预览凭证");
        if(!c.impactToken().equals(impact(id).get("impactToken"))) throw new BusinessException(409,"停用影响已变化，请重新预览");
    }
}
