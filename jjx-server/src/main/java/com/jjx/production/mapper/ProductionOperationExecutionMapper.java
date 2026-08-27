package com.jjx.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
