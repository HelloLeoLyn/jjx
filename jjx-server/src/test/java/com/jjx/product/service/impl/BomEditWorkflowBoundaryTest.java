package com.jjx.product.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.EngineeringBomItemDTO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.service.IEngineeringBomService;
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

    // ==================== dev-20261111-018：编辑占用 + 服务端内容版本 ====================

    /** 取事务代理背后的真实实现，用于注入占用服务替身（隔离测试不装配 Spring）。 */
    private EngineeringBomServiceImpl target() {
        try {
            return (EngineeringBomServiceImpl) ((org.springframework.aop.framework.Advised) fixture.service)
                    .getTargetSource().getTarget();
        } catch (Exception ex) {
            throw new IllegalStateException("无法取得测试代理目标", ex);
        }
    }

    /** 开启严格校验分支：token 是否有效、Redis 是否故障由参数控制。 */
    private void enableStrictSession(boolean tokenValid, boolean redisBroken) {
        org.springframework.test.util.ReflectionTestUtils.setField(target(), "bomEditSessionService",
                new com.jjx.product.service.BomEditSessionService() {
                    @Override
                    public com.jjx.product.domain.vo.BomEditSessionVO describe(Long bomId, String clientId) {
                        return new com.jjx.product.domain.vo.BomEditSessionVO();
                    }

                    @Override
                    public com.jjx.product.domain.vo.BomEditSessionVO acquire(Long bomId, String clientId) {
                        return new com.jjx.product.domain.vo.BomEditSessionVO();
                    }

                    @Override
                    public com.jjx.product.domain.vo.BomEditSessionVO renew(Long bomId, String clientId, String token) {
                        return new com.jjx.product.domain.vo.BomEditSessionVO();
                    }

                    @Override
                    public boolean release(Long bomId, String token) {
                        return false;
                    }

                    @Override
                    public void verifySaveToken(Long bomId, String token) {
                        if (redisBroken) {
                            throw new BusinessException(503, "EDIT_LEASE_UNAVAILABLE：占用服务暂不可用，请稍后重试");
                        }
                        if (!tokenValid || token == null || token.isBlank()) {
                            throw new BusinessException(409, "BOM_EDIT_LEASE_REQUIRED：缺少编辑占用凭证");
                        }
                    }

                    @Override
                    public void forceUnlock(Long bomId) {
                        throw new BusinessException(403, "未启用");
                    }
                });
    }

    private String revisionOf(Long bomId) {
        return fixture.service.getBomDetail(bomId).getContentRevision();
    }

    @Test void staleContentRevisionRejectsSecondSaveWithoutAnyWrite() {
        Long id = create();
        String first = revisionOf(id);
        // 乙先保存：直接改库模拟另一会话已提交的新内容（真实并发由 MySQL 双会话另行验证）
        fixture.db.update("UPDATE engineering_bom_item SET material_name='乙改后' WHERE bom_id=?", id);
        String second = revisionOf(id);
        assertNotEquals(first, second, "内容变化必须改变内容指纹");

        enableStrictSession(true, false);
        var dto = edit(id);
        dto.setBomName("甲的旧快照覆盖");
        dto.setExpectedContentRevision(first);
        dto.setEditToken("token-A");
        var ex = assertThrows(BusinessException.class, () -> fixture.service.updateBomWithDetail(dto));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_CONTENT_STALE"), ex.getMessage());
        // 甲无写入：乙内容完整保留，主表名称未变，行数未变
        assertEquals("BOM名称", fixture.db.queryForObject(
                "SELECT bom_name FROM engineering_bom WHERE bom_id=?", String.class, id));
        assertEquals("乙改后", fixture.db.queryForObject(
                "SELECT material_name FROM engineering_bom_item WHERE bom_id=?", String.class, id));
        assertEquals(Integer.valueOf(1), fixture.db.queryForObject(
                "SELECT COUNT(*) FROM engineering_bom_item WHERE bom_id=?", Integer.class, id));
        assertEquals(second, revisionOf(id), "失败保存不能改变库中内容指纹");
    }

    @Test void matchingContentRevisionSavesAndReturnsReloadableRevision() {
        Long id = create();
        String first = revisionOf(id);
        enableStrictSession(true, false);

        var dto = edit(id);
        dto.setBomName("正常改名");
        dto.setExpectedContentRevision(first);
        dto.setEditToken("token-A");
        var result = fixture.service.updateBomWithDetail(dto);
        assertTrue(result.isSuccess());
        assertNotNull(result.getContentRevision());
        assertEquals(revisionOf(id), result.getContentRevision(),
                "保存返回的新指纹必须等于重新加载得到的指纹，否则同一会话下次保存会误判过期");

        var again = edit(id);
        again.setBomName("再改名");
        again.setExpectedContentRevision(result.getContentRevision());
        again.setEditToken("token-A");
        assertTrue(fixture.service.updateBomWithDetail(again).isSuccess(), "连续保存不能自我冲突");
    }

    @Test void invalidTokenRejectsSaveWithoutAnyWrite() {
        Long id = create();
        String revision = revisionOf(id);
        enableStrictSession(false, false);
        var dto = edit(id);
        dto.setBomName("不应写入");
        dto.setExpectedContentRevision(revision);
        dto.setEditToken("expired-token");
        var ex = assertThrows(BusinessException.class, () -> fixture.service.updateBomWithDetail(dto));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_EDIT_LEASE_REQUIRED"), ex.getMessage());
        assertEquals("BOM名称", fixture.db.queryForObject(
                "SELECT bom_name FROM engineering_bom WHERE bom_id=?", String.class, id));
    }

    @Test void redisFailureRejectsSaveFailClosed() {
        Long id = create();
        String revision = revisionOf(id);
        enableStrictSession(true, true);
        var dto = edit(id);
        dto.setBomName("Redis故障也不能写入");
        dto.setExpectedContentRevision(revision);
        dto.setEditToken("token-A");
        var ex = assertThrows(BusinessException.class, () -> fixture.service.updateBomWithDetail(dto));
        assertEquals(Integer.valueOf(503), ex.getCode(), "Redis 异常必须拒绝保存，不能 fail-open");
        assertTrue(ex.getMessage().contains("EDIT_LEASE_UNAVAILABLE"), ex.getMessage());
        assertEquals("BOM名称", fixture.db.queryForObject(
                "SELECT bom_name FROM engineering_bom WHERE bom_id=?", String.class, id));
    }

    @Test void missingContentRevisionIsRejectedEvenWithValidLease() {
        Long id = create();
        enableStrictSession(true, false);
        var dto = edit(id);
        dto.setExpectedContentRevision(null);
        dto.setEditToken("token-A");
        var ex = assertThrows(BusinessException.class, () -> fixture.service.updateBomWithDetail(dto));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_CONTENT_REVISION_REQUIRED"), ex.getMessage());
    }

    @Test void approveRejectsWhenContentChangedAfterLoad() {
        Long id = create();
        fixture.db.update("UPDATE engineering_bom SET approve_status=? WHERE bom_id=?",
                ProductEnums.BomStatus.REVIEWING.getValue(), id);
        String loaded = revisionOf(id);
        fixture.db.update("UPDATE engineering_bom_item SET material_name='审核前被改' WHERE bom_id=?", id);
        enableStrictSession(true, false);

        var dto = new com.jjx.product.domain.dto.UpdateBomStatusDTO();
        dto.setBomId(id);
        dto.setExpectedContentRevision(loaded);
        var ex = assertThrows(BusinessException.class, () -> fixture.service.approve(dto));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_CONTENT_STALE"), ex.getMessage());
        assertEquals(ProductEnums.BomStatus.REVIEWING.getValue(), fixture.db.queryForObject(
                "SELECT approve_status FROM engineering_bom WHERE bom_id=?", Integer.class, id),
                "内容过期时不得写状态");
        assertEquals("审核前被改", fixture.db.queryForObject(
                "SELECT material_name FROM engineering_bom_item WHERE bom_id=?", String.class, id));
    }

    @Test void rowReviewRejectsStaleRevisionAndNeverOverwritesNewContent() {
        Long id = create();
        fixture.db.update("UPDATE engineering_bom SET approve_status=? WHERE bom_id=?",
                ProductEnums.BomStatus.REVIEWING.getValue(), id);
        fixture.db.update("UPDATE engineering_bom_item SET review_status=? WHERE bom_id=?",
                ProductEnums.BomItemReviewStatus.PENDING_REMOVE.getValue(), id);
        Long itemId = fixture.service.getBomItems(id).get(0).getItemId();
        String loaded = revisionOf(id);
        // 审核员加载后，工程保存了新数量与名称
        fixture.db.update("UPDATE engineering_bom_item SET quantity=9.5, material_name='保存后的新名字' WHERE item_id=?",
                itemId);
        String latest = revisionOf(id);
        enableStrictSession(true, false);
        org.springframework.test.util.ReflectionTestUtils.setField(target(), "reviewFlowService",
                org.mockito.Mockito.mock(com.jjx.system.service.ReviewFlowService.class));

        var stale = new com.jjx.product.domain.dto.BomItemReviewDTO();
        stale.setItemId(itemId);
        stale.setAction("CONFIRM_REMOVE");
        stale.setExpectedContentRevision(loaded);
        var ex = assertThrows(BusinessException.class, () -> fixture.service.reviewItem(stale));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_CONTENT_STALE"), ex.getMessage());
        assertNotEquals(Boolean.TRUE, fixture.db.queryForObject(
                "SELECT remove_confirmed FROM engineering_bom_item WHERE item_id=?", Boolean.class, itemId),
                "过期审核不得写行");

        var fresh = new com.jjx.product.domain.dto.BomItemReviewDTO();
        fresh.setItemId(itemId);
        fresh.setAction("CONFIRM_REMOVE");
        fresh.setExpectedContentRevision(latest);
        fixture.service.reviewItem(fresh);
        // 只改审核字段：新数量/名称保留（锁后重读，不用旧实体整行覆盖）
        assertEquals(Boolean.TRUE, fixture.db.queryForObject(
                "SELECT remove_confirmed FROM engineering_bom_item WHERE item_id=?", Boolean.class, itemId));
        assertEquals("保存后的新名字", fixture.db.queryForObject(
                "SELECT material_name FROM engineering_bom_item WHERE item_id=?", String.class, itemId));
        assertEquals(0, new java.math.BigDecimal("9.5").compareTo(fixture.db.queryForObject(
                "SELECT quantity FROM engineering_bom_item WHERE item_id=?", java.math.BigDecimal.class, itemId)));
    }

    @Test void batchReviewFailsEveryRowWhenContentChangedAfterLoad() {
        Long id = create();
        fixture.db.update("UPDATE engineering_bom SET approve_status=? WHERE bom_id=?",
                ProductEnums.BomStatus.REVIEWING.getValue(), id);
        fixture.db.update("UPDATE engineering_bom_item SET review_status=? WHERE bom_id=?",
                ProductEnums.BomItemReviewStatus.PENDING.getValue(), id);
        fixture.db.update("UPDATE engineering_bom_item SET material_id=5 WHERE bom_id=?", id);
        String loaded = revisionOf(id);
        fixture.db.update("UPDATE engineering_bom_item SET material_name='批量前被改' WHERE bom_id=?", id);
        enableStrictSession(true, false);
        // 批量审核保留「每行 REQUIRES_NEW」：TransactionTemplate 需要独立事务管理器（隔离夹具默认未注入）。
        org.springframework.test.util.ReflectionTestUtils.setField(target(), "transactionManager",
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(fixture.db.getDataSource()));
        Long itemId = fixture.service.getBomItems(id).get(0).getItemId();

        var result = fixture.service.reviewItemsBatch(new IEngineeringBomService.BatchItemReviewRequest(
                id, List.of(itemId), "APPROVE", "批量通过", loaded));
        assertTrue(result.successIds().isEmpty());
        assertEquals(1, result.failed().size());
        assertTrue(result.failed().get(0).reason().contains("BOM_CONTENT_STALE"),
                result.failed().get(0).reason());
        // 每行仍是独立事务（原语义保留）：失败行不产生任何写入
        assertEquals(ProductEnums.BomItemReviewStatus.PENDING.getValue(),
                fixture.service.getBomItems(id).get(0).getReviewStatus());
        assertEquals("批量前被改", fixture.db.queryForObject(
                "SELECT material_name FROM engineering_bom_item WHERE bom_id=?", String.class, id));
    }
}
