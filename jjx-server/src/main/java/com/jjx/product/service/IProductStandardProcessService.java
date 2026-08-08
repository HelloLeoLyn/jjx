package com.jjx.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.product.domain.dto.ProductStandardProcessQueryDTO;
import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.domain.vo.ProductStandardProcessVO;

import java.util.List;

/**
 * 产品标准工序服务接口
 */
public interface IProductStandardProcessService extends IService<ProductStandardProcess> {

    /**
     * 标准工序批量导入（2026-08-08）：编码必填+唯一、类型/类别枚举校验、库内判重跳过，返回结构化结果
     */
    com.jjx.inventory.dto.vo.MaterialImportResultVO importStandardProcesses(
            java.util.List<com.jjx.product.dto.imports.StandardProcessImportDTO> importList);

    // ==================== 基础 CRUD ====================

    /**
     * 创建标准工序
     *
     * @param process 工序实体
     * @return 工序VO
     */
    ProductStandardProcessVO createProcess(ProductStandardProcess process);

    /**
     * 更新标准工序
     *
     * @param process 工序实体
     * @return 工序VO
     */
    ProductStandardProcessVO updateProcess(ProductStandardProcess process);

    /**
     * 删除标准工序
     *
     * @param processId 工序ID
     */
    void deleteProcess(Long processId);

    /**
     * 分页查询标准工序
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    IPage<ProductStandardProcessVO> pageQuery(ProductStandardProcessQueryDTO queryDTO);

    /**
     * 根据ID获取标准工序
     *
     * @param processId 工序ID
     * @return 工序VO
     */
    ProductStandardProcessVO getProcessById(Long processId);

    // ==================== 状态管理 ====================

    /**
     * 启用/禁用工序
     *
     * @param processId 工序ID
     * @param enabled   是否启用
     */
    void setEnabled(Long processId, Boolean enabled);

    /**
     * 启用工序
     *
     * @param processId 工序ID
     */
    default void enable(Long processId) {
        setEnabled(processId, true);
    }

    /**
     * 禁用工序
     *
     * @param processId 工序ID
     */
    default void disable(Long processId) {
        setEnabled(processId, false);
    }

    // ==================== 查询接口 ====================

    /**
     * 获取所有启用的工序列表（按显示顺序排序）
     *
     * @return 工序VO列表
     */
    List<ProductStandardProcessVO> getEnabledProcesses();

    /**
     * 根据工序类型获取工序列表
     *
     * @param processType 工序类型（PRINTING/CUTTING/LAMINATING/TESTING/PACKAGING）
     * @return 工序VO列表
     */
    List<ProductStandardProcessVO> getByProcessType(String processType);

    /**
     * 根据工序类别获取工序列表
     *
     * @param processCategory 工序类别（PREPARATION/MAIN/FINISHING/QUALITY）
     * @return 工序VO列表
     */
    List<ProductStandardProcessVO> getByProcessCategory(String processCategory);

    /**
     * 根据工序类型和类别获取工序列表
     *
     * @param processType     工序类型
     * @param processCategory 工序类别
     * @return 工序VO列表
     */
    List<ProductStandardProcessVO> getByProcessTypeAndCategory(String processType, String processCategory);

    /**
     * 根据ID列表批量获取工序
     *
     * @param processIds 工序ID列表
     * @return 工序VO列表
     */
    List<ProductStandardProcessVO> getProcessesByIds(List<Long> processIds);

    // ==================== 批量操作 ====================

    /**
     * 批量更新显示顺序
     *
     * @param processIds 排序后的工序ID列表
     */
    void batchUpdateDisplayOrder(List<Long> processIds);

    /**
     * 批量启用工序
     *
     * @param processIds 工序ID列表
     */
    void batchEnable(List<Long> processIds);

    /**
     * 批量禁用工序
     *
     * @param processIds 工序ID列表
     */
    void batchDisable(List<Long> processIds);

    /**
     * 批量删除工序
     *
     * @param processIds 工序ID列表
     */
    void batchDelete(List<Long> processIds);

    // ==================== 验证接口 ====================

    /**
     * 验证工序编码是否唯一
     *
     * @param processCode 工序编码
     * @param excludeId   排除的工序ID（更新时使用）
     * @return 是否唯一
     */
    boolean checkProcessCodeUnique(String processCode, Long excludeId);

    /**
     * 验证工序是否可删除（未被引用）
     *
     * @param processId 工序ID
     * @return 是否可删除
     */
    boolean canDelete(Long processId);

    // ==================== 编码生成 ====================

    /**
     * 生成下一个工序编码
     * 规则：T{工序类型编码}+C{工序类别编码}+3位序号
     * 示例：T4C3001
     *
     * @param processType     工序类型（字典项Key）
     * @param processCategory 工序类别（字典项Key）
     * @return 生成的工序编码
     */
    String generateNextProcessCode(String processType, String processCategory);
}
