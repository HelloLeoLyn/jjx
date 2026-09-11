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
     * 取或建（导入时自动建标签用）
     *
     * @param tagGroup 分组
     * @param tagName  标签名
     * @param parentId 父标签ID（可空）
     * @return 标签ID
     */
    Long ensureTag(String tagGroup, String tagName, Long parentId, String operName);
}
