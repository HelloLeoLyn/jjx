package com.jjx.engineering.printing.artwork;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.utils.Sha256Utils;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.jjx.engineering.printing.artwork.ArtworkCommands.*;

@Service
@RequiredArgsConstructor
public class FilmRequestService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RedisSequenceService numbers;
    private final ArtworkService artworks;

    static boolean schemaReady(JdbcTemplate jdbc) {
        return Integer.valueOf(2).equals(jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
            AND ((TABLE_NAME='print_film_request' AND COLUMN_NAME='request_id')
              OR (TABLE_NAME='print_film_copy' AND COLUMN_NAME='film_request_id'))
            """,Integer.class));
    }
    private void requireSchema() {
        if (!schemaReady(jdbc)) throw new BusinessException(409,"出片申请结构尚未部署，请先完成迁移399");
    }
    private Map<String,Object> one(String sql,Object... args) {
        var rows=jdbc.queryForList(sql,args);
        if (rows.isEmpty()) throw new BusinessException(404,"出片申请或所属版本不存在");
        return rows.getFirst();
    }
    private int received(long requestId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM print_film_copy WHERE film_request_id=?",Integer.class,requestId);
    }

    @Transactional(readOnly=true)
    public List<FilmRequestView> list(long artworkId) {
        requireSchema();
        return jdbc.query("""
            SELECT r.*, (SELECT COUNT(*) FROM print_film_copy f WHERE f.film_request_id=r.request_id) AS received_quantity
            FROM print_film_request r JOIN print_artwork_version v ON v.version_id=r.version_id
            WHERE v.artwork_id=? ORDER BY r.request_id DESC
            """,(rs,index)->{
                int total=rs.getInt("requested_quantity"),returned=rs.getInt("received_quantity");
                return new FilmRequestView(rs.getLong("request_id"),rs.getString("request_no"),rs.getLong("version_id"),
                    total,returned,total-returned,FilmRequestView.Progress.from(total,returned).name(),rs.getString("material"),
                    rs.getBigDecimal("thickness_mm"),rs.getString("size"),rs.getObject("due_date",java.time.LocalDate.class),
                    rs.getString("remark"),rs.getInt("lock_version"),rs.getString("create_by"),
                    rs.getTimestamp("create_time").toLocalDateTime());
            },artworkId);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public Map<String,Object> execute(boolean receipt,long target,FilmRequestCommands command) {
        command.validate();requireSchema();
        String user=SecurityUtils.getUsername();
        if (user==null || user.isBlank()) throw new BusinessException(403,"无法确认当前操作人");
        String action=receipt ? "receiveCopies" : "applyFilm";
        try {
            String hash=Sha256Utils.ofText(json.writeValueAsString(List.of(action,target,user,command)));
            jdbc.update("""
                INSERT INTO engineering_resource_maintenance(resource_type,resource_id,action_type,operator,request_id,request_hash)
                VALUES('FILM_REQUEST',?,?,?,?,?) ON DUPLICATE KEY UPDATE maintenance_id=maintenance_id
                """,target,action,user,command.requestId(),hash);
            var event=one("SELECT request_hash,result_snapshot FROM engineering_resource_maintenance WHERE request_id=? FOR UPDATE",command.requestId());
            if (!hash.equals(event.get("request_hash"))) throw new BusinessException(409,"请求号已用于不同的出片操作");
            if (event.get("result_snapshot")!=null) return json.readValue(event.get("result_snapshot").toString(),new TypeReference<>() {});
            var result=receipt ? receive(target,command,user) : apply(target,command,user);
            jdbc.update("""
                UPDATE engineering_resource_maintenance SET resource_id=?,after_status=?,description=?,result_snapshot=? WHERE request_id=?
                """,result.get("requestId"),result.get("progress"),command.remark(),json.writeValueAsString(result),command.requestId());
            return result;
        } catch (DuplicateKeyException error) { throw new BusinessException(409,"菲林片编号或申请编号已存在，请检查重复编号"); }
        catch (JsonProcessingException error) { throw new IllegalStateException("出片履历格式异常",error); }
    }
    private Map<String,Object> apply(long versionId,FilmRequestCommands command,String user) {
        command.validateApplication();
        artworks.requireUsableVersion(versionId);
        var version=one("SELECT lock_version FROM print_artwork_version WHERE version_id=? FOR UPDATE",versionId);
        if (((Number)version.get("lock_version")).intValue()!=command.expectedRevision()) throw new BusinessException(409,"菲林版本已变化，请重新加载后申请");
        int quantity=command.quantity().intValueExact();
        String no=numbers.generateBusinessNumberByType("PRINT_FILM_REQUEST","CP-","yyMM'-'",3);
        jdbc.update("""
            INSERT INTO print_film_request(request_no,version_id,requested_quantity,material,thickness_mm,size,due_date,remark,create_by,create_time)
            VALUES(?,?,?,?,?,?,?,?,?,NOW(3))
            """,no,versionId,quantity,command.material().trim(),command.thicknessMm(),command.size().trim(),command.dueDate(),command.remark(),user);
        long requestId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        return Map.of("requestId",requestId,"requestNo",no,"versionId",versionId,"requestedQuantity",quantity,
                "receivedQuantity",0,"remainingQuantity",quantity,"progress",FilmRequestView.Progress.PENDING.name(),"revision",0);
    }
    private Map<String,Object> receive(long requestId,FilmRequestCommands command,String user) throws JsonProcessingException {
        command.validateReceipt();
        long versionId=((Number)one("SELECT version_id FROM print_film_request WHERE request_id=?",requestId).get("version_id")).longValue();
        artworks.requireUsableVersion(versionId); // 与停用保持版本→申请→实物的锁顺序。
        var request=one("SELECT * FROM print_film_request WHERE request_id=? FOR UPDATE",requestId);
        int revision=((Number)request.get("lock_version")).intValue();
        if (command.expectedRevision()!=revision) throw new BusinessException(409,"出片申请已有新的回片记录，请重新加载");
        int total=((Number)request.get("requested_quantity")).intValue(), before=received(requestId);
        check(command.copies().size()<=total-before,"本次回片张数不能超过未回张数");
        Set<String> names=new HashSet<>();
        for (var copy:command.copies()) check(names.add(copy.filmNo().trim().toUpperCase(Locale.ROOT)),"本次回片的菲林片编号不能重复");
        List<Long> ids=new ArrayList<>();
        for (var copy:command.copies()) {
            jdbc.update("""
                INSERT INTO print_film_copy(film_no,version_id,film_request_id,location,status,material,thickness_mm,size,output_time,create_by,create_time)
                VALUES(?,?,?,?,'AVAILABLE',?,?,?,NOW(3),?,NOW(3))
                """,copy.filmNo().trim(),versionId,requestId,copy.location().trim(),request.get("material"),request.get("thickness_mm"),request.get("size"),user);
            long filmId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);ids.add(filmId);
            jdbc.update("""
                INSERT INTO engineering_resource_maintenance(resource_type,resource_id,action_type,after_status,operator,result_snapshot)
                VALUES('FILM_COPY',?,'receiveCopy','AVAILABLE',?,?)
                """,filmId,user,json.writeValueAsString(Map.of("filmRequestId",requestId,"filmNo",copy.filmNo().trim(),"location",copy.location().trim())));
        }
        jdbc.update("UPDATE print_film_request SET lock_version=lock_version+1 WHERE request_id=?",requestId);
        int returned=before+ids.size();
        return Map.of("requestId",requestId,"versionId",versionId,"requestedQuantity",total,"receivedQuantity",returned,
                "remainingQuantity",total-returned,"progress",FilmRequestView.Progress.from(total,returned).name(),"revision",revision+1,"filmIds",ids);
    }
}
