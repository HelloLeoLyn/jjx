package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.product.domain.dto.EngineeringRoutingDTO;
import com.jjx.product.domain.dto.EngineeringRoutingQueryDTO;
import com.jjx.product.domain.entity.EngineeringRouting;
import com.jjx.product.domain.vo.EngineeringRoutingVO;

import java.util.List;
import java.util.Map;

public interface IEngineeringRoutingService extends IService<EngineeringRouting> {

    /**
     * 创建工艺路线
     */
    EngineeringRoutingVO createRouting(EngineeringRoutingDTO dto);

    /**
     * 更新工艺路线
     */
    EngineeringRoutingVO updateRouting(EngineeringRoutingDTO dto);

    /**
     * 复制工艺路线为新版本
     */
    EngineeringRoutingVO copyAsNewVersion(Long routingId, String newVersion);

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
    EngineeringRoutingVO getCurrentByProductId(Long productId);

    /**
     * 获取产品所有版本工艺路线
     */
    List<EngineeringRoutingVO> getAllVersionsByProductId(Long productId);

    /**
     * 获取工艺路线详情（包含明细）
     */
    EngineeringRoutingVO getRoutingItems(Long routingId);

    /**
     * 计算工时
     */
    void calculateHours(Long routingId);

    PageResult<EngineeringRoutingVO> pageQuery(EngineeringRoutingQueryDTO queryDTO);

}
