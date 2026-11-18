package com.jjx.inventory.measurement;

import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MaterialMeasurementService {
    private final InventoryMaterialMapper materials;
    public record PreviewRequest(Long materialId, MeasurementPolicy policy, MeasurementInput actual) { }
    public MeasurementResult preview(PreviewRequest request) {
        if (request == null) throw new BusinessException("缺少计量预览参数");
        InventoryMaterial material = request.materialId() == null ? null : materials.selectById(request.materialId());
        if (request.materialId() != null && material == null) throw new BusinessException("物料不存在");
        MeasurementPolicy policy = request.policy() != null ? request.policy()
                : material == null ? null : MeasurementPolicy.parse(material.getMeasurementPolicyJson());
        MeasurementInput draft = request.actual();
        // 草稿尺寸本身就是明确输入；此便捷转换只限预览，不放宽余卷业务实际参数。
        if (draft != null && draft.rollLengthM() == null && draft.lengthMm() != null)
            draft = new MeasurementInput(draft.physicalQuantity(), draft.widthMm(), draft.lengthMm(),
                    draft.lengthMm().movePointLeft(3), draft.unitWeightG(), draft.grammage(), draft.actualWeightKg());
        MeasurementInput input = defaults(draft, material);
        return MeasurementCalculator.calculate(policy, input);
    }
    public static MeasurementInput defaults(MeasurementInput actual, InventoryMaterial material) {
        if (actual == null) throw new BusinessException("请提供本次实物数量");
        if (material == null) return actual;
        return new MeasurementInput(actual.physicalQuantity(), actual.widthMm() == null ? material.getWidthMm() : actual.widthMm(),
                actual.lengthMm() == null ? material.getLengthMm() : actual.lengthMm(),
                actual.rollLengthM() == null && material.getLengthMm() != null ? material.getLengthMm().movePointLeft(3) : actual.rollLengthM(),
                actual.unitWeightG(), actual.grammage() == null ? material.getGrammage() : actual.grammage(), actual.actualWeightKg());
    }
    /** 业务入口必须显式决定是否允许档案默认；余卷退料不得回落标准整卷长度。 */
    public static MeasurementResult actual(InventoryMaterial material, MeasurementInput input, boolean useDefaults) {
        MeasurementPolicy policy = MeasurementPolicy.parse(material.getMeasurementPolicyJson());
        if (policy == null) throw new BusinessException("物料尚未配置双单位换算规则");
        return MeasurementCalculator.calculate(policy, useDefaults ? defaults(input, material) : input);
    }
}
