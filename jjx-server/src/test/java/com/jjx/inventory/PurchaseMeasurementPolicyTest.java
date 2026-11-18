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
        var old=new PurchaseOrderItem();old.setItemId(9L);old.setOrderId(55L);old.setMaterialId(11L);
        old.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(bd("984"),bd("285"),bd("315"),null,null,null,null))));
        var mapper=mock(PurchaseOrderItemMapper.class);when(mapper.selectById(9L)).thenReturn(old);
        var service=purchasing();ReflectionTestUtils.setField(service,"orderItemMapper",mapper);
        var item=new PurchaseOrderItemDTO();item.setItemId(9L);item.setOrderId(55L);item.setMaterialId(11L);
        item.setPurchaseQuantity(bd("984"));item.setUnitPrice(bd("2"));
        var dto=new PurchaseOrderDTO();dto.setItems(List.of(item));service.previewPricing(dto);
        eq("88.3386",item.getPriceQuantity());assertEquals("㎡",item.getPriceUnit());eq("285",item.getPieceWidthMm());
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
}
