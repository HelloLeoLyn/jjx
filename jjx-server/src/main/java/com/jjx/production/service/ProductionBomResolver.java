package com.jjx.production.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.production.domain.entity.ProductionOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 生产用料统一按工单绑定的已批准 BOM 版本取数，不随产品当前版本切换。 */
@Service
@RequiredArgsConstructor
public class ProductionBomResolver {
    private final EngineeringBomMapper bomMapper;

    public EngineeringBom resolve(ProductionOrder order) {
        if (order.getBomId() == null) {
            // 兼容未绑定版本的历史工单；不推断、不回填其历史 BOM。
            return bomMapper.selectOne(new LambdaQueryWrapper<EngineeringBom>()
                    .eq(EngineeringBom::getProductId, order.getProductId())
                    .eq(EngineeringBom::getIsCurrent, YesNoEnum.YES.getCode())
                    .eq(EngineeringBom::getApproveStatus, ProductEnums.BomStatus.APPROVED.getValue())
                    .orderByDesc(EngineeringBom::getCreateTime)
                    .last("LIMIT 1"));
        }
        EngineeringBom bom = bomMapper.selectById(order.getBomId());
        if (bom == null) {
            throw new BusinessException("生产单[" + order.getOrderNo() + "]绑定的BOM不存在，请核查版本关联");
        }
        if (!Objects.equals(order.getProductId(), bom.getProductId())) {
            throw new BusinessException("生产单[" + order.getOrderNo() + "]绑定的BOM与产品不一致");
        }
        if (!ProductEnums.BomStatus.APPROVED.getValue().equals(bom.getApproveStatus())) {
            throw new BusinessException("生产单[" + order.getOrderNo() + "]绑定的BOM未批准或已作废，请核查版本");
        }
        return bom;
    }
}
