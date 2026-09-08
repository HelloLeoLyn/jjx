package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.QualityArchiveQueryDTO;
import com.jjx.production.domain.vo.QualityArchiveVO;
import com.jjx.production.mapper.QualityArchiveMapper;
import com.jjx.production.service.QualityArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QualityArchiveServiceImpl implements QualityArchiveService {
    private final QualityArchiveMapper mapper;

    @Override
    public PageResult<QualityArchiveVO> page(QualityArchiveQueryDTO query) {
        Page<QualityArchiveVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        mapper.selectArchivePage(page, query);
        return PageResult.of(page, page.getRecords());
    }
}
