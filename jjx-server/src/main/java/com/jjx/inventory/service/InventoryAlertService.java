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
     * 检查安全库存预警
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
