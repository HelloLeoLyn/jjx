package com.jjx.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysTag;
import com.jjx.system.domain.entity.SysTagRel;
import com.jjx.system.mapper.SysTagMapper;
import com.jjx.system.mapper.SysTagRelMapper;
import com.jjx.system.service.ISysTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统标签服务实现（dev-20260911-007）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysTagServiceImpl extends ServiceImpl<SysTagMapper, SysTag> implements ISysTagService {

    private final SysTagMapper tagMapper;
    private final SysTagRelMapper tagRelMapper;

    @Override
    public List<SysTag> listTags(String tagGroup, String keyword, Integer status) {
        LambdaQueryWrapper<SysTag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(tagGroup)) {
            wrapper.eq(SysTag::getTagGroup, tagGroup);
        }
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SysTag::getTagName, keyword).or().like(SysTag::getTagCode, keyword));
        }
        if (status != null) {
            wrapper.eq(SysTag::getStatus, status);
        }
        wrapper.orderByAsc(SysTag::getTagGroup).orderByAsc(SysTag::getSortOrder).orderByAsc(SysTag::getTagId);
        return tagMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysTag createTag(SysTag tag, String operName) {
        if (StringUtils.isBlank(tag.getTagGroup())) {
            throw new BusinessException("标签分组不能为空");
        }
        if (StringUtils.isBlank(tag.getTagName())) {
            throw new BusinessException("标签名称不能为空");
        }
        String code = StringUtils.trimToNull(tag.getTagCode());
        if (code == null) {
            code = buildCode(tag.getTagGroup(), tag.getTagName(), tag.getParentId());
        }
        if (existsCode(tag.getTagGroup(), code, null)) {
            throw new BusinessException("同分组下标签编码已存在：" + code);
        }
        tag.setTagCode(code);
        tag.setTagName(tag.getTagName().trim());
        if (tag.getStatus() == null) {
            tag.setStatus(1);
        }
        if (tag.getSortOrder() == null) {
            tag.setSortOrder(0);
        }
        tag.setCreateBy(operName);
        tag.setUpdateBy(operName);
        tag.setDelFlag("0");
        tagMapper.insert(tag);
        return tag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTag(SysTag tag, String operName) {
        if (tag.getTagId() == null) {
            throw new BusinessException("标签ID不能为空");
        }
        SysTag existing = tagMapper.selectById(tag.getTagId());
        if (existing == null) {
            throw new BusinessException("标签不存在");
        }
        if (StringUtils.isBlank(tag.getTagName())) {
            throw new BusinessException("标签名称不能为空");
        }
        // 编码、分组不允许改（避免关联失真）
        tag.setTagCode(null);
        tag.setTagGroup(null);
        tag.setParentId(null);
        tag.setUpdateBy(operName);
        return tagMapper.updateById(tag) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTag(Long tagId, String operName) {
        if (tagId == null) {
            throw new BusinessException("标签ID不能为空");
        }
        SysTag existing = tagMapper.selectById(tagId);
        if (existing == null) {
            throw new BusinessException("标签不存在");
        }
        Long childCount = tagMapper.selectCount(new LambdaQueryWrapper<SysTag>()
                .eq(SysTag::getParentId, tagId));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("存在子标签，请先删除子标签");
        }
        tagRelMapper.delete(new LambdaQueryWrapper<SysTagRel>().eq(SysTagRel::getTagId, tagId));
        log.info("删除标签 {}（{}），操作人 {}", tagId, existing.getTagName(), operName);
        return tagMapper.deleteById(tagId) > 0;
    }

    @Override
    public List<SysTag> getBizTags(String bizType, Long bizId) {
        if (StringUtils.isBlank(bizType) || bizId == null) {
            return new ArrayList<>();
        }
        return tagMapper.selectTagsByBiz(bizType, bizId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setBizTags(String bizType, Long bizId, List<Long> tagIds, String operName) {
        if (StringUtils.isBlank(bizType) || bizId == null) {
            throw new BusinessException("业务类型与业务ID不能为空");
        }
        tagRelMapper.deleteByBiz(bizType, bizId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>(tagIds);
        for (Long tagId : distinct) {
            if (tagId == null) {
                continue;
            }
            SysTagRel rel = new SysTagRel();
            rel.setTagId(tagId);
            rel.setBizType(bizType);
            rel.setBizId(bizId);
            rel.setCreateBy(operName);
            rel.setCreateTime(LocalDateTime.now());
            tagRelMapper.insert(rel);
        }
    }

    @Override
    public List<Long> getBizIdsByTagIds(String bizType, List<Long> tagIds) {
        if (StringUtils.isBlank(bizType) || tagIds == null || tagIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<SysTagRel> rels = tagRelMapper.selectList(new LambdaQueryWrapper<SysTagRel>()
                .eq(SysTagRel::getBizType, bizType)
                .in(SysTagRel::getTagId, tagIds));
        Set<Long> ids = new LinkedHashSet<>();
        for (SysTagRel rel : rels) {
            ids.add(rel.getBizId());
        }
        return new ArrayList<>(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ensureTag(String tagGroup, String tagName, Long parentId, String operName) {
        if (StringUtils.isBlank(tagGroup) || StringUtils.isBlank(tagName)) {
            return null;
        }
        String name = tagName.trim();
        LambdaQueryWrapper<SysTag> wrapper = new LambdaQueryWrapper<SysTag>()
                .eq(SysTag::getTagGroup, tagGroup)
                .eq(SysTag::getTagName, name);
        if (parentId == null) {
            wrapper.isNull(SysTag::getParentId);
        } else {
            wrapper.eq(SysTag::getParentId, parentId);
        }
        SysTag exists = tagMapper.selectOne(wrapper.last("LIMIT 1"));
        if (exists != null) {
            return exists.getTagId();
        }
        SysTag tag = new SysTag();
        tag.setTagGroup(tagGroup);
        tag.setTagName(name);
        tag.setParentId(parentId);
        try {
            return createTag(tag, operName).getTagId();
        } catch (DuplicateKeyException e) {
            // 并发下同一标签被同时创建：回查一次
            SysTag again = tagMapper.selectOne(wrapper.last("LIMIT 1"));
            if (again != null) {
                return again.getTagId();
            }
            throw e;
        }
    }

    /** 生成标签编码：顶级=名称；二级=父编码*名称（与源数据「大类*明细」口径一致） */
    private String buildCode(String tagGroup, String tagName, Long parentId) {
        String name = tagName.trim();
        if (parentId == null) {
            return name;
        }
        SysTag parent = tagMapper.selectById(parentId);
        String parentCode = parent == null || StringUtils.isBlank(parent.getTagCode())
                ? String.valueOf(parentId) : parent.getTagCode();
        return parentCode + "*" + name;
    }

    /** 同分组编码是否已存在（排除指定ID） */
    private boolean existsCode(String tagGroup, String code, Long excludeId) {
        LambdaQueryWrapper<SysTag> wrapper = new LambdaQueryWrapper<SysTag>()
                .eq(SysTag::getTagGroup, tagGroup)
                .eq(SysTag::getTagCode, code);
        if (excludeId != null) {
            wrapper.ne(SysTag::getTagId, excludeId);
        }
        Long count = tagMapper.selectCount(wrapper);
        return count != null && count > 0;
    }
}
