package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.product.domain.dto.ProductRoutingDTO;
import com.jjx.product.domain.dto.ProductRoutingQueryDTO;
import com.jjx.product.domain.entity.ProductRouting;
import com.jjx.product.domain.vo.ProductRoutingVO;

import java.util.List;
import java.util.Map;

public interface IProductRoutingService extends IService<ProductRouting> {

    /**
     * 创建工艺路线
     */
    ProductRoutingVO createRouting(ProductRoutingDTO dto);

    /**
     * 更新工艺路线
     */
    ProductRoutingVO updateRouting(ProductRoutingDTO dto);

    /**
     * 复制工艺路线为新版本
     */
    ProductRoutingVO copyAsNewVersion(Long routingId, String newVersion);

    /**
     * 设置当前版本
     */
    void setCurrentVersion(Long routingId);

    /**
     * 提交审批
     */
    void submitApprove(Long routingId);

    /**
     * 审批通过
     */
    void approve(Long routingId, String remark);

    /**
     * 审批驳回
     */
    void reject(Long routingId, String remark);

    /**
     * 获取产品当前工艺路线
     */
    ProductRoutingVO getCurrentByProductId(Long productId);

    /**
     * 获取产品所有版本工艺路线
     */
    List<ProductRoutingVO> getAllVersionsByProductId(Long productId);

    /**
     * 获取工艺路线详情（包含明细）
     */
    ProductRoutingVO getRoutingItems(Long routingId);

    /**
     * 计算工时
     */
    void calculateHours(Long routingId);

    PageResult<ProductRoutingVO> pageQuery(ProductRoutingQueryDTO queryDTO);

}
