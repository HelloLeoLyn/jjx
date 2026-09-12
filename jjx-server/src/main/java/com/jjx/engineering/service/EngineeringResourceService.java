package com.jjx.engineering.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.enums.EngineeringResourceEnums.ActionType;
import com.jjx.engineering.enums.EngineeringResourceEnums.DieStatus;
import com.jjx.engineering.enums.EngineeringResourceEnums.ResourceType;
import com.jjx.engineering.enums.EngineeringResourceEnums.ScreenFrameStatus;
import com.jjx.engineering.enums.EngineeringResourceEnums.ScreenPlateStatus;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EngineeringResourceService {
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> listFrames(String keyword, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT f.*,
              p.plate_id current_plate_id,p.plate_no current_plate_no,p.film_id,p.content plate_content,
              GROUP_CONCAT(DISTINCT CONCAT(pr.product_id, ':', prod.product_code, ' ', prod.product_name)
                ORDER BY prod.product_code SEPARATOR '||') product_refs
            FROM engineering_screen_frame f
            LEFT JOIN engineering_screen_plate p ON p.frame_id=f.frame_id AND p.status=?
            LEFT JOIN engineering_resource_product_rel pr ON pr.resource_type=? AND pr.resource_id=p.plate_id AND pr.is_active=1
            LEFT JOIN product prod ON prod.product_id=pr.product_id
            WHERE f.del_flag='0'
            """);
        List<Object> args = new ArrayList<>(List.of(ScreenPlateStatus.ACTIVE.name(), ResourceType.SCREEN_PLATE.name()));
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (f.frame_no LIKE ? OR p.plate_no LIKE ? OR p.content LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (status != null && !status.isBlank()) {
            ScreenFrameStatus.valueOf(status);
            sql.append(" AND f.status=?"); args.add(status);
        }
        sql.append(" GROUP BY f.frame_id,p.plate_id ORDER BY f.frame_no");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Long saveFrame(Map<String, Object> body) {
        String no = required(body, "frameNo");
        Long id = longValue(body.get("frameId"));
        if (id == null) {
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.update(c -> {
                PreparedStatement ps = c.prepareStatement("INSERT INTO engineering_screen_frame(frame_no,frame_type,mesh,location,status,remark,create_by) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, no); ps.setString(2, text(body.get("frameType"))); ps.setString(3, text(body.get("mesh")));
                ps.setString(4, text(body.get("location"))); ps.setString(5, ScreenFrameStatus.EMPTY.name());
                ps.setString(6, text(body.get("remark"))); ps.setString(7, username()); return ps;
            }, key);
            return key.getKey().longValue();
        }
        jdbc.update("UPDATE engineering_screen_frame SET frame_no=?,frame_type=?,mesh=?,location=?,remark=?,update_by=? WHERE frame_id=? AND del_flag='0'",
                no, text(body.get("frameType")), text(body.get("mesh")), text(body.get("location")), text(body.get("remark")), username(), id);
        return id;
    }

    @Transactional
    public Long createPlate(Map<String, Object> body) {
        Long frameId = requireLong(body, "frameId");
        String plateNo = required(body, "plateNo");
        String frameStatus = jdbc.queryForObject("SELECT status FROM engineering_screen_frame WHERE frame_id=? AND del_flag='0'", String.class, frameId);
        if (ScreenFrameStatus.SCRAPPED.name().equals(frameStatus)) throw new BusinessException("报废网框不能制版");
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM engineering_screen_plate WHERE frame_id=? AND status=?", Integer.class, frameId, ScreenPlateStatus.ACTIVE.name());
        if (active != null && active > 0) throw new BusinessException("该网框已有有效版面，请先洗版");
        Long filmId = longValue(body.get("filmId"));
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("INSERT INTO engineering_screen_plate(frame_id,plate_no,film_id,content,status,remark,create_by) VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, frameId); ps.setString(2, plateNo);
            if (filmId == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, filmId);
            ps.setString(4, text(body.get("content"))); ps.setString(5, ScreenPlateStatus.ACTIVE.name());
            ps.setString(6, text(body.get("remark"))); ps.setString(7, username()); return ps;
        }, key);
        Long plateId = key.getKey().longValue();
        syncProducts(ResourceType.SCREEN_PLATE, plateId, productIds(body), text(body.get("purpose")));
        jdbc.update("UPDATE engineering_screen_frame SET status=?,update_by=? WHERE frame_id=?", ScreenFrameStatus.PLATED.name(), username(), frameId);
        log(ResourceType.SCREEN_FRAME, frameId, ActionType.PLATE, frameStatus, ScreenFrameStatus.PLATED.name(), null, null, text(body.get("remark")));
        return plateId;
    }

    @Transactional
    public void washFrame(Long frameId, String reason) {
        Map<String, Object> frame = jdbc.queryForMap("SELECT status FROM engineering_screen_frame WHERE frame_id=? AND del_flag='0'", frameId);
        List<Long> plates = jdbc.queryForList("SELECT plate_id FROM engineering_screen_plate WHERE frame_id=? AND status=?", Long.class, frameId, ScreenPlateStatus.ACTIVE.name());
        if (plates.isEmpty()) throw new BusinessException("该网框没有可洗掉的有效版面");
        jdbc.update("UPDATE engineering_screen_plate SET status=?,washed_time=NOW(),end_reason=?,update_by=? WHERE frame_id=? AND status=?",
                ScreenPlateStatus.WASHED.name(), reason, username(), frameId, ScreenPlateStatus.ACTIVE.name());
        jdbc.update("UPDATE engineering_screen_frame SET status=?,update_by=? WHERE frame_id=?", ScreenFrameStatus.EMPTY.name(), username(), frameId);
        log(ResourceType.SCREEN_FRAME, frameId, ActionType.WASH, String.valueOf(frame.get("status")), ScreenFrameStatus.EMPTY.name(), null, null, reason);
    }

    @Transactional
    public void actOnFrame(Long frameId, Map<String, Object> body) {
        ActionType action = ActionType.valueOf(required(body, "actionType"));
        ScreenFrameStatus before = ScreenFrameStatus.valueOf(jdbc.queryForObject(
                "SELECT status FROM engineering_screen_frame WHERE frame_id=? AND del_flag='0'", String.class, frameId));
        if (before == ScreenFrameStatus.SCRAPPED) throw new BusinessException("报废网框不可再维护");
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM engineering_screen_plate WHERE frame_id=? AND status=?",
                Integer.class, frameId, ScreenPlateStatus.ACTIVE.name());
        ScreenFrameStatus after = switch (action) {
            case REPAIR -> ScreenFrameStatus.MAINTENANCE;
            case ENABLE -> active != null && active > 0 ? ScreenFrameStatus.PLATED : ScreenFrameStatus.EMPTY;
            case SCRAP -> {
                if (active != null && active > 0) throw new BusinessException("请先洗掉有效版面再报废网框");
                yield ScreenFrameStatus.SCRAPPED;
            }
            default -> throw new BusinessException("该动作不适用于网框");
        };
        jdbc.update("UPDATE engineering_screen_frame SET status=?,update_by=? WHERE frame_id=?", after.name(), username(), frameId);
        log(ResourceType.SCREEN_FRAME, frameId, action, before.name(), after.name(), null, null, text(body.get("description")));
    }

    public List<Map<String, Object>> listDies(String keyword, String status) {
        StringBuilder sql = new StringBuilder("""
            SELECT d.*,GROUP_CONCAT(DISTINCT CONCAT(pr.product_id, ':', p.product_code, ' ', p.product_name)
              ORDER BY p.product_code SEPARATOR '||') product_refs
            FROM engineering_die d
            LEFT JOIN engineering_resource_product_rel pr ON pr.resource_type=? AND pr.resource_id=d.die_id AND pr.is_active=1
            LEFT JOIN product p ON p.product_id=pr.product_id
            WHERE d.del_flag='0'
            """);
        List<Object> args = new ArrayList<>(List.of(ResourceType.DIE.name()));
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (d.die_no LIKE ? OR d.die_name LIKE ? OR d.purpose LIKE ?)");
            String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like); args.add(like);
        }
        if (status != null && !status.isBlank()) { DieStatus.valueOf(status); sql.append(" AND d.status=?"); args.add(status); }
        sql.append(" GROUP BY d.die_id ORDER BY d.die_no");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Long saveDie(Map<String, Object> body) {
        Long id = longValue(body.get("dieId"));
        String no = required(body, "dieNo"); String name = required(body, "dieName");
        if (id == null) {
            id = insertDie(no, name, text(body.get("purpose")), text(body.get("specification")), text(body.get("version")), text(body.get("location")), null, text(body.get("remark")));
        } else {
            jdbc.update("UPDATE engineering_die SET die_no=?,die_name=?,purpose=?,specification=?,version=?,location=?,remark=?,update_by=? WHERE die_id=? AND del_flag='0'",
                    no,name,text(body.get("purpose")),text(body.get("specification")),text(body.get("version")),text(body.get("location")),text(body.get("remark")),username(),id);
        }
        syncProducts(ResourceType.DIE, id, productIds(body), text(body.get("purpose")));
        return id;
    }

    @Transactional
    public Long actOnDie(Long dieId, Map<String, Object> body) {
        ActionType action = ActionType.valueOf(required(body, "actionType"));
        Map<String, Object> die = jdbc.queryForMap("SELECT * FROM engineering_die WHERE die_id=? AND del_flag='0'", dieId);
        DieStatus before = DieStatus.valueOf(String.valueOf(die.get("status")));
        if (before == DieStatus.SCRAPPED) throw new BusinessException("报废刀模不可再维护");
        String description = text(body.get("description"));
        if (action == ActionType.REMAKE) {
            String newNo = required(body, "newDieNo");
            Long successor = insertDie(newNo, text(die.get("die_name")), text(die.get("purpose")),
                    text(die.get("specification")), text(die.get("version")), text(die.get("location")), dieId, description);
            jdbc.update("INSERT INTO engineering_resource_product_rel(resource_type,resource_id,product_id,purpose,is_active,create_by) SELECT resource_type,?,product_id,purpose,is_active,? FROM engineering_resource_product_rel WHERE resource_type=? AND resource_id=? AND is_active=1",
                    successor, username(), ResourceType.DIE.name(), dieId);
            jdbc.update("UPDATE engineering_die SET status=?,update_by=? WHERE die_id=?", DieStatus.REPLACED.name(), username(), dieId);
            log(ResourceType.DIE, dieId, action, before.name(), DieStatus.REPLACED.name(), ResourceType.DIE, successor, description);
            return successor;
        }
        DieStatus after = switch (action) {
            case REPAIR -> DieStatus.MAINTENANCE;
            case STOP -> DieStatus.STOPPED;
            case ENABLE -> DieStatus.AVAILABLE;
            case SCRAP -> DieStatus.SCRAPPED;
            default -> throw new BusinessException("该动作不适用于刀模");
        };
        jdbc.update("UPDATE engineering_die SET status=?,update_by=? WHERE die_id=?", after.name(), username(), dieId);
        log(ResourceType.DIE, dieId, action, before.name(), after.name(), null, null, description);
        return dieId;
    }

    public List<Map<String, Object>> products(String type, Long id) {
        ResourceType.valueOf(type);
        return jdbc.queryForList("SELECT r.product_id,p.product_code,p.product_name,r.purpose FROM engineering_resource_product_rel r JOIN product p ON p.product_id=r.product_id WHERE r.resource_type=? AND r.resource_id=? AND r.is_active=1 ORDER BY p.product_code", type, id);
    }

    @Transactional
    public void replaceProducts(String type, Long id, Map<String, Object> body) {
        ResourceType resourceType = ResourceType.valueOf(type);
        if (resourceType == ResourceType.SCREEN_FRAME) throw new BusinessException("产品应关联到版面，不关联空网框");
        syncProducts(resourceType, id, productIds(body), text(body.get("purpose")));
    }

    public List<Map<String, Object>> maintenance(String type, Long id) {
        ResourceType.valueOf(type);
        return jdbc.queryForList("SELECT * FROM engineering_resource_maintenance WHERE resource_type=? AND resource_id=? ORDER BY operate_time DESC,maintenance_id DESC", type, id);
    }

    private Long insertDie(String no,String name,String purpose,String spec,String version,String location,Long predecessor,String remark) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("INSERT INTO engineering_die(die_no,die_name,purpose,specification,version,location,status,predecessor_id,remark,create_by) VALUES(?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,no); ps.setString(2,name); ps.setString(3,purpose); ps.setString(4,spec); ps.setString(5,version); ps.setString(6,location);
            ps.setString(7,DieStatus.AVAILABLE.name()); if(predecessor==null) ps.setNull(8,java.sql.Types.BIGINT); else ps.setLong(8,predecessor);
            ps.setString(9,remark); ps.setString(10,username()); return ps;
        }, key); return key.getKey().longValue();
    }

    private void syncProducts(ResourceType type, Long id, List<Long> productIds, String purpose) {
        jdbc.update("UPDATE engineering_resource_product_rel SET is_active=0 WHERE resource_type=? AND resource_id=?", type.name(), id);
        for (Long productId : productIds) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_id=?", Integer.class, productId);
            if (exists == null || exists == 0) throw new BusinessException("产品不存在：" + productId);
            jdbc.update("INSERT INTO engineering_resource_product_rel(resource_type,resource_id,product_id,purpose,is_active,create_by) VALUES(?,?,?,?,1,?) ON DUPLICATE KEY UPDATE purpose=VALUES(purpose),is_active=1,update_time=NOW()",
                    type.name(),id,productId,purpose,username());
        }
    }

    private void log(ResourceType type,Long id,ActionType action,String before,String after,ResourceType successorType,Long successorId,String description) {
        jdbc.update("INSERT INTO engineering_resource_maintenance(resource_type,resource_id,action_type,before_status,after_status,successor_type,successor_id,description,operator) VALUES(?,?,?,?,?,?,?,?,?)",
                type.name(),id,action.name(),before,after,successorType==null?null:successorType.name(),successorId,description,username());
    }

    @SuppressWarnings("unchecked")
    private List<Long> productIds(Map<String,Object> body) {
        Object raw=body.get("productIds"); if(!(raw instanceof List<?> list)) return List.of();
        return list.stream().map(this::longValue).filter(java.util.Objects::nonNull).distinct().toList();
    }
    private String required(Map<String,Object> b,String key){String v=text(b.get(key));if(v==null||v.isBlank())throw new BusinessException(key+"不能为空");return v.trim();}
    private Long requireLong(Map<String,Object>b,String key){Long v=longValue(b.get(key));if(v==null)throw new BusinessException(key+"不能为空");return v;}
    private Long longValue(Object value){if(value==null||String.valueOf(value).isBlank())return null;return value instanceof Number n?n.longValue():Long.valueOf(String.valueOf(value));}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private String username(){try{return SecurityUtils.getUsername();}catch(Exception e){return "system";}}
}
