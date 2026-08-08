package com.jjx.inventory.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import com.jjx.common.enums.StatusEnum;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.converter.MaterialConverter;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.dto.imports.MaterialImportDTO;
import com.jjx.inventory.dto.query.MaterialCheckDTO;
import com.jjx.inventory.dto.query.MaterialQueryDTO;
import com.jjx.inventory.dto.vo.MaterialVO;
import com.jjx.inventory.enums.ProcessGroup;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryStockMapper;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.purchase.domain.vo.PurchaseSupplierVO;
import com.jjx.purchase.service.IPurchaseSupplierService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.jjx.system.annotation.Event;

/**
 * 物料主数据服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryMaterialServiceImpl extends ServiceImpl<InventoryMaterialMapper, InventoryMaterial>
        implements InventoryMaterialService {

    private final InventoryMaterialMapper materialMapper;
    private final InventoryStockMapper stockMapper;
    private final RedisSequenceService redisSequenceService;
    private final MaterialConverter materialConverter;
    private final IPurchaseSupplierService purchaseSupplierService;
    private final com.jjx.purchase.mapper.PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final com.jjx.sales.mapper.SalesOrderProductMapper salesOrderProductMapper;
    private final com.jjx.production.mapper.ProductionOrderMapper productionOrderMapper;

    @Override
    public PageResult<MaterialVO> pageQuery(MaterialQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = buildQueryWrapper(queryDTO);
        Page<InventoryMaterial> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        materialMapper.selectPage(page, wrapper);
        List<InventoryMaterial> records = page.getRecords();
        List<MaterialVO> voList = materialConverter.toVOList(records);
        return PageResult.build(voList, page.getTotal());
    }

    private static @NonNull LambdaQueryWrapper<InventoryMaterial> buildQueryWrapper(MaterialQueryDTO queryDTO) {
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getMaterialCode() != null && !queryDTO.getMaterialCode().isEmpty()) {
            wrapper.like(InventoryMaterial::getMaterialCode, queryDTO.getMaterialCode());
        }
        if (queryDTO.getMaterialName() != null && !queryDTO.getMaterialName().isEmpty()) {
            wrapper.like(InventoryMaterial::getMaterialName, queryDTO.getMaterialName());
        }
        if (queryDTO.getSpecification() != null && !queryDTO.getSpecification().isEmpty()) {
            wrapper.eq(InventoryMaterial::getSpecification, queryDTO.getSpecification());
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
        wrapper.orderByDesc(InventoryMaterial::getCreateTime);
        return wrapper;
    }

    @Override
    public MaterialVO getDetailById(Long id) {
        InventoryMaterial material = materialMapper.selectById(id);
        return materialConverter.toVO(material);
    }

    @Override
    public InventoryMaterial getByCode(String materialCode) {
        return materialMapper.selectByCode(materialCode);
    }

    @Override
    @Event(value = "inventory.material.created", bizId = "#material", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean create(InventoryMaterial material) {
        // 检查编码是否已存在
        if (existsByCode(material.getMaterialCode())) {
            log.error("物料编码已存在: {}", material.getMaterialCode());
            return false;
        }

        return materialMapper.insert(material) > 0;
    }

    @Override
    @Event(value = "inventory.material.updated", bizId = "#material", bizType = "'inventory'")
    @Transactional(rollbackFor = Exception.class)
    public boolean update(InventoryMaterial material) {
        InventoryMaterial existing = materialMapper.selectById(material.getMaterialId());
        if (existing == null) {
            log.error("物料不存在: {}", material.getMaterialId());
            return false;
        }

        // 检查编码是否重复（如果修改了编码）
        if (!existing.getMaterialCode().equals(material.getMaterialCode())) {
            if (existsByCode(material.getMaterialCode())) {
                log.error("物料编码已存在: {}", material.getMaterialCode());
                return false;
            }
        }

        return materialMapper.updateById(material) > 0;
    }

    @Override
    @Event(value = "inventory.material.deleted", bizId = "#id", bizType = "'inventory'")
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

        return materialMapper.deleteById(id) > 0;
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
        // 按编码/名称/英文名模糊匹配（8-03 修复：原条件错位导致搜不到）
        // 8-08 修复：无任何查询条件时不再拼 and() 空括号（SQL: WHERE () 语法错误），直接查全部
        LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
        String code = queryDTO.getMaterialCode();
        String name = queryDTO.getMaterialName();
        boolean hasCondition = StringUtils.isNotBlank(code) || StringUtils.isNotBlank(name);
        if (hasCondition) {
            wrapper.and(w -> {
                w.like(StringUtils.isNotBlank(code), InventoryMaterial::getMaterialCode, code)
                        .or().like(StringUtils.isNotBlank(name), InventoryMaterial::getMaterialName, name)
                        .or().like(StringUtils.isNotBlank(name), InventoryMaterial::getMaterialNameEn, name);
            });
        }
        IPage<InventoryMaterial> page = new Page<InventoryMaterial>().setSize(queryDTO.getPageSize())
                .setCurrent(queryDTO.getPageNum());
        materialMapper.selectPage(page, wrapper);
        List<MaterialVO> voList = materialConverter.toVOList(page.getRecords());
        return PageResult.build(voList, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.jjx.inventory.dto.vo.MaterialImportResultVO importMaterial(List<MaterialImportDTO> importList, String operName) {
        com.jjx.inventory.dto.vo.MaterialImportResultVO result = new com.jjx.inventory.dto.vo.MaterialImportResultVO();
        if (importList == null || importList.isEmpty()) {
            return result;
        }

        int successCount = 0;
        int skipCount = 0;
        // DEV-702：文件内重复检测（材料+规格+供应商）
        java.util.Map<String, Integer> dupCountMap = new java.util.HashMap<>();
        for (MaterialImportDTO dto : importList) {
            String k = (dto.getMaterialName() == null ? "" : dto.getMaterialName().trim())
                    + "|" + (dto.getSpecification() == null ? "" : dto.getSpecification().trim())
                    + "|" + (dto.getSupplierName() == null ? "" : dto.getSupplierName().trim());
            dupCountMap.merge(k, 1, Integer::sum);
        }
        String dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (int i = 0; i < importList.size(); i++) {
            MaterialImportDTO dto = importList.get(i);
            int excelRow = i + 2; // 第1行表头，数据从第2行开始
            try {
                // 文件内重复检测
                String dupKey = (dto.getMaterialName() == null ? "" : dto.getMaterialName().trim())
                        + "|" + (dto.getSpecification() == null ? "" : dto.getSpecification().trim())
                        + "|" + (dto.getSupplierName() == null ? "" : dto.getSupplierName().trim());
                if (dupCountMap.getOrDefault(dupKey, 0) > 1) {
                    result.addFail(excelRow, dto.getMaterialName(), "文件内重复行（同一材料+规格+供应商出现 " + dupCountMap.get(dupKey) + " 次），请删除重复行或合并");
                    continue;
                }

                PurchaseSupplierVO purchaseSupplierVO = purchaseSupplierService
                        .selectSupplierByName(dto.getSupplierName());
                if (purchaseSupplierVO == null) {
                    result.addFail(excelRow, dto.getMaterialName(), "找不到对应供应商: " + dto.getSupplierName());
                    continue;
                }
                // 检查是否已存在（按物料名称+规格+供应商去重）
                LambdaQueryWrapper<InventoryMaterial> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(InventoryMaterial::getMaterialName, dto.getMaterialName());
                if (dto.getSpecification() != null && !dto.getSpecification().isEmpty()) {
                    wrapper.eq(InventoryMaterial::getSpecification, dto.getSpecification());
                }
                if (dto.getSupplierName() != null && !dto.getSupplierName().isEmpty()) {
                    wrapper.eq(InventoryMaterial::getSupplierName, dto.getSupplierName());
                }

                Long existCount = materialMapper.selectCount(wrapper);
                if (existCount != null && existCount > 0) {
                    skipCount++;
                    continue;
                }

                // 生成物料编码
                String materialCode = generateMaterialCode(dateKey);

                // 创建物料实体
                InventoryMaterial material = new InventoryMaterial();
                material.setMaterialCode(materialCode);
                material.setMaterialName(dto.getMaterialName());
                material.setMaterialNameEn(dto.getMaterialNameEn());
                material.setMaterialType(dto.getMaterialType());
                material.setSpecification(dto.getSpecification());
                material.setUnit(dto.getUnit());
                material.setSupplierId(purchaseSupplierVO.getSupplierId());
                material.setSupplierName(dto.getSupplierName());
                material.setRemark(dto.getRemark());
                material.setStatus(StatusEnum.NORMAL.getCode());
                material.setSafeStock(BigDecimal.ZERO);
                material.setMaxStock(BigDecimal.ZERO);
                material.setReorderPoint(BigDecimal.ZERO);
                material.setBatchControl(false);
                material.setExpiryAlertDays(30);
                material.setProcessGroup(getProcessGroup(dto));
                materialMapper.insert(material);
                successCount++;

            } catch (Exception e) {
                log.error("导入第{}条数据失败: {}", i + 1, dto.getMaterialName(), e);
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
        return materialConverter.toVOList(inventoryMaterials);
    }

    @Override
    public String generateCode() {
        String dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return generateMaterialCode(dateKey);
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
        return materialConverter.toVO(material);
    }

    private static String getProcessGroup(MaterialImportDTO dto) {
        ProcessGroup processGroup = ProcessGroup.fromCode(dto.getProcessGroup());
        if (processGroup == null) {
            processGroup = ProcessGroup.fromName(dto.getProcessGroup());
            return processGroup.getCode();
        }
        return null;
    }

    /**
     * 生成物料编码
     * 格式：MTR + 日期(yyyyMMdd) + 序列号(4位)
     * 例如：MTR202604280001
     */
    private String generateMaterialCode(String dateKey) {
        Long sequence = redisSequenceService.getNextSequence("material:" + dateKey);
        return "MTR" + dateKey + String.format("%04d", sequence);
    }
}
