package com.jjx.inventory.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryStockItem;
import com.jjx.inventory.dto.vo.StockVO;
import com.jjx.inventory.measurement.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** 即时库存只读投影；参数来自批次快照，历史无快照仅用可核实的卷身份/规格。 */
public final class InventoryStockMeasurement {
    private InventoryStockMeasurement() { }
    private record Balance(String physicalUnit,BigDecimal physical,BigDecimal reserved,
                           String priceUnit,BigDecimal priced,BigDecimal area) { }
    private static BigDecimal zero(BigDecimal q) {return q==null?BigDecimal.ZERO:q;}

    public static void enrich(StockVO vo, InventoryMaterial material,List<InventoryStockItem> rows) {
        if(vo==null || vo.getUnit()==null)return;
        String physicalUnit=null,priceUnit=null;
        BigDecimal physical=BigDecimal.ZERO,reserved=BigDecimal.ZERO,priced=BigDecimal.ZERO,area=BigDecimal.ZERO,ledger=BigDecimal.ZERO;
        boolean physicalKnown=true,reservedKnown=true,priceKnown=true,areaKnown=true;
        Set<String> physicalUnits=new HashSet<>(),priceUnits=new HashSet<>();
        for(var row:rows) {
            if(zero(row.getQuantity()).signum()<=0)continue;
            ledger=ledger.add(row.getQuantity());
            try {
                var value=balance(vo.getUnit(),material,row);
                if(value.physical()==null)physicalKnown=false;else physical=physical.add(value.physical());
                if(value.reserved()==null)reservedKnown=false;else reserved=reserved.add(value.reserved());
                if(value.physicalUnit()!=null)physicalUnits.add(value.physicalUnit());
                if(value.priced()==null)priceKnown=false;else priced=priced.add(value.priced());
                if(value.priceUnit()!=null)priceUnits.add(value.priceUnit());
                if(value.area()==null)areaKnown=false;else area=area.add(value.area());
            } catch(BusinessException ex) {
                physicalKnown=false;priceKnown=false;areaKnown=false;
                vo.setMeasurementMessage(ex.getMessage());
            }
        }
        // 汇总/批次不一致时不展示貌似完整的派生数量，保留原账量供定位。
        if(ledger.compareTo(zero(vo.getTotalQuantity()))!=0) {
            vo.setMeasurementMessage("汇总与有效批次余额不一致，请核对库存流水");return;
        }
        if(physicalUnits.size()>1)physicalKnown=false;
        if(priceUnits.size()>1)priceKnown=false;
        if(rows.isEmpty()) {
            // 零库存可显示原记账单位，不从新档案策略猜历史批次单位。
            physicalUnit=UnitCodes.normalize(vo.getUnit());priceUnit=null;priceKnown=false;
            areaKnown=UnitCodes.SQM.equals(physicalUnit);
        } else {
            physicalUnit=physicalUnits.stream().findFirst().orElse(null);
            priceUnit=priceUnits.stream().findFirst().orElse(null);
        }
        if(physicalKnown && physicalUnit!=null) {
            vo.setPhysicalUnit(physicalUnit);vo.setPhysicalQuantity(physical);
            if(reservedKnown) {vo.setReservedPhysicalQuantity(reserved);vo.setAvailablePhysicalQuantity(physical.subtract(reserved));}
        }
        if(priceKnown && priceUnit!=null) {vo.setPriceUnit(priceUnit);vo.setPriceQuantity(priced);}
        if(areaKnown)vo.setInventoryArea(area.stripTrailingZeros());
    }

    private static Balance balance(String ledgerUnit,InventoryMaterial material,InventoryStockItem stock) {
        String unit=UnitCodes.normalize(ledgerUnit);
        BigDecimal q=stock.getQuantity(),r=zero(stock.getReservedQuantity());
        boolean roll=stock.getRollNo()!=null && !stock.getRollNo().isBlank();
        if(stock.getMeasurementSnapshot()!=null && !stock.getMeasurementSnapshot().isBlank()) {
            var result=InventoryMeasurementSnapshotService.balance(stock.getMeasurementSnapshot(),unit,q,roll);
            if(result==null)return new Balance(null,null,null,null,null,null);
            BigDecimal physical=result.physicalQuantity();
            BigDecimal reserved=roll?(r.signum()>0?BigDecimal.ONE:BigDecimal.ZERO):physical.multiply(r).divide(q,12,RoundingMode.HALF_UP).stripTrailingZeros();
            if(isCount(result.physicalUnit()) && reserved.scale()>0)reserved=null;
            BigDecimal area=areaApplicable(material,result)?MeasurementCalculator.inventoryArea(result):null;
            return new Balance(result.physicalUnit(),physical,reserved,result.priceUnit(),result.priceQuantity(),area);
        }
        if(roll) {
            // 历史卷号就是实物身份；㎡账量已是该余卷的实际余额，不能取名义整卷长重算。
            return new Balance(UnitCodes.ROLL,BigDecimal.ONE,r.signum()>0?BigDecimal.ONE:BigDecimal.ZERO,
                null,null,UnitCodes.SQM.equals(unit)?q:null);
        }
        BigDecimal area=null;
        if(UnitCodes.SQM.equals(unit))area=q;
        else if(material!=null && areaMaterial(material) && Set.of(UnitCodes.PCS,UnitCodes.SHEET).contains(unit)
                && positive(material.getWidthMm()) && positive(material.getLengthMm())) {
            var policy=new MeasurementPolicy(unit,UnitCodes.SQM,MeasurementPolicy.Mode.PIECE_AREA,null);
            area=MeasurementCalculator.calculate(policy,new MeasurementInput(q,material.getWidthMm(),material.getLengthMm(),null,null,null,null)).priceQuantity();
        } else if(material!=null && areaMaterial(material) && UnitCodes.M.equals(unit) && positive(material.getWidthMm())) {
            var policy=new MeasurementPolicy(unit,UnitCodes.SQM,MeasurementPolicy.Mode.LENGTH_AREA,null);
            area=MeasurementCalculator.calculate(policy,new MeasurementInput(q,material.getWidthMm(),null,null,null,null,null)).priceQuantity();
        }
        return new Balance(unit,q,r,null,null,area);
    }
    private static boolean areaApplicable(InventoryMaterial material,MeasurementResult result) {
        return Set.of(MeasurementPolicy.Mode.PIECE_AREA,MeasurementPolicy.Mode.LENGTH_AREA,MeasurementPolicy.Mode.ROLL_AREA,MeasurementPolicy.Mode.ROLL_WEIGHT,MeasurementPolicy.Mode.PIECE_AREA_WEIGHT).contains(result.mode())
            || UnitCodes.SQM.equals(result.physicalUnit()) || areaMaterial(material);
    }
    private static boolean areaMaterial(InventoryMaterial material) {
        return material!=null && Set.of("FILM","SHEET","BOARD","ROLL","PAPER","TAPE","FOAM").contains(Objects.toString(material.getMaterialForm(),""));
    }
    private static boolean positive(BigDecimal q) {return q!=null && q.signum()>0;}
    private static boolean isCount(String unit) {return Set.of(UnitCodes.ROLL,UnitCodes.PCS,UnitCodes.SHEET,UnitCodes.SET,UnitCodes.PIN).contains(unit);}
}
