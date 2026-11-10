package com.jjx.sales.service.impl;

import com.jjx.sales.domain.entity.*;
import com.jjx.sales.dto.save.SampleProcessPlanDTO;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.*;
import com.jjx.inventory.service.impl.InventoryAlertServiceImpl;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SampleOrderProcessBomTest {
    @Mock OrderMapper orderMapper;
    @Mock SalesSampleProcessMapper sampleProcessMapper;
    @Mock SalesSampleBomMapper sampleBomMapper;
    @Mock InventoryAlertServiceImpl inventoryAlertService;
    @Mock com.jjx.product.mapper.ProductStandardProcessMapper standardProcessMapper;
    @InjectMocks SampleOrderServiceImpl service;
    final List<SalesSampleBom> bom = new ArrayList<>();
    final List<SalesSampleProcess> plan = new ArrayList<>();
    long sequence = 100;

    @BeforeEach void setup() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L); order.setDeleted(0); order.setSampleRound(1);
        order.setSampleStatus(SampleOrderStatusEnum.ENGINEERING.getValue());
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(sampleBomMapper.selectByOrderId(1L)).thenAnswer(i -> new ArrayList<>(bom));
        lenient().when(sampleBomMapper.insert(any(SalesSampleBom.class))).thenAnswer(i -> {
            SalesSampleBom row = i.getArgument(0); row.setBomId(++sequence); bom.add(row); return 1;
        });
        lenient().when(sampleBomMapper.deleteById(anyLong())).thenAnswer(i -> {
            Long id = i.getArgument(0); return bom.removeIf(row -> id.equals(row.getBomId())) ? 1 : 0;
        });
    }

    private void mockPlan() {
        when(sampleProcessMapper.selectList(any())).thenAnswer(i -> new ArrayList<>(plan));
        when(sampleProcessMapper.delete(any())).thenAnswer(i -> { plan.clear(); return 1; });
        when(sampleProcessMapper.insert(any(SalesSampleProcess.class))).thenAnswer(i -> {
            SalesSampleProcess row = i.getArgument(0); row.setProcessId(++sequence); plan.add(row); return 1;
        });
        when(sampleProcessMapper.selectByOrderAndRound(1L, 1)).thenAnswer(i -> new ArrayList<>(plan));
    }

    private SampleProcessPlanDTO dto(String materials) {
        var item = new SampleProcessPlanDTO.Item();
        item.setProcessName("保护膜"); item.setStdProcessId(22L); item.setMaterials(materials);
        item.setCustomProcessParams("{\"sampleUic\":{\"key\":\"owner-a\"}}");
        var dto = new SampleProcessPlanDTO(); dto.setItems(List.of(item)); dto.setRoundNo(1);
        return dto;
    }

    private SalesSampleBom independent() {
        var row = new SalesSampleBom(); row.setBomId(7L); row.setOrderId(1L); row.setRoundNo(1);
        row.setLayerName(""); row.setMaterialName("独立材料"); row.setUnit("PCS");
        row.setBaseQty(BigDecimal.ONE); row.setModuleQty(BigDecimal.ONE); row.setQuantity(new BigDecimal("1.0000"));
        row.setProjectIds("[22]"); bom.add(row); return row;
    }

    @Test void repeatedSchemeSaveDeduplicatesAndKeepsIndependentIdentityAndRatio() {
        mockPlan(); var standalone = independent();
        String material = "{\"materialId\":113,\"name\":\"PE\",\"baseQty\":1,\"moduleQty\":3,\"qty\":0.3333,\"processItemKey\":\"owner-a\"}";
        var dto = dto("[" + material + "," + material + "]");
        dto.setStandaloneMaterials(List.of(standalone));
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn("codex");
            service.saveProcessPlan(1L, dto); service.saveProcessPlan(1L, dto);
        }
        assertEquals(2, bom.size()); assertSame(standalone, bom.getFirst()); assertEquals(7L, standalone.getBomId());
        var process = bom.getLast(); assertEquals(new BigDecimal("0.3333"), process.getQuantity());
        assertEquals(BigDecimal.ONE, process.getBaseQty()); assertEquals(new BigDecimal("3"), process.getModuleQty());
        assertTrue(process.getRemark().startsWith("sample-process-material:"));
        assertEquals("[22]", process.getProjectIds());
        assertEquals(BigDecimal.ONE, InventoryAlertServiceImpl.sampleDemand(process, 2));
    }

    @Test void emptyMaterialsRemoveOnlyProcessRows() {
        mockPlan(); var standalone = independent();
        var stale = new SalesSampleBom(); stale.setBomId(8L); stale.setRoundNo(1); stale.setRemark("sample-process-material:old"); bom.add(stale);
        try (var security = mockStatic(SecurityUtils.class)) { service.saveProcessPlan(1L, dto("[]")); }
        assertEquals(List.of(standalone), bom);
    }

    @Test void sharedLegacyJsonIgnoresMaterialsAssignedToOtherSubprocess() {
        mockPlan();
        try (var security = mockStatic(SecurityUtils.class)) {
            service.saveProcessPlan(1L, dto("[{\"name\":\"other\",\"qty\":1,\"processItemKey\":\"owner-b\"}]"));
        }
        assertTrue(bom.isEmpty());
    }

    @Test void distinctProcessesKeepTheirOwnMaterialsAndDemand() {
        mockPlan();
        var dto = dto("[{\"materialId\":113,\"name\":\"PE\",\"baseQty\":1,\"moduleQty\":3,\"projectIds\":[22]}]");
        var second = new SampleProcessPlanDTO.Item(); second.setStdProcessId(24L); second.setProcessName("冲孔");
        second.setMaterials("[{\"materialId\":109,\"name\":\"PC\",\"baseQty\":1,\"moduleQty\":6,\"projectIds\":[24]}]");
        dto.setItems(List.of(dto.getItems().getFirst(), second));
        try (var security = mockStatic(SecurityUtils.class)) { service.saveProcessPlan(1L, dto); }
        assertEquals(2, bom.size());
        assertEquals(List.of("[22]", "[24]"), bom.stream().map(SalesSampleBom::getProjectIds).toList());
        assertEquals(List.of(BigDecimal.ONE, BigDecimal.ONE), bom.stream().map(row -> InventoryAlertServiceImpl.sampleDemand(row, 2)).toList());
    }

    @Test void standaloneSaveNeverDeletesProcessRows() {
        var process = new SalesSampleBom(); process.setBomId(8L); process.setRoundNo(1); process.setRemark("sample-process-material:100"); bom.add(process);
        independent();
        try (var security = mockStatic(SecurityUtils.class)) { service.saveSampleBom(1L, 1, List.of()); }
        assertEquals(List.of(process), bom);
    }
}
