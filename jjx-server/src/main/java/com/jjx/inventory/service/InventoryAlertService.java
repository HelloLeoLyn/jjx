package com.jjx.inventory.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.inventory.domain.InventoryAlertLog;
import com.jjx.inventory.dto.query.AlertQueryDTO;
import com.jjx.inventory.dto.vo.AlertVO;

import java.util.List;
import java.util.Map;

/**
 * 库存预警服务接口
 */
public interface InventoryAlertService extends IService<InventoryAlertLog> {

    /**
     * 分页查询预警列表
     */
    IPage<AlertVO> page(AlertQueryDTO query);

    /**
     * 执行预警检查（定时任务调用）
     */
    void executeAlertCheck();

    /**
     * 订单确认齐套检查：按订单产品BOM算料，对比库存，缺口生成订单缺料预警(order_shortage)
     * 幂等：同订单重算先清旧未处理缺料预警再重建
     */
    void checkOrderShortage(Long orderId);

    /**
     * 查询订单未处理缺料预警数（DEV-583 前端弹窗用）
     */
    long countUnprocessedOrderShortage(Long orderId);

    /**
     * 检查单个物料的安全库存预警
     * 出库/入库确认后调用
     */
    void checkSafeStockAlert(Long materialId);

    /**
     * 检查全量物料的安全库存（批量扫描用）
     */
    void checkSafeStockAlert();

    /**
     * 检查最高库存预警
     */
    void checkMaxStockAlert();

    /**
     * 检查保质期预警
     */
    void checkExpiryAlert();

    /**
     * 检查呆滞料预警
     */
    void checkObsoleteAlert();

    /**
     * 标记预警已读
     */
    boolean markRead(Long alertId);

    /**
     * 批量标记已读
     */
    boolean batchMarkRead(List<Long> alertIds);

    /**
     * 处理预警
     */
    boolean processAlert(Long alertId, String processedBy, String remark);

    /**
     * 批量处理预警（采购计划确认后回写：状态→已处理 + 处理人 + 关联采购订单号）
     */
    boolean batchProcessAlert(List<Long> alertIds, String relatedOrderNo, String remark);

    /**
     * 生成采购建议（基于安全库存）
     */
    List<Map<String, Object>> generatePurchaseSuggestions();

    /**
     * 查询未处理的预警
     */
    List<AlertVO> getUnprocessed();

    /**
     * 查询指定物料是否存在未处理的预警
     */
    boolean existsUnprocessed(String alertType, Long materialId);

    /**
     * 分页查询预警列表（旧方法，兼容性）
     */
    IPage<InventoryAlertLog> pageQuery(Map<String, Object> params);

}
