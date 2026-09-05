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
     * 订单齐套检查只读预览：两步算料并返回成品、物料及汇总信息，不生成预警
     */
    Map<String, Object> previewOrderShortage(Long orderId);

    /**
     * 订单齐套检查（扣在途采购量），返回缺料明细（含在途/实际缺口）
     */
    java.util.List<java.util.Map<String, Object>> checkOrderShortageWithDetail(Long orderId);

    /**
     * 全局汇总缺料检查（082定稿：订单缺料预警主逻辑，非单订单）
     * 在途订单(状态4已审核/6已确认/7生产中)按BOM展开物料需求→汇总→对比可用量+在途采购→缺口生成物料维度预警
     * 两步走（035/096）：产品维度先扣产品库存→还需生产→BOM展开→物料缺口
     */
    void checkGlobalShortage();

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
    boolean batchProcessAlert(List<Long> alertIds, List<Long> materialIds, String relatedOrderNo, String remark);

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
     * 按物料列表查询未处理预警ID（status 0新增/1已读，DEV-997）
     */
    java.util.List<Long> getUnprocessedAlertIdsByMaterials(java.util.List<Long> materialIds);

    /**
     * 分页查询预警列表（旧方法，兼容性）
     */
    IPage<InventoryAlertLog> pageQuery(Map<String, Object> params);

}
