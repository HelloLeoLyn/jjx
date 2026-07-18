package com.jjx.purchase.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.purchase.domain.dto.MaterialInquiryDTO;
import com.jjx.purchase.domain.dto.MaterialInquiryQueryDTO;
import com.jjx.purchase.domain.vo.MaterialInquiryVO;

import java.util.List;

/**
 * 材料询价服务接口
 *
 * @author JJX ERP系统
 * @date 2026-04-02
 */
public interface IMaterialInquiryService {

    /**
     * 查询材料询价列表
     *
     * @param queryDTO 查询条件
     * @return 询价列表
     */
    PageResult<MaterialInquiryVO> selectMaterialInquiryList(MaterialInquiryQueryDTO queryDTO);

    /**
     * 根据ID查询材料询价详情
     *
     * @param inquiryId 询价ID
     * @return 询价详情
     */
    MaterialInquiryVO selectMaterialInquiryById(Long inquiryId);

    /**
     * 新增材料询价
     *
     * @param inquiryDTO 询价信息
     * @return 结果
     */
    int insertMaterialInquiry(MaterialInquiryDTO inquiryDTO);

    /**
     * 修改材料询价
     *
     * @param inquiryDTO 询价信息
     * @return 结果
     */
    int updateMaterialInquiry(MaterialInquiryDTO inquiryDTO);

    /**
     * 批量删除材料询价
     *
     * @param inquiryIds 需要删除的询价ID
     * @return 结果
     */
    int deleteMaterialInquiryByIds(Long[] inquiryIds);

    /**
     * 删除材料询价信息
     *
     * @param inquiryId 询价ID
     * @return 结果
     */
    int deleteMaterialInquiryById(Long inquiryId);

    /**
     * 根据物料编码查询询价历史
     *
     * @param materialCode 物料编码
     * @param limit 限制条数
     * @return 询价历史列表
     */
    List<MaterialInquiryVO> selectInquiryByMaterialCode(String materialCode, Integer limit);

    /**
     * 获取物料最新询价
     *
     * @param materialCode 物料编码
     * @return 最新询价
     */
    MaterialInquiryVO selectLatestInquiryByMaterialCode(String materialCode);

    /**
     * 获取物料询价统计信息
     *
     * @param materialCode 物料编码
     * @return 统计信息
     */
    MaterialInquiryVO selectMaterialInquiryStats(String materialCode);

    /**
     * 批量更新询价状态
     *
     * @param inquiryIds 询价ID列表
     * @param status 状态
     * @return 更新数量
     */
    int updateInquiryStatusBatch(List<Long> inquiryIds, String status);

    /**
     * 更新过期询价状态
     *
     * @return 更新数量
     */
    int updateExpiredInquiryStatus();

    /**
     * 检查询价是否存在
     *
     * @param materialCode 物料编码
     * @param supplierId 供应商ID
     * @param inquiryDate 询价日期
     * @return 是否存在
     */
    boolean existsInquiry(String materialCode, Long supplierId, String inquiryDate);

    /**
     * 导入材料询价数据
     *
     * @param inquiryList 询价数据列表
     * @param isUpdateSupport 是否更新支持，如果已存在则进行更新数据
     * @param operName 操作人
     * @return 结果
     */
    String importMaterialInquiry(List<MaterialInquiryDTO> inquiryList, Boolean isUpdateSupport, String operName);

    /**
     * 导出材料询价数据
     *
     * @param queryDTO 查询条件
     * @return 导出数据列表
     */
    List<MaterialInquiryVO> exportMaterialInquiry(MaterialInquiryQueryDTO queryDTO);

    /**
     * 复制材料询价
     *
     * @param inquiryId 源询价ID
     * @return 新询价ID
     */
    Long copyMaterialInquiry(Long inquiryId);

    /**
     * 批量新增材料询价
     *
     * @param inquiryDTOList 询价列表
     * @return 新增数量
     */
    int batchInsertMaterialInquiry(List<MaterialInquiryDTO> inquiryDTOList);

    /**
     * 获取价格趋势数据
     *
     * @param materialCode 物料编码
     * @param days 天数
     * @return 价格趋势列表
     */
    List<MaterialInquiryVO> selectPriceTrend(String materialCode, Integer days);

    /**
     * 获取供应商询价统计
     *
     * @param supplierId 供应商ID
     * @return 统计信息
     */
    MaterialInquiryVO selectSupplierInquiryStats(Long supplierId);

    /**
     * 逻辑删除询价记录
     *
     * @param inquiryId 询价ID
     * @return 删除数量
     */
    int logicDeleteMaterialInquiry(Long inquiryId);

    /**
     * 恢复逻辑删除的询价记录
     *
     * @param inquiryId 询价ID
     * @return 恢复数量
     */
    int recoverMaterialInquiry(Long inquiryId);

    /**
     * 验证询价数据
     *
     * @param inquiryDTO 询价数据
     * @return 验证结果
     */
    String validateMaterialInquiry(MaterialInquiryDTO inquiryDTO);

    /**
     * 获取可用的询价状态列表
     *
     * @return 状态列表
     */
    List<String> getAvailableInquiryStatus();

    /**
     * 获取币种列表
     *
     * @return 币种列表
     */
    List<String> getCurrencyList();

    /**
     * 获取询价人列表
     *
     * @return 询价人列表
     */
    List<String> getInquiryPersonList();
}
