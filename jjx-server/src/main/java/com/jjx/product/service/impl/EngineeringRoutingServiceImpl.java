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
import com.jjx.product.domain.entity.EngineeringRouting;
import com.jjx.product.domain.entity.EngineeringRoutingItem;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineeringRoutingVO createRouting(EngineeringRoutingDTO dto) {
        // 检查编码是否重复
        checkCodeUnique(dto.getRoutingCode(), dto.getRoutingVersion());

        // 创建路线
        EngineeringRouting routing = new EngineeringRouting();
        BeanUtil.copyProperties(dto, routing);
        routing.setApproveStatus(ApproveStatusEnum.DRAFT.getCode());
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
        if (!ApproveStatusEnum.getByCode(routing.getApproveStatus()).isEditable()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_CANNOT_EDIT);
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
        newRouting.setApproveStatus(ApproveStatusEnum.DRAFT.getCode());
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

        if (routing.getApproveStatus() != ApproveStatusEnum.DRAFT.getCode()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_ALREADY_APPROVED);
        }

        routing.setApproveStatus(ApproveStatusEnum.PENDING.getCode());
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

        if (routing.getApproveStatus() != ApproveStatusEnum.PENDING.getCode()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_ALREADY_APPROVED);
        }

        routing.setApproveStatus(ApproveStatusEnum.APPROVED.getCode());
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

        if (routing.getApproveStatus() != ApproveStatusEnum.PENDING.getCode()) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_ALREADY_APPROVED);
        }

        routing.setApproveStatus(ApproveStatusEnum.REJECTED.getCode());
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
        List<EngineeringRoutingItemVO> items = routingDetailMapper.selectVOsByRoutingId(routingId);
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

        // 第一步：收集所有有组合的工序，按临时 groupId 分组
        Map<Long, List<EngineeringRoutingItemDTO>> groupMap = itemDTOs.stream()
            .filter(item -> item.getGroupId() != null)
            .collect(Collectors.groupingBy(EngineeringRoutingItemDTO::getGroupId));

        // 第二步：为每个组合生成一个真实的 groupId
        Map<Long, Long> tempToRealGroupId = new HashMap<>();
        for (Long tempGroupId : groupMap.keySet()) {
            tempToRealGroupId.put(tempGroupId, generateGroupId());
        }

        // 第三步：DTO 转 Entity 并统一保存
        int order = 1;
        for (EngineeringRoutingItemDTO dto : itemDTOs) {
            EngineeringRoutingItem item = new EngineeringRoutingItem();
            BeanUtil.copyProperties(dto, item);
            item.setItemId(null);       // 新增模式
            item.setRoutingId(routingId);
            item.setProcessOrder(order++);  // 重新生成全局顺序

            // 空字符串转 null，避免数据库约束问题
            if (item.getCustomProcessParams() != null && item.getCustomProcessParams().isBlank()) {
                item.setCustomProcessParams(null);
            }
            if (item.getDescription() != null && item.getDescription().isBlank()) {
                item.setDescription(null);
            }
            if (item.getProcessCategory() != null && item.getProcessCategory().isBlank()) {
                item.setProcessCategory(null);
            }

            // 如果有组合，替换临时 groupId 为真实 groupId
            if (item.getGroupId() != null) {
                Long realGroupId = tempToRealGroupId.get(item.getGroupId());
                item.setGroupId(realGroupId);
            }
            // 没有组合的，groupId 保持 null

            routingDetailMapper.insert(item);
        }
    }

    /**
     * 生成组合ID（使用时间戳+随机数，避免冲突）
     */
    private Long generateGroupId() {
        return System.currentTimeMillis() * 1000 + (long)(Math.random() * 1000);
    }

}
