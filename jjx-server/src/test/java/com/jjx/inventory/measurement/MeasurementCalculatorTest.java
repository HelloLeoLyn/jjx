package com.jjx.inventory.measurement;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.MaterialAttributeSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeasurementCalculatorTest {
    static BigDecimal n(String s) { return new BigDecimal(s); }
    static MeasurementInput input(String qty, String width, String length, String roll, String weight, String grammage, String measured) {
        return new MeasurementInput(n(qty), width==null?null:n(width), length==null?null:n(length), roll==null?null:n(roll), weight==null?null:n(weight), grammage==null?null:n(grammage), measured==null?null:n(measured));
    }
    static MeasurementPolicy policy(String physical, String priced, MeasurementPolicy.Mode mode) { return new MeasurementPolicy(physical, priced, mode, null); }
    static void eq(String expected, BigDecimal actual) { assertEquals(0,n(expected).compareTo(actual)); }
    @Test void threeRollsAndActualFortyMetreReturnUseSameFormulaWithoutNominalLengthLeak() {
        var p=policy("ROLL","㎡",MeasurementPolicy.Mode.ROLL_AREA);
        eq("366",MeasurementCalculator.calculate(p,input("3","1220",null,"100",null,null,null)).priceQuantity());
        eq("48.8",MeasurementCalculator.calculate(p,input("1","1220",null,"40",null,null,null)).priceQuantity());
        assertThrows(BusinessException.class,()->MeasurementCalculator.calculate(p,input("1","1220",null,null,null,null,null)));
    }
    @Test void piecesKeepOriginalDimensionsInsteadOfRoundedSinglePieceFactor() {
        var r=MeasurementCalculator.calculate(policy("PCS","㎡",MeasurementPolicy.Mode.PIECE_AREA),input("984","285","315",null,null,null,null));
        eq("88.3386",r.priceQuantity());eq("984",MeasurementCalculator.ledgerQuantity("PCS",r));eq("88.3386",MeasurementCalculator.ledgerQuantity("㎡",r));
        assertThrows(BusinessException.class,()->MeasurementCalculator.ledgerQuantity("KG",r));
    }
    @Test void metresAndWeightModesAreExplicit() {
        eq("3.9",MeasurementCalculator.calculate(policy("M","㎡",MeasurementPolicy.Mode.LENGTH_AREA),input("20","195",null,null,null,null,null)).priceQuantity());
        eq("2.5",MeasurementCalculator.calculate(policy("PCS","KG",MeasurementPolicy.Mode.PIECE_WEIGHT),input("1000",null,null,null,"2.5",null,null)).priceQuantity());
        eq("1.8",MeasurementCalculator.calculate(policy("㎡","KG",MeasurementPolicy.Mode.AREA_WEIGHT),input("10",null,null,null,null,"180",null)).priceQuantity());
        eq("6.125",MeasurementCalculator.calculate(policy("ROLL","KG",MeasurementPolicy.Mode.MEASURED_WEIGHT),input("1",null,null,null,null,null,"6.125")).priceQuantity());
    }
    @Test void sameUnitAndZeroQuantityStayZero() {
        eq("500",MeasurementCalculator.calculate(policy("PCS","PCS",MeasurementPolicy.Mode.SAME),input("500",null,null,null,null,null,null)).priceQuantity());
        eq("0",MeasurementCalculator.calculate(policy("PCS","㎡",MeasurementPolicy.Mode.PIECE_AREA),input("0","285","315",null,null,null,null)).priceQuantity());
    }
    @Test void missingParametersInvalidUnitsNegativeAndFractionalCountsFailClosed() {
        var roll=policy("ROLL","㎡",MeasurementPolicy.Mode.ROLL_AREA);
        assertThrows(BusinessException.class,()->MeasurementCalculator.calculate(roll,input("0.5","1220",null,"100",null,null,null)));
        assertThrows(BusinessException.class,()->MeasurementCalculator.calculate(roll,input("-1","1220",null,"100",null,null,null)));
        assertThrows(BusinessException.class,()->MeasurementCalculator.calculate(roll,input("1","0",null,"100",null,null,null)));
        assertThrows(BusinessException.class,()->policy("PCS","KG",MeasurementPolicy.Mode.PIECE_AREA).normalized());
        assertThrows(BusinessException.class,()->MeasurementCalculator.calculate(policy("PCS","KG",MeasurementPolicy.Mode.PIECE_WEIGHT),input("1",null,null,null,null,null,null)));
        assertThrows(BusinessException.class,()->MeasurementCalculator.calculate(policy("ROLL","KG",MeasurementPolicy.Mode.MEASURED_WEIGHT),input("1",null,null,null,null,null,null)));
    }
    @Test void materialDefaultsAreOverriddenOnlyByExplicitActualsAndResultFreezesWeightBasis() {
        var m=new InventoryMaterial();m.setMaterialId(1L);m.setWidthMm(n("1220"));m.setLengthMm(n("100000"));
        m.setMeasurementPolicyJson(policy("ROLL","㎡",MeasurementPolicy.Mode.ROLL_AREA).toJson());
        var mapper=mock(InventoryMaterialMapper.class);when(mapper.selectById(1L)).thenReturn(m);var service=new MaterialMeasurementService(mapper);
        eq("366",service.preview(new MaterialMeasurementService.PreviewRequest(1L,null,input("3",null,null,null,null,null,null))).priceQuantity());
        eq("48.8",service.preview(new MaterialMeasurementService.PreviewRequest(1L,null,input("1",null,null,"40",null,null,null))).priceQuantity());
        assertThrows(BusinessException.class,()->MaterialMeasurementService.actual(m,input("1",null,null,null,null,null,null),false));
        var p=new MeasurementPolicy("PCS","KG",MeasurementPolicy.Mode.PIECE_WEIGHT,n("2.5"));
        var r=MeasurementCalculator.calculate(p,input("1000",null,null,null,null,null,null));eq("2.5",r.actual().unitWeightG());
    }
    @Test void policyAndCategoryDefaultsRoundTripAndNeverChangeLedgerUnit() throws Exception {
        var p=policy("ROLL","㎡",MeasurementPolicy.Mode.ROLL_AREA);assertEquals(p,MeasurementPolicy.parse(p.toJson()));assertNull(MeasurementPolicy.parse(null));
        p.validateLedgerUnit("㎡");assertThrows(BusinessException.class,()->p.validateLedgerUnit("KG"));
        assertThrows(BusinessException.class,()->MeasurementPolicy.parse("{}"));assertThrows(BusinessException.class,()->MeasurementPolicy.parse("null"));
        var schema=MaterialAttributeSchema.parse("{\"version\":1,\"meta_options\":[],\"fields\":[],\"measurement_defaults\":"+p.toJson()+"}",new ObjectMapper());
        assertEquals(p,schema.measurementDefaults());
        assertNull(MaterialAttributeSchema.parse("{\"version\":1,\"meta_options\":[],\"fields\":[]}",new ObjectMapper()).measurementDefaults());
    }
}
