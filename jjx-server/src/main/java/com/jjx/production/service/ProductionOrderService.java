package com.jjx.production.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.ConvertPlanToWorkOrdersDTO;
import com.jjx.production.domain.dto.ProductionOrderCreateDTO;
import com.jjx.production.domain.dto.ProductionOrderQueryDTO;
import com.jjx.production.domain.dto.ProductionOrderUpdateDTO;
import com.jjx.production.domain.vo.OrderStatisticsVO;
import com.jjx.production.domain.vo.ProductionOrderVO;

import java.util.List;

/**
 * 生产工单服务接口
 */
public interface ProductionOrderService {

    /**
     * 创建生产工单
     */
    Long createOrder(ProductionOrderCreateDTO createDTO);

    /**
     * 更新生产工单
     */
    boolean updateOrder(ProductionOrderUpdateDTO updateDTO);

    /**
     * 删除生产工单
     */
    boolean deleteOrder(Long orderId);

    /**
     * 批量删除生产工单
     */
    boolean batchDeleteOrder(List<Long> orderIds);

    /**
     * 根据ID获取生产工单详情
     */
    ProductionOrderVO getOrderById(Long orderId);

    /**
     * 根据编码获取生产工单详情
     */
    ProductionOrderVO getOrderByCode(String orderCode);

    /**
     * 查询生产工单列表
     */
    List<ProductionOrderVO> queryOrderList(ProductionOrderQueryDTO queryDTO);

    /**
     * 分页查询生产工单
     */
    Page<ProductionOrderVO> queryOrderPage(ProductionOrderQueryDTO queryDTO);

    /**
     * 启动生产工单
     */
    boolean startOrder(Long orderId);

    /**
     * 暂停生产工单
     */
    boolean pauseOrder(Long orderId);

    /**
     * 完成生产工单
     */
    boolean completeOrder(Long orderId);

    /**
     * 取消生产工单
     */
    boolean cancelOrder(Long orderId);

    /**
     * 关闭生产工单
     */
    boolean closeOrder(Long orderId);

    /**
     * 检查工单编码是否存在
     */
    boolean checkOrderCodeExists(String orderCode);

    /**
     * 根据产品ID查询生产工单
     */
    List<ProductionOrderVO> getOrdersByProductId(Long productId);

    /**
     * 根据工艺路线ID查询生产工单
     */
    List<ProductionOrderVO> getOrdersByRoutingId(Long routingId);

    /**
     * 复制生产工单
     */
    Long copyOrder(Long sourceOrderId, String targetOrderCode, String targetOrderName);

    /**
     * 导入生产工单数据
     */
    Result importOrderData(List<ProductionOrderCreateDTO> importData);

    /**
     * 导出生产工单数据
     */
    List<ProductionOrderVO> exportOrderData(ProductionOrderQueryDTO queryDTO);

    /**
     * 获取生产工单统计信息
     */
    OrderStatisticsVO getOrderStatistics(ProductionOrderQueryDTO queryDTO);

    /**
     * 计划转工单
     */
    List<Long> convertPlanToWorkOrders(ConvertPlanToWorkOrdersDTO dto);

    /**
     * 更新订单状态（通用）
     */
    boolean updateOrderStatus(Long orderId, Integer newStatus, String remark);

    /**
     * 批量更新订单状态
     */
    boolean batchUpdateOrderStatus(List<Long> orderIds, Integer newStatus, String remark);
}
