package com.jjx.sales.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.product.domain.vo.ProductValidationVO;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.dto.SalesOrderProductQueryDTO;
import com.jjx.sales.domain.vo.SalesOrderProductVO;

import java.util.List;

/**
 * 订单产品明细服务接口
 */
public interface ISalesOrderProductService {

    /**
     * 根据ID查询订单产品明细
     *
     * @param id 主键ID
     * @return 订单产品明细VO
     */
    SalesOrderProductVO getById(Long id);

    /**
     * 分页查询订单产品明细列表
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageResult<SalesOrderProductVO> getPageList(SalesOrderProductQueryDTO queryDTO);

    /**
     * 根据订单ID查询订单产品明细列表
     *
     * @param orderId 订单ID
     * @return 订单产品明细列表
     */
    List<SalesOrderProductVO> getListByOrderId(Long orderId);

    /**
     * 新增订单产品明细
     *
     * @param addDTO 新增DTO
     * @return 是否成功
     */
    boolean add(SalesOrderProductDTO addDTO);

    /**
     * 批量新增订单产品明细
     *
     * @param addDTOList 新增DTO列表
     * @return 是否成功
     */
    boolean batchAdd(List<SalesOrderProductDTO> addDTOList);

    /**
     * 修改订单产品明细
     *
     * @param editDTO 修改DTO
     * @return 是否成功
     */
    boolean update(SalesOrderProductDTO editDTO);

    /**
     * 删除订单产品明细
     *
     * @param id 主键ID
     * @return 是否成功
     */
    boolean delete(Long id);

    /**
     * 批量删除订单产品明细
     *
     * @param ids 主键ID列表
     * @return 是否成功
     */
    boolean batchDelete(List<Long> ids);

    /**
     * 根据订单ID删除订单产品明细
     *
     * @param orderId 订单ID
     * @return 是否成功
     */
    boolean deleteByOrderId(Long orderId);

    /**
     * 根据订单ID查询是否存在
     */
    boolean isExists(Long orderId);

    List<ProductValidationVO> validation(Long orderId);
}
