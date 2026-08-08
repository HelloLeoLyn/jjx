package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.product.domain.dto.ProductDTO;
import com.jjx.product.domain.dto.ProductUpdateDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.query.ProductQuery;
import com.jjx.product.domain.vo.ProductFullVO;
import com.jjx.product.domain.vo.ProductVo;

import java.util.List;

/**
 * 产品Service接口
 */
public interface IProductService extends IService<Product> {

    /**
     * 获取产品列表（带分页）
     */
    List<ProductVo> getProductList(ProductQuery query);

    /**
     * 获取产品列表（带分页）
     */
    PageResult<ProductVo> getProductPage(ProductQuery query);

    /**
     * 获取产品完整信息列表（带分页）
     */
    PageResult<ProductVo> getProductFullPage(ProductQuery query);

    /**
     * 获取产品详情
     */
    ProductVo getProductDetail(Long productId);

    /**
     * 检查产品编码是否唯一
     */
    boolean checkProductCodeUnique(String productCode, Long productId);

    /**
     * 检查产品名称是否唯一
     */
    boolean checkProductNameUnique(String productName, Long productId);

    /**
     * 发布产品
     */
    boolean releaseProduct(Long productId);

    /**
     * 停用产品
     */
    boolean obsoleteProduct(Long productId);

    /**
     * 根据分类获取产品列表
     */
    List<Product> getProductsByCategory(Long categoryId);

    /**
     * 搜索产品
     */
    List<Product> searchProducts(String keyword);

    /**
     * 新增产品
     */
    Long addProduct(ProductDTO productDTO);

    String getProductCode(String categoryCode);

    ProductFullVO getFullProductDetail(Long productId);

    boolean submitProduct(Long productId);

    boolean approveProduct(ProductUpdateDTO dto);

    boolean rejectProduct(ProductUpdateDTO dto);

    /**
     * 取消产品（取消审核/取消发布）
     */
    boolean cancelProduct(Long productId);

    /**
     * 根据客户ID生成流水号（3位数字）
     */
    String generateSerialNo(Long customerId);

    /**
     * 样品询价/报价建档草稿产品（2026-08-08）：同编码已存在则复用，否则新建（状态=开发中1，标记来源）
     */
    Long ensureDraftProduct(String productCode, String productName, String unit, String source);

    /**
     * 作废联动清理（2026-08-08）：来源匹配+开发中+无单据明细引用 → 置取消(8)
     * @return 是否已置取消
     */
    boolean cleanupDraftProduct(Long productId, String source);
}
