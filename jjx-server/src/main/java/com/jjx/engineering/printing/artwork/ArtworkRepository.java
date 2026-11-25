package com.jjx.engineering.printing.artwork;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.vo.EngineeringFilmVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
            SELECT version_id,version_no,status,drawing_file_id,technical_spec,designer_id,
                   approved_by,approved_time,released_by,released_time,lock_version AS revision
            FROM print_artwork_version WHERE artwork_id=? ORDER BY version_id
            """, new DataClassRowMapper<>(ArtworkView.Version.class), id);
        var films = jdbc.query("""
            SELECT f.film_id,f.version_id,f.film_no,f.location,f.status,f.material,f.thickness_mm,
                   f.size,f.output_time,f.destroy_time,f.lock_version AS revision
            FROM print_film_copy f JOIN print_artwork_version v ON v.version_id=f.version_id
            WHERE v.artwork_id=? ORDER BY f.film_id
            """, new DataClassRowMapper<>(ArtworkView.Film.class), id);
        return new ArtworkView(id, (String) header.get("artwork_no"), (String) header.get("name"),
            (String) header.get("purpose"), (String) header.get("family"), (String) header.get("set_code"),
            (Long) header.get("sample_order_id"), (Long) header.get("default_version_id"),
            ((Number) header.get("lock_version")).intValue(), products, versions, films);
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
