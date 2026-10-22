package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.domain.converter.EngineeringBomConverterImpl;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.EngineeringBomItemDTO;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.system.service.OperLogChangeRecorder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 真实 Service、MyBatis Mapper 与 Spring 事务，使用隔离 H2；不写业务 MySQL。 */
class BomGroupPersistenceTest {
    IEngineeringBomService service;
    JdbcTemplate db;

    @BeforeEach
    void setup() {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("bom-groups", new SpringManagedTransactionFactory(), source));
        configuration.addMapper(EngineeringBomMapper.class);
        configuration.addMapper(EngineeringBomItemMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        db = new JdbcTemplate(source);
        createTable(EngineeringBom.class);
        createTable(EngineeringBomItem.class);
        var implementation = new EngineeringBomServiceImpl(session.getMapper(EngineeringBomMapper.class),
                session.getMapper(EngineeringBomItemMapper.class), mock(ProductMapper.class), new EngineeringBomConverterImpl(),
                null, null, null, null, mock(OperLogChangeRecorder.class), null);
        var proxy = new ProxyFactory(implementation);
        proxy.setInterfaces(IEngineeringBomService.class);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        service = (IEngineeringBomService) proxy.getProxy();
    }

    private void createTable(Class<?> entity) {
        var table = TableInfoHelper.getTableInfo(entity);
        var columns = new StringBuilder(table.getKeyColumn() + " BIGINT AUTO_INCREMENT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            Class<?> type = field.getPropertyType();
            String sqlType = type == String.class ? "VARCHAR(2000)"
                    : type == LocalDateTime.class ? "TIMESTAMP"
                    : type == Boolean.class ? "BOOLEAN"
                    : type == BigDecimal.class ? "DECIMAL(18,4)" : "BIGINT";
            columns.append(',').append(field.getColumn()).append(' ').append(sqlType);
        }
        db.execute("CREATE TABLE " + table.getTableName() + " (" + columns + ")");
    }

    private EngineeringBomItemDTO row(long id, Long parent, String name, int order) {
        var row = new EngineeringBomItemDTO();
        row.setItemId(id);
        row.setParentMaterialId(parent);
        row.setMaterialId(0L);
        row.setMaterialName(name);
        row.setProcessId(7L);
        row.setProcessName("模切");
        row.setUnit("PCS");
        row.setBaseQty(BigDecimal.ONE);
        row.setModuleQty(BigDecimal.ONE);
        row.setItemOrder(order);
        return row;
    }

    private EngineeringBomDTO payload(EngineeringBomItemDTO... rows) {
        var dto = new EngineeringBomDTO();
        dto.setProductId(1L);
        dto.setBomCode("GROUP-BOM");
        dto.setBomName("原名称");
        dto.setBomVersion("V1.0");
        dto.setItems(List.of(rows));
        return dto;
    }

    @Test
    void createAndReopenGroupsNeverPersistsTemporaryIdsEvenAcrossTwoNewBoms() {
        for (int n = 0; n < 2; n++) {
            var first = row(-1, null, "甲", 1);
            var second = row(-2, -1L, "乙", 2);
            Long bomId = service.createBomReturnId(payload(first, second, row(-3, null, "独立料", 3)));
            var stored = service.getBomItems(bomId);
            assertEquals(3, stored.size());
            assertTrue(stored.stream().allMatch(it -> it.getItemId() > 0));
            assertEquals(List.of("甲", "乙", "独立料"), stored.stream().map(EngineeringBomItem::getMaterialName).toList());
            assertNull(stored.get(0).getParentMaterialId());
            assertEquals(stored.get(0).getItemId(), stored.get(1).getParentMaterialId());
            assertNull(stored.get(2).getParentMaterialId());
            assertEquals(-1L, second.getParentMaterialId(), "服务端映射不能改写请求中的临时引用");
        }
    }

    @Test
    void editCanInsertChildBeforeTemporaryLeaderAndPreservesExistingRowIds() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "旧料", 1)));
        Long existingId = service.getBomItems(bomId).get(0).getItemId();
        var edit = payload(row(-3, -2L, "新子料", 3), row(existingId, null, "旧料", 1), row(-2, null, "新首料", 2));
        edit.setBomId(bomId);
        assertTrue(service.updateBomWithDetail(edit).isSuccess());
        var stored = service.getBomItems(bomId);
        assertEquals(existingId, stored.get(0).getItemId());
        assertEquals("新首料", stored.get(1).getMaterialName());
        assertEquals(stored.get(1).getItemId(), stored.get(2).getParentMaterialId());
        assertEquals(-2L, edit.getItems().get(0).getParentMaterialId());
    }

    @Test
    void splittingAndReorderingGroupClearsOldParentInDatabase() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "甲", 1), row(-2, -1L, "乙", 2)));
        var stored = service.getBomItems(bomId);
        long a = stored.get(0).getItemId(), b = stored.get(1).getItemId();
        var reverse = payload(row(b, null, "乙", 1), row(a, b, "甲", 2));
        reverse.setBomId(bomId);
        service.updateBomWithDetail(reverse);
        stored = service.getBomItems(bomId);
        assertEquals(b, stored.get(0).getItemId());
        assertNull(stored.get(0).getParentMaterialId(), "原子行变首行必须清空旧父编号");
        assertEquals(b, stored.get(1).getParentMaterialId());
        var split = payload(row(b, null, "乙", 1), row(a, null, "甲", 2));
        split.getItems().get(0).setProcessId(null);
        split.getItems().get(0).setProcessName("");
        split.setBomId(bomId);
        service.updateBomWithDetail(split);
        assertTrue(service.getBomItems(bomId).stream().allMatch(it -> it.getParentMaterialId() == null));
        assertNull(service.getBomItems(bomId).get(0).getProcessId(), "清空项目后不能回读出旧工序编号");
    }

    @Test
    void failedCreateRollsBackMainAndDetailRows() {
        assertThrows(RuntimeException.class, () -> service.createBomReturnId(
                payload(row(-1, null, "甲", 1), row(-2, -999L, "无首行", 2))));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM engineering_bom", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM engineering_bom_item", Integer.class));
    }

    @Test
    void failedEditRollsBackMainNameAndInsertedRows() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "甲", 1)));
        Long itemId = service.getBomItems(bomId).get(0).getItemId();
        var edit = payload(row(itemId, null, "甲", 1), row(-2, -999L, "无首行", 2));
        edit.setBomId(bomId);
        edit.setBomName("不应保存的新名称");
        assertThrows(RuntimeException.class, () -> service.updateBomWithDetail(edit));
        assertEquals("原名称", db.queryForObject("SELECT bom_name FROM engineering_bom WHERE bom_id=?", String.class, bomId));
        assertEquals(1, service.getBomItems(bomId).size());
    }

    @Test
    void cyclicGroupsAreRejectedWithoutSavingMainOrDetails() {
        assertThrows(RuntimeException.class, () -> service.createBomReturnId(
                payload(row(-1, -2L, "甲", 1), row(-2, -1L, "乙", 2))));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM engineering_bom", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM engineering_bom_item", Integer.class));
    }

    @Test
    void copyRemapsEveryParentToNewVersionAndClearsApprovalWithoutChangingOriginal() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "甲", 1), row(-2, -1L, "乙", 2)));
        var original = service.getBomItems(bomId);
        db.update("UPDATE engineering_bom_item SET approved_fingerprint='old', approved_by='审核员', review_status=? WHERE bom_id=?",
                ProductEnums.BomItemReviewStatus.APPROVED.getValue(), bomId);
        var copied = service.copyAsNewVersion(bomId, "V2.0");
        var rows = service.getBomItems(copied.getBomId());
        assertEquals(2, rows.size());
        assertEquals(rows.get(0).getItemId(), rows.get(1).getParentMaterialId());
        assertNotEquals(original.get(0).getItemId(), rows.get(0).getItemId());
        assertEquals(original.get(0).getItemId(), service.getBomItems(bomId).get(1).getParentMaterialId());
        for (var row : rows) {
            assertEquals(copied.getBomId(), row.getBomId());
            assertEquals(ProductEnums.BomItemReviewStatus.DRAFT.getValue(), row.getReviewStatus());
            assertNull(row.getApprovedFingerprint());
            assertNull(row.getApprovedBy());
        }
    }

    @Test
    void invalidVersionParentRollsBackNewVersionAndAllCopiedRows() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "甲", 1), row(-2, -1L, "乙", 2)));
        db.update("UPDATE engineering_bom_item SET parent_material_id=99999 WHERE bom_id=? AND item_order=2", bomId);
        assertThrows(RuntimeException.class, () -> service.copyAsNewVersion(bomId, "V2.0"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM engineering_bom", Integer.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM engineering_bom_item", Integer.class));
    }

    @Test
    void legacyApprovalIsAdoptedWithoutDowngradeButRegroupingRequiresReviewAndLogsBothDuplicateRows() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "同料", 1), row(-2, -1L, "同料", 2)));
        var stored = service.getBomItems(bomId);
        for (var it : stored) {
            db.update("UPDATE engineering_bom_item SET approved_fingerprint=?,review_status=? WHERE item_id=?",
                    EngineeringBomServiceImpl.legacyFingerprint(it), ProductEnums.BomItemReviewStatus.APPROVED.getValue(), it.getItemId());
        }
        var unchanged = payload(row(stored.get(0).getItemId(), null, "同料", 1),
                row(stored.get(1).getItemId(), stored.get(0).getItemId(), "同料", 2));
        unchanged.setBomId(bomId);
        service.updateBomWithDetail(unchanged);
        for (var it : service.getBomItems(bomId)) {
            assertEquals(ProductEnums.BomItemReviewStatus.APPROVED.getValue(), it.getReviewStatus());
            assertEquals(64, it.getApprovedFingerprint().length());
            assertEquals(EngineeringBomServiceImpl.fingerprint(it), it.getApprovedFingerprint());
        }
        var regroup = payload(row(stored.get(1).getItemId(), null, "同料", 1),
                row(stored.get(0).getItemId(), stored.get(1).getItemId(), "同料", 2));
        regroup.setBomId(bomId);
        var detail = cn.hutool.json.JSONUtil.parseObj(service.updateBomWithDetail(regroup).getDetailMessage());
        assertEquals(2, detail.getJSONArray("groupChanges").size());
        assertTrue(service.getBomItems(bomId).stream().allMatch(it ->
                it.getReviewStatus().equals(ProductEnums.BomItemReviewStatus.PENDING.getValue())));
    }

    @Test
    void removingLeaderWhileRetainingChildRollsBackAndCannotLeaveDanglingReference() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "甲", 1), row(-2, -1L, "乙", 2)));
        var stored = service.getBomItems(bomId);
        var leader = row(stored.get(0).getItemId(), null, "甲", 1);
        leader.setRemoveRequested(true);
        var edit = payload(leader, row(stored.get(1).getItemId(), leader.getItemId(), "乙", 2));
        edit.setBomId(bomId);
        assertThrows(RuntimeException.class, () -> service.updateBomWithDetail(edit));
        assertEquals(2, service.getBomItems(bomId).size());
        assertEquals(leader.getItemId(), service.getBomItems(bomId).get(1).getParentMaterialId());
    }

    @Test
    void cyclicOldVersionCannotBeCopied() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "甲", 1), row(-2, -1L, "乙", 2)));
        var stored = service.getBomItems(bomId);
        db.update("UPDATE engineering_bom_item SET parent_material_id=? WHERE item_id=?",
                stored.get(1).getItemId(), stored.get(0).getItemId());
        assertThrows(RuntimeException.class, () -> service.copyAsNewVersion(bomId, "V2.0"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM engineering_bom", Integer.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM engineering_bom_item", Integer.class));
    }

    @Test
    void omittingMixedStatusGroupCannotDeleteLeaderAndLeaveApprovedChildDangling() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "甲", 1), row(-2, -1L, "乙", 2)));
        var stored = service.getBomItems(bomId);
        db.update("UPDATE engineering_bom_item SET review_status=? WHERE item_id=?",
                ProductEnums.BomItemReviewStatus.APPROVED.getValue(), stored.get(1).getItemId());
        var edit = payload();
        edit.setBomId(bomId);
        assertThrows(RuntimeException.class, () -> service.updateBomWithDetail(edit));
        assertEquals(2, service.getBomItems(bomId).size());
        assertEquals(ProductEnums.BomItemReviewStatus.APPROVED.getValue(), service.getBomItems(bomId).get(1).getReviewStatus());
    }

    @Test
    void pendingRemovalChildRetainsContentAndBlocksDeletingItsLeader() {
        Long bomId = service.createBomReturnId(payload(row(-1, null, "甲", 1), row(-2, -1L, "乙", 2)));
        var stored = service.getBomItems(bomId);
        db.update("UPDATE engineering_bom_item SET review_status=? WHERE item_id=?",
                ProductEnums.BomItemReviewStatus.PENDING_REMOVE.getValue(), stored.get(1).getItemId());
        var child = row(stored.get(1).getItemId(), null, "乙", 2);
        child.setRemoveRequested(true);
        var edit = payload(child);
        edit.setBomId(bomId);
        assertThrows(RuntimeException.class, () -> service.updateBomWithDetail(edit));
        assertEquals(2, service.getBomItems(bomId).size());
        assertEquals(stored.get(0).getItemId(), service.getBomItems(bomId).get(1).getParentMaterialId());
    }
}
