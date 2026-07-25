package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.inventory.domain.InventoryStocktakeOrder;
import com.jjx.inventory.dto.query.StocktakeQueryDTO;
import com.jjx.inventory.dto.vo.StocktakeVO;
import com.jjx.inventory.mapper.InventoryStocktakeOrderMapper;
import com.jjx.inventory.service.InventoryStocktakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 盘点服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryStocktakeServiceImpl extends ServiceImpl<InventoryStocktakeOrderMapper, InventoryStocktakeOrder>
        implements InventoryStocktakeService {

    private final InventoryStocktakeOrderMapper stocktakeOrderMapper;

    @Override
    public IPage<StocktakeVO> page(StocktakeQueryDTO query) {
        // 这里需要实现分页查询，返回StocktakeVO
        // 暂时返回空分页，实际需要实现查询逻辑
        Page<StocktakeVO> page = new Page<>(query.getCurrent(), query.getSize());
        return page;
    }

    @Override
    public StocktakeVO getDetail(Long stocktakeId) {
        // TODO: 实现获取盘点单详情，包括明细项
        // 暂时返回空对象
        return new StocktakeVO();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Map<String, Object> params) {
        // TODO: 实现创建盘点单逻辑
        log.info("创建盘点单: {}", params);
        return 1L;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startStocktake(Long stocktakeId) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!"draft".equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法开始盘点: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus("processing");
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean inputStocktakeData(Long stocktakeId, List<Map<String, Object>> items) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!"processing".equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法录入数据: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        // TODO: 实现录入盘点数据逻辑
        log.info("录入盘点数据: stocktakeId={}, items={}", stocktakeId, items);
        return true;
    }

    @Override
    public Map<String, Object> calculateDiff(Long stocktakeId) {
        // TODO: 实现计算盘点差异逻辑
        log.info("计算盘点差异: stocktakeId={}", stocktakeId);
        return Map.of("message", "计算差异功能待实现");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmResult(Long stocktakeId) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!"processing".equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法确认结果: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus("confirmed");
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean processDiff(Long stocktakeId, Long operatorId, String operatorName) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!"confirmed".equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法处理盈亏: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        // TODO: 实现处理盈亏逻辑，生成入库单/出库单
        log.info("处理盈亏: stocktakeId={}, operatorId={}, operatorName={}", stocktakeId, operatorId, operatorName);
        order.setOrderStatus("processed");
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeStocktake(Long stocktakeId) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!"processed".equals(order.getOrderStatus())) {
            log.error("盘点单状态不正确，无法关闭: stocktakeId={}, status={}", stocktakeId, order.getOrderStatus());
            return false;
        }

        order.setOrderStatus("closed");
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean submitApprove(Long stocktakeId) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        order.setApproveStatus("pending");
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    public boolean approve(Long stocktakeId, Long approverId, String approverName, String remark) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        if (!"pending".equals(order.getApproveStatus())) {
            log.error("盘点单审批状态不正确，无法审批: stocktakeId={}, status={}", stocktakeId, order.getApproveStatus());
            return false;
        }

        order.setApproveStatus("approved");
        order.setApproverId(approverId);
        order.setApproverName(approverName);
        order.setApproveRemark(remark);
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    public List<StocktakeVO> getProcessing() {
        List<InventoryStocktakeOrder> orders = stocktakeOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryStocktakeOrder>()
                        .eq(InventoryStocktakeOrder::getOrderStatus, "processing")
                        .orderByAsc(InventoryStocktakeOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public List<StocktakeVO> getPendingApproval() {
        List<InventoryStocktakeOrder> orders = stocktakeOrderMapper.selectList(
                new LambdaQueryWrapper<InventoryStocktakeOrder>()
                        .eq(InventoryStocktakeOrder::getApproveStatus, "pending")
                        .orderByAsc(InventoryStocktakeOrder::getCreateTime)
        );
        return convertToVOList(orders);
    }

    @Override
    public boolean updateStatus(Long stocktakeId, String status) {
        InventoryStocktakeOrder order = stocktakeOrderMapper.selectById(stocktakeId);
        if (order == null) {
            log.error("盘点单不存在: stocktakeId={}", stocktakeId);
            return false;
        }

        order.setOrderStatus(status);
        return stocktakeOrderMapper.updateById(order) > 0;
    }

    @Override
    public IPage<InventoryStocktakeOrder> pageQuery(Map<String, Object> params) {
        String stocktakeNo = (String) params.get("stocktakeNo");
        String startDate = (String) params.get("startDate");
        String endDate = (String) params.get("endDate");
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 10);

        LambdaQueryWrapper<InventoryStocktakeOrder> wrapper = new LambdaQueryWrapper<>();
        if (stocktakeNo != null && !stocktakeNo.isEmpty()) {
            wrapper.like(InventoryStocktakeOrder::getStocktakeNo, stocktakeNo);
        }

        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(InventoryStocktakeOrder::getCreateTime, startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(InventoryStocktakeOrder::getCreateTime, endDate);
        }
        wrapper.orderByDesc(InventoryStocktakeOrder::getCreateTime);

        Page<InventoryStocktakeOrder> page = new Page<>(pageNum, pageSize);
        return stocktakeOrderMapper.selectPage(page, wrapper);
    }

    @Override
    public Map<String, Object> getDetail(Map<String, Object> params) {
        // TODO: 实现获取盘点单详情，包括明细项
        return Map.of("message", "详情功能待实现");
    }

    private static List<StocktakeVO> convertToVOList(List<InventoryStocktakeOrder> orders) {
        List<StocktakeVO> result = new ArrayList<>();
        for (InventoryStocktakeOrder order : orders) {
            result.add(convertToVO(order));
        }
        return result;
    }

    private static StocktakeVO convertToVO(InventoryStocktakeOrder order) {
        if (order == null) {
            return null;
        }

        StocktakeVO vo = new StocktakeVO();
        vo.setStocktakeId(order.getStocktakeId());
        vo.setStocktakeNo(order.getStocktakeNo());
        vo.setStocktakeType(order.getStocktakeType());
        vo.setWarehouseId(order.getWarehouseId());
        vo.setLocationIds(order.getLocationIds());
        vo.setMaterialIds(order.getMaterialIds());
        vo.setPlanStartTime(order.getPlanStartTime());
        vo.setPlanEndTime(order.getPlanEndTime());
        vo.setActualStartTime(order.getActualStartTime());
        vo.setActualEndTime(order.getActualEndTime());
        vo.setStocktakerId(order.getStocktakerId());
        vo.setStocktakerName(order.getStocktakerName());
        vo.setSupervisorId(order.getSupervisorId());
        vo.setSupervisorName(order.getSupervisorName());
        vo.setTotalSystemQuantity(order.getTotalSystemQuantity());
        vo.setTotalActualQuantity(order.getTotalActualQuantity());
        vo.setTotalDiffQuantity(order.getTotalDiffQuantity());
        vo.setTotalDiffAmount(order.getTotalDiffAmount());
        vo.setOrderStatus(order.getOrderStatus());
        vo.setApproveStatus(order.getApproveStatus());
        vo.setApproverId(order.getApproverId());
        vo.setApproverName(order.getApproverName());
        vo.setApproveTime(order.getApproveTime());
        vo.setApproveRemark(order.getApproveRemark());
        vo.setRemark(order.getRemark());



        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        // 设置类型名称

        return vo;
    }

    private static String getStocktakeTypeName(String stocktakeType) {
        if (stocktakeType == null) {
            return "";
        }
        switch (stocktakeType) {
            case "full": return "全盘";
            case "partial": return "抽盘";
            case "cycle": return "循环盘点";
            default: return stocktakeType;
        }
    }

    private static String getOrderStatusName(String orderStatus) {
        if (orderStatus == null) {
            return "";
        }
        switch (orderStatus) {
            case "draft": return "草稿";
            case "processing": return "盘点中";
            case "confirmed": return "已确认";
            case "processed": return "已处理";
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
