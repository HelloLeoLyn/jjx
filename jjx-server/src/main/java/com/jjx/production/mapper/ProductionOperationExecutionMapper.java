package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 生产工序执行 Mapper 接口
 */
@Mapper
public interface ProductionOperationExecutionMapper extends BaseMapper<ProductionOperationExecution> {

    @Select("SELECT e.execution_id,e.process_order AS processOrder,e.task_seq AS taskSeq,o.order_no AS orderNo "
            + "FROM production_operation_execution e JOIN production_order o ON o.order_id=e.order_id "
            + "WHERE e.execution_id=#{executionId} FOR UPDATE")
    Map<String, Object> selectTaskNoContextForUpdate(@Param("executionId") Long executionId);

    @Update("UPDATE production_operation_execution SET task_seq=task_seq+1 WHERE execution_id=#{executionId}")
    int incrementTaskSeq(@Param("executionId") Long executionId);

    /**
     * 按 execution 聚合已审批报工工时并核算工单人工成本，避免读取可能滞后的 execution 投影。
     */
    @Select("SELECT COALESCE(SUM(x.labor_hours * COALESCE((SELECT ri.standard_wage "
            + "FROM engineering_routing_item ri WHERE ri.routing_id=#{routingId} "
            + "AND ri.process_id=x.process_id LIMIT 1), 0)), 0) "
            + "FROM (SELECT e.execution_id, e.process_id, COALESCE(SUM(wr.labor_hours), 0) labor_hours "
            + "FROM production_operation_execution e JOIN production_work_report wr "
            + "ON wr.execution_id=e.execution_id AND wr.report_status=#{reportStatus} "
            + "WHERE e.order_id=#{orderId} GROUP BY e.execution_id, e.process_id) x")
    BigDecimal calculateApprovedLaborCost(@Param("orderId") Long orderId,
                                          @Param("routingId") Long routingId,
                                          @Param("reportStatus") String reportStatus);

    /**
     * 根据订单ID查询工序执行列表
     *
     * @param orderId 订单ID
     * @return 工序执行列表
     */
    List<ProductionOperationExecution> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据工序ID查询工序执行
     *
     * @param processId 工序ID
     * @return 工序执行列表
     */
    List<ProductionOperationExecution> selectByProcessId(@Param("processId") Long processId);

    /**
     * 根据操作员ID查询工序执行
     *
     * @param operatorId 操作员ID
     * @return 工序执行列表
     */
    List<ProductionOperationExecution> selectByOperatorId(@Param("operatorId") Long operatorId);

    /**
     * 根据设备ID查询工序执行
     *
     * @param equipmentId 设备ID
     * @return 工序执行列表
     */
    List<ProductionOperationExecution> selectByEquipmentId(@Param("equipmentId") Long equipmentId);

}
