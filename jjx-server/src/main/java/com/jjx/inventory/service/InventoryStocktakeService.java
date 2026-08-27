package com.jjx.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryStocktakeOrder;
import com.jjx.inventory.dto.query.StocktakeQueryDTO;
import com.jjx.inventory.dto.vo.StocktakeVO;

import java.util.List;
import java.util.Map;

/**
 * 盘点服务接口
 */
public interface InventoryStocktakeService extends IService<InventoryStocktakeOrder> {

    /**
     * 分页查询盘点单
     */
    IPage<StocktakeVO> page(StocktakeQueryDTO query);

    /**
     * 获取盘点单详情
     */
    StocktakeVO getDetail(Long stocktakeId);

    /**
     * 创建盘点单
     */
    Long create(Map<String, Object> params);

    /**
     * 开始盘点
     */
    boolean startStocktake(Long stocktakeId);

    /**
     * 录入盘点数据
     */
    boolean inputStocktakeData(Long stocktakeId, List<Map<String, Object>> items);

    /**
     * 计算盘点差异
     */
    Map<String, Object> calculateDiff(Long stocktakeId);

    /**
     * 确认盘点结果
     */
    boolean confirmResult(Long stocktakeId);

    /**
     * 处理盈亏（生成入库单/出库单）
     */
    boolean processDiff(Long stocktakeId, Long operatorId, String operatorName);

    /**
     * 关闭盘点单
     */
    boolean closeStocktake(Long stocktakeId);

    /**
     * 提交审批
     */
    boolean submitApprove(Long stocktakeId);

    /**
     * 审批通过
     */
    boolean approve(Long stocktakeId, Long approverId, String approverName, String remark);

    /**
     * 查询进行中的盘点单
     */
    List<StocktakeVO> getProcessing();

    /**
     * 查询待审批的盘点单
     */
    List<StocktakeVO> getPendingApproval();

    /**
     * 更新盘点单状态
     */
    boolean updateStatus(Long stocktakeId, Integer status);

    /**
     * 分页查询盘点单（旧方法，兼容性）
     */
    IPage<InventoryStocktakeOrder> pageQuery(Map<String, Object> params);

    /**
     * 获取盘点单详情（旧方法，兼容性）
     */
    Map<String, Object> getDetail(Map<String, Object> params);

}
