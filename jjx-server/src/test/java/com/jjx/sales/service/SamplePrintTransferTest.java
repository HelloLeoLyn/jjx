package com.jjx.sales.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.printing.artwork.ArtworkService;
import com.jjx.engineering.printing.screen.ScreenService;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.dto.transfer.SampleTransferConfirmDTO;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.enums.SampleRoundResultEnum;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.impl.SampleOrderServiceImpl;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.product.service.IEngineeringRoutingService;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
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
class SamplePrintTransferTest {
    @Mock OrderMapper orderMapper;
    @Mock SalesSampleRoundMapper sampleRoundMapper;
    @Mock SalesSampleProcessMapper sampleProcessMapper;
    @Mock SalesSampleTransferMapper sampleTransferMapper;
    @Mock ProductMapper productMapper;
    @Mock ProductStandardProcessMapper standardProcessMapper;
    @Mock InventoryMaterialMapper inventoryMaterialMapper;
    @Mock RedisSequenceService redisSequenceService;
    @Mock ISalesOrderProductService orderProductService;
    @Mock IEngineeringBomService bomService;
    @Mock IEngineeringRoutingService routingService;
    @Mock ArtworkService artworkService;
    @Mock ScreenService screenService;
    @Spy ObjectMapper objectMapper=new ObjectMapper().findAndRegisterModules();
    @InjectMocks SampleOrderServiceImpl service;
    @BeforeEach void injectSharedRules() {
        ReflectionTestUtils.setField(service,"artworkService",artworkService);
        ReflectionTestUtils.setField(service,"screenService",screenService);
    }

    SalesSampleRound round(long id,int number,SampleRoundResultEnum result,long version) throws Exception {
        var process=new SalesSampleProcess();process.setProcessId(id*10);process.setOrderId(10L);process.setRoundNo(number);
        process.setMajorCategory("PRINT");process.setProcessName("面板印刷");process.setProcessOrder(1);
        process.setArtworkVersionId(version);process.setCustomProcessParams("{\"colorNo\":\"黑\"}");process.setPrintRequirements("{\"meshMin\":100}");
        var round=new SalesSampleRound();round.setRoundId(id);round.setOrderId(10L);round.setRoundNo(number);round.setResult(result.getValue());
        round.setProcessSnapshot(objectMapper.writeValueAsString(List.of(process)));round.setBomSnapshot("[]");return round;
    }
    SampleTransferConfirmDTO request() {
        var dto=new SampleTransferConfirmDTO();dto.setOrderId(10L);dto.setRoundId(20L);dto.setConfirmedSchemeId(20L);dto.setRequestId("p09-request");return dto;
    }

    @Test void confirmedSecondRoundTransfersItsVersionEvenWithThirdRoundInProgress() throws Exception {
        var order=new SalesOrder();order.setOrderId(10L);order.setDeleted(0);order.setSampleRound(3);order.setSampleStatus(SampleOrderStatusEnum.ENGINEERING.getValue());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);when(orderMapper.selectById(10L)).thenReturn(order);
        when(sampleProcessMapper.lockSampleProfile(10L)).thenReturn(order);
        when(sampleProcessMapper.lockArtworkVersion(200L)).thenReturn(200L);
        var rounds=List.of(round(10,1,SampleRoundResultEnum.REJECTED,100),round(20,2,SampleRoundResultEnum.CONFIRMED,200),round(30,3,SampleRoundResultEnum.PENDING,300));
        when(sampleRoundMapper.selectByOrderId(10L)).thenReturn(rounds);
        when(standardProcessMapper.selectEnabledProcesses()).thenReturn(List.of());when(inventoryMaterialMapper.selectList(any())).thenReturn(List.of());
        when(sampleProcessMapper.planningVersion(200L,10L)).thenReturn(Map.of("artworkId",5L));
        when(redisSequenceService.generateBusinessNumberByType(anyString(),anyString(),anyString(),anyInt())).thenReturn("TF-P09");
        var product=new com.jjx.product.domain.entity.Product();product.setProductId(42L);product.setProductCode("P42");
        when(productMapper.selectById(42L)).thenReturn(product);
        var line=new com.jjx.sales.domain.vo.SalesOrderProductVO();line.setProductId(42L);line.setProductCode("P42");line.setProductName("面板");
        when(orderProductService.getListByOrderId(10L)).thenReturn(List.of(line));
        var captured=ArgumentCaptor.forClass(com.jjx.product.domain.dto.EngineeringRoutingDTO.class);
        var route=new com.jjx.product.domain.vo.EngineeringRoutingVO();route.setRoutingId(70L);
        when(routingService.createRouting(captured.capture())).thenReturn(route);
        var transferred=new java.util.concurrent.atomic.AtomicReference<SalesSampleTransfer>();
        when(sampleTransferMapper.insert(any(SalesSampleTransfer.class))).thenAnswer(call->{SalesSampleTransfer record=call.getArgument(0);record.setTransferId(90L);transferred.set(record);return 1;});
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("p09-tester");
            var result=service.confirmTransfer(request());
            assertEquals(20L,result.get("roundId"));assertEquals(70L,result.get("routingId"));
            SampleTransferConfirmDTO explicit=ReflectionTestUtils.invokeMethod(service,"buildRoundTransferMappings",10L,20L);
            explicit.setRequestId("same-scheme-new-request");
            when(sampleTransferMapper.selectOne(any())).thenReturn(null,transferred.get());
            doThrow(new BusinessException("后续已停用")).when(artworkService).requireUsableVersion(200L);
            // 当前标准库已变化也不应重算首次映射；重放只读首次冻结的canonical。
            clearInvocations(standardProcessMapper,inventoryMaterialMapper);
            var replay=service.confirmTransfer(explicit);
            assertEquals(result,replay);
            verifyNoInteractions(standardProcessMapper,inventoryMaterialMapper);
        }
        assertEquals(200L,captured.getValue().getItems().getFirst().getArtworkVersionId());
        assertEquals("{\"meshMin\":100}",captured.getValue().getItems().getFirst().getPrintRequirements());
        verify(artworkService).requireUsableVersion(200L);verify(artworkService,never()).requireUsableVersion(300L);
        verify(sampleProcessMapper,never()).selectByOrderId(anyLong());verify(sampleProcessMapper).linkArtworkProduct(5L,42L);
        verify(routingService).createRouting(any());verify(sampleTransferMapper).insert(any(SalesSampleTransfer.class));
    }

    @Test void missingOrRejectedSelectionNeverFallsBackToLatest() throws Exception {
        var rounds=List.of(round(20,2,SampleRoundResultEnum.REJECTED,200),round(30,3,SampleRoundResultEnum.PENDING,300));
        when(sampleRoundMapper.selectByOrderId(10L)).thenReturn(rounds);
        assertThrows(BusinessException.class,()->ReflectionTestUtils.invokeMethod(service,"requireConfirmedRound",10L,20L));
        verifyNoInteractions(productMapper,routingService);
    }

    @Test void confirmedSnapshotCannotBeReboundByTransferPayload() {
        var canonical=request();var source=new SampleTransferConfirmDTO.ProcessMapping();source.setSampleProcessId(200L);source.setArtworkVersionId(200L);
        canonical.setProcessMappings(List.of(source));canonical.setMaterialMappings(List.of());
        var client=request();var tampered=new SampleTransferConfirmDTO.ProcessMapping();tampered.setSampleProcessId(200L);tampered.setArtworkVersionId(300L);
        client.setProcessMappings(List.of(tampered));client.setMaterialMappings(List.of());
        assertThrows(BusinessException.class,()->ReflectionTestUtils.invokeMethod(service,"mergeConfirmedMappings",canonical,client));
    }
}
