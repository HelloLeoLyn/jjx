package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.UpdateBomStatusDTO;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.domain.query.EngineeringBomQuery;
import com.jjx.product.domain.vo.EngineeringBomVO;

import java.util.List;

/**
 * 产品BOM Service接口
 */
public interface IEngineeringBomService extends IService<EngineeringBom> {

    /**
     * 获取产品BOM列表
     */
    List<EngineeringBomVO> getBomList(EngineeringBomQuery query);

    /**
     * 获取产品BOM列表
     */
    PageResult<EngineeringBomVO> getBomListPage(EngineeringBomQuery query);
    /**
     * 获取BOM详情（包含明细）
     */
    EngineeringBomVO getBomDetail(Long bomId);

    /**
     * 创建BOM（包含明细）
     */
    boolean createBom(EngineeringBomDTO dto);

    /**
     * 更新BOM（包含明细）
     */
    boolean updateBom(EngineeringBomDTO dto);

    /**
     * 删除BOM（包含明细）
     */
    boolean removeBomWithItems(Long bomId);

    /**
     * 设置默认BOM
     */
    boolean setDefaultBom(Long bomId);

    /**
     * 获取产品的默认BOM
     */
    EngineeringBom getDefaultBomByProductId(Long productId);

    /**
     * 获取BOM明细列表
     */
    List<EngineeringBomItem> getBomItems(Long bomId);

    /**
     * 计算BOM总成本
     */
    void calculateBomCost(Long bomId);

    /**
     * 检查bom码唯一性
     */
    boolean checkBomCodeUnique(String bomCode, String bomVersion, Long bomId);

    /**
     * 分页查询关联product表
     */
    PageResult<EngineeringBomVO> listPage(EngineeringBomQuery query);

    /**
     * 复制为新版本（DEV-619：版本号递增、明细复制、isCurrent 保持非当前）
     */
    EngineeringBomVO copyAsNewVersion(Long bomId, String newVersion);

    boolean approve(UpdateBomStatusDTO dto);
    boolean submitApprove(Long bomId);

    boolean updateStatus(UpdateBomStatusDTO dto);

    boolean reject(UpdateBomStatusDTO dto);
}
