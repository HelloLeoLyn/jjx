package com.jjx.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.enums.ProductEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * BOM 版本资格与单张校验的唯一规则实现（dev-20261111-019，ECN-lite 规则层）。
 *
 * <p>资格口径：审批已通过（approve_status=已批准）→ 生效日期不晚于判定日 →
 * 失效日期为空或晚于判定日。仍多条时取 is_current=1，再取最新生效日期、最大 bomId。
 * is_current 只作过渡期并列时的次序依据，不单独决定资格。</p>
 */
@Component
@RequiredArgsConstructor
public class BomVersionRules {

    private final EngineeringBomMapper bomMapper;

    /** 跳距行免模数/基数：pitchMm>0，或 pcsPerPitch>=1 且 pitch 有值。 */
    public static boolean pitchRowExemptFromRatio(BigDecimal pitchMm, Integer pcsPerPitch) {
        // 跳距必须为正数才算跳距行；0/负值仍按普通行校验，避免用 0 跳过模数/基数。
        return pitchMm != null && pitchMm.signum() > 0 && (pcsPerPitch == null || pcsPerPitch >= 1);
    }

    /** LocalDate 版本资格（供需求试算等已用 LocalDate 的调用方复用，口径与 Date 版一致）。 */
    public static boolean isEligibleLocal(Integer approveStatus, LocalDate effective, LocalDate expiry,
            LocalDate day, Integer approvedStatusValue) {
        if (approveStatus == null || !approveStatus.equals(approvedStatusValue)) {
            return false;
        }
        LocalDate target = day == null ? LocalDate.now() : day;
        if (effective != null && effective.isAfter(target)) {
            return false;
        }
        return expiry == null || expiry.isAfter(target);
    }

    /** 单版本资格：审批通过 + 生效日 <= 判定日 + （失效日空 或 > 判定日）。 */
    public static boolean isEligible(Integer approveStatus, Date effectiveDate, Date expiryDate, LocalDate day,
                                     Integer approvedStatusValue) {
        if (approveStatus == null || !approveStatus.equals(approvedStatusValue)) {
            return false;
        }
        LocalDate target = day == null ? LocalDate.now() : day;
        LocalDate effective = toLocalDate(effectiveDate);
        LocalDate expiry = toLocalDate(expiryDate);
        if (effective != null && effective.isAfter(target)) {
            return false;
        }
        return expiry == null || expiry.isAfter(target);
    }

    /**
     * 同一产品同一时点的唯一有效版本。bomCode 为空表示不限方案编号（产品主方案）；
     * 无可用版本时抛中文业务异常（不返回“最新版本”冒充）。
     */
    public EngineeringBom resolveEffectiveVersion(Long productId, String bomCode, LocalDate day) {
        if (productId == null) {
            throw new BusinessException("产品ID不能为空，无法判定BOM版本资格");
        }
        LocalDate target = day == null ? LocalDate.now() : day;
        Integer approved = ProductEnums.BomStatus.APPROVED.getValue();
        LambdaQueryWrapper<EngineeringBom> wrapper = new LambdaQueryWrapper<EngineeringBom>()
                .eq(EngineeringBom::getProductId, productId)
                .eq(EngineeringBom::getApproveStatus, approved)
                .and(w -> w.isNull(EngineeringBom::getOutputType).or().eq(EngineeringBom::getOutputType, "PRODUCT"));
        if (bomCode != null && !bomCode.isBlank()) {
            wrapper.eq(EngineeringBom::getBomCode, bomCode.trim());
        }
        List<EngineeringBom> candidates = bomMapper.selectList(wrapper);
        List<EngineeringBom> valid = candidates == null ? List.of() : candidates.stream()
                .filter(b -> isEligible(b.getApproveStatus(), b.getEffectiveDate(), b.getExpiryDate(), target, approved))
                .toList();
        if (valid.isEmpty()) {
            throw new BusinessException("产品[" + productId + "]在 " + target + " 没有已批准且有效的制造BOM"
                    + (bomCode == null || bomCode.isBlank() ? "" : "[" + bomCode + "]"));
        }
        return valid.stream().min(ORDER).orElseThrow();
    }

    /** 过渡期并列次序：is_current 优先 → 生效日期新者优先 → bomId 大者优先。 */
    private static final Comparator<EngineeringBom> ORDER = Comparator
            .comparing((EngineeringBom b) -> Boolean.TRUE.equals(b.getIsCurrent()) ? 0 : 1)
            .thenComparing(b -> toLocalDate(b.getEffectiveDate()), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(b -> b.getBomId() == null ? Long.MIN_VALUE : -b.getBomId());

    private static LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
