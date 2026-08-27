package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.dto.SalesInquiryEditDTO;
import com.jjx.sales.domain.entity.SalesInquiry;
import com.jjx.sales.domain.vo.InquiryToQuotationVO;
import com.jjx.sales.domain.vo.SalesInquiryEditVO;

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
     * 编码生成器：按客户简称取下一个流水号（查询该客户已有产品编码第4-6位最大值+1）
     */
    String nextProductSerial(String customerShort);

    /**
     * 修改询价单
     */
    SalesInquiryEditVO updateInquiry(SalesInquiryEditDTO inquiry);

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
    InquiryToQuotationVO convertToQuotation(Long inquiryId);

    /**
     * 发送询价（草稿/待处理 → 已发送）
     */
    int sendInquiry(Long inquiryId);

    /**
     * 客户确认询价（已发送 → 已确认）
     */
    int acceptInquiry(Long inquiryId);

    /**
     * 客户拒绝询价（已发送 → 已拒绝）
     */
    int rejectInquiry(Long inquiryId);

    /**
     * 导出询价单列表
     *
     * @param inquiry 查询条件
     * @return Excel 字节数组
     */
    byte[] exportInquiryList(SalesInquiry inquiry);

    /**
     * 获取询价单状态选项
     */
    List<Object> getStatusOptions();
}
