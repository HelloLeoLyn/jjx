package com.jjx.production.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.production.domain.dto.ToolingDTO;
import com.jjx.production.domain.dto.ToolingImportDTO;
import com.jjx.production.domain.dto.ToolingQueryDTO;
import com.jjx.production.domain.entity.ProductionTooling;
import com.jjx.production.domain.vo.ToolingVO;

import java.util.List;

/**
 * 工装模具档案 Service
 */
public interface ToolingService {

    /** 分页查询 */
    PageResult<ToolingVO> page(ToolingQueryDTO query);

    /** 查询列表（导出用，全量按筛选） */
    List<ToolingVO> list(ToolingQueryDTO query);

    /** 详情（含照片附件ID） */
    ToolingVO getById(Long id);

    /** 下拉选项（未报废，可按类型过滤） */
    List<ToolingVO> options(String type);

    /** 按规则生成编号 */
    String genNo(String type);

    /** 新增 */
    Long create(ToolingDTO dto);

    /** 修改 */
    void update(ToolingDTO dto);

    /** 状态变更 */
    void changeStatus(Long id, Integer status);

    /** 删除（逻辑删） */
    void delete(Long id);

    /** Excel 导入，返回结果描述（成功行数/失败明细） */
    String importExcel(List<ToolingImportDTO> list, String operator);

    /** 查询导入模板列名（供前端提示，可空实现） */
    Class<ToolingImportDTO> importDtoClass();

    /** 实体转 VO（供导出前转换） */
    ToolingVO toVO(ProductionTooling e);
}
