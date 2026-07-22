package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesQuotation;

import java.util.List;

/**
 * 销售报价单服务接口
 * 提供销售报价单的业务逻辑操作
 */
public interface IQuotationService {

    /**
     * 查询销售报价单列表
     *
     * @param quotation 销售报价单查询条件
     * @return 销售报价单列表
     */
    List<SalesQuotation> selectQuotationList(SalesQuotation quotation);

    PageResult<SalesQuotation> selectQuotationPage(SalesQuotation quotation, Integer pageNum, Integer pageSize);

    /**
     * 根据ID查询销售报价单
     *
     * @param quotationId 报价单ID
     * @return 销售报价单
     */
    SalesQuotation selectQuotationById(Long quotationId);

    /**
     * 新增销售报价单
     *
     * @param quotation 销售报价单
     * @return 结果
     */
    int insertQuotation(SalesQuotation quotation);

    /**
     * 修改销售报价单
     *
     * @param quotation 销售报价单
     * @return 结果
     */
    int updateQuotation(SalesQuotation quotation);

    /**
     * 删除销售报价单
     *
     * @param quotationId 报价单ID
     * @return 结果
     */
    int deleteQuotationById(Long quotationId);

    /**
     * 批量删除销售报价单
     *
     * @param quotationIds 需要删除的报价单ID数组
     * @return 结果
     */
    int deleteQuotationByIds(Long[] quotationIds);

    /**
     * 检查报价单号是否存在
     *
     * @param quotationNo 报价单号
     * @return 是否存在
     */
    boolean checkQuotationNoUnique(String quotationNo);

    /**
     * 更新报价单状态
     *
     * @param quotationId 报价单ID
     * @param status 报价单状态
     * @return 结果
     */
    int updateQuotationStatus(Long quotationId, String status);

    /**
     * 发送报价单给客户
     *
     * @param quotationId 报价单ID
     * @return 结果
     */
    int sendQuotation(Long quotationId);

    /**
     * 报价单转为订单
     *
     * @param quotationId 报价单ID
     * @return 转换结果
     */
    Object convertToOrder(Long quotationId);

    /**
     * 导出报价单PDF
     *
     * @param quotationId 报价单ID
     * @return PDF文件路径
     */
    String exportPdf(Long quotationId);

    /**
     * 复制报价单
     *
     * @param quotationId 报价单ID
     * @return 复制的报价单
     */
    SalesQuotation copyQuotation(Long quotationId);

    /**
     * 提交报价单审核
     *
     * @param quotationId 报价单ID
     * @return 结果
     */
    int submitReview(Long quotationId);

    /**
     * 审核报价单
     *
     * @param quotationId 报价单ID
     * @param approved 是否通过
     * @param remark 审核备注
     * @return 结果
     */
    int reviewQuotation(Long quotationId, Boolean approved, String remark);

    /**
     * 导出报价单列表
     *
     * @param quotation 查询条件
     * @return 导出文件路径
     */
    String exportQuotationList(SalesQuotation quotation);

    /**
     * 获取报价单状态选项
     *
     * @return 状态选项列表
     */
    List<Object> getStatusOptions();

    /**
     * 获取币种选项
     *
     * @return 币种选项列表
     */
    List<Object> getCurrencyOptions();

    /**
     * 获取报价模板列表
     *
     * @return 模板列表
     */
    List<Object> getTemplates();

    /**
     * 根据模板创建报价单
     *
     * @param templateId 模板ID
     * @param customerId 客户ID
     * @return 创建的报价单
     */
    SalesQuotation createFromTemplate(Long templateId, Long customerId);

    /**
     * 快速报价
     *
     * @param quickQuoteRequest 快速报价请求
     * @return 创建的报价单
     */
    SalesQuotation quickQuote(Object quickQuoteRequest);

    /**
     * 获取客户历史报价
     *
     * @param customerId 客户ID
     * @return 历史报价列表
     */
    List<SalesQuotation> getCustomerHistory(Long customerId);

    /**
     * 获取报价单统计信息
     *
     * @return 统计信息
     */
    Object getQuotationStatistics();
}
