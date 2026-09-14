package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.QualityTemplatePrintLog;
import com.jjx.production.domain.entity.QualityTemplateRegistry;
import com.jjx.production.mapper.QualityTemplatePrintLogMapper;
import com.jjx.production.mapper.QualityTemplateRegistryMapper;
import com.jjx.sales.domain.dto.SalesDeliveryQueryDTO;
import com.jjx.sales.domain.entity.SalesDelivery;
import com.jjx.sales.domain.vo.SalesDeliveryVO;
import com.jjx.sales.mapper.SalesDeliveryMapper;
import com.jjx.sales.service.ISalesDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jjx.system.utils.SecurityUtils;

import java.util.List;
import java.util.Date;
import java.util.Map;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 销售发货单服务实现
 */
@Service
@RequiredArgsConstructor
public class SalesDeliveryServiceImpl implements ISalesDeliveryService {

    private static final String[] DELIVERY_STATUS_DESC = {"未知", "待发货", "已发货", "运输中", "已签收", "已拒收"};

    private final SalesDeliveryMapper salesDeliveryMapper;

    /** 打印留痕（口径 D3）：复用 production 包既有实体/Mapper，不另建表映射 */
    private final QualityTemplatePrintLogMapper printLogMapper;
    private final QualityTemplateRegistryMapper templateRegistryMapper;

    private static final String PRINT_BIZ_TYPE = "sales_delivery";
    /** quality_template_registry.status：1=生效 */
    private static final Integer TEMPLATE_STATUS_ACTIVE = 1;

    @Override
    public Page<SalesDeliveryVO> pageQuery(SalesDeliveryQueryDTO dto) {
        LambdaQueryWrapper<SalesDelivery> wrapper = new LambdaQueryWrapper<>();
        if (dto.getOrderId() != null) {
            wrapper.eq(SalesDelivery::getOrderId, dto.getOrderId());
        }
        if (dto.getDeliveryNo() != null && !dto.getDeliveryNo().isEmpty()) {
            wrapper.like(SalesDelivery::getDeliveryNo, dto.getDeliveryNo());
        }
        if (dto.getCustomerName() != null && !dto.getCustomerName().isEmpty()) {
            wrapper.like(SalesDelivery::getCustomerName, dto.getCustomerName());
        }
        if (dto.getDeliveryStatus() != null) {
            wrapper.eq(SalesDelivery::getDeliveryStatus, dto.getDeliveryStatus());
        }
        if (dto.getDeliveryDateStart() != null) {
            wrapper.ge(SalesDelivery::getDeliveryDate, dto.getDeliveryDateStart());
        }
        if (dto.getDeliveryDateEnd() != null) {
            wrapper.le(SalesDelivery::getDeliveryDate, dto.getDeliveryDateEnd());
        }
        wrapper.orderByDesc(SalesDelivery::getCreateTime).orderByDesc(SalesDelivery::getDeliveryId);

        Page<SalesDelivery> page = salesDeliveryMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);

        Page<SalesDeliveryVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<SalesDeliveryVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        fillPrintInfo(records);
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public SalesDeliveryVO getById(Long deliveryId) {
        SalesDelivery entity = salesDeliveryMapper.selectById(deliveryId);
        return entity != null ? toVO(entity) : null;
    }

    @Override
    public List<SalesDeliveryVO> listByOrderId(Long orderId) {
        LambdaQueryWrapper<SalesDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SalesDelivery::getOrderId, orderId)
               .orderByDesc(SalesDelivery::getCreateTime);
        return salesDeliveryMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long deliveryId, SalesDelivery receiveInfo) {
        SalesDelivery current = salesDeliveryMapper.selectById(deliveryId);
        if (current == null) {
            throw new BusinessException("发货单不存在");
        }
        if (Integer.valueOf(4).equals(current.getDeliveryStatus())) {
            throw new BusinessException("发货单已签收，请勿重复操作");
        }
        SalesDelivery update = new SalesDelivery();
        update.setDeliveryId(deliveryId);
        update.setReceiverName(receiveInfo == null ? null : receiveInfo.getReceiverName());
        update.setReceiverPhone(receiveInfo == null ? null : receiveInfo.getReceiverPhone());
        update.setReceiveRemark(receiveInfo == null ? null : receiveInfo.getReceiveRemark());
        update.setReceiveTime(new Date());
        update.setReceiveBy(SecurityUtils.getUserId());
        String receiveName = SecurityUtils.getRealName();
        update.setReceiveName(receiveName == null || receiveName.isBlank()
                ? SecurityUtils.getUsername() : receiveName);
        update.setDeliveryStatus(4);
        if (salesDeliveryMapper.updateById(update) <= 0) {
            throw new BusinessException("签收失败，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPrintLog(Long deliveryId) {
        SalesDelivery delivery = salesDeliveryMapper.selectById(deliveryId);
        if (delivery == null) {
            throw new BusinessException("发货单不存在");
        }
        // 口径 D3：走 1237 的打印留痕机制（quality_template_print_log 按 biz_type+biz_id 记账）。
        // 该表 template_id 为 NOT NULL，所以先按 biz_type 解析生效模板；没有登记模板则不留痕（不阻断打印）。
        LambdaQueryWrapper<QualityTemplateRegistry> tw = new LambdaQueryWrapper<>();
        tw.eq(QualityTemplateRegistry::getBizType, PRINT_BIZ_TYPE)
          .eq(QualityTemplateRegistry::getStatus, TEMPLATE_STATUS_ACTIVE)
          .orderByAsc(QualityTemplateRegistry::getId)
          .last("LIMIT 1");
        QualityTemplateRegistry template = templateRegistryMapper.selectOne(tw);
        if (template == null) {
            return;
        }
        QualityTemplatePrintLog log = new QualityTemplatePrintLog();
        log.setTemplateId(template.getId());
        log.setRecordNo(template.getRecordNo());
        log.setBizType(PRINT_BIZ_TYPE);
        log.setBizId(deliveryId);
        log.setOperatorId(SecurityUtils.getUserId());
        String realName = SecurityUtils.getRealName();
        log.setOperatorName(realName == null || realName.isBlank() ? SecurityUtils.getUsername() : realName);
        log.setPrintTime(LocalDateTime.now());
        printLogMapper.insert(log);
    }

    /** 批量回填打印次数/最近打印人（一次查询，避免逐行查） */
    private void fillPrintInfo(List<SalesDeliveryVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> ids = vos.stream().map(SalesDeliveryVO::getDeliveryId)
                .filter(id -> id != null).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<QualityTemplatePrintLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QualityTemplatePrintLog::getBizType, PRINT_BIZ_TYPE)
               .in(QualityTemplatePrintLog::getBizId, ids)
               .orderByAsc(QualityTemplatePrintLog::getPrintTime);
        Map<Long, List<QualityTemplatePrintLog>> grouped = printLogMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(QualityTemplatePrintLog::getBizId));
        for (SalesDeliveryVO vo : vos) {
            List<QualityTemplatePrintLog> logs = grouped.get(vo.getDeliveryId());
            if (logs == null || logs.isEmpty()) {
                vo.setPrintCount(0);
                continue;
            }
            vo.setPrintCount(logs.size());
            QualityTemplatePrintLog last = logs.get(logs.size() - 1);
            vo.setLastPrintBy(last.getOperatorName());
            vo.setLastPrintTime(last.getPrintTime() == null ? null : Timestamp.valueOf(last.getPrintTime()));
        }
    }

    private SalesDeliveryVO toVO(SalesDelivery entity) {
        SalesDeliveryVO vo = new SalesDeliveryVO();
        BeanUtils.copyProperties(entity, vo);
        int idx = entity.getDeliveryStatus() != null ? entity.getDeliveryStatus() : 0;
        if (idx >= 0 && idx < DELIVERY_STATUS_DESC.length) {
            vo.setDeliveryStatusDesc(DELIVERY_STATUS_DESC[idx]);
        }
        return vo;
    }

}
