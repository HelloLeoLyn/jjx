package com.jjx.quality.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.quality.domain.entity.QualityCapa;
import com.jjx.quality.domain.entity.QualityNcr;
import com.jjx.quality.mapper.QualityCapaMapper;
import com.jjx.quality.mapper.QualityNcrMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class QualityCapaService {
    private final QualityCapaMapper capaMapper;
    private final QualityNcrMapper ncrMapper;
    private final RedisSequenceService redisSequenceService;

    public Page<QualityCapa> page(long pageNum, long pageSize, String status, Long ncrId) {
        return capaMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<QualityCapa>()
                .eq(status != null && !status.isBlank(), QualityCapa::getStatus, status)
                .eq(ncrId != null, QualityCapa::getNcrId, ncrId)
                .orderByDesc(QualityCapa::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityCapa create(QualityCapa capa) {
        QualityNcr ncr = capa.getNcrId() == null ? null : ncrMapper.selectById(capa.getNcrId());
        if (ncr == null) throw new BusinessException("关联的不良单不存在");
        capa.setCapaNo(redisSequenceService.generateBusinessNumberByType("quality_capa", "CAPA", "yyMMdd", 3));
        capa.setStatus("PENDING_ANALYSIS");
        capa.setDelFlag(0);
        capaMapper.insert(capa);
        if ("CLOSED".equals(ncr.getStatus())) {
            ncr.setStatus("DISPOSING");
            ncrMapper.updateById(ncr);
        }
        return capa;
    }

    @Transactional(rollbackFor = Exception.class)
    public QualityCapa advance(Long id, QualityCapa input) {
        QualityCapa capa = capaMapper.selectById(id);
        if (capa == null) throw new BusinessException("CAPA 不存在");
        switch (capa.getStatus()) {
            case "PENDING_ANALYSIS" -> {
                if (blank(input.getRootCause()) || blank(input.getActionPlan())) throw new BusinessException("请填写根因和措施计划");
                capa.setRootCauseCategory(input.getRootCauseCategory()); capa.setRootCause(input.getRootCause());
                capa.setActionPlan(input.getActionPlan()); capa.setOwnerId(input.getOwnerId());
                capa.setOwnerName(input.getOwnerName()); capa.setDueDate(input.getDueDate());
                capa.setStatus("ACTION_IN_PROGRESS");
            }
            case "ACTION_IN_PROGRESS" -> capa.setStatus("PENDING_VERIFICATION");
            case "PENDING_VERIFICATION" -> {
                if (blank(input.getVerificationResult())) throw new BusinessException("关闭前必须填写验证结论");
                capa.setVerificationResult(input.getVerificationResult()); capa.setStatus("CLOSED"); capa.setClosedTime(LocalDateTime.now());
                QualityNcr ncr = ncrMapper.selectById(capa.getNcrId());
                if (ncr != null && ncr.pendingQuantity().signum() == 0 && countOpen(capa.getNcrId(), id) == 0) {
                    ncr.setStatus("CLOSED"); ncrMapper.updateById(ncr);
                }
            }
            default -> throw new BusinessException("CAPA 已关闭，不能继续推进");
        }
        capaMapper.updateById(capa);
        return capa;
    }

    public long countOpen(Long ncrId, Long excludeId) {
        return capaMapper.selectCount(new LambdaQueryWrapper<QualityCapa>().eq(QualityCapa::getNcrId, ncrId)
                .ne(QualityCapa::getStatus, "CLOSED").ne(excludeId != null, QualityCapa::getCapaId, excludeId));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
