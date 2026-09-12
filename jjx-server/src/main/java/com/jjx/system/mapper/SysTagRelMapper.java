package com.jjx.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.system.domain.entity.SysTagRel;
import com.jjx.system.domain.vo.TagFacetVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统标签关联Mapper（dev-20260911-007）
 */
@Mapper
public interface SysTagRelMapper extends BaseMapper<SysTagRel> {

    /**
     * 清除某业务对象的所有标签关联（重设标签前调用）
     *
     * @param bizType 业务类型
     * @param bizId   业务主键
     * @return 影响行数
     */
    @Delete("DELETE FROM sys_tag_rel WHERE biz_type = #{bizType} AND biz_id = #{bizId}")
    int deleteByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /**
     * 按标签反查业务ID——任一命中（OR 语义），dev-20260912-004
     */
    @Select("<script>SELECT DISTINCT biz_id FROM sys_tag_rel WHERE biz_type = #{bizType} "
            + "AND tag_id IN <foreach collection='tagIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Long> selectBizIdsByAnyTag(@Param("bizType") String bizType, @Param("tagIds") List<Long> tagIds);

    /**
     * 按标签反查业务ID——全部命中（AND 语义，要求所需标签数与实际命中数一致），dev-20260912-004
     */
    @Select("<script>SELECT biz_id FROM sys_tag_rel WHERE biz_type = #{bizType} "
            + "AND tag_id IN <foreach collection='tagIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY biz_id HAVING COUNT(DISTINCT tag_id) = #{required}</script>")
    List<Long> selectBizIdsByAllTags(@Param("bizType") String bizType, @Param("tagIds") List<Long> tagIds,
                                     @Param("required") int required);

    /**
     * 统计某业务类型下各标签的关联数量（facets 计数，dev-20260912-004）
     * bizIds 为空表示不限业务对象（=该类型下标签总关联数）
     */
    @Select("<script>SELECT tag_id AS tagId, COUNT(*) AS `count` FROM sys_tag_rel WHERE biz_type = #{bizType} "
            + "<if test='bizIds != null'> AND biz_id IN "
            + "<foreach collection='bizIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if> "
            + "GROUP BY tag_id</script>")
    List<TagFacetVO> countByTagGrouped(@Param("bizType") String bizType, @Param("bizIds") List<Long> bizIds);
}
