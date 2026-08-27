package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.product.domain.entity.ProductInstance;
import com.jjx.product.domain.query.ProductInstanceQuery;
import com.jjx.product.domain.vo.ProductInstanceVo;

import java.util.List;

/**
 * 产品实例Service接口
 */
public interface IProductInstanceService extends IService<ProductInstance> {

    /**
     * 获取产品实例列表
     */
    List<ProductInstanceVo> getInstanceList(ProductInstanceQuery query);
    /**
     * 根据条件分页查询产品实例
     * @param query 产品实例
     * @return 产品实例集合信息
     */
    PageResult<ProductInstanceVo> selectInstanceList(ProductInstanceQuery query, Integer pageNum, Integer pageSize);

    /**
     * 获取产品实例详情
     */
    ProductInstanceVo getInstanceDetail(Long instanceId);

    /**
     * 创建产品实例
     */
    boolean createInstance(ProductInstance instance);

    /**
     * 批量创建产品实例
     */
    boolean batchCreateInstances(List<ProductInstance> instances);

    /**
     * 更新产品实例状态
     */
    boolean updateInstanceStatus(Long instanceId, Integer status);

    /**
     * 开始生产
     */
    boolean startProduction(Long instanceId);

    /**
     * 完成生产
     */
    boolean completeProduction(Long instanceId);

    /**
     * 交付产品
     */
    boolean deliverInstance(Long instanceId);

    /**
     * 根据订单获取产品实例列表
     */
    List<ProductInstance> getInstancesByOrderId(Long orderId);

    /**
     * 根据产品获取产品实例列表
     */
    List<ProductInstance> getInstancesByProductId(Long productId);

    /**
     * 根据客户获取产品实例列表
     */
    List<ProductInstance> getInstancesByCustomerId(Long customerId);

    /**
     * 统计产品实例数量
     */
    long countInstances(ProductInstanceQuery query);
}
