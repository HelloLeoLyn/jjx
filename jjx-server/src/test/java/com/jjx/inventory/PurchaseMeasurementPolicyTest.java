package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.measurement.*;
import com.jjx.inventory.service.impl.InventoryMeasurementSnapshotService;
import com.jjx.purchase.domain.dto.*;
import com.jjx.purchase.domain.entity.PurchaseOrderItem;
import com.jjx.purchase.mapper.PurchaseOrderItemMapper;
import com.jjx.purchase.service.impl.PurchaseOrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 采购策略冻结与实际交付参数；收货适配使用替身依赖，库存事务见生命周期测试。 */
class PurchaseMeasurementPolicyTest extends RollFixture {
    PurchaseOrderServiceImpl purchasing() throws Exception {
        var service=construct(PurchaseOrderServiceImpl.class);
        ReflectionTestUtils.setField(service,"materialMapper",materials);
        return service;
    }
    @Test void sheetPurchasePrices984PiecesBy88Point3386SquareMetresAndIgnoresInjectedSnapshot() throws Exception {
        var material=materials.selectById(11L);material.setUnit("PCS");
        material.setWidthMm(bd("285"));material.setLengthMm(bd("315"));
        var policy=new MeasurementPolicy("PCS","㎡",MeasurementPolicy.Mode.PIECE_AREA,null);
        material.setMeasurementPolicyJson(policy.toJson());
        var item=new PurchaseOrderItemDTO();item.setMaterialId(11L);item.setPurchaseQuantity(bd("984"));item.setUnitPrice(bd("2"));
        item.setMeasurementSnapshot("untrusted input");
        var dto=new PurchaseOrderDTO();dto.setItems(List.of(item));purchasing().previewPricing(dto);
        eq("984",item.getQuantity());eq("88.3386",item.getPriceQuantity());eq("176.68",item.getAmount());
        eq("984",item.getPurchaseQuantity());assertEquals("PCS",item.getPurchaseUnit());
        assertEquals(policy,InventoryMeasurementSnapshotService.read(item.getMeasurementSnapshot()).policy());
    }
    @Test void editingSavedOrderUsesFrozenPolicyAndDimensionsInsteadOfChangedMaster() throws Exception {
        var material=materials.selectById(11L);material.setUnit("PCS");material.setWidthMm(bd("999"));
        material.setMeasurementPolicyJson(new MeasurementPolicy("PCS","KG",MeasurementPolicy.Mode.PIECE_WEIGHT,bd("5")).toJson());
        var policy=new MeasurementPolicy("PCS","㎡",MeasurementPolicy.Mode.PIECE_AREA,null);
        var old=new PurchaseOrderItem();old.setItemId(9L);old.setOrderId(55L);old.setMaterialId(11L);old.setUnit("PCS");old.setPurchaseUnit("PCS");old.setPriceUnit("㎡");
        old.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(bd("984"),bd("285"),bd("315"),null,null,null,null))));
        var mapper=mock(PurchaseOrderItemMapper.class);when(mapper.selectById(9L)).thenReturn(old);
        var service=purchasing();ReflectionTestUtils.setField(service,"orderItemMapper",mapper);
        var item=new PurchaseOrderItemDTO();item.setItemId(9L);item.setMaterialId(11L);
        item.setPurchaseQuantity(bd("984"));item.setUnitPrice(bd("2"));
        var dto=new PurchaseOrderDTO();dto.setOrderId(55L);dto.setItems(List.of(item));service.previewPricing(dto);
        eq("88.3386",item.getPriceQuantity());assertEquals("㎡",item.getPriceUnit());eq("285",item.getPieceWidthMm());
    }
    @Test void legacyStoredLineDoesNotAdoptNewMasterPolicyOrNewDimensions() throws Exception {
        var material=materials.selectById(11L);material.setUnit("PCS");material.setWidthMm(bd("999"));
        material.setMeasurementPolicyJson(new MeasurementPolicy("PCS","KG",MeasurementPolicy.Mode.PIECE_WEIGHT,bd("5")).toJson());
        var old=new PurchaseOrderItem();old.setItemId(9L);old.setOrderId(55L);old.setMaterialId(11L);
        old.setUnit("PCS");old.setPurchaseUnit("PCS");old.setPriceUnit("㎡");old.setPieceWidthMm(bd("285"));old.setPieceLengthMm(bd("315"));
        var mapper=mock(PurchaseOrderItemMapper.class);when(mapper.selectById(9L)).thenReturn(old);
        var service=purchasing();ReflectionTestUtils.setField(service,"orderItemMapper",mapper);
        var item=new PurchaseOrderItemDTO();item.setItemId(9L);item.setMaterialId(11L);item.setPurchaseQuantity(bd("984"));item.setUnitPrice(bd("2"));
        var dto=new PurchaseOrderDTO();dto.setOrderId(55L);dto.setItems(List.of(item));service.previewPricing(dto);
        assertNull(item.getMeasurementSnapshot());eq("88.3386",item.getPriceQuantity());eq("285",item.getPieceWidthMm());
        assertEquals("㎡",item.getPriceUnit());
        dto.setOrderId(56L);assertThrows(BusinessException.class,()->service.previewPricing(dto));
    }
    @Test void unchangedMeasuredOrderKeepsFrozenNetWeightButChangedQuantityMustBeReconfirmed() throws Exception {
        var material=materials.selectById(11L);material.setUnit("KG");
        material.setMeasurementPolicyJson(new MeasurementPolicy("ROLL","KG",MeasurementPolicy.Mode.ROLL_WEIGHT,null).toJson());
        var old=new PurchaseOrderItem();old.setItemId(9L);old.setOrderId(55L);old.setMaterialId(11L);
        old.setUnit("KG");old.setPurchaseUnit("ROLL");old.setPriceUnit("KG");
        var policy=new MeasurementPolicy("ROLL","KG",MeasurementPolicy.Mode.MEASURED_WEIGHT,null);
        old.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(bd("2"),bd("195"),null,bd("50"),null,null,bd("10")))));
        var mapper=mock(PurchaseOrderItemMapper.class);when(mapper.selectById(9L)).thenReturn(old);
        var service=purchasing();ReflectionTestUtils.setField(service,"orderItemMapper",mapper);
        var item=new PurchaseOrderItemDTO();item.setItemId(9L);item.setMaterialId(11L);item.setPurchaseQuantity(bd("2"));item.setUnitPrice(bd("2"));
        var dto=new PurchaseOrderDTO();dto.setOrderId(55L);dto.setItems(List.of(item));service.previewPricing(dto);
        eq("10",item.getMeasurementActual().actualWeightKg());eq("20",item.getAmount());eq("2",item.getMeasurementActual().physicalQuantity());
        var original=item.getMeasurementActual();item.setPurchaseQuantity(bd("1"));
        assertThrows(BusinessException.class,()->service.previewPricing(dto));
        item.setMeasurementActual(null);
        assertThrows(BusinessException.class,()->service.previewPricing(dto));
        item.setPurchaseQuantity(bd("2"));item.setMeasurementActual(new MeasurementInput(original.physicalQuantity(),original.widthMm(),null,original.rollLengthM(),null,null,null));
        assertThrows(BusinessException.class,()->service.previewPricing(dto));
    }
    @Test void legacyReceiptEndpointRejectsForeignOrMeasuredLinesBeforeAnyReceiptWrite() throws Exception {
        var service=purchasing();var orders=mock(com.jjx.purchase.mapper.PurchaseOrderMapper.class);
        var order=new com.jjx.purchase.domain.entity.PurchaseOrder();order.setOrderId(55L);
        order.setApprovalStatus(com.jjx.common.enums.ApproveStatusEnum.APPROVED.getValue());
        when(orders.selectById(55L)).thenReturn(order);ReflectionTestUtils.setField(service,"orderMapper",orders);
        var mapper=mock(PurchaseOrderItemMapper.class);var line=new PurchaseOrderItem();line.setItemId(9L);line.setOrderId(56L);
        when(mapper.selectById(9L)).thenReturn(line);ReflectionTestUtils.setField(service,"orderItemMapper",mapper);
        assertThrows(BusinessException.class,()->service.receiveOrderItem(55L,9L,bd("1")));
        line.setOrderId(55L);line.setMeasurementSnapshot("{}");
        assertThrows(BusinessException.class,()->service.receiveOrderItem(55L,9L,bd("1")));
        verify(mapper,never()).updateById(any(PurchaseOrderItem.class));
    }
    @Test void kilogramRollReceiptUsesActualWeightAndLengthAndRequiresEveryRoll() throws Exception {
        var source=new PurchaseOrderItem();source.setItemId(9L);source.setMaterialId(11L);source.setUnit("KG");source.setPriceUnit("KG");source.setAmount(bd("100"));
        var policy=new MeasurementPolicy("ROLL","KG",MeasurementPolicy.Mode.MEASURED_WEIGHT,null);
        source.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(bd("2"),bd("1220"),null,bd("100"),null,null,bd("20")))));
        var input=new PurchaseOrderReceiveDTO.ReceiveItemDTO();input.setReceivedQuantity(bd("6"));
        var service=purchasing();
        assertThrows(BusinessException.class,()->ReflectionTestUtils.invokeMethod(service,"measurementReceipt",source,input));
        var roll=new PurchaseOrderReceiveDTO.RollReceiveDTO();roll.setRollWidthMm(bd("1220"));roll.setRollLengthM(bd("40"));roll.setActualWeightKg(bd("6"));input.setRolls(List.of(roll));
        List<com.jjx.inventory.domain.InventoryInboundItem> lines=ReflectionTestUtils.invokeMethod(service,"measurementReceipt",source,input);
        var actual=lines.get(0);eq("6",actual.getQuantity());eq("30",actual.getAmount());eq("5",actual.getUnitPrice());
        var frozen=InventoryMeasurementSnapshotService.read(actual.getMeasurementSnapshot());eq("1",frozen.result().physicalQuantity());
        eq("48.8",MeasurementCalculator.inventoryArea(frozen.result()));eq("40",frozen.result().actual().rollLengthM());
        input.setReceivedQuantity(bd("7"));
        assertThrows(BusinessException.class,()->ReflectionTestUtils.invokeMethod(service,"measurementReceipt",source,input));
    }
    @Test void rollPurchaseOnMetreMaterialKeepsMetresAndPricesByArea() throws Exception {
        // 基本单位=米、计价=㎡、采购单位=卷：数量记 1 卷 × 100m = 100 米，面积只做计价
        var material=materials.selectById(11L);material.setUnit("M");material.setWidthMm(bd("195"));
        var policy=new MeasurementPolicy("M","㎡",MeasurementPolicy.Mode.LENGTH_AREA,null);
        material.setMeasurementPolicyJson(policy.toJson());
        var item=new PurchaseOrderItemDTO();item.setMaterialId(11L);
        item.setPurchaseUnit("ROLL");item.setPriceUnit("㎡");item.setUnitPrice(bd("75"));
        item.setPurchaseQuantity(bd("1"));item.setRollWidthMm(bd("195"));item.setRollLengthM(bd("100"));
        var dto=new PurchaseOrderDTO();dto.setItems(List.of(item));purchasing().previewPricing(dto);
        eq("100",item.getQuantity());eq("19.5",item.getPriceQuantity());eq("1462.50",item.getAmount());
        assertEquals("M",item.getUnit());assertEquals("ROLL",item.getPurchaseUnit());assertEquals("㎡",item.getPriceUnit());
        assertEquals(1,item.getRollCount());eq("195",item.getRollWidthMm());eq("100",item.getRollLengthM());
        assertEquals(policy,InventoryMeasurementSnapshotService.read(item.getMeasurementSnapshot()).policy());
    }
    @Test void metreRollReceiptWritesOneLineInMetresAndPricesByArea() throws Exception {
        // 收货按卷录 1 卷：入库明细 1 行、数量 100 米、金额按面积精确
        var policy=new MeasurementPolicy("M","㎡",MeasurementPolicy.Mode.LENGTH_AREA,null);
        var source=new PurchaseOrderItem();source.setItemId(9L);source.setMaterialId(11L);
        source.setUnit("M");source.setPurchaseUnit("ROLL");source.setPriceUnit("㎡");source.setAmount(bd("1462.50"));
        source.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(bd("100"),bd("195"),null,bd("100"),null,null,null))));
        var roll=new PurchaseOrderReceiveDTO.RollReceiveDTO();roll.setRollWidthMm(bd("195"));roll.setRollLengthM(bd("100"));
        var input=new PurchaseOrderReceiveDTO.ReceiveItemDTO();input.setRolls(List.of(roll));
        List<com.jjx.inventory.domain.InventoryInboundItem> lines=ReflectionTestUtils.invokeMethod(purchasing(),"measurementReceipt",source,input);
        assertEquals(1,lines.size());
        var line=lines.get(0);
        eq("100",line.getQuantity());eq("1462.50",line.getAmount());eq("14.625",line.getUnitPrice());
        eq("195",line.getRollWidthMm());eq("100",line.getRollLengthM());
        var frozen=InventoryMeasurementSnapshotService.read(line.getMeasurementSnapshot());
        eq("100",frozen.result().physicalQuantity());eq("19.5",frozen.result().priceQuantity());
        eq("100",input.getReceivedQuantity());
    }
}
