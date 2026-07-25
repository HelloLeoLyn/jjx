package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.QualityInspectionCreateDTO;
import com.jjx.production.domain.dto.QualityInspectionQueryDTO;
import com.jjx.production.domain.dto.QualityInspectionUpdateDTO;
import com.jjx.production.domain.vo.QualityInspectionVO;

public interface QualityInspectionService {
    PageResult<QualityInspectionVO> page(QualityInspectionQueryDTO query);
    QualityInspectionVO getById(Long id);
    Long create(QualityInspectionCreateDTO dto);
    void update(QualityInspectionUpdateDTO dto);
    void delete(Long id);
    Object getStatistics();
}
