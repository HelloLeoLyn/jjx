package com.jjx.product.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.product.domain.converter.ProductRoutingConverter;
import com.jjx.product.domain.dto.ProductRoutingDTO;
import com.jjx.product.domain.dto.ProductRoutingItemDTO;
import com.jjx.product.domain.dto.ProductRoutingQueryDTO;
import com.jjx.product.domain.entity.ProductRouting;
import com.jjx.product.domain.entity.ProductRoutingItem;
import com.jjx.product.domain.vo.ProductRoutingItemVO;
import com.jjx.product.domain.vo.ProductRoutingVO;
import com.jjx.product.mapper.ProductRoutingItemMapper;
import com.jjx.product.mapper.ProductRoutingMapper;
import com.jjx.product.service.IProductRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRoutingServiceImpl extends ServiceImpl<ProductRoutingMapper, ProductRouting>
        implements IProductRoutingService {

    private final ProductRoutingMapper routingMapper;
    private final ProductRoutingItemMapper routingDetailMapper;
    private final ProductRoutingConverter routingConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductRoutingVO createRouting(ProductRoutingDTO dto) {
        // 检查编码是否重复
        checkCodeUnique(dto.getRoutingCode(), dto.getRoutingVersion());

        // 创建路线
        ProductRouting routing = new ProductRouting();
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
    public ProductRoutingVO updateRouting(ProductRoutingDTO dto) {
        ProductRouting routing = getById(dto.getRoutingId());
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
    public ProductRoutingVO copyAsNewVersion(Long routingId, String newVersion) {
        ProductRouting oldRouting = getById(routingId);
        if (oldRouting == null) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_NOT_FOUND);
        }

        // 检查新版本是否存在
        checkCodeUnique(oldRouting.getRoutingCode(), newVersion);

        // 复制路线
        ProductRouting newRouting = new ProductRouting();
        BeanUtil.copyProperties(oldRouting, newRouting);
        newRouting.setRoutingId(null);
        newRouting.setRoutingVersion(newVersion);
        newRouting.setApproveStatus(ApproveStatusEnum.DRAFT.getCode());
        newRouting.setIsCurrent(0);
        save(newRouting);

        // 复制明细（保留组合信息和前置工序）
        List<ProductRoutingItem> oldDetails = routingDetailMapper.selectByRoutingId(routingId);
        for (ProductRoutingItem detail : oldDetails) {
            ProductRoutingItem newDetail = new ProductRoutingItem();
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
    @Transactional(rollbackFor = Exception.class)
    public void setCurrentVersion(Long routingId) {
        ProductRouting routing = getById(routingId);
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
    @Transactional(rollbackFor = Exception.class)
    public void submitApprove(Long routingId) {
        ProductRouting routing = getById(routingId);
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
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long routingId, String remark) {
        ProductRouting routing = getById(routingId);
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
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long routingId, String remark) {
        ProductRouting routing = getById(routingId);
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
    public ProductRoutingVO getCurrentByProductId(Long productId) {
        ProductRouting routing = routingMapper.selectCurrentByProductId(productId);
        if (routing == null) {
            return null;
        }
        return getRoutingItems(routing.getRoutingId());
    }

    @Override
    public List<ProductRoutingVO> getAllVersionsByProductId(Long productId) {
        List<ProductRouting> routings = routingMapper.selectAllVersionsByProductId(productId);
        return routings.stream()
                .map(r -> getRoutingItems(r.getRoutingId()))
                .collect(Collectors.toList());
    }

    @Override
    public ProductRoutingVO getRoutingItems(Long routingId) {
        ProductRouting routing = getById(routingId);
        if (routing == null) {
            return null;
        }

        ProductRoutingVO vo = new ProductRoutingVO();
        BeanUtil.copyProperties(routing, vo);

        // 设置是否当前版本名称
        vo.setIsCurrentName(routing.getIsCurrent() == 1 ? "是" : "否");

        // 获取明细（按 group_order, process_order 排序）
        List<ProductRoutingItemVO> items = routingDetailMapper.selectVOsByRoutingId(routingId);
        vo.setItems(items);

        // 计算组合汇总信息
        Map<Long, List<ProductRoutingItemVO>> groupMap = items.stream()
            .filter(item -> item.getGroupId() != null)
            .collect(Collectors.groupingBy(ProductRoutingItemVO::getGroupId));

        if (!groupMap.isEmpty()) {
            List<ProductRoutingVO.GroupSummary> summaries = new ArrayList<>();
            groupMap.forEach((groupId, groupItems) -> {
                ProductRoutingVO.GroupSummary summary = new ProductRoutingVO.GroupSummary();
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
        List<ProductRoutingItem> details = routingDetailMapper.selectByRoutingId(routingId);

        BigDecimal totalLabor = BigDecimal.ZERO;
        BigDecimal totalMachine = BigDecimal.ZERO;

        for (ProductRoutingItem detail : details) {
            if (detail.getCustomLaborHours() != null) {
                totalLabor = totalLabor.add(detail.getCustomLaborHours());
            }
            if (detail.getCustomMachineHours() != null) {
                totalMachine = totalMachine.add(detail.getCustomMachineHours());
            }
        }

        ProductRouting routing = getById(routingId);
        routing.setTotalLaborHours(totalLabor);
        routing.setTotalMachineHours(totalMachine);
        routing.setProcessCount(details.size());
        updateById(routing);
    }

    @Override
    public PageResult<ProductRoutingVO> pageQuery(ProductRoutingQueryDTO queryDTO) {
        LambdaQueryWrapper<ProductRouting> wrapper = new LambdaQueryWrapper<>();

        // 构建查询条件
        if (StringUtils.hasText(queryDTO.getRoutingCode())) {
            wrapper.like(ProductRouting::getRoutingCode, queryDTO.getRoutingCode());
        }
        if (StringUtils.hasText(queryDTO.getRoutingName())) {
            wrapper.like(ProductRouting::getRoutingName, queryDTO.getRoutingName());
        }
        if (queryDTO.getProductId() != null) {
            wrapper.eq(ProductRouting::getProductId, queryDTO.getProductId());
        }
        if (StringUtils.hasText(queryDTO.getProductCode())) {
            wrapper.like(ProductRouting::getProductCode, queryDTO.getProductCode());
        }
        if (queryDTO.getApproveStatus() != null) {
            wrapper.eq(ProductRouting::getApproveStatus, queryDTO.getApproveStatus());
        }
        if (queryDTO.getIsCurrent() != null) {
            wrapper.eq(ProductRouting::getIsCurrent, queryDTO.getIsCurrent());
        }

        // 排序
        if (StringUtils.hasText(queryDTO.getOrderByColumn())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getIsAsc());
            switch (queryDTO.getOrderByColumn()) {
                case "routingId":
                    wrapper.orderBy(true, isAsc, ProductRouting::getRoutingId);
                    break;
                case "routingCode":
                    wrapper.orderBy(true, isAsc, ProductRouting::getRoutingCode);
                    break;
                case "createTime":
                    wrapper.orderBy(true, isAsc, ProductRouting::getCreateTime);
                    break;
                default:
                    wrapper.orderByDesc(ProductRouting::getCreateTime);
            }
        } else {
            wrapper.orderByDesc(ProductRouting::getCreateTime);
        }

        Page<ProductRouting> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        Page<ProductRouting> resultPage = page(page, wrapper);

        // 转换为VO
        List<ProductRouting> records = resultPage.getRecords();
        List<ProductRoutingVO> voList = routingConverter.toVOList(records);
        return PageResult.build(voList,resultPage.getTotal());
    }


    /**
     * 检查编码是否唯一
     */
    private void checkCodeUnique(String routingCode, String version) {
        LambdaQueryWrapper<ProductRouting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductRouting::getRoutingCode, routingCode)
               .eq(ProductRouting::getRoutingVersion, version);
        long count = count(wrapper);
        if (count > 0) {
            throw new BusinessException(BusinessExceptionEnum.ROUTING_CODE_DUPLICATE);
        }
    }

    /**
     * 保存明细（支持组合工序）
     * 前端传的 groupId 是临时负数，需要替换为真实ID
     */
    private void saveItems(Long routingId, List<ProductRoutingItemDTO> itemDTOs) {
        if (itemDTOs == null || itemDTOs.isEmpty()) return;

        // 第一步：收集所有有组合的工序，按临时 groupId 分组
        Map<Long, List<ProductRoutingItemDTO>> groupMap = itemDTOs.stream()
            .filter(item -> item.getGroupId() != null)
            .collect(Collectors.groupingBy(ProductRoutingItemDTO::getGroupId));

        // 第二步：为每个组合生成一个真实的 groupId
        Map<Long, Long> tempToRealGroupId = new HashMap<>();
        for (Long tempGroupId : groupMap.keySet()) {
            tempToRealGroupId.put(tempGroupId, generateGroupId());
        }

        // 第三步：DTO 转 Entity 并统一保存
        int order = 1;
        for (ProductRoutingItemDTO dto : itemDTOs) {
            ProductRoutingItem item = new ProductRoutingItem();
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
