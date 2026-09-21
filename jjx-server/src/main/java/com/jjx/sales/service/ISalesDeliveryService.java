package com.jjx.sales.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.sales.domain.dto.SalesDeliveryQueryDTO;
import com.jjx.sales.domain.entity.SalesDelivery;
import com.jjx.sales.domain.vo.SalesDeliveryVO;

import java.util.List;

/**
 * 销售发货单服务接口
 */
public interface ISalesDeliveryService {

    /**
     * 分页查询发货单
     */
    Page<SalesDeliveryVO> pageQuery(SalesDeliveryQueryDTO dto);

    /**
     * 根据ID查询发货单
     */
    SalesDeliveryVO getById(Long deliveryId);

    /**
     * 根据销售订单ID查询发货单
     */
    List<SalesDeliveryVO> listByOrderId(Long orderId);

    /** 签收发货单 */
    void receive(Long deliveryId, SalesDelivery receiveInfo);

    /**
     * 记录送货单打印留痕
     * 口径（D3）：biz_type='sales_delivery'、biz_id=deliveryId，写 quality_template_print_log
     */
    void recordPrintLog(Long deliveryId);

    /**
     * 导出送货单PDF（单张表单）
     */

    /**
     * 客户拒收登记（自动回冲库存 + 订单可重发 + 通知销售），2026-09-21 dev-20260921-039
     */
    void reject(Long deliveryId, String reason);
}
