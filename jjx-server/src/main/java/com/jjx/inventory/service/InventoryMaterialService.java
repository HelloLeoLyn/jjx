package com.jjx.inventory.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.dto.imports.MaterialImportDTO;
import com.jjx.inventory.dto.query.MaterialCheckDTO;
import com.jjx.inventory.dto.query.MaterialQueryDTO;
import com.jjx.inventory.dto.vo.MaterialVO;

import java.util.List;
import java.util.Map;

/**
 * 物料主数据服务接口
 */
public interface InventoryMaterialService extends IService<InventoryMaterial> {

    /**
     * 分页查询物料列表
     */
    PageResult<MaterialVO> pageQuery(MaterialQueryDTO queryDTO);

    /**
     * 根据ID获取物料详情
     */
    MaterialVO getDetailById(Long id);

    /**
     * 根据编码获取物料
     */
    InventoryMaterial getByCode(String materialCode);

    /**
     * 创建物料
     */
    boolean create(InventoryMaterial material);

    /**
     * 更新物料
     */
    boolean update(InventoryMaterial material);

    /**
     * 删除物料（检查是否被使用）
     */
    boolean deleteWithCheck(Long id);

    /**
     * 获取物料下拉选项
     */
    List<Map<String, Object>> getOptions(String keyword);


    /**
     * 检查物料编码是否存在
     */
    boolean existsByCode(String materialCode);

    /**
     * 批量更新物料状态
     */
    boolean batchUpdateStatus(List<Long> ids, Integer status);

    PageResult<MaterialVO> search(MaterialQueryDTO queryDTO);

    /**
     * 导入物料数据
     *
     * @param importList 导入的物料数据列表
     * @param operName 操作人
     * @return 导入结果信息
     */
    String importMaterial(List<MaterialImportDTO> importList, String operName);

    List<MaterialVO> selectList(MaterialQueryDTO queryDTO);

    /** 生成编码 */
    String generateCode();

    /**
     * 校验物料是否存在（根据名称、规格等条件）
     * @param checkDTO 校验条件（名称、规格、供应商等）
     * @return 找到返回 MaterialVO，未找到返回 null
     */
    MaterialVO checkMaterial(MaterialCheckDTO checkDTO);
}
