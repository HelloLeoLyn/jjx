package com.jjx.product.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.product.domain.dto.ProductConfigDTO;
import com.jjx.product.domain.dto.ProductConfigRouteDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.ProductConfigResult;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper productMapper;
    private final IEngineeringBomService bomService;
    private final IEngineeringRoutingService routingService;

    public ProductConfigResult setBom(ProductConfigDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }
        // DEV-805：只能配置已审批通过的BOM（防止把当前配置指向未批准草稿，导致发布/转量产校验失败）
        EngineeringBom bom = bomService.getById(dto.getCurrentBomId());
        if (bom == null || !Objects.equals(product.getProductId(), bom.getProductId())) {
            throw new BusinessException("BOM不存在或不属于该产品");
        }
        if (!Objects.equals(ProductEnums.BomStatus.APPROVED.getValue(), bom.getApproveStatus())) {
            throw new BusinessException("只能配置已审批通过的BOM，请先提交审核并批准后再配置");
        }
        product.setCurrentBomId(dto.getCurrentBomId());
        productMapper.updateById(product);

        ProductConfigResult result = new ProductConfigResult();
        result.setProductId(dto.getProductId());
        result.setBomId(dto.getCurrentBomId());
        result.setSuccess(true);
        return result;
    }

    public ProductConfigResult setRoute(ProductConfigRouteDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }
        // DEV-805：只能配置已审批通过的工艺路线
        EngineeringRouting routing = routingService.getById(dto.getCurrentRouteId());
        if (routing == null || !Objects.equals(product.getProductId(), routing.getProductId())) {
            throw new BusinessException("工艺路线不存在或不属于该产品");
        }
        if (!Objects.equals(ProductEnums.RouteStatus.APPROVED.getValue(), routing.getApproveStatus())) {
            throw new BusinessException("只能配置已审批通过的工艺路线，请先提交审核并批准后再配置");
        }
        product.setCurrentRouteId(dto.getCurrentRouteId());
        productMapper.updateById(product);

        ProductConfigResult result = new ProductConfigResult();
        result.setProductId(dto.getProductId());
        result.setRouteId(dto.getCurrentRouteId());
        result.setSuccess(true);
        return result;
    }
}
