package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.product.domain.dto.ProductBomDTO;
import com.jjx.product.domain.dto.UpdateBomStatusDTO;
import com.jjx.product.domain.entity.ProductBom;
import com.jjx.product.domain.entity.ProductBomItem;
import com.jjx.product.domain.query.ProductBomQuery;
import com.jjx.product.domain.vo.ProductBomVO;

import java.util.List;

/**
 * 产品BOM Service接口
 */
public interface IProductBomService extends IService<ProductBom> {

    /**
     * 获取产品BOM列表
     */
    List<ProductBomVO> getBomList(ProductBomQuery query);

    /**
     * 获取产品BOM列表
     */
    PageResult<ProductBomVO> getBomListPage(ProductBomQuery query);
    /**
     * 获取BOM详情（包含明细）
     */
    ProductBomVO getBomDetail(Long bomId);

    /**
     * 创建BOM（包含明细）
     */
    boolean createBom(ProductBomDTO dto);

    /**
     * 更新BOM（包含明细）
     */
    boolean updateBom(ProductBomDTO dto);

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
    ProductBom getDefaultBomByProductId(Long productId);

    /**
     * 获取BOM明细列表
     */
    List<ProductBomItem> getBomItems(Long bomId);

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
    PageResult<ProductBomVO> listPage(ProductBomQuery query);

    boolean approve(UpdateBomStatusDTO dto);

    boolean updateStatus(UpdateBomStatusDTO dto);

    boolean reject(UpdateBomStatusDTO dto);
}
