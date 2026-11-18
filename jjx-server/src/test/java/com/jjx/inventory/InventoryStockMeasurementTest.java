package com.jjx.inventory;

import com.jjx.inventory.domain.*;
import com.jjx.inventory.dto.vo.StockVO;
import com.jjx.inventory.measurement.*;
import com.jjx.inventory.service.impl.InventoryMeasurementSnapshotService;
import com.jjx.inventory.service.impl.InventoryStockMeasurement;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryStockMeasurementTest {
    private static BigDecimal bd(String n){return new BigDecimal(n);}
    private static void eq(String n,BigDecimal actual){assertNotNull(actual);assertEquals(0,bd(n).compareTo(actual));}
    private static StockVO summary(String unit,String quantity){var vo=new StockVO();vo.setUnit(unit);vo.setTotalQuantity(bd(quantity));return vo;}
    private static InventoryStockItem batch(String quantity,String reserved,String roll){var s=new InventoryStockItem();s.setQuantity(bd(quantity));s.setReservedQuantity(bd(reserved));s.setRollNo(roll);return s;}
    private static InventoryMaterial material(String form){var m=new InventoryMaterial();m.setMaterialForm(form);return m;}

    @Test void rm350ExistingRollIdentitiesGiveTwoRollsAnd244SquareMetresWithoutChangingPolicy() {
        var vo=summary("㎡","244");var m=material("FILM");m.setWidthMm(bd("1220"));m.setLengthMm(bd("100000"));
        var a=batch("122","0","PO-R2");var b=batch("122","122","PO-R3");
        InventoryStockMeasurement.enrich(vo,m,List.of(a,b));
        assertEquals("ROLL",vo.getPhysicalUnit());eq("2",vo.getPhysicalQuantity());
        eq("1",vo.getReservedPhysicalQuantity());eq("1",vo.getAvailablePhysicalQuantity());eq("244",vo.getInventoryArea());
        assertNull(m.getMeasurementPolicyJson());assertNull(a.getMeasurementSnapshot());
        assertNull(vo.getPriceQuantity());assertNull(vo.getPriceUnit());
    }
    @Test void rm352ExistingPieceBalancesUseGeometryWithoutPurchaseLossAllocation() {
        var vo=summary("PCS","984");var m=material("FILM");m.setWidthMm(bd("285"));m.setLengthMm(bd("315"));
        InventoryStockMeasurement.enrich(vo,m,List.of(batch("634","50",null),batch("350","0",null)));
        assertEquals("PCS",vo.getPhysicalUnit());eq("984",vo.getPhysicalQuantity());eq("50",vo.getReservedPhysicalQuantity());
        eq("934",vo.getAvailablePhysicalQuantity());eq("88.3386",vo.getInventoryArea());assertNull(m.getMeasurementPolicyJson());
        assertNull(vo.getPriceQuantity());assertNull(vo.getPriceUnit());
    }
    @Test void remainingRollIsOnePhysicalRollAndUsesFrozenActual40MetresInsteadOf100MetreArchive() {
        var vo=summary("㎡","48.8");var m=material("FILM");m.setLengthMm(bd("100000"));
        var row=batch("48.8","48.8","RM350-OLD-ROLL");
        var policy=new MeasurementPolicy("ROLL","㎡",MeasurementPolicy.Mode.ROLL_AREA,null);
        row.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(BigDecimal.ONE,bd("1220"),null,bd("40"),null,null,null))));
        InventoryStockMeasurement.enrich(vo,m,List.of(row));
        eq("1",vo.getPhysicalQuantity());eq("1",vo.getReservedPhysicalQuantity());eq("0",vo.getAvailablePhysicalQuantity());
        eq("48.8",vo.getInventoryArea());eq("48.8",vo.getPriceQuantity());
    }
    @Test void explicitWeightSnapshotShowsCountAndKilogramsButComponentPackagingDoesNotCreateArea() {
        var vo=summary("KG","5");var m=material("COMPONENT");m.setWidthMm(bd("100"));m.setLengthMm(bd("200"));
        var row=batch("5","1",null);var policy=new MeasurementPolicy("PCS","KG",MeasurementPolicy.Mode.PIECE_WEIGHT,bd("50"));
        row.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(bd("100"),bd("100"),bd("200"),null,null,null,null))));
        InventoryStockMeasurement.enrich(vo,m,List.of(row));
        eq("100",vo.getPhysicalQuantity());eq("20",vo.getReservedPhysicalQuantity());eq("80",vo.getAvailablePhysicalQuantity());
        eq("5",vo.getPriceQuantity());assertEquals("KG",vo.getPriceUnit());assertNull(vo.getInventoryArea());
    }
    @Test void measuredWeightRollShowsExplicitActualAreaAndNetWeight() {
        var vo=summary("KG","6");var row=batch("6","0","WEIGHED-ROLL");
        var policy=new MeasurementPolicy("ROLL","KG",MeasurementPolicy.Mode.MEASURED_WEIGHT,null);
        row.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(BigDecimal.ONE,bd("1220"),null,bd("40"),null,null,bd("6")))));
        InventoryStockMeasurement.enrich(vo,material("FILM"),List.of(row));
        eq("1",vo.getPhysicalQuantity());eq("6",vo.getPriceQuantity());eq("48.8",vo.getInventoryArea());
    }
    @Test void nonAreaLegacyComponentAndRubberDoNotGainAreaFromDimensions() {
        for(String form:List.of("COMPONENT","RUBBER")) {
            var vo=summary("PCS","100");var m=material(form);m.setWidthMm(bd("10"));m.setLengthMm(bd("20"));
            InventoryStockMeasurement.enrich(vo,m,List.of(batch("100","0",null)));
            eq("100",vo.getPhysicalQuantity());assertNull(vo.getInventoryArea());
        }
    }
    @Test void explicitRollGrammageFormulaCarriesAreaEvenWithoutFormDefaults() {
        var vo=summary("KG","14.64");var row=batch("14.64","0","WEIGHT-ROLL");
        var policy=new MeasurementPolicy("ROLL","KG",MeasurementPolicy.Mode.ROLL_WEIGHT,null);
        row.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(BigDecimal.ONE,bd("1220"),null,bd("40"),null,bd("300"),null))));
        InventoryStockMeasurement.enrich(vo,null,List.of(row));
        eq("1",vo.getPhysicalQuantity());eq("14.64",vo.getPriceQuantity());eq("48.8",vo.getInventoryArea());
    }
    @Test void frozenIntegerPiecesSurviveTerminalFourDecimalKilogramRounding() {
        var vo=summary("KG","8.8339");var row=batch("8.8339","0",null);
        var policy=new MeasurementPolicy("PCS","KG",MeasurementPolicy.Mode.PIECE_AREA_WEIGHT,null);
        row.setMeasurementSnapshot(InventoryMeasurementSnapshotService.freeze(policy,MeasurementCalculator.calculate(policy,
            new MeasurementInput(bd("984"),bd("285"),bd("315"),null,null,bd("100"),null))));
        InventoryStockMeasurement.enrich(vo,null,List.of(row));
        eq("984",vo.getPhysicalQuantity());eq("88.3386",vo.getInventoryArea());eq("8.83386",vo.getPriceQuantity());
    }
    @Test void inconsistentSummaryOrMixedUnconvertibleBatchesDoNotShowPartialDerivedTotals() {
        var vo=summary("㎡","244");InventoryStockMeasurement.enrich(vo,material("FILM"),List.of(batch("122","0","R1")));
        assertNull(vo.getPhysicalQuantity());assertNull(vo.getInventoryArea());assertNotNull(vo.getMeasurementMessage());
        var mixed=summary("㎡","244");InventoryStockMeasurement.enrich(mixed,material("FILM"),List.of(batch("122","0","R1"),batch("122","0",null)));
        assertNull(mixed.getPhysicalQuantity());eq("244",mixed.getInventoryArea());
    }
}
