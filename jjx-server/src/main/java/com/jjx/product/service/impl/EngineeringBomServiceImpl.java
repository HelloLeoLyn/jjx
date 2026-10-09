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
    /** 2026-09-21（dev-20260921-013）：BOM 事件改手写 payload（带 BOM 编码）。 */
    private final com.jjx.event.EventPublisher eventPublisher;
    private final com.jjx.system.service.OperLogChangeRecorder changeRecorder;
    /** 2026-10-07 dev-20261007-006：提交审核体检用（项目=标准工序 引用完整性）。 */
    private final com.jjx.product.mapper.ProductStandardProcessMapper standardProcessMapper;
    public EngineeringBomServiceImpl(EngineeringBomMapper productBomMapper,
                                 EngineeringBomItemMapper productBomItemMapper,
                                 ProductMapper productMapper, EngineeringBomConverter bomConverter,
                                 com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper,
                                 com.jjx.inventory.mapper.InventoryMaterialMapper inventoryMaterialMapper,
                                 ReviewFlowService reviewFlowService,
                                 com.jjx.event.EventPublisher eventPublisher,
                                 com.jjx.system.service.OperLogChangeRecorder changeRecorder,
                                 com.jjx.product.mapper.ProductStandardProcessMapper standardProcessMapper) {
        this.productBomMapper = productBomMapper;
        this.productBomItemMapper = productBomItemMapper;
        this.productMapper = productMapper;
        this.bomConverter = bomConverter;
        this.productionOrderMapper = productionOrderMapper;
        this.inventoryMaterialMapper = inventoryMaterialMapper;
        this.reviewFlowService = reviewFlowService;
        // 2026-09-21 dev-20260921-023：加字段时漏了构造函数赋值，全量编译不过（此错误此前被类型错掩盖）
        this.eventPublisher = eventPublisher;
        this.changeRecorder = changeRecorder;
        this.standardProcessMapper = standardProcessMapper;
    }

    /**
     * BOM 事件统一发布（2026-09-21 dev-20260921-013 工程批）：
     * 手写 payload，bizNo 取 BOM 编码（原注解 @Event("bom.submitted") 连 bizId 都没有）。
     */
    private void publishBomEvent(String eventCode, EngineeringBom bom) {
        if (bom == null) {
            return;
        }
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "bom", bom.getBomId(), bom.getBomCode());
        payload.put("bomCode", bom.getBomCode());
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, eventCode, payload);
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
        return createBomReturnId(dto) != null;
    }

    /**
     * 创建BOM，返回新 BOM id（失败返回 null），供 @Log bizId 使用
     */
    @Override
    public Long createBomReturnId(EngineeringBomDTO dto) {
        EngineeringBom bom = bomConverter.toEntity(dto);
        bom.setApproveStatus(ApproveStatusEnum.DRAFT.getValue());

        // 生成BOM版本
        generateBomVersion(dto.getProductId(), bom);

        // 保存BOM主表
        if (!saveBom(bom)) {
            return null;
        }

        // 保存BOM明细
        saveBomItems(dto.getItems(), bom.getBomId());

        // 如果是当前版本，更新其他版本为非当前
        if (isCurrentVersion(bom)) {
            setOtherBomNotCurrent(bom.getProductId(), bom.getBomId());
        }

        return bom.getBomId();
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
        ProductEnums.BomStatus latestStatus = ProductEnums.BomStatus.fromValue(bom.getApproveStatus());
        vo.setBizStatus(latestStatus != null ? latestStatus.getLabel() : null);
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
                // 2026-09-04 修复（dev-1404）：明细 diff 补 基数(base_qty)/模数(module_qty)，
                // 原只比 quantity 导致改模数/基数（含空→值、值→空）流水无记录；统一 diffDecimal 防 BigDecimal scale 误报
                String label = matLabel(e.getValue());
                changeRecorder.diffDecimal(changes, "用量(" + label + ")",
                        oldIt.getQuantity(), calculatedUnitQuantity(e.getValue().getBaseQty(),
                                e.getValue().getModuleQty(), e.getValue().getQuantity()));
                changeRecorder.diffDecimal(changes, "基数(" + label + ")",
                        oldIt.getBaseQty(), e.getValue().getBaseQty());
                changeRecorder.diffDecimal(changes, "模数(" + label + ")",
                        oldIt.getModuleQty(), e.getValue().getModuleQty());
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
    public ProductEnums.BomStatus removeBomWithItems(Long bomId) {
        // 检查BOM是否存在
        EngineeringBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            return null;
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
        // 记录删除时的状态：行已不存在，能反映业务事实的只有删除前的状态
        return productBomMapper.deleteById(bomId) > 0 ? ProductEnums.BomStatus.fromValue(st) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductEnums.BomStatus setDefaultBom(Long bomId) {
        EngineeringBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            return null;
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

        // 本操作只改 is_current，不改 approve_status，直接返回该 BOM 当前状态
        return ProductEnums.BomStatus.fromValue(bom.getApproveStatus());
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
        newBom.setApproveStatus(ApproveStatusEnum.DRAFT.getValue());
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
        // 2026-10-07 dev-20261007-006：提交前数据完整性体检（不通过即拦，前 10 条入提示）
        List<com.jjx.product.domain.vo.BomCheckIssueVO> issues = checkBomForSubmit(bomId);
        if (!issues.isEmpty()) {
            StringBuilder sb = new StringBuilder("BOM 不满足提交审核条件：\n");
            int max = Math.min(issues.size(), 10);
            for (int i = 0; i < max; i++) {
                com.jjx.product.domain.vo.BomCheckIssueVO v = issues.get(i);
                String loc = v.getMaterialCode() != null && !v.getMaterialCode().isBlank()
                        ? v.getMaterialCode()
                        : (v.getItemId() != null ? ("行#" + v.getItemId()) : "主表");
                sb.append("· [").append(loc).append("] ").append(v.getMessage()).append("\n");
            }
            if (issues.size() > max) {
                sb.append("… 共 ").append(issues.size()).append(" 项");
            }
            throw new BusinessException(sb.toString());
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
        publishBomEvent("bom.submitted", bom);
        return updated;
    }
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
        publishBomEvent("bom.approved", productBom);
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
        if (productBom == null) {
            throw new BusinessException("BOM不存在");
        }
        if (!Objects.equals(productBom.getApproveStatus(), ProductEnums.BomStatus.REVIEWING.getValue())) {
            throw new BusinessException("只有审核中的BOM才能驳回");
        }
        dto.setCurrent(ProductEnums.BomStatus.REVIEWING.getValue());
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
        item.setProcessId(dto.getProcessId());
        item.setProcessName(dto.getProcessName());
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
        // ===== 应用料/实际投料（2026-08-10）：统一按公式计算 =====
        calculateAppliedIssue(item);
        return item;
    }

    private java.math.BigDecimal calculatedUnitQuantity(java.math.BigDecimal base, java.math.BigDecimal module,
                                                       java.math.BigDecimal legacyQuantity) {
        if (base != null && base.signum() > 0 && module != null && module.signum() > 0) {
            return base.divide(module, 4, java.math.RoundingMode.HALF_UP);
        }
        // 缺少有效基数/模数的历史明细保留原用量，由既有审核校验处理。
        return legacyQuantity != null ? legacyQuantity : java.math.BigDecimal.ZERO;
    }

    /**
     * 计算应用料/实际投料：
     *  quantity = base_qty / module_qty（有效基数、模数下先重算）；applied_qty = quantity × (1 + loss_rate/100)
     *  actual_issue_qty：存单位应用料（含损耗、不取整）
     *  整批取整与最低投料量下限由领料/缺料/预留侧按工单数量计算（对应各服务里的 batchDemand 方法）
     *  始终按公式重新计算
     */
    private void calculateAppliedIssue(EngineeringBomItem item) {
        // 与前端一致：有效基数、模数下先计算单位用量，不采用导入/旧客户端的实发数量。
        java.math.BigDecimal qty = calculatedUnitQuantity(item.getBaseQty(), item.getModuleQty(), item.getQuantity());
        item.setQuantity(qty);
        // 应用料
        Integer loss = item.getLossRate() != null ? item.getLossRate() : 0;
        item.setAppliedQty(qty.multiply(java.math.BigDecimal.valueOf(1 + loss / 100.0))
                .setScale(4, java.math.RoundingMode.HALF_UP));
        // 实际投料保存单位应用料（含损耗、不取整）
        item.setActualIssueQty(item.getAppliedQty());
    }

    /**
     * BOM 提交审核前数据完整性体检（只读）。
     * 2026-10-07 dev-20261007-006（方案 A：纯校验，不动 DB）。
     */
    @Override
    public List<com.jjx.product.domain.vo.BomCheckIssueVO> checkBomForSubmit(Long bomId) {
        List<com.jjx.product.domain.vo.BomCheckIssueVO> issues = new java.util.ArrayList<>();
        EngineeringBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            issues.add(issue(null, null, null, "bomId", "BOM不存在"));
            return issues;
        }
        // 主表：产品必须存在
        if (bom.getProductId() == null) {
            issues.add(issue(null, null, null, "productId", "BOM未关联产品"));
        } else if (productMapper.selectById(bom.getProductId()) == null) {
            issues.add(issue(null, null, null, "productId", "关联产品不存在（product_id=" + bom.getProductId() + "）"));
        }
        // 主表：同一产品只能有一个当前版本
        if (Boolean.TRUE.equals(bom.getIsCurrent()) && bom.getProductId() != null) {
            Long currentCnt = productBomMapper.selectCount(new LambdaQueryWrapper<EngineeringBom>()
                    .eq(EngineeringBom::getProductId, bom.getProductId())
                    .eq(EngineeringBom::getIsCurrent, true));
            if (currentCnt != null && currentCnt > 1) {
                issues.add(issue(null, null, null, "isCurrent",
                        "同一产品存在 " + currentCnt + " 个当前版本（is_current=1），请先修正"));
            }
        }
        List<EngineeringBomItem> items = productBomItemMapper.selectList(
                new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bomId));
        if (items == null || items.isEmpty()) {
            issues.add(issue(null, null, null, "items", "BOM明细不能为空"));
            return issues;
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (EngineeringBomItem it : items) {
            if (it.getItemId() != null) {
                ids.add(it.getItemId());
            }
        }
        boolean hasRoot = false;
        for (EngineeringBomItem it : items) {
            Long iid = it.getItemId();
            String code = it.getMaterialCode();
            String name = it.getMaterialName();
            // 行归属：明细必须挂在本 BOM 下
            if (!java.util.Objects.equals(it.getBomId(), bomId)) {
                issues.add(issue(iid, code, name, "bomId", "明细不属于本BOM"));
            }
            // 物料
            if (it.getMaterialId() == null || it.getMaterialId() <= 0) {
                issues.add(issue(iid, code, name, "materialId", "未选择物料"));
            } else if (inventoryMaterialMapper.selectById(it.getMaterialId()) == null) {
                issues.add(issue(iid, code, name, "materialId", "物料不存在（material_id=" + it.getMaterialId() + "）"));
            }
            // 用量
            if (it.getQuantity() == null || it.getQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                issues.add(issue(iid, code, name, "quantity", "用量必须大于0"));
            }
            // 单位
            if (org.apache.commons.lang3.StringUtils.isBlank(it.getUnit())) {
                issues.add(issue(iid, code, name, "unit", "单位不能为空"));
            }
            // 损耗率
            if (it.getLossRate() != null && (it.getLossRate() < 0 || it.getLossRate() > 100)) {
                issues.add(issue(iid, code, name, "lossRate", "损耗率须在 0~100 之间"));
            }
            // 来源类型
            if (it.getSourceType() != null && !"buy".equals(it.getSourceType()) && !"make".equals(it.getSourceType())) {
                issues.add(issue(iid, code, name, "sourceType", "来源类型非法（应为 buy/make）"));
            }
            // 模数 / 基数（用户口径 2026-10-07：非空且 >=1）
            if (it.getModuleQty() == null || it.getModuleQty().compareTo(java.math.BigDecimal.ONE) < 0) {
                issues.add(issue(iid, code, name, "moduleQty", "模数不能为空且须 ≥1"));
            }
            if (it.getBaseQty() == null || it.getBaseQty().compareTo(java.math.BigDecimal.ONE) < 0) {
                issues.add(issue(iid, code, name, "baseQty", "基数不能为空且须 ≥1"));
            }
            // 项目（标准工序）引用完整性
            if (it.getProcessId() != null) {
                com.jjx.product.domain.entity.ProductStandardProcess sp = standardProcessMapper.selectById(it.getProcessId());
                if (sp == null) {
                    issues.add(issue(iid, code, name, "processId", "项目（标准工序）不存在（process_id=" + it.getProcessId() + "）"));
                } else if (sp.getIsEnabled() != null && sp.getIsEnabled() == 0) {
                    issues.add(issue(iid, code, name, "processId", "项目（标准工序）「" + sp.getProcessName() + "」已停用"));
                }
            }
            // 父引用
            Long pid = it.getParentMaterialId();
            if (pid == null) {
                hasRoot = true;
            } else if (pid.equals(iid)) {
                issues.add(issue(iid, code, name, "parentMaterialId", "不能以自己为父节点"));
            } else if (!ids.contains(pid)) {
                issues.add(issue(iid, code, name, "parentMaterialId", "父节点不存在（悬空，parent_material_id=" + pid + "）"));
            }
        }
        if (!hasRoot) {
            issues.add(issue(null, null, null, "parentMaterialId", "必须至少有一行根节点（parent_material_id 为空）"));
        }
        // 环检测：沿父链上溯，重复出现即为环
        java.util.Map<Long, EngineeringBomItem> byId = new java.util.HashMap<>();
        for (EngineeringBomItem it : items) {
            if (it.getItemId() != null) {
                byId.put(it.getItemId(), it);
            }
        }
        for (EngineeringBomItem it : items) {
            java.util.Set<Long> seen = new java.util.HashSet<>();
            Long cur = it.getItemId();
            while (cur != null && byId.containsKey(cur)) {
                if (!seen.add(cur)) {
                    issues.add(issue(it.getItemId(), it.getMaterialCode(), it.getMaterialName(),
                            "parentMaterialId", "父子关系存在环"));
                    break;
                }
                cur = byId.get(cur).getParentMaterialId();
            }
        }
        return issues;
    }

    private com.jjx.product.domain.vo.BomCheckIssueVO issue(Long itemId, String code, String name, String field, String message) {
        com.jjx.product.domain.vo.BomCheckIssueVO v = new com.jjx.product.domain.vo.BomCheckIssueVO();
        v.setItemId(itemId);
        v.setMaterialCode(code);
        v.setMaterialName(name);
        v.setField(field);
        v.setMessage(message);
        return v;
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
