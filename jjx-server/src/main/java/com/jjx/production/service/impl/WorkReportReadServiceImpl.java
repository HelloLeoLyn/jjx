package com.jjx.production.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionWorkReport;
import com.jjx.production.domain.vo.WorkReportVO;
import com.jjx.production.enums.WorkReportStatusEnum;
import com.jjx.production.mapper.ProductionWorkReportMapper;
import com.jjx.production.service.WorkReportReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 生产报工读取服务实现（P2-B：只读）
 * 时间排序：reportTime DESC → createTime DESC → reportId DESC（Execution 报工历史 Drawer 默认）
 */
@Service
@RequiredArgsConstructor
public class WorkReportReadServiceImpl implements WorkReportReadService {

    private final ProductionWorkReportMapper workReportMapper;

    @Override
    public WorkReportVO getById(Long reportId) {
        ProductionWorkReport e = workReportMapper.selectById(reportId);
        if (e == null) throw new BusinessException("报工记录不存在");
        return toVO(e);
    }

    @Override
    public List<WorkReportVO> listByExecutionId(Long executionId) {
        return workReportMapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                        .eq(ProductionWorkReport::getExecutionId, executionId)
                        .orderByDesc(ProductionWorkReport::getReportTime)
                        .orderByDesc(ProductionWorkReport::getCreateTime)
                        .orderByDesc(ProductionWorkReport::getReportId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<WorkReportVO> listSubmittedByExecutionId(Long executionId) {
        return workReportMapper.selectList(Wrappers.<ProductionWorkReport>lambdaQuery()
                        .eq(ProductionWorkReport::getExecutionId, executionId)
                        .eq(ProductionWorkReport::getReportStatus, WorkReportStatusEnum.SUBMITTED.getCode())
                        .orderByDesc(ProductionWorkReport::getReportTime)
                        .orderByDesc(ProductionWorkReport::getReportId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    private WorkReportVO toVO(ProductionWorkReport e) {
        WorkReportVO vo = new WorkReportVO();
        vo.setReportId(e.getReportId());
        vo.setOrderId(e.getOrderId());
        vo.setOrderNo(e.getOrderNo());
        vo.setExecutionId(e.getExecutionId());
        vo.setReporterId(e.getReporterId());
        vo.setReporterName(e.getReporterName());
        vo.setEquipmentId(e.getEquipmentId());
        vo.setEquipmentName(e.getEquipmentName());
        vo.setQualifiedQuantity(e.getQualifiedQuantity());
        vo.setDefectiveQuantity(e.getDefectiveQuantity());
        vo.setLaborHours(e.getLaborHours());
        vo.setMachineHours(e.getMachineHours());
        vo.setWorkStartTime(e.getWorkStartTime());
        vo.setWorkEndTime(e.getWorkEndTime());
        vo.setReportTime(e.getReportTime());
        vo.setDefectReason(e.getDefectReason());
        vo.setRemark(e.getRemark());
        vo.setReportStatus(e.getReportStatus());
        vo.setReportStatusLabel(WorkReportStatusEnum.labelOf(e.getReportStatus()));
        vo.setCancelledByName(e.getCancelledByName());
        vo.setCancelledAt(e.getCancelledAt());
        vo.setCancelReason(e.getCancelReason());
        return vo;
    }
}
