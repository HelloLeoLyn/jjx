package com.jjx.inventory.measurement;

import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.production.domain.model.ExactQuantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** 所有入口调用本类，不分别维护采购/库存/预留公式；只使用数值参数，不执行用户文本公式。 */
public final class MeasurementCalculator {
    private MeasurementCalculator() { }
    private static ExactQuantity x(BigDecimal n) { return ExactQuantity.fromDecimal(n); }
    private static BigDecimal positive(BigDecimal n, String label) {
        if (n == null || n.signum() <= 0 || n.stripTrailingZeros().scale() > 8 || n.precision() > 20)
            throw new BusinessException(label + "须为大于0的明确数值，最多8位小数");
        return n;
    }
    public static MeasurementResult calculate(MeasurementPolicy policy, MeasurementInput input) {
        if (policy == null || input == null) throw new BusinessException("缺少物料计量策略或实际参数");
        MeasurementPolicy p = policy.normalized();
        BigDecimal q = input.physicalQuantity();
        if (q == null || q.signum() < 0 || q.stripTrailingZeros().scale() > 4 || q.precision() > 16)
            throw new BusinessException("实物数量须非负，最多4位小数、16位有效数");
        if (Set.of(UnitCodes.ROLL, UnitCodes.PCS, UnitCodes.SHEET, UnitCodes.SET, UnitCodes.PIN).contains(p.physicalUnit())
                && q.stripTrailingZeros().scale() > 0) throw new BusinessException("卷数/片数/个数必须是整数");
        ExactQuantity result = x(q);
        String formula;
        switch (p.mode()) {
            case SAME -> formula = "计价数量=实物数量";
            case PIECE_AREA -> {
                result = result.multiply(x(positive(input.widthMm(), "片宽(mm)")))
                        .multiply(x(positive(input.lengthMm(), "片长(mm)"))).divide(ExactQuantity.of(1000000));
                formula = "面积(㎡)=片数×宽(mm)×长(mm)÷1000000";
            }
            case LENGTH_AREA -> {
                result = result.multiply(x(positive(input.widthMm(), "料宽(mm)"))).divide(ExactQuantity.of(1000));
                formula = "面积(㎡)=米数×宽(mm)÷1000";
            }
            case ROLL_AREA -> {
                result = result.multiply(x(positive(input.widthMm(), "卷宽(mm)")))
                        .multiply(x(positive(input.rollLengthM(), "本次每卷实际长(m)"))).divide(ExactQuantity.of(1000));
                formula = "面积(㎡)=卷数×卷宽(mm)×本次每卷实际长(m)÷1000；异规格逐卷合计";
            }
            case PIECE_WEIGHT -> {
                BigDecimal weight = input.unitWeightG() == null ? p.unitWeightG() : input.unitWeightG();
                result = result.multiply(x(positive(weight, "单重(g)"))).divide(ExactQuantity.of(1000));
                formula = "重量(KG)=实物数量×单重(g)÷1000";
            }
            case AREA_WEIGHT -> {
                result = result.multiply(x(positive(input.grammage(), "克重(g/㎡)"))).divide(ExactQuantity.of(1000));
                formula = "重量(KG)=面积(㎡)×克重(g/㎡)÷1000";
            }
            case MEASURED_WEIGHT -> {
                BigDecimal measured = input.actualWeightKg();
                if (measured == null || measured.signum() < 0 || (q.signum() > 0 && measured.signum() == 0)
                        || (q.signum() == 0 && measured.signum() != 0) || measured.stripTrailingZeros().scale() > 8)
                    throw new BusinessException("请确认本次实物数量对应的实际净重(KG)");
                result = x(measured); formula = "计价重量(KG)=本次确认的实际净重";
            }
            default -> throw new BusinessException("不支持的计量公式");
        }
        if (input.actualWeightKg() != null && p.mode() != MeasurementPolicy.Mode.MEASURED_WEIGHT)
            throw new BusinessException("当前换算方式不接受实测重量覆盖，请选择实际称重方式");
        BigDecimal priced = result.toBigDecimal(12, RoundingMode.HALF_UP).stripTrailingZeros();
        if (q.signum() > 0 && priced.signum() == 0) throw new BusinessException("计价数量低于计算精度，请核对规格");
        MeasurementInput actual = p.mode() == MeasurementPolicy.Mode.PIECE_WEIGHT && input.unitWeightG() == null
                ? new MeasurementInput(q, input.widthMm(), input.lengthMm(), input.rollLengthM(), p.unitWeightG(), input.grammage(), input.actualWeightKg()) : input;
        return new MeasurementResult(p.physicalUnit(), p.priceUnit(), p.mode(), q, priced, formula, actual);
    }
    public static BigDecimal ledgerQuantity(String ledgerUnit, MeasurementResult result) {
        String unit = UnitCodes.requireDictUnit(ledgerUnit, "库存记账单位");
        if (unit.equals(result.physicalUnit())) return result.physicalQuantity();
        if (unit.equals(result.priceUnit())) return result.priceQuantity();
        throw new BusinessException("换算结果与库存单位不匹配");
    }
}
