package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.product.domain.dto.ProductConfigDTO;
import com.jjx.product.domain.dto.ProductConfigRouteDTO;
import com.jjx.product.domain.vo.ProductConfigResult;
import com.jjx.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产品配置Controller
 */
@RestController
@RequestMapping("/product/config")
@RequiredArgsConstructor
public class ProductConfigController extends BaseController {
    private final ProductService configService;

    /**
     * 配置BOM
     */
    @PostMapping("/bom")
    public Result<ProductConfigResult> bom(@Validated @RequestBody ProductConfigDTO dto) {
        return Result.success(configService.setBom(dto));
    }

    /**
     * 配置工艺路线
     */
    @PostMapping("/route")
    public Result<ProductConfigResult> route(@Validated @RequestBody ProductConfigRouteDTO dto) {
        return Result.success(configService.setRoute(dto));
    }
}
