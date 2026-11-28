package com.jjx.engineering.printing.artwork;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.vo.EngineeringFilmVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ArtworkRepository {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public ArtworkView detail(long id) {
        var headers = jdbc.queryForList("SELECT * FROM print_artwork WHERE artwork_id=?", id);
        if (headers.isEmpty()) throw new BusinessException(404, "图案不存在");
        var header = headers.get(0);
        var products = jdbc.queryForList("""
            SELECT product_id FROM engineering_resource_product_rel
            WHERE resource_type='ARTWORK' AND resource_id=? AND is_active=1 ORDER BY product_id
            """, Long.class, id);
        var versions = jdbc.query("""
            SELECT v.version_id,v.version_no,v.status,v.drawing_file_id,v.technical_spec,v.designer_id,
                   v.approved_by,v.approved_time,v.released_by,v.released_time,v.lock_version AS revision,
                   NULLIF(JSON_UNQUOTE(JSON_EXTRACT(m.result_snapshot,'$.controlledContent.contentDigest')),'null') AS content_digest,
                   JSON_UNQUOTE(JSON_EXTRACT(m.result_snapshot,'$.controlledContent.algorithm')) AS content_algorithm,
                   NULLIF(JSON_UNQUOTE(JSON_EXTRACT(m.result_snapshot,'$.controlledContent.drawingSha256')),'null') AS drawing_sha256,
                   att.file_name AS drawing_file_name,att.file_type AS drawing_file_type,v.styles_json
            FROM print_artwork_version v
            LEFT JOIN sys_attachment att ON att.id=v.drawing_file_id
            LEFT JOIN engineering_resource_maintenance m ON m.maintenance_id=(
                SELECT MAX(x.maintenance_id) FROM engineering_resource_maintenance x
                WHERE x.resource_type='ARTWORK_VERSION' AND x.resource_id=v.version_id AND x.action_type='release'
                AND x.result_snapshot IS NOT NULL)
            WHERE v.artwork_id=? ORDER BY v.version_id
            """, new DataClassRowMapper<>(ArtworkView.Version.class), id);
        boolean requestsReady=FilmRequestService.schemaReady(jdbc);
        var films = jdbc.query("""
            SELECT f.film_id,f.version_id,f.film_no,f.location,f.status,f.material,f.thickness_mm,
                   f.size,f.output_time,f.destroy_time,f.lock_version AS revision,%s AS film_request_id
            FROM print_film_copy f JOIN print_artwork_version v ON v.version_id=f.version_id
            WHERE v.artwork_id=? AND f.status IN ('AVAILABLE','ARCHIVED','DESTROYED') ORDER BY f.film_id
            """.formatted(requestsReady ? "f.film_request_id" : "NULL"), new DataClassRowMapper<>(ArtworkView.Film.class), id);
        return new ArtworkView(id, (String) header.get("artwork_no"), (String) header.get("name"),
            (String) header.get("purpose"), (String) header.get("family"), (String) header.get("set_code"),
            (Long) header.get("sample_order_id"), (Long) header.get("default_version_id"),
            ((Number) header.get("lock_version")).intValue(), products, versions, films, productText(id));
    }

    /** P06/M1：图案分页列表（只读）。keyword 匹配图案编号/名称；status 匹配“存在该状态版本”。 */
    @Transactional(readOnly = true)
    public Map<String, Object> listArtworks(String keyword, String family, String status, int pageNum, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append("""
                AND (a.artwork_no LIKE ? OR a.name LIKE ? OR EXISTS (
                    SELECT 1 FROM engineering_resource_product_rel rel JOIN product p ON p.product_id=rel.product_id
                    WHERE rel.resource_type='ARTWORK' AND rel.resource_id=a.artwork_id AND rel.is_active=1
                      AND (p.product_code LIKE ? OR p.product_name LIKE ?)))
                """);
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (family != null && !family.isBlank()) {
            where.append(" AND a.family=?");
            args.add(family);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM print_artwork_version x WHERE x.artwork_id=a.artwork_id AND x.status=?)");
            args.add(status);
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM print_artwork a" + where, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((pageNum - 1) * pageSize);
        String pending=FilmRequestService.schemaReady(jdbc) ? """
            (SELECT COALESCE(SUM(r.requested_quantity-(SELECT COUNT(*) FROM print_film_copy c WHERE c.film_request_id=r.request_id)),0)
             FROM print_film_request r JOIN print_artwork_version rv ON rv.version_id=r.version_id WHERE rv.artwork_id=a.artwork_id)
            """ : "NULL";
        var rows = jdbc.queryForList("""
            SELECT a.artwork_id AS artworkId, a.artwork_no AS artworkNo, a.name, a.purpose, a.family,
                   a.set_code AS setCode, a.default_version_id AS defaultVersionId, dv.version_no AS defaultVersionNo,
                   (SELECT COUNT(*) FROM print_artwork_version v WHERE v.artwork_id=a.artwork_id) AS versionCount,
                   (SELECT COUNT(*) FROM print_artwork_version v WHERE v.artwork_id=a.artwork_id AND v.status='RELEASED') AS releasedVersionCount,
                   (SELECT v2.status FROM print_artwork_version v2 WHERE v2.artwork_id=a.artwork_id
                     ORDER BY v2.version_id DESC LIMIT 1) AS latestStatus,
                   (SELECT COUNT(*) FROM print_film_copy f JOIN print_artwork_version fv ON fv.version_id=f.version_id
                    WHERE fv.artwork_id=a.artwork_id AND f.status IN ('AVAILABLE','ARCHIVED','DESTROYED')) AS copyCount,
                   %s AS pendingFilmCount,
                   (SELECT GROUP_CONCAT(CONCAT(p.product_code,' ',p.product_name) ORDER BY p.product_code SEPARATOR ' / ')
                    FROM engineering_resource_product_rel rel JOIN product p ON p.product_id=rel.product_id
                    WHERE rel.resource_type='ARTWORK' AND rel.resource_id=a.artwork_id AND rel.is_active=1) AS productsText,
                   a.update_time AS updateTime
            FROM print_artwork a
            LEFT JOIN print_artwork_version dv ON dv.version_id=a.default_version_id
            """.formatted(pending) + where + " ORDER BY a.update_time IS NULL, a.update_time DESC, a.artwork_id DESC LIMIT ? OFFSET ?",
            pageArgs.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", rows);
        result.put("total", total == null ? 0 : total);
        return result;
    }

    private String productText(long artworkId) {
        return jdbc.queryForObject("""
            SELECT GROUP_CONCAT(CONCAT(p.product_code,' ',p.product_name) ORDER BY p.product_code SEPARATOR ' / ')
            FROM engineering_resource_product_rel rel JOIN product p ON p.product_id=rel.product_id
            WHERE rel.resource_type='ARTWORK' AND rel.resource_id=? AND rel.is_active=1
            """,String.class,artworkId);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> listFilms(String keyword,String family,String status,int pageNum,int pageSize) {
        String from=" FROM print_film_copy f JOIN print_artwork_version v ON v.version_id=f.version_id JOIN print_artwork a ON a.artwork_id=v.artwork_id";
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        List<Object> args=new ArrayList<>();
        if(keyword!=null && !keyword.isBlank()) {
            where.append(" AND (f.film_no LIKE ? OR a.artwork_no LIKE ? OR a.name LIKE ? OR v.version_no LIKE ? OR f.location LIKE ?)");
            for(int i=0;i<5;i++) args.add("%"+keyword.trim()+"%");
        }
        if(family!=null && !family.isBlank()) { where.append(" AND a.family=?"); args.add(family); }
        if(status!=null && !status.isBlank()) { where.append(" AND f.status=?"); args.add(status); }
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+from+where,Long.class,args.toArray());
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(pageSize);pageArgs.add((pageNum-1)*pageSize);
        var rows=jdbc.queryForList("""
            SELECT f.film_id AS filmId,f.film_no AS filmNo,f.version_id AS versionId,
                   a.artwork_id AS artworkId,a.artwork_no AS artworkNo,a.name,a.family,v.version_no AS versionNo,
                   f.status,f.location,f.material,f.thickness_mm AS thicknessMm,f.size,f.output_time AS outputTime,
                   f.lock_version AS revision,
                   (SELECT JSON_UNQUOTE(JSON_EXTRACT(m.result_snapshot,'$.dueDate'))
                      FROM engineering_resource_maintenance m WHERE m.resource_type='FILM_COPY'
                      AND m.resource_id=f.film_id AND m.action_type='requestDetails' ORDER BY m.maintenance_id DESC LIMIT 1) AS dueDate
            """+from+where+" ORDER BY f.film_id DESC LIMIT ? OFFSET ?",pageArgs.toArray());
        return Map.of("records",rows,"total",total==null ? 0 : total);
    }

    public List<EngineeringFilmVO> productVersions(long productId, boolean currentOnly) {
        return jdbc.query("""
            SELECT a.artwork_id,a.artwork_no AS film_code,a.name AS film_name,a.family AS film_type,
                   r.product_id,v.version_id,v.version_no AS version,v.status AS version_status,
                   v.technical_spec,v.drawing_file_id AS file_id,
                   IF(a.default_version_id=v.version_id,1,0) AS is_current
            FROM engineering_resource_product_rel r
            JOIN print_artwork a ON a.artwork_id=r.resource_id
            JOIN print_artwork_version v ON v.artwork_id=a.artwork_id
            WHERE r.resource_type='ARTWORK' AND r.product_id=? AND r.is_active=1
              AND (?=0 OR a.default_version_id=v.version_id)
            ORDER BY a.artwork_no,v.version_id
            """, new BeanPropertyRowMapper<>(EngineeringFilmVO.class), productId, currentOnly);
    }
}
