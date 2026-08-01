package com.jjx.engineering.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.Bom;
import com.jjx.engineering.mapper.BomMapper;
import com.jjx.engineering.service.IBomService;
import com.jjx.system.annotation.Event;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomServiceImpl extends ServiceImpl<BomMapper, Bom> implements IBomService {

    @Override
    public PageResult<?> listPage(Object query) {
        LambdaQueryWrapper<Bom> wrapper = new LambdaQueryWrapper<Bom>()
                .orderByDesc(Bom::getBomId);
        // 关联产品名通过 join 在 VO 层处理，这里先返回全部
        java.util.List<Bom> list = list(wrapper);
        return PageResult.build(list, list.size());
    }

    @Override
    @Event(value = "bom.submitted", bizId = "#bomId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void submitApprove(Long bomId) {
        Bom bom = getById(bomId);
        if (bom == null) {
            throw new BusinessException("BOM不存在");
        }
        if (bom.getApproveStatus() != null && bom.getApproveStatus() == 3) {
            throw new BusinessException("BOM已批准，不能重复提交");
        }
        bom.setApproveStatus(2L); // PENDING
        bom.setUpdateBy(SecurityUtils.getUsername());
        updateById(bom);
        log.info("BOM[{}] 提交审核 → PENDING", bom.getBomCode());
    }

    @Override
    @Event(value = "bom.approved", bizId = "#bomId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long bomId, String remark) {
        Bom bom = getById(bomId);
        if (bom == null) {
            throw new BusinessException("BOM不存在");
        }
        if (bom.getApproveStatus() == null || bom.getApproveStatus() != 2) {
            throw new BusinessException("只有待审批(PENDING)的BOM可以审核通过");
        }
        bom.setApproveStatus(3L); // APPROVED
        bom.setApproveBy(SecurityUtils.getUsername());
        bom.setApproveTime(LocalDateTime.now());
        bom.setApproveRemark(remark);
        bom.setUpdateBy(SecurityUtils.getUsername());
        updateById(bom);
        log.info("BOM[{}] 审核通过 → APPROVED", bom.getBomCode());
    }

    @Override
    @Event(value = "bom.rejected", bizId = "#bomId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long bomId, String remark) {
        Bom bom = getById(bomId);
        if (bom == null) {
            throw new BusinessException("BOM不存在");
        }
        if (bom.getApproveStatus() == null || bom.getApproveStatus() != 2) {
            throw new BusinessException("只有待审批(PENDING)的BOM可以审核驳回");
        }
        bom.setApproveStatus(4L); // REJECTED
        bom.setApproveBy(SecurityUtils.getUsername());
        bom.setApproveTime(LocalDateTime.now());
        bom.setApproveRemark(remark);
        bom.setUpdateBy(SecurityUtils.getUsername());
        updateById(bom);
        log.info("BOM[{}] 审核驳回 → REJECTED", bom.getBomCode());
    }
}
