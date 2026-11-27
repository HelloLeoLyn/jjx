package com.jjx.sales.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.printing.artwork.ArtworkService;
import com.jjx.engineering.printing.screen.ScreenService;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.dto.save.*;
import com.jjx.sales.enums.*;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.impl.SampleOrderServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class SamplePrintingExecutionTest {
    @Mock OrderMapper orderMapper;
    @Mock SalesSampleProcessMapper sampleProcessMapper;
    @Mock SalesSampleBomMapper sampleBomMapper;
    @Mock ArtworkService artworkService;
    @Mock ScreenService screenService;
    @Spy ObjectMapper objectMapper=new ObjectMapper().findAndRegisterModules();
    @InjectMocks SampleOrderServiceImpl service;
    @BeforeEach void injectSharedRules() {
        ReflectionTestUtils.setField(service,"artworkService",artworkService);
        ReflectionTestUtils.setField(service,"screenService",screenService);
    }

    SalesOrder order() {
        var row=new SalesOrder();row.setOrderId(10L);row.setDeleted(0);row.setSampleRound(1);row.setSampleStatus(SampleOrderStatusEnum.ENGINEERING.getValue());return row;
    }
    SalesSampleProcess process(SampleProcessStatusEnum status) {
        var row=new SalesSampleProcess();row.setProcessId(100L);row.setOrderId(10L);row.setRoundNo(1);
        row.setMajorCategory("PRINT");row.setProcessName("丝印");row.setArtworkVersionId(200L);row.setStatus(status.getValue());return row;
    }
    void selected(SalesSampleProcess row) {
        when(orderMapper.selectById(10L)).thenReturn(order());
        when(sampleProcessMapper.selectById(100L)).thenReturn(row);
        when(sampleProcessMapper.lockArtworkVersion(200L)).thenReturn(200L);
    }

    @Test void firstExecutionCapturesActualPlateAndServerSnapshot() throws Exception {
        var before=process(SampleProcessStatusEnum.PENDING);var stored=process(SampleProcessStatusEnum.PENDING);selected(before);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order());when(sampleProcessMapper.selectForUpdate(100L)).thenReturn(stored);
        when(sampleProcessMapper.lockSampleProfile(10L)).thenReturn(order());
        when(sampleProcessMapper.planningVersion(200L,10L)).thenReturn(Map.of("artworkId",7L,"artworkNo","A7","versionNo","V2"));
        when(sampleProcessMapper.executionPlate(4L)).thenReturn(Map.of("plateNo","PL4","frameNo","FR1","mesh",120));
        var requirements=objectMapper.createObjectNode();when(screenService.validateRequirements(null)).thenReturn(requirements);
        when(sampleProcessMapper.selectById(100L)).thenReturn(before,stored);
        var dto=new SampleProcessItemStatusDTO();dto.setStatus(SampleProcessStatusEnum.IN_PROGRESS.getValue());dto.setActualPlateId(4L);
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("operator");
            var result=service.updateProcessItemStatus(10L,100L,dto);
            assertEquals(4L,result.getActualPlateId());assertNotNull(result.getStartTime());
            var snapshot=objectMapper.readTree(result.getExecutionSnapshot());
            assertEquals("V2",snapshot.path("versionNo").asText());assertEquals("operator",snapshot.path("operator").asText());
            assertEquals("PL4",snapshot.path("plateNo").asText());assertEquals(1,snapshot.path("roundNo").asInt());
        }
        var locks=inOrder(sampleProcessMapper,orderMapper);
        locks.verify(sampleProcessMapper).lockArtworkVersion(200L);
        locks.verify(orderMapper).selectByIdForUpdate(10L);
        locks.verify(sampleProcessMapper).selectForUpdate(100L);
        verify(sampleProcessMapper).updateById(stored);
    }

    @Test void occupiedPlateCannotStartAnotherSample() {
        var row=process(SampleProcessStatusEnum.PENDING);selected(row);
        when(sampleProcessMapper.planningVersion(200L,10L)).thenReturn(Map.of("artworkId",7L));
        when(sampleProcessMapper.otherSampleUses(4L,100L,SampleProcessStatusEnum.IN_PROGRESS.getValue())).thenReturn(List.of(999L));
        var dto=new SampleProcessItemStatusDTO();dto.setActualPlateId(4L);dto.setStatus(SampleProcessStatusEnum.IN_PROGRESS.getValue());
        assertThrows(BusinessException.class,()->service.updateProcessItemStatus(10L,100L,dto));
        verify(sampleProcessMapper,never()).updateById(any(SalesSampleProcess.class));
    }

    @Test void completedReplayKeepsHistoricalSnapshotAndCannotChangePlate() {
        var row=process(SampleProcessStatusEnum.COMPLETED);row.setActualPlateId(4L);row.setExecutionSnapshot("{\"plateNo\":\"original\"}");selected(row);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order());when(sampleProcessMapper.selectForUpdate(100L)).thenReturn(row);
        when(sampleProcessMapper.lockSampleProfile(10L)).thenReturn(order());
        var dto=new SampleProcessItemStatusDTO();dto.setStatus(SampleProcessStatusEnum.COMPLETED.getValue());dto.setActualPlateId(4L);
        assertEquals("{\"plateNo\":\"original\"}",service.updateProcessItemStatus(10L,100L,dto).getExecutionSnapshot());
        dto.setActualPlateId(5L);assertThrows(BusinessException.class,()->service.updateProcessItemStatus(10L,100L,dto));
        verifyNoInteractions(screenService);verify(sampleProcessMapper,never()).updateById(any(SalesSampleProcess.class));
    }

    @Test void executedPrintCannotBeRelabelledAsAssemblyByPlanSave() {
        var old=process(SampleProcessStatusEnum.IN_PROGRESS);old.setActualPlateId(4L);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order());
        when(sampleProcessMapper.lockSampleProfile(10L)).thenReturn(order());
        when(sampleProcessMapper.selectList(any())).thenReturn(List.of(old));
        when(sampleProcessMapper.planningVersion(200L,10L)).thenReturn(Map.of("artworkId",7L));
        var item=new SampleProcessPlanDTO.Item();item.setProcessId(100L);item.setProcessName("改成组装");item.setMajorCategory("ASSEMBLY");item.setArtworkVersionId(200L);
        var dto=new SampleProcessPlanDTO();dto.setItems(List.of(item));
        assertThrows(BusinessException.class,()->service.saveProcessPlan(10L,dto));
        verify(sampleProcessMapper,never()).updateById(any(SalesSampleProcess.class));verify(sampleProcessMapper,never()).deleteById(anyLong());
    }
}
