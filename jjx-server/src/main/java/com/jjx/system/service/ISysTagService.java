package com.jjx.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.system.domain.entity.SysTag;

import java.util.List;

/**
 * 系统标签服务（通用标签体系，dev-20260911-007）
 */
public interface ISysTagService extends IService<SysTag> {

    /**
     * 查询标签列表
     *
     * @param tagGroup 标签分组（可空=全部）
     * @param keyword  名称/编码模糊（可空）
     * @param status   状态（可空）
     * @return 标签列表
     */
    List<SysTag> listTags(String tagGroup, String keyword, Integer status);

    /**
     * 新增标签（分组内编码唯一）
     */
    SysTag createTag(SysTag tag, String operName);

    /**
     * 修改标签（仅名称/排序/状态/备注，编码不改）
     */
    boolean updateTag(SysTag tag, String operName);

    /**
     * 删除标签（软删；存在子标签时拒绝；同时清理关联）
     */
    boolean deleteTag(Long tagId, String operName);

    /**
     * 查询某业务对象已挂标签
     */
    List<SysTag> getBizTags(String bizType, Long bizId);

    /**
     * 重设某业务对象的标签（全量替换）
     *
     * @param tagIds 目标标签ID集合，空集合表示清空
     */
    void setBizTags(String bizType, Long bizId, List<Long> tagIds, String operName);

    /**
     * 按标签反查业务ID（用于按标签筛选业务列表）
     */
    List<Long> getBizIdsByTagIds(String bizType, List<Long> tagIds);

    /**
     * 按标签反查业务ID（dev-20260912-004 标签查询组件）
     *
     * @param bizType  业务类型
     * @param tagIds   标签ID集合
     * @param matchAll true=同时含全部标签（AND）；false=含任一标签（OR）
     */
    List<Long> getBizIdsByTagIds(String bizType, List<Long> tagIds, boolean matchAll);

    /**
     * 标签查询辅助（facets）：返回标签列表 + 在当前选中标签组合下的关联数量。
     * 计数口径：MATCH=AND 时以「已选标签 AND 交集后的业务对象集合」为分母；OR 时不做收窄（计数=各标签全库数量）。
     *
     * @param bizType         业务类型
     * @param selectedTagIds  已选标签（参与收窄，可为空）
     * @param keyword         标签名/编码模糊搜索（可为空）
     */
    List<com.jjx.system.domain.vo.TagFacetVO> facets(String bizType, List<Long> selectedTagIds, String keyword);

    /**
     * 标签查询辅助（facets）带匹配模式（dev-20260912-007）
     *
     * @param matchMode AND=按已选收窄（默认）；OR=不收窄
     */
    List<com.jjx.system.domain.vo.TagFacetVO> facets(String bizType, List<Long> selectedTagIds, String keyword, String matchMode);

    /**
     * 统一按标签过滤：返回需要 in 的业务ID列表（dev-20260912-007 公共助手）
     * <p>约定：各模块 QueryVO 统一用 tagIds + tagMatchMode(AND/OR) 两个字段；
     * tagIds 为空 → 返回 null 表示「无标签过滤」；非空 → 返回命中的 bizId 列表（可能为空集合=无命中）。</p>
     *
     * @param bizType       业务类型
     * @param tagIds        标签ID集合
     * @param tagMatchMode  AND=同时含全部（默认）；OR=含任一
     * @return null=无过滤；非null=需 in 的 bizId 列表
     */
    List<Long> resolveBizIdsFilter(String bizType, List<Long> tagIds, String tagMatchMode);

    /**
     * 取或建（导入时自动建标签用）
     *
     * @param tagGroup 分组
     * @param tagName  标签名
     * @param parentId 父标签ID（可空）
     * @return 标签ID
     */
    Long ensureTag(String tagGroup, String tagName, Long parentId, String operName);
}
