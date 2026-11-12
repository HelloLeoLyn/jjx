package com.jjx.product.service;

import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.service.impl.EngineeringBomServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BOM 服务端内容指纹（dev-20261111-018）。
 *
 * <p>纯计算单测：不连库、不连 Redis，也不代表真实并发事务已验证。
 */
class BomContentRevisionTest {

    private EngineeringBom bom() {
        EngineeringBom bom = new EngineeringBom();
        bom.setBomId(1001L);
        bom.setBomCode("BOM-1001");
        bom.setBomName("测试BOM");
        bom.setBomType("engineering");
        bom.setBomVersion("V1.0");
        bom.setVersion("V1.0");
        bom.setProductId(9L);
        bom.setOutputType("PRODUCT");
        bom.setOutputId(9L);
        bom.setApproveStatus(1);
        bom.setIsCurrent(false);
        bom.setRemark("备注");
        bom.setEffectiveDate(Date.valueOf("2026-11-01"));
        bom.setExpiryDate(Date.valueOf("2027-11-01"));
        return bom;
    }

    private EngineeringBomItem item(long id, long parent, String name, String spec, String qty) {
        EngineeringBomItem item = new EngineeringBomItem();
        item.setItemId(id);
        item.setBomId(1001L);
        item.setParentMaterialId(parent);
        item.setMaterialId(500L + id);
        item.setMaterialCode("RM" + id);
        item.setMaterialName(name);
        item.setSpecification(spec);
        item.setSplit("75mm");
        item.setProcessId(7L);
        item.setProcessName("模切");
        item.setUnit("PCS");
        item.setQuantity(new BigDecimal(qty));
        item.setLossRate(new BigDecimal("0.0500"));
        item.setModuleQty(new BigDecimal("6.0000"));
        item.setBaseQty(new BigDecimal("1.0000"));
        item.setMinIssueQty(new BigDecimal("2.0000"));
        item.setWidthMm(new BigDecimal("340.0000"));
        item.setLengthMm(new BigDecimal("305.0000"));
        item.setUnitFactor(new BigDecimal("1.0000"));
        item.setUnitFactorBasis("展开面积");
        item.setUnitFactorSource("档案尺寸");
        item.setItemOrder(1);
        item.setReviewStatus(2);
        item.setRemoveConfirmed(false);
        item.setApprovedFingerprint("fp-1");
        return item;
    }

    private List<EngineeringBomItem> items() {
        List<EngineeringBomItem> rows = new ArrayList<>();
        rows.add(item(11L, 0L, "白卡纸", "300g", "1.5000"));
        rows.add(item(12L, 11L, "瓦楞纸", "500g", "2.2500"));
        return rows;
    }

    @Test
    void sameContentProducesSameRevisionTwice() {
        String first = EngineeringBomServiceImpl.computeContentRevision(bom(), items());
        String second = EngineeringBomServiceImpl.computeContentRevision(bom(), items());
        assertEquals(first, second);
        assertNotNull(first);
        assertEquals(64, first.length(), "SHA-256 十六进制");
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void detailOrderDoesNotAffectRevision() {
        List<EngineeringBomItem> forward = items();
        List<EngineeringBomItem> reversed = new ArrayList<>(forward);
        java.util.Collections.reverse(reversed);
        assertEquals(EngineeringBomServiceImpl.computeContentRevision(bom(), forward),
                EngineeringBomServiceImpl.computeContentRevision(bom(), reversed),
                "明细按 itemId 升序参与哈希，数据库返回顺序不能影响结果");
    }

    @Test
    void changingAnyParticipatingFieldChangesRevision() {
        String base = EngineeringBomServiceImpl.computeContentRevision(bom(), items());

        EngineeringBom renamed = bom();
        renamed.setBomName("改名");
        assertNotEquals(base, EngineeringBomServiceImpl.computeContentRevision(renamed, items()), "父表名称");

        EngineeringBom reapproved = bom();
        reapproved.setApproveStatus(3);
        assertNotEquals(base, EngineeringBomServiceImpl.computeContentRevision(reapproved, items()), "审批状态");

        EngineeringBom newDate = bom();
        newDate.setEffectiveDate(Date.valueOf("2026-12-01"));
        assertNotEquals(base, EngineeringBomServiceImpl.computeContentRevision(newDate, items()), "生效日期");

        assertItemFieldChanges(base, "数量", it -> it.setQuantity(new BigDecimal("1.5001")));
        assertItemFieldChanges(base, "物料", it -> it.setMaterialId(999L));
        assertItemFieldChanges(base, "单位", it -> it.setUnit("KG"));
        assertItemFieldChanges(base, "规格", it -> it.setSpecification("350g"));
        assertItemFieldChanges(base, "项目", it -> it.setProcessName("烫金"));
        assertItemFieldChanges(base, "模数", it -> it.setModuleQty(new BigDecimal("8")));
        assertItemFieldChanges(base, "跳距", it -> it.setSplit("80mm"));
        assertItemFieldChanges(base, "顺序", it -> it.setItemOrder(2));
        assertItemFieldChanges(base, "审核状态", it -> it.setReviewStatus(3));
        assertItemFieldChanges(base, "下层方案", it -> it.setSubBomId(2002L));
        assertItemFieldChanges(base, "组合父行", it -> it.setParentMaterialId(null));
        assertItemFieldChanges(base, "换算系数", it -> it.setUnitFactor(new BigDecimal("2")));
        assertItemFieldChanges(base, "换算依据", it -> it.setUnitFactorBasis("批次实测"));
        assertItemFieldChanges(base, "行指纹", it -> it.setApprovedFingerprint("fp-2"));
    }

    private void assertItemFieldChanges(String base, String label, java.util.function.Consumer<EngineeringBomItem> mutation) {
        List<EngineeringBomItem> rows = items();
        mutation.accept(rows.get(0));
        assertNotEquals(base, EngineeringBomServiceImpl.computeContentRevision(bom(), rows),
                "明细字段变化必须改变内容指纹：" + label);
    }

    @Test
    void auditFieldsNeverAffectRevision() {
        String base = EngineeringBomServiceImpl.computeContentRevision(bom(), items());

        EngineeringBom audited = bom();
        audited.setCreateBy("creator");
        audited.setUpdateBy("updater");
        audited.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        audited.setUpdateTime(LocalDateTime.of(2026, 2, 2, 0, 0));
        audited.setIsCurrent(true);
        assertEquals(base, EngineeringBomServiceImpl.computeContentRevision(audited, items()),
                "审计字段与生效标记不参与内容指纹");

        List<EngineeringBomItem> rows = items();
        rows.get(0).setApprovedBy("审核员");
        rows.get(0).setApprovedTime(LocalDateTime.of(2026, 3, 3, 0, 0));
        rows.get(0).setRemoveConfirmedBy("确认人");
        rows.get(0).setRemoveConfirmedTime(LocalDateTime.of(2026, 4, 4, 0, 0));
        assertEquals(base, EngineeringBomServiceImpl.computeContentRevision(bom(), rows),
                "行审计字段不参与内容指纹");
    }

    @Test
    void nullAndEmptyStringAreDistinct() {
        List<EngineeringBomItem> nullSpec = items();
        nullSpec.get(0).setSpecification(null);
        List<EngineeringBomItem> emptySpec = items();
        emptySpec.get(0).setSpecification("");
        assertNotEquals(EngineeringBomServiceImpl.computeContentRevision(bom(), nullSpec),
                EngineeringBomServiceImpl.computeContentRevision(bom(), emptySpec),
                "null 与空串必须区分，长度前缀编码不允许歧义");
    }

    @Test
    void decimalScaleOnlyDifferenceIsTreatedAsSameValue() {
        List<EngineeringBomItem> scaled = items();
        scaled.get(0).setQuantity(new BigDecimal("1.5"));
        assertEquals(EngineeringBomServiceImpl.computeContentRevision(bom(), items()),
                EngineeringBomServiceImpl.computeContentRevision(bom(), scaled),
                "同值不同小数位（1.5000 / 1.5）视为同一内容，避免误报过期");
    }

    @Test
    void deletedRowChangesRevision() {
        List<EngineeringBomItem> onlyFirst = new ArrayList<>(items());
        onlyFirst.remove(1);
        assertNotEquals(EngineeringBomServiceImpl.computeContentRevision(bom(), items()),
                EngineeringBomServiceImpl.computeContentRevision(bom(), onlyFirst),
                "新增/删除行必须改变内容指纹");
    }
}
