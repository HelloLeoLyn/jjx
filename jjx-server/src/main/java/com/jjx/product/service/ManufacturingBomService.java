package com.jjx.product.service;

import com.jjx.product.domain.dto.ManufacturingBomSaveDTO;
import com.jjx.product.domain.dto.ManufacturingSchemeChoiceDTO;
import com.jjx.product.domain.vo.ManufacturingBomVO;
import com.jjx.product.domain.vo.ManufacturingSchemeVO;

import java.util.List;

/** 制造 BOM 方案后端能力（dev-20261111-004）：产出身份、多行投入、多方案与产品主方案。 */
public interface ManufacturingBomService {

    /** 某产出对象的候选方案；传 productId 时回显该产品已保存的主方案标记。 */
    List<ManufacturingSchemeVO> options(String outputType, Long outputId, Long productId);

    /** 新建制造方案并保存明细；不自动发布、不改父 BOM。 */
    ManufacturingBomVO save(ManufacturingBomSaveDTO dto);

    /** 编辑已保存制造方案；产出身份不可原地改变。 */
    ManufacturingBomVO update(Long bomId, ManufacturingBomSaveDTO dto);

    /** 生成采用 token，不落库、不设主方案。 */
    ManufacturingSchemeVO adopt(Long bomId);

    /** 查看父产品 BOM 已保存的方案选择；非产品 BOM 返回空列表。 */
    List<ManufacturingSchemeChoiceDTO> getSchemeChoices(Long bomId);
}
