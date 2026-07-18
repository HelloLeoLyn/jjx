package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import com.jjx.inventory.dto.query.OutboundQueryDTO;
import com.jjx.inventory.dto.vo.OutboundVO;
import com.jjx.inventory.enums.OrderStatusEnum;
import com.jjx.inventory.mapper.InventoryOutboundOrderMapper;
import com.jjx.inventory.service.InventoryOutboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 出库服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryOutboundServiceImpl extends ServiceImpl<InventoryOutboundOrderMapper, InventoryOutboundOrder>
        implements InventoryOutboundService {

    private final InventoryOutboundOrderMapper outboundOrderMapper;

    @Override
    public IPage<OutboundVO> page(OutboundQueryDTO query) {
        // 这里需要实现分页查询，返回OutboundVO
        // 暂时返回空分页，实际需要实现查询逻辑
        Page<OutboundVO> page = new Page<>(query.getCurrent(), query.getSize());
        return page;
    }

    @Override
    public OutboundVO getDetail(Long outboundId) {
        // TODO: 实现获取出库单详情，包括明细项
        // 暂时返回空对象
        return new OutboundVO();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> params) {
        // TODO: 实现创建出库单逻辑
        log.info("创建出库单: {}", params);
        return 1L;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(Long outboundId, Long operatorId, String operatorName) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!"pending".equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法确认: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        // TODO: 执行库存扣减逻辑
        order.setOrderStatus("completed");
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long outboundId, String reason) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if ("completed".equals(order.getOrderStatus())) {
            log.error("已完成的出库单无法取消: outboundId={}", outboundId);
            return false;
        }

        order.setOrderStatus("cancelled");
        order.setRemark(reason);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean submitApprove(Long outboundId) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        order.setOrderStatus("pending_approval");
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean approve(Long outboundId, Long approverId, String approverName, String remark) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!"pending_approval".equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法审批: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus("approved");
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean reject(Long outboundId, Long approverId, String approverName, String remark) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        if (!"pending_approval".equals(order.getOrderStatus())) {
            log.error("出库单状态不正确，无法驳回: outboundId={}, status={}", outboundId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus(OrderStatusEnum.REJECTED.getCode());
        order.setRemark(remark);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public Long createFromProduction(Long workOrderId) {
        // TODO: 实现从生产工单创建出库单
        log.info("从生产工单创建出库单: workOrderId={}", workOrderId);
        return 1L;
    }

    @Override
    public Long createFromSales(Long salesOrderId) {
        // TODO: 实现从销售订单创建出库单
        log.info("从销售订单创建出库单: salesOrderId={}", salesOrderId);
        return 1L;
    }

    @Override
    public List<OutboundVO> getPendingApproval() {
        List<InventoryOutboundOrder> orders = outboundOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryOutboundOrder>()
                        .eq(InventoryOutboundOrder::getOrderStatus, "pending_approval")
                        .orderByAsc(InventoryOutboundOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public List<OutboundVO> getByDateRange(String startDate, String endDate) {
        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryOutboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryOutboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime);

        List<InventoryOutboundOrder> orders = outboundOrderMapper.selectList(wrapper);
        return convertToVOList(orders);
    }

    @Override
    public OutboundVO getBySource(String sourceType, Long sourceId) {
        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryOutboundOrder::getSourceType, sourceType)
                .eq(InventoryOutboundOrder::getSourceId, sourceId);
        InventoryOutboundOrder order = outboundOrderMapper.selectOne(wrapper);
        return convertToVO(order);
    }

    @Override
    public boolean updateStatus(Long outboundId, String status) {
        InventoryOutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            log.error("出库单不存在: outboundId={}", outboundId);
            return false;
        }

        order.setOrderStatus(status);
        return outboundOrderMapper.updateById(order) > 0;
    }

    @Override
    public IPage<InventoryOutboundOrder> pageQuery(Map<String, Object> params) {
        String outboundNo = (String) params.get("outboundNo");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryOutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (outboundNo != null && !outboundNo.isEmpty()) {
            wrapper.like(InventoryOutboundOrder::getOutboundNo, outboundNo);
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryOutboundOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryOutboundOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryOutboundOrder::getCreateTime);

        Page<InventoryOutboundOrder> page = new Page<>(pageNum, pageSize);
        return outboundOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Map<String, Object> params) {
        // TODO: 实现获取出库单详情，包括明细项
        return Map.of("message", "详情功能待实现");
    }

    private static List<OutboundVO> convertToVOList(List<InventoryOutboundOrder> orders) {
        List<OutboundVO> result = new ArrayList<>();
        for (InventoryOutboundOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private static OutboundVO convertToVO(InventoryOutboundOrder order) {
        if (order == null) {
            return null;
        }

        OutboundVO vo = new OutboundVO();
        vo.setOutboundId(order.getOutboundId());
        vo.setOutboundNo(order.getOutboundNo());
        vo.setOutboundType(order.getOutboundType());
        vo.setWarehouseId(order.getWarehouseId());
        vo.setSourceType(order.getSourceType());
        vo.setSourceId(order.getSourceId());
        vo.setSourceNo(order.getSourceNo());
        vo.setCustomerId(order.getCustomerId());
        vo.setOutboundDate(order.getOutboundDate());
        vo.setTotalQuantity(order.getTotalQuantity());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setOrderStatus(order.getOrderStatus());
        vo.setApproveStatus(order.getApproveStatus());
        vo.setRemark(order.getRemark());

        // 处理createBy和updateBy，尝试转换为Long


        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        // 设置类型名称
        vo.setOutboundTypeName(getOutboundTypeName(order.getOutboundType()));
        vo.setSourceTypeName(getSourceTypeName(order.getSourceType()));
        vo.setOrderStatusName(getOrderStatusName(order.getOrderStatus()));
        vo.setApproveStatusName(getApproveStatusName(order.getApproveStatus()));

        return vo;
    }

    private static String getOutboundTypeName(String outboundType) {
        if (outboundType == null) {
            return "";
        }
        switch (outboundType) {
            case "production": return "生产领料";
            case "sales": return "销售出库";
            case "return": return "退货出库";
            case "transfer": return "调拨出库";
            case "other": return "其他出库";
            default: return outboundType;
        }
    }

    private static String getSourceTypeName(String sourceType) {
        if (sourceType == null) {
            return "";
        }
        switch (sourceType) {
            case "work_order": return "工单";
            case "sales_order": return "销售订单";
            case "purchase_return": return "采购退货";
            case "transfer_order": return "调拨单";
            default: return sourceType;
        }
    }

    private static String getOrderStatusName(String orderStatus) {
        if (orderStatus == null) {
            return "";
        }
        switch (orderStatus) {
            case "pending": return "待确认";
            case "pending_approval": return "待审批";
            case "approved": return "已审批";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            case "rejected": return "已驳回";
            default: return orderStatus;
        }
    }

    private static String getApproveStatusName(String approveStatus) {
        if (approveStatus == null) {
            return "";
        }
        switch (approveStatus) {
            case "pending": return "待审批";
            case "approved": return "已通过";
            case "rejected": return "已驳回";
            default: return approveStatus;
        }
    }
}
