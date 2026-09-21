package com.jjx.engineering.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.dto.DieImportDTO;
import com.jjx.engineering.domain.dto.ScreenFrameImportDTO;
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

    /**
     * 网框分页列表（2026-09-21 性能改造）：
     * 老台账导入后网框 7,291 条，原实现全表返回 2.8MB JSON + 前端一次渲染上万行，页面卡死。
     * 现改为：分页 + 关键字/状态过滤 + 版面内容只回前 60 字（编辑/制版不依赖列表里的全文）。
     */
    public Map<String, Object> pageFrames(String keyword, String status, Integer pageNum, Integer pageSize) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        List<Object> args = new ArrayList<>();
        StringBuilder extra = new StringBuilder();
        if (keyword != null && !keyword.isBlank()) {
            extra.append(" AND (f.frame_no LIKE ? OR p.plate_no LIKE ? OR p.content LIKE ? OR f.remark LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        if (status != null && !status.isBlank()) {
            ScreenFrameStatus.valueOf(status);
            extra.append(" AND f.status=?");
            args.add(status);
        }
        // 修复（2026-09-21）：SELECT 里用了 pr/prod 两个别名，FROM 必须带上对应 JOIN，
        // 否则报 bad SQL grammar（未知表别名）。参数顺序：p.status → pr.resource_type → 过滤条件 → 分页。
        String from = """
            FROM engineering_screen_frame f
            LEFT JOIN engineering_screen_plate p ON p.frame_id=f.frame_id AND p.status=?
            LEFT JOIN engineering_resource_product_rel pr ON pr.resource_type=? AND pr.resource_id=p.plate_id AND pr.is_active=1
            LEFT JOIN product prod ON prod.product_id=pr.product_id
            WHERE f.del_flag='0'
            """;
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(ScreenPlateStatus.ACTIVE.name());
        countArgs.add(ResourceType.SCREEN_PLATE.name());
        countArgs.addAll(args);
        Integer total = jdbc.queryForObject("SELECT COUNT(DISTINCT f.frame_id) " + from + extra,
                Integer.class, countArgs.toArray());
        List<Object> pageArgs = new ArrayList<>(countArgs);
        pageArgs.add(ps);
        pageArgs.add((pn - 1) * ps);
        List<Map<String, Object>> records = jdbc.queryForList("""
                SELECT f.*,p.plate_id plate_id,p.plate_no plate_no,LEFT(p.content,60) content,
                  GROUP_CONCAT(DISTINCT CONCAT(pr.product_id, ':', prod.product_code, ' ', prod.product_name)
                    ORDER BY prod.product_code SEPARATOR '||') product_refs
                """ + from + extra + " GROUP BY f.frame_id,p.plate_id ORDER BY f.frame_no LIMIT ? OFFSET ?",
                pageArgs.toArray());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        return result;
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

    /**
     * 刀模分页列表（2026-09-21 性能改造）：老台账导入后 12,134 条，原全表返回 4.8MB JSON。
     */
    public Map<String, Object> pageDies(String keyword, String status, Integer pageNum, Integer pageSize) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        List<Object> args = new ArrayList<>();
        StringBuilder extra = new StringBuilder();
        if (keyword != null && !keyword.isBlank()) {
            extra.append(" AND (d.die_no LIKE ? OR d.die_name LIKE ? OR d.purpose LIKE ? OR d.location LIKE ? OR d.remark LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 5; i++) {
                args.add(like);
            }
        }
        if (status != null && !status.isBlank()) {
            DieStatus.valueOf(status);
            extra.append(" AND d.status=?");
            args.add(status);
        }
        String from = """
            FROM engineering_die d
            LEFT JOIN engineering_resource_product_rel pr ON pr.resource_type=? AND pr.resource_id=d.die_id AND pr.is_active=1
            LEFT JOIN product p ON p.product_id=pr.product_id
            WHERE d.del_flag='0'
            """;
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(ResourceType.DIE.name());
        countArgs.addAll(args);
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM engineering_die d WHERE d.del_flag='0'" + extra, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(countArgs);
        pageArgs.add(ps);
        pageArgs.add((pn - 1) * ps);
        List<Map<String, Object>> records = jdbc.queryForList("""
                SELECT d.*,GROUP_CONCAT(DISTINCT CONCAT(pr.product_id, ':', p.product_code, ' ', p.product_name)
                  ORDER BY p.product_code SEPARATOR '||') product_refs
                """ + from + extra + " GROUP BY d.die_id ORDER BY d.die_no LIMIT ? OFFSET ?",
                pageArgs.toArray());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        return result;
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

    /**
     * 网版（网框 + 当前版面）Excel 导入（2026-09-21）。
     *
     * <p>规则：
     * · 按网框编号 upsert（表已有唯一索引 uk_screen_frame_no）；
     * · 框型留空 → 取编号首字母（G0001 → G）；
     * · 状态支持中文（空框/已制版/维护中/报废）与英文枚举；留空时：有版面内容=已制版，无内容=空框；
     * · 版面内容非空 → 落到该网框的当前版面（有 ACTIVE 版面则更新内容，无则新建 ACTIVE 版面）；
     * · 单行失败不中断整体，返回失败明细（最多 20 条）。</p>
     *
     * @return {total, inserted, updated, plates, failed, errors}
     */
    @Transactional
    public Map<String, Object> importScreenFrames(List<ScreenFrameImportDTO> rows) {
        int inserted = 0;
        int updated = 0;
        int plates = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        int rowNo = 1; // 第 1 行是表头
        for (ScreenFrameImportDTO dto : rows) {
            rowNo++;
            try {
                String no = normalizeFrameNo(dto.getFrameNo());
                if (no == null) {
                    throw new BusinessException("网框编号为空");
                }
                String content = text(dto.getContent());
                String frameType = text(dto.getFrameType());
                if (frameType == null) {
                    frameType = no.substring(0, 1).toUpperCase();
                }
                String status = parseFrameStatus(dto.getStatus(), content != null);
                String remark = text(dto.getRemark());
                if (remark != null && remark.length() > 500) {
                    remark = remark.substring(0, 500);
                }

                List<Long> ids = jdbc.queryForList(
                        "SELECT frame_id FROM engineering_screen_frame WHERE frame_no=? AND del_flag='0' LIMIT 1", Long.class, no);
                Long frameId;
                if (ids.isEmpty()) {
                    frameId = insertFrame(no, frameType, text(dto.getMesh()), text(dto.getLocation()), status, remark);
                    inserted++;
                } else {
                    frameId = ids.get(0);
                    Map<String, Object> cur = jdbc.queryForMap(
                            "SELECT frame_type,mesh,location,status,remark FROM engineering_screen_frame WHERE frame_id=?", frameId);
                    jdbc.update("""
                            UPDATE engineering_screen_frame SET frame_type=?,mesh=?,location=?,status=?,remark=?,update_by=?
                             WHERE frame_id=?
                            """,
                            frameType,
                            text(dto.getMesh()) != null ? text(dto.getMesh()) : cur.get("mesh"),
                            text(dto.getLocation()) != null ? text(dto.getLocation()) : cur.get("location"),
                            status,
                            remark != null ? remark : cur.get("remark"),
                            username(), frameId);
                    updated++;
                }

                if (content != null) {
                    List<Long> plateIds = jdbc.queryForList(
                            "SELECT plate_id FROM engineering_screen_plate WHERE frame_id=? AND status=? ORDER BY plate_id DESC LIMIT 1",
                            Long.class, frameId, ScreenPlateStatus.ACTIVE.name());
                    if (plateIds.isEmpty()) {
                        jdbc.update("""
                                INSERT INTO engineering_screen_plate(frame_id,plate_no,content,status,plated_time,create_by)
                                VALUES(?,?,?,?,NOW(),?)
                                """, frameId, no, truncate(content, 1000), ScreenPlateStatus.ACTIVE.name(), username());
                    } else {
                        jdbc.update("UPDATE engineering_screen_plate SET content=?,update_by=? WHERE plate_id=?",
                                truncate(content, 1000), username(), plateIds.get(0));
                    }
                    plates++;
                }
            } catch (Exception e) {
                failed++;
                if (errors.size() < 20) {
                    errors.add("第" + rowNo + "行[" + (dto.getFrameNo() == null ? "" : dto.getFrameNo()) + "]：" + e.getMessage());
                }
            }
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("plates", plates);
        result.put("failed", failed);
        result.put("errors", errors);
        return result;
    }

    private Long insertFrame(String no, String frameType, String mesh, String location, String status, String remark) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO engineering_screen_frame(frame_no,frame_type,mesh,location,status,remark,create_by) VALUES(?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, no);
            ps.setString(2, frameType);
            ps.setString(3, mesh);
            ps.setString(4, location);
            ps.setString(5, status);
            ps.setString(6, remark);
            ps.setString(7, username());
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private String normalizeFrameNo(String raw) {
        if (raw == null) {
            return null;
        }
        String no = raw.replaceAll("\\s+", "").toUpperCase();
        return no.isBlank() ? null : no;
    }

    /** 状态解析：中文（空框/已制版/维护中/报废）或英文枚举；留空按是否有版面内容判定。 */
    private String parseFrameStatus(String raw, boolean hasContent) {
        String s = raw == null ? "" : raw.replaceAll("\\s+", "");
        if (s.isEmpty()) {
            return hasContent ? ScreenFrameStatus.PLATED.name() : ScreenFrameStatus.EMPTY.name();
        }
        for (ScreenFrameStatus st : ScreenFrameStatus.values()) {
            if (st.name().equalsIgnoreCase(s)) {
                return st.name();
            }
        }
        if (s.contains("报废") || s.contains("作废")) {
            return ScreenFrameStatus.SCRAPPED.name();
        }
        if (s.contains("维修") || s.contains("维护") || s.contains("送修")) {
            return ScreenFrameStatus.MAINTENANCE.name();
        }
        if (s.contains("制版") || s.contains("已用") || s.contains("使用中") || s.contains("在用")) {
            return ScreenFrameStatus.PLATED.name();
        }
        if (s.contains("空") || s.contains("未用") || s.contains("闲置")) {
            return ScreenFrameStatus.EMPTY.name();
        }
        throw new BusinessException("状态无法识别：" + raw + "（空框 已制版 维护中 报废）");
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    /**
     * 刀模 Excel 导入（2026-09-21）。
     *
     * <p>规则：
     * · 按刀模编号 upsert（存在→更新，不存在→新增），编号大写去空格；
     * · 刀模名称留空 → 用编号；
     * · 状态支持中文（可用/维护中/停用/已重做/报废，以及 无刀/空档/只印刷/打样款… → 停用）与英文枚举；
     * · 存放位置：多个库位（2 个以上空格 / 、 / , / ; 分隔）规范化为「；」分隔并去重；
     * · 共用刀模号 → 备注前缀「共用 XX-000」；备注超 500 字截断；
     * · 单行失败不中断整体，返回失败明细（最多 20 条）。</p>
     *
     * @return {total, inserted, updated, failed, errors}
     */
    @Transactional
    public Map<String, Object> importDies(List<DieImportDTO> rows) {
        int inserted = 0;
        int updated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        int rowNo = 1; // 第 1 行是表头
        for (DieImportDTO dto : rows) {
            rowNo++;
            try {
                String no = normalizeDieNo(dto.getDieNo());
                if (no == null) {
                    throw new BusinessException("刀模编号为空");
                }
                String name = text(dto.getDieName());
                if (name == null) {
                    name = no;
                }
                String status = parseDieStatus(dto.getStatus());
                String location = normalizeLocation(dto.getLocation());
                String remark = buildDieRemark(dto.getShareDieNo(), dto.getRemark());
                java.sql.Date stockIn = parseDate(dto.getStockInDate());

                Long existId = dieIdByNo(no);
                if (existId == null) {
                    jdbc.update("""
                            INSERT INTO engineering_die(die_no,die_name,purpose,specification,version,quantity,location,stock_in_date,status,remark,create_by)
                            VALUES(?,?,?,?,?,?,?,?,?,?,?)
                            """, no, name, text(dto.getPurpose()), text(dto.getSpecification()), text(dto.getVersion()),
                            dto.getQuantity(), location, stockIn, status, remark, username());
                    inserted++;
                } else {
                    jdbc.update("""
                            UPDATE engineering_die SET die_name=?,purpose=?,specification=?,version=?,quantity=?,location=?,stock_in_date=?,status=?,remark=?,update_by=?
                             WHERE die_id=? AND del_flag='0'
                            """, name, text(dto.getPurpose()), text(dto.getSpecification()), text(dto.getVersion()),
                            dto.getQuantity(), location, stockIn, status, remark, username(), existId);
                    updated++;
                }
            } catch (Exception e) {
                failed++;
                if (errors.size() < 20) {
                    errors.add("第" + rowNo + "行[" + (dto.getDieNo() == null ? "" : dto.getDieNo()) + "]：" + e.getMessage());
                }
            }
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("inserted", inserted);
        result.put("updated", updated);
        result.put("failed", failed);
        result.put("errors", errors);
        return result;
    }

    /** 刀模编号规范化：大写、去所有空白。 */
    private String normalizeDieNo(String raw) {
        if (raw == null) {
            return null;
        }
        String no = raw.replaceAll("\\s+", "").toUpperCase();
        return no.isBlank() ? null : no;
    }

    private Long dieIdByNo(String dieNo) {
        List<Long> ids = jdbc.queryForList(
                "SELECT die_id FROM engineering_die WHERE die_no=? AND del_flag='0' ORDER BY die_id LIMIT 1", Long.class, dieNo);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 状态解析：支持英文枚举 + 中文（含老台账里的写法）。
     * 老台账里「无刀 / 空档 / 只印刷 / 打样款(无刀) / 暂不用」按 停用 处理（口径 2026-09-21 Leo 确认）。
     */
    private String parseDieStatus(String raw) {
        String s = raw == null ? "" : raw.replaceAll("\\s+", "").trim();
        if (s.isEmpty()) {
            return DieStatus.AVAILABLE.name();
        }
        for (DieStatus st : DieStatus.values()) {
            if (st.name().equalsIgnoreCase(s)) {
                return st.name();
            }
        }
        if (s.contains("报废")) {
            return DieStatus.SCRAPPED.name();
        }
        if (s.contains("重做")) {
            return DieStatus.REPLACED.name();
        }
        if (s.contains("维修") || s.contains("送修") || s.contains("维护") || s.contains("委外")) {
            return DieStatus.MAINTENANCE.name();
        }
        if (s.contains("停用") || s.contains("无刀") || s.contains("空档") || s.contains("只印刷")
                || s.contains("不印刷") || s.contains("打样") || s.contains("暂不用")) {
            return DieStatus.STOPPED.name();
        }
        if (s.contains("可用") || s.contains("正常") || s.contains("使用中")) {
            return DieStatus.AVAILABLE.name();
        }
        throw new BusinessException("状态无法识别：" + raw + "（可用 维护中 停用 已重做 报废）");
    }

    /** 存放位置规范化：2+ 空格 / 、 / , / ; / ； 分隔 → 「；」，去重去空。 */
    private String normalizeLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String p : raw.split("\\s{2,}|、|,|;|；")) {
            String v = p.trim();
            if (!v.isEmpty() && !parts.contains(v)) {
                parts.add(v);
            }
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private String buildDieRemark(String shareDieNo, String remark) {
        StringBuilder sb = new StringBuilder();
        String share = text(shareDieNo);
        if (share != null) {
            sb.append("共用 ").append(share.replaceAll("\\s+", "").toUpperCase()).append("；");
        }
        String r = text(remark);
        if (r != null) {
            sb.append(r);
        }
        String out = sb.toString();
        if (out.isEmpty()) {
            return null;
        }
        return out.length() > 500 ? out.substring(0, 500) : out;
    }

    private java.sql.Date parseDate(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        String normalized = s.replace('/', '-').replace('.', '-');
        if (normalized.length() >= 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            return java.sql.Date.valueOf(java.time.LocalDate.parse(normalized));
        } catch (Exception e) {
            throw new BusinessException("入库日期格式应为 yyyy-MM-dd：" + raw);
        }
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
