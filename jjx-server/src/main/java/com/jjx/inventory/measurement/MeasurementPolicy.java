package com.jjx.inventory.measurement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import java.math.BigDecimal;
import java.util.Set;

/** 物料/品类统一计量策略；原始尺寸仍在档案，单据保存当次实际参数。 */
public record MeasurementPolicy(String physicalUnit, String priceUnit, Mode mode, BigDecimal unitWeightG) {
    private static final ObjectMapper JSON = new ObjectMapper();
    public enum Mode { SAME, PIECE_AREA, LENGTH_AREA, ROLL_AREA, PIECE_WEIGHT, AREA_WEIGHT, MEASURED_WEIGHT }
    public MeasurementPolicy normalized() {
        String physical = UnitCodes.requireDictUnit(physicalUnit, "实物单位");
        String price = UnitCodes.requireDictUnit(priceUnit, "计价单位");
        if (mode == null) throw new BusinessException("请选择换算方式");
        boolean pieces = Set.of(UnitCodes.PCS, UnitCodes.SHEET, UnitCodes.SET, UnitCodes.PIN).contains(physical);
        boolean valid = switch (mode) {
            case SAME -> physical.equals(price);
            case PIECE_AREA -> Set.of(UnitCodes.PCS, UnitCodes.SHEET).contains(physical) && UnitCodes.SQM.equals(price);
            case LENGTH_AREA -> UnitCodes.M.equals(physical) && UnitCodes.SQM.equals(price);
            case ROLL_AREA -> UnitCodes.ROLL.equals(physical) && UnitCodes.SQM.equals(price);
            case PIECE_WEIGHT -> pieces && UnitCodes.KG.equals(price);
            case AREA_WEIGHT -> UnitCodes.SQM.equals(physical) && UnitCodes.KG.equals(price);
            case MEASURED_WEIGHT -> !UnitCodes.KG.equals(physical) && UnitCodes.KG.equals(price);
        };
        if (!valid) throw new BusinessException("实物单位、计价单位与换算方式不匹配");
        if (unitWeightG != null && (unitWeightG.signum() <= 0 || unitWeightG.scale() > 8 || unitWeightG.precision() > 18))
            throw new BusinessException("单重(g)须大于0，最多8位小数、18位有效数");
        return new MeasurementPolicy(physical, price, mode, unitWeightG);
    }
    public static MeasurementPolicy parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            MeasurementPolicy policy = JSON.readValue(value, MeasurementPolicy.class);
            if (policy == null) throw new BusinessException("计量策略不能为JSON null");
            return policy.normalized();
        } catch (JsonProcessingException ex) { throw new BusinessException("计量策略格式无效"); }
    }
    public String toJson() {
        try { return JSON.writeValueAsString(normalized()); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("计量策略序列化失败", ex); }
    }
    /** 历史账本单位保持原值；策略不允许把已有库存改成另一种单位。 */
    public void validateLedgerUnit(String unit) {
        MeasurementPolicy p = normalized();
        String ledger = UnitCodes.requireDictUnit(unit, "库存记账单位");
        if (!ledger.equals(p.physicalUnit()) && !ledger.equals(p.priceUnit()))
            throw new BusinessException("计量策略须包含既有库存记账单位，不能静默更换库存单位");
    }
}
