package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.system.annotation.Event;
import com.jjx.product.domain.converter.ProductBomConverter;
import com.jjx.product.domain.dto.ProductBomDTO;
import com.jjx.product.domain.dto.ProductBomItemDTO;
import com.jjx.product.domain.dto.UpdateBomStatusDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.ProductBom;
import com.jjx.product.domain.entity.ProductBomItem;
import com.jjx.product.domain.query.ProductBomQuery;
import com.jjx.product.domain.vo.ProductBomVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.ProductBomItemMapper;
import com.jjx.product.mapper.ProductBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IProductBomService;
import lombok.NonNull;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * BOM Service实现
 */
@Service
public class ProductBomServiceImpl extends ServiceImpl<ProductBomMapper,ProductBom> implements IProductBomService {

    private final ProductBomMapper productBomMapper;
    private final ProductBomItemMapper productBomItemMapper;
    private final ProductMapper productMapper;
    private final ProductBomConverter bomConverter;
    public ProductBomServiceImpl(ProductBomMapper productBomMapper,
                                 ProductBomItemMapper productBomItemMapper,
                                 ProductMapper productMapper, ProductBomConverter bomConverter) {
        this.productBomMapper = productBomMapper;
        this.productBomItemMapper = productBomItemMapper;
        this.productMapper = productMapper;
        this.bomConverter = bomConverter;
    }

    @Override
    public List<ProductBomVO> getBomList(ProductBomQuery query) {
        LambdaQueryWrapper<ProductBom> wrapper = buildQueryWrapper(query);
        List<ProductBom> productBoms = productBomMapper.selectList(wrapper);
        return bomConverter.toVOList(productBoms);
    }


    @Override
    public PageResult<ProductBomVO> getBomListPage(ProductBomQuery query) {
        LambdaQueryWrapper<ProductBom> wrapper = buildQueryWrapper(query);
        IPage<ProductBom> page = new Page<>(query.getPageNum(),query.getPageSize());
        IPage<ProductBom> productBomIPage = productBomMapper.selectPage(page, wrapper);
        List<ProductBom> records = productBomIPage.getRecords();
        return PageResult.build(bomConverter.toVOList(records),productBomIPage.getTotal());
    }

    @Override
    public ProductBomVO getBomDetail(Long bomId) {
        ProductBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            return null;
        }

        ProductBomVO vo = bomConverter.toVO(bom);

        // 获取BOM明细
        List<ProductBomItem> items = getBomItems(bomId);
        vo.setItems(items);

        // 获取产品信息
        if (bom.getProductId() != null) {
            Product product = productMapper.selectById(bom.getProductId());
            if (product != null) {
                vo.setProductCode(product.getProductCode());
                vo.setProductName(product.getProductName());
            }
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createBom(ProductBomDTO dto) {

        ProductBom bom = bomConverter.toEntity(dto);
        bom.setApproveStatus(ApproveStatusEnum.DRAFT.getCode());

        // 生成BOM版本
        generateBomVersion(dto.getProductId(), bom);

        // 保存BOM主表
        if (!saveBom(bom)) {
            return false;
        }

        // 保存BOM明细
        saveBomItems(dto.getItems(), bom.getBomId());

        // 如果是当前版本，更新其他版本为非当前
        if (isCurrentVersion(bom)) {
            setOtherBomNotCurrent(bom.getProductId(), bom.getBomId());
        }

        return true;
    }

// ==================== 提取的方法 ====================

    /**
     * 生成BOM版本号
     */
    private void generateBomVersion(Long productId, ProductBom bom) {
        if (StringUtils.isNotBlank(bom.getBomVersion())) {
            return;
        }

        ProductBom latestBom = productBomMapper.selectLatestVersion(productId);
        String version = getNextVersion(latestBom);
        bom.setBomVersion(version);
    }

    /**
     * 根据最新BOM获取下一个版本号
     */
    private String getNextVersion(ProductBom latestBom) {
        if (latestBom == null || StringUtils.isBlank(latestBom.getBomVersion())) {
            return "V1.0";
        }

        String latestVersion = latestBom.getBomVersion();
        try {
            int versionNum = Integer.parseInt(latestVersion.replace("V", "").replace(".0", ""));
            return "V" + (versionNum + 1) + ".0";
        } catch (NumberFormatException e) {
            return "V1.0";
        }
    }

    /**
     * 保存BOM主表
     */
    private boolean saveBom(ProductBom bom) {
        return productBomMapper.insert(bom) > 0;
    }

    /**
     * 保存BOM明细列表
     */
    private void saveBomItems(List<ProductBomItemDTO> items, Long bomId) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (ProductBomItemDTO itemDTO : items) {
            ProductBomItem item = toBomItemEntity(itemDTO);
            if (item == null) {
                continue;
            }
            item.setBomId(bomId);
            productBomItemMapper.insert(item);
        }
    }

    /**
     * 判断是否为当前版本
     */
    private boolean isCurrentVersion(ProductBom bom) {
        return bom.getIsCurrent() != null && bom.getIsCurrent();
    }

    private void setOtherBomNotCurrent(Long productId, Long bomId) {
        LambdaUpdateWrapper<ProductBom> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ProductBom::getIsCurrent, YesNoEnum.NO.getCode())
                .eq(ProductBom::getProductId,productId)
                .ne(ProductBom::getBomId,bomId);
        productBomMapper.update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBom(ProductBomDTO dto) {
        if (dto.getBomId() == null) {
            throw new BusinessException("BOM ID不能为空");
        }

        // 检查BOM是否存在
        ProductBom existingBom = productBomMapper.selectById(dto.getBomId());
        if (existingBom == null) {
            throw new BusinessException("BOM不存在");
        }

        // 如果BOM已审批，不允许修改
        boolean editable = ProductEnums.BomStatus.fromValue(existingBom.getApproveStatus()).isEditable();
        if (!editable) {
            throw new BusinessException("BOM已审批，不允许修改");
        }

        // DTO转Entity
        ProductBom bom = bomConverter.toEntity(dto);

        // 更新BOM主表
        boolean bomUpdated = productBomMapper.updateById(bom) > 0;
        if (!bomUpdated) {
            return false;
        }

        // 删除旧的BOM明细
        productBomItemMapper.deleteByBomId(dto.getBomId());

        // 保存新的BOM明细
        if (ObjectUtils.isNotEmpty(dto.getItems())) {
            for (ProductBomItemDTO itemDTO : dto.getItems()) {
                ProductBomItem item = toBomItemEntity(itemDTO);
                item.setBomId(dto.getBomId());
                productBomItemMapper.insert(item);
            }
        }

        // 如果是当前版本，更新其他版本为非当前
        if (bom.getIsCurrent() != null && bom.getIsCurrent()) {
            setOtherBomNotCurrent(bom.getProductId(), bom.getBomId());
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeBomWithItems(Long bomId) {
        // 检查BOM是否存在
        ProductBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            return false;
        }

        // 如果BOM已审批，不允许删除
        if ("approved".equals(bom.getApproveStatus())) {
            throw new BusinessException("BOM已审批，不允许删除");
        }

        // 删除BOM明细
        productBomItemMapper.deleteByBomId(bomId);

        // 删除BOM主表
        return productBomMapper.deleteById(bomId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefaultBom(Long bomId) {
        ProductBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            return false;
        }

        // 设置当前BOM为默认
        bom.setIsCurrent(true);
        productBomMapper.updateById(bom);

        // 将其他BOM设置为非默认
        setOtherBomNotCurrent(bom.getProductId(), bomId);

        return true;
    }

    @Override
    public ProductBom getDefaultBomByProductId(Long productId) {
        LambdaQueryWrapper<ProductBom> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductBom::getProductId,productId)
                .eq(ProductBom::getIsCurrent,YesNoEnum.YES.getCode());
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public List<ProductBomItem> getBomItems(Long bomId) {
        LambdaQueryWrapper<ProductBomItem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductBomItem::getBomId,bomId);
        return productBomItemMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateBomCost(Long bomId) {
        // 这里实现BOM成本计算逻辑
        // 1. 获取BOM明细
        List<ProductBomItem> items = getBomItems(bomId);

        // 2. 计算物料成本（需要调用库存模块获取物料单价）
        final double materialCost = 0.0;
        for (ProductBomItem item : items) {
            // 这里需要调用库存模块获取物料单价
            // double unitPrice = materialService.getUnitPrice(item.getMaterialId());
            // materialCost += unitPrice * item.getQuantity() * (1 + item.getLossRate() / 100);
        }

        // 3. 计算人工成本和制造费用（需要调用工艺路线模块）
        // 这里暂时不实现

        // 4. 更新BOM成本信息（如果有成本字段的话）
        // 实际项目中BOM表可能有成本字段，这里只是示例
    }

    @Override public boolean checkBomCodeUnique(String bomCode, String bomVersion, Long bomId) {
//        ProductBom bom = productBomMapper.selectByCodeAndVersion(bomCode, bomVersion);
//        if (bom == null) {
//            return true;
//        }
//        if (bomId != null && bom.getBomId().equals(bomId)) {
//            return true;
//        }
        return true;
    }

    @Override
    public PageResult<ProductBomVO> listPage(ProductBomQuery query) {
        // 计算偏移量
        int offset = (query.getPageNum() - 1) * query.getPageSize();

        // 查询总数
        long total = productBomMapper.selectBomCount(query);

        // 查询分页数据
        List<ProductBomVO> records = productBomMapper.selectBomList(query, offset);
        return PageResult.build(records, total);
    }

    @Event("bom.submitted")
    @Override
    public boolean submitApprove(Long bomId) {
        ProductBom bom = productBomMapper.selectById(bomId);
        if (bom == null) throw new BusinessException("BOM不存在");
        if (!Objects.equals(bom.getApproveStatus(), ProductEnums.BomStatus.DRAFT.getValue())) {
            throw new BusinessException("只有草稿状态的BOM才能提交审批");
        }
        LambdaQueryWrapper<ProductBomItem> checkItems = new LambdaQueryWrapper<>();
        checkItems.eq(ProductBomItem::getBomId, bomId);
        if (!productBomItemMapper.exists(checkItems)) {
            throw new BusinessException("BOM明细不能为空");
        }
        // 用 updateStatus 改为 PENDING
        UpdateBomStatusDTO dto = new UpdateBomStatusDTO();
        dto.setBomId(bomId);
        dto.setCurrent(ProductEnums.BomStatus.DRAFT.getValue());
        dto.setTarget(ProductEnums.BomStatus.REVIEWING.getValue());
        return updateStatus(dto);
    }

    @Event("bom.approved")
    @Override
    public boolean approve(UpdateBomStatusDTO dto) {
        ProductBom productBom = productBomMapper.selectById(dto.getBomId());
        dto.setCurrent(ProductEnums.BomStatus.REVIEWING.getValue());
        dto.setTarget(ProductEnums.BomStatus.APPROVED.getValue());
        return updateStatus(dto);
    }



    @Override
    public boolean updateStatus(UpdateBomStatusDTO dto) {
        LambdaUpdateWrapper<ProductBom> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(ProductBom::getApproveStatus,dto.getTarget())
                .set(StringUtils.isNotBlank(dto.getRemark()),ProductBom::getApproveRemark,dto.getRemark())
                .eq(ProductBom::getBomId,dto.getBomId())
                .eq(ProductBom::getApproveStatus,dto.getCurrent());
        return baseMapper.update(updateWrapper)>0;
    }

    @Override
    public boolean reject(UpdateBomStatusDTO dto) {
        ProductBom productBom = productBomMapper.selectById(dto.getBomId());
        if (!Objects.equals(productBom.getApproveStatus(), ProductEnums.BomStatus.DRAFT.getValue())) {
            return false;
        }
        dto.setCurrent(ProductEnums.BomStatus.DRAFT.getValue());
        dto.setTarget(ProductEnums.BomStatus.REJECT.getValue());
        return updateStatus(dto);
    }

    /**
     * ProductBomItemDTO 转 ProductBomItem 实体
     */
    private ProductBomItem toBomItemEntity(ProductBomItemDTO dto) {
        if (dto == null) {
            return null;
        }
        ProductBomItem item = new ProductBomItem();
        item.setItemId(dto.getItemId());
        item.setMaterialId(dto.getMaterialId());
        item.setMaterialCode(dto.getMaterialCode());
        item.setMaterialName(dto.getMaterialName());
        item.setSpecification(dto.getSpecification());
        item.setUnit(dto.getUnit());
        item.setQuantity(dto.getQuantity());
        item.setLossRate(dto.getLossRate());
        item.setModuleQty(dto.getModuleQty());
        item.setBaseQty(dto.getBaseQty());
        item.setMinIssueQty(dto.getMinIssueQty());
        item.setWidthMm(dto.getWidthMm());
        item.setLengthMm(dto.getLengthMm());
        item.setLayer(dto.getLayer());
        item.setPositionNo(dto.getPositionNo());
        item.setSourceType(dto.getSourceType());
        item.setSubstituteJson(dto.getSubstituteJson());
        item.setItemOrder(dto.getItemOrder());
        item.setRemark(dto.getRemark());
        return item;
    }

    private static @NonNull LambdaQueryWrapper<ProductBom> buildQueryWrapper(ProductBomQuery query) {
        // 创建查询条件
        LambdaQueryWrapper<ProductBom> wrapper = new LambdaQueryWrapper<>();
        // BOM编码查询
        if (StringUtils.isNotBlank(query.getBomCode())) {
            wrapper.like(ProductBom::getBomCode, query.getBomCode());
        }

        // 产品ID查询
        if (query.getProductId() != null) {
            wrapper.eq(ProductBom::getProductId, query.getProductId());
        }

        // BOM版本查询
        if (StringUtils.isNotBlank(query.getBomVersion())) {
            wrapper.eq(ProductBom::getBomVersion, query.getBomVersion());
        }

        // 是否当前版本查询
        if (query.getIsCurrent() != null) {
            wrapper.eq(ProductBom::getIsCurrent, query.getIsCurrent());
        }

        // 审批状态查询
        if (StringUtils.isNotBlank(query.getApproveStatus())) {
            wrapper.eq(ProductBom::getApproveStatus, query.getApproveStatus());
        }
        return wrapper;
    }
}
