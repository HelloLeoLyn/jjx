package com.jjx.inventory.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.enums.StatusEnum;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.converter.MaterialConverter;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.dto.imports.MaterialImportDTO;
import com.jjx.inventory.dto.query.MaterialCheckDTO;
import com.jjx.inventory.dto.query.MaterialQueryDTO;
import com.jjx.inventory.dto.vo.MaterialVO;
import com.jjx.inventory.dto.vo.MaterialCategoryOptionVO;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.service.InventoryMaterialCategoryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jjx.inventory.enums.ProcessGroup;
import com.jjx.inventory.util.MaterialClassification;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.purchase.domain.vo.PurchaseSupplierVO;
import com.jjx.purchase.service.IPurchaseSupplierService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.jjx.system.annotation.Event;
import com.jjx.system.domain.entity.SysTag;
import com.jjx.system.service.ISysTagService;

/**
 * 物料主数据服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMaterialServiceImpl extends ServiceImpl<InventoryMaterialMapper, InventoryMaterial>
        implements InventoryMaterialService {

    private static final String MATERIAL_TAG_GROUP = "material_attribute";
    private static final String MATERIAL_BIZ_TYPE = "inventory_material";

    private final InventoryMaterialMapper materialMapper;
    private final InventoryMaterialCategoryService categoryService;
    /** 2026-09-21（dev-20260921-013）：库存事件改手写 payload。 */
    private final com.jjx.event.EventPublisher eventPublisher;
    private final InventoryStockMapper stockMapper;
    private final RedisSequenceService redisSequenceService;
    private final MaterialConverter materialConverter;
    private final IPurchaseSupplierService purchaseSupplierService;
    private final com.jjx.purchase.mapper.PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final com.jjx.sales.mapper.SalesOrderProductMapper salesOrderProductMapper;
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;
    private final ISysTagService tagService;

    /**
     * 库存主数据/预警事件统一发布（2026-09-21 dev-20260921-013 库存批 3/3）：
     * 手写 payload，bizNo 取对象编码（删除类由调用方传入删除前取到的编码）。
     */
    private void publishMaterialEvent(String eventCode, Long id, String knownCode) {
        InventoryMaterial m = (id == null || knownCode != null) ? null : materialMapper.selectById(id);
        String code = knownCode != null ? knownCode : (m == null ? null : m.getMaterialCode());
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "material", id, code);
        // 删除路径由调用方传 knownCode，此时 m 为 null —— 不防空会 NPE（2026-09-21 dev-20260921-023）
        if (m != null) {
            payload.put("materialName", m.getMaterialName());
        }
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, eventCode, payload);
    }

    @Override
    public PageResult<MaterialVO> pageQuery(MaterialQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = buildQueryWrapper(queryDTO);
        Page<InventoryMaterial> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        materialMapper.selectPage(page, wrapper);
        List<InventoryMaterial> records = page.getRecords();
        List<MaterialVO> voList = enrichTags(convertMaterials(records));
        return PageResult.of(page, voList);
    }

    @Override
    public List<MaterialCategoryOptionVO> getCategoryOptions() {
        return categoryService.listMaps(new QueryWrapper<InventoryMaterialCategory>()
                .select("category_id", "category_code", "category_name", "category_path", "name_path")
                .eq("status", StatusEnum.NORMAL.getCode()).orderByAsc("sort_order", "category_id"))
                .stream().map(row -> {
                    MaterialCategoryOptionVO option = new MaterialCategoryOptionVO();
                    option.setCategoryId(((Number) row.get("category_id")).longValue());
                    option.setCategoryCode((String) row.get("category_code"));
                    option.setCategoryName((String) row.get("category_name"));
                    option.setRootType(MaterialClassification.materialTypeOf(row));
                    option.setCategoryRootName(MaterialClassification.rootNameOf(row));
                    return option;
                }).toList();
    }

    /** 一次批量读取关联品类；包含停用品类，历史物料名称仍可显示。 */
    private List<MaterialVO> convertMaterials(List<InventoryMaterial> materials) {
        if (materials.isEmpty()) return List.of();
        List<Long> ids = materials.stream().map(InventoryMaterial::getCategoryId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        Map<Long, InventoryMaterialCategory> classified = new HashMap<>();
        if (!ids.isEmpty()) {
            for (InventoryMaterialCategory category : categoryService.listByIds(ids)) {
                names.put(category.getCategoryId(), category.getCategoryName());
                classified.put(category.getCategoryId(), category);
            }
        }
        List<MaterialVO> result = materialConverter.toVOList(materials, names);
        result.forEach(vo -> {
            InventoryMaterialCategory category = classified.get(vo.getCategoryId());
            Map<String, Object> path = category == null || category.getCategoryPath() == null ? null : Map.of("category_path", category.getCategoryPath());
            vo.setCategoryRootName(MaterialClassification.rootNameOf(path));
            vo.setRootType(MaterialClassification.materialTypeOf(path));
        });
        return result;
    }

    private @NonNull LambdaQueryWrapper<InventoryMaterial> buildQueryWrapper(MaterialQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getMaterialCode() != null && !queryDTO.getMaterialCode().isEmpty()) {
            wrapper.like(InventoryMaterial::getMaterialCode, queryDTO.getMaterialCode());
        }
        if (queryDTO.getMaterialName() != null && !queryDTO.getMaterialName().isEmpty()) {
            wrapper.like(InventoryMaterial::getMaterialName, queryDTO.getMaterialName());
        }
        // 列表/导出也支持关键词：同时匹配 search_blob 与分类中文全路径（dev-20261024-006）
        if (StringUtils.isNotBlank(queryDTO.getKeyword())) {
            for (String term : queryDTO.getKeyword().trim().split("\\s+")) {
                if (StringUtils.isNotBlank(term)) {
                    applyKeywordMatch(wrapper, term);
                }
            }
        }
        // 按标签筛选（dev-20260912-007 公共助手）：tagIds + tagMatchMode；兼容单值 tagId
        java.util.List<Long> tagIds = queryDTO.getTagIds();
        if ((tagIds == null || tagIds.isEmpty()) && queryDTO.getTagId() != null) {
            tagIds = java.util.List.of(queryDTO.getTagId());
        }
        List<Long> tagBizIds = tagService.resolveBizIdsFilter(MATERIAL_BIZ_TYPE, tagIds, queryDTO.getTagMatchMode());
        if (tagBizIds != null) {
            if (tagBizIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(InventoryMaterial::getMaterialId, tagBizIds);
            }
        }
        if (queryDTO.getSpecification() != null && !queryDTO.getSpecification().isEmpty()) {
            // 文本框语义是「包含」，不能精确匹配（dev-20261024-024）
            wrapper.like(InventoryMaterial::getSpecification, queryDTO.getSpecification());
        }
        if (queryDTO.getSupplierId() != null) {
            wrapper.eq(InventoryMaterial::getSupplierId, queryDTO.getSupplierId());
        }
        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(InventoryMaterial::getCategoryId, queryDTO.getCategoryId());
        }
        if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
            wrapper.eq(InventoryMaterial::getStatus, queryDTO.getStatus());
        }
        applyAttributeFilters(wrapper, queryDTO);
        wrapper.orderByDesc(InventoryMaterial::getCreateTime).orderByDesc(InventoryMaterial::getMaterialId);
        return wrapper;
    }

    @Override
    public MaterialVO getDetailById(Long id) {
        InventoryMaterial material = materialMapper.selectById(id);
        return enrichTags(material == null ? null : convertMaterials(List.of(material)).get(0));
    }

    @Override
    public InventoryMaterial getByCode(String materialCode) {
        return materialMapper.selectByCode(materialCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean create(InventoryMaterial material, List<Long> tagIds, String operName) {
        validateMasterData(material, null);
        synchronizeAttributeColumns(material, null);
        // 检查编码是否已存在
        if (existsByCode(material.getMaterialCode())) {
            log.error("物料编码已存在: {}", material.getMaterialCode());
            return false;
        }

        boolean created = materialMapper.insert(material) > 0;
        if (created) {
            setMaterialTags(material.getMaterialId(), tagIds, operName);
        }
        if (created) {
            publishMaterialEvent("inventory.material.created", material.getMaterialId(), material.getMaterialCode());
        }
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(InventoryMaterial material, List<Long> tagIds, String operName) {
        InventoryMaterial existing = materialMapper.selectById(material.getMaterialId());
        if (existing == null) {
            log.error("物料不存在: {}", material.getMaterialId());
            return false;
        }

        validateMasterData(material, existing);
        boolean attributesSaved = synchronizeAttributeColumns(material, existing);
        // 编辑DTO不传编码时保留原编码。
        if (material.getMaterialCode() != null && !existing.getMaterialCode().equals(material.getMaterialCode())) {
            if (existsByCode(material.getMaterialCode())) {
                log.error("物料编码已存在: {}", material.getMaterialCode());
                return false;
            }
        }

        // MyBatis默认跳过null；显式清空选填字段需单独SET，省略字段保持原值。
        LambdaUpdateWrapper<InventoryMaterial> patch = new LambdaUpdateWrapper<>();
        patch.eq(InventoryMaterial::getMaterialId, material.getMaterialId());
        for (String key : material.getSuppliedFields()) {
            String column = MASTER_COLUMNS.get(key);
            if (column != null && fieldValue(material, key) == null) patch.setSql(column + " = NULL");
        }
        if (attributesSaved) {
            // JSON清空或厚度/尺寸移除时也必须显式清空派生值。
            if (material.getAttributeJson() == null) patch.set(InventoryMaterial::getAttributeJson, null);
            if (material.getThickness() == null) patch.set(InventoryMaterial::getThickness, null);
            if (material.getWidthSize() == null) patch.set(InventoryMaterial::getWidthSize, null);
        }
        boolean updated = materialMapper.update(material, patch) > 0;
        if (updated) {
            setMaterialTags(material.getMaterialId(), tagIds, operName);
        }
        if (updated) {
            publishMaterialEvent("inventory.material.updated", existing.getMaterialId(), existing.getMaterialCode());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWithCheck(Long id) {
        InventoryMaterial material = materialMapper.selectById(id);
        if (material == null) {
            log.error("物料不存在: {}", id);
            return false;
        }

        // 检查库存汇总
        InventoryStock stock = new InventoryStock();
        stock.setMaterialId(id);
        Long stockCount = stockMapper
                .selectCount(new LambdaQueryWrapper<InventoryStock>().eq(InventoryStock::getMaterialId, id));
        if (stockCount != null && stockCount > 0) {
            log.error("物料已被库存引用，无法删除: materialId={}", id);
            throw new RuntimeException("物料已被库存引用，无法删除");
        }

        // 检查采购订单引用
        Long purchaseRef = purchaseOrderItemMapper.selectCount(
                new LambdaQueryWrapper<com.jjx.purchase.domain.entity.PurchaseOrderItem>()
                        .eq(com.jjx.purchase.domain.entity.PurchaseOrderItem::getMaterialId, id));
        if (purchaseRef != null && purchaseRef > 0) {
            throw new RuntimeException("物料已被采购订单引用，无法删除");
        }

        // 检查销售订单引用
        Long salesRef = salesOrderProductMapper.selectCount(
                new LambdaQueryWrapper<com.jjx.sales.domain.entity.SalesOrderProduct>()
                        .eq(com.jjx.sales.domain.entity.SalesOrderProduct::getProductId, id));
        // 销售订单引用的是产品ID，物料ID无直接引用——跳过产品侧
        // 检查生产工单引用（工单关联产品+物料，这里查工单表是否存在该物料）
        Long prodRef = productionOrderMapper.selectCount(
                new LambdaQueryWrapper<com.jjx.production.domain.entity.ProductionOrder>()
                        .and(w -> w.eq(com.jjx.production.domain.entity.ProductionOrder::getProductId, id)
                                .or().eq(com.jjx.production.domain.entity.ProductionOrder::getProductCode, material.getMaterialCode())));
        if (prodRef != null && prodRef > 0) {
            throw new RuntimeException("物料已被生产工单引用，无法删除");
        }

        boolean deleted = materialMapper.deleteById(id) > 0;
        if (deleted) {
            tagService.setBizTags(MATERIAL_BIZ_TYPE, id, List.of(), "system");
        }
        if (deleted) {
            publishMaterialEvent("inventory.material.deleted", material.getMaterialId(), material.getMaterialCode());
        }
        return deleted;
    }

    @Override
    public List<Map<String, Object>> getOptions(String keyword) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<InventoryMaterial>()
                .eq(InventoryMaterial::getStatus, 0);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(InventoryMaterial::getMaterialCode, keyword)
                    .or().like(InventoryMaterial::getMaterialName, keyword));
        }
        List<InventoryMaterial> materials = materialMapper.selectList(wrapper
                .orderByAsc(InventoryMaterial::getMaterialCode)
                .last("LIMIT 100"));

        List<Map<String, Object>> options = new ArrayList<>();
        for (InventoryMaterial material : materials) {
            Map<String, Object> option = new HashMap<>();
            option.put("value", material.getMaterialId());
            option.put("label", material.getMaterialCode() + " - " + material.getMaterialName());
            option.put("materialCode", material.getMaterialCode());
            option.put("materialName", material.getMaterialName());
            option.put("unit", material.getUnit());
            option.put("specification", material.getSpecification());
            options.add(option);
        }

        return options;
    }

    @Override
    public boolean existsByCode(String materialCode) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryMaterial::getMaterialCode, materialCode);
        Long count = materialMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    @Event(value = "inventory.material.status_updated", bizId = "#ids", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean batchUpdateStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        LambdaUpdateWrapper<InventoryMaterial> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(InventoryMaterial::getStatus, status)
                .in(InventoryMaterial::getMaterialId, ids);
        return materialMapper.update(updateWrapper) > 0;
    }

    @Override
    public PageResult<MaterialVO> search(MaterialQueryDTO queryDTO) {
        // 统一检索生成列（采购订单物料选择等远程搜索共用）。
        // 8-08 修复：无任何查询条件时不再拼 and() 空括号（SQL: WHERE () 语法错误），直接查全部
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        String keyword = queryDTO.getKeyword();
        if (keyword != null) {
            keyword = StringUtils.trimToNull(keyword.replaceAll("(?U)\\s+", " "));
        }
        String code = queryDTO.getMaterialCode();
        String name = queryDTO.getMaterialName();
        if (keyword != null) {
            List<String> terms = java.util.Arrays.stream(keyword.split(" +"))
                    .filter(StringUtils::isNotBlank)
                    .limit(4)
                    .toList();
            List<String> ranks = new java.util.ArrayList<>();
            String param = "#{" + wrapper.getParamAlias() + ".paramNameValuePairs.";
            for (String term : terms) {
                // 每词匹配 search_blob 或分类名/中文全路径，词间 AND；值使用绑定参数（dev-20261024-006）。
                applyKeywordMatch(wrapper, term);
                // 每词独立绑定比较值，SQL 片段只拼接内部生成的参数名。
                String key = "rank" + ranks.size();
                wrapper.getParamNameValuePairs().put(key + "Exact", term);
                wrapper.getParamNameValuePairs().put(key + "Prefix", term + "%");
                wrapper.getParamNameValuePairs().put(key + "Contains", "%" + term + "%");
                String specificationRank = "WHEN specification LIKE " + param + key + "Contains} THEN 0 ";
                ranks.add("CASE "
                        + (term.matches("[0-9]+(?:\\*[0-9]+)?") ? specificationRank : "")
                        + "WHEN material_name = " + param + key + "Exact} THEN 1 "
                        + "WHEN material_name LIKE " + param + key + "Prefix} THEN 2 "
                        + "WHEN material_name LIKE " + param + key + "Contains} THEN 3 "
                        + "ELSE 4 END");
            }
            String rank = (ranks.size() == 1 ? ranks.get(0) : "LEAST(" + String.join(", ", ranks) + ")")
                    + " ASC";
            wrapper.getExpression().add(com.baomidou.mybatisplus.core.enums.SqlKeyword.ORDER_BY, () -> rank);
        } else if (StringUtils.isNotBlank(code) || StringUtils.isNotBlank(name)) {
            wrapper.and(w -> {
                w.like(StringUtils.isNotBlank(code), InventoryMaterial::getMaterialCode, code)
                        .or().like(StringUtils.isNotBlank(name), InventoryMaterial::getMaterialName, name);
            });
        }
        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(InventoryMaterial::getCategoryId, queryDTO.getCategoryId());
        }
        if (queryDTO.getSupplierId() != null) {
            wrapper.eq(InventoryMaterial::getSupplierId, queryDTO.getSupplierId());
        }
        applyAttributeFilters(wrapper, queryDTO);
        wrapper.eq(InventoryMaterial::getStatus, StatusEnum.NORMAL.getCode())
                .orderByDesc(InventoryMaterial::getCreateTime)
                .orderByDesc(InventoryMaterial::getMaterialId);
        IPage<InventoryMaterial> page = new Page<InventoryMaterial>().setSize(queryDTO.getPageSize())
                .setCurrent(queryDTO.getPageNum());
        materialMapper.selectPage(page, wrapper);
        List<MaterialVO> voList = enrichTags(convertMaterials(page.getRecords()));
        return PageResult.of(page, voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.jjx.inventory.dto.vo.MaterialImportResultVO importMaterial(List<MaterialImportDTO> importList, String operName) {
        com.jjx.inventory.dto.vo.MaterialImportResultVO result = new com.jjx.inventory.dto.vo.MaterialImportResultVO();
        if (importList == null || importList.isEmpty()) {
            return result;
        }

        // 1) 整表预检（dev-20261023-019）：归一 → 品类 → 名称 → 按品类必填矩阵与尺寸规则校验，逐行给出缺失列
        List<ImportRow> rows = new ArrayList<>();
        Map<String, Integer> keyCountMap = new HashMap<>();
        for (int i = 0; i < importList.size(); i++) {
            MaterialImportDTO dto = importList.get(i);
            int excelRow = i + 2; // 第1行表头，数据从第2行开始
            try {
                normalizeImport(dto);
                Map<String, Object> category = resolveImportCategory(dto);
                if (StringUtils.isBlank(dto.getMaterialName())) {
                    String composed = composeImportName(dto.getMaterialGrade(), dto.getModel(), dto.getColor(), dto.getBrand());
                    if (StringUtils.isBlank(composed)) {
                        throw new BusinessException("缺【物料名称】列：请填写名称，或填写【材料/型号/颜色/品牌】自动生成");
                    }
                    dto.setMaterialName(composed);
                }
                validateImportRow(dto, category);
                ImportRow row = new ImportRow(excelRow, dto, category, importDedupeKey(dto));
                rows.add(row);
                keyCountMap.merge(row.key(), 1, Integer::sum);
            } catch (Exception e) {
                log.warn("导入第{}条预检失败: {}", excelRow, e.getMessage());
                result.addFail(excelRow, dto.getMaterialName(), e.getMessage());
            }
        }

        int successCount = 0;
        int skipCount = 0;
        for (ImportRow row : rows) {
            MaterialImportDTO dto = row.dto();
            int excelRow = row.excelRow();
            try {
                int sameRows = keyCountMap.getOrDefault(row.key(), 0);
                if (sameRows > 1) {
                    result.addFail(excelRow, dto.getMaterialName(),
                            "文件内重复行（同一 品类+材料+型号+颜色+品牌+尺寸+规格 出现 " + sameRows + " 次），请删除重复行或合并");
                    continue;
                }

                // 与库内已存在判定：按结构化属性去重，不再用「名称+规格+供应商」（dev-20261023-019）
                if (importMaterialExists(dto, row.category())) {
                    skipCount++;
                    continue;
                }

                // 供应商可不填/未建档（Leo 2026-09-12）：能匹配则挂档案，匹配不到不阻断，物料仍导入，供应商后续补
                String supplierName = StringUtils.trimToNull(dto.getSupplierName());
                PurchaseSupplierVO purchaseSupplierVO = supplierName == null ? null
                        : purchaseSupplierService.selectSupplierByName(supplierName);

                String materialCode = generateMaterialCode(((Number) row.category().get("category_id")).longValue());

                // 创建物料实体
                InventoryMaterial material = new InventoryMaterial();
                material.setMaterialCode(materialCode);
                material.setMaterialName(dto.getMaterialName());
                material.setSpecification(dto.getSpecification());
                material.setCategoryId(((Number) row.category().get("category_id")).longValue());
                material.setMaterialGrade(dto.getMaterialGrade());
                material.setModel(dto.getModel());
                material.setColor(dto.getColor());
                material.setBrand(dto.getBrand());
                material.setSize(dto.getSize());
                material.setSearchKeywords(StringUtils.trimToNull(dto.getSearchKeywords()));
                validateMasterData(material, null);
                material.setUnit(dto.getUnit());
                material.setSupplierId(purchaseSupplierVO != null ? purchaseSupplierVO.getSupplierId() : null);
                material.setSupplierName(supplierName);
                material.setRemark(dto.getRemark());
                material.setStatus(StatusEnum.NORMAL.getCode());
                BigDecimal safeStock = importDecimal(dto.getSafeStock(), "安全库存");
                BigDecimal maxStock = importDecimal(dto.getMaxStock(), "最高库存");
                BigDecimal reorderPoint = importDecimal(dto.getReorderPoint(), "再订货点");
                material.setSafeStock(safeStock == null ? BigDecimal.ZERO : safeStock);
                material.setMaxStock(maxStock == null ? BigDecimal.ZERO : maxStock);
                material.setReorderPoint(reorderPoint == null ? BigDecimal.ZERO : reorderPoint);
                material.setStandardPrice(importDecimal(dto.getStandardPrice(), "标准单价"));
                material.setLeadTime(importInteger(dto.getLeadTime(), "采购周期"));
                material.setShelfLife(importInteger(dto.getShelfLife(), "保质期"));
                Integer alertDays = importInteger(dto.getExpiryAlertDays(), "预警提前天数");
                material.setExpiryAlertDays(alertDays == null ? 30 : alertDays);
                Boolean batchControl = importBoolean(dto.getBatchControl(), "批次控制");
                material.setBatchControl(batchControl != null && batchControl);
                material.setProcessGroup(getProcessGroup(dto));
                materialMapper.insert(material);

                // 材料标签（dev-20260912-005）：品类明细作物料标签(material_attribute)导入，自动建档
                List<Long> tagIds = resolveImportTags(dto.getTagTexts(), operName);
                if (!tagIds.isEmpty()) {
                    tagService.setBizTags(MATERIAL_BIZ_TYPE, material.getMaterialId(), tagIds, operName);
                }
                successCount++;

            } catch (Exception e) {
                log.error("导入第{}条数据失败: {}", excelRow, dto.getMaterialName(), e);
                result.addFail(excelRow, dto.getMaterialName(), e.getMessage());
            }
        }

        result.setSuccessCount(successCount);
        result.setSkipCount(skipCount);
        log.info("物料导入完成：成功{}条，跳过重复{}条，失败{}条", successCount, skipCount, result.getFailCount());
        return result;
    }

    @Override
    public List<MaterialVO> selectList(MaterialQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterial> queryWrapper = buildQueryWrapper(queryDTO);
        List<InventoryMaterial> inventoryMaterials = materialMapper.selectList(queryWrapper);
        return enrichTags(convertMaterials(inventoryMaterials));
    }

    @Override
    public List<InventoryMaterial> selectEntities(MaterialQueryDTO queryDTO) {
        return materialMapper.selectList(buildQueryWrapper(queryDTO));
    }

    @Override
    public String generateCode(Long categoryId) {
        return generateMaterialCode(categoryId);
    }

    @Override
    public MaterialVO checkMaterial(MaterialCheckDTO checkDTO) {
        if (checkDTO.getMaterialName() == null || checkDTO.getMaterialName().isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryMaterial::getMaterialName, checkDTO.getMaterialName());

        if (checkDTO.getSpecification() != null && !checkDTO.getSpecification().isEmpty()) {
            wrapper.eq(InventoryMaterial::getSpecification, checkDTO.getSpecification());
        }
        if (checkDTO.getSupplierName() != null && !checkDTO.getSupplierName().isEmpty()) {
            wrapper.eq(InventoryMaterial::getSupplierName, checkDTO.getSupplierName());
        }

        InventoryMaterial material = materialMapper.selectOne(wrapper, false);
        if (material == null) {
            return null;
        }
        return enrichTags(material == null ? null : convertMaterials(List.of(material)).get(0));
    }

    private static String getProcessGroup(MaterialImportDTO dto) {
        ProcessGroup processGroup = ProcessGroup.fromCode(dto.getProcessGroup());
        if (processGroup == null) {
            processGroup = ProcessGroup.fromName(dto.getProcessGroup());
        }
        return processGroup.getCode();
    }

    /**
     * 解析导入的材料标签文本（dev-20260912-005）
     * <p>多个标签用 / ， , ; ； | 、 分隔；单项支持「大类*明细」两级，标签不存在时自动建档。</p>
     */
    private List<Long> resolveImportTags(String tagTexts, String operName) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isBlank(tagTexts)) {
            return ids;
        }
        for (String raw : tagTexts.split("[/,，;；|、]", -1)) {
            String item = raw == null ? "" : raw.trim();
            if (item.isEmpty()) {
                continue;
            }
            Long parentId = null;
            String name = item;
            int star = item.indexOf('*');
            if (star > 0 && star < item.length() - 1) {
                String parentName = item.substring(0, star).trim();
                name = item.substring(star + 1).trim();
                if (!parentName.isEmpty()) {
                    parentId = tagService.ensureTag(MATERIAL_TAG_GROUP, parentName, null, operName);
                }
            }
            Long tagId = tagService.ensureTag(MATERIAL_TAG_GROUP, name, parentId, operName);
            if (tagId != null && !ids.contains(tagId)) {
                ids.add(tagId);
            }
        }
        return ids;
    }

    /**
     * 生成物料编码
     * 格式：物料类型前缀 + 6 位全局流水（RM/AUX/INK 等共享同一计数器，由
     * sys_number_sequence 的 material 序列决定，非按类型独立）。
     * 例如：RM001601、AUX001574。
     * 注意：新增前该序列需已接续存量最大流水，否则会从 1 起致撞号（dev-20261009-032）。
     */
    private String generateMaterialCode(Long categoryId) {
        String prefix = MaterialClassification.codePrefixOf(categoryId == null ? null : materialMapper.selectCategory(categoryId));
        // 存量兜底：清库/回滚会把 material 序号清零，取「存量最大流水+1」作下限，避免从 1 撞存量
        Long maxSeq = materialMapper.selectMaxCodeSequence();
        long minSequence = (maxSeq == null || maxSeq <= 0) ? 0L : maxSeq + 1;
        return redisSequenceService.generateBusinessNumberByTypeWithPrefix(
                "material", prefix, "", 6, minSequence);
    }

    @Override
    public String generateUnclassifiedCode() {
        Long maximum = materialMapper.selectMaxCodeSequence();
        return redisSequenceService.generateBusinessNumberByTypeWithPrefix("material", "MAT", "", 6,
                maximum == null || maximum <= 0 ? 0L : maximum + 1);
    }

    /** 物料保存服务仅在属性保存时写入这三列；其它调用不能独立覆盖。 */
    private boolean synchronizeAttributeColumns(InventoryMaterial material, InventoryMaterial existing) {
        material.setTemplateId(null); material.setThickness(null); material.setWidthSize(null);
        boolean supplied = material.getAttributeJson() != null || material.getSuppliedFields().contains("attributeJson");
        if (!supplied) return false;
        Long categoryId = material.getCategoryId() != null ? material.getCategoryId() : existing == null ? null : existing.getCategoryId();
        Map<String, Object> attributes;
        try {
            attributes = material.getAttributeJson() == null ? Map.of() : new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(material.getAttributeJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("物料属性数据格式错误");
        }
        if (attributes == null) throw new BusinessException("物料属性必须为JSON对象");
        if (existing != null && material.getSize() == null && !material.getSuppliedFields().contains("size")) material.setSize(existing.getSize());
        com.jjx.inventory.master.MaterialAttributeColumns.apply(material,
                categoryId == null ? null : materialMapper.selectTemplateId(categoryId));
        return true;
    }

    private static final Map<String, String> MASTER_COLUMNS = Map.of(
            "materialGrade", "material_grade", "model", "model", "color", "color", "brand", "brand",
            "searchKeywords", "search_keywords", "size", "size", "specification", "specification");
    private static final Map<String, String> FIELD_LABELS = Map.of(
            "materialGrade", "材料", "model", "型号", "color", "颜色", "brand", "品牌",
            "size", "尺寸", "specification", "规格", "searchKeywords", "别名");

    private static String fieldValue(InventoryMaterial material, String key) {
        return switch (key) {
            case "materialGrade" -> material.getMaterialGrade();
            case "model" -> material.getModel();
            case "color" -> material.getColor();
            case "brand" -> material.getBrand();
            case "size" -> material.getSize();
            case "specification" -> material.getSpecification();
            case "searchKeywords" -> material.getSearchKeywords();
            default -> null;
        };
    }

    /** 新增校验全部规则；编辑仅校验实际变化的字段（含显式清空）。 */
    private void validateMasterData(InventoryMaterial material, InventoryMaterial existing) {
        if (isNewModelMaterial(material) || existing != null && isNewModelMaterial(existing)) {
            throw new BusinessException("新模型物料请使用新版物料保存入口");
        }
        if (existing != null && material.getSuppliedFields().contains("categoryId") && material.getCategoryId() == null) {
            throw new BusinessException("品类不能为空");
        }
        Long categoryId = material.getCategoryId() != null ? material.getCategoryId()
                : existing == null ? null : existing.getCategoryId();
        Map<String, Object> category = categoryId == null ? null : materialMapper.selectCategory(categoryId);
        if (categoryId != null && category == null) throw new BusinessException("品类不存在：" + categoryId);
        if (existing == null && category == null) throw new BusinessException("品类不能为空");
        if (category != null) {
            if (isNewModelCategory(category)) throw new BusinessException("该品类请使用新版物料保存入口");
        }
        String categoryCode = category == null ? null : (String) category.get("category_code");
        if (categoryCode != null) {
            for (String column : materialMapper.selectRequiredFields(categoryCode)) {
                String key = MASTER_COLUMNS.entrySet().stream().filter(e -> e.getValue().equals(column))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                if (key == null) throw new BusinessException("不支持的物料必填规则：" + column);
                String value = fieldValue(material, key);
                boolean supplied = value != null || material.getSuppliedFields().contains(key);
                boolean changed = existing == null || (supplied && !java.util.Objects.equals(value, fieldValue(existing, key)));
                if (changed && StringUtils.isBlank(value)) throw new BusinessException(FIELD_LABELS.get(key) + "不能为空");
            }
        }
        boolean sizeChanged = existing == null || !java.util.Objects.equals(material.getSize(), existing.getSize());
        if (StringUtils.isNotBlank(material.getSize()) && sizeChanged) {
            try {
                com.jjx.inventory.util.MaterialSizeParser.parse(material.getSize());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(e.getMessage());
            }
        }
    }

    /**
     * 关键词命中：物料检索列 search_blob，或分类名/中文全路径（name_path）。
     * 用 EXISTS 子查询等价 JOIN，避免破坏既有 resultMap 与分页计数（dev-20261024-006）。
     */
    private static void applyKeywordMatch(LambdaQueryWrapper<InventoryMaterial> wrapper, String term) {
        String like = "%" + term + "%";
        wrapper.apply("(search_blob LIKE {0} OR supplier_name LIKE {0} "
                + "OR EXISTS (SELECT 1 FROM inventory_material_category c "
                + "WHERE c.category_id = inventory_material.category_id "
                + "AND (c.name_path LIKE {0} OR c.category_name LIKE {0})))", like);
    }

    private static void applyAttributeFilters(LambdaQueryWrapper<InventoryMaterial> wrapper, MaterialQueryDTO dto) {
        // 查询接口忽略未知状态；有效三态继续复用既定过滤口径。
        switch (java.util.Objects.toString(dto.getClassStatus(), "")) {
            case "unclassified", "rule_unconfirmed", "confirmed" ->
                    com.jjx.inventory.classify.MaterialClassStatus.filter(wrapper, dto.getClassStatus());
            default -> { }
        }
        if (StringUtils.isNotBlank(dto.getRootType())) MaterialClassification.filterType(wrapper, dto.getRootType());
        wrapper.eq(StringUtils.isNotBlank(dto.getMaterialMeta()), InventoryMaterial::getMaterialMeta, dto.getMaterialMeta())
                .eq(StringUtils.isNotBlank(dto.getMaterialForm()), InventoryMaterial::getMaterialForm, dto.getMaterialForm())
                .eq(dto.getIsCustomerSupplied() != null, InventoryMaterial::getIsCustomerSupplied, dto.getIsCustomerSupplied())
                .eq(dto.getIsSample() != null, InventoryMaterial::getIsSample, dto.getIsSample());
        wrapper.eq(StringUtils.isNotBlank(dto.getMaterialGrade()), InventoryMaterial::getMaterialGrade, dto.getMaterialGrade())
                // 型号是自由文本，按「包含」匹配（dev-20261024-024）
                .like(StringUtils.isNotBlank(dto.getModel()), InventoryMaterial::getModel, dto.getModel())
                .eq(StringUtils.isNotBlank(dto.getColor()), InventoryMaterial::getColor, dto.getColor())
                .eq(StringUtils.isNotBlank(dto.getBrand()), InventoryMaterial::getBrand, dto.getBrand());
    }

    /** 材料受控复合词（23 个，与前端 materialMasterFields 同一份口径）。 */
    private static final List<String> IMPORT_MATERIAL_GRADES = List.of(
            "PET保护膜", "PE保护膜", "EVA泡棉", "VHB胶带", "PC膜", "PET膜", "PVC膜", "PE膜",
            "离型纸", "离型膜", "标签纸", "胶带", "泡棉", "灯", "螺丝", "垫片", "不锈钢",
            "铜", "铝", "油墨", "箱", "袋", "其他");

    /** 颜色词表（13 色）。 */
    private static final List<String> IMPORT_COLORS = List.of(
            "透明", "咖啡色", "白色", "黑色", "黄色", "红色", "蓝色", "绿色", "灰色", "银色",
            "金色", "紫色", "橙色");

    private record ImportRow(int excelRow, MaterialImportDTO dto, Map<String, Object> category, String key) { }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 名称 = 材料 + 型号 + 颜色（品牌）：无空格、缺项跳过、品牌全角括号。
     * 与前端 jjx-web/src/components/inventory/materialMasterFields.ts 的 composeMaterialName 等价。
     */
    static String composeImportName(String materialGrade, String model, String color, String brand) {
        StringBuilder name = new StringBuilder();
        for (String part : List.of(nz(materialGrade), nz(model), nz(color))) {
            name.append(part);
        }
        String brandText = nz(brand);
        if (brandText.isEmpty()) return name.toString();
        if (name.length() == 0) return brandText;
        return name + "（" + brandText + "）";
    }

    private static boolean isControlledGrade(String value) {
        return value != null && IMPORT_MATERIAL_GRADES.contains(value.trim());
    }

    /** 品类解析：编码或名称都认，统一归一为品类编码并带出大类。 */
    private Map<String, Object> resolveImportCategory(MaterialImportDTO dto) {
        String value = StringUtils.trimToNull(dto.getCategoryName());
        if (value == null) throw new BusinessException("缺【品类】列：请填写品类编码或完整名称");
        Map<String, Object> category = materialMapper.findCategory(value);
        if (category == null) throw new BusinessException("品类不存在：" + value);
        if (isNewModelCategory(category)) throw new BusinessException("该品类请下载新版按品类导入模板");
        dto.setCategoryName((String) category.get("category_code"));
        return category;
    }

    private boolean isNewModelMaterial(InventoryMaterial material) {
        return material.getCategoryId() != null && isNewModelCategory(materialMapper.selectCategory(material.getCategoryId()));
    }

    private boolean isNewModelCategory(Map<String, Object> category) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int depth = 0; category != null && depth < 4; depth++) {
            if (com.jjx.inventory.master.MaterialMasterCatalog.ROOT_TYPES.containsKey(category.get("category_code"))) return true;
            Object parent = category.get("parent_id");
            if (!(parent instanceof Number number) || number.longValue() == 0 || !seen.add(number.longValue())) return false;
            category = materialMapper.selectCategory(number.longValue());
        }
        return false;
    }

    private String importFieldValue(MaterialImportDTO dto, String key) {
        return switch (key) {
            case "materialGrade" -> dto.getMaterialGrade();
            case "model" -> dto.getModel();
            case "color" -> dto.getColor();
            case "brand" -> dto.getBrand();
            case "specification" -> dto.getSpecification();
            case "size" -> dto.getSize();
            case "searchKeywords" -> dto.getSearchKeywords();
            default -> null;
        };
    }

    /** 整表预检：按品类必填矩阵检查，错误信息指明缺哪一列。 */
    private void validateImportRow(MaterialImportDTO dto, Map<String, Object> category) {
        String categoryCode = (String) category.get("category_code");
        for (String column : materialMapper.selectRequiredFields(categoryCode)) {
            String key = MASTER_COLUMNS.entrySet().stream().filter(e -> e.getValue().equals(column))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (key == null) throw new BusinessException("不支持的物料必填规则：" + column);
            if (StringUtils.isBlank(importFieldValue(dto, key))) {
                throw new BusinessException("缺【" + FIELD_LABELS.getOrDefault(key, key) + "】列（品类 "
                        + categoryCode + " 必填）");
            }
        }
        if (StringUtils.isNotBlank(dto.getSize())) {
            try {
                com.jjx.inventory.util.MaterialSizeParser.parse(dto.getSize());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(e.getMessage());
            }
        }
        // 数值列提前解析，避免落库阶段才报错
        importDecimal(dto.getStandardPrice(), "标准单价");
        importDecimal(dto.getSafeStock(), "安全库存");
        importDecimal(dto.getMaxStock(), "最高库存");
        importDecimal(dto.getReorderPoint(), "再订货点");
        importInteger(dto.getLeadTime(), "采购周期");
        importInteger(dto.getShelfLife(), "保质期");
        importInteger(dto.getExpiryAlertDays(), "预警提前天数");
        importBoolean(dto.getBatchControl(), "批次控制");
    }

    /**
     * 去重键：品类 + 材料 + 型号 + 颜色 + 品牌 + 尺寸 + 规格（品牌用于区分不同品牌，避免误判同一物料）。
     * 若型号/尺寸/规格全空（无区分属性），追加名称，避免把不同物料误判为重复。
     */
    private String importDedupeKey(MaterialImportDTO dto) {
        String base = String.join("|",
                nz(dto.getCategoryName()), nz(dto.getMaterialGrade()), nz(dto.getModel()),
                nz(dto.getColor()), nz(dto.getBrand()), nz(dto.getSize()), nz(dto.getSpecification()));
        return hasDistinguishingAttribute(dto) ? base : base + "|" + nz(dto.getMaterialName());
    }

    private static boolean hasDistinguishingAttribute(MaterialImportDTO dto) {
        return !nz(dto.getModel()).isEmpty() || !nz(dto.getSize()).isEmpty() || !nz(dto.getSpecification()).isEmpty();
    }

    /** 库内已存在判定：按结构化属性比对，不再用「名称+规格+供应商」。 */
    private boolean importMaterialExists(MaterialImportDTO dto, Map<String, Object> category) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.apply("IFNULL(category_id, 0) = {0}", ((Number) category.get("category_id")).longValue())
                .apply("IFNULL(material_grade, '') = {0}", nz(dto.getMaterialGrade()))
                .apply("IFNULL(model, '') = {0}", nz(dto.getModel()))
                .apply("IFNULL(color, '') = {0}", nz(dto.getColor()))
                .apply("IFNULL(brand, '') = {0}", nz(dto.getBrand()))
                .apply("IFNULL(size, '') = {0}", nz(dto.getSize()))
                .apply("IFNULL(specification, '') = {0}", nz(dto.getSpecification()));
        if (!hasDistinguishingAttribute(dto)) {
            wrapper.eq(InventoryMaterial::getMaterialName, dto.getMaterialName());
        }
        Long existCount = materialMapper.selectCount(wrapper);
        return existCount != null && existCount > 0;
    }

    private static BigDecimal importDecimal(String raw, String label) {
        if (StringUtils.isBlank(raw)) return null;
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() < 0) throw new BusinessException("【" + label + "】不能为负数：" + raw);
            return value;
        } catch (NumberFormatException e) {
            throw new BusinessException("【" + label + "】不是有效数字：" + raw);
        }
    }

    private static Integer importInteger(String raw, String label) {
        if (StringUtils.isBlank(raw)) return null;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) throw new BusinessException("【" + label + "】不能为负数：" + raw);
            return value;
        } catch (NumberFormatException e) {
            throw new BusinessException("【" + label + "】不是整数：" + raw);
        }
    }

    private static Boolean importBoolean(String raw, String label) {
        if (StringUtils.isBlank(raw)) return null;
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "是", "true", "1", "y", "yes", "启用", "开" -> Boolean.TRUE;
            case "否", "false", "0", "n", "no", "禁用", "关" -> Boolean.FALSE;
            default -> throw new BusinessException("【" + label + "】只接受 是/否（true/false/1/0/启用/禁用）：" + raw);
        };
    }

    private void normalizeImport(MaterialImportDTO dto) {
        String explicitName = StringUtils.trimToNull(dto.getMaterialName());
        String gradeInput = StringUtils.trimToNull(dto.getMaterialGrade());
        // 旧模板「材料」列放的是名称：不是受控复合词时按旧名称处理，名称不改写
        boolean legacyMaterialColumn = gradeInput != null && !isControlledGrade(gradeInput);
        String inferenceName = explicitName != null ? explicitName
                : legacyMaterialColumn ? gradeInput
                : composeImportName(gradeInput, dto.getModel(), dto.getColor(), dto.getBrand());
        if (StringUtils.isBlank(dto.getCategoryName())) {
            dto.setCategoryName(inferImportCategoryCode(inferenceName));
        }
        if (explicitName == null && legacyMaterialColumn) {
            dto.setMaterialName(gradeInput);
        }
        if (legacyMaterialColumn) {
            dto.setMaterialGrade(null);
        }
        if (StringUtils.isBlank(dto.getMaterialGrade())) {
            dto.setMaterialGrade(inferImportGrade(inferenceName));
        }
        if (StringUtils.isBlank(dto.getColor())) dto.setColor(inferImportColor(inferenceName));
        if (StringUtils.isBlank(dto.getBrand())
                && inferenceName.toUpperCase(java.util.Locale.ROOT).startsWith("3M")) {
            dto.setBrand("3M");
        }
        if (StringUtils.isBlank(dto.getModel())) dto.setModel(inferImportModel(inferenceName));
        if (StringUtils.isBlank(dto.getSize())) {
            dto.setSize(com.jjx.inventory.util.MaterialSizeParser.extractLegacy(dto.getSpecification()));
        }
    }

    private static String inferImportCategoryCode(String name) {
        String text = nz(name).toUpperCase(java.util.Locale.ROOT);
        if (text.matches(".*(离型纸|离型膜|标签纸|纸材).*")) return "RM-PAPER";
        if (text.matches(".*(保护膜|双面胶|VHB).*")) return "RM-ADHESIVE";
        if (text.matches(".*(胶带|胶水|3M).*")) return "AUX-ADHESIVE";
        if (text.matches(".*(LED|灯|电阻|电容|连接器|IC).*")) return "AUX-ELEC";
        if (text.matches(".*(螺丝|垫片|弹片|铆钉|铜|铝).*")) return "AUX-HARDWARE";
        if (text.matches(".*(油墨).*")) return "RM-INK";
        if (text.matches(".*(PC|PET|PVC|PP|PE|膜|片材).*")) return "RM-FILM";
        if (text.matches(".*(纸箱|袋|泡棉|标签).*")) return "AUX-PACK";
        if (text.matches(".*(刀模|治具|砂纸|手套).*")) return "AUX-TOOL";
        return null; // 没有明确分类证据时拒绝导入，不默认归入R/A。
    }

    private static String inferImportGrade(String name) {
        String text = nz(name).toUpperCase(java.util.Locale.ROOT);
        for (String candidate : IMPORT_MATERIAL_GRADES) {
            if (text.contains(candidate.toUpperCase(java.util.Locale.ROOT))) return candidate;
        }
        if (text.contains("PC")) return "PC膜";
        if (text.contains("PET")) return "PET膜";
        if (text.contains("PVC")) return "PVC膜";
        if (text.contains("LED")) return "灯";
        if (text.startsWith("3M")) return "胶带";
        return "其他";
    }

    private static String inferImportColor(String name) {
        String text = nz(name);
        for (String color : IMPORT_COLORS) {
            if (text.contains(color)) return color;
        }
        return null;
    }

    private static String inferImportModel(String name) {
        String text = nz(name);
        if (text.isEmpty()) return null;
        String body = text.replaceFirst("(?i)^3M", "");
        String pattern = text.toUpperCase(java.util.Locale.ROOT).startsWith("3M")
                ? "^\\s*([A-Za-z0-9][A-Za-z0-9#/_.*-]*)"
                : "(?i)(?<![A-Za-z0-9.])[A-Za-z][A-Za-z0-9#/_.*-]*[0-9][A-Za-z0-9#/_.*-]*|(?<![0-9.])[0-9]+[A-Za-z][A-Za-z0-9#/_.*-]*";
        var model = java.util.regex.Pattern.compile(pattern).matcher(body);
        return model.find() ? model.group() : null;
    }

    private void setMaterialTags(Long materialId, List<Long> tagIds, String operName) {
        List<Long> requested = tagIds == null ? List.of() : tagIds.stream().distinct().toList();
        if (!requested.isEmpty()) {
            List<Long> allowed = tagService.listTags(MATERIAL_TAG_GROUP, null, StatusEnum.NORMAL.getCode()).stream()
                    .map(SysTag::getTagId).toList();
            if (!allowed.containsAll(requested)) {
                throw new BusinessException("物料标签不存在、已停用或不属于物料属性分组");
            }
        }
        tagService.setBizTags(MATERIAL_BIZ_TYPE, materialId, requested, operName);
    }

    private MaterialVO enrichTags(MaterialVO vo) {
        if (vo != null && vo.getMaterialId() != null) {
            vo.setTags(tagService.getBizTags(MATERIAL_BIZ_TYPE, vo.getMaterialId()));
        }
        return vo;
    }

    private List<MaterialVO> enrichTags(List<MaterialVO> list) {
        list.forEach(this::enrichTags);
        return list;
    }
}
