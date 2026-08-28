package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.QualityTemplateQueryDTO;
import com.jjx.production.domain.entity.QualityTemplateRegistry;
import com.jjx.production.domain.entity.QualityTemplatePrintLog;
import com.jjx.production.enums.QualityTemplateStatusEnum;
import com.jjx.production.mapper.QualityTemplateRegistryMapper;
import com.jjx.production.mapper.QualityTemplatePrintLogMapper;
import com.jjx.production.service.QualityTemplateRegistryService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QualityTemplateRegistryServiceImpl implements QualityTemplateRegistryService {
    private static final String CATEGORY_BLANK = "blank";
    private static final String CATEGORY_DATA = "data";
    private final QualityTemplateRegistryMapper mapper;
    private final QualityTemplatePrintLogMapper printLogMapper;

    @Override
    public PageResult<QualityTemplateRegistry> page(QualityTemplateQueryDTO query) {
        LambdaQueryWrapper<QualityTemplateRegistry> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(QualityTemplateRegistry::getRecordNo, query.getKeyword())
                    .or().like(QualityTemplateRegistry::getRecordName, query.getKeyword()));
        }
        wrapper.like(StringUtils.isNotBlank(query.getRecordNo()), QualityTemplateRegistry::getRecordNo, query.getRecordNo())
                .like(StringUtils.isNotBlank(query.getRecordName()), QualityTemplateRegistry::getRecordName, query.getRecordName())
                .eq(StringUtils.isNotBlank(query.getOwnerDept()), QualityTemplateRegistry::getOwnerDept, query.getOwnerDept())
                .eq(StringUtils.isNotBlank(query.getCategory()), QualityTemplateRegistry::getCategory, query.getCategory())
                .eq(query.getStatus() != null, QualityTemplateRegistry::getStatus, query.getStatus())
                .orderByAsc(QualityTemplateRegistry::getRecordNo);
        Page<QualityTemplateRegistry> page = new Page<>(query.getPageNum(), query.getPageSize());
        mapper.selectPage(page, wrapper);
        page.getRecords().forEach(this::fillHasFile);
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public QualityTemplateRegistry getById(Long id) {
        QualityTemplateRegistry template = mapper.selectById(id);
        if (template == null) throw new BusinessException("质量记录模板不存在");
        fillHasFile(template);
        return template;
    }

    @Override
    public List<String> listOwnerDepts() {
        return mapper.selectDistinctOwnerDepts();
    }

    @Override
    @Transactional
    public void recordPrint(Long id) {
        QualityTemplateRegistry template = getById(id);
        if (!Integer.valueOf(QualityTemplateStatusEnum.ACTIVE.getCode()).equals(template.getStatus())) {
            throw new BusinessException("仅生效模板可打印");
        }
        if (CATEGORY_DATA.equals(template.getCategory())) {
            throw new BusinessException("数据联动模板将在后续版本开放打印");
        }
        QualityTemplatePrintLog log = new QualityTemplatePrintLog();
        log.setTemplateId(template.getId());
        log.setRecordNo(template.getRecordNo());
        log.setOperatorId(SecurityUtils.getUserId());
        String realName = SecurityUtils.getRealName();
        log.setOperatorName(StringUtils.isNotBlank(realName) ? realName : SecurityUtils.getUsername());
        log.setPrintTime(LocalDateTime.now());
        printLogMapper.insert(log);
    }

    private void fillHasFile(QualityTemplateRegistry template) {
        template.setHasFile(template.getFileId() != null);
    }

    @Override
    @Transactional
    public Long create(QualityTemplateRegistry template) {
        template.setId(null);
        if (template.getRetentionYears() == null) template.setRetentionYears(2);
        if (StringUtils.isBlank(template.getCategory())) template.setCategory(CATEGORY_BLANK);
        if (template.getStatus() == null) template.setStatus(QualityTemplateStatusEnum.DRAFT.getCode());
        validate(template, true);
        ensureRecordNoUnique(template.getRecordNo(), null);
        template.setCreateBy(SecurityUtils.getUsername());
        mapper.insert(template);
        return template.getId();
    }

    @Override
    @Transactional
    public void update(QualityTemplateRegistry input) {
        if (input.getId() == null) throw new BusinessException("缺少模板ID");
        QualityTemplateRegistry existing = getById(input.getId());
        QualityTemplateRegistry update = new QualityTemplateRegistry();
        update.setId(existing.getId());
        update.setRecordName(input.getRecordName());
        update.setVersion(input.getVersion());
        update.setOwnerDept(input.getOwnerDept());
        update.setRetentionYears(input.getRetentionYears());
        update.setCategory(input.getCategory());
        update.setBizType(input.getBizType());
        update.setFileId(input.getFileId());
        update.setRemark(input.getRemark());
        validate(update, false);
        update.setUpdateBy(SecurityUtils.getUsername());
        mapper.updateById(update);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        QualityTemplateStatusEnum target = QualityTemplateStatusEnum.fromCode(status);
        if (target != QualityTemplateStatusEnum.ACTIVE && target != QualityTemplateStatusEnum.DISABLED) {
            throw new BusinessException("仅支持生效或停用操作");
        }
        getById(id);
        QualityTemplateRegistry update = new QualityTemplateRegistry();
        update.setId(id);
        update.setStatus(target.getCode());
        update.setUpdateBy(SecurityUtils.getUsername());
        mapper.updateById(update);
    }

    @Override
    public void delete(Long id) {
        QualityTemplateRegistry template = getById(id);
        if (!Integer.valueOf(QualityTemplateStatusEnum.DRAFT.getCode()).equals(template.getStatus())) {
            throw new BusinessException("仅草稿模板可删除");
        }
        mapper.deleteById(id);
    }

    private void validate(QualityTemplateRegistry template, boolean creating) {
        if (creating && StringUtils.isBlank(template.getRecordNo())) throw new BusinessException("记录编号不能为空");
        if (StringUtils.isBlank(template.getRecordName())) throw new BusinessException("记录名称不能为空");
        if (StringUtils.isBlank(template.getVersion())) throw new BusinessException("版次不能为空");
        if (template.getRetentionYears() != null && template.getRetentionYears() <= 0) {
            throw new BusinessException("保存期限必须大于0");
        }
        if (!CATEGORY_BLANK.equals(template.getCategory()) && !CATEGORY_DATA.equals(template.getCategory())) {
            throw new BusinessException("模板类别不正确");
        }
        if (template.getStatus() != null && QualityTemplateStatusEnum.fromCode(template.getStatus()) == null) {
            throw new BusinessException("模板状态不正确");
        }
    }

    private void ensureRecordNoUnique(String recordNo, Long excludeId) {
        LambdaQueryWrapper<QualityTemplateRegistry> wrapper = new LambdaQueryWrapper<QualityTemplateRegistry>()
                .eq(QualityTemplateRegistry::getRecordNo, recordNo);
        if (excludeId != null) wrapper.ne(QualityTemplateRegistry::getId, excludeId);
        if (mapper.selectCount(wrapper) > 0) throw new BusinessException("记录编号已存在：" + recordNo);
    }
}
