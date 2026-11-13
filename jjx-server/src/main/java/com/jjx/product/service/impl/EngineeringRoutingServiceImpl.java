package com.jjx.product.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.product.domain.converter.EngineeringRoutingConverter;
import com.jjx.product.domain.dto.EngineeringRoutingDTO;
import com.jjx.product.domain.dto.RoutingRemarkQueryDTO;
import com.jjx.product.domain.dto.EngineeringRoutingItemDTO;
import com.jjx.product.domain.dto.EngineeringRoutingQueryDTO;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.product.domain.vo.EngineeringRoutingItemVO;
import com.jjx.product.domain.vo.EngineeringRoutingVO;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import com.jjx.product.mapper.EngineeringRoutingMapper;
import com.jjx.product.service.IEngineeringRoutingService;
import com.jjx.system.service.OperLogChangeRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.jjx.system.annotation.Event;

@Slf4j
@Service
@RequiredArgsConstructor
public class EngineeringRoutingServiceImpl extends ServiceImpl<EngineeringRoutingMapper, EngineeringRouting>
        implements IEngineeringRoutingService {

    private static final ObjectMapper ROUTING_DIFF_JSON = new ObjectMapper();

    private final EngineeringRoutingMapper routingMapper;
    /** 2026-09-21（dev-20260921-013）：工程事件改手写 payload（带业务编码）。 */
    private final com.jjx.event.EventPublisher eventPublisher;
    private final EngineeringRoutingItemMapper routingDetailMapper;
    private final EngineeringRoutingConverter routingConverter;
    private final com.jjx.product.mapper.ProductMapper productMapper;
    private final OperLogChangeRecorder changeRecorder;

    /** MATERIAL 路线产出存在性校验；字段注入避免改变既有测试构造参数。 */
    @org.springframework.beans.factory.annotation.Autowired
    private com.jjx.inventory.mapper.InventoryMaterialMapper inventoryMaterialMapper;

    @Override
    public List<RoutingVersionRow> getObjectVersions(String outputType, Long outputId) {
        if (outputId == null) throw new BusinessException("产出对象不能为空");
        return routingMapper.selectAllVersionsByOutput(com.jjx.product.enums.ManufacturingOutputType.normalize(outputType).name(), outputId)
                .stream().map(r -> {
                    RoutingVersionRow row = new RoutingVersionRow(); BeanUtil.copyProperties(r,row);
                    row.setCanSetCurrent(ApproveStatusEnum.APPROVED.getValue().equals(r.getApproveStatus()));
                    return row;
                }).toList();
    }

    @Override
    public List<String> suggestRemarks(RoutingRemarkQueryDTO query) {
        // ID 集合忽略顺序及重复项，单作业与组合使用同一匹配规则。
        String processKey = Optional.ofNullable(query.getProcessIds()).orElseGet(List::of)
                .stream().filter(Objects::nonNull).distinct().sorted()
                .map(String::valueOf).collect(Collectors.joining(","));
        return routingDetailMapper.selectRemarkSuggestions(
                query.getProcessCategory() == null ? "" : query.getProcessCategory().trim(),
                processKey, query.getKeyword() == null ? "" : query.getKeyword().trim());
    }

    /**
     * 工程事件统一发布（2026-09-21 dev-20260921-013 工程批）：手写 payload，bizNo 取工艺路线编号。
     */
    private void publishRoutingEvent(String eventCode, EngineeringRouting routing) {
        if (routing == null) {
            return;
        }
        java.util.Map<String, Object> payload = com.jjx.event.EventPublishSupport.payload(
                "routing", routing.getRoutingId(), routing.getRoutingCode());
        payload.put("routingCode", routing.getRoutingCode());
        payload.put("productCode", routing.getProductCode());
        payload.put("routingVersion", routing.getRoutingVersion());
        com.jjx.event.EventPublishSupport.fireAfterCommit(eventPublisher, eventCode, payload);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineeringRoutingVO createRouting(EngineeringRoutingDTO dto) {
        // 检查编码是否重复
        checkCodeUnique(dto.getRoutingCode(), dto.getRoutingVersion());

        // 产出身份显式化：PRODUCT 兼容 productId，MATERIAL 必须有启用物料档案。
        normalizeOutput(dto);

        // 创建路线
        EngineeringRouting routing = new EngineeringRouting();
        BeanUtil.copyProperties(dto, routing);
        // 2026-08-10 DEV-769：双字段同步，统一语义（version 为空时对齐 routingVersion）
        if (routing.getVersion() == null || routing.getVersion().isEmpty()) {
            routing.setVersion(routing.getRoutingVersion());
        }
        if (routing.getRoutingVersion() == null || routing.getRoutingVersion().isEmpty()) {
            routing.setRoutingVersion(routing.getVersion());
        }
        routing.setApproveStatus(ApproveStatusEnum.DRAFT.getValue());
        routing.setIsCurrent(0);
        routing.setProcessCount(0);
        routing.setTotalLaborHours(BigDecimal.ZERO);
        routing.setTotalMachineHours(BigDecimal.ZERO);

        save(routing);

        // 保存明细
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            saveItems(routing.getRoutingId(), dto.getItems());
            calculateHours(routing.getRoutingId());
        }

        log.info("创建工艺路线成功: {}", routing.getRoutingCode());
        return getRoutingItems(routing.getRoutingId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineeringRoutingVO updateRouting(EngineeringRoutingDTO dto) {
        EngineeringRouting routing = getById(dto.getRoutingId());
        if (routing == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }

        normalizeOutputForUpdate(dto, routing);

        // 检查状态是否可编辑
        if (!ApproveStatusEnum.getByValue(routing.getApproveStatus()).isEditable()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_CANNOT_EDIT);
        }

        // 变更明细：保存前采集（此时库里还是旧值）
        String detailMessage = null;
        try {
            List<String> changes = new ArrayList<>();
            buildRoutingDiff(changes, routing, dto);
            if (!changes.isEmpty()) {
                detailMessage = changeRecorder.toDetailJson(changes);
            }
        } catch (Exception e) {
            log.warn("工艺路线变更明细生成失败: {}", e.getMessage());
        }

        EngineeringRoutingVO vo;
        if (Boolean.TRUE.equals(dto.getBumpVersion())) {
            vo = saveAsNewVersion(routing, dto);
        } else {
            BeanUtil.copyProperties(dto, routing);
            routing.setUpdateTime(LocalDateTime.now());
            updateById(routing);
            routingDetailMapper.deleteByRoutingId(routing.getRoutingId());
            if (dto.getItems() != null && !dto.getItems().isEmpty()) {
                saveItems(routing.getRoutingId(), dto.getItems());
                calculateHours(routing.getRoutingId());
            }
            log.info("更新工艺路线成功: {}", routing.getRoutingCode());
            vo = getRoutingItems(routing.getRoutingId());
        }
        vo.setDetailMessage(detailMessage);
        vo.setBizStatus(ApproveStatusEnum.getByValue(vo.getApproveStatus()).getLabel());
        return vo;
    }

    /**
     * 明细逐项匹配：先保留真实ID，再按完整工序身份及内容匹配无ID旧客户端。
     * 每条旧明细只消费一次，重复工序不会被单值Map覆盖；顺序按实际保存口径比较。
     */
    private void buildRoutingDiff(List<String> changes, EngineeringRouting oldRouting, EngineeringRoutingDTO dto) {
        diffText(changes, "路线名称", oldRouting.getRoutingName(), dto.getRoutingName());
        diffText(changes, "路线编码", oldRouting.getRoutingCode(), dto.getRoutingCode());
        diffText(changes, "说明", oldRouting.getDescription(), dto.getDescription());
        diffText(changes, "备注", oldRouting.getRemark(), dto.getRemark());

        List<EngineeringRoutingItem> oldFlat = routingDetailMapper.selectByRoutingId(oldRouting.getRoutingId());
        List<EngineeringRoutingItem> oldParents = new ArrayList<>();
        Map<Long, List<EngineeringRoutingItem>> oldChildByParent = new HashMap<>();
        for (EngineeringRoutingItem item : oldFlat) {
            if (item.getParentId() == null) {
                oldParents.add(item);
            } else {
                oldChildByParent.computeIfAbsent(item.getParentId(), key -> new ArrayList<>()).add(item);
            }
        }
        List<EngineeringRoutingItemDTO> newParents = dto.getItems() == null ? List.of() : dto.getItems();
        List<EngineeringRoutingItem> matches = matchRoutingItems(oldParents, newParents);
        Set<EngineeringRoutingItem> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Integer> workflowSeqByCategory = new LinkedHashMap<>();
        Map<String, Integer> nextOrderByCategory = new HashMap<>();
        for (int i = 0; i < newParents.size(); i++) {
            EngineeringRoutingItemDTO np = newParents.get(i);
            EngineeringRoutingItem normalized = buildItem(np, oldRouting.getRoutingId());
            String category = normalized.getProcessCategory();
            int workflowSeq = workflowSeqByCategory.computeIfAbsent(category, key -> workflowSeqByCategory.size() + 1);
            int order = nextOrderByCategory.merge(category, 1, Integer::sum);
            EngineeringRoutingItem op = matches.get(i);
            String label = routingItemLabel(np.getProcessName(), category, order);
            if (op == null) {
                changes.add("新增" + label + hoursSuffix(np));
                continue;
            }
            retained.add(op);
            diffRoutingItemFields(changes, label, op, np);
            changeRecorder.diff(changes, label + " 顺序", op.getProcessOrder(), order);
            changeRecorder.diff(changes, label + " 分组顺序", op.getWorkflowSeq(), workflowSeq);

            List<EngineeringRoutingItem> oldChildren = oldChildByParent.getOrDefault(op.getItemId(), List.of());
            List<EngineeringRoutingItemDTO> newChildren = np.getChildren() == null ? List.of() : np.getChildren();
            List<EngineeringRoutingItem> childMatches = matchRoutingItems(oldChildren, newChildren);
            Set<EngineeringRoutingItem> retainedChildren = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int j = 0; j < newChildren.size(); j++) {
                EngineeringRoutingItemDTO nc = newChildren.get(j);
                EngineeringRoutingItem oc = childMatches.get(j);
                String childLabel = label + " 作业项:" + itemName(nc) + "（第" + (j + 1) + "项）";
                if (oc == null) {
                    changes.add(label + " 新增作业项:" + itemName(nc));
                } else {
                    retainedChildren.add(oc);
                    diffRoutingItemFields(changes, childLabel, oc, nc);
                    changeRecorder.diff(changes, childLabel + " 顺序", oldChildren.indexOf(oc) + 1, j + 1);
                }
            }
            for (EngineeringRoutingItem oc : oldChildren) {
                if (!retainedChildren.contains(oc)) changes.add(label + " 移除作业项:" + itemName(oc));
            }
        }
        for (EngineeringRoutingItem op : oldParents) {
            if (!retained.contains(op)) {
                changes.add("移除" + routingItemLabel(op.getProcessName(), op.getProcessCategory(), op.getProcessOrder()));
            }
        }
    }

    private List<EngineeringRoutingItem> matchRoutingItems(List<EngineeringRoutingItem> oldItems,
                                                           List<EngineeringRoutingItemDTO> newItems) {
        List<EngineeringRoutingItem> matches = new ArrayList<>(Collections.nCopies(newItems.size(), null));
        boolean[] used = new boolean[oldItems.size()];
        // 先为所有带真实ID的明细保留旧行，再匹配无ID项，防止重复工序抢占别人的旧行。
        for (int pass = 0; pass < 3; pass++) {
            for (int n = 0; n < newItems.size(); n++) {
                if (matches.get(n) != null) continue;
                EngineeringRoutingItemDTO next = newItems.get(n);
                for (int o = 0; o < oldItems.size(); o++) {
                    if (used[o]) continue;
                    EngineeringRoutingItem previous = oldItems.get(o);
                    boolean match;
                    if (pass == 0) {
                        match = next.getItemId() != null && next.getItemId() > 0
                                && Objects.equals(previous.getItemId(), next.getItemId());
                    } else {
                        match = sameRoutingItemIdentity(previous, next);
                        if (match && pass == 1) {
                            List<String> fieldChanges = new ArrayList<>();
                            diffRoutingItemFields(fieldChanges, "", previous, next);
                            match = fieldChanges.isEmpty();
                        }
                    }
                    if (match) {
                        matches.set(n, previous);
                        used[o] = true;
                        break;
                    }
                }
            }
        }
        return matches;
    }

    private boolean sameRoutingItemIdentity(EngineeringRoutingItem oldItem, EngineeringRoutingItemDTO next) {
        EngineeringRoutingItem newItem = buildItem(next, oldItem.getRoutingId());
        if (!Objects.equals(textValue(oldItem.getMajorCategory()), textValue(newItem.getMajorCategory()))
                || !Objects.equals(textValue(oldItem.getProcessCategory()), textValue(newItem.getProcessCategory()))) {
            return false;
        }
        if (oldItem.getProcessId() != null || newItem.getProcessId() != null) {
            return Objects.equals(oldItem.getProcessId(), newItem.getProcessId());
        }
        return Objects.equals(textValue(oldItem.getProcessName()), textValue(newItem.getProcessName()));
    }

    private void diffRoutingItemFields(List<String> changes, String label,
                                       EngineeringRoutingItem oldItem, EngineeringRoutingItemDTO dto) {
        // 对新值应用与保存一致的空值/无效工序ID规范化，不比较临时ID、时间戳或标准工序展示字段。
        EngineeringRoutingItem next = buildItem(dto, oldItem.getRoutingId());
        changeRecorder.diff(changes, label + " 关联标准工序", oldItem.getProcessId(), next.getProcessId());
        diffText(changes, label + " 名称", oldItem.getProcessName(), next.getProcessName());
        diffText(changes, label + " 大类", oldItem.getMajorCategory(), next.getMajorCategory());
        diffText(changes, label + " 类别", oldItem.getProcessCategory(), next.getProcessCategory());
        changeRecorder.diffDecimal(changes, label + " 人工工时", hoursValue(oldItem.getCustomLaborHours()), hoursValue(next.getCustomLaborHours()));
        changeRecorder.diffDecimal(changes, label + " 机器工时", hoursValue(oldItem.getCustomMachineHours()), hoursValue(next.getCustomMachineHours()));
        diffText(changes, label + " 说明", oldItem.getDescription(), next.getDescription());
        diffText(changes, label + " 作业说明", oldItem.getWorkInstruction(), next.getWorkInstruction());
        diffText(changes, label + " 备注", oldItem.getRemark(), next.getRemark());
        changeRecorder.diff(changes, label + " 下标", oldItem.getIndexNumber(), next.getIndexNumber());
        diffText(changes, label + " 前置依赖", oldItem.getPrecondition(), next.getPrecondition());
        diffText(changes, label + " 前置依赖名称", oldItem.getPreconditionDisplay(), next.getPreconditionDisplay());
        changeRecorder.diff(changes, label + " 可选", oldItem.getIsOptional(), next.getIsOptional());
        diffRoutingParameters(changes, label, oldItem.getCustomProcessParams(), next.getCustomProcessParams());
    }

    private void diffRoutingParameters(List<String> changes, String label, String oldValue, String newValue) {
        JsonNode oldNode = routingParameters(oldValue);
        JsonNode newNode = routingParameters(newValue);
        if (oldNode.isObject() && newNode.isObject()) {
            Set<String> keys = new TreeSet<>();
            oldNode.fieldNames().forEachRemaining(keys::add);
            newNode.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                JsonNode before = oldNode.get(key);
                JsonNode after = newNode.get(key);
                if (!sameParameterValue(before, after)) {
                    String name = switch (key) {
                        case "printName" -> "印刷名称";
                        case "colorNo" -> "色号";
                        case "inkNo" -> "油墨编号";
                        case "screenNo" -> "网框编号";
                        default -> "工艺参数[" + key + "]";
                    };
                    changeRecorder.diff(changes, label + " " + name, parameterDisplay(before), parameterDisplay(after));
                }
            }
        } else if (!sameParameterValue(oldNode, newNode)) {
            changeRecorder.diff(changes, label + " 工艺参数", parameterDisplay(oldNode), parameterDisplay(newNode));
        }
    }

    private JsonNode routingParameters(String value) {
        if (!StringUtils.hasText(value)) return ROUTING_DIFF_JSON.createObjectNode();
        try {
            JsonNode node = ROUTING_DIFF_JSON.readTree(value);
            return node == null || node.isNull() ? ROUTING_DIFF_JSON.createObjectNode() : node;
        } catch (JsonProcessingException e) {
            // 兼容旧非JSON参数：原文比较，不因解析失败丢掉整次变更记录。
            return TextNode.valueOf(value);
        }
    }

    private boolean sameParameterValue(JsonNode before, JsonNode after) {
        if (parameterDisplay(before) == null || parameterDisplay(after) == null) {
            return parameterDisplay(before) == null && parameterDisplay(after) == null;
        }
        return before.equals((left, right) -> left.isNumber() && right.isNumber()
                ? left.decimalValue().compareTo(right.decimalValue()) : left.equals(right) ? 0 : 1, after);
    }

    private String parameterDisplay(JsonNode value) {
        if (value == null || value.isNull() || (value.isTextual() && value.textValue().isBlank())) return null;
        return value.isTextual() ? value.textValue() : value.toString();
    }

    private void diffText(List<String> changes, String label, String before, String after) {
        changeRecorder.diff(changes, label, textValue(before), textValue(after));
    }

    private String textValue(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private BigDecimal hoursValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String routingItemLabel(String name, String category, Integer order) {
        String categoryName = Arrays.stream(com.jjx.product.enums.ProcessCategoryEnum.values())
                .filter(item -> Objects.equals(item.getCode(), category))
                .map(com.jjx.product.enums.ProcessCategoryEnum::getLabel)
                .findFirst().orElse(StringUtils.hasText(category) ? category : "未分类");
        return "工序:" + (StringUtils.hasText(name) ? name : "工序")
                + "（" + categoryName + "第" + order + "道）";
    }

    private String itemName(EngineeringRoutingItemDTO item) {
        return StringUtils.hasText(item.getProcessName()) ? item.getProcessName() : "工序";
    }

    private String itemName(EngineeringRoutingItem item) {
        return StringUtils.hasText(item.getProcessName()) ? item.getProcessName() : "工序";
    }

    /** 新增工序后缀：custom 工时非空时带 （人工X/机器Y），否则空串 */
    private String hoursSuffix(EngineeringRoutingItemDTO it) {
        String suffix = "";
        if (it.getCustomLaborHours() != null) {
            suffix += "人工" + it.getCustomLaborHours().stripTrailingZeros().toPlainString();
        }
        if (it.getCustomMachineHours() != null) {
            suffix += (suffix.isEmpty() ? "" : "/") + "机器"
                    + it.getCustomMachineHours().stripTrailingZeros().toPlainString();
        }
        return suffix.isEmpty() ? "" : "（" + suffix + "）";
    }

    /**
     * 自动升版保存：旧版本失效（is_current=0），新建版本（版本号+1、is_current=1、parent指向旧版本），
     * 明细写入新版本，同步 product.current_routing_version
     */
    private EngineeringRoutingVO saveAsNewVersion(EngineeringRouting oldRouting, EngineeringRoutingDTO dto) {
        // 1. 计算新版本号：当前版本主号+1（V1.0 → V2.0）
        String outputType = com.jjx.product.enums.ManufacturingOutputType.normalize(oldRouting.getOutputType()).name();
        Long outputId = oldRouting.getOutputId() != null ? oldRouting.getOutputId() : oldRouting.getProductId();
        List<String> versions = list(new LambdaQueryWrapper<EngineeringRouting>()
                        .eq(EngineeringRouting::getOutputType, outputType)
                        .eq(EngineeringRouting::getOutputId, outputId))
                .stream()
                .map(r -> r.getVersion() != null ? r.getVersion() : r.getRoutingVersion())
                .collect(Collectors.toList());
        String newVersion = computeNextRoutingVersion(versions);

        // 2. 旧版本失效（该产品所有版本 is_current=0，再设新版本为当前）
        routingMapper.setAllNotCurrentByOutput(outputType, outputId);
        oldRouting.setIsCurrent(0);
        oldRouting.setUpdateTime(LocalDateTime.now());
        updateById(oldRouting);

        // 3. 新建版本（parent 指向旧版本）
        EngineeringRouting newRouting = new EngineeringRouting();
        BeanUtil.copyProperties(dto, newRouting);
        newRouting.setRoutingId(null);
        newRouting.setVersion(newVersion);
        newRouting.setRoutingVersion(newVersion);
        newRouting.setIsCurrent(1);
        newRouting.setParentRoutingId(oldRouting.getRoutingId());
        newRouting.setOutputType(outputType);
        newRouting.setOutputId(outputId);
        newRouting.setProductId(oldRouting.getProductId());
        newRouting.setApproveStatus(ApproveStatusEnum.DRAFT.getValue());
        newRouting.setProcessCount(0);
        newRouting.setTotalLaborHours(BigDecimal.ZERO);
        newRouting.setTotalMachineHours(BigDecimal.ZERO);
        // 变更说明记录到 remark（自动生成 + 用户输入拼接）
        String changeNote = dto.getChangeNote();
        String oldVer = oldRouting.getVersion() != null ? oldRouting.getVersion() : oldRouting.getRoutingVersion();
        String remark = newVersion + " 变更："
                + (StringUtils.hasText(changeNote) ? changeNote : "工序内容调整")
                + "（由 " + oldVer + " 升版）";
        newRouting.setRemark(remark);
        newRouting.setUpdateTime(LocalDateTime.now());
        save(newRouting);

        // 4. 明细写入新版本
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            saveItems(newRouting.getRoutingId(), dto.getItems());
            calculateHours(newRouting.getRoutingId());
        }

        // 5. 同步产品表 current_routing_version + 指针（DEV-771：发布校验用 current_route_id）
        if (com.jjx.product.enums.ManufacturingOutputType.PRODUCT.name().equals(outputType)) {
            com.jjx.product.domain.entity.Product product = productMapper.selectById(outputId);
            if (product != null) {
                product.setCurrentRoutingVersion(newVersion);
                product.setCurrentRouteId(newRouting.getRoutingId());
                productMapper.updateById(product);
            }
        }

        log.info("工艺路线自动升版: {} V{} -> V{}（parent={}）",
                oldRouting.getRoutingCode(), oldRouting.getRoutingVersion(), newVersion, oldRouting.getRoutingId());
        return getRoutingItems(newRouting.getRoutingId());
    }

    /**
     * 计算下一个版本号（V1.0 → V2.0，取所有版本主号最大值+1）
     * 2026-08-10 DEV-765：统一走公共工具类
     */
    private String computeNextRoutingVersion(List<String> existingVersions) {
        return com.jjx.common.utils.VersionUtils.next(existingVersions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineeringRoutingVO copyAsNewVersion(Long routingId, String newVersion) {
        EngineeringRouting oldRouting = getById(routingId);
        if (oldRouting == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }

        String type = com.jjx.product.enums.ManufacturingOutputType.normalize(oldRouting.getOutputType()).name();
        Long outputId = oldRouting.getOutputId() != null ? oldRouting.getOutputId() : oldRouting.getProductId();
        var versions = routingMapper.lockObjectVersions(type,outputId);
        oldRouting = versions.stream().filter(r -> routingId.equals(r.getRoutingId())).findFirst()
                .orElseThrow(() -> new BusinessException("原工艺路线已变化，请刷新后复制"));
        if (!StringUtils.hasText(newVersion)) newVersion = com.jjx.product.service.IEngineeringBomService.nextCopyVersion(
                versions.stream().map(EngineeringRouting::getRoutingVersion).toList());
        newVersion = newVersion.trim();
        if (newVersion.length() > 20) throw new BusinessException("版本号不能超过20个字符");
        for (var version : versions) {
            if (newVersion.equalsIgnoreCase(version.getRoutingVersion())) throw new BusinessException("同产品版本号已存在：" + newVersion);
        }

        // 复制路线
        EngineeringRouting newRouting = new EngineeringRouting();
        BeanUtil.copyProperties(oldRouting, newRouting);
        newRouting.setRoutingId(null);
        newRouting.setRoutingVersion(newVersion);
        newRouting.setVersion(newVersion); // 2026-08-10 DEV-769：双字段同步，统一语义
        newRouting.setApproveStatus(ApproveStatusEnum.DRAFT.getValue());
        newRouting.setIsCurrent(0);
        newRouting.setParentRoutingId(routingId);
        newRouting.setCreateBy(null); newRouting.setCreateTime(null); newRouting.setUpdateBy(null); newRouting.setUpdateTime(null);
        save(newRouting);

        // 复制明细（保留组合信息与前置工序）
        // dev-20261013-032：复制时把 parent_id 由旧 itemId 重映射为新 itemId，避免组合工序子行悬挂。
        copyRoutingDetails(routingDetailMapper, routingId, newRouting.getRoutingId());

        calculateHours(newRouting.getRoutingId());

        log.info("复制工艺路线成功: {} -> {}", oldRouting.getRoutingCode(), newVersion);
        return getRoutingItems(newRouting.getRoutingId());
    }

    /**
     * 复制路线明细，并把 parent_id 从旧 itemId 重映射为新 itemId（dev-20261013-032）。
     * 父子结构下 parent_id 指向本表 detail_id：先落为根（parent_id=null）建立旧→新映射，再回填新父ID，
     * 避免子作业项沿用旧父ID而悬挂（组合工序在新路线中丢失/错位）。
     */
    static Map<Long, Long> copyRoutingDetails(EngineeringRoutingItemMapper mapper, Long sourceRoutingId, Long newRoutingId) {
        List<EngineeringRoutingItem> oldDetails = mapper.selectByRoutingId(sourceRoutingId);
        Map<Long, Long> idMap = new HashMap<>();
        List<EngineeringRoutingItem> newDetails = new ArrayList<>();
        for (EngineeringRoutingItem detail : oldDetails) {
            EngineeringRoutingItem newDetail = new EngineeringRoutingItem();
            BeanUtil.copyProperties(detail, newDetail);
            newDetail.setItemId(null);
            newDetail.setRoutingId(newRoutingId);
            newDetail.setParentId(null);
            mapper.insert(newDetail);
            idMap.put(detail.getItemId(), newDetail.getItemId());
            newDetails.add(newDetail);
        }
        for (int i = 0; i < oldDetails.size(); i++) {
            Long oldParentId = oldDetails.get(i).getParentId();
            if (oldParentId == null) continue;
            Long newParentId = idMap.get(oldParentId);
            if (newParentId == null) continue; // 旧父行缺失：保持为根，避免悬挂
            EngineeringRoutingItem patch = new EngineeringRoutingItem();
            patch.setItemId(newDetails.get(i).getItemId());
            patch.setParentId(newParentId);
            mapper.updateById(patch);
        }
        return idMap;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCurrentVersion(Long routingId) { setCurrentVersion(routingId, null); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCurrentVersion(Long routingId, Long expectedCurrentId) {
        EngineeringRouting snapshot = getById(routingId);
        if (snapshot == null) throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        String type = com.jjx.product.enums.ManufacturingOutputType.normalize(snapshot.getOutputType()).name();
        Long outputId = snapshot.getOutputId() != null ? snapshot.getOutputId() : snapshot.getProductId();
        if (outputId == null) throw new BusinessException("路线产出对象不能为空");
        Long observed = expectedCurrentId;
        if (observed == null) observed = routingMapper.selectAllVersionsByOutput(type, outputId).stream()
                .filter(r -> Integer.valueOf(1).equals(r.getIsCurrent())).map(EngineeringRouting::getRoutingId).findFirst().orElse(0L);
        var locked = routingMapper.lockObjectVersions(type, outputId);
        var routing = locked.stream().filter(r -> routingId.equals(r.getRoutingId())).findFirst()
                .orElseThrow(() -> new BusinessException("工艺路线版本已变化，请刷新"));
        var before = locked.stream().filter(r -> Integer.valueOf(1).equals(r.getIsCurrent())).findFirst().orElse(null);
        Long actual = before == null ? 0L : before.getRoutingId();
        if (!Objects.equals(observed,actual) || Integer.valueOf(1).equals(routing.getIsCurrent())) {
            throw new BusinessException(409,"当前版本已被设置或切换，请刷新版本列表后重试");
        }
        if (!ApproveStatusEnum.APPROVED.getValue().equals(routing.getApproveStatus())) {
            throw new BusinessException("只有已批准的工艺路线才能设为当前版本");
        }
        routingMapper.clearObjectCurrent(type,outputId);
        if (routingMapper.promoteCurrent(routingId,ApproveStatusEnum.APPROVED.getValue()) != 1) {
            throw new BusinessException(409,"工艺路线状态已变化，设置当前版本失败，请刷新");
        }
        if ("PRODUCT".equals(type)) {
            var product = productMapper.selectById(outputId);
            if (product != null) {
                product.setCurrentRouteId(routingId); product.setCurrentRoutingVersion(routing.getRoutingVersion());
                productMapper.updateById(product);
            }
        }
        changeRecorder.recordUpdate("工艺路线管理","routing.set-current","routing",String.valueOf(routingId),null,
                ApproveStatusEnum.APPROVED.getLabel(),com.jjx.common.constant.LogActions.ROUTING_SET_CURRENT,
                List.of("当前版本:" + (before == null ? "无" : before.getRoutingVersion() + "(#" + before.getRoutingId() + ")")
                        + "→" + routing.getRoutingVersion() + "(#" + routingId + ")"));
        publishRoutingEvent("product.routing.version_changed",routing);
    }

    @Override
    public String suggestCopyVersion(Long routingId) {
        var route = getById(routingId);
        if (route == null) throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        Long outputId = route.getOutputId() != null ? route.getOutputId() : route.getProductId();
        return com.jjx.product.service.IEngineeringBomService.nextCopyVersion(
                routingMapper.selectAllVersionsByOutput(com.jjx.product.enums.ManufacturingOutputType.normalize(route.getOutputType()).name(),outputId)
                        .stream().map(EngineeringRouting::getRoutingVersion).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidVersion(Long routingId) {
        var snapshot = getById(routingId);
        if (snapshot == null) throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        String type = com.jjx.product.enums.ManufacturingOutputType.normalize(snapshot.getOutputType()).name();
        Long outputId = snapshot.getOutputId() != null ? snapshot.getOutputId() : snapshot.getProductId();
        var route = routingMapper.lockObjectVersions(type,outputId).stream().filter(r -> routingId.equals(r.getRoutingId()))
                .findFirst().orElseThrow(() -> new BusinessException("工艺路线不存在"));
        if (!Integer.valueOf(1).equals(route.getIsCurrent()) || !ApproveStatusEnum.APPROVED.getValue().equals(route.getApproveStatus())) {
            throw new BusinessException("只有已生效的工艺路线版本可以作废");
        }
        route.setIsCurrent(0); route.setApproveStatus(ApproveStatusEnum.CANCELLED.getValue()); updateById(route);
        if ("PRODUCT".equals(type)) productMapper.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.jjx.product.domain.entity.Product>()
                .eq(com.jjx.product.domain.entity.Product::getProductId,outputId)
                .eq(com.jjx.product.domain.entity.Product::getCurrentRouteId,routingId)
                .set(com.jjx.product.domain.entity.Product::getCurrentRouteId,null)
                .set(com.jjx.product.domain.entity.Product::getCurrentRoutingVersion,null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVersion(Long routingId) {
        var route = routingMapper.selectForUpdate(routingId);
        if (route == null) throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        if (!ApproveStatusEnum.DRAFT.getValue().equals(route.getApproveStatus())) throw new BusinessException("只有草稿版本可以删除");
        routingDetailMapper.delete(new LambdaQueryWrapper<EngineeringRoutingItem>().eq(EngineeringRoutingItem::getRoutingId,routingId));
        removeById(routingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitApprove(Long routingId) {
        EngineeringRouting routing = routingMapper.selectForUpdate(routingId);
        if (routing == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }

        if (routing.getApproveStatus() != ApproveStatusEnum.DRAFT.getValue()
                && routing.getApproveStatus() != ApproveStatusEnum.REJECTED.getValue()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_ALREADY_APPROVED);
        }

        routing.setApproveStatus(ApproveStatusEnum.PENDING.getValue());
        updateById(routing);

        log.info("提交审批成功: {}", routing.getRoutingCode());
        publishRoutingEvent("product.routing.submitted", routing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long routingId, String remark) {
        EngineeringRouting routing = routingMapper.selectForUpdate(routingId);
        if (routing == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }

        if (routing.getApproveStatus() != ApproveStatusEnum.PENDING.getValue()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_ALREADY_APPROVED);
        }

        routing.setApproveStatus(ApproveStatusEnum.APPROVED.getValue());
        updateById(routing);

        log.info("审批通过: {}", routing.getRoutingCode());
        publishRoutingEvent("product.routing.approved", routing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long routingId, String remark) {
        EngineeringRouting routing = routingMapper.selectForUpdate(routingId);
        if (routing == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }

        if (routing.getApproveStatus() != ApproveStatusEnum.PENDING.getValue()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_ALREADY_APPROVED);
        }

        routing.setApproveStatus(ApproveStatusEnum.REJECTED.getValue());
        updateById(routing);

        log.info("审批驳回: {}", routing.getRoutingCode());
        publishRoutingEvent("product.routing.rejected", routing);
    }

    @Override
    public EngineeringRoutingVO getCurrentByProductId(Long productId) {
        EngineeringRouting routing = routingMapper.selectCurrentByProductId(productId);
        if (routing == null) {
            return null;
        }
        return getRoutingItems(routing.getRoutingId());
    }

    @Override
    public List<EngineeringRoutingVO> getAllVersionsByProductId(Long productId) {
        List<EngineeringRouting> routings = routingMapper.selectAllVersionsByProductId(productId);
        return routings.stream()
                .map(r -> getRoutingItems(r.getRoutingId()))
                .collect(Collectors.toList());
    }

    @Override
    public EngineeringRoutingVO getRoutingItems(Long routingId) {
        EngineeringRouting routing = getById(routingId);
        if (routing == null) {
            return null;
        }

        EngineeringRoutingVO vo = new EngineeringRoutingVO();
        BeanUtil.copyProperties(routing, vo);

        // 设置是否当前版本名称
        vo.setIsCurrentName(routing.getIsCurrent() == 1 ? "是" : "否");

        // 获取明细（按 group_order, process_order 排序）
        List<EngineeringRoutingItemVO> allItems = routingDetailMapper.selectVOsByRoutingId(routingId);
        // 2026-09-05 父子结构：父行 = 工序；子行挂父行 children 组树；纯平铺旧数据（无子行）原样返回
        boolean hasChildRows = allItems.stream().anyMatch(i -> i.getParentId() != null);
        List<EngineeringRoutingItemVO> items;
        if (hasChildRows) {
            Map<Long, List<EngineeringRoutingItemVO>> childMap = allItems.stream()
                .filter(i -> i.getParentId() != null)
                .collect(Collectors.groupingBy(EngineeringRoutingItemVO::getParentId));
            items = allItems.stream().filter(i -> i.getParentId() == null).collect(Collectors.toList());
            for (EngineeringRoutingItemVO parent : items) {
                List<EngineeringRoutingItemVO> children = childMap.get(parent.getItemId());
                if (children != null) {
                    parent.setChildren(children);
                }
            }
        } else {
            items = allItems;
        }
        vo.setItems(items);

        // 计算组合汇总信息
        Map<Long, List<EngineeringRoutingItemVO>> groupMap = items.stream()
            .filter(item -> item.getGroupId() != null)
            .collect(Collectors.groupingBy(EngineeringRoutingItemVO::getGroupId));

        if (!groupMap.isEmpty()) {
            List<EngineeringRoutingVO.GroupSummary> summaries = new ArrayList<>();
            groupMap.forEach((groupId, groupItems) -> {
                EngineeringRoutingVO.GroupSummary summary = new EngineeringRoutingVO.GroupSummary();
                summary.setGroupId(groupId);
                summary.setGroupOrder(groupItems.get(0).getGroupOrder());
                summary.setGroupName(groupItems.get(0).getGroupName());
                summary.setTotalLaborHours(groupItems.stream()
                    .map(i -> i.getCustomLaborHours() != null ? i.getCustomLaborHours() :
                         i.getStandardLaborHours() != null ? i.getStandardLaborHours() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
                summary.setTotalMachineHours(groupItems.stream()
                    .map(i -> i.getCustomMachineHours() != null ? i.getCustomMachineHours() :
                         i.getStandardMachineHours() != null ? i.getStandardMachineHours() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
                summary.setProcessCount(groupItems.size());
                summaries.add(summary);
            });
            vo.setGroupSummaries(summaries);
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateHours(Long routingId) {
        List<EngineeringRoutingItem> details = routingDetailMapper.selectByRoutingId(routingId);
        // 2026-09-05 父子结构：工时/工序数只统计父行（组合父行工时已含子作业和，子行不重复计）
        List<EngineeringRoutingItem> parents = details.stream()
                .filter(d -> d.getParentId() == null)
                .collect(java.util.stream.Collectors.toList());
        if (parents.isEmpty()) {
            parents = details; // 纯平铺旧数据兑底
        }

        BigDecimal totalLabor = BigDecimal.ZERO;
        BigDecimal totalMachine = BigDecimal.ZERO;

        for (EngineeringRoutingItem detail : parents) {
            if (detail.getCustomLaborHours() != null) {
                totalLabor = totalLabor.add(detail.getCustomLaborHours());
            }
            if (detail.getCustomMachineHours() != null) {
                totalMachine = totalMachine.add(detail.getCustomMachineHours());
            }
        }

        EngineeringRouting routing = getById(routingId);
        routing.setTotalLaborHours(totalLabor);
        routing.setTotalMachineHours(totalMachine);
        routing.setProcessCount(parents.size());
        updateById(routing);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<com.jjx.product.service.IEngineeringBomService.ProductVersionRow> getProductPage(EngineeringRoutingQueryDTO query) {
        var paging = new com.jjx.common.core.page.PageQuery();
        paging.setPageNum(query.getPageNum()); paging.setPageSize(query.getPageSize());
        var page = routingMapper.selectProductPage(new Page<>(paging.getPageNum(), paging.getPageSize()), query);
        for (var row : page.getRecords()) {
            var versions = routingMapper.selectAllVersionsByOutput(row.getOutputType(), row.getOutputId());
            versions.stream().max((a,b) -> {
                int result = com.jjx.product.service.IEngineeringBomService.compareVersions(a.getRoutingVersion(), b.getRoutingVersion());
                return result != 0 ? result : a.getRoutingId().compareTo(b.getRoutingId());
            }).ifPresent(latest -> { row.setLatestId(latest.getRoutingId()); row.setLatestVersion(latest.getRoutingVersion()); });
            versions.stream().filter(r -> Integer.valueOf(1).equals(r.getIsCurrent()))
                    .filter(r -> ApproveStatusEnum.APPROVED.getValue().equals(r.getApproveStatus()))
                    .findFirst().ifPresent(current -> {
                        row.setCurrentId(current.getRoutingId()); row.setCurrentVersion(current.getRoutingVersion());
                    });
        }
        return PageResult.of(page, page.getRecords());
    }

    @Override
    public PageResult<EngineeringRoutingVO> pageQuery(EngineeringRoutingQueryDTO queryDTO) {
        LambdaQueryWrapper<EngineeringRouting> wrapper = new LambdaQueryWrapper<>();

        // 构建查询条件
        if (StringUtils.hasText(queryDTO.getRoutingCode())) {
            wrapper.like(EngineeringRouting::getRoutingCode, queryDTO.getRoutingCode());
        }
        if (StringUtils.hasText(queryDTO.getRoutingName())) {
            wrapper.like(EngineeringRouting::getRoutingName, queryDTO.getRoutingName());
        }
        if (queryDTO.getProductId() != null) {
            wrapper.eq(EngineeringRouting::getProductId, queryDTO.getProductId());
        }
        if (StringUtils.hasText(queryDTO.getProductCode())) {
            wrapper.like(EngineeringRouting::getProductCode, queryDTO.getProductCode());
        }
        if (queryDTO.getApproveStatus() != null) {
            wrapper.eq(EngineeringRouting::getApproveStatus, queryDTO.getApproveStatus());
        }
        if (queryDTO.getIsCurrent() != null) {
            wrapper.eq(EngineeringRouting::getIsCurrent, queryDTO.getIsCurrent());
        }

        // 排序
        if (StringUtils.hasText(queryDTO.getOrderByColumn())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getIsAsc());
            switch (queryDTO.getOrderByColumn()) {
                case "routingId":
                    wrapper.orderBy(true, isAsc, EngineeringRouting::getRoutingId);
                    break;
                case "routingCode":
                    wrapper.orderBy(true, isAsc, EngineeringRouting::getRoutingCode);
                    break;
                case "createTime":
                    wrapper.orderBy(true, isAsc, EngineeringRouting::getCreateTime);
                    break;
                default:
                    wrapper.orderByDesc(EngineeringRouting::getCreateTime);
            }
        } else {
            wrapper.orderByDesc(EngineeringRouting::getCreateTime);
        }

        Page<EngineeringRouting> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<EngineeringRouting> resultPage = page(page, wrapper);

        // 转换为VO
        List<EngineeringRouting> records = resultPage.getRecords();
        List<EngineeringRoutingVO> voList = routingConverter.toVOList(records);
        return PageResult.build(voList,resultPage.getTotal());
    }


    /** 产出身份归一化：PRODUCT 兼容 productId；MATERIAL 校验启用物料档案。 */
    private void normalizeOutput(EngineeringRoutingDTO dto) {
        com.jjx.product.enums.ManufacturingOutputType type;
        try {
            type = com.jjx.product.enums.ManufacturingOutputType.normalize(dto.getOutputType());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
        Long outputId = dto.getOutputId() != null ? dto.getOutputId() : dto.getProductId();
        if (outputId == null) {
            throw new BusinessException("路线产出对象不能为空");
        }
        if (type == com.jjx.product.enums.ManufacturingOutputType.PRODUCT) {
            dto.setProductId(outputId);
        } else {
            com.jjx.inventory.domain.InventoryMaterial material = inventoryMaterialMapper == null
                    ? null : inventoryMaterialMapper.selectById(outputId);
            if (material == null) {
                throw new BusinessException("MATERIAL 路线产出物料不存在（material_id=" + outputId + "）");
            }
            if (material.getStatus() != null && material.getStatus() != 1) {
                throw new BusinessException("MATERIAL 路线产出物料已停用（material_id=" + outputId + "）");
            }
            dto.setProductId(null);
            dto.setProductCode(null);
            dto.setProductName(null);
        }
        dto.setOutputType(type.name());
        dto.setOutputId(outputId);
    }

    /** 已保存路线的产出身份不可原地改变；未传时沿用库中身份。 */
    private void normalizeOutputForUpdate(EngineeringRoutingDTO dto, EngineeringRouting existing) {
        com.jjx.product.enums.ManufacturingOutputType existingType =
                com.jjx.product.enums.ManufacturingOutputType.normalize(existing.getOutputType());
        Long existingId = existing.getOutputId() != null ? existing.getOutputId() : existing.getProductId();
        com.jjx.product.enums.ManufacturingOutputType type =
                (dto.getOutputType() == null || dto.getOutputType().isBlank())
                        ? existingType : com.jjx.product.enums.ManufacturingOutputType.normalize(dto.getOutputType());
        Long outputId = dto.getOutputId() != null ? dto.getOutputId()
                : (type == com.jjx.product.enums.ManufacturingOutputType.PRODUCT && dto.getProductId() != null
                ? dto.getProductId() : existingId);
        if (type != existingType || !Objects.equals(outputId, existingId)) {
            throw new BusinessException("路线产出身份不可修改（routingId=" + existing.getRoutingId() + "）");
        }
        dto.setOutputType(type.name());
        dto.setOutputId(existingId);
        dto.setProductId(type == com.jjx.product.enums.ManufacturingOutputType.PRODUCT ? existingId : null);
    }

    /**
     * 检查编码是否唯一
     */
    private void checkCodeUnique(String routingCode, String version) {
        LambdaQueryWrapper<EngineeringRouting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EngineeringRouting::getRoutingCode, routingCode)
               .eq(EngineeringRouting::getRoutingVersion, version);
        long count = count(wrapper);
        if (count > 0) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_CODE_DUPLICATE);
        }
    }

    /**
     * 保存明细（支持组合工序）
     * 前端传的 groupId 是临时负数，需要替换为真实ID
     */
    private void saveItems(Long routingId, List<EngineeringRoutingItemDTO> itemDTOs) {
        if (itemDTOs == null || itemDTOs.isEmpty()) return;

        // 2026-09-05 父子结构落库：每个 dto = 一道工序（父行），children = 组合作业项（子行）
        // 兼容旧平铺数据：无 children 的行作为单作业父行直接落（process_id 自带）
        // dev-20260929-027：工序序号按「组（process_category：面板/上线/下线）」各自从 1 开始；
        // workflow_seq = 组的出现顺序（1..N），process_order = 组内序号 ⇒ 唯一键 (routing_id, workflow_seq, process_order)。
        java.util.LinkedHashMap<String, Integer> workflowSeqByCategory = new java.util.LinkedHashMap<>();
        java.util.HashMap<String, Integer> nextOrderByCategory = new java.util.HashMap<>();
        for (EngineeringRoutingItemDTO dto : itemDTOs) {
            // 父行（工序）
            EngineeringRoutingItem parent = buildItem(dto, routingId);
            String category = parent.getProcessCategory();
            int workflowSeq = workflowSeqByCategory.computeIfAbsent(category, key -> workflowSeqByCategory.size() + 1);
            parent.setWorkflowSeq(workflowSeq);
            parent.setProcessOrder(nextOrderByCategory.merge(category, 1, Integer::sum)); // 组内工序顺序 1..N
            parent.setParentId(null);
            parent.setGroupId(null);
            parent.setGroupName(null);
            parent.setGroupOrder(null);
            routingDetailMapper.insert(parent); // MP 回填 itemId

            // 子行（组合作业项）：挂刚插入的父行
            if (dto.getChildren() != null && !dto.getChildren().isEmpty()) {
                for (EngineeringRoutingItemDTO childDto : dto.getChildren()) {
                    EngineeringRoutingItem child = buildItem(childDto, routingId);
                    child.setWorkflowSeq(workflowSeq);
                    child.setProcessOrder(null); // 子行无工序序号（列已允许 NULL）
                    child.setParentId(parent.getItemId());
                    child.setGroupId(null);
                    child.setGroupName(null);
                    child.setGroupOrder(null);
                    routingDetailMapper.insert(child);
                }
            }
        }
    }

    /** DTO → Entity 公共字段处理（2026-09-05 从原 saveItems 提取） */
    private EngineeringRoutingItem buildItem(EngineeringRoutingItemDTO dto, Long routingId) {
        EngineeringRoutingItem item = new EngineeringRoutingItem();
        BeanUtil.copyProperties(dto, item);
        item.setItemId(null);       // 新增模式
        item.setRoutingId(routingId);
        item.setProcessOrder(null); // 由调用方决定

        // processId 无效(0/null)时置 null, 避免外键约束失败(fk_routing_detail_process)
        if (item.getProcessId() != null && item.getProcessId() <= 0) {
            item.setProcessId(null);
        }
        // 空字符串转 null，避免数据库约束问题
        if (item.getCustomProcessParams() != null && item.getCustomProcessParams().isBlank()) {
            item.setCustomProcessParams(null);
        }
        if (item.getDescription() != null && item.getDescription().isBlank()) {
            item.setDescription(null);
        }
        if (item.getRemark() != null && item.getRemark().isBlank()) {
            item.setRemark(null);
        }
        // 工序类别：空/无效时默认 MAIN（表 NOT NULL）
        if (item.getProcessCategory() == null || item.getProcessCategory().isBlank()) {
            item.setProcessCategory("MAIN");
        }
        return item;
    }

    /**
     * 生成组合ID（使用时间戳+随机数，避免冲突）
     */
    private Long generateGroupId() {
        return System.currentTimeMillis() * 1000 + (long)(Math.random() * 1000);
    }

}
