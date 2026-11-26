package com.jjx.engineering.printing.search;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 印刷资源统一搜索查询（只读，dev-20261125-011）。含无图案版本的网框候选。 */
@Mapper
public interface PrintSearchRepository {

    String ARTWORK_ROWS = "SELECT a.artwork_id, v.version_id, f.film_id, p.plate_id, p.frame_id, " +
            "a.artwork_no, a.name, a.purpose, v.version_no, v.status AS version_status, " +
            "f.film_no, f.status AS film_status, p.plate_no, p.status AS plate_status, p.position, " +
            "fr.frame_no, fr.status AS frame_status, st.mesh, st.tension_n_cm, st.qualified, " +
            "prod.product_code, prod.product_name, " +
            "CASE WHEN a.artwork_no = #{q} OR f.film_no = #{q} OR p.plate_no = #{q} OR fr.frame_no = #{q} THEN 0 " +
            "WHEN a.artwork_no LIKE CONCAT(#{q},'%') OR f.film_no LIKE CONCAT(#{q},'%') " +
            "OR p.plate_no LIKE CONCAT(#{q},'%') OR fr.frame_no LIKE CONCAT(#{q},'%') THEN 1 ELSE 2 END AS rank " +
            "FROM print_artwork_version v " +
            "JOIN print_artwork a ON a.artwork_id = v.artwork_id " +
            "LEFT JOIN print_film_copy f ON f.version_id = v.version_id " +
            "LEFT JOIN engineering_screen_plate p ON p.version_id = v.version_id " +
            "LEFT JOIN engineering_screen_frame fr ON fr.frame_id = p.frame_id " +
            "LEFT JOIN print_frame_stretch st ON st.stretch_id = p.stretch_id " +
            "LEFT JOIN engineering_resource_product_rel rel ON rel.resource_type='ARTWORK' AND rel.resource_id=a.artwork_id AND rel.is_active=1 " +
            "LEFT JOIN product prod ON prod.product_id = rel.product_id ";

    String FRAME_ROWS = "SELECT NULL artwork_id, NULL version_id, NULL film_id, NULL plate_id, f.frame_id, " +
            "NULL artwork_no, NULL name, NULL purpose, NULL version_no, NULL version_status, " +
            "NULL film_no, NULL film_status, NULL plate_no, NULL plate_status, NULL position, " +
            "f.frame_no, f.status AS frame_status, st.mesh, st.tension_n_cm, st.qualified, " +
            "NULL product_code, NULL product_name, " +
            "CASE WHEN f.frame_no = #{q} THEN 0 WHEN f.frame_no LIKE CONCAT(#{q},'%') THEN 1 ELSE 2 END AS rank " +
            "FROM engineering_screen_frame f " +
            "LEFT JOIN (SELECT s1.* FROM print_frame_stretch s1 JOIN (SELECT frame_id, MAX(stretch_id) mid FROM print_frame_stretch GROUP BY frame_id) m ON m.mid=s1.stretch_id) st ON st.frame_id=f.frame_id ";

    @Select({"<script>",
            "SELECT * FROM (",
            "(", ARTWORK_ROWS, "WHERE 1=1 ",
            "<foreach collection='tokens' item='t'>",
            "AND (a.artwork_no LIKE CONCAT('%',#{t},'%') OR a.name LIKE CONCAT('%',#{t},'%') " +
            "OR v.version_no LIKE CONCAT('%',#{t},'%') OR f.film_no LIKE CONCAT('%',#{t},'%') " +
            "OR p.plate_no LIKE CONCAT('%',#{t},'%') OR fr.frame_no LIKE CONCAT('%',#{t},'%') " +
            "OR prod.product_code LIKE CONCAT('%',#{t},'%') OR prod.product_name LIKE CONCAT('%',#{t},'%') " +
            "OR a.purpose LIKE CONCAT('%',#{t},'%'))",
            "</foreach>",
            "<if test='productId != null'>AND rel.product_id = #{productId} </if>",
            ") UNION ALL (", FRAME_ROWS, "WHERE f.del_flag=0 ",
            "<foreach collection='tokens' item='t'>",
            "AND (f.frame_no LIKE CONCAT('%',#{t},'%') OR f.frame_type LIKE CONCAT('%',#{t},'%'))",
            "</foreach>",
            ")",
            ") t ORDER BY t.rank, t.version_id, t.plate_id, t.frame_id",
            "</script>"})
    IPage<PrintSearchRow> search(Page<PrintSearchRow> page,
                                 @Param("tokens") List<String> tokens,
                                 @Param("q") String q,
                                 @Param("productId") Long productId);
}
