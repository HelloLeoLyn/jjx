package com.jjx.product.service.impl;

import com.jjx.engineering.domain.entity.*;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.*;
import com.jjx.product.service.IEngineeringBomService.BatchItemReviewRequest;
import com.jjx.system.service.ReviewFlowService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** H2真实事务 + mapper替身，不代表生产MySQL实跑。 */
class BomItemBatchReviewTest {
    EngineeringBomServiceImpl service;
    JdbcTemplate jdbc;
    EngineeringBomItemMapper items;
    ReviewFlowService flows;
    org.springframework.jdbc.datasource.embedded.EmbeddedDatabase db;
    @AfterEach void close() { db.shutdown(); }
    @BeforeEach void setup() {
        db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(db);
        jdbc.execute("create table batch_item(id bigint primary key, bom_id bigint, material_id bigint, status int)");
        jdbc.execute("create table batch_log(item_id bigint)");
        items = mock(EngineeringBomItemMapper.class);
        var boms = mock(EngineeringBomMapper.class);
        var materials = mock(com.jjx.inventory.mapper.InventoryMaterialMapper.class);
        flows = mock(ReviewFlowService.class);
        service = new EngineeringBomServiceImpl(boms, items, null, null, null, materials, flows, null, null, null);
        ReflectionTestUtils.setField(service, "transactionManager", new DataSourceTransactionManager(db));
        var bom = new EngineeringBom();
        bom.setBomId(1L);
        bom.setApproveStatus(ProductEnums.BomStatus.REVIEWING.getValue());
        when(boms.selectById(1L)).thenReturn(bom);
        when(materials.selectById(5L)).thenReturn(new InventoryMaterial());
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"), EngineeringBomItem.class);
        when(items.selectOne(any())).thenAnswer(inv -> {
            var wrapper = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) inv.getArgument(0);
            wrapper.getSqlSegment();
            Long id = (Long) wrapper.getParamNameValuePairs().values().iterator().next();
            return load(id, true);
        });
        when(items.selectById(anyLong())).thenAnswer(inv -> load(inv.getArgument(0), false));
        when(items.updateById(any(EngineeringBomItem.class))).thenAnswer(inv -> {
            EngineeringBomItem item = inv.getArgument(0);
            return jdbc.update("update batch_item set status=? where id=?", item.getReviewStatus(), item.getItemId());
        });
        doAnswer(inv -> {
            Long id = inv.getArgument(1);
            jdbc.update("insert into batch_log values(?)", id);
            if (id == 3L) throw new IllegalStateException("模拟日志写入失败");
            return null;
        }).when(flows).record(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), isNull());
    }
    EngineeringBomItem load(Long id, boolean lock) {
        var rows = jdbc.query("select * from batch_item where id=?" + (lock ? " for update" : ""), (rs, n) -> {
            var item = new EngineeringBomItem();
            item.setItemId(rs.getLong("id")); item.setBomId(rs.getLong("bom_id"));
            item.setMaterialId(rs.getObject("material_id", Long.class)); item.setReviewStatus(rs.getInt("status"));
            return item;
        }, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    void row(long id, long bomId, Long material, ProductEnums.BomItemReviewStatus status) {
        jdbc.update("insert into batch_item values(?,?,?,?)", id, bomId, material, status.getValue());
    }
    BatchItemReviewRequest request(Long... ids) { return new BatchItemReviewRequest(1L, List.of(ids), "APPROVE", "批量通过"); }
    @Test void allSuccessAndIdempotentRepeat() {
        row(1, 1, 5L, ProductEnums.BomItemReviewStatus.PENDING);
        row(2, 1, 5L, ProductEnums.BomItemReviewStatus.PENDING);
        var result = service.reviewItemsBatch(request(1L, 2L));
        assertEquals(List.of(1L, 2L), result.successIds()); assertTrue(result.failed().isEmpty());
        assertEquals(result, service.reviewItemsBatch(request(1L, 2L)));
        assertEquals(2, jdbc.queryForObject("select count(*) from batch_log", Integer.class));
    }
    @Test void partialFailureRollsBackOnlyFailedRowAndContinues() {
        row(1, 1, 5L, ProductEnums.BomItemReviewStatus.PENDING);
        row(2, 1, null, ProductEnums.BomItemReviewStatus.PENDING);
        row(3, 1, 5L, ProductEnums.BomItemReviewStatus.PENDING);
        row(4, 1, 5L, ProductEnums.BomItemReviewStatus.PENDING);
        var result = service.reviewItemsBatch(request(1L, 2L, 3L, 4L));
        assertEquals(List.of(1L, 4L), result.successIds());
        assertEquals(List.of(2L, 3L), result.failed().stream().map(f -> f.itemId()).toList());
        assertTrue(result.failed().getFirst().reason().contains("未关联物料"));
        assertEquals(ProductEnums.BomItemReviewStatus.PENDING.getValue(), load(3L, false).getReviewStatus());
        assertEquals(2, jdbc.queryForObject("select count(*) from batch_log", Integer.class));
    }
    @Test void rejectsWrongBomAndNonPendingStates() {
        row(1, 9, 5L, ProductEnums.BomItemReviewStatus.APPROVED);
        row(2, 1, 5L, ProductEnums.BomItemReviewStatus.DRAFT);
        row(3, 1, 5L, ProductEnums.BomItemReviewStatus.PENDING_REMOVE);
        row(4, 1, 5L, ProductEnums.BomItemReviewStatus.REJECTED);
        var result = service.reviewItemsBatch(request(1L, 2L, 3L, 4L, 99L));
        assertTrue(result.successIds().isEmpty()); assertEquals(5, result.failed().size());
        assertTrue(result.failed().getFirst().reason().contains("不属于")); verifyNoInteractions(flows);
        assertThrows(com.jjx.common.exception.BusinessException.class,
            () -> service.reviewItemsBatch(new BatchItemReviewRequest(1L, List.of(2L), "REJECT", null)));
    }
}
