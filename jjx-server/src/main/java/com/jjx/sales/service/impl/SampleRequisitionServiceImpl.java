package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.SampleDefectRecordDTO;
import com.jjx.sales.domain.entity.SalesSampleDefectRecord;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import com.jjx.sales.domain.entity.SalesSampleRequisitionSign;
import com.jjx.sales.enums.SalesSampleCraftType;
import com.jjx.sales.enums.SalesSampleRequisitionSignRole;
import com.jjx.sales.mapper.SalesSampleDefectRecordMapper;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import com.jjx.sales.mapper.SalesSampleRequisitionSignMapper;
import com.jjx.sales.service.ISampleRequisitionService;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 样品需求单会签 + 打样不良记录服务实现（dev-20261010-028）
 */
@Service
@RequiredArgsConstructor
public class SampleRequisitionServiceImpl implements ISampleRequisitionService {

    private final SalesSampleRequisitionSignMapper signMapper;
    private final SalesSampleDefectRecordMapper defectMapper;
    private final SalesSampleOrderMapper sampleOrderMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SalesSampleRequisitionSign> listSigns(Long sampleOrderId) {
        return signMapper.selectByOrderId(sampleOrderId);
    }

    @Override
    @Transactional
    public SalesSampleRequisitionSign sign(Long sampleOrderId, String roleCode, Boolean approved, String comment) {
        if (sampleOrderId == null) {
            throw new BusinessException("样品单不能为空");
        }
        SalesSampleRequisitionSignRole role = SalesSampleRequisitionSignRole.of(roleCode);
        if (role == null) {
            throw new BusinessException("未知签字位: " + roleCode);
        }
        // 权限由权限点决定：谁有该签字位权限谁才能签（甲方案）
        if (!Boolean.TRUE.equals(SecurityUtils.hasPermission(role.getPermission()))) {
            throw new BusinessException("无该签字位的签署权限");
        }
        int round = currentRound(sampleOrderId);
        SalesSampleRequisitionSign existing = signMapper.selectOne(
                new LambdaQueryWrapper<SalesSampleRequisitionSign>()
                        .eq(SalesSampleRequisitionSign::getSampleOrderId, sampleOrderId)
                        .eq(SalesSampleRequisitionSign::getSignRole, role.getCode())
                        .eq(SalesSampleRequisitionSign::getRoundNo, round)
                        .last("LIMIT 1"));

        LocalDateTime now = LocalDateTime.now();
        SalesSampleRequisitionSign rec = existing != null ? existing : new SalesSampleRequisitionSign();
        rec.setSampleOrderId(sampleOrderId);
        rec.setRoundNo(round);
        rec.setSignRole(role.getCode());
        rec.setApproveResult(Boolean.TRUE.equals(approved) ? 1 : 0);
        rec.setComment(comment);
        rec.setSignerId(safeUserId());
        rec.setSignerName(displayName());
        rec.setSignTime(now);
        if (existing != null) {
            rec.setUpdateBy(safeUsername());
            signMapper.updateById(rec);
        } else {
            rec.setCreateBy(safeUsername());
            rec.setCreateTime(now);
            signMapper.insert(rec);
        }
        return rec;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesSampleDefectRecord> listDefects(Long sampleOrderId) {
        return defectMapper.selectByOrderId(sampleOrderId);
    }

    @Override
    @Transactional
    public SalesSampleDefectRecord addDefect(Long sampleOrderId, SampleDefectRecordDTO dto) {
        if (sampleOrderId == null) {
            throw new BusinessException("样品单不能为空");
        }
        if (dto == null) {
            throw new BusinessException("参数不能为空");
        }
        SalesSampleCraftType craft = SalesSampleCraftType.of(dto.getCraftType());
        if (craft == null) {
            throw new BusinessException("制样类别不合法");
        }
        LocalDateTime now = LocalDateTime.now();
        SalesSampleDefectRecord rec = new SalesSampleDefectRecord();
        rec.setSampleOrderId(sampleOrderId);
        rec.setRoundNo(dto.getRoundNo() != null ? dto.getRoundNo() : currentRound(sampleOrderId));
        rec.setCraftType(craft.getCode());
        rec.setDefectReason(dto.getDefectReason());
        rec.setImprovement(dto.getImprovement());
        rec.setRecordDate(parseDate(dto.getRecordDate()));
        rec.setRecorderId(safeUserId());
        rec.setRecorderName(displayName());
        rec.setCreateBy(safeUsername());
        rec.setCreateTime(now);
        rec.setDeleted(0);
        defectMapper.insert(rec);
        return rec;
    }

    @Override
    @Transactional
    public void deleteDefect(Long id) {
        if (id == null) {
            throw new BusinessException("记录ID不能为空");
        }
        defectMapper.deleteById(id);
    }

    // ==================== 内部工具 ====================

    /** 样品单当前轮次（取不到默认 1） */
    private int currentRound(Long orderId) {
        SalesSampleOrder so = sampleOrderMapper.selectOne(
                new LambdaQueryWrapper<SalesSampleOrder>()
                        .eq(SalesSampleOrder::getOrderId, orderId)
                        .last("LIMIT 1"));
        return so != null && so.getSampleRound() != null ? so.getSampleRound() : 1;
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return LocalDate.now();
        }
        return LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private Long safeUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeUsername() {
        try {
            return SecurityUtils.getUsername();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String displayName() {
        try {
            String realName = SecurityUtils.getRealName();
            if (realName != null && !realName.isBlank()) {
                return realName;
            }
            return SecurityUtils.getUsername();
        } catch (Exception ignored) {
            return null;
        }
    }
}
