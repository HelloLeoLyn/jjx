package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.converter.StockConverter;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStock;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.domain.InventoryStorageLocation;
import com.jjx.inventory.domain.InventoryWarehouse;
import com.jjx.inventory.dto.query.StockCheckDTO;
import com.jjx.inventory.dto.query.StockBatchCheckItemDTO;
import com.jjx.inventory.dto.query.StockImportDTO;
import com.jjx.inventory.dto.query.StockQueryDTO;
import com.jjx.inventory.dto.vo.StockCheckVO;
import com.jjx.inventory.dto.vo.StockBatchCheckItemVO;
import com.jjx.inventory.dto.vo.StockImportResultVO;
import com.jjx.inventory.dto.vo.StockSummaryVO;
import com.jjx.inventory.dto.vo.StockVO;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.service.InventoryStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 库存汇总 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryStockServiceImpl extends ServiceImpl<InventoryStockMapper, InventoryStock>
        implements InventoryStockService {

    private final InventoryStockMapper stockMapper;
    private final InventoryStockItemMapper stockItemMapper;
    private final InventoryMaterialMapper materialMapper;
    private final InventoryWarehouseMapper warehouseMapper;
    private final InventoryStorageLocationMapper storageLocationMapper;
    private final StockConverter stockConverter;

    @Override
    public IPage<StockVO> page(StockQueryDTO query) {
        // 构建查询条件
        LambdaQueryWrapper<InventoryStock> wrapper = new LambdaQueryWrapper<>();

        if (query.getMaterialId() != null) {
            wrapper.eq(InventoryStock::getMaterialId, query.getMaterialId());
        }
        if (query.getMaterialCode() != null && !query.getMaterialCode().isEmpty()) {
            wrapper.like(InventoryStock::getMaterialCode, query.getMaterialCode());
        }
        if (query.getMaterialName() != null && !query.getMaterialName().isEmpty()) {
            wrapper.like(InventoryStock::getMaterialName, query.getMaterialName());
        }
        if (query.getMinQuantity() != null) {
            wrapper.ge(InventoryStock::getTotalQuantity, query.getMinQuantity());
        }
        if (query.getMaxQuantity() != null) {
            wrapper.le(InventoryStock::getTotalQuantity, query.getMaxQuantity());
        }

        // 临期查询
        if (Boolean.TRUE.equals(query.getExpiring())) {
            LocalDate alertDate = LocalDate.now().plusDays(30);
            wrapper.le(InventoryStock::getEarliestExpiry, alertDate);
            wrapper.isNotNull(InventoryStock::getEarliestExpiry);
        }

        wrapper.orderByDesc(InventoryStock::getLastUpdateTime);

        // 分页查询
        Page<InventoryStock> stockPage = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<InventoryStock> pageResult = stockMapper.selectPage(stockPage, wrapper);

        // 转换为 StockVO
        List<StockVO> voList = pageResult.getRecords().stream()
                .map(this::convertToStockVO)
                .peek(this::enrichStockVO)
                .collect(Collectors.toList());

        // 低库存过滤（需要关联物料表的安全库存，在 enrichStockVO 中已判断）
        if (Boolean.TRUE.equals(query.getLowStock())) {
            voList = voList.stream()
                    .filter(vo -> Boolean.TRUE.equals(vo.getLowStock()))
                    .collect(Collectors.toList());
        }

        // 构建分页结果
        Page<StockVO> resultPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public StockSummaryVO getSummary(StockQueryDTO query) {
        LambdaQueryWrapper<InventoryStock> wrapper = new LambdaQueryWrapper<>();
        if (query.getMaterialId() != null) {
            wrapper.eq(InventoryStock::getMaterialId, query.getMaterialId());
        }

        List<InventoryStock> stocks = stockMapper.selectList(wrapper);

        StockSummaryVO summary = new StockSummaryVO();
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalReserved = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        int materialCount = 0;
        List<Long> materialIds = new ArrayList<>();

        for (InventoryStock stock : stocks) {
            if (stock.getTotalQuantity() != null) {
                totalQuantity = totalQuantity.add(stock.getTotalQuantity());
            }
            if (stock.getTotalReserved() != null) {
                totalReserved = totalReserved.add(stock.getTotalReserved());
            }
            if (stock.getMaterialId() != null && !materialIds.contains(stock.getMaterialId())) {
                materialIds.add(stock.getMaterialId());
                materialCount++;
            }
        }

        summary.setTotalQuantity(totalQuantity);
        summary.setTotalReservedQuantity(totalReserved);
        summary.setTotalAvailableQuantity(totalQuantity.subtract(totalReserved));
        summary.setTotalCost(totalCost);
        summary.setMaterialCount(materialCount);

        return summary;
    }

    @Override
    public StockVO getById(Long stockId) {
        InventoryStock stock = stockMapper.selectById(stockId);
        if (stock == null) return null;
        StockVO vo = convertToStockVO(stock);
        enrichStockVO(vo);
        return vo;
    }

    @Override
    public StockVO getByMaterialId(Long materialId) {
        InventoryStock stock = stockMapper.selectByMaterialId(materialId);
        if (stock == null) return null;
        StockVO vo = convertToStockVO(stock);
        enrichStockVO(vo);
        return vo;
    }

    @Override
    public List<StockVO> getByWarehouseId(Long warehouseId) {
        // 通过明细表查询该仓库下有哪些物料，再查汇总
        List<InventoryStockItem> items = stockItemMapper.selectList(
                new LambdaQueryWrapper<InventoryStockItem>()
                        .eq(InventoryStockItem::getWarehouseId, warehouseId)
                        .eq(InventoryStockItem::getStatus, 1)
                        .groupBy(InventoryStockItem::getMaterialId)
        );
        List<Long> materialIds = items.stream()
                .map(InventoryStockItem::getMaterialId)
                .distinct()
                .collect(Collectors.toList());

        if (materialIds.isEmpty()) return new ArrayList<>();

        LambdaQueryWrapper<InventoryStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(InventoryStock::getMaterialId, materialIds);
        List<InventoryStock> stocks = stockMapper.selectList(wrapper);
        return stocks.stream()
                .map(this::convertToStockVO)
                .peek(this::enrichStockVO)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getAlertInfo() {
        Map<String, Object> alertInfo = new HashMap<>();

        // 低库存数量
        List<InventoryStock> lowStocks = stockMapper.selectLowStock();
        alertInfo.put("lowStockCount", lowStocks.size());

        // 临期数量
        List<InventoryStock> expiringStocks = stockMapper.selectExpiring();
        alertInfo.put("expiringStockCount", expiringStocks.size());

        // 呆滞数量
        List<InventoryStock> obsoleteStocks = stockMapper.selectObsolete();
        alertInfo.put("obsoleteStockCount", obsoleteStocks.size());

        return alertInfo;
    }

    @Override
    public List<StockVO> getLowStock() {
        List<InventoryStock> stocks = stockMapper.selectLowStock();
        return stocks.stream()
                .map(this::convertToStockVO)
                .peek(this::enrichStockVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StockVO> getExpiring() {
        List<InventoryStock> stocks = stockMapper.selectExpiring();
        return stocks.stream()
                .map(this::convertToStockVO)
                .peek(this::enrichStockVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<StockVO> getObsolete() {
        List<InventoryStock> stocks = stockMapper.selectObsolete();
        return stocks.stream()
                .map(this::convertToStockVO)
                .peek(this::enrichStockVO)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();

        // 总库存数量
        List<InventoryStock> allStocks = stockMapper.selectList(null);
        BigDecimal totalQuantity = BigDecimal.ZERO;
        for (InventoryStock stock : allStocks) {
            if (stock.getTotalQuantity() != null) {
                totalQuantity = totalQuantity.add(stock.getTotalQuantity());
            }
        }

        dashboard.put("totalQuantity", totalQuantity);
        dashboard.put("stockCount", allStocks.size());

        // 预警信息
        Map<String, Object> alertInfo = getAlertInfo();
        dashboard.putAll(alertInfo);

        return dashboard;
    }

    @Override
    public StockCheckVO check(StockCheckDTO checkDTO) {
        StockCheckVO result = new StockCheckVO();

        // 1. 查找物料
        if (checkDTO.getMaterialName() == null || checkDTO.getMaterialName().isEmpty()) {
            return result;
        }

        LambdaQueryWrapper<InventoryMaterial> materialWrapper = new LambdaQueryWrapper<>();
        materialWrapper.eq(InventoryMaterial::getMaterialName, checkDTO.getMaterialName());
        if (checkDTO.getSpecification() != null && !checkDTO.getSpecification().isEmpty()) {
            materialWrapper.eq(InventoryMaterial::getSpecification, checkDTO.getSpecification());
        }
        if (checkDTO.getSupplierName() != null && !checkDTO.getSupplierName().isEmpty()) {
            materialWrapper.like(InventoryMaterial::getSupplierName, checkDTO.getSupplierName());
        }
        materialWrapper.last("LIMIT 1");

        InventoryMaterial material = materialMapper.selectOne(materialWrapper);
        if (material != null) {
            result.setMaterialId(material.getMaterialId());
            result.setMaterialCode(material.getMaterialCode());
            result.setMaterialName(material.getMaterialName());
            result.setSpecification(material.getSpecification());
            result.setUnit(material.getUnit());
        }

        // 解析仓库（DEV-692：校验时回填仓库名）
        if (checkDTO.getWarehouseId() != null) {
            InventoryWarehouse warehouse = warehouseMapper.selectById(checkDTO.getWarehouseId());
            if (warehouse != null) {
                result.setWarehouseId(warehouse.getWarehouseId());
                result.setWarehouseName(warehouse.getWarehouseName());
            }
        }

        // 解析库位（DEV-692：按「仓库+摆放区域」匹配，回填库位编码/名称）
        if (checkDTO.getWarehouseId() != null && checkDTO.getLocationDesc() != null
                && !checkDTO.getLocationDesc().trim().isEmpty()) {
            String desc = checkDTO.getLocationDesc().trim();
            LambdaQueryWrapper<InventoryStorageLocation> locWrapper = new LambdaQueryWrapper<>();
            locWrapper.eq(InventoryStorageLocation::getWarehouseId, checkDTO.getWarehouseId())
                    .and(w -> w.eq(InventoryStorageLocation::getLocationName, desc)
                            .or().eq(InventoryStorageLocation::getLocationCode, desc))
                    .last("LIMIT 1");
            InventoryStorageLocation loc = storageLocationMapper.selectOne(locWrapper);
            if (loc != null) {
                result.setLocationCode(loc.getLocationCode());
                result.setLocationName(loc.getLocationName());
            }
        }

        return result;
    }

    @Override
    public java.util.List<StockBatchCheckItemVO> batchCheck(java.util.List<StockBatchCheckItemDTO> items) {
        java.util.List<StockBatchCheckItemVO> results = new java.util.ArrayList<>();
        if (items == null || items.isEmpty()) {
            return results;
        }

        // DEV-701：统计文件内重复（物料+规格+仓库+摆放区域）
        java.util.Map<String, Integer> dupCountMap = new java.util.HashMap<>();
        for (StockBatchCheckItemDTO item : items) {
            String k = (item.getMaterialName() == null ? "" : item.getMaterialName().trim())
                    + "|" + (item.getSpecification() == null ? "" : item.getSpecification().trim())
                    + "|" + (item.getWarehouseName() == null ? "" : item.getWarehouseName().trim())
                    + "|" + (item.getLocationDesc() == null ? "" : item.getLocationDesc().trim());
            dupCountMap.merge(k, 1, Integer::sum);
        }

        // 批量查物料缓存：key=名称|规格|供应商 -> 物料（避免逐行查库）
        java.util.Map<String, InventoryMaterial> materialCache = new java.util.HashMap<>();
        java.util.List<String> names = items.stream()
                .map(StockBatchCheckItemDTO::getMaterialName)
                .filter(n -> n != null && !n.isEmpty())
                .distinct().collect(java.util.stream.Collectors.toList());
        if (!names.isEmpty()) {
            LambdaQueryWrapper<InventoryMaterial> mw = new LambdaQueryWrapper<>();
            mw.in(InventoryMaterial::getMaterialName, names);
            for (InventoryMaterial m : materialMapper.selectList(mw)) {
                String spec = m.getSpecification() == null ? "" : m.getSpecification().trim();
                String sup = m.getSupplierName() == null ? "" : m.getSupplierName().trim();
                materialCache.put(m.getMaterialName().trim() + "|" + spec + "|" + sup, m);
            }
        }

        // 批量查仓库缓存：名称 -> 仓库
        java.util.Map<String, InventoryWarehouse> warehouseCache = new java.util.HashMap<>();
        java.util.List<String> whNames = items.stream()
                .map(StockBatchCheckItemDTO::getWarehouseName)
                .filter(n -> n != null && !n.isEmpty())
                .distinct().collect(java.util.stream.Collectors.toList());
        if (!whNames.isEmpty()) {
            LambdaQueryWrapper<InventoryWarehouse> ww = new LambdaQueryWrapper<>();
            ww.in(InventoryWarehouse::getWarehouseName, whNames);
            for (InventoryWarehouse w : warehouseMapper.selectList(ww)) {
                warehouseCache.put(w.getWarehouseName().trim(), w);
            }
        }

        for (StockBatchCheckItemDTO item : items) {
            StockBatchCheckItemVO vo = new StockBatchCheckItemVO();
            vo.setRowIndex(item.getRowIndex());
            vo.setStatus("ok");

            // 0. 文件内重复检测（DEV-701：同物料+规格+仓库+摆放区域重复行，导入会撞唯一键）
            String dupKey = (item.getMaterialName() == null ? "" : item.getMaterialName().trim())
                    + "|" + (item.getSpecification() == null ? "" : item.getSpecification().trim())
                    + "|" + (item.getWarehouseName() == null ? "" : item.getWarehouseName().trim())
                    + "|" + (item.getLocationDesc() == null ? "" : item.getLocationDesc().trim());
            Integer dupCount = dupCountMap.get(dupKey);
            if (dupCount != null && dupCount > 1) {
                vo.setStatus("error");
                vo.setErrorType("DUPLICATE");
                addFieldError(vo, "materialName", "DUPLICATE",
                        "文件内重复行（同一物料+仓库+摆放区域出现 " + dupCount + " 次），导入会冲突，请删除重复行或合并数量");
            }

            // 1. 物料名称必填
            String name = item.getMaterialName() == null ? "" : item.getMaterialName().trim();
            if (name.isEmpty()) {
                vo.setStatus("error");
                vo.setErrorType("MISSING_REQUIRED");
                addFieldError(vo, "materialName", "MISSING_REQUIRED", "物料名称不能为空");
            } else {
                // 2. 查物料（名称+规格+供应商匹配，与单行 check 一致）
                String spec = item.getSpecification() == null ? "" : item.getSpecification().trim();
                String sup = item.getSupplierName() == null ? "" : item.getSupplierName().trim();
                InventoryMaterial m = materialCache.get(name + "|" + spec + "|" + sup);
                if (m == null) {
                    // 降级：只按名称+规格匹配（供应商可不填）
                    for (java.util.Map.Entry<String, InventoryMaterial> e : materialCache.entrySet()) {
                        if (e.getKey().startsWith(name + "|" + spec + "|")) {
                            m = e.getValue();
                            break;
                        }
                    }
                }
                if (m == null) {
                    vo.setStatus("error");
                    vo.setErrorType("NOT_FOUND");
                    addFieldError(vo, "materialName", "NOT_FOUND", "物料未建档: " + name + (spec.isEmpty() ? "" : " / " + spec));
                } else {
                    vo.setMaterialId(m.getMaterialId());
                    vo.setMaterialCode(m.getMaterialCode());
                }
            }

            // 3. 仓库校验
            if (vo.getStatus().equals("ok")) {
                String whName = item.getWarehouseName() == null ? "" : item.getWarehouseName().trim();
                if (whName.isEmpty()) {
                    vo.setStatus("error");
                    vo.setErrorType("MISSING_REQUIRED");
                    addFieldError(vo, "warehouseName", "MISSING_REQUIRED", "仓库不能为空");
                } else if (!warehouseCache.containsKey(whName)) {
                    vo.setStatus("error");
                    vo.setErrorType("WAREHOUSE_NOT_FOUND");
                    addFieldError(vo, "warehouseName", "WAREHOUSE_NOT_FOUND", "仓库不存在: " + whName);
                }
            }

            // 4. 数量校验
            if (vo.getStatus().equals("ok")) {
                if (item.getQuantity() == null || item.getQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    vo.setStatus("error");
                    vo.setErrorType("INVALID");
                    addFieldError(vo, "quantity", "INVALID", "库存数量必须大于0");
                }
            }

            results.add(vo);
        }
        return results;
    }

    private void addFieldError(StockBatchCheckItemVO vo, String field, String type, String message) {
        StockBatchCheckItemVO.FieldError fe = new StockBatchCheckItemVO.FieldError();
        fe.setField(field);
        fe.setType(type);
        fe.setMessage(message);
        vo.getErrors().add(fe);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockImportResultVO batchImport(List<StockImportDTO> list) {
        StockImportResultVO result = new StockImportResultVO();

        if (list == null || list.isEmpty()) {
            return result;
        }

        // 0. 批量查询所有仓库，缓存到 Map<仓库名称, 仓库>（模板「仓库」列为名称）
        List<String> warehouseNames = list.stream()
                .map(StockImportDTO::getWarehouseName)
                .filter(n -> n != null && !n.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        Map<String, InventoryWarehouse> warehouseCache = new HashMap<>();
        if (!warehouseNames.isEmpty()) {
            LambdaQueryWrapper<InventoryWarehouse> warehouseWrapper = new LambdaQueryWrapper<>();
            warehouseWrapper.in(InventoryWarehouse::getWarehouseName, warehouseNames);
            List<InventoryWarehouse> warehouses = warehouseMapper.selectList(warehouseWrapper);
            for (InventoryWarehouse w : warehouses) {
                warehouseCache.put(w.getWarehouseName(), w);
            }
        }

        // 1. 批量查询所有物料，缓存到 Map<物料名称+规格, 物料>
        List<String> materialNames = list.stream()
                .map(StockImportDTO::getMaterialName)
                .filter(n -> n != null && !n.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        Map<String, InventoryMaterial> materialCache = new HashMap<>();
        if (!materialNames.isEmpty()) {
            LambdaQueryWrapper<InventoryMaterial> materialWrapper = new LambdaQueryWrapper<>();
            materialWrapper.in(InventoryMaterial::getMaterialName, materialNames);
            List<InventoryMaterial> materials = materialMapper.selectList(materialWrapper);
            for (InventoryMaterial m : materials) {
                materialCache.put(buildMaterialKey(m.getMaterialName(), m.getSpecification()), m);
            }
        }

        // 3. 遍历导入数据，逐条处理
        int rowIndex = 0;
        // 库位缓存：key=warehouseId|locationDesc -> locationId，避免重复查询/创建
        Map<String, Long> locationCache = new HashMap<>();
        for (StockImportDTO dto : list) {
            rowIndex++;

            // 3.1 基本校验
            if (dto.getMaterialName() == null || dto.getMaterialName().isEmpty()) {
                result.addFail(rowIndex, "", "物料名称为空");
                continue;
            }
            if (dto.getQuantity() == null || dto.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                result.addFail(rowIndex, dto.getMaterialName(), "库存数量必须大于0");
                continue;
            }

            // 3.2 查找物料
            String materialKey = buildMaterialKey(dto.getMaterialName(), dto.getSpecification());
            InventoryMaterial material = materialCache.get(materialKey);
            if (material == null) {
                result.addFail(rowIndex, dto.getMaterialName(), "物料未建档，请先校验并建档");
                continue;
            }

            // 3.2.1 解析仓库：模板「仓库」列为名称，转仓库ID；空或查不到报错
            Long warehouseId;
            if (dto.getWarehouseName() != null && !dto.getWarehouseName().isEmpty()) {
                InventoryWarehouse warehouse = warehouseCache.get(dto.getWarehouseName());
                if (warehouse == null) {
                    result.addFail(rowIndex, dto.getMaterialName(), "仓库'" + dto.getWarehouseName() + "'不存在");
                    continue;
                }
                warehouseId = warehouse.getWarehouseId();
            } else {
                result.addFail(rowIndex, dto.getMaterialName(), "仓库不能为空");
                continue;
            }

            // 3.2.2 解析摆放区域 → 查找或自动创建库位（DEV-692）
            Long locationId = null;
            String locationDesc = dto.getLocationDesc();
            if (locationDesc != null && !locationDesc.trim().isEmpty()) {
                String desc = locationDesc.trim();
                String cacheKey = warehouseId + "|" + desc;
                if (locationCache.containsKey(cacheKey)) {
                    locationId = locationCache.get(cacheKey);
                } else {
                    try {
                        // 按「仓库+名称或编码」查找已存在库位
                        LambdaQueryWrapper<InventoryStorageLocation> locWrapper = new LambdaQueryWrapper<>();
                        locWrapper.eq(InventoryStorageLocation::getWarehouseId, warehouseId)
                                .and(w -> w.eq(InventoryStorageLocation::getLocationName, desc)
                                        .or().eq(InventoryStorageLocation::getLocationCode, desc))
                                .last("LIMIT 1");
                        InventoryStorageLocation existLoc = storageLocationMapper.selectOne(locWrapper);
                        if (existLoc != null) {
                            locationId = existLoc.getLocationId();
                        } else {
                            // 自动创建库位：编码=仓库编码-序号（如 WH-RAW-01）
                            InventoryWarehouse wh = warehouseCache.get(dto.getWarehouseName());
                            String prefix = wh != null && wh.getWarehouseCode() != null
                                    ? wh.getWarehouseCode() : "WH" + warehouseId;
                            Long count = storageLocationMapper.selectCount(
                                    new LambdaQueryWrapper<InventoryStorageLocation>()
                                            .eq(InventoryStorageLocation::getWarehouseId, warehouseId));
                            InventoryStorageLocation newLoc = new InventoryStorageLocation();
                            newLoc.setWarehouseId(warehouseId);
                            newLoc.setLocationCode(String.format("%s-%02d", prefix, count + 1));
                            newLoc.setLocationName(desc);
                            newLoc.setLocationType("normal");
                            newLoc.setCapacity(new BigDecimal("99999"));
                            newLoc.setUsedCapacity(BigDecimal.ZERO);
                            newLoc.setSortOrder(0);
                            newLoc.setStatus("0"); // 启用
                            storageLocationMapper.insert(newLoc);
                            locationId = newLoc.getLocationId();
                            log.info("库存导入自动创建库位: {} ({})", newLoc.getLocationCode(), desc);
                        }
                    } catch (Exception e) {
                        log.warn("库存导入解析库位失败(跳过): warehouseId={}, desc={}, err={}", warehouseId, desc, e.getMessage());
                        locationId = null;
                    }
                    locationCache.put(cacheKey, locationId);
                }
            }

            // 3.4 写入明细表
            String batchNo = dto.getBatchNo() != null && !dto.getBatchNo().isEmpty()
                    ? dto.getBatchNo()
                    : LocalDate.now().toString();

            // DEV-701：同(物料+仓库+库位+批次)已存在 → 数量累加，避免唯一键冲突导致整批回滚
            InventoryStockItem existing = stockItemMapper.selectOne(
                    new LambdaQueryWrapper<InventoryStockItem>()
                            .eq(InventoryStockItem::getMaterialId, material.getMaterialId())
                            .eq(InventoryStockItem::getWarehouseId, warehouseId)
                            .eq(locationId != null, InventoryStockItem::getLocationId, locationId)
                            .eq(InventoryStockItem::getBatchNo, batchNo)
                            .last("LIMIT 1"));
            if (existing != null) {
                existing.setQuantity(existing.getQuantity().add(dto.getQuantity()));
                existing.setLastInboundTime(LocalDateTime.now());
                if (dto.getUnitCost() != null && existing.getUnitCost() == null) {
                    existing.setUnitCost(dto.getUnitCost());
                }
                stockItemMapper.updateById(existing);
                log.info("库存导入累加数量: material={}, batch={}, qty+={}", material.getMaterialName(), batchNo, dto.getQuantity());
                refreshSummary(material.getMaterialId());
                result.addSuccess();
                continue;
            }

            // 查找或创建明细记录
            InventoryStockItem newItem = new InventoryStockItem();
            newItem.setMaterialId(material.getMaterialId());
            newItem.setMaterialCode(material.getMaterialCode());
            newItem.setMaterialName(material.getMaterialName());
            newItem.setWarehouseId(warehouseId);
            newItem.setLocationId(locationId);
            newItem.setBatchNo(batchNo);
            newItem.setQuantity(dto.getQuantity());
            newItem.setReservedQuantity(BigDecimal.ZERO);
            newItem.setUnitCost(dto.getUnitCost());
            newItem.setStatus(1); // 生效

            // 解析日期
            if (dto.getProductionDate() != null && !dto.getProductionDate().isEmpty()) {
                try {
                    newItem.setProductionDate(LocalDate.parse(dto.getProductionDate()));
                } catch (Exception e) {
                    log.warn("解析生产日期失败: {}", dto.getProductionDate());
                }
            }else{
                newItem.setProductionDate(LocalDate.now());
            }
            if (dto.getExpiryDate() != null && !dto.getExpiryDate().isEmpty()) {
                try {
                    newItem.setExpiryDate(LocalDate.parse(dto.getExpiryDate()));
                } catch (Exception e) {
                    log.warn("解析到期日期失败: {}", dto.getExpiryDate());
                }
            }else{
                newItem.setExpiryDate(LocalDate.now().plusYears(1));
            }

            newItem.setLastInboundTime(LocalDateTime.now());
            stockItemMapper.insert(newItem);

            // 3.6 刷新汇总表
            refreshSummary(material.getMaterialId());

            result.addSuccess();
        }

        log.info("库存导入完成：成功{}条，失败{}条", result.getSuccessCount(), result.getFailCount());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshSummary(Long materialId) {
        // 检查汇总记录是否存在，不存在则创建
        InventoryStock stock = stockMapper.selectByMaterialId(materialId);
        if (stock == null) {
            InventoryMaterial material = materialMapper.selectById(materialId);
            if (material == null) {
                log.warn("物料不存在，无法刷新汇总: materialId={}", materialId);
                return;
            }
            stock = new InventoryStock();
            stock.setMaterialId(materialId);
            stock.setMaterialCode(material.getMaterialCode());
            stock.setMaterialName(material.getMaterialName());
            stock.setTotalQuantity(BigDecimal.ZERO);
            stock.setTotalReserved(BigDecimal.ZERO);
            stock.setEarliestExpiry(LocalDate.now().plusYears(1));
            stockMapper.insert(stock);
        }

        // 刷新汇总数据
        stockMapper.refreshSummary(materialId);
    }

    /**
     * 将 InventoryStock 实体转换为 StockVO
     */
    private StockVO convertToStockVO(InventoryStock stock) {
        if (stock == null) return null;

        StockVO vo = stockConverter.toVO(stock);

        // 计算距离过期天数
        if (stock.getEarliestExpiry() != null) {
            vo.setDaysToExpiry(ChronoUnit.DAYS.between(LocalDate.now(), stock.getEarliestExpiry()));
            vo.setExpiring(vo.getDaysToExpiry() <= 30);
        }

        return vo;
    }

    /**
     * 填充仓库名称和库位名称
     */
    private void enrichStockVO(StockVO vo) {
        if (vo == null) return;

        // 填充库位名称（DEV-692）
        if (vo.getLocationId() != null) {
            InventoryStorageLocation loc = storageLocationMapper.selectById(vo.getLocationId());
            if (loc != null) {
                vo.setLocationCode(loc.getLocationCode());
                vo.setLocationName(loc.getLocationName());
            }
        }

        // 填充物料信息
        if (vo.getMaterialId() != null) {
            InventoryMaterial material = materialMapper.selectById(vo.getMaterialId());
            if (material != null) {
                vo.setSpecification(material.getSpecification());
                vo.setUnit(material.getUnit());
                vo.setSafeStock(material.getSafeStock());
                vo.setMaxStock(material.getMaxStock());
                // 判断是否低库存
                if (material.getSafeStock() != null && vo.getTotalQuantity() != null) {
                    vo.setLowStock(vo.getTotalQuantity().compareTo(material.getSafeStock()) < 0);
                }
            }
        }
    }

    /**
     * 构建物料缓存key：物料名称+规格
     */
    private String buildMaterialKey(String name, String spec) {
        String cleanName = name != null ? name.trim() : "";
        String cleanSpec = spec != null ? spec.trim() : "";
        return cleanName + "|" + cleanSpec;
    }
}
