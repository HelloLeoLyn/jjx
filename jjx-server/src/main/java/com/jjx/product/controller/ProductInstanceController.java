package com.jjx.product.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.product.domain.entity.ProductInstance;
import com.jjx.product.domain.query.ProductInstanceQuery;
import com.jjx.product.domain.vo.ProductInstanceVo;
import com.jjx.product.service.IProductInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品实例Controller
 */
@RestController
@RequestMapping("/product/instance")
@RequiredArgsConstructor
public class ProductInstanceController extends BaseController {

    private final IProductInstanceService IProductInstanceService;

    /**
     * 获取产品实例列表
     */
    @GetMapping("/list")
    public Result<PageResult<ProductInstanceVo>> list(ProductInstanceQuery query) {
        PageResult<ProductInstanceVo> result = IProductInstanceService.selectInstanceList(query, getPageNum(), getPageSize());
        return Result.success(result);
    }

    /**
     * 获取产品实例详情
     */
    @GetMapping("/{instanceId}")
    public Result<ProductInstanceVo> getInfo(@PathVariable Long instanceId) {
        ProductInstanceVo instance = IProductInstanceService.getInstanceDetail(instanceId);
        return Result.success(instance);
    }

    /**
     * 新增产品实例
     */
    @PostMapping
    public Result<Void> add(@Validated @RequestBody ProductInstance instance) {
        boolean result = IProductInstanceService.createInstance(instance);
        return result ? Result.success() : Result.error();
    }

    /**
     * 批量新增产品实例
     */
    @PostMapping("/batch")
    public Result<Void> batchAdd(@Validated @RequestBody List<ProductInstance> instances) {
        boolean result = IProductInstanceService.batchCreateInstances(instances);
        return result ? Result.success() : Result.error();
    }

    /**
     * 修改产品实例
     */
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody ProductInstance instance) {
        boolean result = IProductInstanceService.updateById(instance);
        return result ? Result.success() : Result.error();
    }

    /**
     * 删除产品实例
     */
    @DeleteMapping("/{instanceId}")
    public Result<Void> remove(@PathVariable Long instanceId) {
        boolean result = IProductInstanceService.removeById(instanceId);
        return result ? Result.success() : Result.error();
    }

    /**
     * 开始生产
     */
    @PutMapping("/startProduction/{instanceId}")
    public Result<Void> startProduction(@PathVariable Long instanceId) {
        boolean result = IProductInstanceService.startProduction(instanceId);
        return result ? Result.success() : Result.error();
    }

    /**
     * 完成生产
     */
    @PutMapping("/completeProduction/{instanceId}")
    public Result<Void> completeProduction(@PathVariable Long instanceId) {
        boolean result = IProductInstanceService.completeProduction(instanceId);
        return result ? Result.success() : Result.error();
    }

    /**
     * 交付产品
     */
    @PutMapping("/deliver/{instanceId}")
    public Result<Void> deliver(@PathVariable Long instanceId) {
        boolean result = IProductInstanceService.deliverInstance(instanceId);
        return result ? Result.success() : Result.error();
    }

    /**
     * 根据订单获取产品实例列表
     */
    @GetMapping("/byOrder/{orderId}")
    public Result<List<ProductInstance>> getByOrder(@PathVariable Long orderId) {
        List<ProductInstance> instances = IProductInstanceService.getInstancesByOrderId(orderId);
        return Result.success(instances);
    }

    /**
     * 根据产品获取产品实例列表
     */
    @GetMapping("/byProduct/{productId}")
    public Result<List<ProductInstance>> getByProduct(@PathVariable Long productId) {
        List<ProductInstance> instances = IProductInstanceService.getInstancesByProductId(productId);
        return Result.success(instances);
    }

    /**
     * 根据客户获取产品实例列表
     */
    @GetMapping("/byCustomer/{customerId}")
    public Result<List<ProductInstance>> getByCustomer(@PathVariable Long customerId) {
        List<ProductInstance> instances = IProductInstanceService.getInstancesByCustomerId(customerId);
        return Result.success(instances);
    }

    /**
     * 统计产品实例数量
     */
    @GetMapping("/count")
    public Result<Long> count(ProductInstanceQuery query) {
        long count = IProductInstanceService.countInstances(query);
        return Result.success(count);
    }
}
