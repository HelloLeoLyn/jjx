package com.jjx.purchase.service;

import com.jjx.purchase.domain.dto.PurchaseSupplierDTO;
import com.jjx.purchase.domain.dto.SupplierEvaluationDTO;
import com.jjx.purchase.domain.dto.SupplierImportDTO;
import com.jjx.purchase.domain.vo.PurchaseSupplierQueryVO;
import com.jjx.purchase.domain.vo.PurchaseSupplierVO;

import java.util.List;

/**
 * 供应商服务接口
 * 提供供应商的业务逻辑操作
 */
public interface IPurchaseSupplierService {

    /**
     * 查询供应商列表
     *
     * @param queryVO 供应商查询条件
     * @return 供应商列表
     */
    List<PurchaseSupplierVO> selectSupplierList(PurchaseSupplierQueryVO queryVO);

    /**
     * 根据ID查询供应商
     *
     * @param supplierId 供应商ID
     * @return 供应商
     */
    PurchaseSupplierVO selectSupplierById(Long supplierId);

    /**
     * 根据名称查询供应商
     *
     * @param supplierName 供应商名称
     * @return 供应商
     */
    PurchaseSupplierVO selectSupplierByName(String supplierName);

    /**
     * 新增供应商
     *
     * @param supplierDTO 供应商数据传输对象
     * @return 结果
     */
    int insertSupplier(PurchaseSupplierDTO supplierDTO);

    /**
     * 修改供应商
     *
     * @param supplierDTO 供应商数据传输对象
     * @return 结果
     */
    int updateSupplier(PurchaseSupplierDTO supplierDTO);

    /**
     * 删除供应商
     *
     * @param supplierId 供应商ID
     * @return 结果
     */
    int deleteSupplierById(Long supplierId);

    /**
     * 批量删除供应商
     *
     * @param supplierIds 需要删除的供应商ID数组
     * @return 结果
     */
    int deleteSupplierByIds(Long[] supplierIds);

    /**
     * 检查供应商编码是否存在
     *
     * @param supplierCode 供应商编码
     * @return 是否存在
     */
    boolean checkSupplierCodeUnique(String supplierCode);

    /**
     * 检查供应商名称是否存在
     *
     * @param supplierName 供应商名称
     * @return 是否存在
     */
    boolean checkSupplierNameUnique(String supplierName);

    /**
     * 更新供应商状态
     *
     * @param supplierId 供应商ID
     * @param status 状态（0正常 1停用）
     * @return 结果
     */
    int updateSupplierStatus(Long supplierId, Integer status);

    /**
     * 更新供应商评估信息
     *
     * @return 结果
     */
    int updateSupplierEvaluation(SupplierEvaluationDTO supplierEvaluationDTO);

    /**
     * 根据供应商类型查询供应商列表
     *
     * @param supplierType 供应商类型
     * @return 供应商列表
     */
    List<PurchaseSupplierVO> selectSuppliersByType(String supplierType);

    /**
     * 查询活跃供应商列表
     *
     * @return 活跃供应商列表
     */
    List<PurchaseSupplierVO> selectActiveSuppliers();

    /**
     * 根据评估分数查询优质供应商
     *
     * @param minScore 最低评估分数
     * @return 优质供应商列表
     */
    List<PurchaseSupplierVO> selectHighQualitySuppliers(Double minScore);

    /**
     * 导出供应商列表
     *
     * @param queryVO 查询条件
     * @return 导出文件路径
     */
    String exportSupplierList(PurchaseSupplierQueryVO queryVO);

    /**
     * 获取供应商统计信息
     *
     * @return 统计信息
     */
    Object getSupplierStatistics();

    /**
     * 导入供应商数据
     *
     * @param importList 导入数据列表
     * @param operName 操作人
     * @return 导入结果
     */
    String importSuppliers(List<SupplierImportDTO> importList, String operName);
}
