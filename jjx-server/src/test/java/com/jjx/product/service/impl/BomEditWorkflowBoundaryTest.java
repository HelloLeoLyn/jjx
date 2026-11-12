package com.jjx.product.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.EngineeringBomItemDTO;
import com.jjx.product.enums.ProductEnums;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reuses the isolated H2 mapper/transaction fixture; never writes business MySQL. */
class BomEditWorkflowBoundaryTest {
    private BomGroupPersistenceTest fixture;

    @BeforeEach void setup() {
        fixture = new BomGroupPersistenceTest();
        fixture.setup();
    }

    private EngineeringBomDTO payload(Long bomId, Long itemId) {
        var row = new EngineeringBomItemDTO();
        row.setItemId(itemId);
        row.setMaterialId(0L);
        row.setMaterialName("投入材料");
        row.setMaterialSpecification("系统规格");
        row.setUnit("PCS");
        row.setBaseQty(BigDecimal.ONE);
        row.setModuleQty(BigDecimal.ONE);
        row.setItemOrder(1);
        var dto = new EngineeringBomDTO();
        dto.setBomId(bomId);
        dto.setProductId(1L);
        dto.setBomCode("BOUNDARY");
        dto.setBomName("BOM名称");
        dto.setBomVersion("V1.0");
        dto.setItems(List.of(row));
        return dto;
    }

    private Long create() {
        return fixture.service.createBomReturnId(payload(null, -1L));
    }

    private EngineeringBomDTO edit(Long id) {
        return payload(id, fixture.service.getBomItems(id).get(0).getItemId());
    }

    private void assertWorkflow(Long id, ProductEnums.BomStatus status, boolean current) {
        assertEquals(status.getValue(), fixture.db.queryForObject(
                "SELECT approve_status FROM engineering_bom WHERE bom_id=?", Integer.class, id));
        assertEquals(current, fixture.db.queryForObject(
                "SELECT is_current FROM engineering_bom WHERE bom_id=?", Boolean.class, id));
    }

    @Test void createAlwaysStartsAsDraftAndNonCurrent() {
        var dto = payload(null, -1L);
        dto.setApproveStatus(ProductEnums.BomStatus.APPROVED.getValue());
        dto.setIsCurrent(true);
        Long id = fixture.service.createBomReturnId(dto);
        assertWorkflow(id, ProductEnums.BomStatus.DRAFT, false);
    }

    @Test void ordinaryEditCannotApproveOrActivateDraft() {
        Long id = create();
        Long other = create();
        fixture.db.update("UPDATE engineering_bom SET is_current=true, approve_status=? WHERE bom_id=?",
                ProductEnums.BomStatus.APPROVED.getValue(), other);
        var before = fixture.service.getBomItems(id).get(0);
        var dto = edit(id);
        dto.setBomName("正常修改名称");
        dto.setApproveStatus(ProductEnums.BomStatus.APPROVED.getValue());
        dto.setIsCurrent(true);
        var result = fixture.service.updateBomWithDetail(dto);
        assertTrue(result.isSuccess());
        assertWorkflow(id, ProductEnums.BomStatus.DRAFT, false);
        assertWorkflow(other, ProductEnums.BomStatus.APPROVED, true);
        assertEquals(ProductEnums.BomStatus.DRAFT.getLabel(), result.getBizStatus());
        assertEquals("正常修改名称", fixture.db.queryForObject(
                "SELECT bom_name FROM engineering_bom WHERE bom_id=?", String.class, id));
        assertEquals(before.getReviewStatus(), fixture.service.getBomItems(id).get(0).getReviewStatus());
    }

    @Test void rejectedCurrentBomRetainsWorkflowEvenWhenClientRequestsDraftAndInactive() {
        Long id = create();
        fixture.db.update("UPDATE engineering_bom SET is_current=true, approve_status=? WHERE bom_id=?",
                ProductEnums.BomStatus.REJECT.getValue(), id);
        var dto = edit(id);
        dto.setApproveStatus(ProductEnums.BomStatus.DRAFT.getValue());
        dto.setIsCurrent(false);
        assertTrue(fixture.service.updateBomWithDetail(dto).isSuccess());
        assertWorkflow(id, ProductEnums.BomStatus.REJECT, true);
    }

    @Test void omittedWorkflowFieldsKeepPersistedValues() {
        Long id = create();
        fixture.db.update("UPDATE engineering_bom SET approve_status=? WHERE bom_id=?",
                ProductEnums.BomStatus.REJECT.getValue(), id);
        assertTrue(fixture.service.updateBomWithDetail(edit(id)).isSuccess());
        assertWorkflow(id, ProductEnums.BomStatus.REJECT, false);
    }

    @Test void approvedBomCannotBeDemotedThroughOrdinaryEdit() {
        Long id = create();
        fixture.db.update("UPDATE engineering_bom SET is_current=true, approve_status=? WHERE bom_id=?",
                ProductEnums.BomStatus.APPROVED.getValue(), id);
        var dto = edit(id);
        dto.setBomName("不能写入");
        dto.setApproveStatus(ProductEnums.BomStatus.DRAFT.getValue());
        dto.setIsCurrent(false);
        assertThrows(BusinessException.class, () -> fixture.service.updateBomWithDetail(dto));
        assertWorkflow(id, ProductEnums.BomStatus.APPROVED, true);
        assertEquals("BOM名称", fixture.db.queryForObject(
                "SELECT bom_name FROM engineering_bom WHERE bom_id=?", String.class, id));
    }
}
