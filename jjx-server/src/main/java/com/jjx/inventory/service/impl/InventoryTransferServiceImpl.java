package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.domain.InventoryTransferOrder;
import com.jjx.inventory.dto.query.TransferQueryDTO;
import com.jjx.inventory.dto.vo.TransferVO;
import com.jjx.inventory.mapper.InventoryTransferOrderMapper;
import com.jjx.inventory.service.InventoryTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 调拨服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryTransferServiceImpl extends ServiceImpl<InventoryTransferOrderMapper, InventoryTransferOrder>
        implements InventoryTransferService {

    private final InventoryTransferOrderMapper transferOrderMapper;

    @Override
    public IPage<TransferVO> page(TransferQueryDTO query) {
        // 这里需要实现分页查询，返回TransferVO
        // 暂时返回空分页，实际需要实现查询逻辑
        Page<TransferVO> page = new Page<>(query.getCurrent(), query.getSize());
        return page;
    }

    @Override
    public TransferVO getDetail(Long transferId) {
        // TODO: 实现获取调拨单详情，包括明细项
        // 暂时返回空对象
        return new TransferVO();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> params) {
        // TODO: 实现创建调拨单逻辑
        log.info("创建调拨单: {}", params);
        return 1L;
    }

    @Override
    public boolean submitApprove(Long transferId) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        order.setApproveStatus("pending");
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean approve(Long transferId, Long approverId, String approverName, String remark) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        if (!"pending".equals(order.getApproveStatus())) {
            log.error("调拨单审批状态不正确，无法审批: transferId={}, status={}", transferId, order.getApproveStatus());
            return false;
        }

        order.setApproveStatus("approved");
        order.setApproverId(approverId);
        order.setApproverName(approverName);
        order.setApproveRemark(remark);
        order.setOrderStatus("approved");
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean reject(Long transferId, Long approverId, String approverName, String remark) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        if (!"pending".equals(order.getApproveStatus())) {
            log.error("调拨单审批状态不正确，无法驳回: transferId={}, status={}", transferId, order.getApproveStatus());
            return false;
        }

        order.setApproveStatus("rejected");
        order.setApproverId(approverId);
        order.setApproverName(approverName);
        order.setApproveRemark(remark);
        order.setOrderStatus("cancelled");
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmOut(Long transferId, Long operatorId, String operatorName) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        if (!"approved".equals(order.getOrderStatus())) {
            log.error("调拨单状态不正确，无法调出: transferId={}, status={}", transferId, order.getOrderStatus());
            return false;
        }

        // TODO: 执行调出仓库出库逻辑
        order.setOrderStatus("out_confirm");
        order.setOutOperator(operatorName);
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmIn(Long transferId, Long operatorId, String operatorName) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        if (!"out_confirm".equals(order.getOrderStatus())) {
            log.error("调拨单状态不正确，无法调入: transferId={}, status={}", transferId, order.getOrderStatus());
            return false;
        }

        // TODO: 执行调入仓库入库逻辑
        order.setOrderStatus("in_confirm");
        order.setInOperator(operatorName);
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(Long transferId, String reason) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        if ("in_confirm".equals(order.getOrderStatus()) || "closed".equals(order.getOrderStatus())) {
            log.error("已完成的调拨单无法取消: transferId={}", transferId);
            return false;
        }

        order.setOrderStatus("cancelled");
        order.setRemark(reason);
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    public List<TransferVO> getPendingApproval() {
        List<InventoryTransferOrder> orders = transferOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryTransferOrder>()
                        .eq(InventoryTransferOrder::getApproveStatus, "pending")
                        .orderByAsc(InventoryTransferOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public List<TransferVO> getProcessing() {
        List<InventoryTransferOrder> orders = transferOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryTransferOrder>()
                        .in(InventoryTransferOrder::getOrderStatus, "approved", "out_confirm")
                        .orderByAsc(InventoryTransferOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public boolean updateStatus(Long transferId, String status) {
        InventoryTransferOrder order = transferOrderMapper.selectById(transferId);
        if (order == null) {
            log.error("调拨单不存在: transferId={}", transferId);
            return false;
        }

        order.setOrderStatus(status);
        return transferOrderMapper.updateById(order) > 0;
    }

    @Override
    public IPage<InventoryTransferOrder> pageQuery(Map<String, Object> params) {
        String transferNo = (String) params.get("transferNo");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryTransferOrder> wrapper = new LambdaQueryWrapper<>();
        if (transferNo != null && !transferNo.isEmpty()) {
            wrapper.like(InventoryTransferOrder::getTransferNo, transferNo);
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryTransferOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryTransferOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryTransferOrder::getCreateTime);

        Page<InventoryTransferOrder> page = new Page<>(pageNum, pageSize);
        return transferOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Map<String, Object> params) {
        // TODO: 实现获取调拨单详情，包括明细项
        return Map.of("message", "详情功能待实现");
    }

    private static List<TransferVO> convertToVOList(List<InventoryTransferOrder> orders) {
        List<TransferVO> result = new ArrayList<>();
        for (InventoryTransferOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private static TransferVO convertToVO(InventoryTransferOrder order){
        return new TransferVO();
    }

    private static String getTransferTypeName(String transferType) {
        if (transferType == null) {
            return "";
        }
        switch (transferType) {
            case "normal": return "普通调拨";
            case "urgent": return "紧急调拨";
            default: return transferType;
        }
    }

    private static String getOrderStatusName(String orderStatus) {
        if (orderStatus == null) {
            return "";
        }
        switch (orderStatus) {
            case "draft": return "草稿";
            case "approved": return "已批准";
            case "out_confirm": return "已出库";
            case "in_confirm": return "已入库";
            case "closed": return "已关闭";
            case "cancelled": return "已取消";
            default: return orderStatus;
        }
    }

    private static String getApproveStatusName(String approveStatus) {
        if (approveStatus == null) {
            return "";
        }
        switch (approveStatus) {
            case "pending": return "待审批";
            case "approved": return "已批准";
            case "rejected": return "已驳回";
            default: return approveStatus;
        }
    }
}
