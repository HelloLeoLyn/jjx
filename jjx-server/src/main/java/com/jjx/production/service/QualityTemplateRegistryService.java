package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.QualityTemplateQueryDTO;
import com.jjx.production.domain.entity.QualityTemplateRegistry;

import java.util.List;

public interface QualityTemplateRegistryService {
    PageResult<QualityTemplateRegistry> page(QualityTemplateQueryDTO query);
    QualityTemplateRegistry getById(Long id);
    List<String> listOwnerDepts();
    void recordPrint(Long id);
    Long create(QualityTemplateRegistry template);
    void update(QualityTemplateRegistry template);
    void changeStatus(Long id, Integer status);
    void delete(Long id);
}
