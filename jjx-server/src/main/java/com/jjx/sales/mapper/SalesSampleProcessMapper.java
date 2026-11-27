package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesSampleProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 打样工序历史记录Mapper
 */
@Mapper
public interface SalesSampleProcessMapper extends BaseMapper<SalesSampleProcess> {
    String DISPLAY_SELECT="SELECT p.*,a.artwork_no AS artworkNo,v.version_no AS artworkVersionNo,"
            + "sp.plate_no AS defaultPlateNo,f.frame_no AS defaultFrameNo FROM sales_sample_process p "
            + "LEFT JOIN print_artwork_version v ON v.version_id=p.artwork_version_id "
            + "LEFT JOIN print_artwork a ON a.artwork_id=v.artwork_id "
            + "LEFT JOIN engineering_screen_plate sp ON sp.plate_id=p.default_plate_id "
            + "LEFT JOIN engineering_screen_frame f ON f.frame_id=sp.frame_id ";
    @Select("SELECT * FROM sales_sample_process WHERE process_id=#{id} FOR UPDATE")
    SalesSampleProcess selectForUpdate(@Param("id") Long id);

    @Select("SELECT a.sample_order_id AS sampleOrderId,a.artwork_id AS artworkId,a.name AS artworkName,v.version_no AS versionNo,a.artwork_no AS artworkNo "
            + "FROM print_artwork_version v JOIN print_artwork a ON a.artwork_id=v.artwork_id "
            + "WHERE v.version_id=#{versionId} AND (EXISTS "
            + "(SELECT 1 FROM sales_sample_order s WHERE s.order_id=#{orderId} AND s.sample_order_id=a.sample_order_id) OR EXISTS "
            + "(SELECT 1 FROM engineering_resource_product_rel r WHERE r.resource_type='ARTWORK' "
            + "AND r.resource_id=a.artwork_id AND r.is_active=1 AND EXISTS "
            + "(SELECT 1 FROM sales_order_product op WHERE op.order_id=#{orderId} AND op.product_id=r.product_id)))")
    java.util.Map<String,Object> planningVersion(@Param("versionId") Long versionId,
            @Param("orderId") Long orderId);

    @Select("SELECT p.plate_no AS plateNo,f.frame_no AS frameNo,p.version_id AS versionId,p.film_copy_id AS filmId,p.stretch_id AS stretchId,"
            + "s.mesh,s.tension_n_cm AS tensionNcm,p.position FROM engineering_screen_plate p "
            + "JOIN engineering_screen_frame f ON f.frame_id=p.frame_id "
            + "JOIN print_frame_stretch s ON s.stretch_id=p.stretch_id WHERE p.plate_id=#{plateId}")
    java.util.Map<String,Object> executionPlate(@Param("plateId") Long plateId);

    @org.apache.ibatis.annotations.Insert("INSERT INTO engineering_resource_product_rel(resource_type,resource_id,product_id,is_active) "
            + "VALUES('ARTWORK',#{artworkId},#{productId},1) ON DUPLICATE KEY UPDATE is_active=1")
    int linkArtworkProduct(@Param("artworkId") Long artworkId,@Param("productId") Long productId);

    @Select("SELECT version_id FROM print_artwork_version WHERE version_id=#{id} FOR UPDATE")
    Long lockArtworkVersion(@Param("id") Long id);

    @Select("SELECT process_id FROM sales_sample_process WHERE actual_plate_id=#{plateId} AND status=#{activeStatus} AND process_id != #{processId} FOR UPDATE")
    List<Long> otherSampleUses(@Param("plateId") Long plateId,@Param("processId") Long processId,@Param("activeStatus") Integer activeStatus);

    @Select("SELECT segment_id FROM print_use_segment WHERE open_plate_id=#{plateId} FOR UPDATE")
    List<Long> productionUses(@Param("plateId") Long plateId);

    @Select("SELECT o.*,s.sample_status,s.sample_round,s.engineering_note FROM sales_order o "
            + "JOIN sales_sample_order s ON s.order_id=o.order_id WHERE o.order_id=#{id} AND o.deleted=0 FOR UPDATE")
    com.jjx.sales.domain.entity.SalesOrder lockSampleProfile(@Param("id") Long id);

    /**
     * 查询样品单所有工序记录（按时间正序）
     */
    @Select(DISPLAY_SELECT+"WHERE p.order_id = #{orderId} ORDER BY p.process_id ASC")
    List<SalesSampleProcess> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 查询样品单指定轮次工序记录（DEV-500 按轮次展示）
     */
    @Select(DISPLAY_SELECT+"WHERE p.order_id = #{orderId} AND p.round_no = #{roundNo} ORDER BY p.process_id ASC")
    List<SalesSampleProcess> selectByOrderAndRound(@Param("orderId") Long orderId, @Param("roundNo") Integer roundNo);
}
