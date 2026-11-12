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
import com.jjx.product.domain.dto.ManufacturingSchemeChoiceDTO;
import com.jjx.product.domain.dto.BomItemReviewDTO;
import com.jjx.product.domain.dto.UpdateBomStatusDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.domain.query.EngineeringBomQuery;
import com.jjx.product.domain.vo.EngineeringBomVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.enums.ManufacturingOutputType;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.system.service.ReviewFlowService;
import lombok.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * BOM Service实现
 */
@Service
public class EngineeringBomServiceImpl extends ServiceImpl<EngineeringBomMapper,EngineeringBom> implements IEngineeringBomService {

    /** 产品主方案 JSON 编解码；只用于 product.material_scheme_json，不参与 BOM 行指纹。 */
    private static final ObjectMapper SCHEME_JSON = new ObjectMapper();
    private static final Set<String> SOURCE_TYPES = Set.of("buy", "make");

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @org.springframework.beans.factory.annotation.Autowired
    private BomSpecificationService bomSpecificationService;

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

    /**
     * 编辑占用会话（dev-20261111-018）。Spring 上下文必注入（字段注入默认 required，缺失即启动失败），
     * 因此生产 HTTP 保存一律走严格校验；只有直接构造本类的隔离测试不装配，走兼容分支。
     */
    @org.springframework.beans.factory.annotation.Autowired
    private com.jjx.product.service.BomEditSessionService bomEditSessionService;

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

    // ==================== 内容指纹与编辑会话校验（dev-20261111-018）====================

    /** 编辑会话校验是否启用：装配了占用服务即启用；Spring 必注入，缺失即启动失败。 */
    private boolean editContractActive() {
        return bomEditSessionService != null;
    }

    /**
     * 服务端内容指纹（SHA-256 十六进制）。
     *
     * <p>规范：父表 + 全部明细按 itemId 升序；每字段「长度前缀 + UTF-8」，null 与空串区分；
     * 排除审计字段（createTime/updateTime/createBy/updateBy/approvedBy/approvedTime/removeConfirmed*）。
     * 客户端不得计算或提供权威版本号。
     */
    public static String computeContentRevision(EngineeringBom bom, List<EngineeringBomItem> items) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("BOM|");
        if (bom != null) {
            putHashed(sb, bom.getBomId());
            putHashed(sb, bom.getBomCode());
            putHashed(sb, bom.getBomName());
            putHashed(sb, bom.getBomVersion());
            putHashed(sb, bom.getVersion());
            putHashed(sb, bom.getBomType());
            putHashed(sb, bom.getOutputType());
            putHashed(sb, bom.getOutputId());
            putHashed(sb, bom.getProductId());
            putHashed(sb, bom.getEffectiveDate() == null ? null : bom.getEffectiveDate().getTime());
            putHashed(sb, bom.getExpiryDate() == null ? null : bom.getExpiryDate().getTime());
            putHashed(sb, bom.getRemark());
            putHashed(sb, bom.getApproveStatus());
        }
        sb.append("ITEMS|");
        if (items != null) {
            List<EngineeringBomItem> ordered = new java.util.ArrayList<>(items);
            ordered.sort(java.util.Comparator.comparing(EngineeringBomItem::getItemId,
                    java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
            for (EngineeringBomItem it : ordered) {
                if (it == null) continue;
                sb.append("ROW|");
                putHashed(sb, it.getItemId());
                putHashed(sb, it.getParentMaterialId());
                putHashed(sb, it.getMaterialId());
                putHashed(sb, it.getSubBomId());
                putHashed(sb, it.getMaterialCode());
                putHashed(sb, it.getMaterialName());
                putHashed(sb, it.getSpecification());
                putHashed(sb, it.getSplit());
                putHashed(sb, it.getPitchMm());
                putHashed(sb, it.getPcsPerPitch());
                putHashed(sb, it.getProcessId());
                putHashed(sb, it.getProcessName());
                putHashed(sb, it.getUnit());
                putHashed(sb, it.getQuantity());
                putHashed(sb, it.getLossRate());
                putHashed(sb, it.getModuleQty());
                putHashed(sb, it.getBaseQty());
                putHashed(sb, it.getMinIssueQty());
                putHashed(sb, it.getWidthMm());
                putHashed(sb, it.getLengthMm());
                putHashed(sb, it.getUnitFactor());
                putHashed(sb, it.getUnitFactorBasis());
                putHashed(sb, it.getUnitFactorSource());
                putHashed(sb, it.getItemOrder());
                putHashed(sb, it.getReviewStatus());
                putHashed(sb, it.getRemoveConfirmed());
                putHashed(sb, it.getApprovedFingerprint());
            }
        }
        return sha256Hex(sb.toString());
    }

    /** 长度前缀编码，区分 null（-1）与空串（0）。 */
    private static void putHashed(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("-1|");
            return;
        }
        String text = value instanceof BigDecimal dec
                ? dec.stripTrailingZeros().toPlainString()
                : String.valueOf(value);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        sb.append(bytes.length).append('|').append(text).append('|');
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new BusinessException("BOM内容指纹计算失败: " + ex.getMessage());
        }
    }

    /** 已锁父行后读取明细当前值（FOR UPDATE），供内容指纹与行合并共用。 */
    private List<EngineeringBomItem> lockedItems(Long bomId) {
        return bomId == null ? List.of() : productBomItemMapper.selectByBomIdForUpdate(bomId);
    }

    /** 父行锁（先锁父行、再锁明细）；替身 Mapper 未实现行锁时退回普通读。 */
    private EngineeringBom lockBomForWrite(Long bomId) {
        EngineeringBom bom = bomId == null ? null : productBomMapper.selectForUpdate(bomId);
        if (bom == null && bomId != null) {
            bom = productBomMapper.selectById(bomId);
        }
        return bom;
    }

    /**
     * 内容版本校验：只允许「与库中重算值一致」的保存/审批继续，缺失或不一致一律 409。
     * 校验发生在父行锁之后、任何写之前，事务不写任何行。
     */
    private void assertContentRevision(EngineeringBom bom, List<EngineeringBomItem> lockedItems,
                                       String expected, String message) {
        if (!editContractActive()) {
            return;
        }
        String current = computeContentRevision(bom, lockedItems);
        if (expected == null || expected.isBlank()) {
            throw new BusinessException(409,
                    "BOM_CONTENT_REVISION_REQUIRED：请求缺少BOM内容版本，请重新加载后再提交");
        }
        if (!current.equals(expected.trim())) {
            throw new BusinessException(409, message);
        }
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

    /** 先在 SQL 中选定每个编码的代表版本，再由数据库分页，绝不合并已分页的版本。 */
    @Override
    public PageResult<BomListRow> getBomListPage(BomListQuery query) {
        LambdaQueryWrapper<EngineeringBom> wrapper = buildQueryWrapper(query);
        wrapper.like(StringUtils.isNotBlank(query.getBomName()), EngineeringBom::getBomName, query.getBomName());
        wrapper.eq(StringUtils.isNotBlank(query.getBomType()), EngineeringBom::getBomType, query.getBomType());
        wrapper.apply(StringUtils.isNotBlank(query.getProductCode()),
                "product_id IN (SELECT product_id FROM product WHERE product_code LIKE CONCAT('%', {0}, '%'))", query.getProductCode());
        wrapper.apply(StringUtils.isNotBlank(query.getProductName()),
                "product_id IN (SELECT product_id FROM product WHERE product_name LIKE CONCAT('%', {0}, '%'))", query.getProductName());
        if (!query.isShowAllVersions()) {
            // 当前优先；无当前时按创建时间/id 取最新。修改历史版不会把它变成最新版本。
            wrapper.apply("NOT EXISTS (SELECT 1 FROM engineering_bom newer WHERE newer.bom_code = engineering_bom.bom_code "
                    + "AND (COALESCE(newer.is_current, 0) > COALESCE(engineering_bom.is_current, 0) "
                    + "OR (COALESCE(newer.is_current, 0) = COALESCE(engineering_bom.is_current, 0) "
                    + "AND (COALESCE(newer.create_time, '1000-01-01') > COALESCE(engineering_bom.create_time, '1000-01-01') "
                    + "OR (COALESCE(newer.create_time, '1000-01-01') = COALESCE(engineering_bom.create_time, '1000-01-01') "
                    + "AND newer.bom_id > engineering_bom.bom_id)))))");
        }
        wrapper.orderByAsc(EngineeringBom::getBomCode).orderByDesc(EngineeringBom::getCreateTime, EngineeringBom::getBomId);
        IPage<EngineeringBom> page = productBomMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<EngineeringBom> records = page.getRecords();
        Map<String, Long> counts = new HashMap<>();
        Map<Long, Product> products = new HashMap<>();
        if (!records.isEmpty()) {
            productBomMapper.selectList(new LambdaQueryWrapper<EngineeringBom>()
                    .select(EngineeringBom::getBomCode)
                    .in(EngineeringBom::getBomCode, records.stream().map(EngineeringBom::getBomCode).distinct().toList()))
                    .forEach(bom -> counts.merge(bom.getBomCode(), 1L, Long::sum));
            List<Long> productIds = records.stream().map(EngineeringBom::getProductId).filter(Objects::nonNull).distinct().toList();
            if (!productIds.isEmpty()) {
                productMapper.selectBatchIds(productIds).forEach(product -> products.put(product.getProductId(), product));
            }
        }
        List<BomListRow> rows = records.stream().map(bom -> {
            BomListRow row = new BomListRow();
            cn.hutool.core.bean.BeanUtil.copyProperties(bomConverter.toVO(bom), row);
            row.setBomType(bom.getBomType());
            row.setVersionCount(counts.getOrDefault(bom.getBomCode(), 0L));
            Product product = products.get(bom.getProductId());
            if (product != null) {
                row.setProductCode(product.getProductCode());
                row.setProductName(product.getProductName());
            }
            return row;
        }).toList();
        return PageResult.of(page, rows);
    }

    @Override
    public List<EngineeringBomVO> getBomVersions(String bomCode) {
        if (StringUtils.isBlank(bomCode)) throw new BusinessException("BOM编码不能为空");
        return bomConverter.toVOList(productBomMapper.selectList(new LambdaQueryWrapper<EngineeringBom>()
                .eq(EngineeringBom::getBomCode, bomCode)
                .orderByDesc(EngineeringBom::getCreateTime, EngineeringBom::getBomId)));
    }

    @Override
    @Transactional(readOnly = true)
    public EngineeringBomVO getBomDetail(Long bomId) {
        EngineeringBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            return null;
        }

        EngineeringBomVO vo = bomConverter.toVO(bom);

        // 获取BOM明细
        List<EngineeringBomItem> items = getBomItems(bomId);
        vo.setItems(items);
        // 父表与明细同一只读快照，内容指纹才与返回的明细一致（dev-20261111-018）。
        vo.setContentRevision(computeContentRevision(bom, items));

        // 获取产品信息
        if (bom.getProductId() != null) {
            Product product = productMapper.selectById(bom.getProductId());
            if (product != null) {
                vo.setProductCode(product.getProductCode());
                vo.setProductName(product.getProductName());
                vo.setSchemeRevision(product.getSchemeRevision());
                vo.setSchemeChoices(readSchemeChoices(product.getMaterialSchemeJson()));
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
    @Transactional(rollbackFor = Exception.class)
    public Long createBomReturnId(EngineeringBomDTO dto) {
        // 产出身份先归一化：历史调用没有 outputType 时按 PRODUCT/productId 兼容。
        normalizeOutput(dto);
        // 新建方案全部按新行校验（正小数/单位依据/显式下层引用）；草稿可引用草稿方案。
        validateItemRules(null, dto.getOutputType(), dto.getItems(), Map.of(), true);
        EngineeringBom bom = bomConverter.toEntity(dto);
        bom.setApproveStatus(ApproveStatusEnum.DRAFT.getValue());
        // 未批准的 BOM 不能成为当前/生效版本（dev-20261020-029 F）
        bom.setIsCurrent(false);
        bom.setOutputType(dto.getOutputType());
        bom.setOutputId(dto.getOutputId());

        // 生成BOM版本
        generateBomVersion(bom);

        // 保存BOM主表
        if (!saveBom(bom)) {
            return null;
        }

        // 保存BOM明细
        saveBomItems(dto.getItems(), bom.getBomId());

        // 如果是当前版本，更新其他版本为非当前
        if (isCurrentVersion(bom)) {
            setOtherBomNotCurrent(bom.getOutputType(), bom.getOutputId(), bom.getBomId());
        }

        // 父 BOM 保存才持久化产品主方案；采用 token 本身不落库。
        applySchemeChoices(dto, bom);

        return bom.getBomId();
    }

// ==================== 提取的方法 ====================

    /**
     * 生成BOM版本号
     */
    private void generateBomVersion(EngineeringBom bom) {
        if (StringUtils.isNotBlank(bom.getBomVersion())) {
            return;
        }

        // 2026-08-10 DEV-765：按产出身份查所有版本号；MATERIAL 没有 product_id。
        String outputType = ManufacturingOutputType.normalize(bom.getOutputType()).name();
        Long outputId = bom.getOutputId() != null ? bom.getOutputId() : bom.getProductId();
        java.util.List<String> versions = productBomMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EngineeringBom>()
                                .and(w -> w.eq(EngineeringBom::getOutputType, outputType)
                                        .eq(EngineeringBom::getOutputId, outputId)
                                        .or()
                                        .isNull(EngineeringBom::getOutputType)
                                        .eq(EngineeringBom::getProductId, outputId)))
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

        // 新增与修改共用临时行号映射，避免把 -1 等前端编号直接写为主键。
        EngineeringBomDTO payload = new EngineeringBomDTO();
        payload.setBomId(bomId);
        payload.setItems(items);
        mergeBomItems(payload);
    }

    /**
     * 判断是否为当前版本
     */
    private boolean isCurrentVersion(EngineeringBom bom) {
        return bom.getIsCurrent() != null && bom.getIsCurrent();
    }

    private void setOtherBomNotCurrent(String outputType, Long outputId, Long bomId) {
        LambdaUpdateWrapper<EngineeringBom> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(EngineeringBom::getIsCurrent, YesNoEnum.NO.getCode())
                .eq(EngineeringBom::getOutputType, ManufacturingOutputType.normalize(outputType).name())
                .eq(EngineeringBom::getOutputId, outputId)
                .ne(EngineeringBom::getBomId,bomId);
        productBomMapper.update(updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBom(EngineeringBomDTO dto) {
        return updateBomWithDetail(dto).isSuccess();
    }

    /** 本次保存是否会写入显式下层方案引用（sub_bom_id）；纯粹删除引用不会新增环，无需取图锁。 */
    private boolean hasExplicitSubBom(List<EngineeringBomItemDTO> items) {
        if (items == null) return false;
        for (EngineeringBomItemDTO row : items) {
            if (row != null && !Boolean.TRUE.equals(row.getRemoveRequested()) && row.getSubBomId() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 更新BOM（含变更明细，供 @Log detail 展示）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.jjx.product.domain.vo.EngineeringBomEditVO updateBomWithDetail(EngineeringBomDTO dto) {
        if (dto.getBomId() == null) {
            throw new BusinessException("BOM ID不能为空");
        }

        // 显式下层引用变更先取全局图锁：必须是本事务第一条数据库语句，且排在父行锁之前。
        // 数据库行锁跨实例有效，固定 bom_id 顺序避免 A→B/B→A 并发快照读各自通过后成环。
        if (hasExplicitSubBom(dto.getItems())) {
            productBomMapper.lockGraphForMutation();
        }

        // 检查BOM是否存在；父保存锁行，避免并发覆盖明细。
        // 替身 Mapper 未实现行锁时退回普通读；真实库两个查询等价。
        EngineeringBom existingBom = lockBomForWrite(dto.getBomId());
        if (existingBom == null) {
            throw new BusinessException("BOM不存在");
        }

        // 编辑会话校验第一步（dev-20261111-018）：先校验编辑 token（Redis 异常即拒绝，不 fail-open）。
        // 位置在既有图锁 + 父行锁之后、任何写之前；内容指纹比较在读取锁定明细后立即执行。
        if (editContractActive()) {
            bomEditSessionService.verifySaveToken(dto.getBomId(), dto.getEditToken());
        }

        // 产出身份不可原地改变；历史空值按 PRODUCT 兼容。
        normalizeOutputForUpdate(dto, existingBom);

        // 如果BOM已审批，不允许修改
        boolean editable = ProductEnums.BomStatus.fromValue(existingBom.getApproveStatus()).isEditable();
        if (!editable) {
            throw new BusinessException("BOM已审批，不允许修改");
        }

        // 既有未改行保留原保存行为；仅新增/变更行执行新制造规则。
        Map<Long, EngineeringBomItem> storedRows = new HashMap<>();
        // 已锁父行后明细用当前读，避免 MySQL RR 早快照把「他人刚保存」的行当成本次基线。
        List<EngineeringBomItem> storedList = editContractActive()
                ? productBomItemMapper.selectByBomIdForUpdate(dto.getBomId())
                : productBomItemMapper.selectList(
                        new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, dto.getBomId()));
        // 编辑会话校验第二步：与服务端重算的内容指纹比较；不一致 409，事务不写任何行。
        assertContentRevision(existingBom, storedList, dto.getExpectedContentRevision(),
                "BOM_CONTENT_STALE：BOM 已被其他人保存，请重新加载后再提交");
        for (EngineeringBomItem row : storedList) {
            if (row.getItemId() != null) storedRows.put(row.getItemId(), row);
        }
        validateItemRules(dto.getBomId(), dto.getOutputType(), dto.getItems(), storedRows, false);

        // 变更明细：主表 + 明细行对比（保存前采集）
        List<String> changes = new java.util.ArrayList<>();
        List<Map<String, Object>> groupChanges = buildGroupChanges(existingBom.getBomId(), dto);
        List<Map<String, Object>> itemChanges = buildOriginalValueChanges(existingBom.getBomId(), dto);
        try {
            buildBomDiff(changes, existingBom, dto);
        } catch (Exception e) {
            log.warn("BOM变更明细生成失败: " + e.getMessage());
        }

        // DTO转Entity
        EngineeringBom bom = bomConverter.toEntity(dto);

        // 普通保存仅修改内容；审核和生效只能通过专用流程变更，不能信任客户端回传值。
        bom.setApproveStatus(existingBom.getApproveStatus());
        bom.setIsCurrent(existingBom.getIsCurrent());

        // 更新BOM主表
        boolean bomUpdated = productBomMapper.updateById(bom) > 0;
        if (!bomUpdated) {
            com.jjx.product.domain.vo.EngineeringBomEditVO failVo = new com.jjx.product.domain.vo.EngineeringBomEditVO();
            failVo.setSuccess(false);
            return failVo;
        }

        // 按 itemId 合并明细：更新/新增/标记待移除，禁止整表 delete+insert（dev-20261020-029 C）
        mergeBomItems(dto);

        com.jjx.product.domain.vo.EngineeringBomEditVO vo = new com.jjx.product.domain.vo.EngineeringBomEditVO();
        vo.setSuccess(true);
        if (!groupChanges.isEmpty()) {
            changes.add("组合归属或顺序调整：" + groupChanges.size() + "行");
        }
        var detail = cn.hutool.json.JSONUtil.createObj();
        detail.set("changes", changes);
        detail.set("attachments", List.of());
        detail.set("groupChanges", groupChanges);
        detail.set("itemChanges", itemChanges);
        vo.setDetailMessage(changes.isEmpty() && itemChanges.isEmpty() ? null : detail.toString());
        ProductEnums.BomStatus latestStatus = ProductEnums.BomStatus.fromValue(bom.getApproveStatus());
        vo.setBizStatus(latestStatus != null ? latestStatus.getLabel() : null);
        // 父 BOM 保存同事务写产品主方案；返回新 revision 供前端继续编辑。
        Integer schemeRevision = applySchemeChoices(dto, existingBom);
        vo.setSchemeRevision(schemeRevision);
        List<String> warnings = collectSubBomWarnings(dto.getBomId(), dto.getSchemeChoices());
        vo.setWarnings(warnings.isEmpty() ? null : warnings);
        // 保存成功后返回新内容指纹（以库中最新父行 + 明细重算，不用 DTO 转换结果，避免漏字段导致下一次误判过期）。
        EngineeringBom latest = productBomMapper.selectById(dto.getBomId());
        vo.setContentRevision(computeContentRevision(latest != null ? latest : bom,
                lockedItems(dto.getBomId())));
        return vo;
    }

    /**
     * 按 itemId 合并明细（dev-20261020-029 C）：
     * 已有行更新、无 itemId 的新增、缺失的已通过行转“待移除”、缺失的未通过行物理删除。
     * 行 ID 全程保留，父节点引用不再需要旧ID→新ID重映射。
     */
    private void mergeBomItems(EngineeringBomDTO dto) {
        Long bomId = dto.getBomId();
        // dev-20261022-017：组合首行先落库，再用真实行号回填组内后续行。
        // 映射仅作用于实体，不改写请求 DTO，保留日志/调用方的原始临时引用。
        java.util.Map<Long, Long> tempIdToReal = new HashMap<>();
        Map<Long, String> specificationValues = new HashMap<>();
        List<EngineeringBomItem> stored = productBomItemMapper.selectList(
                new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bomId));
        Map<Long, EngineeringBomItem> storedById = new HashMap<>();
        if (stored != null) {
            for (EngineeringBomItem it : stored) {
                if (it.getItemId() != null) storedById.put(it.getItemId(), it);
            }
        }
        Set<Long> kept = new HashSet<>();
        if (dto.getItems() != null) {
            List<EngineeringBomItemDTO> ordered = parentFirstItems(dto.getItems());
            Set<Long> removedIds = new HashSet<>();
            for (EngineeringBomItemDTO row : ordered) {
                if (Boolean.TRUE.equals(row.getRemoveRequested())) removedIds.add(row.getItemId());
            }
            for (EngineeringBomItemDTO itemDTO : ordered) {
                Long parentRef = itemDTO.getParentMaterialId();
                if (parentRef != null && removedIds.contains(parentRef)
                        && !Boolean.TRUE.equals(itemDTO.getRemoveRequested())) {
                    throw new BusinessException("组合首行待移除，请先拆组或同时移除组内材料");
                }
                if (parentRef != null && parentRef < 0) {
                    Long realParent = tempIdToReal.get(parentRef);
                    if (realParent == null) {
                        throw new BusinessException("组合父行未能保存，无法确定子行归属");
                    }
                    parentRef = realParent;
                } else if (parentRef != null) {
                    parentRef = tempIdToReal.getOrDefault(parentRef, parentRef);
                }
                Long incomingTempId = itemDTO.getItemId();
                EngineeringBomItem incoming = toBomItemEntity(itemDTO);
                if (incoming == null) continue;
                incoming.setParentMaterialId(parentRef != null && parentRef > 0 ? parentRef : null);
                incoming.setBomId(bomId);
                EngineeringBomItem old = itemDTO.getItemId() == null || itemDTO.getItemId() <= 0
                        ? null : storedById.get(itemDTO.getItemId());
                if (old != null && itemDTO.getMaterialSpecification() != null) {
                    specificationValues.put(old.getItemId(), itemDTO.getMaterialSpecification());
                }
                if (old == null) {
                    incoming.setItemId(null);
                    applyDraftStatus(incoming);
                    productBomItemMapper.insert(incoming);
                    if (incoming.getItemId() != null && itemDTO.getMaterialSpecification() != null) {
                        specificationValues.put(incoming.getItemId(), itemDTO.getMaterialSpecification());
                    }
                    if (incoming.getItemId() != null) kept.add(incoming.getItemId());
                    if (incomingTempId != null && incoming.getItemId() != null) {
                        tempIdToReal.put(incomingTempId, incoming.getItemId());
                    }
                    continue;
                }
                kept.add(old.getItemId());
                ProductEnums.BomItemReviewStatus oldStatus =
                        ProductEnums.BomItemReviewStatus.fromValue(old.getReviewStatus());
                // 待移除行：审核员确认前不改内容；工程侧撤销移除时按指纹恢复
                if (oldStatus == ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                    if (!Boolean.TRUE.equals(itemDTO.getRemoveRequested())) {
                        boolean same = !contentChanged(old, incoming);
                        copyItemContent(incoming, old);
                        old.setReviewStatus(same
                                ? ProductEnums.BomItemReviewStatus.APPROVED.getValue()
                                : ProductEnums.BomItemReviewStatus.PENDING.getValue());
                        old.setRemoveConfirmed(false);
                        old.setRemoveConfirmedBy(null);
                        old.setRemoveConfirmedTime(null);
                        productBomItemMapper.updateById(old);
                    }
                    continue;
                }
                if (Boolean.TRUE.equals(itemDTO.getRemoveRequested())) {
                    if (oldStatus == ProductEnums.BomItemReviewStatus.APPROVED) {
                        markPendingRemove(old);
                    } else {
                        productBomItemMapper.deleteById(old.getItemId());
                        kept.remove(old.getItemId());
                    }
                    continue;
                }
                boolean changed = contentChanged(old, incoming);
                copyItemContent(incoming, old);
                if (oldStatus == ProductEnums.BomItemReviewStatus.APPROVED) {
                    if (changed) {
                        old.setReviewStatus(ProductEnums.BomItemReviewStatus.PENDING.getValue());
                        clearApproval(old);
                    } else if (!Objects.equals(old.getApprovedFingerprint(), fingerprint(old))) {
                        // 历史行没有指纹：内容未变时收养当前指纹，避免无故退回待审核
                        old.setApprovedFingerprint(fingerprint(old));
                    }
                }
                productBomItemMapper.updateById(old);
            }
        }
        if (stored == null) return;
        for (EngineeringBomItem old : stored) {
            if (old.getItemId() == null || kept.contains(old.getItemId())) continue;
            ProductEnums.BomItemReviewStatus status =
                    ProductEnums.BomItemReviewStatus.fromValue(old.getReviewStatus());
            if (status == ProductEnums.BomItemReviewStatus.APPROVED) {
                markPendingRemove(old);
            } else if (status != ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                productBomItemMapper.deleteById(old.getItemId());
            }
        }
        // 以落库结果复核，覆盖省略行删除及已审核子行转待移除的混合状态。
        List<EngineeringBomItem> remaining = productBomItemMapper.selectList(
                new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bomId));
        Map<Long, EngineeringBomItem> byId = new HashMap<>();
        for (EngineeringBomItem row : remaining) byId.put(row.getItemId(), row);
        for (EngineeringBomItem row : remaining) {
            Set<Long> path = new HashSet<>();
            EngineeringBomItem cursor = row;
            while (cursor.getParentMaterialId() != null) {
                if (!path.add(cursor.getItemId()) || !byId.containsKey(cursor.getParentMaterialId())) {
                    throw new BusinessException("组合首行仍被保留的材料引用，请先拆组或完成移除审核");
                }
                cursor = byId.get(cursor.getParentMaterialId());
            }
        }
        if (remaining.stream().anyMatch(row -> row.getMaterialId() != null && row.getMaterialId() > 0)) {
            bomSpecificationService.record(bomId, remaining, specificationValues);
        }
    }

    /** 按依赖保存，不能把负数父编号当成无父行；同时拒绝悬空引用和环。 */
    private List<EngineeringBomItemDTO> parentFirstItems(List<EngineeringBomItemDTO> items) {
        Set<Long> incomingIds = new HashSet<>();
        for (EngineeringBomItemDTO item : items) {
            if (item == null) throw new BusinessException("BOM明细行不能为空");
            if (item.getItemId() != null && !incomingIds.add(item.getItemId())) {
                throw new BusinessException("BOM明细行编号重复");
            }
        }
        List<EngineeringBomItemDTO> remaining = new java.util.ArrayList<>(items);
        List<EngineeringBomItemDTO> ordered = new java.util.ArrayList<>();
        Set<Long> resolved = new HashSet<>();
        while (!remaining.isEmpty()) {
            int previousSize = remaining.size();
            var iterator = remaining.iterator();
            while (iterator.hasNext()) {
                EngineeringBomItemDTO item = iterator.next();
                Long parent = item.getParentMaterialId();
                if (parent != null && parent != 0) {
                    if (!incomingIds.contains(parent)) {
                        throw new BusinessException("组合首行不在本次BOM明细中，无法确定材料归属");
                    }
                    if (!resolved.contains(parent)) continue;
                }
                ordered.add(item);
                if (item.getItemId() != null) resolved.add(item.getItemId());
                iterator.remove();
            }
            if (remaining.size() == previousSize) {
                throw new BusinessException("BOM组合关系存在循环引用");
            }
        }
        return ordered;
    }

    /** 新行/草稿默认状态与审核痕迹清零 */
    private void applyDraftStatus(EngineeringBomItem item) {
        item.setReviewStatus(ProductEnums.BomItemReviewStatus.DRAFT.getValue());
        item.setRejectReason(null);
        item.setRemoveConfirmed(false);
        item.setRemoveConfirmedBy(null);
        item.setRemoveConfirmedTime(null);
        clearApproval(item);
    }

    private void clearApproval(EngineeringBomItem item) {
        item.setApprovedFingerprint(null);
        item.setApprovedBy(null);
        item.setApprovedTime(null);
    }

    private void markPendingRemove(EngineeringBomItem item) {
        item.setReviewStatus(ProductEnums.BomItemReviewStatus.PENDING_REMOVE.getValue());
        item.setRemoveConfirmed(false);
        item.setRemoveConfirmedBy(null);
        item.setRemoveConfirmedTime(null);
        item.setRejectReason(null);
        productBomItemMapper.updateById(item);
    }

    /** 只搬运业务字段，保留 itemId/行状态/审核痕迹 */
    private void copyItemContent(EngineeringBomItem src, EngineeringBomItem dst) {
        dst.setParentMaterialId(src.getParentMaterialId());
        dst.setMaterialId(src.getMaterialId());
        dst.setSubBomId(src.getSubBomId());
        dst.setUnitFactor(src.getUnitFactor());
        dst.setUnitFactorBasis(src.getUnitFactorBasis());
        dst.setUnitFactorSource(src.getUnitFactorSource());
        dst.setMaterialCode(src.getMaterialCode());
        dst.setMaterialName(src.getMaterialName());
        dst.setSpecification(src.getSpecification());
        dst.setSplit(src.getSplit());
        dst.setPitchMm(src.getPitchMm());
        dst.setPcsPerPitch(src.getPcsPerPitch());
        dst.setProcessId(src.getProcessId());
        dst.setProcessName(src.getProcessName());
        dst.setIsCommon(src.getIsCommon());
        dst.setUnit(src.getUnit());
        dst.setQuantity(src.getQuantity());
        dst.setAppliedQty(src.getAppliedQty());
        dst.setActualIssueQty(src.getActualIssueQty());
        dst.setLossRate(src.getLossRate());
        dst.setModuleQty(src.getModuleQty());
        dst.setBaseQty(src.getBaseQty());
        dst.setMinIssueQty(src.getMinIssueQty());
        dst.setWidthMm(src.getWidthMm());
        dst.setLengthMm(src.getLengthMm());
        dst.setLayer(src.getLayer());
        dst.setPositionNo(src.getPositionNo());
        dst.setSourceType(src.getSourceType());
        dst.setSubstituteJson(src.getSubstituteJson());
        dst.setItemOrder(src.getItemOrder());
        dst.setRemark(src.getRemark());
    }

    /** 已通过行内容是否变化（指纹为空的历史行按未变化处理） */
    private boolean contentChanged(EngineeringBomItem stored, EngineeringBomItem incoming) {
        String approved = stored.getApprovedFingerprint();
        if (!Objects.equals(stored.getParentMaterialId(), incoming.getParentMaterialId())) return true;
        // 制造关系与换算依据变化必须逐行重新审核；不改变历史指纹算法。
        if (!sameDecimal(stored.getPitchMm(), incoming.getPitchMm())) return true;
        if (!Objects.equals(stored.getPcsPerPitch(), incoming.getPcsPerPitch())) return true;
        if (!Objects.equals(stored.getSubBomId(), incoming.getSubBomId())) return true;
        if (!sameDecimal(stored.getUnitFactor(), incoming.getUnitFactor())) return true;
        if (!Objects.equals(text(stored.getUnitFactorBasis()), text(incoming.getUnitFactorBasis()))) return true;
        if (!Objects.equals(text(stored.getUnitFactorSource()), text(incoming.getUnitFactorSource()))) return true;
        if (approved == null || approved.isBlank()) return !legacyFingerprint(stored).equals(legacyFingerprint(incoming));
        return !approved.equals(fingerprint(incoming)) && !approved.equals(legacyFingerprint(incoming));
    }

    /** 行内容指纹：业务字段规范串的 SHA-256 */
    static String fingerprint(EngineeringBomItem item) {
        if (item == null) return null;
        // 列宽64：算法域标识参与散列，不增加持久化指纹长度。
        return hash("group-v1\u0001" + legacyCanonical(item) + "\u0001" + num(item.getParentMaterialId()));
    }

    static String legacyFingerprint(EngineeringBomItem item) {
        return hash(legacyCanonical(item));
    }

    private static String legacyCanonical(EngineeringBomItem item) {
        String canonical = String.join("\u0001",
                num(item.getMaterialId()), text(item.getMaterialCode()), text(item.getMaterialName()),
                text(item.getSpecification()), text(item.getUnit()), dec(item.getQuantity()),
                dec(item.getLossRate() == null ? null : item.getLossRate()),
                dec(item.getModuleQty()), dec(item.getBaseQty()), dec(item.getMinIssueQty()),
                dec(item.getWidthMm()), dec(item.getLengthMm()), text(item.getSplit()),
                num(item.getProcessId()), text(item.getProcessName()), text(item.getSourceType()),
                text(item.getLayer()), text(item.getPositionNo()));
        return canonical;
    }

    private static String hash(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("内容指纹算法不可用", e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** 按稳定行号记录归属与排序；同物料多行不会互相覆盖。请求临时编号原样留在日志。 */
    private List<Map<String, Object>> buildGroupChanges(Long bomId, EngineeringBomDTO dto) {
        Map<Long, EngineeringBomItem> oldById = new HashMap<>();
        for (EngineeringBomItem row : productBomItemMapper.selectList(
                new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bomId))) {
            oldById.put(row.getItemId(), row);
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        if (dto.getItems() == null) return result;
        for (EngineeringBomItemDTO row : dto.getItems()) {
            EngineeringBomItem old = oldById.get(row.getItemId());
            if (old == null) continue;
            if (Objects.equals(old.getParentMaterialId(), row.getParentMaterialId())
                    && Objects.equals(old.getItemOrder(), row.getItemOrder())) continue;
            Map<String, Object> change = new java.util.LinkedHashMap<>();
            change.put("itemId", row.getItemId());
            change.put("oldParentMaterialId", old.getParentMaterialId());
            change.put("newParentMaterialId", row.getParentMaterialId());
            change.put("oldItemOrder", old.getItemOrder());
            change.put("newItemOrder", row.getItemOrder());
            result.add(change);
        }
        return result;
    }

    /** 改名前值按明细ID而非物料ID对应：未建档、同料多行和换物料仍保留各自原文。 */
    private List<Map<String, Object>> buildOriginalValueChanges(Long bomId, EngineeringBomDTO dto) {
        Map<Long, EngineeringBomItem> oldById = new HashMap<>();
        for (EngineeringBomItem row : productBomItemMapper.selectList(
                new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bomId))) {
            oldById.put(row.getItemId(), row);
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        if (dto.getItems() == null) return result;
        for (EngineeringBomItemDTO row : dto.getItems()) {
            EngineeringBomItem old = oldById.get(row.getItemId());
            if (old == null || Boolean.TRUE.equals(row.getRemoveRequested())) continue;
            for (String field : List.of("materialName", "specification")) {
                String oldValue = "materialName".equals(field) ? old.getMaterialName() : old.getSpecification();
                String newValue = "materialName".equals(field) ? row.getMaterialName() : row.getSpecification();
                if (Objects.equals(oldValue, newValue)) continue;
                Map<String, Object> change = new java.util.LinkedHashMap<>();
                change.put("itemId", row.getItemId());
                change.put("field", field);
                change.put("oldValue", oldValue);
                change.put("newValue", newValue);
                result.add(change);
            }
        }
        return result;
    }

    private static String num(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String dec(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String currentOperator() {
        try {
            return com.jjx.system.utils.SecurityUtils.getDisplayName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * BOM 变更对比：主表字段 + 按稳定itemId对比的明细行。
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
            if (it.getItemId() != null) oldByMat.put(it.getItemId(), it);
        }
        java.util.Map<Long, EngineeringBomItemDTO> newByMat = new java.util.HashMap<>();
        if (dto.getItems() != null) {
            for (EngineeringBomItemDTO it : dto.getItems()) {
                if (it.getItemId() != null) newByMat.put(it.getItemId(), it);
                else changes.add("新增物料:" + matLabel(it));
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
                // dev-20261022-021：审核页需要对比"原材料名称/原规格"，把这两项也纳入变更记录。
                // 标签里带物料编码（稳定标识）而不是名称，改名后仍能按行对上。
                String codeLabel = (oldIt.getMaterialCode() != null && !oldIt.getMaterialCode().isBlank())
                        ? oldIt.getMaterialCode() : label;
                changeRecorder.diff(changes, "材料名称[" + codeLabel + "]",
                        oldIt.getMaterialName(), e.getValue().getMaterialName());
                changeRecorder.diff(changes, "规格[" + codeLabel + "]",
                        oldIt.getSpecification(), e.getValue().getSpecification());
                changeRecorder.diffDecimal(changes, "用量(" + label + ")",
                        oldIt.getQuantity(), calculatedUnitQuantity(e.getValue().getBaseQty(),
                                e.getValue().getModuleQty(), e.getValue().getQuantity()));
                changeRecorder.diffDecimal(changes, "基数(" + label + ")",
                        oldIt.getBaseQty(), e.getValue().getBaseQty());
                changeRecorder.diffDecimal(changes, "模数(" + label + ")",
                        oldIt.getModuleQty(), e.getValue().getModuleQty());
                changeRecorder.diff(changes, "跳距(" + label + ")",
                        oldIt.getSplit(), e.getValue().getSplit());
                // 2026-10-09 dev-20261009-048：补 项目名称 与 关联标准工序(processId) 的变更记录
                changeRecorder.diff(changes, "项目(" + label + ")",
                        oldIt.getProcessName(), e.getValue().getProcessName());
                if (!java.util.Objects.equals(oldIt.getProcessId(), e.getValue().getProcessId())) {
                    changes.add("关联工序(" + label + "):"
                            + procLabel(oldIt.getProcessId(), oldIt.getProcessName()) + "→"
                            + procLabel(e.getValue().getProcessId(), e.getValue().getProcessName()));
                }
            }
        }
        for (EngineeringBomItem oldIt : oldItems) {
            if (oldIt.getItemId() != null && !newByMat.containsKey(oldIt.getItemId())) {
                changes.add("移除物料:" + matLabel(oldIt));
            }
        }
        // 明细顺序变更（2026-10-10 dev-20261010-019）：拖动排序后操作日志要可见。
        // 按「新旧共同物料」的相对先后比较：仅纯增/删的记录上面已出，这里只补真实顺序被调整的情况。
        java.util.List<Long> oldSeq = new java.util.ArrayList<>();
        for (EngineeringBomItem it : oldItems) {
            if (it.getItemId() != null && !oldSeq.contains(it.getItemId())) {
                oldSeq.add(it.getItemId());
            }
        }
        java.util.List<Long> newSeq = new java.util.ArrayList<>();
        if (dto.getItems() != null) {
            for (EngineeringBomItemDTO it : dto.getItems()) {
                if (it.getItemId() != null && !newSeq.contains(it.getItemId())) {
                    newSeq.add(it.getItemId());
                }
            }
        }
        java.util.List<Long> commonOld = new java.util.ArrayList<>();
        for (Long id : oldSeq) {
            if (newSeq.contains(id)) commonOld.add(id);
        }
        java.util.List<Long> commonNew = new java.util.ArrayList<>();
        for (Long id : newSeq) {
            if (oldSeq.contains(id)) commonNew.add(id);
        }
        if (commonOld.size() > 1 && !commonOld.equals(commonNew)) {
            changes.add("明细顺序调整：" + seqLabel(commonOld, oldByMat, newByMat)
                    + " → " + seqLabel(commonNew, oldByMat, newByMat));
        }
    }

    /** 明细顺序展示：materialId 序列 → 物料编码序列（取新旧任一侧的编码） */
    private String seqLabel(java.util.List<Long> ids,
                            java.util.Map<Long, EngineeringBomItem> oldByMat,
                            java.util.Map<Long, EngineeringBomItemDTO> newByMat) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(" → ");
            Long id = ids.get(i);
            EngineeringBomItemDTO n = newByMat.get(id);
            EngineeringBomItem o = oldByMat.get(id);
            String code = null;
            if (n != null) {
                code = n.getMaterialCode() != null ? n.getMaterialCode() : n.getMaterialName();
            } else if (o != null) {
                code = o.getMaterialCode() != null ? o.getMaterialCode() : o.getMaterialName();
            }
            sb.append(code != null ? code : String.valueOf(id));
        }
        return sb.toString();
    }

    /** 关联标准工序显示名（未关联→“未关联”；无名称→#id） */
    private String procLabel(Long processId, String processName) {
        if (processId == null) return "未关联";
        return processName == null || processName.isBlank() ? ("#" + processId) : processName;
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

        // 未批准的 BOM 不得成为当前/生效版本（dev-20261020-029 F）
        if (!Objects.equals(bom.getApproveStatus(), ProductEnums.BomStatus.APPROVED.getValue())) {
            throw new BusinessException("只有已批准的BOM才能设为当前生效版本");
        }
        List<com.jjx.product.domain.vo.BomCheckIssueVO> issues = checkBomForApprove(bomId);
        if (!issues.isEmpty()) {
            throw new BusinessException(formatIssues("BOM 明细未全部审核通过，不能设为当前生效版本", issues));
        }

        // 设置当前BOM为默认
        bom.setIsCurrent(true);
        productBomMapper.updateById(bom);

        // 将其他BOM设置为非默认
        setOtherBomNotCurrent(bom.getOutputType(), bom.getOutputId(), bomId);

        // DEV-771：同步产品 current_bom_id 指针（发布校验用）
        if (ManufacturingOutputType.normalize(bom.getOutputType()) == ManufacturingOutputType.PRODUCT
                && bom.getProductId() != null) {
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
        queryWrapper.eq(EngineeringBomItem::getBomId,bomId)
                .orderByAsc(EngineeringBomItem::getItemOrder)
                .orderByAsc(EngineeringBomItem::getItemId);
        List<EngineeringBomItem> items = productBomItemMapper.selectList(queryWrapper);
        // 带出分类根类型（R含基材/辅材/油墨，未归类为null）
        if (items != null) {
            for (EngineeringBomItem it : items) {
                it.setMaterialType(null);
                if (it.getMaterialId() != null) {
                    try {
                        com.jjx.inventory.domain.InventoryMaterial mat = inventoryMaterialMapper.selectById(it.getMaterialId());
                        it.setMaterialType(mat == null ? null : inventoryMaterialMapper.materialTypeOf(mat.getCategoryId()));
                    } catch (Exception ignored) { }
                }
            }
            if (items.stream().anyMatch(row -> row.getMaterialId() != null && row.getMaterialId() > 0)) {
                bomSpecificationService.enrich(bomId, items);
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

        // 两遍复制：先获得新行号，再映射归属；禁止把旧版本行号带入新版本。
        List<EngineeringBomItem> oldItems = productBomItemMapper.selectList(
                new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bomId)
                        .orderByAsc(EngineeringBomItem::getItemOrder).orderByAsc(EngineeringBomItem::getItemId));
        Map<Long, EngineeringBomItem> originals = new HashMap<>();
        for (EngineeringBomItem row : oldItems) originals.put(row.getItemId(), row);
        for (EngineeringBomItem row : oldItems) {
            Set<Long> path = new HashSet<>();
            EngineeringBomItem cursor = row;
            while (cursor.getParentMaterialId() != null) {
                if (!path.add(cursor.getItemId()) || !originals.containsKey(cursor.getParentMaterialId())) {
                    throw new BusinessException("原BOM组合归属存在悬空或循环，不能复制版本");
                }
                cursor = originals.get(cursor.getParentMaterialId());
            }
        }
        Map<Long, EngineeringBomItem> copies = new HashMap<>();
        for (EngineeringBomItem item : oldItems) {
            EngineeringBomItem newItem = new EngineeringBomItem();
            cn.hutool.core.bean.BeanUtil.copyProperties(item, newItem);
            newItem.setItemId(null);
            newItem.setBomId(newBom.getBomId());
            newItem.setParentMaterialId(null);
            // 复制出的行重新走审核：全部草稿、清审核痕迹（dev-20261020-029）
            newItem.setReviewStatus(ProductEnums.BomItemReviewStatus.DRAFT.getValue());
            newItem.setRejectReason(null);
            newItem.setApprovedFingerprint(null);
            newItem.setApprovedBy(null);
            newItem.setApprovedTime(null);
            newItem.setRemoveConfirmed(false);
            newItem.setRemoveConfirmedBy(null);
            newItem.setRemoveConfirmedTime(null);
            productBomItemMapper.insert(newItem);
            copies.put(item.getItemId(), newItem);
        }
        for (EngineeringBomItem old : oldItems) {
            if (old.getParentMaterialId() == null) continue;
            EngineeringBomItem parent = copies.get(old.getParentMaterialId());
            if (parent == null || Objects.equals(old.getItemId(), old.getParentMaterialId())) {
                throw new BusinessException("原BOM组合归属无效，不能复制版本");
            }
            EngineeringBomItem child = copies.get(old.getItemId());
            child.setParentMaterialId(parent.getItemId());
            productBomItemMapper.updateById(child);
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
        // 统一锁顺序（dev-20261111-018）：先父行锁，再读/写明细。
        EngineeringBom bom = lockBomForWrite(bomId);
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
        // 行级状态：草稿/驳回 → 待审核；已通过行与待移除行保持（dev-20261020-029 B）
        List<EngineeringBomItem> submitItems = editContractActive() ? lockedItems(bomId) : getBomItems(bomId);
        for (EngineeringBomItem it : submitItems) {
            ProductEnums.BomItemReviewStatus status =
                    ProductEnums.BomItemReviewStatus.fromValue(it.getReviewStatus());
            if (status == ProductEnums.BomItemReviewStatus.DRAFT
                    || status == ProductEnums.BomItemReviewStatus.REJECTED) {
                it.setReviewStatus(ProductEnums.BomItemReviewStatus.PENDING.getValue());
                it.setRejectReason(null);
                productBomItemMapper.updateById(it);
            }
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
        // 统一锁顺序：先父行锁，再校验内容版本，最后改明细/状态。
        EngineeringBom productBom = lockBomForWrite(dto.getBomId());
        if (productBom == null) {
            throw new BusinessException("BOM不存在");
        }
        if (!Objects.equals(productBom.getApproveStatus(), ProductEnums.BomStatus.REVIEWING.getValue())) {
            throw new BusinessException("只有审核中的BOM才能批准");
        }
        List<EngineeringBomItem> lockedRows = editContractActive() ? lockedItems(dto.getBomId()) : getBomItems(dto.getBomId());
        assertContentRevision(productBom, lockedRows, dto.getExpectedContentRevision(),
                "BOM_CONTENT_STALE：审批期间BOM内容已被保存修改，请重新加载后再审核");
        // 批准条件（dev-20261020-029 E）：无草稿/待审核/驳回；待移除行已确认；所有行已关联有效物料
        List<com.jjx.product.domain.vo.BomCheckIssueVO> issues = checkBomForApprove(dto.getBomId());
        if (!issues.isEmpty()) {
            throw new BusinessException(formatIssues("BOM 不满足批准条件", issues));
        }
        String operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        for (EngineeringBomItem it : lockedRows) {
            ProductEnums.BomItemReviewStatus status =
                    ProductEnums.BomItemReviewStatus.fromValue(it.getReviewStatus());
            if (status == ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                // 批准事务内物理删除已确认的待移除行，并写行级流水
                reviewFlowService.record("engineering_bom_item", it.getItemId(), "REMOVE_COMMIT",
                        "确认移除并删除", status.getLabel(), "已删除", itemOriginalText(it), null);
                productBomItemMapper.deleteById(it.getItemId());
            } else {
                it.setReviewStatus(ProductEnums.BomItemReviewStatus.APPROVED.getValue());
                it.setApprovedFingerprint(fingerprint(it));
                it.setApprovedBy(operator);
                it.setApprovedTime(now);
                it.setRejectReason(null);
                it.setRemoveConfirmed(false);
                productBomItemMapper.updateById(it);
            }
        }
        dto.setCurrent(ProductEnums.BomStatus.REVIEWING.getValue());
        dto.setTarget(ProductEnums.BomStatus.APPROVED.getValue());
        boolean updated = updateStatus(dto);
        if (!updated) {
            throw new BusinessException("BOM状态已变化，批准未生效");
        }
        reviewFlowService.record("engineering_bom", dto.getBomId(), "APPROVE", "审核通过",
                dto.getCurrent(), dto.getTarget(), dto.getRemark(), null);
        publishBomEvent("bom.approved", productBom);
        return true;
    }

    private String formatIssues(String title, List<com.jjx.product.domain.vo.BomCheckIssueVO> issues) {
        StringBuilder sb = new StringBuilder(title).append("：\n");
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
        return sb.toString();
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
        // 统一锁顺序：先父行锁，再校验内容版本，最后改明细/状态。
        EngineeringBom productBom = lockBomForWrite(dto.getBomId());
        if (productBom == null) {
            throw new BusinessException("BOM不存在");
        }
        if (!Objects.equals(productBom.getApproveStatus(), ProductEnums.BomStatus.REVIEWING.getValue())) {
            throw new BusinessException("只有审核中的BOM才能驳回");
        }
        List<EngineeringBomItem> lockedRows = editContractActive() ? lockedItems(dto.getBomId()) : getBomItems(dto.getBomId());
        assertContentRevision(productBom, lockedRows, dto.getExpectedContentRevision(),
                "BOM_CONTENT_STALE：驳回期间BOM内容已被保存修改，请重新加载后再审核");
        dto.setCurrent(ProductEnums.BomStatus.REVIEWING.getValue());
        dto.setTarget(ProductEnums.BomStatus.REJECT.getValue());
        boolean updated = updateStatus(dto);
        if (updated) {
            reviewFlowService.record("engineering_bom", dto.getBomId(), "REJECT", "审核驳回",
                    dto.getCurrent(), dto.getTarget(), dto.getRemark(), null);
            // 待审核行 → 审核驳回并保留原因；已通过行保持（dev-20261020-029 B）
            for (EngineeringBomItem it : lockedRows) {
                if (ProductEnums.BomItemReviewStatus.fromValue(it.getReviewStatus())
                        == ProductEnums.BomItemReviewStatus.PENDING) {
                    it.setReviewStatus(ProductEnums.BomItemReviewStatus.REJECTED.getValue());
                    it.setRejectReason(dto.getRemark());
                    productBomItemMapper.updateById(it);
                }
            }
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
        item.setParentMaterialId(dto.getParentMaterialId() != null && dto.getParentMaterialId() > 0
                ? dto.getParentMaterialId() : null);
        // 快速模式允许 materialId 为空；0/负数统一归一化为 null（dev-20261020-029 A）
        item.setMaterialId(dto.getMaterialId() != null && dto.getMaterialId() > 0 ? dto.getMaterialId() : null);
        item.setSubBomId(dto.getSubBomId() != null && dto.getSubBomId() > 0 ? dto.getSubBomId() : null);
        item.setUnitFactor(dto.getUnitFactor());
        item.setUnitFactorBasis(dto.getUnitFactorBasis());
        item.setUnitFactorSource(dto.getUnitFactorSource());
        // 行状态由服务端维护；新实体先给草稿，已有行在 mergeBomItems 中保留原状态
        item.setReviewStatus(ProductEnums.BomItemReviewStatus.DRAFT.getValue());
        item.setRemoveConfirmed(false);
        item.setMaterialCode(dto.getMaterialCode());
        item.setMaterialName(dto.getMaterialName());
        item.setSpecification(dto.getSpecification());
        item.setSplit(dto.getSplit() == null ? null : dto.getSplit().trim());
        item.setPitchMm(dto.getPitchMm());
        item.setPcsPerPitch(dto.getPcsPerPitch() == null ? 1 : dto.getPcsPerPitch());
        item.setProcessId(dto.getProcessId());
        item.setProcessName(dto.getProcessName());
        item.setIsCommon(dto.getProcessId() == null
                && (dto.getProcessName() == null || dto.getProcessName().isBlank()));
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
            // 原始比例是权威；这里仅生成展示快照。比例过小导致 4 位快照归零时明确阻断，
            // 不把 0 当成合法用量（dev-20261111-004）。
            var exact = com.jjx.production.domain.model.BomConsumptionCalculator.theoretical(
                    BigDecimal.ONE, base, module, legacyQuantity);
            java.math.BigDecimal display = exact.toBigDecimal(4, java.math.RoundingMode.HALF_UP);
            if (display.signum() == 0 && exact.isPositive()) {
                throw new BusinessException("MODULE_QTY_INVALID：基数/模数比例过小，4 位展示用量会归零；请调整精度");
            }
            return display;
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
        // 后端统一从原始参数计算；quantity/appliedQty 只是4位快照，不反向参与试算。
        boolean pitchGroup = item.getPitchMm() != null || (item.getPcsPerPitch() != null && item.getPcsPerPitch() != 1);
        com.jjx.production.domain.model.ExactQuantity theoretical;
        if (pitchGroup || item.getBaseQty() != null || item.getModuleQty() != null) {
            var material = pitchGroup && item.getMaterialId() != null
                    ? inventoryMaterialMapper.selectById(item.getMaterialId()) : null;
            theoretical = com.jjx.production.service.BomConsumptionPreviewService.rawUnitQuantity(
                    item.getBaseQty(), item.getModuleQty(), item.getPitchMm(), item.getPcsPerPitch(),
                    item.getUnit(), material == null ? null : material.getWidthMm());
            item.setQuantity(theoretical.toBigDecimal(4, java.math.RoundingMode.HALF_UP));
        } else {
            // 两组均空允许存草稿；历史仅用量行保留原值。
            item.setQuantity(item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity());
            theoretical = com.jjx.production.domain.model.ExactQuantity.fromDecimal(item.getQuantity());
        }
        var applied = com.jjx.production.domain.model.BomConsumptionCalculator.lossApplied(theoretical,
                (item.getLossRate() == null ? BigDecimal.ZERO : item.getLossRate()));
        item.setAppliedQty(applied.toBigDecimal(4, java.math.RoundingMode.HALF_UP));
        item.setActualIssueQty(item.getAppliedQty());
    }

    /**
     * BOM 提交审核前数据完整性体检（只读）。
     * 2026-10-07 dev-20261007-006（方案 A：纯校验，不动 DB）。
     */
    @Override
    public List<com.jjx.product.domain.vo.BomCheckIssueVO> checkBomForSubmit(Long bomId) {
        return checkBom(bomId, false);
    }

    /**
     * BOM 批准前体检（只读）：行必须全部审核通过、待移除行已确认、所有行已关联有效物料。
     */
    @Override
    public List<com.jjx.product.domain.vo.BomCheckIssueVO> checkBomForApprove(Long bomId) {
        return checkBom(bomId, true);
    }

    private List<com.jjx.product.domain.vo.BomCheckIssueVO> checkBom(Long bomId, boolean forApprove) {
        List<com.jjx.product.domain.vo.BomCheckIssueVO> issues = new java.util.ArrayList<>();
        EngineeringBom bom = productBomMapper.selectById(bomId);
        if (bom == null) {
            issues.add(issue(null, null, null, "bomId", "BOM不存在"));
            return issues;
        }
        // 主表：按产出身份校验存在性（MATERIAL 不再用 product_id 冒充）
        String outputType = ManufacturingOutputType.normalize(bom.getOutputType()).name();
        Long outputId = bom.getOutputId() != null ? bom.getOutputId() : bom.getProductId();
        if (outputId == null) {
            issues.add(issue(null, null, null, "outputId", "BOM未关联产出对象"));
        } else if (ManufacturingOutputType.PRODUCT.name().equals(outputType)) {
            if (productMapper.selectById(outputId) == null) {
                issues.add(issue(null, null, null, "productId", "关联产品不存在（product_id=" + outputId + "）"));
            }
        } else {
            com.jjx.inventory.domain.InventoryMaterial material = inventoryMaterialMapper.selectById(outputId);
            if (material == null) {
                issues.add(issue(null, null, null, "outputId", "产出物料不存在（material_id=" + outputId + "）"));
            } else if (material.getStatus() != null && material.getStatus() != 1) {
                issues.add(issue(null, null, null, "outputId", "产出物料已停用（material_id=" + outputId + "）"));
            }
        }
        // 主表：同一产出对象只能有一个当前版本
        if (Boolean.TRUE.equals(bom.getIsCurrent()) && outputId != null) {
            Long currentCnt = productBomMapper.selectCount(new LambdaQueryWrapper<EngineeringBom>()
                    .eq(EngineeringBom::getOutputType, outputType)
                    .eq(EngineeringBom::getOutputId, outputId)
                    .eq(EngineeringBom::getIsCurrent, true));
            if (currentCnt != null && currentCnt > 1) {
                issues.add(issue(null, null, null, "isCurrent",
                        "同一产出对象存在 " + currentCnt + " 个当前版本（is_current=1），请先修正"));
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
        java.util.Set<Long> referencedParents = new java.util.HashSet<>();
        for (EngineeringBomItem it : items) {
            if (it.getParentMaterialId() != null) {
                referencedParents.add(it.getParentMaterialId());
            }
        }
        boolean hasRoot = false;
        int activeRows = 0;
        for (EngineeringBomItem it : items) {
            Long iid = it.getItemId();
            String code = it.getMaterialCode();
            String name = it.getMaterialName();
            ProductEnums.BomItemReviewStatus rowStatus =
                    ProductEnums.BomItemReviewStatus.fromValue(it.getReviewStatus());
            // 行归属：明细必须挂在本 BOM 下
            if (!java.util.Objects.equals(it.getBomId(), bomId)) {
                issues.add(issue(iid, code, name, "bomId", "明细不属于本BOM"));
            }
            // 待移除行不参与内容校验；批准前必须已由审核员确认移除
            if (rowStatus == ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                if (forApprove && !Boolean.TRUE.equals(it.getRemoveConfirmed())) {
                    issues.add(issue(iid, code, name, "removeConfirmed", "待移除行尚未确认移除"));
                }
                if (iid != null && referencedParents.contains(iid)) {
                    issues.add(issue(iid, code, name, "removeConfirmed",
                            "待移除行仍被其他明细引用为父节点，请先调整父子关系"));
                }
                continue;
            }
            activeRows++;
            if (forApprove && rowStatus != ProductEnums.BomItemReviewStatus.APPROVED) {
                issues.add(issue(iid, code, name, "reviewStatus",
                        "行状态为「" + rowStatus.getLabel() + "」，需逐行审核通过"));
            }
            // 材料原文（快速模式）：名称必填，规格选填；未建档允许先入审核
            if (org.apache.commons.lang3.StringUtils.isBlank(it.getMaterialName())) {
                issues.add(issue(iid, code, name, "materialName", "材料名称不能为空"));
            }
            // 物料关联：批准时必须已匹配有效物料
            if (it.getMaterialId() == null || it.getMaterialId() <= 0) {
                if (forApprove) {
                    issues.add(issue(iid, code, name, "materialId", "未匹配物料，请审核员关联已有物料或新建"));
                }
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
            if (it.getLossRate() != null && (it.getLossRate().signum() < 0 || it.getLossRate().compareTo(BigDecimal.valueOf(100)) > 0)) {
                issues.add(issue(iid, code, name, "lossRate", "损耗率须在 0~100 之间"));
            }
            // 来源类型
            String sourceType = it.getSourceType() == null || it.getSourceType().isBlank()
                    ? "buy" : it.getSourceType().trim();
            if (!SOURCE_TYPES.contains(sourceType)) {
                issues.add(issue(iid, code, name, "sourceType", "来源类型非法（应为 buy/make）"));
            }
            // 来源与显式下层引用：make 必须有方案，buy 不得挂方案
            if ("buy".equals(sourceType) && it.getSubBomId() != null) {
                issues.add(issue(iid, code, name, "subBomId", "BUY_WITH_SUB_BOM：外购行不能引用制造方案"));
            }
            if ("make".equals(sourceType) && it.getSubBomId() == null) {
                issues.add(issue(iid, code, name, "subBomId", "MAKE_WITHOUT_SCHEME：自制行必须明确下层制造方案"));
            }
            if (it.getSubBomId() != null) {
                String subError = validateSubBomReference(bomId, it, true);
                if (subError != null) {
                    issues.add(issue(iid, code, name, "subBomId", subError));
                }
            }
            // 模数 / 基数（dev-20261111-004：>0 正小数，最多 4 位，拒绝超精度静默归零）
            String moduleError = validatePositiveDecimal(it.getModuleQty(), "MODULE_QTY_INVALID", 12, 4);
            if (moduleError != null) {
                issues.add(issue(iid, code, name, "moduleQty", moduleError));
            }
            String baseError = validatePositiveDecimal(it.getBaseQty(), "BASE_QTY_INVALID", 12, 4);
            if (baseError != null) {
                issues.add(issue(iid, code, name, "baseQty", baseError));
            }
            // 单位依据：跨单位必须有换算系数与依据
            String unitError = validateUnitBasis(it);
            if (unitError != null) {
                issues.add(issue(iid, code, name, "unitFactor", unitError));
            }
            // 项目（标准工序）引用完整性
            if (it.getProcessId() != null) {
                com.jjx.product.domain.entity.ProductStandardProcess sp = standardProcessMapper.selectById(it.getProcessId());
                if (sp == null) {
                    issues.add(issue(iid, code, name, "processId", "项目（标准工序）不存在（process_id=" + it.getProcessId() + "）"));
                } else if (sp.getIsEnabled() != null && sp.getIsEnabled() == 0) {
                    issues.add(issue(iid, code, name, "processId", "项目（标准工序）「" + sp.getProcessName() + "」已停用"));
                }
            } else if (it.getProcessName() != null && !it.getProcessName().isBlank()) {
                issues.add(issue(iid, code, name, "processId", "项目名称未匹配标准工序，请重新选择项目或清空作为公共物料"));
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
        if (activeRows == 0) {
            issues.add(issue(null, null, null, "items", "BOM至少保留一行有效明细（待移除行全部删除后不能为空）"));
        }
        if (!hasRoot && activeRows > 0) {
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
        // 显式下层引用全图 DFS：环/深度/未批准子方案，不以“两层截断”伪装通过
        String graphError = validateSubBomGraph(bomId, items, true);
        if (graphError != null) {
            issues.add(issue(null, null, null, "subBomId", graphError));
        }
        // 产品主方案引用的方案也必须已批准
        String schemeError = validateSavedSchemeChoices(bom);
        if (schemeError != null) {
            issues.add(issue(null, null, null, "schemeChoices", schemeError));
        }
        return issues;
    }

    // ==================== 制造 BOM 身份/精度/单位/防环（dev-20261111-004） ====================

    /** 新增/编辑前归一化产出身份；PRODUCT 兼容历史 productId 调用，MATERIAL 校验物料档案。 */
    private void normalizeOutput(EngineeringBomDTO dto) {
        ManufacturingOutputType type = ManufacturingOutputType.normalize(dto.getOutputType());
        Long outputId = dto.getOutputId() != null ? dto.getOutputId() : dto.getProductId();
        if (outputId == null) {
            throw new BusinessException("产出对象不能为空（outputId/productId）");
        }
        if (type == ManufacturingOutputType.PRODUCT) {
            dto.setProductId(outputId);
        } else {
            com.jjx.inventory.domain.InventoryMaterial material = inventoryMaterialMapper.selectById(outputId);
            if (material == null) {
                throw new BusinessException("产出物料不存在（material_id=" + outputId + "）");
            }
            if (material.getStatus() != null && material.getStatus() != 1) {
                throw new BusinessException("产出物料已停用（material_id=" + outputId + "）");
            }
            dto.setProductId(null);
        }
        dto.setOutputType(type.name());
        dto.setOutputId(outputId);
    }

    /** 编辑时产出身份不可改变；未传时沿用库中身份。 */
    private void normalizeOutputForUpdate(EngineeringBomDTO dto, EngineeringBom existing) {
        ManufacturingOutputType existingType = ManufacturingOutputType.normalize(existing.getOutputType());
        Long existingId = existing.getOutputId() != null ? existing.getOutputId() : existing.getProductId();
        ManufacturingOutputType type = (dto.getOutputType() == null || dto.getOutputType().isBlank())
                ? existingType : ManufacturingOutputType.normalize(dto.getOutputType());
        Long outputId = dto.getOutputId() != null ? dto.getOutputId()
                : (type == ManufacturingOutputType.PRODUCT && dto.getProductId() != null ? dto.getProductId() : existingId);
        if (type != existingType || !Objects.equals(outputId, existingId)) {
            throw new BusinessException("产出身份不可修改（bomId=" + existing.getBomId() + "）");
        }
        dto.setOutputType(type.name());
        dto.setOutputId(existingId);
        dto.setProductId(type == ManufacturingOutputType.PRODUCT ? existingId : null);
    }

    /** 保存阶段规则：新行/变更行校验；既有未改行保持原保存行为，不强制重解释历史单位。 */
    private void validateItemRules(Long bomId, String outputType, List<EngineeringBomItemDTO> items,
                                   Map<Long, EngineeringBomItem> stored, boolean allNew) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<EngineeringBomItem> incoming = new java.util.ArrayList<>();
        for (EngineeringBomItemDTO row : items) {
            if (row == null) continue;
            if (Boolean.TRUE.equals(row.getRemoveRequested())) continue;
            EngineeringBomItem incomingRow = toBomItemEntity(row);
            incoming.add(incomingRow);
            boolean changed = allNew || row.getItemId() == null || row.getItemId() <= 0
                    || !stored.containsKey(row.getItemId())
                    || manufacturingContentChanged(stored.get(row.getItemId()), incomingRow);
            if (!changed) continue;
            String sourceType = row.getSourceType() == null || row.getSourceType().isBlank()
                    ? "buy" : row.getSourceType().trim();
            if (!SOURCE_TYPES.contains(sourceType)) {
                throw new BusinessException("来源类型非法（应为 buy/make）");
            }
            if ("buy".equals(sourceType) && row.getSubBomId() != null) {
                throw new BusinessException("BUY_WITH_SUB_BOM：外购行不能引用制造方案");
            }
            if ("make".equals(sourceType) && row.getSubBomId() == null) {
                throw new BusinessException("MAKE_WITHOUT_SCHEME：自制行必须明确下层制造方案");
            }
            String unitError = validateUnitBasis(incomingRow);
            if (unitError != null) throw new BusinessException(unitError);
        }
        String graphError = validateSubBomGraph(bomId, incoming, false);
        if (graphError != null) throw new BusinessException(graphError);
    }

    /** 只有影响制造规则的行内容变化才触发新规则；历史行未改时不重解释单位/来源。 */
    private boolean manufacturingContentChanged(EngineeringBomItem stored, EngineeringBomItem incoming) {
        if (stored == null || incoming == null) return true;
        return !Objects.equals(stored.getMaterialId(), incoming.getMaterialId())
                || !Objects.equals(stored.getSubBomId(), incoming.getSubBomId())
                || !Objects.equals(text(stored.getSourceType()), text(incoming.getSourceType()))
                || !Objects.equals(text(stored.getUnit()), text(incoming.getUnit()))
                || !sameDecimal(stored.getUnitFactor(), incoming.getUnitFactor())
                || !Objects.equals(text(stored.getUnitFactorBasis()), text(incoming.getUnitFactorBasis()))
                || !Objects.equals(text(stored.getUnitFactorSource()), text(incoming.getUnitFactorSource()))
                || !sameDecimal(stored.getPitchMm(), incoming.getPitchMm())
                || !Objects.equals(stored.getPcsPerPitch(), incoming.getPcsPerPitch())
                || !sameDecimal(stored.getModuleQty(), incoming.getModuleQty())
                || !sameDecimal(stored.getBaseQty(), incoming.getBaseQty())
                || !sameDecimal(stored.getLossRate(), incoming.getLossRate());
    }

    private static boolean sameDecimal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** >0 正小数；拒绝 0/负数/空值；按目标 decimal(precision,scale) 拒绝超精度，避免落库静默归零。 */
    private String validatePositiveDecimal(BigDecimal value, String code, int precision, int scale) {
        if (value == null) {
            return code + "：不能为空";
        }
        if (value.signum() <= 0) {
            return code + "：必须大于 0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        int actualScale = Math.max(0, stripped.scale());
        if (actualScale > scale) {
            return code + "：最多支持 " + scale + " 位小数，拒绝超精度静默归零（输入=" + value.toPlainString() + "）";
        }
        int integerDigits = stripped.precision() - stripped.scale();
        if (integerDigits > precision - scale) {
            return code + "：整数位超出 decimal(" + precision + "," + scale + ") 上限（输入=" + value.toPlainString() + "）";
        }
        return null;
    }

    /** 单位依据：BOM 单位与档案单位一致无需换算；不一致必须给出正系数和依据。 */
    private String validateUnitBasis(EngineeringBomItem item) {
        if (item.getMaterialId() == null || item.getUnit() == null || item.getUnit().isBlank()) {
            return null;
        }
        if (inventoryMaterialMapper == null) {
            return null;
        }
        com.jjx.inventory.domain.InventoryMaterial material = inventoryMaterialMapper.selectById(item.getMaterialId());
        if (material == null || material.getUnit() == null || material.getUnit().isBlank()) {
            return null;
        }
        boolean same = material.getUnit().trim().equalsIgnoreCase(item.getUnit().trim());
        if (same) {
            if (item.getUnitFactor() != null && item.getUnitFactor().signum() <= 0) {
                return "UNIT_FACTOR_MISSING：换算系数必须大于 0";
            }
            return null;
        }
        if (item.getUnitFactor() == null || item.getUnitFactor().signum() <= 0) {
            return "UNIT_FACTOR_MISSING：单位 " + item.getUnit() + " 与档案单位 " + material.getUnit()
                    + " 不一致，必须填写换算系数";
        }
        String factorError = validatePositiveDecimal(item.getUnitFactor(), "UNIT_FACTOR_MISSING", 18, 8);
        if (factorError != null) {
            return factorError;
        }
        if (item.getUnitFactorBasis() == null || item.getUnitFactorBasis().isBlank()) {
            return "UNIT_FACTOR_MISSING：跨单位换算必须填写依据（尺寸/批次实测/供应商资料）";
        }
        return null;
    }

    /** 校验单行显式下层引用；rootBomId 为空时只校验存在/输出一致（新建尚未落库）。 */
    private String validateSubBomReference(Long rootBomId, EngineeringBomItem row, boolean requireApproved) {
        if (row.getSubBomId() == null) {
            return null;
        }
        if (row.getMaterialId() == null) {
            return "SUB_BOM_OUTPUT_MISMATCH：投入行未关联物料，无法绑定制造方案";
        }
        if (rootBomId != null && Objects.equals(rootBomId, row.getSubBomId())) {
            return "BOM_CYCLE：方案不能引用自身（bomId=" + rootBomId + "）";
        }
        EngineeringBom child = productBomMapper.selectById(row.getSubBomId());
        if (child == null) {
            return "SUB_BOM_NOT_FOUND：下层方案不存在（bomId=" + row.getSubBomId() + "）";
        }
        if (ManufacturingOutputType.normalize(child.getOutputType()) != ManufacturingOutputType.MATERIAL) {
            return "SUB_BOM_OUTPUT_MISMATCH：下层方案产出不是物料（bomId=" + child.getBomId() + "）";
        }
        Long childOutputId = child.getOutputId() != null ? child.getOutputId() : child.getProductId();
        if (!Objects.equals(childOutputId, row.getMaterialId())) {
            return "SUB_BOM_OUTPUT_MISMATCH：下层方案产出物料 " + childOutputId
                    + " 与本行物料 " + row.getMaterialId() + " 不一致";
        }
        if (requireApproved && !Objects.equals(child.getApproveStatus(), ProductEnums.BomStatus.APPROVED.getValue())) {
            return "SUB_BOM_NOT_APPROVED：下层方案未批准（bomId=" + child.getBomId() + "）";
        }
        return null;
    }

    /**
     * 显式引用全图校验：迭代 DFS + 三色标记，不依赖固定业务层数。
     * 共享 DAG 只按节点查询/遍历一次；同一条访问路径重复出现才是环。
     */
    private String validateSubBomGraph(Long rootBomId, List<EngineeringBomItem> rows, boolean requireApproved) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        java.util.Set<Long> starts = new java.util.LinkedHashSet<>();
        for (EngineeringBomItem row : rows) {
            if (row.getSubBomId() == null) continue;
            String error = validateSubBomReference(rootBomId, row, requireApproved);
            if (error != null) return error;
            starts.add(row.getSubBomId());
        }
        java.util.Map<Long, java.util.List<Long>> childCache = new java.util.HashMap<>();
        java.util.Map<Long, Integer> color = new java.util.HashMap<>(); // 1=访问中，2=已完成
        for (Long start : starts) {
            String error = walkSubBom(start, rootBomId, requireApproved, childCache, color);
            if (error != null) return error;
        }
        return null;
    }

    /** 显式栈深度优先；每节点最多展开一次，深链不会栈溢出。 */
    private String walkSubBom(Long start, Long rootBomId, boolean requireApproved,
                              java.util.Map<Long, java.util.List<Long>> childCache,
                              java.util.Map<Long, Integer> color) {
        if (rootBomId != null && Objects.equals(rootBomId, start)) {
            return "BOM_CYCLE：显式制造关系形成环，路径=" + rootBomId + " -> " + start;
        }
        Integer state = color.get(start);
        if (state != null && state == 2) return null;
        if (state != null && state == 1) {
            return "BOM_CYCLE：显式制造关系形成环（bomId=" + start + "）";
        }
        SubBomScan startScan = scanSubBomNode(start, rootBomId, requireApproved, childCache);
        if (startScan.error != null) return startScan.error;
        java.util.Deque<SubBomFrame> stack = new java.util.ArrayDeque<>();
        java.util.List<Long> path = new java.util.ArrayList<>();
        color.put(start, 1);
        path.add(start);
        stack.push(new SubBomFrame(start, startScan.children));
        while (!stack.isEmpty()) {
            SubBomFrame frame = stack.peek();
            if (frame.next >= frame.children.size()) {
                color.put(frame.bomId, 2);
                stack.pop();
                path.remove(path.size() - 1);
                continue;
            }
            Long next = frame.children.get(frame.next++);
            if (rootBomId != null && Objects.equals(rootBomId, next)) {
                return "BOM_CYCLE：显式制造关系形成环，路径=" + path + " -> " + next;
            }
            Integer nextState = color.get(next);
            if (nextState != null && nextState == 2) continue;
            if (nextState != null && nextState == 1) {
                return "BOM_CYCLE：显式制造关系形成环，路径=" + path + " -> " + next;
            }
            SubBomScan scan = scanSubBomNode(next, rootBomId, requireApproved, childCache);
            if (scan.error != null) return scan.error;
            color.put(next, 1);
            path.add(next);
            stack.push(new SubBomFrame(next, scan.children));
        }
        return null;
    }

    /** 按节点缓存显式下层引用；同一节点在共享 DAG 中只查库一次。 */
    private SubBomScan scanSubBomNode(Long bomId, Long rootBomId, boolean requireApproved,
                                      java.util.Map<Long, java.util.List<Long>> childCache) {
        java.util.List<Long> cached = childCache.get(bomId);
        if (cached != null) return new SubBomScan(cached, null);
        java.util.List<EngineeringBomItem> children = productBomItemMapper.selectList(
                new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bomId));
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (EngineeringBomItem child : children) {
            if (child.getSubBomId() == null) continue;
            String error = validateSubBomReference(rootBomId, child, requireApproved);
            if (error != null) return new SubBomScan(null, error);
            ids.add(child.getSubBomId());
        }
        childCache.put(bomId, ids);
        return new SubBomScan(ids, null);
    }

    private static final class SubBomFrame {
        private final Long bomId;
        private final java.util.List<Long> children;
        private int next;

        private SubBomFrame(Long bomId, java.util.List<Long> children) {
            this.bomId = bomId;
            this.children = children;
        }
    }

    private static final class SubBomScan {
        private final java.util.List<Long> children;
        private final String error;

        private SubBomScan(java.util.List<Long> children, String error) {
            this.children = children;
            this.error = error;
        }
    }

    /**
     * 产品主方案校验：只校验本 BOM 行实际显式引用的方案（sub_bom_id）。
     * 产品 JSON 主方案未被实际引用时（例如同材料另有已批准非主方案、或只是直接大料行），
     * 不得因其草稿阻断审批；坏 JSON 仍明确阻断，不静默当空。
     */
    private String validateSavedSchemeChoices(EngineeringBom bom) {
        if (ManufacturingOutputType.normalize(bom.getOutputType()) != ManufacturingOutputType.PRODUCT
                || bom.getProductId() == null) {
            return null;
        }
        Product product = productMapper.selectById(bom.getProductId());
        if (product == null) return null;
        List<ManufacturingSchemeChoiceDTO> choices;
        try {
            choices = readSchemeChoices(product.getMaterialSchemeJson());
        } catch (BusinessException ex) {
            return ex.getMessage();
        }
        if (choices.isEmpty()) return null;
        // 只认本 BOM 行的显式引用（sub_bom_id → materialId）：产品 JSON 主方案只是默认选择，
        // 未被本 BOM 实际引用时不得因其未批准阻断审批；直接大料行也不会被强制制造。
        Map<Long, Set<Long>> explicitRefs = new HashMap<>();
        for (EngineeringBomItem item : productBomItemMapper.selectList(
                new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bom.getBomId()))) {
            if (ProductEnums.BomItemReviewStatus.fromValue(item.getReviewStatus())
                    == ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                continue;
            }
            if (item.getSubBomId() == null || item.getMaterialId() == null) continue;
            explicitRefs.computeIfAbsent(item.getSubBomId(), k -> new java.util.LinkedHashSet<>())
                    .add(item.getMaterialId());
        }
        for (ManufacturingSchemeChoiceDTO choice : choices) {
            if (!Boolean.TRUE.equals(choice.getIsPrimary())) continue;
            Set<Long> refMaterials = explicitRefs.get(choice.getBomId());
            if (refMaterials == null || !refMaterials.contains(choice.getMaterialId())) continue;
            EngineeringBom child = productBomMapper.selectById(choice.getBomId());
            if (child == null) {
                return "SUB_BOM_NOT_FOUND：产品主方案引用不存在（bomId=" + choice.getBomId() + "）";
            }
            Long childOutputId = child.getOutputId() != null ? child.getOutputId() : child.getProductId();
            if (ManufacturingOutputType.normalize(child.getOutputType()) != ManufacturingOutputType.MATERIAL
                    || !Objects.equals(childOutputId, choice.getMaterialId())) {
                return "SUB_BOM_OUTPUT_MISMATCH：产品主方案产出与物料不一致（materialId=" + choice.getMaterialId() + "）";
            }
            if (!Objects.equals(child.getApproveStatus(), ProductEnums.BomStatus.APPROVED.getValue())) {
                return "SUB_BOM_NOT_APPROVED：产品主方案未批准（bomId=" + choice.getBomId() + "）";
            }
        }
        return null;
    }

    /**
     * 父 BOM 显式保存时同事务写产品主方案 JSON；行锁 + scheme_revision 乐观锁。
     * 语义：isPrimary=true 只替换该 materialId 的主方案；isPrimary=false/null 表示父 BOM 实际采用，
     * 由行的 sub_bom_id 表达，不改产品主方案；cancelPrimary=true 只移除该物料主方案。
     * 只有真正写 JSON 时才要求 schemeRevision，旧前端未传 schemeChoices 不受影响。
     */
    private Integer applySchemeChoices(EngineeringBomDTO dto, EngineeringBom bom) {
        if (dto.getSchemeChoices() == null || dto.getSchemeChoices().isEmpty()) {
            return null;
        }
        if (ManufacturingOutputType.normalize(dto.getOutputType()) != ManufacturingOutputType.PRODUCT) {
            throw new BusinessException("只有产品 BOM 可以保存产品主方案选择");
        }
        Long productId = dto.getProductId() != null ? dto.getProductId() : bom.getProductId();
        if (productId == null) {
            throw new BusinessException("产品 BOM 未关联产品，不能保存主方案选择");
        }
        Product product = productMapper.selectSchemeForUpdate(productId);
        if (product == null) {
            throw new BusinessException("产品不存在（product_id=" + productId + "）");
        }
        int currentRevision = product.getSchemeRevision() == null ? 0 : product.getSchemeRevision();
        if (dto.getSchemeRevision() != null && !Objects.equals(dto.getSchemeRevision(), currentRevision)) {
            throw new BusinessException(409, "CONCURRENT_MODIFICATION：主方案已被其他会话修改，请重新加载后重试");
        }
        Set<Long> usedMaterials = usedMaterialIdsForScheme(
                dto.getBomId() != null ? dto.getBomId() : bom.getBomId(), dto);
        List<ManufacturingSchemeChoiceDTO> mutations = new java.util.ArrayList<>();
        for (ManufacturingSchemeChoiceDTO choice : dto.getSchemeChoices()) {
            if (choice == null || choice.getMaterialId() == null) {
                throw new BusinessException("主方案选择必须包含 materialId");
            }
            boolean cancel = Boolean.TRUE.equals(choice.getCancelPrimary());
            boolean primary = Boolean.TRUE.equals(choice.getIsPrimary());
            if (cancel && primary) {
                throw new BusinessException("取消主方案与设为主方案不能同时提交（materialId=" + choice.getMaterialId() + "）");
            }
            // 设主/普通采用必须有 bomId；仅显式取消允许为空（与 DTO BeanValidation 双重兜底）。
            if (!cancel && choice.getBomId() == null) {
                throw new BusinessException("制造方案不能为空（materialId=" + choice.getMaterialId() + "）");
            }
            if (!cancel && !primary) {
                // 非主采用：父 BOM 实际采用由行 sub_bom_id 表达，不覆盖产品主方案。
                validateSchemeChoiceChild(choice);
                continue;
            }
            if (!usedMaterials.contains(choice.getMaterialId())) {
                throw new BusinessException("父 BOM 未使用该物料，不能设置/取消主方案（materialId="
                        + choice.getMaterialId() + "）");
            }
            if (!cancel) validateSchemeChoiceChild(choice);
            mutations.add(choice);
        }
        if (mutations.isEmpty()) {
            return currentRevision;
        }
        if (dto.getSchemeRevision() == null) {
            throw new BusinessException(400,
                    "SCHEME_REVISION_REQUIRED：修改产品主方案必须携带 schemeRevision，不能跳过并发校验");
        }
        ObjectNode root;
        try {
            String raw = product.getMaterialSchemeJson();
            JsonNode parsed = raw == null || raw.isBlank() ? null : SCHEME_JSON.readTree(raw);
            if (parsed != null && !(parsed instanceof ObjectNode)) {
                throw new BusinessException("产品主方案 JSON 结构非法（不是对象），请先核对历史数据");
            }
            root = parsed == null ? SCHEME_JSON.createObjectNode() : (ObjectNode) parsed;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("产品主方案 JSON 解析失败，请先核对历史数据: " + ex.getMessage());
        }
        for (ManufacturingSchemeChoiceDTO choice : mutations) {
            if (Boolean.TRUE.equals(choice.getCancelPrimary())) {
                root.remove(String.valueOf(choice.getMaterialId()));
                continue;
            }
            // 只改当前物料节点，其他材料的主方案保持原样。
            ObjectNode node = root.withObject("/" + choice.getMaterialId());
            node.put("bomId", choice.getBomId());
            node.put("isPrimary", true);
        }
        int updated = productMapper.updateScheme(productId, root.toString(), currentRevision);
        if (updated == 0) {
            throw new BusinessException(409, "CONCURRENT_MODIFICATION：主方案已被其他会话修改，请重新加载后重试");
        }
        return currentRevision + 1;
    }

    /**
     * 本 BOM 实际显式关联图可达的投入物料集合；只允许这些物料被本次保存写主方案。
     * 根层使用本次 DTO 行（含新增未保存行、待移除行）；下层沿 sub_bom_id 读取已保存行逐层展开，
     * 按 bomId 缓存访问防环。仅沿显式引用，不按分组或物料来源推断上下层关系。
     */
    private Set<Long> usedMaterialIdsForScheme(Long bomId, EngineeringBomDTO dto) {
        Set<Long> out = new java.util.LinkedHashSet<>();
        Set<Long> visitedBoms = new HashSet<>();
        java.util.ArrayDeque<Long> pendingBoms = new java.util.ArrayDeque<>();
        if (dto != null && dto.getItems() != null) {
            // 根层以本次请求为准：删除/换方案后的旧下层路径不再展开。
            if (bomId != null) visitedBoms.add(bomId);
            for (EngineeringBomItemDTO row : dto.getItems()) {
                if (row == null || Boolean.TRUE.equals(row.getRemoveRequested())) continue;
                if (row.getMaterialId() != null) out.add(row.getMaterialId());
                if (row.getSubBomId() != null && visitedBoms.add(row.getSubBomId())) {
                    pendingBoms.add(row.getSubBomId());
                }
            }
        } else if (bomId != null && visitedBoms.add(bomId)) {
            pendingBoms.add(bomId);
        }
        while (!pendingBoms.isEmpty()) {
            Long currentBomId = pendingBoms.poll();
            for (EngineeringBomItem row : productBomItemMapper.selectList(
                    new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, currentBomId))) {
                if (ProductEnums.BomItemReviewStatus.fromValue(row.getReviewStatus())
                        == ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                    continue;
                }
                if (row.getMaterialId() != null) out.add(row.getMaterialId());
                if (row.getSubBomId() != null && visitedBoms.add(row.getSubBomId())) {
                    pendingBoms.add(row.getSubBomId());
                }
            }
        }
        return out;
    }

    /** 校验被选制造方案存在、产出为 MATERIAL 且与投入物料一致；草稿可保存，提交/批准另校验。 */
    private void validateSchemeChoiceChild(ManufacturingSchemeChoiceDTO choice) {
        EngineeringBom child = productBomMapper.selectById(choice.getBomId());
        if (child == null) {
            throw new BusinessException("SUB_BOM_NOT_FOUND：制造方案不存在（bomId=" + choice.getBomId() + "）");
        }
        Long childOutputId = child.getOutputId() != null ? child.getOutputId() : child.getProductId();
        if (ManufacturingOutputType.normalize(child.getOutputType()) != ManufacturingOutputType.MATERIAL
                || !Objects.equals(childOutputId, choice.getMaterialId())) {
            throw new BusinessException("SUB_BOM_OUTPUT_MISMATCH：方案产出与物料不一致（materialId="
                    + choice.getMaterialId() + "，bomId=" + choice.getBomId() + "）");
        }
    }

    /** 读取 product.material_scheme_json；坏数据明确报错，不静默返回空导致审批绕过。 */
    private List<ManufacturingSchemeChoiceDTO> readSchemeChoices(String raw) {
        List<ManufacturingSchemeChoiceDTO> out = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        JsonNode root;
        try {
            root = SCHEME_JSON.readTree(raw);
        } catch (Exception ex) {
            throw new BusinessException("SCHEME_JSON_INVALID：产品主方案 JSON 无法解析（保留原数据）: " + ex.getMessage());
        }
        if (!(root instanceof ObjectNode)) {
            throw new BusinessException("SCHEME_JSON_INVALID：产品主方案 JSON 结构不是对象（保留原数据）");
        }
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            long materialId;
            try {
                materialId = Long.parseLong(entry.getKey());
            } catch (NumberFormatException ex) {
                throw new BusinessException("SCHEME_JSON_INVALID：主方案物料键非法（" + entry.getKey() + "）");
            }
            JsonNode value = entry.getValue();
            if (value == null || !value.isObject() || !value.hasNonNull("bomId")) {
                throw new BusinessException("SCHEME_JSON_INVALID：主方案条目缺少 bomId（materialId=" + materialId + "）");
            }
            ManufacturingSchemeChoiceDTO choice = new ManufacturingSchemeChoiceDTO();
            choice.setMaterialId(materialId);
            choice.setBomId(value.path("bomId").asLong());
            choice.setIsPrimary(value.path("isPrimary").asBoolean(false));
            EngineeringBom child = productBomMapper.selectById(choice.getBomId());
            if (child != null) {
                choice.setBomCode(child.getBomCode());
                choice.setBomVersion(child.getBomVersion());
                choice.setApproveStatus(child.getApproveStatus());
                if (!Objects.equals(child.getApproveStatus(), ProductEnums.BomStatus.APPROVED.getValue())) {
                    choice.setWarning("SUB_BOM_NOT_APPROVED：所选方案未批准，父 BOM 提交/批准前需先批准");
                }
            } else {
                choice.setWarning("SUB_BOM_NOT_FOUND：所选方案已不存在");
            }
            out.add(choice);
        }
        return out;
    }

    /** 草稿保存允许引用未批准下层方案，但返回可定位提示。 */
    private List<String> collectSubBomWarnings(Long bomId, List<ManufacturingSchemeChoiceDTO> choices) {
        List<String> warnings = new java.util.ArrayList<>();
        Set<Long> ids = new java.util.LinkedHashSet<>();
        if (bomId != null) {
            for (EngineeringBomItem row : productBomItemMapper.selectList(
                    new LambdaQueryWrapper<EngineeringBomItem>().eq(EngineeringBomItem::getBomId, bomId))) {
                if (row.getSubBomId() != null) ids.add(row.getSubBomId());
            }
        }
        if (choices != null) {
            for (ManufacturingSchemeChoiceDTO choice : choices) {
                if (choice != null && choice.getBomId() != null) ids.add(choice.getBomId());
            }
        }
        for (Long id : ids) {
            EngineeringBom child = productBomMapper.selectById(id);
            if (child == null) {
                warnings.add("SUB_BOM_NOT_FOUND：下层方案不存在（bomId=" + id + "）");
            } else if (!Objects.equals(child.getApproveStatus(), ProductEnums.BomStatus.APPROVED.getValue())) {
                warnings.add("SUB_BOM_NOT_APPROVED：下层方案未批准（bomId=" + id + "），父 BOM 提交/批准前需先批准");
            }
        }
        return warnings;
    }

    @Override
    public List<com.jjx.product.domain.vo.BomMaterialMatchResultVO> matchMaterials(
            List<com.jjx.product.domain.dto.BomMaterialMatchItemDTO> items) {
        List<com.jjx.product.domain.vo.BomMaterialMatchResultVO> results = new java.util.ArrayList<>();
        if (items == null || items.isEmpty()) {
            return results;
        }
        // 一次性加载启用中的物料，归一化后按「名称+规格」建索引（物料档案量级有限，全量装载足够）
        List<com.jjx.inventory.domain.InventoryMaterial> materials = inventoryMaterialMapper.selectList(
                new LambdaQueryWrapper<com.jjx.inventory.domain.InventoryMaterial>()
                        .eq(com.jjx.inventory.domain.InventoryMaterial::getStatus,
                                com.jjx.common.enums.StatusEnum.NORMAL.getCode()));
        java.util.Map<String, List<com.jjx.inventory.domain.InventoryMaterial>> index = new java.util.HashMap<>();
        if (materials != null) {
            for (com.jjx.inventory.domain.InventoryMaterial m : materials) {
                index.computeIfAbsent(matchKey(m.getMaterialName(), m.getSpecification()),
                        k -> new java.util.ArrayList<>()).add(m);
            }
        }
        for (com.jjx.product.domain.dto.BomMaterialMatchItemDTO item : items) {
            com.jjx.product.domain.vo.BomMaterialMatchResultVO r = new com.jjx.product.domain.vo.BomMaterialMatchResultVO();
            r.setIndex(item == null ? null : item.getIndex());
            List<com.jjx.inventory.domain.InventoryMaterial> candidates = java.util.Collections.emptyList();
            if (item != null) {
                candidates = index.getOrDefault(matchKey(item.getName(), item.getSpec()),
                        java.util.Collections.emptyList());
            }
            r.setMatchCount(candidates.size());
            java.util.List<com.jjx.product.domain.vo.BomMaterialMatchResultVO.Candidate> candidatesVo =
                    new java.util.ArrayList<>();
            for (com.jjx.inventory.domain.InventoryMaterial cm : candidates) {
                com.jjx.product.domain.vo.BomMaterialMatchResultVO.Candidate c =
                        new com.jjx.product.domain.vo.BomMaterialMatchResultVO.Candidate();
                c.setMaterialId(cm.getMaterialId());
                c.setMaterialCode(cm.getMaterialCode());
                c.setMaterialName(cm.getMaterialName());
                c.setSpecification(cm.getSpecification());
                c.setSize(cm.getSize());
                c.setUnit(cm.getUnit());
                candidatesVo.add(c);
            }
            r.setCandidates(candidatesVo);
            if (candidates.size() == 1) {
                // 唯一命中：可自动回填
                com.jjx.inventory.domain.InventoryMaterial m = candidates.get(0);
                r.setStatus("MATCHED");
                r.setMaterialId(m.getMaterialId());
                r.setMaterialCode(m.getMaterialCode());
                r.setUnit(m.getUnit());
            } else if (candidates.size() > 1) {
                // 多条同名同规格：不自动填，前端标黄提示手动选择
                r.setStatus("AMBIGUOUS");
            } else {
                r.setStatus("NOT_FOUND");
            }
            results.add(r);
        }
        return results;
    }

    @Override
    public com.jjx.product.domain.vo.BomMaterialBatchResultVO batchLinkMaterials(
            com.jjx.product.domain.dto.BomMaterialBatchLinkDTO dto) {
        if (dto == null || dto.getBomId() == null) {
            throw new BusinessException("BOM不能为空");
        }
        if (dto.getLinks() == null || dto.getLinks().isEmpty()) {
            throw new BusinessException("请选择要关联的行");
        }
        if (dto.getLinks().size() > 200) {
            throw new BusinessException("单次最多处理200行");
        }
        com.jjx.engineering.domain.entity.EngineeringBom bom = productBomMapper.selectById(dto.getBomId());
        if (bom == null) {
            throw new BusinessException("BOM不存在");
        }
        if (!Objects.equals(bom.getApproveStatus(), ProductEnums.BomStatus.REVIEWING.getValue())) {
            throw new BusinessException("只有审核中的BOM才能批量关联物料");
        }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (com.jjx.product.domain.dto.BomMaterialBatchLinkDTO.Link link : dto.getLinks()) {
            if (link == null || link.getItemId() == null) {
                throw new BusinessException("明细ID不能为空");
            }
            if (!seen.add(link.getItemId())) {
                throw new BusinessException("同一明细重复提交：" + link.getItemId());
            }
        }
        com.jjx.product.domain.vo.BomMaterialBatchResultVO result =
                new com.jjx.product.domain.vo.BomMaterialBatchResultVO();
        result.setTotal(dto.getLinks().size());
        for (com.jjx.product.domain.dto.BomMaterialBatchLinkDTO.Link link : dto.getLinks()) {
            com.jjx.product.domain.vo.BomMaterialBatchResultVO.Row row =
                    new com.jjx.product.domain.vo.BomMaterialBatchResultVO.Row();
            row.setItemId(link.getItemId());
            try {
                if (link.getMaterialId() == null || link.getMaterialId() <= 0) {
                    throw new BusinessException("未选择物料");
                }
                com.jjx.engineering.domain.entity.EngineeringBomItem item =
                        productBomItemMapper.selectById(link.getItemId());
                if (item == null) {
                    throw new BusinessException("BOM明细不存在");
                }
                if (!Objects.equals(item.getBomId(), dto.getBomId())) {
                    throw new BusinessException("该明细不属于当前BOM");
                }
                ProductEnums.BomItemReviewStatus from =
                        ProductEnums.BomItemReviewStatus.fromValue(item.getReviewStatus());
                if (from == ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                    throw new BusinessException("待移除行不能关联物料");
                }
                com.jjx.inventory.domain.InventoryMaterial material =
                        inventoryMaterialMapper.selectById(link.getMaterialId());
                if (material == null) {
                    throw new BusinessException("物料不存在");
                }
                if (Objects.equals(item.getMaterialId(), material.getMaterialId())) {
                    row.setSuccess(true);
                    row.setMessage("已关联该物料，无需重复");
                    row.setMaterialId(material.getMaterialId());
                    row.setMaterialCode(material.getMaterialCode());
                } else {
                    item.setMaterialId(material.getMaterialId());
                    item.setMaterialCode(material.getMaterialCode());
                    if (org.apache.commons.lang3.StringUtils.isBlank(item.getUnit())) {
                        item.setUnit(material.getUnit());
                    }
                    if (from == ProductEnums.BomItemReviewStatus.APPROVED) {
                        item.setReviewStatus(ProductEnums.BomItemReviewStatus.PENDING.getValue());
                        clearApproval(item);
                    }
                    productBomItemMapper.updateById(item);
                    reviewFlowService.record("engineering_bom_item", item.getItemId(), "LINK_MATERIAL", "关联物料",
                            from.getLabel(),
                            ProductEnums.BomItemReviewStatus.fromValue(item.getReviewStatus()).getLabel(),
                            buildItemReviewComment(item,
                                    "关联：" + material.getMaterialName() + " / " + material.getMaterialCode()),
                            null);
                    row.setSuccess(true);
                    row.setMessage("已关联");
                    row.setMaterialId(material.getMaterialId());
                    row.setMaterialCode(material.getMaterialCode());
                }
            } catch (Exception e) {
                row.setSuccess(false);
                row.setMessage(e.getMessage() == null ? "关联失败" : e.getMessage());
            }
            result.getRows().add(row);
        }
        int success = 0;
        for (com.jjx.product.domain.vo.BomMaterialBatchResultVO.Row r : result.getRows()) {
            if (r.isSuccess()) {
                success++;
            }
        }
        result.setSuccessCount(success);
        result.setFailedCount(result.getTotal() - success);
        return result;
    }

    /**
     * 匹配键：名称 + 规格 归一化（去空白、全角转半角、忽略大小写）后拼接。
     * 归一化可避免「白卡纸 300g」与「白卡纸300g」这类空格/全角差异导致误判不中。
     */
    private static String matchKey(String name, String spec) {
        return normalizeText(name) + "\u0001" + normalizeText(spec);
    }

    private static String normalizeText(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /**
     * BOM 明细行级审核（dev-20261020-029 D/F）：
     * APPROVE 行通过、REJECT 行驳回、CONFIRM_REMOVE 确认待移除、LINK_MATERIAL 关联已有物料。
     * 每次操作写 review_flow（含操作人、时间与行原文字）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewItem(BomItemReviewDTO dto) {
        if (dto == null || dto.getItemId() == null) {
            throw new BusinessException("明细ID不能为空");
        }
        // 未锁读只用于定位所属 BOM；加锁按「先父行、再明细」顺序，随后重读当前行。
        EngineeringBomItem probe = productBomItemMapper.selectById(dto.getItemId());
        if (probe == null) {
            throw new BusinessException("BOM明细不存在");
        }
        EngineeringBom bom = lockBomForWrite(probe.getBomId());
        if (bom == null) {
            throw new BusinessException("BOM不存在");
        }
        if (!Objects.equals(bom.getApproveStatus(), ProductEnums.BomStatus.REVIEWING.getValue())) {
            throw new BusinessException("只有审核中的BOM才能做行级审核");
        }
        EngineeringBomItem item = probe;
        if (editContractActive()) {
            List<EngineeringBomItem> lockedRows = lockedItems(probe.getBomId());
            assertContentRevision(bom, lockedRows, dto.getExpectedContentRevision(),
                    "BOM_CONTENT_STALE：审核期间BOM内容已被保存修改，请重新加载后再审核");
            // 锁后重读：只改审核相关字段，数量/物料/单位等不被锁前旧实体整行覆盖。
            item = lockedRows.stream()
                    .filter(row -> row != null && dto.getItemId().equals(row.getItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("BOM明细不存在"));
        }
        ProductEnums.BomItemReviewStatus from =
                ProductEnums.BomItemReviewStatus.fromValue(item.getReviewStatus());
        String action = dto.getAction() == null ? "" : dto.getAction().trim().toUpperCase(java.util.Locale.ROOT);
        String operator = currentOperator();
        LocalDateTime now = LocalDateTime.now();
        switch (action) {
            case "APPROVE" -> {
                if (from == ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                    throw new BusinessException("待移除行不能审核通过");
                }
                if (item.getMaterialId() == null || item.getMaterialId() <= 0
                        || inventoryMaterialMapper.selectById(item.getMaterialId()) == null) {
                    throw new BusinessException("该行尚未关联有效物料，请先匹配或新建物料");
                }
                item.setReviewStatus(ProductEnums.BomItemReviewStatus.APPROVED.getValue());
                item.setApprovedFingerprint(fingerprint(item));
                item.setApprovedBy(operator);
                item.setApprovedTime(now);
                item.setRejectReason(null);
            }
            case "REJECT" -> {
                if (StringUtils.isBlank(dto.getRemark())) {
                    throw new BusinessException("驳回原因不能为空");
                }
                if (from == ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                    throw new BusinessException("待移除行请使用确认移除");
                }
                item.setReviewStatus(ProductEnums.BomItemReviewStatus.REJECTED.getValue());
                item.setRejectReason(trimTo500(dto.getRemark()));
                clearApproval(item);
            }
            case "CONFIRM_REMOVE" -> {
                if (from != ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                    throw new BusinessException("只有待移除行才能确认移除");
                }
                item.setRemoveConfirmed(true);
                item.setRemoveConfirmedBy(operator);
                item.setRemoveConfirmedTime(now);
            }
            case "LINK_MATERIAL" -> {
                if (from == ProductEnums.BomItemReviewStatus.PENDING_REMOVE) {
                    throw new BusinessException("待移除行不能关联物料");
                }
                if (dto.getMaterialId() == null || dto.getMaterialId() <= 0) {
                    throw new BusinessException("关联物料不能为空");
                }
                com.jjx.inventory.domain.InventoryMaterial material =
                        inventoryMaterialMapper.selectById(dto.getMaterialId());
                if (material == null) {
                    throw new BusinessException("物料不存在（material_id=" + dto.getMaterialId() + "）");
                }
                item.setMaterialId(material.getMaterialId());
                item.setMaterialCode(material.getMaterialCode());
                if (org.apache.commons.lang3.StringUtils.isBlank(item.getUnit())) {
                    item.setUnit(material.getUnit());
                }
                if (from == ProductEnums.BomItemReviewStatus.APPROVED) {
                    item.setReviewStatus(ProductEnums.BomItemReviewStatus.PENDING.getValue());
                    clearApproval(item);
                }
            }
            case "UNLINK_MATERIAL" -> {
                if (item.getMaterialId() == null) {
                    throw new BusinessException("该行尚未关联物料，无需解除");
                }
                // 解除关联必须受控落定：updateById 会跳过 null，这里显式置空并回读
                LambdaUpdateWrapper<EngineeringBomItem> clearLink = new LambdaUpdateWrapper<>();
                clearLink.eq(EngineeringBomItem::getItemId, item.getItemId())
                        .set(EngineeringBomItem::getMaterialId, null)
                        .set(EngineeringBomItem::getMaterialCode, null);
                productBomItemMapper.update(null, clearLink);
                item.setMaterialId(null);
                item.setMaterialCode(null);
                if (from == ProductEnums.BomItemReviewStatus.APPROVED) {
                    item.setReviewStatus(ProductEnums.BomItemReviewStatus.PENDING.getValue());
                    clearApproval(item);
                }
            }
            default -> throw new BusinessException("未支持的审核动作：" + dto.getAction());
        }
        productBomItemMapper.updateById(item);
        reviewFlowService.record("engineering_bom_item", item.getItemId(), action, itemActionName(action),
                from.getLabel(), ProductEnums.BomItemReviewStatus.fromValue(item.getReviewStatus()).getLabel(),
                buildItemReviewComment(item, dto.getRemark()), null);
        if ("REJECT".equals(action)) {
            // 行驳回后整单退回，工程可修改后重新提交，避免审核中无法修改造成死锁
            UpdateBomStatusDTO statusDto = new UpdateBomStatusDTO();
            statusDto.setBomId(bom.getBomId());
            statusDto.setCurrent(ProductEnums.BomStatus.REVIEWING.getValue());
            statusDto.setTarget(ProductEnums.BomStatus.REJECT.getValue());
            statusDto.setRemark("行#" + item.getItemId() + "驳回：" + dto.getRemark());
            if (updateStatus(statusDto)) {
                reviewFlowService.record("engineering_bom", bom.getBomId(), "REJECT", "行驳回退回",
                        ProductEnums.BomStatus.REVIEWING.getValue(), ProductEnums.BomStatus.REJECT.getValue(),
                        statusDto.getRemark(), null);
            }
        }
    }

    @Override
    public BatchItemReviewResult reviewItemsBatch(BatchItemReviewRequest request) {
        if (request == null || request.bomId() == null || request.itemIds() == null
                || request.itemIds().isEmpty() || !"APPROVE".equals(request.action())) {
            throw new BusinessException("批量审核需提供BOM、明细ID且动作只能为APPROVE");
        }
        var successIds = new java.util.ArrayList<Long>();
        var failed = new java.util.ArrayList<ItemReviewFailure>();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 内容版本基线（dev-20261111-018）：保留「每行 REQUIRES_NEW」原语义，不改成整批原子事务；
        // 基线随成功行滚动，中途被并发保存改动的行判失败，后续行同样失败，不用旧快照覆盖。
        var rolling = new java.util.concurrent.atomic.AtomicReference<String>(request.expectedContentRevision());
        for (Long itemId : new java.util.LinkedHashSet<>(request.itemIds())) {
            try {
                transaction.executeWithoutResult(status -> {
                    if (itemId == null) throw new BusinessException("明细ID不能为空");
                    // 先父行锁、再明细行锁，与保存/审批同一锁顺序。
                    EngineeringBom bom = lockBomForWrite(request.bomId());
                    if (bom == null) throw new BusinessException("BOM不存在");
                    // 锁定后校验，避免并发批量重复写审核日志。
                    EngineeringBomItem item = productBomItemMapper.selectOne(
                            new LambdaQueryWrapper<EngineeringBomItem>()
                                    .eq(EngineeringBomItem::getItemId, itemId).last("FOR UPDATE"));
                    if (item == null) throw new BusinessException("BOM明细不存在");
                    if (!Objects.equals(item.getBomId(), request.bomId())) {
                        throw new BusinessException("该明细不属于指定BOM");
                    }
                    if (item.getMaterialId() == null || item.getMaterialId() <= 0) {
                        throw new BusinessException("该行尚未关联物料");
                    }
                    if (Objects.equals(item.getReviewStatus(), ProductEnums.BomItemReviewStatus.APPROVED.getValue())) {
                        return;
                    }
                    if (!Objects.equals(item.getReviewStatus(), ProductEnums.BomItemReviewStatus.PENDING.getValue())) {
                        throw new BusinessException("只有待审核行才能批量通过");
                    }
                    String expected = rolling.get();
                    if (editContractActive()) {
                        List<EngineeringBomItem> lockedRows = lockedItems(request.bomId());
                        assertContentRevision(bom, lockedRows, expected,
                                "BOM_CONTENT_STALE：该行审核期间BOM内容已被保存修改，请重新加载审核");
                        // 锁后重读当前行：只改审核字段，数量/物料/单位不被旧快照覆盖。
                        item = lockedRows.stream()
                                .filter(row -> row != null && itemId.equals(row.getItemId()))
                                .findFirst()
                                .orElseThrow(() -> new BusinessException("BOM明细不存在"));
                    }
                    BomItemReviewDTO dto = new BomItemReviewDTO();
                    dto.setItemId(itemId);
                    dto.setAction("APPROVE");
                    dto.setRemark(request.remark());
                    dto.setExpectedContentRevision(expected);
                    // 当前行的显式事务覆盖单行逻辑及日志写入，异常由模板回滚。
                    reviewItem(dto);
                    if (editContractActive()) {
                        rolling.set(computeContentRevision(bom, lockedItems(request.bomId())));
                    }
                });
                successIds.add(itemId);
            } catch (Exception e) {
                failed.add(new ItemReviewFailure(itemId,
                        e instanceof BusinessException ? e.getMessage() : "该行审核失败，请稍后重试"));
                if (!(e instanceof BusinessException)) {
                    log.error("BOM明细批量审核失败，itemId=" + itemId, e);
                }
            }
        }
        return new BatchItemReviewResult(successIds, failed);
    }

    private static String itemActionName(String action) {
        return switch (action) {
            case "APPROVE" -> "行审核通过";
            case "REJECT" -> "行审核驳回";
            case "CONFIRM_REMOVE" -> "确认移除";
            case "LINK_MATERIAL" -> "关联物料";
            case "UNLINK_MATERIAL" -> "解除关联物料";
            default -> action;
        };
    }

    private static String trimTo500(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    /** 行原文字留痕：材料名称/规格/单位/用量/编码/物料ID + 审核意见 */
    private String buildItemReviewComment(EngineeringBomItem item, String remark) {
        StringBuilder sb = new StringBuilder(itemOriginalText(item));
        if (org.apache.commons.lang3.StringUtils.isNotBlank(remark)) {
            sb.append("；意见：").append(remark.trim());
        }
        return sb.toString();
    }

    private String itemOriginalText(EngineeringBomItem item) {
        return "原文字：名称=" + nullToEmpty(item.getMaterialName())
                + "；规格=" + nullToEmpty(item.getSpecification())
                + "；单位=" + nullToEmpty(item.getUnit())
                + "；用量=" + (item.getQuantity() == null ? "" : item.getQuantity().toPlainString())
                + "；编码=" + nullToEmpty(item.getMaterialCode())
                + "；物料ID=" + (item.getMaterialId() == null ? "" : item.getMaterialId());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
