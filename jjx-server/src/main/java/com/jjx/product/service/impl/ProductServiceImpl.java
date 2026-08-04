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
import com.jjx.product.domain.entity.EngineeringBom;
import com.jjx.product.domain.entity.EngineeringRouting;
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
    private final ProductConverter productConverter;
    private final IEngineeringBomService bomService;
    private final IEngineeringRoutingService routingService;
    private final IProductCategoryService categoryService;
    private final IEngineeringFilmService filmService;
    private final NotificationService notificationService;
    private final com.jjx.inventory.mapper.InventoryMaterialMapper inventoryMaterialMapper;
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
        if (product.getCurrentBomId() == null) {
            throw new BusinessException(BusinessExceptionEnum.BOM_NOT_FOUND);
        }
        EngineeringBom bom = bomService.getById(product.getCurrentBomId());
        if (bom == null || !ProductEnums.BomStatus.APPROVED.getValue().equals(bom.getApproveStatus())) {
            throw new BusinessException("当前BOM未审批通过，无法发布产品");
        }

        if (product.getCurrentRouteId() == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }
        EngineeringRouting routing = routingService.getById(product.getCurrentRouteId());
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
        if(!Objects.equals(ProductEnums.Status.DEVELOPING.getValue(), product.getProductStatus())){
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_CANNOT_SUBMIT);
        }
        ProductUpdateDTO dto = new ProductUpdateDTO();
        dto.setProductId(productId);
        dto.setApproveRemark("");
        dto.setCurrentStatus(ProductEnums.Status.DEVELOPING.getValue());
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
        // 查询该客户已有的最大流水号
        // 编码格式：客户简称(3位) + 流水号(3位) + 面板结构(2位) + 线路结构(2位)
        // 流水号是3位数字，从001开始
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(Product::getProductCode, "___"); // 匹配前3位任意字符
        wrapper.orderByDesc(Product::getProductCode);
        wrapper.last("LIMIT 1");

        Product lastProduct = productMapper.selectOne(wrapper);
        if (lastProduct != null && lastProduct.getProductCode() != null && lastProduct.getProductCode().length() >= 6) {
            try {
                // 取第4-6位作为流水号
                String lastSerial = lastProduct.getProductCode().substring(3, 6);
                int nextSerial = Integer.parseInt(lastSerial) + 1;
                if (nextSerial > 999) {
                    nextSerial = 1;
                }
                return String.format("%03d", nextSerial);
            } catch (NumberFormatException e) {
                return "001";
            }
        }
        return "001";
    }

}
