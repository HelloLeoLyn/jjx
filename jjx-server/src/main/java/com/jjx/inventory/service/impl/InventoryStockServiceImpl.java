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
import com.jjx.inventory.dto.query.StockImportDTO;
import com.jjx.inventory.dto.query.StockQueryDTO;
import com.jjx.inventory.dto.vo.StockCheckVO;
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
    private final InventoryStorageLocationMapper locationMapper;
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

        // 2. 根据摆放/区域描述解析仓库和库位
        if (checkDTO.getLocationDesc() != null && !checkDTO.getLocationDesc().isEmpty()) {
            LambdaQueryWrapper<InventoryStorageLocation> locationWrapper = new LambdaQueryWrapper<>();
            locationWrapper.eq(InventoryStorageLocation::getWarehouseId, checkDTO.getWarehouseId());
            locationWrapper.like(InventoryStorageLocation::getLocationName, checkDTO.getLocationDesc());
            InventoryStorageLocation location = locationMapper.selectOne(locationWrapper);
            if (location != null) {
                result.setLocationCode(location.getLocationCode());
                result.setLocationName(location.getLocationName());
                result.setWarehouseId(checkDTO.getWarehouseId());
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockImportResultVO batchImport(List<StockImportDTO> list, boolean autoCreateLocation) {
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

        // 2. 批量查询所有库位，缓存到 Map<库位编码, 库位>
        List<String> locationCodes = list.stream()
                .map(StockImportDTO::getLocationCode)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        Map<String, InventoryStorageLocation> locationCache = new HashMap<>();
        Map<String, InventoryStorageLocation> locationNameCache = new HashMap<>();
        if (!locationCodes.isEmpty()) {
            LambdaQueryWrapper<InventoryStorageLocation> locationWrapper = new LambdaQueryWrapper<>();
            locationWrapper.in(InventoryStorageLocation::getLocationCode, locationCodes);
            List<InventoryStorageLocation> locations = locationMapper.selectList(locationWrapper);
            for (InventoryStorageLocation loc : locations) {
                locationCache.put(loc.getLocationCode(), loc);
                locationNameCache.put(normalizeName(loc.getLocationName()), loc);
            }
        }

        // 2.1 若有摆放区域描述，补充加载匹配的库位（去空格名称匹配）
        List<String> locationDescs = list.stream()
                .map(StockImportDTO::getLocationDesc)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (!locationDescs.isEmpty()) {
            for (String desc : locationDescs) {
                if (locationNameCache.containsKey(normalizeName(desc))) {
                    continue;
                }
                List<InventoryStorageLocation> matched = locationMapper.selectList(
                        new LambdaQueryWrapper<InventoryStorageLocation>()
                                .likeRight(InventoryStorageLocation::getLocationName, desc.trim()));
                for (InventoryStorageLocation loc : matched) {
                    locationCache.putIfAbsent(loc.getLocationCode(), loc);
                    locationNameCache.putIfAbsent(normalizeName(loc.getLocationName()), loc);
                }
            }
        }

        // 3. 遍历导入数据，逐条处理
        int rowIndex = 0;
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

            // 3.3 校验库位：优先 locationCode，其次按摆放区域(去空格名称)匹配；匹配不到且 autoCreateLocation 时自动创建
            InventoryStorageLocation location = null;
            if (dto.getLocationCode() != null && !dto.getLocationCode().isEmpty()) {
                location = locationCache.get(dto.getLocationCode());
                if (location == null) {
                    result.addFail(rowIndex, dto.getMaterialName(),
                            "库位编码'" + dto.getLocationCode() + "'不存在");
                    continue;
                }
            } else if (dto.getLocationDesc() != null && !dto.getLocationDesc().isEmpty()) {
                location = locationNameCache.get(normalizeName(dto.getLocationDesc()));
                if (location == null && autoCreateLocation) {
                    location = createLocation(warehouseId, dto.getLocationDesc());
                    if (location != null) {
                        locationCache.put(location.getLocationCode(), location);
                        locationNameCache.put(normalizeName(location.getLocationName()), location);
                    }
                }
            }
            if (location != null && location.getCapacity() != null && location.getUsedCapacity() != null) {
                BigDecimal newUsed = location.getUsedCapacity().add(dto.getQuantity());
                if (newUsed.compareTo(location.getCapacity()) > 0) {
                    result.addFail(rowIndex, dto.getMaterialName(),
                            "库位'" + location.getLocationName() + "'容量不足，当前已用"
                                    + location.getUsedCapacity() + "，最大容量"
                                    + location.getCapacity() + "，导入后为" + newUsed);
                    continue;
                }
            }

            // 3.4 写入明细表
            String batchNo = dto.getBatchNo() != null && !dto.getBatchNo().isEmpty()
                    ? dto.getBatchNo()
                    : LocalDate.now().toString();

            // 查找或创建明细记录
            InventoryStockItem newItem = new InventoryStockItem();
            newItem.setMaterialId(material.getMaterialId());
            newItem.setMaterialCode(material.getMaterialCode());
            newItem.setMaterialName(material.getMaterialName());
            newItem.setWarehouseId(warehouseId);
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

            // 设置库位
            if (location != null) {
                newItem.setLocationId(location.getLocationId());
            }
            // 更新库位已使用容量
            if (location != null && location.getCapacity() != null) {
                BigDecimal newUsed = location.getUsedCapacity() != null
                        ? location.getUsedCapacity().add(dto.getQuantity())
                        : dto.getQuantity();
                location.setUsedCapacity(newUsed);
                locationMapper.updateById(location);
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

        // 填充库位信息
        if (vo.getLocationId() != null) {
            InventoryStorageLocation location = locationMapper.selectById(vo.getLocationId());
            if (location != null) {
                vo.setLocationCode(location.getLocationCode());
                vo.setLocationName(location.getLocationName());
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

    /**
     * 规范化库位名称/区域描述：去空格，用于匹配
     */
    private String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    /**
     * 自动创建库位（导入时区域无对应库位且 autoCreateLocation=true）
     * 编码规则：<区域首字母>-<序号>，如 B-01；名称取区域描述
     */
    private InventoryStorageLocation createLocation(Long warehouseId, String locationDesc) {
        try {
            String desc = locationDesc.trim();
            // 生成编码：取区域首个字母 + 序号
            char prefix = 'L';
            if (!desc.isEmpty()) {
                char first = desc.charAt(0);
                if (Character.isLetter(first)) {
                    prefix = Character.toUpperCase(first);
                }
            }
            int seq = locationMapper.selectCount(
                    new LambdaQueryWrapper<InventoryStorageLocation>()
                            .likeRight(InventoryStorageLocation::getLocationCode, prefix + "-")).intValue() + 1;
            String code = String.format("%s-%02d", prefix, seq);

            InventoryStorageLocation loc = new InventoryStorageLocation();
            loc.setWarehouseId(warehouseId);
            loc.setLocationCode(code);
            loc.setLocationName(desc);
            loc.setLocationType("normal");
            loc.setCapacity(new BigDecimal("100000"));
            loc.setUsedCapacity(BigDecimal.ZERO);
            loc.setStatus("0");
            loc.setCreateBy("admin");
            loc.setCreateTime(LocalDateTime.now());
            locationMapper.insert(loc);
            log.info("导入自动创建库位: {} ({})", code, desc);
            return loc;
        } catch (Exception e) {
            log.error("自动创建库位失败: {}", locationDesc, e);
            return null;
        }
    }
}
