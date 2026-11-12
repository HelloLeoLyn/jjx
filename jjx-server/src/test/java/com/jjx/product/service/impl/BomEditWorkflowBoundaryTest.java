package com.jjx.product.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.EngineeringBomItemDTO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reuses the isolated H2 mapper/transaction fixture; never writes business MySQL. */
class BomEditWorkflowBoundaryTest {
    private BomGroupPersistenceTest fixture;
    private MockedStatic<SecurityUtils> security;

    @BeforeEach void setup() {
        fixture = new BomGroupPersistenceTest();
        fixture.setup();
        security = Mockito.mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(7L);
        security.when(SecurityUtils::getDisplayName).thenReturn("张三");
        security.when(SecurityUtils::getUsername).thenReturn("zhangsan");
    }

    @AfterEach void tearDown() {
        security.close();
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

    private EngineeringBomItemDTO rowDto(long itemId, int order) {
        var row = new EngineeringBomItemDTO();
        row.setItemId(itemId);
        row.setMaterialId(0L);
        row.setMaterialName("投入材料" + order);
        row.setMaterialSpecification("系统规格");
        row.setUnit("PCS");
        row.setBaseQty(BigDecimal.ONE);
        row.setModuleQty(BigDecimal.ONE);
        row.setItemOrder(order);
        return row;
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

    /** 取事务代理背后的真实实现，用于注入占用服务（隔离测试不装配 Spring）。 */
    private EngineeringBomServiceImpl target() {
        try {
            return (EngineeringBomServiceImpl) ((org.springframework.aop.framework.Advised) fixture.service)
                    .getTargetSource().getTarget();
        } catch (Exception ex) {
            throw new IllegalStateException("无法取得测试代理目标", ex);
        }
    }

    /**
     * 装配<b>真实</b> BomEditSessionServiceImpl（Redis 用内存替身）：
     * 账号归属、token 过期、Redis 故障、compare-token 全部走生产代码，不用“只看 token 非空”的桩。
     */
    private com.jjx.product.service.BomEditSessionServiceTest.FakeRedis useRealSession() {
        var redis = new com.jjx.product.service.BomEditSessionServiceTest.FakeRedis(null);
        org.springframework.test.util.ReflectionTestUtils.setField(target(), "bomEditSessionService", redis);
        assertEquals(Boolean.TRUE, org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                target(), "editContractActive"), "装配真实占用服务后严格校验分支必须启用");
        return redis;
    }

    /** 打开页面即取得本标签页编辑权，返回真实 token。 */
    private String lease(com.jjx.product.service.BomEditSessionServiceTest.FakeRedis redis, Long bomId) {
        var vo = redis.acquire(bomId, "tab-A");
        assertTrue(vo.isAcquired(), "测试前置：必须取得编辑权");
        assertNotNull(vo.getToken());
        return vo.getToken();
    }

    private String revisionOf(Long bomId) {
        return fixture.service.getBomDetail(bomId).getContentRevision();
    }

    /** 生产路径必须强制装配占用服务：不允许“未装配就当校验关闭”成为验收证据。 */
    @Test void productionWiringRequiresEditSessionService() throws Exception {
        var field = EngineeringBomServiceImpl.class.getDeclaredField("bomEditSessionService");
        var autowired = field.getAnnotation(org.springframework.beans.factory.annotation.Autowired.class);
        assertNotNull(autowired, "正式装配必须注入占用服务；隔离夹具的 null 分支只用于旧用例");
        assertTrue(autowired.required(), "@Autowired 必须是必注入，缺失时启动失败而不是静默关闭校验");
    }

    @Test void staleContentRevisionRejectsSecondSaveWithoutAnyWrite() {
        Long id = create();
        var redis = useRealSession();
        String token = lease(redis, id);
        String first = revisionOf(id);
        // 乙先保存：直接改库模拟另一会话已提交的新内容（真实并发由 MySQL 双会话另行验证）
        fixture.db.update("UPDATE engineering_bom_item SET material_name='乙改后' WHERE bom_id=?", id);
        String second = revisionOf(id);
        assertNotEquals(first, second, "内容变化必须改变内容指纹");

        var dto = edit(id);
        dto.setBomName("甲的旧快照覆盖");
        dto.setExpectedContentRevision(first);
        dto.setEditToken(token);
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
        var redis = useRealSession();
        String token = lease(redis, id);
        String first = revisionOf(id);

        var dto = edit(id);
        dto.setBomName("正常改名");
        dto.setExpectedContentRevision(first);
        dto.setEditToken(token);
        var result = fixture.service.updateBomWithDetail(dto);
        assertTrue(result.isSuccess());
        assertNotNull(result.getContentRevision());
        assertEquals(revisionOf(id), result.getContentRevision(),
                "保存返回的新指纹必须等于重新加载得到的指纹，否则同一会话下次保存会误判过期");

        var again = edit(id);
        again.setBomName("再改名");
        again.setExpectedContentRevision(result.getContentRevision());
        again.setEditToken(token);
        assertTrue(fixture.service.updateBomWithDetail(again).isSuccess(), "连续保存不能自我冲突");
        assertTrue(redis.describe(id, "tab-A").isAcquired(), "保存成功默认不释放租约");
    }

    @Test void otherAccountTokenRejectsSaveWithoutAnyWrite() {
        Long id = create();
        var redis = useRealSession();
        String token = lease(redis, id);
        String revision = revisionOf(id);

        // 同一 clientId 换账号（或他人拿到 token）：token 属于 7 号，当前登录是 8 号
        security.when(SecurityUtils::getUserId).thenReturn(8L);
        security.when(SecurityUtils::getDisplayName).thenReturn("李四");

        var dto = edit(id);
        dto.setBomName("他人 token 不得写入");
        dto.setExpectedContentRevision(revision);
        dto.setEditToken(token);
        var ex = assertThrows(BusinessException.class, () -> fixture.service.updateBomWithDetail(dto));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_EDIT_LEASE_FORBIDDEN"), ex.getMessage());
        assertEquals("BOM名称", fixture.db.queryForObject(
                "SELECT bom_name FROM engineering_bom WHERE bom_id=?", String.class, id));
        assertEquals(revision, revisionOf(id), "被拒绝的保存不得改变内容指纹");
    }

    @Test void expiredLeaseTokenRejectsSaveWithoutAnyWrite() {
        Long id = create();
        var redis = useRealSession();
        String token = lease(redis, id);
        String revision = revisionOf(id);
        redis.expire(com.jjx.product.service.impl.BomEditSessionServiceImpl.key(id));

        var dto = edit(id);
        dto.setBomName("过期 token 不得写入");
        dto.setExpectedContentRevision(revision);
        dto.setEditToken(token);
        var ex = assertThrows(BusinessException.class, () -> fixture.service.updateBomWithDetail(dto));
        assertEquals(Integer.valueOf(409), ex.getCode());
        assertTrue(ex.getMessage().contains("BOM_EDIT_LEASE_EXPIRED"), ex.getMessage());
        assertEquals("BOM名称", fixture.db.queryForObject(
                "SELECT bom_name FROM engineering_bom WHERE bom_id=?", String.class, id));
    }

    @Test void redisFailureRejectsSaveFailClosed() {
        Long id = create();
        var redis = useRealSession();
        String token = lease(redis, id);
        String revision = revisionOf(id);
        redis.broken = true;

        var dto = edit(id);
        dto.setBomName("Redis故障也不能写入");
        dto.setExpectedContentRevision(revision);
        dto.setEditToken(token);
        var ex = assertThrows(BusinessException.class, () -> fixture.service.updateBomWithDetail(dto));
        assertEquals(Integer.valueOf(503), ex.getCode(), "Redis 异常必须拒绝保存，不能 fail-open");
        assertTrue(ex.getMessage().contains("EDIT_LEASE_UNAVAILABLE"), ex.getMessage());
        assertEquals("BOM名称", fixture.db.queryForObject(
                "SELECT bom_name FROM engineering_bom WHERE bom_id=?", String.class, id));
    }

    @Test void missingContentRevisionIsRejectedEvenWithValidLease() {
        Long id = create();
        var redis = useRealSession();
        String token = lease(redis, id);
        var dto = edit(id);
        dto.setExpectedContentRevision(null);
        dto.setEditToken(token);
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
        useRealSession();

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
        useRealSession();
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
        useRealSession();
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

    /**
     * 两行都成功：第一行通过后 review_status 改变内容指纹，第二行必须用滚动后的基线，
     * 否则会被误判 stale。真实并发“行间被保存改动”未在本替身测试验证（见报告未验项）。
     */
    @Test void batchReviewApprovesTwoRowsWithRollingRevision() {
        var dto = payload(null, -1L);
        dto.setItems(List.of(rowDto(-1L, 1), rowDto(-2L, 2)));
        Long id = fixture.service.createBomReturnId(dto);
        fixture.db.update("UPDATE engineering_bom SET approve_status=? WHERE bom_id=?",
                ProductEnums.BomStatus.REVIEWING.getValue(), id);
        fixture.db.update("UPDATE engineering_bom_item SET material_id=5, review_status=? WHERE bom_id=?",
                ProductEnums.BomItemReviewStatus.PENDING.getValue(), id);
        String loaded = revisionOf(id);
        useRealSession();
        // 行审核 APPROVE 需要有效物料档案；隔离夹具默认没有注入物料 Mapper
        var materials = org.mockito.Mockito.mock(com.jjx.inventory.mapper.InventoryMaterialMapper.class);
        org.mockito.Mockito.when(materials.selectById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.jjx.inventory.domain.InventoryMaterial());
        org.springframework.test.util.ReflectionTestUtils.setField(target(), "inventoryMaterialMapper", materials);
        org.springframework.test.util.ReflectionTestUtils.setField(target(), "reviewFlowService",
                org.mockito.Mockito.mock(com.jjx.system.service.ReviewFlowService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(target(), "transactionManager",
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(fixture.db.getDataSource()));
        var items = fixture.service.getBomItems(id);
        assertEquals(2, items.size());
        List<Long> itemIds = items.stream().map(row -> row.getItemId()).toList();

        var result = fixture.service.reviewItemsBatch(new IEngineeringBomService.BatchItemReviewRequest(
                id, itemIds, "APPROVE", "批量通过", loaded));
        assertTrue(result.failed().isEmpty(), () -> "不应有失败行: " + result.failed());
        assertEquals(2, result.successIds().size());
        assertEquals(Integer.valueOf(2), fixture.db.queryForObject(
                "SELECT COUNT(*) FROM engineering_bom_item WHERE bom_id=? AND review_status=?",
                Integer.class, id, ProductEnums.BomItemReviewStatus.APPROVED.getValue()));
        assertNotEquals(loaded, revisionOf(id), "审核字段参与指纹：第一行成功后指纹已变化");
    }
}
