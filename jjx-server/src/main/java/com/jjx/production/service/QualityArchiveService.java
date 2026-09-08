package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.QualityArchiveQueryDTO;
import com.jjx.production.domain.vo.QualityArchiveVO;

public interface QualityArchiveService {
    PageResult<QualityArchiveVO> page(QualityArchiveQueryDTO query);
}
