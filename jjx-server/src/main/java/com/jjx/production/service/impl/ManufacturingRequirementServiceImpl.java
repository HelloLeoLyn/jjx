package com.jjx.production.service.impl;

import com.jjx.common.enums.StatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.RequirementPreviewRequest;
import com.jjx.production.domain.model.ExactQuantity;
import com.jjx.production.domain.model.ManufacturingDemandSnapshot;
import com.jjx.production.domain.vo.RequirementLineVO;
import com.jjx.production.domain.vo.RequirementNodeVO;
import com.jjx.production.domain.vo.RequirementSummaryVO;
import com.jjx.production.mapper.ManufacturingSupplySnapshotMapper;
import com.jjx.production.service.ManufacturingRequirementService;
import com.jjx.production.service.ManufacturingSupplySnapshotProvider;
import com.jjx.production.service.ManufacturingSupplySnapshotProvider.SupplySnapshot;
import com.jjx.product.enums.ProductEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 多层制造需求：精确比例 + 显式 sub_bom_id 展开 + 跨层汇齐 + 服务端供给净额（dev-20261111-006）。
 *
 * <p>只读试算。库存/采购/生产供给全部来自服务端只读查询；不扣库存、不预留、不写流水、不建工单。
 * 计算顺序：按 BOM 依赖从根向下，先汇齐同一物料的全部来源贡献，再统一分配库存/供给，只对缺口展开下层。</p>
 */
@Service
@RequiredArgsConstructor
public class ManufacturingRequirementServiceImpl implements ManufacturingRequirementService {

    private static final Set<String> PIECE_UNITS = Set.of(
            "PCS", "PC", "EA", "SET", "PIN", "SHEET",
            "个", "件", "片", "张", "只", "套", "支", "条", "瓶", "卷");
    private static final Set<String> CONTINUOUS_UNITS = Set.of(
            "M", "M2", "KG", "G", "L", "ML");

    private final ManufacturingSupplySnapshotMapper mapper;
    private final ManufacturingSupplySnapshotProvider supplyProvider;

    @Override
    public RequirementSummaryVO preview(RequirementPreviewRequest request) {
        if (request == null) {
            throw new BusinessException("试算请求不能为空");
        }
        if (request.getProductId() == null) {
            throw new BusinessException("产品不能为空");
        }
        if (request.getQuantity() == null || request.getQuantity().signum() <= 0) {
            throw new BusinessException("需求数量必须大于0");
        }
        LocalDate demandDate = request.getDemandDate() == null ? LocalDate.now() : request.getDemandDate();

        ManufacturingSupplySnapshotMapper.ProductRow product = mapper.selectProduct(request.getProductId());
        if (product == null) {
            throw new BusinessException("产品[" + request.getProductId() + "]不存在，无法试算");
        }
        if (isBlank(product.getUnit())) {
            throw new BusinessException("产品[" + product.getProductCode() + "]单位为空，无法试算");
        }

        RequirementSummaryVO summary = new RequirementSummaryVO();
        summary.setProductId(product.getProductId());
        summary.setProductCode(product.getProductCode());
        summary.setProductName(product.getProductName());
        summary.setProductUnit(product.getUnit());
        summary.setRequestedQuantity(request.getQuantity());
        summary.setDemandDate(demandDate);
        summary.setWarehouseId(request.getWarehouseId());
        summary.setGeneratedAt(LocalDateTime.now());
        summary.setNote("只读试算：不扣库存、不预留、不写流水、不建工单；confirm 持久确认尚未实现。");
        summary.getLimitations().addAll(baseLimitations());

        String productPath = "产品[" + product.getProductCode() + "]";
        ManufacturingDemandSnapshot root = resolveRootBom(request, product, demandDate, summary);
        summary.setBomId(root.getBomId());
        summary.setBomCode(root.getBomCode());

        Graph graph = new Graph();
        Deque<Long> visiting = new ArrayDeque<>();
        visitBom(root, demandDate, productPath + " -> BOM[" + bomLabel(root) + "]",
                graph, visiting, summary);

        Map<Long, MaterialState> states = new LinkedHashMap<>();
        Map<Long, Integer> pendingParents = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> entry : graph.parentBomsByMaterial.entrySet()) {
            pendingParents.put(entry.getKey(), entry.getValue().size());
        }

        List<RequirementNodeVO> nodes = new ArrayList<>();
        Map<Long, Integer> nodeLevels = new HashMap<>();
        long[] sequence = {0L};
        ExactQuantity requested = ExactQuantity.fromDecimal(request.getQuantity());
        String rootSourcePath = productPath + " 需求 " + requested.exactText()
                + " -> BOM[" + bomLabel(root) + "]";
        RequirementNodeVO rootNode = createNode(1L, List.of(), 0, root, "PRODUCT",
                product.getProductId(), product.getProductCode(), product.getProductName(), product.getUnit(),
                requested, "ROOT", List.of(rootSourcePath));
        nodes.add(rootNode);
        nodeLevels.put(rootNode.getNodeId(), 0);

        expandBom(root, requested, rootNode, List.of(rootSourcePath), sequence, states, graph, demandDate);
        markBomProcessed(root.getBomId(), graph, pendingParents, states, sequence);

        PriorityQueue<ReadyMaterial> ready = new PriorityQueue<>(Comparator
                .comparingLong(ReadyMaterial::firstSequence).thenComparingLong(ReadyMaterial::materialId));
        enqueueZeroParentMaterials(pendingParents, states, ready);

        Map<Long, RequirementLineVO> lines = new LinkedHashMap<>();
        Map<Long, Long> lineFirstSequence = new HashMap<>();
        Set<Long> processedBoms = new LinkedHashSet<>();
        processedBoms.add(root.getBomId());

        while (!ready.isEmpty()) {
            ReadyMaterial readyMaterial = ready.poll();
            Long materialId = readyMaterial.materialId();
            MaterialState state = states.computeIfAbsent(materialId, key -> new MaterialState());
            if (state.netted) {
                continue;
            }
            state.netted = true;

            RequirementLineVO line = netMaterial(materialId, state, request.getWarehouseId(), demandDate, summary);
            if (line != null) {
                lines.put(materialId, line);
                lineFirstSequence.put(materialId,
                        state.contributions.stream().mapToLong(c -> c.sequence).min().orElse(Long.MAX_VALUE));
            }

            Set<Long> producers = graph.producersByMaterial.getOrDefault(materialId, Set.of());
            List<Long> sortedProducers = new ArrayList<>(producers);
            sortedProducers.sort(Comparator.naturalOrder());
            for (Long producerBomId : sortedProducers) {
                if (!processedBoms.add(producerBomId)) {
                    continue;
                }
                ManufacturingDemandSnapshot producer = graph.boms.get(producerBomId);
                List<Contribution> gapSources = state.contributions.stream()
                        .filter(c -> Objects.equals(producerBomId, c.subBomId) && c.gap.isPositive())
                        .toList();
                ExactQuantity expandQty = gapSources.stream()
                        .map(c -> c.gap)
                        .reduce(ExactQuantity.ZERO, ExactQuantity::add);
                if (expandQty.isPositive()) {
                    List<Long> parentNodeIds = gapSources.stream()
                            .map(c -> c.parentNodeId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    int level = parentNodeIds.stream()
                            .mapToInt(id -> nodeLevels.getOrDefault(id, 0))
                            .max().orElse(0) + 1;
                    List<String> sourcePaths = new ArrayList<>(gapSources.stream()
                            .flatMap(c -> c.paths.stream())
                            .distinct()
                            .toList());
                    RequirementNodeVO node = createNode((long) (nodes.size() + 1), parentNodeIds, level,
                            producer, "MATERIAL", producer.getOutputId(),
                            state.materialCode, state.materialName, state.stockUnit,
                            expandQty, "MAKE_GAP", sourcePaths);
                    nodes.add(node);
                    nodeLevels.put(node.getNodeId(), level);
                    expandBom(producer, expandQty, node, sourcePaths, sequence, states, graph, demandDate);
                }
                markBomProcessed(producerBomId, graph, pendingParents, states, sequence);
                enqueueZeroParentMaterials(pendingParents, states, ready);
            }
        }

        Set<Long> unprocessed = new LinkedHashSet<>(graph.boms.keySet());
        unprocessed.removeAll(processedBoms);
        if (!unprocessed.isEmpty()) {
            throw new BusinessException("需求展开未完成，存在未处理的制造BOM：" + unprocessed);
        }

        List<Long> lineOrder = new ArrayList<>(lines.keySet());
        lineOrder.sort(Comparator.comparingLong(id -> lineFirstSequence.getOrDefault(id, Long.MAX_VALUE)));
        for (Long materialId : lineOrder) {
            summary.getLines().add(lines.get(materialId));
        }
        summary.setNodes(nodes);
        return summary;
    }

    // ==================== BOM 解析与静态图 ====================

    private ManufacturingDemandSnapshot resolveRootBom(RequirementPreviewRequest request,
                                                       ManufacturingSupplySnapshotMapper.ProductRow product,
                                                       LocalDate demandDate,
                                                       RequirementSummaryVO summary) {
        String path = "产品[" + product.getProductCode() + "]";
        ManufacturingDemandSnapshot root;
        if (request.getBomId() != null) {
            root = mapper.selectBom(request.getBomId());
            if (root == null) {
                throw new BusinessException(path + " 指定的BOM[" + request.getBomId() + "]不存在");
            }
        } else if (product.getCurrentBomId() != null) {
            root = mapper.selectBom(product.getCurrentBomId());
            if (root == null) {
                throw new BusinessException(path + " 当前BOM[" + product.getCurrentBomId() + "]不存在");
            }
        } else {
            root = mapper.selectDefaultProductBom(product.getProductId(),
                    ProductEnums.BomStatus.APPROVED.getValue());
            if (root == null) {
                throw new BusinessException(path + " 无适用已批准BOM，不能试算");
            }
            summary.getWarnings().add(path + " 未绑定当前BOM，按最新已批准 PRODUCT BOM["
                    + bomLabel(root) + "] 试算");
        }
        if (!Objects.equals(product.getProductId(), root.getProductId())) {
            throw new BusinessException(path + " 绑定BOM[" + bomLabel(root) + "]的产品归属不匹配");
        }
        if (root.getOutputType() != null && !"PRODUCT".equals(root.getOutputType())) {
            throw new BusinessException(path + " 绑定BOM[" + bomLabel(root) + "]产出身份["
                    + root.getOutputType() + "]不是产品，不能作为产品需求根BOM");
        }
        return root;
    }

    private void visitBom(ManufacturingDemandSnapshot bom, LocalDate demandDate, String path,
                          Graph graph, Deque<Long> visiting, RequirementSummaryVO summary) {
        Long bomId = bom.getBomId();
        if (visiting.contains(bomId)) {
            throw new BusinessException("制造BOM存在循环引用：" + path + " -> BOM#" + bomId);
        }
        if (graph.boms.containsKey(bomId)) {
            return;
        }
        validateBomHeader(bom, demandDate, path, summary);
        graph.boms.put(bomId, bom);
        if ("MATERIAL".equals(bom.getOutputType()) && bom.getOutputId() != null) {
            graph.producersByMaterial.computeIfAbsent(bom.getOutputId(), key -> new LinkedHashSet<>()).add(bomId);
        }

        List<ManufacturingDemandSnapshot.Line> rawLines = mapper.selectBomLines(bomId);
        List<ManufacturingDemandSnapshot.Line> effectiveLines = new ArrayList<>();
        visiting.push(bomId);
        try {
            for (ManufacturingDemandSnapshot.Line line : rawLines) {
                String linePath = path + " -> " + lineLabel(line);
                if (isPendingRemove(line)) {
                    summary.getWarnings().add(linePath + " 标记待移除，本次不计入需求");
                    continue;
                }
                validateLine(line, linePath, summary);
                ManufacturingSupplySnapshotMapper.MaterialRow material = loadMaterial(line.getMaterialId(), linePath, graph);
                UnitConversion conversion = resolveConversion(line, material, linePath);
                graph.conversions.put(line.getItemId(), conversion);

                effectiveLines.add(line);
                graph.lines.computeIfAbsent(bomId, key -> new ArrayList<>()).add(line);
                graph.parentBomsByMaterial.computeIfAbsent(line.getMaterialId(), key -> new LinkedHashSet<>()).add(bomId);
                graph.childMaterialsByBom.computeIfAbsent(bomId, key -> new LinkedHashSet<>()).add(line.getMaterialId());

                if (isMake(line)) {
                    ManufacturingDemandSnapshot sub = mapper.selectBom(line.getSubBomId());
                    if (sub == null) {
                        throw new BusinessException(linePath + " 引用的下层制造方案[" + line.getSubBomId()
                                + "]不存在（悬空引用）");
                    }
                    if (!"MATERIAL".equals(sub.getOutputType())
                            || !Objects.equals(line.getMaterialId(), sub.getOutputId())) {
                        throw new BusinessException(linePath + " 引用的下层BOM[" + bomLabel(sub)
                                + "]产出身份[" + sub.getOutputType() + "#" + sub.getOutputId()
                                + "]与本行物料身份不一致");
                    }
                    visitBom(sub, demandDate, linePath + " -> BOM[" + bomLabel(sub) + "]", graph, visiting, summary);
                }
            }
        } finally {
            visiting.pop();
        }
        graph.lines.putIfAbsent(bomId, effectiveLines);
    }

    private void validateBomHeader(ManufacturingDemandSnapshot bom, LocalDate demandDate, String path,
                                   RequirementSummaryVO summary) {
        if (!ProductEnums.BomStatus.APPROVED.getValue().equals(bom.getApproveStatus())) {
            throw new BusinessException(path + " BOM[" + bomLabel(bom) + "]未处于已批准状态，不能用于需求计算");
        }
        if (bom.getEffectiveDate() != null && bom.getEffectiveDate().isAfter(demandDate)) {
            throw new BusinessException(path + " BOM[" + bomLabel(bom) + "]生效日期["
                    + bom.getEffectiveDate() + "]晚于需求日期[" + demandDate + "]");
        }
        if (bom.getExpiryDate() != null && bom.getExpiryDate().isBefore(demandDate)) {
            throw new BusinessException(path + " BOM[" + bomLabel(bom) + "]已过失效日期["
                    + bom.getExpiryDate() + "]，不能用于需求计算");
        }
        if (bom.getEffectiveDate() == null) {
            summary.getWarnings().add(path + " BOM[" + bomLabel(bom) + "]未设置生效日期，按历史兼容纳入（需核查版本）");
        }
    }

    private void validateLine(ManufacturingDemandSnapshot.Line line, String path, RequirementSummaryVO summary) {
        if (line.getMaterialId() == null) {
            throw new BusinessException(path + " 物料为空，不能计算需求");
        }
        if (line.getModuleQty() == null || line.getModuleQty().signum() <= 0) {
            throw new BusinessException(path + " 模数必须大于0");
        }
        if (line.getBaseQty() == null || line.getBaseQty().signum() <= 0) {
            throw new BusinessException(path + " 基数必须大于0");
        }
        if (line.getLossRate() != null && line.getLossRate().signum() < 0) {
            throw new BusinessException(path + " 损耗率不能为负数");
        }
        if (line.getReviewStatus() == null) {
            summary.getWarnings().add(path + " 行审核状态为空，按历史兼容纳入（需核查审批）");
        } else if (!ProductEnums.BomItemReviewStatus.APPROVED.getValue().equals(line.getReviewStatus())) {
            throw new BusinessException(path + " 行审核状态[" + line.getReviewStatus() + "]不是审核通过，不能用于需求计算");
        }
        String sourceType = trimToNull(line.getSourceType());
        if (sourceType != null && !"buy".equalsIgnoreCase(sourceType) && !"make".equalsIgnoreCase(sourceType)) {
            throw new BusinessException(path + " 来源类型[" + sourceType + "]未登记，不能推断制造层级");
        }
        if ("make".equalsIgnoreCase(sourceType) && line.getSubBomId() == null) {
            throw new BusinessException(path + " 自制行缺少 sub_bom_id（MAKE_WITHOUT_SCHEME），不能静默按采购处理");
        }
        if (!"make".equalsIgnoreCase(sourceType) && line.getSubBomId() != null) {
            throw new BusinessException(path + " 非自制行不应带 sub_bom_id[" + line.getSubBomId() + "]");
        }
        if (sourceType == null) {
            summary.getWarnings().add(path + " 来源类型为空，按直接耗用兼容处理，不推断制造层级");
        }
        if (isBlank(line.getUnit())) {
            throw new BusinessException(path + " 单位为空，无法换算");
        }
    }

    private ManufacturingSupplySnapshotMapper.MaterialRow loadMaterial(Long materialId, String path, Graph graph) {
        ManufacturingSupplySnapshotMapper.MaterialRow cached = graph.materials.get(materialId);
        if (cached != null) {
            return cached;
        }
        ManufacturingSupplySnapshotMapper.MaterialRow material = mapper.selectMaterial(materialId);
        if (material == null) {
            throw new BusinessException(path + " 物料[" + materialId + "]在物料档案中不存在");
        }
        if (!StatusEnum.NORMAL.getCode().equals(material.getStatus())) {
            throw new BusinessException(path + " 物料[" + material.getMaterialCode() + "]状态非正常");
        }
        if (isBlank(material.getUnit())) {
            throw new BusinessException(path + " 物料[" + material.getMaterialCode() + "]库存档案单位为空");
        }
        requireUnitPolicy(material.getUnit(), path);
        graph.materials.put(materialId, material);
        return material;
    }

    private UnitConversion resolveConversion(ManufacturingDemandSnapshot.Line line,
                                             ManufacturingSupplySnapshotMapper.MaterialRow material,
                                             String path) {
        String lineUnit = normalizeUnit(line.getUnit());
        String stockUnit = normalizeUnit(material.getUnit());
        if (lineUnit.equals(stockUnit)) {
            return new UnitConversion(ExactQuantity.ONE, "同单位", "同一单位");
        }
        if (line.getUnitFactor() != null) {
            if (line.getUnitFactor().signum() <= 0) {
                throw new BusinessException(path + " 换算系数必须大于0");
            }
            if (isBlank(line.getUnitFactorBasis())) {
                throw new BusinessException(path + " 单位[" + line.getUnit() + "→" + material.getUnit()
                        + "]有换算系数但缺少可核实依据，已阻断");
            }
            return new UnitConversion(ExactQuantity.fromDecimal(line.getUnitFactor()),
                    line.getUnitFactorBasis(), line.getUnitFactorSource());
        }
        // 静态尺寸换算只允许“计件 → 面积”，且档案宽、长必须同时存在；不猜测规格文本。
        if (isPieceUnit(lineUnit) && "M2".equals(stockUnit)
                && material.getWidthMm() != null && material.getWidthMm().signum() > 0
                && material.getLengthMm() != null && material.getLengthMm().signum() > 0) {
            ExactQuantity factor = ExactQuantity.fromDecimal(material.getWidthMm())
                    .multiply(ExactQuantity.fromDecimal(material.getLengthMm()))
                    .divide(ExactQuantity.of(1_000_000L));
            return new UnitConversion(factor,
                    "档案尺寸" + material.getWidthMm().stripTrailingZeros().toPlainString() + "×"
                            + material.getLengthMm().stripTrailingZeros().toPlainString() + "mm",
                    "inventory_material.width_mm/length_mm");
        }
        if ("M2".equals(stockUnit) && material.getWidthMm() != null && material.getLengthMm() == null) {
            throw new BusinessException(path + " 卷材只有宽度没有批次实际长度；逐卷余量属013，"
                    + "缺少批次长度时阻断换算，不能假设统一长度");
        }
        throw new BusinessException(path + " 单位[" + line.getUnit() + "→" + material.getUnit()
                + "]缺少已保存换算系数/可核实依据（UNIT_CONVERSION_MISSING）");
    }

    // ==================== 展开与净额 ====================

    private void expandBom(ManufacturingDemandSnapshot bom, ExactQuantity parentQty, RequirementNodeVO node,
                           List<String> sourcePaths, long[] sequence, Map<Long, MaterialState> states,
                           Graph graph, LocalDate demandDate) {
        List<ManufacturingDemandSnapshot.Line> lines = graph.lines.getOrDefault(bom.getBomId(), List.of());
        for (ManufacturingDemandSnapshot.Line line : lines) {
            ExactQuantity theoretical = parentQty
                    .multiply(ExactQuantity.fromDecimal(line.getBaseQty()))
                    .divide(ExactQuantity.fromDecimal(line.getModuleQty()));
            ExactQuantity lossApplied = theoretical.multiply(lossFactor(line.getLossRate()));
            UnitConversion conversion = graph.conversions.get(line.getItemId());
            ManufacturingSupplySnapshotMapper.MaterialRow material = graph.materials.get(line.getMaterialId());

            Contribution contribution = new Contribution();
            contribution.sequence = ++sequence[0];
            contribution.materialId = line.getMaterialId();
            contribution.materialCode = material.getMaterialCode();
            contribution.materialName = material.getMaterialName();
            contribution.stockUnit = material.getUnit();
            contribution.theoreticalBom = theoretical;
            contribution.appliedBom = lossApplied;
            contribution.appliedStock = lossApplied.multiply(conversion.factor());
            contribution.bomUnit = line.getUnit();
            contribution.sourceBomId = bom.getBomId();
            contribution.sourceBomCode = bom.getBomCode();
            contribution.sourceItemId = line.getItemId();
            contribution.sourceItemOrder = line.getItemOrder();
            contribution.parentNodeId = node.getNodeId();
            contribution.subBomId = line.getSubBomId();
            contribution.sourceType = line.getSourceType();
            contribution.make = isMake(line);
            contribution.demandDate = demandDate;
            contribution.unitFactorExact = conversion.factor().exactText();
            contribution.unitFactorBasis = conversion.basis();
            contribution.unitFactorSource = conversion.source();
            ExactQuantity minIssue = line.getMinIssueQty() == null
                    ? ExactQuantity.ZERO : ExactQuantity.fromDecimal(line.getMinIssueQty());
            contribution.minIssueStock = minIssue.multiply(conversion.factor());
            contribution.paths = new ArrayList<>();
            for (String sourcePath : sourcePaths) {
                contribution.paths.add(sourcePath + " -> " + lineLabel(line));
            }
            states.computeIfAbsent(line.getMaterialId(), key -> new MaterialState())
                    .contributions.add(contribution);
        }
    }

    private RequirementLineVO netMaterial(Long materialId, MaterialState state, Long warehouseId,
                                          LocalDate demandDate, RequirementSummaryVO summary) {
        if (state.contributions.isEmpty()) {
            return null;
        }
        List<Contribution> contributions = new ArrayList<>(state.contributions);
        contributions.sort(Comparator.comparing((Contribution c) -> c.demandDate)
                .thenComparingLong(c -> c.sequence));

        SupplySnapshot supply = supplyProvider.loadMaterialSupply(materialId, warehouseId, demandDate);
        state.stockUnit = supply.getUnit();
        state.materialCode = supply.getMaterialCode();
        state.materialName = supply.getMaterialName();

        ExactQuantity remainingStock = supply.getAvailableForDemand();
        ExactQuantity remainingProduction = supply.getProductionSupply();
        ExactQuantity remainingPurchase = supply.getPurchaseSupply();
        for (Contribution contribution : contributions) {
            if (!sameUnit(contribution.stockUnit, supply.getUnit())) {
                throw new BusinessException("物料[" + supply.getMaterialCode() + "]来源单位["
                        + contribution.stockUnit + "]与库存单位[" + supply.getUnit() + "]不一致");
            }
            contribution.stockAllocated = take(contribution.appliedStock, remainingStock);
            remainingStock = remainingStock.subtract(contribution.stockAllocated);
            contribution.productionAllocated = take(contribution.appliedStock.subtract(contribution.stockAllocated),
                    remainingProduction);
            remainingProduction = remainingProduction.subtract(contribution.productionAllocated);
            contribution.purchaseAllocated = take(contribution.appliedStock
                            .subtract(contribution.stockAllocated)
                            .subtract(contribution.productionAllocated),
                    remainingPurchase);
            remainingPurchase = remainingPurchase.subtract(contribution.purchaseAllocated);

            ExactQuantity gap = contribution.appliedStock
                    .subtract(contribution.stockAllocated)
                    .subtract(contribution.productionAllocated)
                    .subtract(contribution.purchaseAllocated);
            contribution.gap = gap.isNegative() ? ExactQuantity.ZERO : gap;
        }
        return buildLine(materialId, state, contributions, supply);
    }

    private RequirementLineVO buildLine(Long materialId, MaterialState state, List<Contribution> contributions,
                                        SupplySnapshot supply) {
        RequirementLineVO line = new RequirementLineVO();
        line.setMaterialId(materialId);
        line.setMaterialCode(supply.getMaterialCode());
        line.setMaterialName(supply.getMaterialName());
        line.setStockUnit(supply.getUnit());
        line.setDemandDate(contributions.get(0).demandDate);
        ExactQuantity minIssueExact = contributions.stream().map(c -> c.minIssueStock)
                .max(Comparator.naturalOrder()).orElse(ExactQuantity.ZERO);
        line.setMinIssueQty(bd(minIssueExact));
        line.setStockOnHand(bd(supply.getOnHand()));
        line.setStockReserved(bd(supply.getReserved()));
        line.setSafeStock(bd(supply.getSafeStock()));
        line.setAvailableForDemand(bd(supply.getAvailableForDemand()));
        line.setSafeStockDeducted(supply.isSafeStockDeducted());
        line.setProductionSupply(bd(supply.getProductionSupply()));
        line.setPurchaseSupply(bd(supply.getPurchaseSupply()));
        line.setMaterialProductionAdapterApplied(Boolean.TRUE);
        line.getWarnings().addAll(supply.getWarnings());

        ExactQuantity theoretical = sum(contributions, c -> c.theoreticalBom);
        ExactQuantity appliedStock = sum(contributions, c -> c.appliedStock);
        ExactQuantity allocatedStock = sum(contributions, c -> c.stockAllocated);
        ExactQuantity allocatedProduction = sum(contributions, c -> c.productionAllocated);
        ExactQuantity allocatedPurchase = sum(contributions, c -> c.purchaseAllocated);
        ExactQuantity gap = sum(contributions, c -> c.gap);

        Set<String> bomUnits = new LinkedHashSet<>();
        Set<Long> subBomIds = new LinkedHashSet<>();
        Set<String> unitFactors = new LinkedHashSet<>();
        Set<String> bases = new LinkedHashSet<>();
        Set<String> factorSources = new LinkedHashSet<>();
        boolean hasMake = false;
        for (Contribution c : contributions) {
            bomUnits.add(normalizeUnit(c.bomUnit));
            if (c.make) {
                hasMake = true;
                if (c.subBomId != null) {
                    subBomIds.add(c.subBomId);
                }
            }
            unitFactors.add(c.unitFactorExact);
            bases.add(Objects.toString(c.unitFactorBasis, ""));
            factorSources.add(Objects.toString(c.unitFactorSource, ""));
        }
        line.setMake(hasMake);
        line.setBomUnit(bomUnits.size() == 1 ? contributions.get(0).bomUnit : null);
        if (hasMake) {
            line.setSubBomId(subBomIds.size() == 1 ? subBomIds.iterator().next() : null);
            if (subBomIds.size() > 1) {
                line.getWarnings().add("同物料存在多个制造方案[" + subBomIds + "]，剩余缺口按各自 sub_bom_id 分别展开");
            }
        }
        line.setUnitFactorExact(unitFactors.size() == 1 ? unitFactors.iterator().next() : null);
        line.setUnitFactorBasis(bases.size() == 1 ? bases.iterator().next() : null);
        line.setUnitFactorSource(factorSources.size() == 1 ? factorSources.iterator().next() : null);

        line.setTheoreticalQtyExact(bomUnits.size() == 1 ? theoretical.exactText() : null);
        line.setTheoreticalQtyApprox(bomUnits.size() == 1 ? theoretical.approximate() : null);
        line.setLossAppliedQtyExact(appliedStock.exactText());
        line.setLossAppliedQtyApprox(appliedStock.approximate());
        line.setAggregateQtyDisplay(displayQty(appliedStock, supply.getUnit()));
        line.setAggregateRounding(roundingName(supply.getUnit()));

        line.setAllocatedStock(bd(allocatedStock));
        line.setAllocatedProduction(bd(allocatedProduction));
        line.setAllocatedPurchase(bd(allocatedPurchase));
        line.setGapQtyExact(gap.exactText());
        line.setGapQtyApprox(gap.approximate());
        line.setGapQtyDisplay(displayQty(gap, supply.getUnit()));

        ExactQuantity issueFloor = appliedStock.compareTo(minIssueExact) >= 0 ? appliedStock : minIssueExact;
        line.setIssueFloorQty(bd(issueFloor));
        // 生产批量/实领粒度没有可信配置，保持为空，不拿 min_order_qty 或 min_issue_qty 冒充。
        line.setProductionBatchQty(null);
        line.setIssueQty(null);
        if (hasMake) {
            line.getLimitations().add("未配置生产批量，下层按精确缺口展开；min_order_qty 未作为生产批量");
        }
        line.getLimitations().add("整张/整卷等实领取整在领料层处理，本试算不在逐层CEILING");

        for (Contribution c : contributions) {
            RequirementLineVO.SourceRef source = new RequirementLineVO.SourceRef();
            source.setSequence(c.sequence);
            source.setSourceBomId(c.sourceBomId);
            source.setSourceBomCode(c.sourceBomCode);
            source.setSourceItemId(c.sourceItemId);
            source.setSourceItemOrder(c.sourceItemOrder);
            source.setParentNodeId(c.parentNodeId);
            source.setSourceType(c.sourceType);
            source.setSubBomId(c.subBomId);
            source.setBomUnit(c.bomUnit);
            source.setPath(c.paths.isEmpty() ? null : c.paths.get(0));
            source.setDemandDate(c.demandDate);
            source.setTheoreticalQtyExact(c.theoreticalBom.exactText());
            source.setAppliedQtyExact(c.appliedBom.exactText());
            source.setAppliedQtyInStockUnitExact(c.appliedStock.exactText());
            source.setAllocatedStockExact(c.stockAllocated.exactText());
            source.setAllocatedProductionExact(c.productionAllocated.exactText());
            source.setAllocatedPurchaseExact(c.purchaseAllocated.exactText());
            source.setGapQtyExact(c.gap.exactText());
            line.getSources().add(source);
        }
        return line;
    }

    // ==================== 依赖推进 ====================

    private void markBomProcessed(Long bomId, Graph graph, Map<Long, Integer> pendingParents,
                                  Map<Long, MaterialState> states, long[] sequence) {
        for (Long childMaterial : graph.childMaterialsByBom.getOrDefault(bomId, Set.of())) {
            Integer pending = pendingParents.get(childMaterial);
            if (pending == null) {
                continue;
            }
            pendingParents.put(childMaterial, pending - 1);
        }
    }

    private void enqueueZeroParentMaterials(Map<Long, Integer> pendingParents, Map<Long, MaterialState> states,
                                            PriorityQueue<ReadyMaterial> ready) {
        for (Map.Entry<Long, Integer> entry : pendingParents.entrySet()) {
            if (entry.getValue() != 0) {
                continue;
            }
            MaterialState state = states.computeIfAbsent(entry.getKey(), key -> new MaterialState());
            if (state.queued || state.netted) {
                continue;
            }
            state.queued = true;
            long firstSequence = state.contributions.stream()
                    .mapToLong(c -> c.sequence)
                    .min().orElse(Long.MAX_VALUE);
            ready.add(new ReadyMaterial(firstSequence, entry.getKey()));
        }
    }

    // ==================== 工具 ====================

    private RequirementNodeVO createNode(Long nodeId, List<Long> parentNodeIds, int level,
                                         ManufacturingDemandSnapshot bom, String outputType, Long outputId,
                                         String outputCode, String outputName, String outputUnit,
                                         ExactQuantity qty, String reason, List<String> sourcePaths) {
        RequirementNodeVO node = new RequirementNodeVO();
        node.setNodeId(nodeId);
        node.setParentNodeIds(new ArrayList<>(new LinkedHashSet<>(parentNodeIds)));
        node.setLevel(level);
        node.setBomId(bom.getBomId());
        node.setBomCode(bom.getBomCode());
        node.setBomName(bom.getBomName());
        node.setOutputType(outputType);
        node.setOutputId(outputId);
        node.setOutputCode(outputCode);
        node.setOutputName(outputName);
        node.setOutputUnit(outputUnit);
        node.setExpandQtyExact(qty.exactText());
        node.setExpandQtyApprox(qty.approximate());
        node.setExpandReason(reason);
        node.setSourcePaths(new ArrayList<>(sourcePaths));
        return node;
    }

    private ExactQuantity lossFactor(BigDecimal lossRatePercent) {
        if (lossRatePercent == null || lossRatePercent.signum() == 0) {
            return ExactQuantity.ONE;
        }
        ExactQuantity percent = ExactQuantity.fromDecimal(lossRatePercent);
        return ExactQuantity.ONE.add(percent.divide(ExactQuantity.of(100L)));
    }

    private ExactQuantity take(ExactQuantity demand, ExactQuantity available) {
        if (demand.isNegative() || demand.isZero() || available.isNegative() || available.isZero()) {
            return ExactQuantity.ZERO;
        }
        return demand.compareTo(available) <= 0 ? demand : available;
    }

    private ExactQuantity sum(List<Contribution> contributions,
                              java.util.function.Function<Contribution, ExactQuantity> getter) {
        ExactQuantity total = ExactQuantity.ZERO;
        for (Contribution contribution : contributions) {
            total = total.add(getter.apply(contribution));
        }
        return total;
    }

    private BigDecimal bd(ExactQuantity quantity) {
        return quantity == null ? null : quantity.approximate();
    }

    private BigDecimal displayQty(ExactQuantity quantity, String unit) {
        if (isPieceUnit(normalizeUnit(unit))) {
            return new BigDecimal(quantity.ceilToInteger());
        }
        return quantity.approximate(4);
    }

    private String roundingName(String unit) {
        return isPieceUnit(normalizeUnit(unit)) ? "CEILING" : "HALF_UP_4";
    }

    private void requireUnitPolicy(String unit, String path) {
        String normalized = normalizeUnit(unit);
        if (!isPieceUnit(normalized) && !CONTINUOUS_UNITS.contains(normalized)) {
            throw new BusinessException(path + " 单位[" + unit + "]未登记取整策略（UNIT_POLICY_MISSING），不能猜测");
        }
    }

    private boolean isPieceUnit(String normalizedUnit) {
        return PIECE_UNITS.contains(normalizedUnit);
    }

    private boolean isMake(ManufacturingDemandSnapshot.Line line) {
        return "make".equalsIgnoreCase(trimToNull(line.getSourceType()));
    }

    private boolean isPendingRemove(ManufacturingDemandSnapshot.Line line) {
        return ProductEnums.BomItemReviewStatus.PENDING_REMOVE.getValue().equals(line.getReviewStatus());
    }

    private String lineLabel(ManufacturingDemandSnapshot.Line line) {
        String code = isBlank(line.getMaterialCode()) ? "ID:" + line.getMaterialId() : line.getMaterialCode();
        return "行[" + (line.getItemOrder() == null ? "-" : line.getItemOrder()) + " 物料[" + code + "]]";
    }

    private String bomLabel(ManufacturingDemandSnapshot bom) {
        return (isBlank(bom.getBomCode()) ? "ID:" + bom.getBomId() : bom.getBomCode()) + "#" + bom.getBomId();
    }

    private String normalizeUnit(String unit) {
        if (unit == null) {
            return "";
        }
        String normalized = unit.trim().toUpperCase();
        return switch (normalized) {
            case "㎡", "平方米", "平方" -> "M2";
            case "米" -> "M";
            case "公斤" -> "KG";
            default -> normalized;
        };
    }

    private boolean sameUnit(String left, String right) {
        return normalizeUnit(left).equals(normalizeUnit(right));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> baseLimitations() {
        List<String> limitations = new ArrayList<>();
        limitations.add("只读试算：不扣库存、不预留、不写流水、不建工单；重复调用不产生写入。");
        limitations.add("current production_order 无 MATERIAL output 列；物料生产供给按工单绑定BOM的 "
                + "output_type=MATERIAL/output_id 兼容读取，未绑定产出身份的工单不计，不把 product_id 当物料身份。");
        limitations.add("生产供给只计入未绑定 sales_order_id/parent_order_id 的工单；已承担其他需求的工单不重复抵扣。");
        limitations.add("顶层产品需求不抵扣既有产品工单产出：当前无需求分配关联，直接抵扣会把其他订单产出误算给本次。");
        limitations.add("持久确认/并发分配（请求头幂等、唯一键、事务锁、失效供给重算）未获新表批准，本接口不提供 confirm 成功响应。");
        limitations.add("卷材逐卷实际长度与余量属 013；缺少批次长度时阻断换算，未用统一长度代替。");
        limitations.add("安全库存按既有 InventoryAlertServiceImpl 材料级口径扣除；仓库维度未分摊安全库存。");
        limitations.add("生产批量与实领粒度未在本卡配置；min_issue_qty 只作为最低投料字段，未当作生产批量。");
        return limitations;
    }

    // ==================== 内部模型 ====================

    private static final class Graph {
        private final Map<Long, ManufacturingDemandSnapshot> boms = new LinkedHashMap<>();
        private final Map<Long, List<ManufacturingDemandSnapshot.Line>> lines = new LinkedHashMap<>();
        private final Map<Long, UnitConversion> conversions = new HashMap<>();
        private final Map<Long, ManufacturingSupplySnapshotMapper.MaterialRow> materials = new HashMap<>();
        private final Map<Long, Set<Long>> parentBomsByMaterial = new LinkedHashMap<>();
        private final Map<Long, Set<Long>> producersByMaterial = new LinkedHashMap<>();
        private final Map<Long, Set<Long>> childMaterialsByBom = new LinkedHashMap<>();
    }

    private record UnitConversion(ExactQuantity factor, String basis, String source) {
    }

    private record ReadyMaterial(long firstSequence, Long materialId) {
    }

    private static final class MaterialState {
        private final List<Contribution> contributions = new ArrayList<>();
        private String materialCode;
        private String materialName;
        private String stockUnit;
        private boolean netted;
        private boolean queued;
    }

    private static final class Contribution {
        private long sequence;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String stockUnit;
        private ExactQuantity theoreticalBom;
        private ExactQuantity appliedBom;
        private ExactQuantity appliedStock;
        private String bomUnit;
        private Long sourceBomId;
        private String sourceBomCode;
        private Long sourceItemId;
        private Integer sourceItemOrder;
        private Long parentNodeId;
        private Long subBomId;
        private String sourceType;
        private boolean make;
        private LocalDate demandDate;
        private ExactQuantity minIssueStock = ExactQuantity.ZERO;
        private List<String> paths = new ArrayList<>();
        private String unitFactorExact;
        private String unitFactorBasis;
        private String unitFactorSource;
        private ExactQuantity stockAllocated = ExactQuantity.ZERO;
        private ExactQuantity productionAllocated = ExactQuantity.ZERO;
        private ExactQuantity purchaseAllocated = ExactQuantity.ZERO;
        private ExactQuantity gap = ExactQuantity.ZERO;
    }
}
