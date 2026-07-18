package com.jjx.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryTransferOrder;
import com.jjx.inventory.dto.query.TransferQueryDTO;
import com.jjx.inventory.dto.vo.TransferVO;

import java.util.List;
import java.util.Map;

/**
 * 调拨服务接口
 */
public interface InventoryTransferService extends IService<InventoryTransferOrder> {

    /**
     * 分页查询调拨单
     */
    IPage<TransferVO> page(TransferQueryDTO query);

    /**
     * 获取调拨单详情
     */
    TransferVO getDetail(Long transferId);

    /**
     * 创建调拨单
     */
    Long create(Map<String, Object> params);

    /**
     * 提交审批
     */
    boolean submitApprove(Long transferId);

    /**
     * 审批通过
     */
    boolean approve(Long transferId, Long approverId, String approverName, String remark);

    /**
     * 审批驳回
     */
    boolean reject(Long transferId, Long approverId, String approverName, String remark);

    /**
     * 调出确认（调出仓库出库）
     */
    boolean confirmOut(Long transferId, Long operatorId, String operatorName);

    /**
     * 调入确认（调入仓库入库）
     */
    boolean confirmIn(Long transferId, Long operatorId, String operatorName);

    /**
     * 取消调拨单
     */
    boolean cancel(Long transferId, String reason);

    /**
     * 查询待审批的调拨单
     */
    List<TransferVO> getPendingApproval();

    /**
     * 查询进行中的调拨单
     */
    List<TransferVO> getProcessing();

    /**
     * 更新调拨单状态
     */
    boolean updateStatus(Long transferId, String status);

    /**
     * 分页查询调拨单（旧方法，兼容性）
     */
    IPage<InventoryTransferOrder> pageQuery(Map<String, Object> params);

    /**
     * 获取调拨单详情（旧方法，兼容性）
     */
    Map<String, Object> getDetail(Map<String, Object> params);

}
