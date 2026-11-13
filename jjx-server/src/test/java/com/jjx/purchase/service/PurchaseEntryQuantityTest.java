package com.jjx.purchase.service;
import com.jjx.common.exception.BusinessException;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import com.jjx.purchase.domain.model.PurchaseQuantityPricing;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class PurchaseEntryQuantityTest {
    static BigDecimal bd(String n) { return new BigDecimal(n); }
    static void eq(String n,BigDecimal v) { assertNotNull(v); assertEquals(0,bd(n).compareTo(v)); }
    PurchaseOrderItemDTO row(String base,String purchase,String price,String qty,String unitPrice) {
        var i=new PurchaseOrderItemDTO();i.setUnit(base);i.setPurchaseUnit(purchase);i.setPriceUnit(price);
        i.setPurchaseQuantity(bd(qty));i.setQuantity(bd("999"));i.setUnitPrice(bd(unitPrice));
        i.setRollWidthMm(bd("195"));i.setRollLengthM(bd("50"));
        i.setPieceWidthMm(bd("450"));i.setPieceLengthMm(bd("225"));return i;
    }
    @Test void actualEbaDimensionsThreeRollsPricedByArea() {
        var i=row("M2","卷","㎡","3","85");i.setRollWidthMm(bd("1220"));i.setRollLengthM(bd("100"));
        PurchaseQuantityPricing.calculate(i);eq("3",i.getPurchaseQuantity());eq("366",i.getQuantity());eq("366",i.getPriceQuantity());eq("31110",i.getAmount());assertEquals("ROLL",i.getPurchaseUnit());
    }
    @Test void five3mRollsDoNotMultiplyRollCountByAreaPrice() {
        var i=row("㎡","ROLL","㎡","5","20");PurchaseQuantityPricing.calculate(i);
        eq("48.75",i.getQuantity());eq("48.75",i.getPriceQuantity());eq("975",i.getAmount());assertEquals(5,i.getRollCount());
    }
    @Test void piecesKeepCountAsStockAndAreaAsPriceQuantity() {
        var i=row("PCS","PCS","㎡","100","20");PurchaseQuantityPricing.calculate(i);
        eq("100",i.getQuantity());eq("10.125",i.getPriceQuantity());eq("202.5",i.getAmount());
    }
    @Test void areaStockCanReceiveExplicitPieceQuantity() {
        var i=row("㎡","PCS","㎡","100","20");i.setPieceWidthMm(bd("195"));i.setPieceLengthMm(bd("80"));
        PurchaseQuantityPricing.calculate(i);eq("1.56",i.getQuantity());eq("100",i.getPurchaseQuantity());eq("31.2",i.getAmount());
    }
    @Test void areaPurchaseRestoresExactWholePieces() {
        var i=row("PCS","㎡","㎡","10.125","20");PurchaseQuantityPricing.calculate(i);eq("100",i.getQuantity());eq("10.125",i.getPriceQuantity());
    }
    @Test void partialPieceCannotBeRoundedIntoWarehouse() {
        var i=row("PCS","㎡","㎡","10","20");assertTrue(assertThrows(BusinessException.class,()->PurchaseQuantityPricing.calculate(i)).getMessage().contains("不能舍入入库"));
    }
    @Test void sheetMaterialUsesOwnDimensionsNotRollBasis() {
        var i=row("SHEET","SHEET","㎡","100","85");i.setPieceWidthMm(bd("930"));i.setPieceLengthMm(bd("1830"));
        PurchaseQuantityPricing.calculate(i);eq("100",i.getQuantity());eq("170.19",i.getPriceQuantity());eq("14466.15",i.getAmount());
    }
    @Test void sameWeightUnitNeedsNoInventedDimensions() {
        var i=row("KG","KG","KG","5.5","20");i.setRollWidthMm(null);i.setRollLengthM(null);i.setPieceWidthMm(null);i.setPieceLengthMm(null);
        PurchaseQuantityPricing.calculate(i);eq("5.5",i.getQuantity());eq("110",i.getAmount());assertNull(i.getEquivalentUnitPrice());
    }
    @Test void metreAreaOnlyNeedsKnownWidth() {
        var i=row("M","M","㎡","8","20");i.setRollLengthM(null);PurchaseQuantityPricing.calculate(i);eq("1.56",i.getPriceQuantity());eq("31.2",i.getAmount());
    }
    @Test void wholeRollPriceKeepsExistingCeilingRule() {
        var i=row("㎡","㎡","ROLL","49","500");PurchaseQuantityPricing.calculate(i);eq("6",i.getPriceQuantity());eq("3000",i.getAmount());eq("49",i.getQuantity());
    }
    @Test void noIntermediateRoundingOfRepeatingPriceQuantity() {
        var i=row("㎡","㎡","M","0.2","3");i.setRollWidthMm(bd("300"));PurchaseQuantityPricing.calculate(i);eq("2",i.getAmount());eq("0.666666666667",i.getPriceQuantity());
    }
    @Test void missingSheetDimensionsCannotBeGuessedFromText() {
        var i=row("PCS","PCS","㎡","100","20");i.setPieceWidthMm(null);i.setMaterialSpec("450*225mm");assertThrows(BusinessException.class,()->PurchaseQuantityPricing.calculate(i));
    }
    @Test void unsupportedUnitPairsAreNotOneToOne() {
        for(String[] pair:new String[][]{{"KG","KG","㎡"},{"PIN","PIN","PCS"},{"㎡","PCS","ROLL"}}) {
            var i=row(pair[0],pair[1],pair[2],"5","20");assertThrows(BusinessException.class,()->PurchaseQuantityPricing.calculate(i));
        }
    }
    @Test void fractionalRollOrPieceRejectedButLengthAllowed() {
        for(String u:new String[]{"ROLL","PCS","SHEET"}) {var i=row("㎡",u,"㎡","1.5","20");assertThrows(BusinessException.class,()->PurchaseQuantityPricing.calculate(i));}
        var i=row("M","M","M","1.5","20");PurchaseQuantityPricing.calculate(i);eq("30",i.getAmount());
    }
    @Test void priceFourDecimalsAndAmountTwoDecimals() {
        var i=row("㎡","㎡","㎡","240","3.564");PurchaseQuantityPricing.calculate(i);eq("3.564",i.getUnitPrice());eq("855.36",i.getAmount());
    }
    @Test void legacyWithoutPurchaseQuantityUsesOriginalBasicQuantity() {
        var i=row("PCS",null,null,"1","3");i.setPurchaseQuantity(null);i.setQuantity(bd("5"));PurchaseQuantityPricing.calculate(i);eq("5",i.getPurchaseQuantity());eq("15",i.getAmount());
    }
    @Test void clientPriceQuantityAndAmountNeverOverrideCalculation() {
        var i=row("PCS","PCS","PCS","2","3");i.setPriceQuantity(bd("999"));i.setAmount(bd("999"));PurchaseQuantityPricing.calculate(i);eq("2",i.getPriceQuantity());eq("6",i.getAmount());
    }
    @Test void positivePurchaseCannotRoundToZeroStock() {
        var i=row("㎡","ROLL","㎡","1","20");i.setRollWidthMm(bd("0.01"));i.setRollLengthM(bd("0.0001"));
        assertTrue(assertThrows(BusinessException.class,()->PurchaseQuantityPricing.calculate(i)).getMessage().contains("小于库存精度"));
    }
    @Test void extremeExponentsRejectedBeforeRationalExpansion() {
        for(String n:new String[]{"1E+999999999","1E-999999999"}) {var i=row("㎡","㎡","㎡","1","1");i.setPurchaseQuantity(bd(n));assertThrows(BusinessException.class,()->PurchaseQuantityPricing.calculate(i));}
    }
    @Test void taxKeepsExistingUnroundedLineRuleAndRejectsInvalidRates() {
        eq("111.1968",PurchaseQuantityPricing.tax(bd("855.36"),bd("13")));
        eq("0.5",PurchaseQuantityPricing.tax(bd("100"),bd("0.5")));
        assertNull(PurchaseQuantityPricing.tax(bd("100"),null));
        assertThrows(BusinessException.class,()->PurchaseQuantityPricing.tax(bd("100"),bd("100.01")));
    }
}
