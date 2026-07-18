package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.dto.CustomerAddDTO;
import com.jjx.sales.domain.dto.CustomerEditDTO;
import com.jjx.sales.domain.dto.CustomerQueryDTO;
import com.jjx.sales.domain.entity.SalesCustomer;
import com.jjx.sales.domain.vo.CustomerVO;

import java.util.List;

/**
 * 客户服务接口
 * 定义客户管理的业务逻辑
 */
public interface ICustomerService {

    /**
     * 查询客户列表
     *
     * @param customer 客户查询条件
     * @return 客户列表
     */
    List<CustomerVO> list(CustomerQueryDTO customer);

    /**
     * 查询客户列表分页
     *
     * @param queryDTO 客户查询条件
     * @return 客户列表
     */
    PageResult<CustomerVO> page(CustomerQueryDTO queryDTO);

    /**
     * 根据ID查询客户信息
     *
     * @param customerId 客户ID
     * @return 客户信息
     */
    CustomerVO selectCustomerById(Long customerId);

    /**
     * 新增客户
     *
     * @param dto 客户新增DTO
     * @return 结果
     */
    int insertCustomer(CustomerAddDTO dto);

    /**
     * 修改客户
     *
     * @param dto 客户修改DTO
     * @return 结果
     */
    int updateCustomer(CustomerEditDTO dto);

    /**
     * 批量删除客户
     *
     * @param customerIds 需要删除的客户ID数组
     * @return 结果
     */
    int deleteCustomerByIds(Long[] customerIds);

    /**
     * 查询客户下拉列表
     *
     * @return 客户下拉列表
     */
    List<SalesCustomer> selectCustomerDropdown();

    /**
     * 变更客户状态
     *
     * @param customerId 客户ID
     * @param status 状态值
     * @return 结果
     */
    int changeCustomerStatus(Long customerId, Integer status);

    /**
     * 批量审核客户
     *
     * @param customerIds 客户ID数组
     * @return 结果
     */
    int approveCustomers(Long[] customerIds);

    /**
     * 获取客户统计信息
     *
     * @return 统计信息
     */
    Object getCustomerStatistics();

    /**
     * 检查客户编码是否唯一
     *
     * @param customer 客户信息
     * @return 结果
     */
    boolean checkCustomerCodeUnique(SalesCustomer customer);

    /**
     * 检查客户名称是否唯一
     *
     * @param customer 客户信息
     * @return 结果
     */
    boolean checkCustomerNameUnique(SalesCustomer customer);

    /**
     * 根据客户编码获取客户信息
     *
     * @param customerCode 客户编码
     * @return 客户信息
     */
    CustomerVO selectCustomerByCode(String customerCode);

    /**
     * 更新客户信用额度
     *
     * @param customerId 客户ID
     * @param creditLimit 信用额度
     * @return 结果
     */
    int updateCustomerCreditLimit(Long customerId, Double creditLimit);


    /**
     * 根据关键词获取用户列表
     */
    List<CustomerVO> search(String keyword);

    /**
     * 导出客户信息
     */
    String exportCustomerList(SalesCustomer customer);


    /**
     * 生成客户编码
     */
    String generateCustomerCode();
}
