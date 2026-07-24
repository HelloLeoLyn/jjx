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
}
