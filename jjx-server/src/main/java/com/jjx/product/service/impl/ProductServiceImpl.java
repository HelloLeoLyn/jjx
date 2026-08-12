package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.product.domain.converter.ProductConverter;
import com.jjx.product.domain.dto.ProductDTO;
import com.jjx.product.domain.dto.ProductUpdateDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.product.domain.query.ProductQuery;
import com.jjx.product.domain.vo.*;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.*;
import com.jjx.system.annotation.Event;
import com.jjx.notification.service.NotificationService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 产品Service实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl extends ServiceImpl<ProductMapper,Product> implements IProductService {

    private final ProductMapper productMapper;
    private final ProductCodeGenerator productCodeGenerator;
    private final com.jjx.product.service.ProductCodeService productCodeService;
    private final ProductConverter productConverter;
    private final IEngineeringBomService bomService;
    private final IEngineeringRoutingService routingService;
    private final IProductCategoryService categoryService;
    private final IEngineeringFilmService filmService;
    private final NotificationService notificationService;
    private final com.jjx.inventory.mapper.InventoryMaterialMapper inventoryMaterialMapper;
    private final com.jjx.sales.mapper.SalesQuotationItemMapper quotationItemMapper;
    private final com.jjx.sales.mapper.SalesOrderProductMapper orderProductMapper;
    private final com.jjx.sales.mapper.CustomerMapper salesCustomerMapper;
    @Override
    public List<ProductVo> getProductList(ProductQuery query) {
        LambdaQueryWrapper<Product> wrapper = buildWrapper(query);
        List<Product> products = productMapper.selectList(wrapper);
        return productConverter.toVOList(products);
    }

    private static @NonNull LambdaQueryWrapper<Product> buildWrapper(ProductQuery query) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        // 产品编码查询
        if (StringUtils.isNotBlank(query.getProductCode())) {
            wrapper.like(Product::getProductCode, query.getProductCode());
        }

        // 产品名称查询
        if (StringUtils.isNotBlank(query.getProductName())) {
            wrapper.like(Product::getProductName, query.getProductName());
        }

        // 分类ID查询
        if (query.getCategoryId() != null) {
            wrapper.eq(Product::getCategoryId, query.getCategoryId());
        }

        // 产品状态查询
        if (StringUtils.isNotBlank(query.getProductStatus())) {
            wrapper.eq(Product::getProductStatus, query.getProductStatus());
        }
        // 日期范围查询
        if (StringUtils.isNotBlank(query.getStartDate())) {
            wrapper.ge(Product::getCreateTime, query.getStartDate());
        }
        if (StringUtils.isNotBlank(query.getEndDate())) {
            wrapper.le(Product::getCreateTime, query.getEndDate());
        }

        return wrapper;
    }

    @Override
    public PageResult<ProductVo> getProductPage(ProductQuery query) {
        LambdaQueryWrapper<Product> wrapper = buildWrapper(query);

        // 分页查询
        Page<Product> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Product> productPage = productMapper.selectPage(page, wrapper);
        List<ProductVo> list = productConverter.toVOList(productPage.getRecords());
        return PageResult.build(list, productPage.getTotal());
    }

    @Override
    public PageResult<ProductVo> getProductFullPage(ProductQuery query) {
        LambdaQueryWrapper<Product> wrapper = buildWrapper(query);
        // 分页查询
        Page<Product> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<ProductVo> productFullPage = productMapper.getProductFullPage(page, wrapper);
        return PageResult.build(productFullPage.getRecords(),productFullPage.getTotal());
    }


    @Override
    public ProductVo getProductDetail(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return null;
        }
        return productConverter.toVO(product);
    }

    @Override
    public boolean checkProductCodeUnique(String productCode, Long productId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductCode, productCode);
        if (productId != null) {
            wrapper.ne(Product::getProductId, productId);
        }
        return productMapper.selectCount(wrapper) == 0;
    }

    @Override
    public boolean checkProductNameUnique(String productName, Long productId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductName, productName);
        if (productId != null) {
            wrapper.ne(Product::getProductId, productId);
        }
        return productMapper.selectCount(wrapper) == 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean releaseProduct(Long productId) {
        // 1. 获取产品
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        // 2. 检查产品当前状态
        if (Objects.equals(ProductEnums.Status.RELEASED.getValue(), product.getProductStatus())) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_ALREADY_RELEASED);
        }

        if (Objects.equals(ProductEnums.Status.OBSOLETE.getValue(), product.getProductStatus())) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_ALREADY_OBSOLETE);
        }

        // 2.5 校验：BOM和路线是否已配置且审批通过
        // DEV-771：current_bom_id 为空时回退用 is_current=1 的BOM（兼容历史数据/指针未同步场景）
        Long bomId = product.getCurrentBomId();
        EngineeringBom bom = null;
        if (bomId != null) {
            bom = bomService.getById(bomId);
        }
        if (bom == null) {
            bom = bomService.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EngineeringBom>()
                            .eq(EngineeringBom::getProductId, product.getProductId())
                            .eq(EngineeringBom::getIsCurrent, true)
                            .last("LIMIT 1"));
        }
        if (bom == null || !ProductEnums.BomStatus.APPROVED.getValue().equals(bom.getApproveStatus())) {
            throw new BusinessException("当前BOM未审批通过，无法发布产品");
        }

        Long routeId = product.getCurrentRouteId();
        EngineeringRouting routing = null;
        if (routeId != null) {
            routing = routingService.getById(routeId);
        }
        if (routing == null) {
            routing = routingService.getOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EngineeringRouting>()
                            .eq(EngineeringRouting::getProductId, product.getProductId())
                            .eq(EngineeringRouting::getIsCurrent, 1)
                            .last("LIMIT 1"));
        }
        if (routing == null || !ProductEnums.RouteStatus.APPROVED.getValue().equals(routing.getApproveStatus())) {
            throw new BusinessException("当前工艺路线未审批通过，无法发布产品");
        }

        // 3. 更新状态为已发布
        product.setProductStatus(ProductEnums.Status.RELEASED.getValue());
        boolean updated = productMapper.updateById(product) > 0;

        if (updated) {
            log.info("产品发布成功，产品ID: {}, 产品编码: {}", productId, product.getProductCode());
            // 发布时自动创建成品物料档案（DEV-290：销售发货/完工入库扣成品库存的前提）
            try {
                com.jjx.inventory.domain.InventoryMaterial exist = inventoryMaterialMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                                .eq(com.jjx.inventory.domain.InventoryMaterial::getProductId, productId)
                                .last("LIMIT 1"));
                if (exist == null) {
                    com.jjx.inventory.domain.InventoryMaterial mat = new com.jjx.inventory.domain.InventoryMaterial();
                    mat.setProductId(productId);
                    mat.setMaterialCode(product.getProductCode());
                    mat.setMaterialName(product.getProductName());
                    mat.setMaterialType("F"); // 成品
                    mat.setUnit(product.getUnit() != null ? product.getUnit() : "PCS");
                    mat.setStatus(1);
                    mat.setStandardPrice(product.getBasePrice());
                    mat.setCreateBy("system");
                    mat.setUpdateBy("system");
                    inventoryMaterialMapper.insert(mat);
                    log.info("产品[{}]发布，自动创建成品物料[{}]", product.getProductCode(), mat.getMaterialCode());
                }
            } catch (Exception e) {
                log.warn("创建成品物料失败: {}", e.getMessage());
            }
        }

        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean obsoleteProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        // 更新状态为已停产
        product.setProductStatus(ProductEnums.Status.OBSOLETE.getValue());
        return productMapper.updateById(product) > 0;
    }

    @Override
    public List<Product> getProductsByCategory(Long categoryId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getCategoryId, categoryId);
        wrapper.eq(Product::getProductStatus, ProductEnums.Status.RELEASED.getValue()); // 只查询已发布的产品
        return productMapper.selectList(wrapper);
    }

    @Override
    public List<Product> searchProducts(String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(Product::getProductCode, keyword)
                   .or()
                   .like(Product::getProductName, keyword);
        }
        wrapper.eq(Product::getProductStatus,ProductEnums.Status.RELEASED.getValue()); // 只查询已发布的产品
        wrapper.orderByDesc(Product::getCreateTime);
        return productMapper.selectList(wrapper);
    }


    @Override
    public Long addProduct(ProductDTO productDTO) {
        // 这里需要实现新增产品的逻辑，包括校验、转换和保存
        if (StringUtils.isNotBlank(productDTO.getProductCode())&&!checkProductCodeUnique(productDTO.getProductCode(), null)) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_CODE_DUPLICATE);
        }
        if (!checkProductNameUnique(productDTO.getProductName(), null)) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_DUPLICATE);
        }
        // 生成productCode（如果没有提供），设置默认状态等
        Product product = new Product();
        BeanUtils.copyProperties(productDTO, product);
        product.setProductStatus(ProductEnums.Status.DEVELOPING.getValue());
        boolean saved = save(product);
        return saved ? product.getProductId() : null;
    }

    @Override
    public String getProductCode(String categoryCode) {
        if(StringUtils.isNotBlank(categoryCode)){
            return productCodeGenerator.generateProductCodeByCategory(categoryCode);
        }
        return productCodeGenerator.generateProductCode();
    }

    @Override
    public ProductFullVO getFullProductDetail(Long productId) {
        ProductFullVO fullVO = new ProductFullVO();

        Product product = productMapper.selectById(productId);
        ProductVo vo = productConverter.toVO(product);
        fullVO.setProduct(vo);

        EngineeringBomVO bomVO = bomService.getBomDetail(product.getCurrentBomId());
        fullVO.setBom(bomVO);

        EngineeringRoutingVO routingVO = routingService.getRoutingItems(product.getCurrentRouteId());
        fullVO.setRouting(routingVO);

        ProductCategoryVO category = categoryService.getCategory(product.getCategoryId());
        fullVO.setCategory(category);

        List<EngineeringFilmVO> films = filmService.getFilmsByProductId(productId);
        fullVO.setFilms(films);

        return fullVO;
    }

    @Event("product.submitted")
    @Override
    public boolean submitProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if(product==null){
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }
        Integer currentStatus = product.getProductStatus();
        // 开发中(1)/已驳回(5)/取消(8) 均可提交审核（与前端列表枚举一致）
        if(!Objects.equals(ProductEnums.Status.DEVELOPING.getValue(), currentStatus)
                && !Objects.equals(ProductEnums.Status.REJECTED.getValue(), currentStatus)
                && !Objects.equals(ProductEnums.Status.CANCELLED.getValue(), currentStatus)){
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_CANNOT_SUBMIT);
        }
        ProductUpdateDTO dto = new ProductUpdateDTO();
        dto.setProductId(productId);
        dto.setApproveRemark("");
        dto.setCurrentStatus(currentStatus);
        dto.setTargetStatus(ProductEnums.Status.PENDING.getValue());
        return updateStatus(dto);
    }

    @Event("product.approved")
    @Override
    public boolean approveProduct(ProductUpdateDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if(product==null){
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }
        if(!Objects.equals(ProductEnums.Status.PENDING.getValue(), product.getProductStatus())){
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_CANNOT_APPROVED);
        }
        dto.setProductName(product.getProductName());
        dto.setCurrentStatus(ProductEnums.Status.PENDING.getValue());
        dto.setTargetStatus(ProductEnums.Status.APPROVED.getValue());
        boolean updated = updateStatus(dto);
        return updated;
    }

    @Override
    public boolean rejectProduct(ProductUpdateDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if(product==null){
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }
        if(!Objects.equals(ProductEnums.Status.PENDING.getValue(), product.getProductStatus())){
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_CANNOT_REJECT);
        }
        dto.setCurrentStatus(ProductEnums.Status.PENDING.getValue());
        dto.setTargetStatus(ProductEnums.Status.REJECTED.getValue());
        return updateStatus(dto);
    }

    private boolean updateStatus(ProductUpdateDTO dto){
        LambdaUpdateWrapper<Product> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(Product::getProductStatus,dto.getTargetStatus())
                .set(StringUtils.isNotBlank(dto.getApproveRemark()),Product::getApproveRemark,dto.getApproveRemark())
                .eq(Product::getProductId,dto.getProductId())
                .eq(Product::getProductStatus,dto.getCurrentStatus());
        return productMapper.update(updateWrapper)>0;

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        Integer currentStatus = product.getProductStatus();
        // 取消操作：待审核(2) → 取消(8)，已通过(4) → 取消(8)，已驳回(5) → 取消(8)
        if (!Objects.equals(ProductEnums.Status.PENDING.getValue(), currentStatus)
                && !Objects.equals(ProductEnums.Status.APPROVED.getValue(), currentStatus)
                && !Objects.equals(ProductEnums.Status.REJECTED.getValue(), currentStatus)) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_CANNOT_CANCEL);
        }

        product.setProductStatus(ProductEnums.Status.CANCELLED.getValue());
        return productMapper.updateById(product) > 0;
    }

    @Override
    public String generateSerialNo(Long customerId) {
        // 编码格式：客户简称(1-3位) + 流水号(3位) + 面板结构(2位) + 线路结构(2位)
        // 2026-08-12：流水号逻辑统一走 ProductCodeService（兼容简称1-3位，正则提取）
        String shortName = null;
        if (customerId != null) {
            try {
                com.jjx.sales.domain.entity.SalesCustomer customer = salesCustomerMapper.selectById(customerId);
                if (customer != null && customer.getCustomerShortName() != null && !customer.getCustomerShortName().isEmpty()) {
                    shortName = customer.getCustomerShortName().trim();
                }
            } catch (Exception ignored) { }
        }
        return productCodeService.nextSerial(shortName);
    }

    @Override
    public Long ensureDraftProduct(String productCode, String productName, String unit, String source) {
        if (productCode == null || productCode.isBlank()) return null;
        String code = productCode.trim();
        Product exist = productMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Product>()
                        .eq(Product::getProductCode, code).last("LIMIT 1"));
        if (exist != null) return exist.getProductId();
        Product p = new Product();
        p.setProductCode(code);
        p.setProductName(productName != null && !productName.isBlank() ? productName : code);
        p.setProductStatus(ProductEnums.Status.DEVELOPING.getValue());
        p.setUnit(unit != null && !unit.isBlank() ? unit : "PCS");
        p.setFromSource(source);
        p.setCreateBy(com.jjx.system.utils.SecurityUtils.getUsername());
        productMapper.insert(p);
        return p.getProductId();
    }

    @Override
    public boolean cleanupDraftProduct(Long productId, String source) {
        if (productId == null) return false;
        Product p = productMapper.selectById(productId);
        if (p == null) return false;
        if (source == null || !source.equals(p.getFromSource())) return false;
        if (!ProductEnums.Status.DEVELOPING.getValue().equals(p.getProductStatus())) return false;
        // 单据明细引用检查（报价明细/订单明细仍引用则不动）
        Long qRef = quotationItemMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesQuotationItem>()
                        .eq(com.jjx.sales.domain.entity.SalesQuotationItem::getProductId, productId));
        Long oRef = orderProductMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesOrderProduct>()
                        .eq(com.jjx.sales.domain.entity.SalesOrderProduct::getProductId, productId));
        if (qRef != null && qRef > 0 || oRef != null && oRef > 0) return false;
        Product upd = new Product();
        upd.setProductId(productId);
        upd.setProductStatus(ProductEnums.Status.CANCELLED.getValue());
        productMapper.updateById(upd);
        return true;
    }

}
