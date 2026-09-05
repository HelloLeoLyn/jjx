package com.jjx.product.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.product.domain.converter.EngineeringRoutingConverter;
import com.jjx.product.domain.dto.EngineeringRoutingDTO;
import com.jjx.product.domain.dto.EngineeringRoutingItemDTO;
import com.jjx.product.domain.dto.EngineeringRoutingQueryDTO;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.product.domain.vo.EngineeringRoutingItemVO;
import com.jjx.product.domain.vo.EngineeringRoutingVO;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import com.jjx.product.mapper.EngineeringRoutingMapper;
import com.jjx.product.service.IEngineeringRoutingService;
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

    private final EngineeringRoutingMapper routingMapper;
    private final EngineeringRoutingItemMapper routingDetailMapper;
    private final EngineeringRoutingConverter routingConverter;
    private final com.jjx.product.mapper.ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineeringRoutingVO createRouting(EngineeringRoutingDTO dto) {
        // 检查编码是否重复
        checkCodeUnique(dto.getRoutingCode(), dto.getRoutingVersion());

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

        // 检查状态是否可编辑
        if (!ApproveStatusEnum.getByValue(routing.getApproveStatus()).isEditable()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_CANNOT_EDIT);
        }

        // ===== 自动升版：内容有变更时生成新版本，旧版本失效 =====
        if (Boolean.TRUE.equals(dto.getBumpVersion())) {
            return saveAsNewVersion(routing, dto);
        }

        BeanUtil.copyProperties(dto, routing);
        routing.setUpdateTime(LocalDateTime.now());
        updateById(routing);

        // 更新明细
        routingDetailMapper.deleteByRoutingId(routing.getRoutingId());
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            saveItems(routing.getRoutingId(), dto.getItems());
            calculateHours(routing.getRoutingId());
        }

        log.info("更新工艺路线成功: {}", routing.getRoutingCode());
        return getRoutingItems(routing.getRoutingId());
    }

    /**
     * 自动升版保存：旧版本失效（is_current=0），新建版本（版本号+1、is_current=1、parent指向旧版本），
     * 明细写入新版本，同步 product.current_routing_version
     */
    private EngineeringRoutingVO saveAsNewVersion(EngineeringRouting oldRouting, EngineeringRoutingDTO dto) {
        // 1. 计算新版本号：当前版本主号+1（V1.0 → V2.0）
        List<String> versions = list(new LambdaQueryWrapper<EngineeringRouting>()
                        .eq(EngineeringRouting::getProductId, oldRouting.getProductId()))
                .stream()
                .map(r -> r.getVersion() != null ? r.getVersion() : r.getRoutingVersion())
                .collect(Collectors.toList());
        String newVersion = computeNextRoutingVersion(versions);

        // 2. 旧版本失效（该产品所有版本 is_current=0，再设新版本为当前）
        routingMapper.setAllNotCurrent(oldRouting.getProductId());
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
        com.jjx.product.domain.entity.Product product = productMapper.selectById(oldRouting.getProductId());
        if (product != null) {
            product.setCurrentRoutingVersion(newVersion);
            product.setCurrentRouteId(newRouting.getRoutingId());
            productMapper.updateById(product);
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

        // 检查新版本是否存在
        checkCodeUnique(oldRouting.getRoutingCode(), newVersion);

        // 复制路线
        EngineeringRouting newRouting = new EngineeringRouting();
        BeanUtil.copyProperties(oldRouting, newRouting);
        newRouting.setRoutingId(null);
        newRouting.setRoutingVersion(newVersion);
        newRouting.setVersion(newVersion); // 2026-08-10 DEV-769：双字段同步，统一语义
        newRouting.setApproveStatus(ApproveStatusEnum.DRAFT.getValue());
        newRouting.setIsCurrent(0);
        save(newRouting);

        // 复制明细（保留组合信息和前置工序）
        List<EngineeringRoutingItem> oldDetails = routingDetailMapper.selectByRoutingId(routingId);
        for (EngineeringRoutingItem detail : oldDetails) {
            EngineeringRoutingItem newDetail = new EngineeringRoutingItem();
            BeanUtil.copyProperties(detail, newDetail);
            newDetail.setItemId(null);
            newDetail.setRoutingId(newRouting.getRoutingId());
            routingDetailMapper.insert(newDetail);
        }

        calculateHours(newRouting.getRoutingId());

        log.info("复制工艺路线成功: {} -> {}", oldRouting.getRoutingCode(), newVersion);
        return getRoutingItems(newRouting.getRoutingId());
    }

    @Override
    @Event(value = "product.routing.version_changed", bizId = "#routingId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void setCurrentVersion(Long routingId) {
        EngineeringRouting routing = getById(routingId);
        if (routing == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }

        // 设置同一产品的所有路线为非当前
        routingMapper.setAllNotCurrent(routing.getProductId());

        // 设置当前路线
        routing.setIsCurrent(1);
        updateById(routing);

        log.info("设置当前版本成功: {} v{}", routing.getRoutingCode(), routing.getRoutingVersion());
    }

    @Override
    @Event(value = "product.routing.submitted", bizId = "#routingId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void submitApprove(Long routingId) {
        EngineeringRouting routing = getById(routingId);
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
    }

    @Override
    @Event(value = "product.routing.approved", bizId = "#routingId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long routingId, String remark) {
        EngineeringRouting routing = getById(routingId);
        if (routing == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }

        if (routing.getApproveStatus() != ApproveStatusEnum.PENDING.getValue()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_ALREADY_APPROVED);
        }

        routing.setApproveStatus(ApproveStatusEnum.APPROVED.getValue());
        updateById(routing);

        log.info("审批通过: {}", routing.getRoutingCode());
    }

    @Override
    @Event(value = "product.routing.rejected", bizId = "#routingId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long routingId, String remark) {
        EngineeringRouting routing = getById(routingId);
        if (routing == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }

        if (routing.getApproveStatus() != ApproveStatusEnum.PENDING.getValue()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_ALREADY_APPROVED);
        }

        routing.setApproveStatus(ApproveStatusEnum.REJECTED.getValue());
        updateById(routing);

        log.info("审批驳回: {}", routing.getRoutingCode());
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

        BigDecimal totalLabor = BigDecimal.ZERO;
        BigDecimal totalMachine = BigDecimal.ZERO;

        for (EngineeringRoutingItem detail : details) {
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
        routing.setProcessCount(details.size());
        updateById(routing);
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
        int order = 1;
        for (EngineeringRoutingItemDTO dto : itemDTOs) {
            // 父行（工序）
            EngineeringRoutingItem parent = buildItem(dto, routingId);
            parent.setProcessOrder(order++); // 工序顺序 1..N
            parent.setParentId(null);
            parent.setGroupId(null);
            parent.setGroupName(null);
            parent.setGroupOrder(null);
            routingDetailMapper.insert(parent); // MP 回填 itemId

            // 子行（组合作业项）：挂刚插入的父行
            if (dto.getChildren() != null && !dto.getChildren().isEmpty()) {
                for (EngineeringRoutingItemDTO childDto : dto.getChildren()) {
                    EngineeringRoutingItem child = buildItem(childDto, routingId);
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
