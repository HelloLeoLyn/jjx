package com.jjx.sales.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.common.core.page.PageResult;
import com.jjx.sales.domain.dto.SalesLogQueryDTO;
import com.jjx.sales.domain.entity.SalesLog;
import com.jjx.sales.domain.vo.SalesLogVO;
import com.jjx.sales.enums.OperationResultEnum;
import com.jjx.sales.enums.OperationTypeEnum;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 销售订单操作日志服务接口
 */
public interface SalesLogService extends IService<SalesLog> {

    /**
     * 分页查询操作日志
     */
    PageResult<SalesLogVO> pageQuery(SalesLogQueryDTO queryDTO);

    /**
     * 根据日志ID查询
     */
    SalesLogVO getById(Long logId);

    /**
     * 根据订单ID查询日志列表
     */
    List<SalesLogVO> getByOrderId(Long orderId);

    /**
     * 根据订单号查询日志列表
     */
    List<SalesLogVO> getByOrderNo(String orderNo);

    /**
     * 查询订单的最新操作日志
     */
    SalesLogVO getLatestByOrderId(Long orderId);

    /**
     * 根据ID删除日志
     */
    void deleteById(Long logId);

    /**
     * 根据订单ID删除所有日志
     */
    void deleteByOrderId(Long orderId);

    /**
     * 批量删除日志
     */
    void batchDeleteByIds(List<Long> logIds);

    /**
     * 导出日志
     */
    byte[] exportLogs(SalesLogQueryDTO queryDTO);

    /**
     * 获取操作类型统计
     */
    List<Map<String, Object>> getOperationTypeStats(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取操作人统计
     */
    List<Map<String, Object>> getOperatorStats(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 记录操作日志
     *
     * @param orderId       订单ID
     * @param orderNo       订单号
     * @param operationType 操作类型
     * @param description   操作描述
     * @param remark        备注
     * @param result        操作结果
     */
    void log(Long orderId, String orderNo, OperationTypeEnum operationType,
             String description, String remark, OperationResultEnum result);

}