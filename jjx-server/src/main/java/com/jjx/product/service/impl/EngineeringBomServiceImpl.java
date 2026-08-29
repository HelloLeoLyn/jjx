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
import com.jjx.product.domain.converter.EngineeringBomConverter;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.EngineeringBomItemDTO;
import com.jjx.product.domain.dto.UpdateBomStatusDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.domain.query.EngineeringBomQuery;
import com.jjx.product.domain.vo.EngineeringBomVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.system.service.ReviewFlowService;
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
public class EngineeringBomServiceImpl extends ServiceImpl<EngineeringBomMapper,EngineeringBom> implements IEngineeringBomService {

    private final EngineeringBomMapper productBomMapper;
    private final EngineeringBomItemMapper productBomItemMapper;
    private final ProductMapper productMapper;
    private final EngineeringBomConverter bomConverter;
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    private final com.jjx.inventory.mapper.InventoryMaterialMapper inventoryMaterialMapper;
    private final ReviewFlowService reviewFlowService;
    private final com.jjx.system.service.OperLogChangeRecorder changeRecorder;
    public EngineeringBomServiceImpl(EngineeringBomMapper productBomMapper,
                                 EngineeringBomItemMapper productBomItemMapper,
                                 ProductMapper productMapper, EngineeringBomConverter bomConverter,
                                 com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper,
                                 com.jjx.inventory.mapper.InventoryMaterialMapper inventoryMaterialMapper,
                                 ReviewFlowService reviewFlowService,
                                 com.jjx.system.service.OperLogChangeRecorder changeRecorder) {
        this.productBomMapper = productBomMapper;
        this.productBomItemMapper = productBomItemMapper;
        this.productMapper = productMapper;
        this.bomConverter = bomConverter;
        this.productionOrderMapper = productionOrderMapper;
        this.inventoryMaterialMapper = inventoryMaterialMapper;
        this.reviewFlowService = reviewFlowService;
        this.changeRecorder = changeRecorder;
    }

    @Override
    public List<EngineeringBomVO> getBomList(EngineeringBomQuery query) {
        LambdaQueryWrapper<EngineeringBom> wrapper = buildQueryWrapper(query);
        List<EngineeringBom> productBoms = productBomMapper.selectList(wrapper);
        return bomConverter.toVOList(productBoms);
    }


    @Override
    public PageResult<EngineeringBomVO> getBomListPage(EngineeringBomQuery query) {
        LambdaQueryWrapper<EngineeringBom> wrapper = buildQueryWrapper(query);
        IPage<EngineeringBom> page = new Page<>(query.getPageNum(),query.getPageSize());
        IPage<EngineeringBom> productBomIPage = productBomMapper.selectPage(page, wrapper);
        List<EngineeringBom> records = productBomIPage.getRecords();
        return PageResult.build(bomConverter.toVOList(records),productBomIPage.getTotal());
    }

    @Override
    public EngineeringBomVO getBomDetail(Long bomId) {
        EngineeringBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            return null;
        }

        EngineeringBomVO vo = bomConverter.toVO(bom);

        // 获取BOM明细
        List<EngineeringBomItem> items = getBomItems(bomId);
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
    public boolean createBom(EngineeringBomDTO dto) {

        EngineeringBom bom = bomConverter.toEntity(dto);
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
    private void generateBomVersion(Long productId, EngineeringBom bom) {
        if (StringUtils.isNotBlank(bom.getBomVersion())) {
            return;
        }

        // 2026-08-10 DEV-765：查该产品所有版本号，统一走公共工具类（比原 selectLatestVersion+replace 更健壮）
        java.util.List<String> versions = productBomMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EngineeringBom>()
                                .eq(EngineeringBom::getProductId, productId))
                .stream()
                .map(b -> b.getVersion() != null ? b.getVersion() : b.getBomVersion())
                .collect(java.util.stream.Collectors.toList());
        bom.setBomVersion(com.jjx.common.utils.VersionUtils.next(versions));
        bom.setVersion(bom.getBomVersion()); // 2026-08-10 DEV-769：双字段同步，统一语义
    }

    /**
     * 保存BOM主表
     */
    private boolean saveBom(EngineeringBom bom) {
        return productBomMapper.insert(bom) > 0;
    }

    /**
     * 保存BOM明细列表
     */
    private void saveBomItems(List<EngineeringBomItemDTO> items, Long bomId) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (EngineeringBomItemDTO itemDTO : items) {
            EngineeringBomItem item = toBomItemEntity(itemDTO);
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
    private boolean isCurrentVersion(EngineeringBom bom) {
        return bom.getIsCurrent() != null && bom.getIsCurrent();
    }

    private void setOtherBomNotCurrent(Long productId, Long bomId) {
        LambdaUpdateWrapper<EngineeringBom> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(EngineeringBom::getIsCurrent, YesNoEnum.NO.getCode())
                .eq(EngineeringBom::getProductId,productId)
                .ne(EngineeringBom::getBomId,bomId);
        productBomMapper.update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBom(EngineeringBomDTO dto) {
        return updateBomWithDetail(dto).isSuccess();
    }

    /**
     * 更新BOM（含变更明细，供 @Log detail 展示）
     */
    @Override
    public com.jjx.product.domain.vo.EngineeringBomEditVO updateBomWithDetail(EngineeringBomDTO dto) {
        if (dto.getBomId() == null) {
            throw new BusinessException("BOM ID不能为空");
        }

        // 检查BOM是否存在
        EngineeringBom existingBom = productBomMapper.selectById(dto.getBomId());
        if (existingBom == null) {
            throw new BusinessException("BOM不存在");
        }

        // 如果BOM已审批，不允许修改
        boolean editable = ProductEnums.BomStatus.fromValue(existingBom.getApproveStatus()).isEditable();
        if (!editable) {
            throw new BusinessException("BOM已审批，不允许修改");
        }

        // 变更明细：主表 + 明细行对比（保存前采集）
        List<String> changes = new java.util.ArrayList<>();
        try {
            buildBomDiff(changes, existingBom, dto);
        } catch (Exception e) {
            log.warn("BOM变更明细生成失败: " + e.getMessage());
        }

        // DTO转Entity
        EngineeringBom bom = bomConverter.toEntity(dto);

        // 更新BOM主表
        boolean bomUpdated = productBomMapper.updateById(bom) > 0;
        if (!bomUpdated) {
            com.jjx.product.domain.vo.EngineeringBomEditVO failVo = new com.jjx.product.domain.vo.EngineeringBomEditVO();
            failVo.setSuccess(false);
            return failVo;
        }

        // 删除旧的BOM明细
        productBomItemMapper.deleteByBomId(dto.getBomId());

        // 保存新的BOM明细（父子关系：旧itemId → 新itemId 映射转换）
        if (ObjectUtils.isNotEmpty(dto.getItems())) {
            java.util.Map<Long, Long> idMap = new java.util.HashMap<>();
            for (EngineeringBomItemDTO itemDTO : dto.getItems()) {
                Long oldId = itemDTO.getItemId();
                EngineeringBomItem item = toBomItemEntity(itemDTO);
                item.setItemId(null);
                item.setBomId(dto.getBomId());
                productBomItemMapper.insert(item);
                if (oldId != null) {
                    idMap.put(oldId, item.getItemId());
                }
            }
            // 第二遍：修正 parentMaterialId（旧ID → 新ID）
            java.util.List<EngineeringBomItem> saved = productBomItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EngineeringBomItem>()
                            .eq(EngineeringBomItem::getBomId, dto.getBomId()));
            boolean needUpdate = false;
            for (EngineeringBomItem it : saved) {
                if (it.getParentMaterialId() != null && idMap.containsKey(it.getParentMaterialId())) {
                    it.setParentMaterialId(idMap.get(it.getParentMaterialId()));
                    needUpdate = true;
                }
            }
            if (needUpdate) {
                for (EngineeringBomItem it : saved) {
                    productBomItemMapper.updateById(it);
                }
            }
        }

        // 如果是当前版本，更新其他版本为非当前
        if (bom.getIsCurrent() != null && bom.getIsCurrent()) {
            setOtherBomNotCurrent(bom.getProductId(), bom.getBomId());
        }

        com.jjx.product.domain.vo.EngineeringBomEditVO vo = new com.jjx.product.domain.vo.EngineeringBomEditVO();
        vo.setSuccess(true);
        vo.setDetailMessage(changes.isEmpty() ? null : changeRecorder.toDetailJson(changes));
        return vo;
    }

    /**
     * BOM 变更对比：主表字段 + 明细行（按 materialId 键对比，明细为全量替换）
     */
    private void buildBomDiff(List<String> changes, EngineeringBom old, EngineeringBomDTO dto) {
        changeRecorder.diff(changes, "BOM版本", old.getBomVersion(), dto.getBomVersion());
        changeRecorder.diff(changes, "BOM名称", old.getBomName(), dto.getBomName());
        changeRecorder.diff(changes, "备注", old.getRemark(), dto.getRemark());
        if (old.getEffectiveDate() != null || dto.getEffectiveDate() != null) {
            changeRecorder.diff(changes, "生效日期",
                    changeRecorder.fmtDate(old.getEffectiveDate()),
                    changeRecorder.fmtDate(dto.getEffectiveDate()));
        }
        if (old.getExpiryDate() != null || dto.getExpiryDate() != null) {
            changeRecorder.diff(changes, "失效日期",
                    changeRecorder.fmtDate(old.getExpiryDate()),
                    changeRecorder.fmtDate(dto.getExpiryDate()));
        }
        // 明细对比（全量替换：旧行集合 vs 新行集合）
        java.util.List<EngineeringBomItem> oldItems = productBomItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EngineeringBomItem>()
                        .eq(EngineeringBomItem::getBomId, dto.getBomId()));
        java.util.Map<Long, EngineeringBomItem> oldByMat = new java.util.HashMap<>();
        for (EngineeringBomItem it : oldItems) {
            if (it.getMaterialId() != null) oldByMat.put(it.getMaterialId(), it);
        }
        java.util.Map<Long, EngineeringBomItemDTO> newByMat = new java.util.HashMap<>();
        if (dto.getItems() != null) {
            for (EngineeringBomItemDTO it : dto.getItems()) {
                if (it.getMaterialId() != null) newByMat.put(it.getMaterialId(), it);
            }
        }
        for (java.util.Map.Entry<Long, EngineeringBomItemDTO> e : newByMat.entrySet()) {
            EngineeringBomItem oldIt = oldByMat.get(e.getKey());
            if (oldIt == null) {
                changes.add("新增物料:" + matLabel(e.getValue()));
            } else {
                changeRecorder.diff(changes, "用量(" + matLabel(e.getValue()) + ")",
                        oldIt.getQuantity(), e.getValue().getQuantity());
            }
        }
        for (EngineeringBomItem oldIt : oldItems) {
            if (oldIt.getMaterialId() != null && !newByMat.containsKey(oldIt.getMaterialId())) {
                changes.add("移除物料:" + matLabel(oldIt));
            }
        }
    }

    private String matLabel(EngineeringBomItem it) {
        String code = it.getMaterialCode() != null ? it.getMaterialCode()
                : (it.getMaterialName() != null ? it.getMaterialName() : String.valueOf(it.getMaterialId()));
        return code + "(id:" + it.getMaterialId() + ")";
    }

    private String matLabel(EngineeringBomItemDTO it) {
        String code = it.getMaterialCode() != null ? it.getMaterialCode()
                : (it.getMaterialName() != null ? it.getMaterialName() : String.valueOf(it.getMaterialId()));
        return code + "(id:" + it.getMaterialId() + ")";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeBomWithItems(Long bomId) {
        // 检查BOM是否存在
        EngineeringBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            return false;
        }

        // 状态校验（2026-08-08 修复：原 "approved".equals(Integer) 永远不生效，已批准BOM可删）
        Integer st = bom.getApproveStatus();
        if (st != null && st == 3) {
            throw new BusinessException("BOM已批准，不允许删除（如需废弃请走版本化/作废）");
        }
        if (st != null && st == 2) {
            throw new BusinessException("BOM审核中，不允许删除");
        }

        // 被引用检查（2026-08-08：产品 current_bom_id / 生产工单 bom_id）
        Long prodRef = productMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.product.domain.entity.Product>()
                        .eq(com.jjx.product.domain.entity.Product::getCurrentBomId, bomId));
        Long orderRef = productionOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.jjx.production.domain.entity.ProductionOrder>()
                        .eq(com.jjx.production.domain.entity.ProductionOrder::getBomId, bomId));
        if (prodRef != null && prodRef > 0) {
            throw new BusinessException("BOM已被产品档案引用（current_bom_id），不允许删除");
        }
        if (orderRef != null && orderRef > 0) {
            throw new BusinessException("BOM已被生产工单引用，不允许删除");
        }

        // 删除BOM明细
        productBomItemMapper.deleteByBomId(bomId);

        // 删除BOM主表
        return productBomMapper.deleteById(bomId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefaultBom(Long bomId) {
        EngineeringBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            return false;
        }

        // 设置当前BOM为默认
        bom.setIsCurrent(true);
        productBomMapper.updateById(bom);

        // 将其他BOM设置为非默认
        setOtherBomNotCurrent(bom.getProductId(), bomId);

        // DEV-771：同步产品 current_bom_id 指针（发布校验用）
        if (bom.getProductId() != null) {
            com.jjx.product.domain.entity.Product product = productMapper.selectById(bom.getProductId());
            if (product != null) {
                product.setCurrentBomId(bomId);
                product.setCurrentBomVersion(bom.getVersion() != null ? bom.getVersion() : bom.getBomVersion());
                productMapper.updateById(product);
            }
        }

        return true;
    }

    @Override
    public EngineeringBom getDefaultBomByProductId(Long productId) {
        LambdaQueryWrapper<EngineeringBom> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EngineeringBom::getProductId,productId)
                .eq(EngineeringBom::getIsCurrent,YesNoEnum.YES.getCode());
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public List<EngineeringBomItem> getBomItems(Long bomId) {
        LambdaQueryWrapper<EngineeringBomItem> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EngineeringBomItem::getBomId,bomId);
        List<EngineeringBomItem> items = productBomItemMapper.selectList(queryWrapper);
        // 带出物料类型（R=板材/卷材，前端展示用）
        if (items != null) {
            for (EngineeringBomItem it : items) {
                if (it.getMaterialId() != null) {
                    try {
                        com.jjx.inventory.domain.InventoryMaterial mat = inventoryMaterialMapper.selectById(it.getMaterialId());
                        if (mat != null) it.setMaterialType(mat.getMaterialType());
                    } catch (Exception ignored) { }
                }
            }
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateBomCost(Long bomId) {
        // 这里实现BOM成本计算逻辑
        // 1. 获取BOM明细
        List<EngineeringBomItem> items = getBomItems(bomId);

        // 2. 计算物料成本（需要调用库存模块获取物料单价）
        final double materialCost = 0.0;
        for (EngineeringBomItem item : items) {
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
//        EngineeringBom bom = productBomMapper.selectByCodeAndVersion(bomCode, bomVersion);
//        if (bom == null) {
//            return true;
//        }
//        if (bomId != null && bom.getBomId().equals(bomId)) {
//            return true;
//        }
        return true;
    }

    /**
     * 复制为新版本（DEV-619）
     * 参照工艺路线 copyAsNewVersion：新版本号、明细复制、isCurrent=false（审批通过后由 set-current/setDefault 切换）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineeringBomVO copyAsNewVersion(Long bomId, String newVersion) {
        EngineeringBom oldBom = productBomMapper.selectById(bomId);
        if (oldBom == null) {
            throw new BusinessException("BOM不存在");
        }
        if (StringUtils.isBlank(newVersion)) {
            throw new BusinessException("新版本号不能为空");
        }
        // 同编码下版本号唯一性校验
        Long dupCount = productBomMapper.selectCount(new LambdaQueryWrapper<EngineeringBom>()
                .eq(EngineeringBom::getBomCode, oldBom.getBomCode())
                .eq(EngineeringBom::getBomVersion, newVersion));
        if (dupCount != null && dupCount > 0) {
            throw new BusinessException("版本号已存在：" + newVersion);
        }

        // 复制主记录
        EngineeringBom newBom = new EngineeringBom();
        cn.hutool.core.bean.BeanUtil.copyProperties(oldBom, newBom);
        newBom.setBomId(null);
        newBom.setBomVersion(newVersion);
        newBom.setVersion(newVersion); // 2026-08-10 DEV-769：双字段同步，统一语义
        newBom.setApproveStatus(ApproveStatusEnum.DRAFT.getCode());
        newBom.setIsCurrent(false);
        productBomMapper.insert(newBom);

        // 复制明细
        List<EngineeringBomItem> oldItems = productBomItemMapper.selectList(
                new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bomId));
        for (EngineeringBomItem item : oldItems) {
            EngineeringBomItem newItem = new EngineeringBomItem();
            cn.hutool.core.bean.BeanUtil.copyProperties(item, newItem);
            newItem.setItemId(null);
            newItem.setBomId(newBom.getBomId());
            productBomItemMapper.insert(newItem);
        }

        calculateBomCost(newBom.getBomId());
        return getBomDetail(newBom.getBomId());
    }

    @Override
    public PageResult<EngineeringBomVO> listPage(EngineeringBomQuery query) {
        // 计算偏移量
        int offset = (query.getPageNum() - 1) * query.getPageSize();

        // 查询总数
        long total = productBomMapper.selectBomCount(query);

        // 查询分页数据
        List<EngineeringBomVO> records = productBomMapper.selectBomList(query, offset);
        return PageResult.build(records, total);
    }

    @Event("bom.submitted")
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean submitApprove(Long bomId) {
        EngineeringBom bom = productBomMapper.selectById(bomId);
        if (bom == null) throw new BusinessException("BOM不存在");
        Integer current = bom.getApproveStatus();
        // 草稿(1)与已驳回(4)均可提交审批（驳回后修改重新提交）
        if (!Objects.equals(current, ProductEnums.BomStatus.DRAFT.getValue())
                && !Objects.equals(current, ProductEnums.BomStatus.REJECT.getValue())) {
            throw new BusinessException("只有草稿或已驳回状态的BOM才能提交审批");
        }
        LambdaQueryWrapper<EngineeringBomItem> checkItems = new LambdaQueryWrapper<>();
        checkItems.eq(EngineeringBomItem::getBomId, bomId);
        if (!productBomItemMapper.exists(checkItems)) {
            throw new BusinessException("BOM明细不能为空");
        }
        // 用 updateStatus 改为 PENDING
        UpdateBomStatusDTO dto = new UpdateBomStatusDTO();
        dto.setBomId(bomId);
        dto.setCurrent(current);
        dto.setTarget(ProductEnums.BomStatus.REVIEWING.getValue());
        boolean updated = updateStatus(dto);
        if (updated) {
            reviewFlowService.record("engineering_bom", bomId, "SUBMIT", "提交审核",
                    current, dto.getTarget(), null, null);
        }
        return updated;
    }

    @Event("bom.approved")
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(UpdateBomStatusDTO dto) {
        EngineeringBom productBom = productBomMapper.selectById(dto.getBomId());
        dto.setCurrent(ProductEnums.BomStatus.REVIEWING.getValue());
        dto.setTarget(ProductEnums.BomStatus.APPROVED.getValue());
        boolean updated = updateStatus(dto);
        if (updated) {
            reviewFlowService.record("engineering_bom", dto.getBomId(), "APPROVE", "审核通过",
                    dto.getCurrent(), dto.getTarget(), dto.getRemark(), null);
        }
        return updated;
    }



    @Override
    public boolean updateStatus(UpdateBomStatusDTO dto) {
        LambdaUpdateWrapper<EngineeringBom> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(EngineeringBom::getApproveStatus,dto.getTarget())
                .set(StringUtils.isNotBlank(dto.getRemark()),EngineeringBom::getApproveRemark,dto.getRemark())
                .eq(EngineeringBom::getBomId,dto.getBomId())
                .eq(EngineeringBom::getApproveStatus,dto.getCurrent());
        return baseMapper.update(updateWrapper)>0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reject(UpdateBomStatusDTO dto) {
        EngineeringBom productBom = productBomMapper.selectById(dto.getBomId());
        if (!Objects.equals(productBom.getApproveStatus(), ProductEnums.BomStatus.DRAFT.getValue())) {
            return false;
        }
        dto.setCurrent(ProductEnums.BomStatus.DRAFT.getValue());
        dto.setTarget(ProductEnums.BomStatus.REJECT.getValue());
        boolean updated = updateStatus(dto);
        if (updated) {
            reviewFlowService.record("engineering_bom", dto.getBomId(), "REJECT", "审核驳回",
                    dto.getCurrent(), dto.getTarget(), dto.getRemark(), null);
        }
        return updated;
    }

    /**
     * EngineeringBomItemDTO 转 EngineeringBomItem 实体
     */
    private EngineeringBomItem toBomItemEntity(EngineeringBomItemDTO dto) {
        if (dto == null) {
            return null;
        }
        EngineeringBomItem item = new EngineeringBomItem();
        item.setItemId(dto.getItemId());
        item.setParentMaterialId(dto.getParentMaterialId());
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
        // ===== 应用料/实际投料（2026-08-10）：Excel 直读优先，否则自动计算 =====
        item.setAppliedQty(dto.getAppliedQty());
        item.setActualIssueQty(dto.getActualIssueQty());
        calculateAppliedIssue(item);
        return item;
    }

    /**
     * 计算应用料/实际投料：
     *  applied_qty = quantity × (1 + loss_rate/100)
     *  actual_issue_qty：物料类型=R（板材/卷材）且 min_issue_qty>0 时 = CEIL(applied/min_issue)×min_issue，否则 = applied
     *  Excel 已直读的字段不覆盖
     */
    private void calculateAppliedIssue(EngineeringBomItem item) {
        // 应用料：未提供才计算
        if (item.getAppliedQty() == null) {
            java.math.BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : java.math.BigDecimal.ZERO;
            Integer loss = item.getLossRate() != null ? item.getLossRate() : 0;
            item.setAppliedQty(qty.multiply(java.math.BigDecimal.valueOf(1 + loss / 100.0))
                    .setScale(4, java.math.RoundingMode.HALF_UP));
        }
        // 实际投料：未提供才计算
        if (item.getActualIssueQty() == null) {
            java.math.BigDecimal applied = item.getAppliedQty() != null ? item.getAppliedQty() : java.math.BigDecimal.ZERO;
            // 查物料类型：R=板材/卷材
            boolean isSheet = false;
            if (item.getMaterialId() != null) {
                try {
                    com.jjx.inventory.domain.InventoryMaterial mat = inventoryMaterialMapper.selectById(item.getMaterialId());
                    isSheet = mat != null && "R".equalsIgnoreCase(mat.getMaterialType());
                } catch (Exception ignored) { }
            }
            java.math.BigDecimal minIssue = item.getMinIssueQty() != null ? item.getMinIssueQty() : java.math.BigDecimal.ZERO;
            if (isSheet && minIssue.compareTo(java.math.BigDecimal.ZERO) > 0) {
                // CEIL(applied / min_issue) × min_issue
                java.math.BigDecimal ratio = applied.divide(minIssue, 10, java.math.RoundingMode.HALF_UP);
                java.math.BigDecimal ceil = ratio.setScale(0, java.math.RoundingMode.CEILING);
                item.setActualIssueQty(ceil.multiply(minIssue).setScale(4, java.math.RoundingMode.HALF_UP));
            } else {
                item.setActualIssueQty(applied.setScale(4, java.math.RoundingMode.HALF_UP));
            }
        }
    }

    private static @NonNull LambdaQueryWrapper<EngineeringBom> buildQueryWrapper(EngineeringBomQuery query) {
        // 创建查询条件
        LambdaQueryWrapper<EngineeringBom> wrapper = new LambdaQueryWrapper<>();
        // BOM编码查询
        if (StringUtils.isNotBlank(query.getBomCode())) {
            wrapper.like(EngineeringBom::getBomCode, query.getBomCode());
        }

        // 产品ID查询
        if (query.getProductId() != null) {
            wrapper.eq(EngineeringBom::getProductId, query.getProductId());
        }

        // BOM版本查询
        if (StringUtils.isNotBlank(query.getBomVersion())) {
            wrapper.eq(EngineeringBom::getBomVersion, query.getBomVersion());
        }

        // 是否当前版本查询
        if (query.getIsCurrent() != null) {
            wrapper.eq(EngineeringBom::getIsCurrent, query.getIsCurrent());
        }

        // 审批状态查询
        if (StringUtils.isNotBlank(query.getApproveStatus())) {
            wrapper.eq(EngineeringBom::getApproveStatus, query.getApproveStatus());
        }
        return wrapper;
    }
}
