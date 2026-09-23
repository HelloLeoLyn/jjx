package com.jjx.production;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.entity.ProductionEquipment;
import com.jjx.production.domain.entity.ProductionOperationExecution;
import com.jjx.production.domain.entity.ProductionOrder;
import com.jjx.production.enums.ExecutionStatusEnum;
import com.jjx.production.enums.ProductionOrderStatusEnum;
import com.jjx.production.mapper.ProductionEquipmentMapper;
import com.jjx.production.mapper.ProductionOperationExecutionMapper;
import com.jjx.production.mapper.ProductionOrderMapper;
import com.jjx.production.service.ProductionOperationRecordService;
import com.jjx.production.service.ProductionTaskService;
import com.jjx.production.service.WorkReportProjectionService;
import com.jjx.production.service.impl.ProductionOperationExecutionServiceImpl;
import com.jjx.quality.service.QualityLotService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionExecutionEquipmentBindingTest {

    @Test
    void shouldBindAvailableEquipmentOnFirstStart() {
        Fixture fixture = fixture(0);

        fixture.service.startExecution(10L, 5L, null, false);

        assertEquals(5L, fixture.execution.getEquipmentId());
        assertEquals("EQ-005", fixture.execution.getEquipmentCode());
        assertEquals("印刷机5号", fixture.execution.getEquipmentName());
        assertEquals(ExecutionStatusEnum.EXECUTING.getValue(), fixture.execution.getExecutionStatus());
        verify(fixture.recordService).createRecord(any());
    }

    @Test
    void shouldRejectMaintenanceEquipment() {
        Fixture fixture = fixture(2);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.startExecution(10L, 5L, null, false));

        assertEquals("设备不可用于开工：印刷机5号（维护中）", error.getMessage());
    }

    @Test
    void shouldRequireConfirmationBeforeChangingEquipment() {
        Fixture fixture = fixture(1);
        fixture.execution.setEquipmentId(3L);
        fixture.execution.setEquipmentCode("EQ-003");
        fixture.execution.setEquipmentName("印刷机3号");

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.startExecution(10L, 5L, null, false));

        assertEquals("工序已绑定设备 印刷机3号，更换为 印刷机5号 需要确认", error.getMessage());
    }

    private Fixture fixture(int equipmentStatus) {
        ProductionOperationExecutionMapper executionMapper = mock(ProductionOperationExecutionMapper.class);
        ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
        ProductionEquipmentMapper equipmentMapper = mock(ProductionEquipmentMapper.class);
        ProductionOperationRecordService recordService = mock(ProductionOperationRecordService.class);
        ProductionOperationExecutionServiceImpl service = spy(new ProductionOperationExecutionServiceImpl(
                executionMapper, orderMapper, equipmentMapper, mock(JdbcTemplate.class),
                mock(WorkReportProjectionService.class), mock(ProductionTaskService.class), recordService,
                mock(QualityLotService.class)));

        ProductionOperationExecution execution = new ProductionOperationExecution();
        execution.setExecutionId(10L);
        execution.setOrderId(20L);
        execution.setExecutionStatus(ExecutionStatusEnum.PENDING.getValue());
        doReturn(execution).when(service).getById(10L);
        doReturn(true).when(service).updateById(execution);

        ProductionOrder order = new ProductionOrder();
        order.setOrderStatus(ProductionOrderStatusEnum.IN_PROGRESS.getValue());
        when(orderMapper.selectById(20L)).thenReturn(order);

        ProductionEquipment equipment = new ProductionEquipment();
        equipment.setEquipmentId(5L);
        equipment.setEquipmentNo("EQ-005");
        equipment.setEquipmentName("印刷机5号");
        equipment.setStatus(equipmentStatus);
        when(equipmentMapper.selectById(5L)).thenReturn(equipment);
        return new Fixture(service, execution, recordService);
    }

    private record Fixture(ProductionOperationExecutionServiceImpl service,
                           ProductionOperationExecution execution,
                           ProductionOperationRecordService recordService) {
    }
}
