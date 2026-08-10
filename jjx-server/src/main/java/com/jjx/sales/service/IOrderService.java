package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.domain.dto.SalesOrderEditDTO;
import com.jjx.sales.domain.dto.SalesOrderQueryDTO;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.vo.OrderReferValidationVO;
import com.jjx.sales.domain.vo.SalesOrderVO;

import java.util.List;

/**
 * 销售订单服务接口
 * 提供销售订单的业务逻辑操作
 */
public interface IOrderService {

    /**
     * 查询销售订单列表
     *
     * @param order 销售订单查询条件
     * @return 销售订单列表
     */
    PageResult<SalesOrderVO> pageQuery(SalesOrderQueryDTO order);

    /**
     * 查询销售订单列表
     *
     * @param order 销售订单查询条件
     * @return 销售订单列表
     */
    List<SalesOrderVO> getOrderList(SalesOrderQueryDTO order);

    /**
     * 根据ID查询销售订单
     *
     * @param orderId 订单ID
     * @return 销售订单
     */
    SalesOrderVO selectOrderById(Long orderId);

    /**
     * 新增销售订单
     *
     * @param order 销售订单
     * @return 结果
     */
    Long insertOrder(SalesOrderAddDTO order);

    /**
     * 修改销售订单
     *
     * @param order 销售订单
     * @return 结果
     */
    boolean updateOrder(SalesOrderEditDTO order);

    /**
     * 删除销售订单
     *
     * @param orderId 订单ID
     * @return 结果
     */
    int deleteOrderById(Long orderId);

    /**
     * 批量删除销售订单
     *
     * @param orderIds 需要删除的订单ID数组
     * @return 结果
     */
    int deleteOrderByIds(Long[] orderIds);

    /**
     * 生成订单号
     *
     * @return 生成的订单号
     */
    String generateOrderNo();

    /**
     * 复制订单（终态订单如已取消/已完成可一键生成新草稿单）
     *
     * @param orderId 源订单ID
     * @return 新订单ID
     */
    Long copyOrder(Long orderId);

    /**
     * 检查订单号是否存在
     *
     * @param orderNo 订单号
     * @return 是否存在
     */
    boolean checkOrderNoUnique(String orderNo);

    /**
     * 更新订单状态
     *
     * @param orderId 订单ID
     * @param status 订单状态
     * @return 结果
     */
    int updateOrderStatus(Long orderId, Integer status);

    /**
     * 审核订单
     *
     * @param orderId 订单ID
     * @param approverId 审核人ID
     * @param approverName 审核人姓名
     * @param approveRemark 审核备注
     * @return 结果
     */
    int approveOrder(Long orderId, Long approverId, String approverName, String approveRemark);

    /**
     * 更新付款信息
     *
     * @param orderId 订单ID
     * @param paidAmount 已付金额
     * @return 结果
     */
    int updatePaymentInfo(Long orderId, Double paidAmount);

    /**
     * 根据客户ID查询订单列表
     *
     * @param customerId 客户ID
     * @return 订单列表
     */
    List<SalesOrder> selectOrdersByCustomerId(Long customerId);

    /**
     * 根据报价单ID查询订单
     *
     * @param quotationId 报价单ID
     * @return 订单
     */
    SalesOrder selectOrderByQuotationId(Long quotationId);

    /**
     * 提交审核
     *
     * @return 结果
     */
    int submitReview(Long orderId);

    /**
     * 客户确认订单
     *
     * @param orderId 订单ID
     * @param confirmedBy 确认人
     * @return 结果
     */
    int confirmOrder(Long orderId, String confirmedBy);

    /**
     * 创建产品实例
     *
     * @param orderId 订单ID
     * @return 结果
     */
    int createInstances(Long orderId);

    /**
     * 导出PDF
     *
     * @param orderId 订单ID
     * @return PDF字节数组
     */
    byte[] exportPdf(Long orderId);

    /**
     * 导出销售订单Excel（单张表单）
     *
     * @return xlsx字节数组
     */
    byte[] exportExcel(Long orderId);

    /**
     * 导出订单确认书PDF（DEV-343/314）
     *
     * @return PDF字节数组
     */
    byte[] exportConfirmationPdf(Long orderId);

    /**
     * 导出订单列表
     *
     * @param order 查询条件
     * @return 导出文件路径
     */
    String exportOrderList(SalesOrder order);

    /**
     * 获取订单统计信息
     *
     * @return 统计信息
     */
    Object getOrderStatistics();

    /**
     * 校验订单信息
     * @param orderId
     * @return
     */
    OrderReferValidationVO validation(Long orderId);
}
