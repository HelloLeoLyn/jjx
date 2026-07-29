package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.entity.SalesInquiry;

import java.util.List;

/**
 * 销售询价单服务接口
 */
public interface IInquiryService {

    /**
     * 分页查询询价单列表
     */
    PageResult<SalesInquiry> selectInquiryPage(SalesInquiry inquiry, Integer pageNum, Integer pageSize);

    /**
     * 查询询价单列表
     */
    List<SalesInquiry> selectInquiryList(SalesInquiry inquiry);

    /**
     * 根据ID查询询价单
     */
    SalesInquiry selectInquiryById(Long inquiryId);

    /**
     * 新增询价单
     */
    int insertInquiry(SalesInquiry inquiry);

    /**
     * 修改询价单
     */
    int updateInquiry(SalesInquiry inquiry);

    /**
     * 删除询价单
     */
    int deleteInquiryById(Long inquiryId);

    /**
     * 批量删除询价单
     */
    int deleteInquiryByIds(Long[] inquiryIds);

    /**
     * 检查询价单号是否唯一
     */
    boolean checkInquiryNoUnique(String inquiryNo);

    /**
     * 询价转报价
     * 创建报价单并返回报价单ID
     */
    Long convertToQuotation(Long inquiryId);

    /**
     * 获取询价单状态选项
     */
    List<Object> getStatusOptions();
}
