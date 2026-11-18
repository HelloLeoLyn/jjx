package com.jjx.inventory.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.inventory.measurement.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** 业务快照适配；公式只委托公共计算器，不读取新档案覆盖历史事实。 */
public final class InventoryMeasurementSnapshotService {
    private static final ObjectMapper JSON=new ObjectMapper();
    private InventoryMeasurementSnapshotService() { }
    public record Snapshot(MeasurementPolicy policy,MeasurementResult result) { }
    public static Snapshot read(String value) {
        if(value==null || value.isBlank()) return null;
        try {
            var snapshot=JSON.readValue(value,Snapshot.class);
            if(snapshot==null || snapshot.policy()==null || snapshot.result()==null || snapshot.result().actual()==null)
                throw new IllegalArgumentException("missing measurement facts");
            var policy=snapshot.policy().normalized();var result=snapshot.result();
            if(result.physicalQuantity()==null || result.priceQuantity()==null || result.physicalQuantity().signum()<0 || result.priceQuantity().signum()<0
                || !policy.physicalUnit().equals(result.physicalUnit()) || !policy.priceUnit().equals(result.priceUnit()) || policy.mode()!=result.mode())
                throw new IllegalArgumentException("inconsistent measurement facts");
            return new Snapshot(policy,result);
        }
        catch(Exception e) {throw new BusinessException("计量快照损坏，请核实历史单据");}
    }
    public static String freeze(MeasurementPolicy policy,MeasurementResult result) {
        try {return JSON.writeValueAsString(new Snapshot(policy.normalized(),result));}
        catch(Exception e) {throw new BusinessException("计量快照生成失败");}
    }
    public static BigDecimal ledger(String snapshot,String unit) {
        return MeasurementCalculator.ledgerQuantity(unit,read(snapshot).result()).setScale(4,RoundingMode.HALF_UP);
    }
    /** 将冻结本批参数适配当前余额，卷在库即一个完整物理卷（余卷亦一卷）。 */
    public static MeasurementResult balance(String snapshot,String unit,BigDecimal quantity,boolean roll) {
        var frozen=read(snapshot);if(frozen==null)return null;
        var old=frozen.result();var a=old.actual();
        if(quantity.signum()>0 && (old.physicalQuantity().signum()<=0 || old.priceQuantity().signum()<=0))
            throw new BusinessException("非零库存缺少有效的原始计量依据");
        BigDecimal physical=null;
        BigDecimal weight=null;
        if (MeasurementCalculator.ledgerQuantity(unit,old).setScale(4,RoundingMode.HALF_UP).compareTo(quantity)==0)
            physical=old.physicalQuantity(); // 原始实物事实仍完整，不能把终点舍入误差反推成碎片。
        if(frozen.policy().mode()==MeasurementPolicy.Mode.MEASURED_WEIGHT) {
            if(UnitCodes.KG.equals(UnitCodes.normalize(unit))) {
                weight=quantity;
                if(roll) physical=quantity.signum()==0?BigDecimal.ZERO:BigDecimal.ONE;
                else {
                    physical=old.physicalQuantity().multiply(quantity).divide(old.priceQuantity(),12,RoundingMode.HALF_UP).stripTrailingZeros();
                    if(physical.scale()>0) return null; // 称重批次的非整数件数没有可核实依据。
                }
            } else {
                physical=quantity;
                weight=old.priceQuantity().multiply(quantity).divide(old.physicalQuantity(),12,RoundingMode.HALF_UP);
            }
        }
        return MeasurementCalculator.convertLedger(frozen.policy(),new MeasurementInput(physical,a.widthMm(),a.lengthMm(),a.rollLengthM(),a.unitWeightG(),a.grammage(),weight),unit,quantity);
    }
    public static String actual(String previous,MeasurementInput actual) {
        var old=read(previous); if(old==null)return null;
        return freeze(old.policy(),MeasurementCalculator.calculate(old.policy(),actual));
    }
}
