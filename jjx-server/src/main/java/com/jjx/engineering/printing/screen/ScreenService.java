package com.jjx.engineering.printing.screen;

import com.jjx.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import static com.jjx.engineering.printing.screen.ScreenCommands.*;

@Service
@RequiredArgsConstructor
public class ScreenService {

    private final ScreenRepository repo;
    private final ObjectMapper json;

    /** 工程、齐套和生产共用要求口径；张力单位固定 N/cm，不接受自由单位换算。 */
    public com.fasterxml.jackson.databind.JsonNode validateRequirements(String raw) {
        try {
            var value = raw == null || raw.isBlank() ? json.createObjectNode() : json.readTree(raw);
            if (!value.isObject()) throw new BusinessException(422, "印刷要求必须是JSON对象");
            var allowed = java.util.Set.of("position", "meshMin", "meshMax", "tensionMin", "tensionMax");
            value.fieldNames().forEachRemaining(key -> {
                if (!allowed.contains(key)) throw new BusinessException(422, "未知印刷要求：" + key);
            });
            if (value.has("position") && (!value.path("position").isTextual()
                    || value.path("position").asText().isBlank() || value.path("position").asText().length() > 64))
                throw new BusinessException(422, "印刷位置必须为1至64字文本");
            for (String prefix : java.util.List.of("mesh", "tension")) {
                for (String suffix : java.util.List.of("Min", "Max")) {
                    var bound = value.get(prefix + suffix);
                    if (bound != null && (!bound.isNumber() || bound.decimalValue().signum() <= 0
                            || (prefix.equals("mesh") && !bound.isIntegralNumber())))
                        throw new BusinessException(422, "目数必须为正整数，张力必须为正数（N/cm）");
                }
                if (value.has(prefix + "Min") && value.has(prefix + "Max")
                        && value.get(prefix + "Min").decimalValue().compareTo(value.get(prefix + "Max").decimalValue()) > 0)
                    throw new BusinessException(422, "印刷要求下限不能大于上限");
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new BusinessException(422, "印刷要求JSON格式无效");
        }
    }

    /** 齐套检查复用实际用版的工艺参数口径，可由同版本合格备用版满足。 */
    @Transactional(readOnly = true)
    public int countUsablePlate(long versionId) {
        return countUsablePlate(versionId, null);
    }

    @Transactional(readOnly = true)
    public int countUsablePlate(long versionId, String requirements) {
        var required = validateRequirements(requirements);
        return (int) repo.usablePlateCandidates(versionId).stream()
                .filter(row -> matchesRequirements(required, row.get("position"), row.get("mesh"), row.get("tension_n_cm")))
                .count();
    }

    private boolean matchesRequirements(com.fasterxml.jackson.databind.JsonNode required,
                                        Object position, Object mesh, Object tension) {
        if (required.has("position") && !required.path("position").asText().equals(position)) return false;
        for (String prefix : List.of("mesh", "tension")) {
            Object value = prefix.equals("mesh") ? mesh : tension;
            if (value == null) return false;
            var actual = new java.math.BigDecimal(value.toString());
            if (required.has(prefix + "Min") && actual.compareTo(required.get(prefix + "Min").decimalValue()) < 0) return false;
            if (required.has(prefix + "Max") && actual.compareTo(required.get(prefix + "Max").decimalValue()) > 0) return false;
        }
        return true;
    }

    /** 调用方先持有图案版本锁，再按版本→框→版顺序锁定。 */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireUsablePlate(long versionId, long plateId, String requirements) {
        var required = validateRequirements(requirements);
        var locked=lockPlateIdentity(versionId,plateId);
        var frame=locked.frame();var plate=locked.plate();
        conflict(!PlateStatus.RELEASED.name().equals(plate.get("status")), "默认网版尚未放行或已经结束");
        conflict(!FrameStatus.READY.name().equals(frame.get("status")), "默认网版的网框不可用");
        var stretch = repo.one("select mesh,tension_n_cm,qualified,end_time from print_frame_stretch where stretch_id=? and frame_id=? for update",
                plate.get("stretchId"), plate.get("frameId"));
        conflict(!Boolean.TRUE.equals(stretch.get("qualified")) && !Integer.valueOf(1).equals(stretch.get("qualified")), "默认网版的绷网未合格");
        conflict(stretch.get("end_time") != null, "默认网版的绷网已结束");
        conflict(!matchesRequirements(required, plate.get("position"), stretch.get("mesh"), stretch.get("tension_n_cm")),
                "网版位置或工艺参数不符合要求");
    }

    /** 历史引用/幂等重放只锁身份，不把历史资源当前状态当成新领用条件。 */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockPlateReference(long versionId,long plateId) { lockPlateIdentity(versionId,plateId); }

    private record PlateIdentity(Map<String,Object> frame,Map<String,Object> plate) {}
    private PlateIdentity lockPlateIdentity(long versionId,long plateId) {
        var identity = repo.plate(plateId, false);
        conflict(((Number) identity.get("versionId")).longValue() != versionId, "默认网版不属于指定图案版本");
        var frame = repo.frame(((Number) identity.get("frameId")).longValue(), true);
        var plate = repo.plate(plateId, true);
        conflict(((Number) plate.get("versionId")).longValue() != versionId, "默认网版不属于指定图案版本");
        return new PlateIdentity(frame,plate);
    }

    enum PlateStatus {
        PENDING, FAILED, RELEASED, WASHED, SCRAPPED;

        boolean occupiesFrame() {
            return this == PENDING || this == FAILED || this == RELEASED;
        }
    }

    enum FrameStatus { READY, MAINTENANCE, SCRAPPED }

    void requirePlateCreation(FrameStatus frame, boolean occupied, boolean currentQualifiedStretch,
                              boolean publishedUsableVersion, boolean availableFilmOfVersion) {
        if (frame != FrameStatus.READY || occupied) {
            throw new BusinessException(409, "制版需要空闲且可用的网框");
        }
        if (!currentQualifiedStretch) {
            throw new BusinessException(409, "请选择该框当前合格的绷网记录");
        }
        if (!publishedUsableVersion || !availableFilmOfVersion) {
            throw new BusinessException(409, "请选择已发布可用版本及其可用实体底片");
        }
    }

    PlateStatus inspect(PlateStatus status, boolean passed) {
        if (status != PlateStatus.PENDING && status != PlateStatus.FAILED) {
            throw new BusinessException(409, "仅待检或检验失败的版面可检验");
        }
        return passed ? PlateStatus.RELEASED : PlateStatus.FAILED;
    }

    PlateStatus retire(PlateStatus status, PlateStatus terminal, boolean inUse) {
        if (terminal != PlateStatus.WASHED && terminal != PlateStatus.SCRAPPED) {
            throw new IllegalArgumentException("结束版面需要洗版或报废状态");
        }
        if (!status.occupiesFrame() || inUse) {
            throw new BusinessException(409, "仅占框且未在用的版面可洗版或报废");
        }
        return terminal;
    }
    @Transactional
    public Map<String,Object> execute(boolean frame, long target, String action, ScreenCommands cmd) {
        cmd.validate();
        String operator = SecurityUtils.getUsername();
        String type = frame ? "SCREEN_FRAME" : "SCREEN_PLATE";
        try {
            var payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(cmd);
            if (cmd.remark()==null) payload.remove("remark"); // 保持部署前命令的摘要与重放能力。
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    json.writeValueAsBytes(List.of(type,target,action,operator,payload))));
            repo.db.update("""
                insert into engineering_resource_maintenance(resource_type,resource_id,action_type,operator,request_id,request_hash)
                values (?,?,?,?,?,?) on duplicate key update maintenance_id=maintenance_id
                """,type,target,action,operator,cmd.requestId(),hash);
            var event = repo.one("select request_hash,result_snapshot from engineering_resource_maintenance where request_id=? for update",cmd.requestId());
            conflict(!hash.equals(event.get("request_hash")), "requestId 已用于其他命令");
            if (event.get("result_snapshot") != null) {
                return json.readValue(event.get("result_snapshot").toString(),new TypeReference<>() {});
            }
            var change = frame ? changeFrame(target,action,cmd,operator) : changePlate(target,action,cmd,operator);
            repo.db.update("""
                update engineering_resource_maintenance set resource_id=?,before_status=?,after_status=?,description=?,
                result_snapshot=? where request_id=?
                """,change.id(),change.before(),change.result().get("status"),change.description(),
                    json.writeValueAsString(change.result()),cmd.requestId());
            return change.result();
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409,"编号重复或网框已被占用");
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("无法保存动作履历",ex);
        }
    }

    private record Change(long id,String before,String description,Map<String,Object> result) {}

    private static void conflict(boolean condition,String message) {
        if (condition) throw new BusinessException(409,message);
    }

    static final class RevisionConflict extends BusinessException {
        final int revision;

        RevisionConflict(int revision) {
            super(409, "资源已变化，请刷新");
            this.revision = revision;
        }
    }

    private static void checkRevision(ScreenCommands c, int revision) {
        if (c.expectedRevision() != revision) throw new RevisionConflict(revision);
    }

    private Change changeFrame(long id,String action,ScreenCommands c,String operator) {
        String before = null;
        String description = c.reason();
        Object beforeRemark = null;
        if (action.equals("create")) {
            checkRevision(c,0);
            required(c.frameNo(),50,"框号");
            repo.db.update("""
                insert into engineering_screen_frame(frame_no,frame_type,location,status,v2_registered,create_by)
                values (?,?,?,'READY',1,?)
                """,c.frameNo(),c.frameType(),c.location(),operator);
            id = repo.insertedId();
        } else {
            var row = repo.frame(id,true);
            before = row.get("status").toString();
            checkRevision(c,((Number)row.get("revision")).intValue());
            conflict(before.equals("SCRAPPED"),"网框已报废");
            String after = before;
            switch (action) {
                case "remark" -> {
                    beforeRemark = row.get("remark");
                    description = "修改网框备注（前后内容见动作快照）";
                    repo.db.update("update engineering_screen_frame set remark=? where frame_id=?", c.remark(), id);
                }
                case "edit" -> {
                    required(c.frameNo(),50,"框号");
                    repo.db.update("update engineering_screen_frame set frame_no=?,frame_type=?,location=? where frame_id=?",
                            c.frameNo(),c.frameType(),c.location(),id);
                }
                case "clean" -> required(c.reason(),500,"清洁记录");
                case "stretch" -> {
                    conflict(!before.equals("READY") || repo.occupied(id),"重绷需要可用空框");
                    require(c.mesh()!=null && c.mesh()>0 && c.tensionNcm()!=null && c.tensionNcm().signum()>0
                            && c.tensionNcm().scale()<=2 && c.tensionNcm().compareTo(new java.math.BigDecimal("1000000"))<0
                            && c.qualified()!=null,"请填写有效目数、张力及合格结论");
                    repo.db.update("update print_frame_stretch set end_time=now(3) where open_frame_id=?",id);
                    repo.db.update("""
                        insert into print_frame_stretch(frame_id,mesh,tension_n_cm,material,qualified,stretch_time,create_by)
                        values (?,?,?,?,?,now(3),?)
                        """,id,c.mesh(),c.tensionNcm(),c.material(),c.qualified(),operator);
                }
                case "maintain", "resume", "scrap" -> {
                    required(c.reason(),500,"原因");
                    if (action.equals("resume")) {
                        conflict(!before.equals("MAINTENANCE"),"网框不在维护中");
                        after="READY";
                    } else {
                        conflict(repo.occupied(id),"请先结束占框版面");
                        if (action.equals("scrap")) {
                            after="SCRAPPED";
                            repo.db.update("update print_frame_stretch set end_time=now(3) where open_frame_id=?",id);
                        } else {
                            conflict(!before.equals("READY"),"网框已在维护中");
                            after="MAINTENANCE";
                        }
                    }
                }
                default -> throw new BusinessException(422,"未知网框动作");
            }
            repo.db.update("update engineering_screen_frame set status=?,revision=revision+1,update_by=? where frame_id=?",after,operator,id);
        }
        var result=repo.frame(id,true);
        if (action.equals("remark")) result.put("beforeRemark", beforeRemark);
        var stretch=repo.db.queryForList("select stretch_id from print_frame_stretch where open_frame_id=?",Long.class,id);
        var active=repo.db.queryForList("select plate_id from engineering_screen_plate where occupied_frame_id=?",Long.class,id);
        result.put("stretchId",stretch.isEmpty()?null:stretch.getFirst());
        result.put("plateId",active.isEmpty()?null:active.getFirst());
        return new Change(id,before,description,result);
    }

    private Change changePlate(long id,String action,ScreenCommands c,String operator) {
        if (action.equals("create")) return createPlate(c,operator);
        var identity=repo.plate(id,false);
        long frame=((Number)identity.get("frameId")).longValue();
        repo.one("select version_id from print_artwork_version where version_id=? for update",identity.get("versionId"));
        repo.frame(frame,true);
        var plate=repo.plate(id,true);
        int revision=((Number)plate.get("revision")).intValue();
        checkRevision(c,revision);
        PlateStatus before=PlateStatus.valueOf(plate.get("status").toString());
        PlateStatus after;
        String description;
        if (action.equals("release") || action.equals("fail")) {
            required(c.inspection(),500,"检验记录");
            description=c.inspection();
            after=inspect(before,action.equals("release"));
            if (after==PlateStatus.RELEASED) {
                if (operator.equals(plate.get("createdBy"))) throw new BusinessException(403,"制版人不能放行本人制版");
                repo.db.update("update engineering_screen_plate set released_by=?,released_time=now(3) where plate_id=?",operator,id);
            }
        } else {
            require(action.equals("wash") || action.equals("scrap"),"未知网版动作");
            required(c.reason(),500,"结束原因");
            description=c.reason();
            boolean used=repo.exists("select segment_id from print_use_segment where open_plate_id=? for update",id);
            used = used || repo.exists("select process_id from sales_sample_process where actual_plate_id=? and status=? for update",
                    id,com.jjx.sales.enums.SampleProcessStatusEnum.IN_PROGRESS.getValue());
            after=retire(before,action.equals("wash")?PlateStatus.WASHED:PlateStatus.SCRAPPED,used);
            repo.db.update("update engineering_screen_plate set end_reason=?,washed_time=if(?='wash',now(),null) where plate_id=?",description,action,id);
            repo.db.update("update engineering_screen_frame set revision=revision+1 where frame_id=?",frame);
        }
        repo.db.update("update engineering_screen_plate set status=?,revision=revision+1,update_by=? where plate_id=?",after.name(),operator,id);
        return new Change(id,before.name(),description,Map.of("plateId",id,"status",after.name(),"frameId",frame,"revision",revision+1));
    }

    private Change createPlate(ScreenCommands c,String operator) {
        require(c.frameId()!=null && c.stretchId()!=null && c.versionId()!=null && c.filmId()!=null,"缺少网框、绷网、版本或底片");
        required(c.plateNo(),80,"版号");
        required(c.position(),100,"印刷位置");
        var version=repo.one("select status from print_artwork_version where version_id=? for update",c.versionId());
        boolean film=repo.exists("select film_id from print_film_copy where version_id=? and film_id=? and status='AVAILABLE' for update",c.versionId(),c.filmId());
        var frame=repo.frame(c.frameId(),true);
        checkRevision(c,((Number)frame.get("revision")).intValue());
        boolean stretch=repo.exists("select stretch_id from print_frame_stretch where open_frame_id=? and stretch_id=? and qualified=1 for update",c.frameId(),c.stretchId());
        requirePlateCreation(FrameStatus.valueOf(frame.get("status").toString()),repo.occupied(c.frameId()),stretch,"RELEASED".equals(version.get("status")),film);
        repo.db.update("""
            insert into engineering_screen_plate(frame_id,plate_no,version_id,film_copy_id,stretch_id,position,status,v2_registered,create_by)
            values (?,?,?,?,?,?,'PENDING',1,?)
            """,c.frameId(),c.plateNo(),c.versionId(),c.filmId(),c.stretchId(),c.position(),operator);
        long id=repo.insertedId();
        repo.db.update("update engineering_screen_frame set revision=revision+1 where frame_id=?",c.frameId());
        return new Change(id,null,c.reason(),Map.of("plateId",id,"status","PENDING","frameId",c.frameId(),"revision",0));
    }
}
