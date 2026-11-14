package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.product.domain.dto.BomReviewPackageRequest;
import com.jjx.product.domain.dto.UpdateBomStatusDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.BomReviewBatchResultVO;
import com.jjx.product.domain.vo.BomReviewPackageVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.system.service.ReviewFlowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 整套审核真实事务边界（H2 + DataSourceTransactionManager，mapper 替身；不代表生产MySQL实跑）。
 * 覆盖：单产品全成或全回滚、共享方案只审一次、重复提交不重复日志。
 */
class BomReviewPackageTransactionTest {

    EmbeddedDatabase db;
    JdbcTemplate jdbc;
    EngineeringBomServiceImpl service;
    EngineeringBomMapper boms;
    EngineeringBomItemMapper items;
    ReviewFlowService flows;
    AtomicBoolean failRootApprove = new AtomicBoolean(false);

    @AfterEach
    void close() {
        if (db != null) db.shutdown();
    }

    @BeforeEach
    void setup() {
        db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(db);
        jdbc.execute("create table review_bom(id bigint primary key, status int, version varchar(20), code varchar(50), output_type varchar(20), output_id bigint)");
        jdbc.execute("create table review_item(item_id bigint primary key, bom_id bigint, parent_material_id bigint, material_id bigint, sub_bom_id bigint, review_status int, source_type varchar(10), material_name varchar(100), quantity decimal(18,4), unit varchar(20), module_qty decimal(18,4), base_qty decimal(18,4), item_order int)");
        jdbc.execute("create table review_log(id bigint auto_increment primary key, biz_type varchar(50), biz_id bigint, action varchar(30))");

        boms = mock(EngineeringBomMapper.class);
        items = mock(EngineeringBomItemMapper.class);
        var products = mock(ProductMapper.class);
        var materials = mock(InventoryMaterialMapper.class);
        flows = mock(ReviewFlowService.class);

        service = spy(new EngineeringBomServiceImpl(boms, items, products, null, null, materials,
                flows, null, null, null));
        ReflectionTestUtils.setField(service, "transactionManager", new DataSourceTransactionManager(db));
        ReflectionTestUtils.setField(service, "baseMapper", boms);
        doAnswer(inv -> {
            UpdateBomStatusDTO dto = inv.getArgument(0);
            int updated = jdbc.update("update review_bom set status=? where id=? and status=?",
                    dto.getTarget(), dto.getBomId(), dto.getCurrent());
            return updated > 0;
        }).when(service).updateStatus(any(UpdateBomStatusDTO.class));

        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), "test"),
                EngineeringBomItem.class);
        when(boms.selectById(anyLong())).thenAnswer(inv -> loadBom(inv.getArgument(0)));
        when(boms.selectBatchIds(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            List<EngineeringBom> list = new ArrayList<>();
            for (Long id : ids) {
                EngineeringBom bom = loadBom(id);
                if (bom != null) list.add(bom);
            }
            return list;
        });
        when(boms.selectCount(any())).thenReturn(0L);
        when(boms.lockGraphForMutation()).thenReturn(List.of(1L, 2L, 4L));
        when(products.selectById(anyLong())).thenAnswer(inv -> {
            Product product = new Product();
            product.setProductId(inv.getArgument(0));
            product.setProductCode("P" + inv.getArgument(0));
            product.setProductName("产品" + inv.getArgument(0));
            return product;
        });
        when(materials.selectById(anyLong())).thenAnswer(inv -> {
            InventoryMaterial material = new InventoryMaterial();
            material.setMaterialId(inv.getArgument(0));
            material.setMaterialCode("M" + inv.getArgument(0));
            material.setMaterialName("材料" + inv.getArgument(0));
            material.setUnit("PCS");
            material.setStatus(1);
            return material;
        });
        when(items.selectList(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<?> wrapper = inv.getArgument(0);
            wrapper.getSqlSegment();
            Long bomId = (Long) wrapper.getParamNameValuePairs().values().iterator().next();
            return loadItems(bomId);
        });
        when(items.selectByBomIdForUpdate(anyLong())).thenAnswer(inv -> loadItems(inv.getArgument(0)));
        when(items.updateById(any(EngineeringBomItem.class))).thenAnswer(inv -> {
            EngineeringBomItem item = inv.getArgument(0);
            return jdbc.update("update review_item set review_status=? where item_id=?",
                    new Object[]{item.getReviewStatus(), item.getItemId()});
        });
        when(items.deleteById(anyLong())).thenAnswer(inv ->
                jdbc.update("delete from review_item where item_id=?", new Object[]{inv.getArgument(0)}));
        doAnswer(inv -> {
            String bizType = inv.getArgument(0);
            Long bizId = inv.getArgument(1);
            String action = inv.getArgument(2);
            jdbc.update("insert into review_log(biz_type,biz_id,action) values(?,?,?)", bizType, bizId, action);
            if (failRootApprove.get() && "engineering_bom".equals(bizType)
                    && "APPROVE".equals(action) && Long.valueOf(1L).equals(bizId)) {
                throw new IllegalStateException("模拟根审核日志失败");
            }
            return null;
        }).when(flows).record(anyString(), anyLong(), anyString(), anyString(), any(), any(), any(), any());
    }

    void bom(long id, String type, long outputId, int status, String code) {
        jdbc.update("insert into review_bom values(?,?,?,?,?,?)", id, status, "V1.0", code, type, outputId);
    }

    void item(long itemId, long bomId, Long materialId, Long subBomId, String source, int status) {
        jdbc.update("insert into review_item values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                itemId, bomId, null, materialId, subBomId, status, source,
                "材料" + materialId, BigDecimal.ONE, "PCS", BigDecimal.ONE, BigDecimal.ONE, (int) itemId);
    }

    EngineeringBom loadBom(long id) {
        var rows = jdbc.query("select * from review_bom where id=?", (rs, n) -> {
            EngineeringBom bom = new EngineeringBom();
            bom.setBomId(rs.getLong("id"));
            bom.setApproveStatus(rs.getInt("status"));
            bom.setBomVersion(rs.getString("version"));
            bom.setBomCode(rs.getString("code"));
            bom.setBomName(rs.getString("code"));
            bom.setOutputType(rs.getString("output_type"));
            bom.setOutputId(rs.getLong("output_id"));
            bom.setProductId(null);
            bom.setIsCurrent(false);
            return bom;
        }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    List<EngineeringBomItem> loadItems(long bomId) {
        return jdbc.query("select * from review_item where bom_id=? order by item_id", (rs, n) -> {
            EngineeringBomItem row = new EngineeringBomItem();
            row.setItemId(rs.getLong("item_id"));
            row.setBomId(rs.getLong("bom_id"));
            row.setParentMaterialId(rs.getObject("parent_material_id", Long.class));
            row.setMaterialId(rs.getObject("material_id", Long.class));
            row.setSubBomId(rs.getObject("sub_bom_id", Long.class));
            row.setReviewStatus(rs.getInt("review_status"));
            row.setSourceType(rs.getString("source_type"));
            row.setMaterialName(rs.getString("material_name"));
            row.setMaterialCode("M" + row.getMaterialId());
            row.setQuantity(rs.getBigDecimal("quantity"));
            row.setUnit(rs.getString("unit"));
            row.setModuleQty(rs.getBigDecimal("module_qty"));
            row.setBaseQty(rs.getBigDecimal("base_qty"));
            row.setItemOrder(rs.getInt("item_order"));
            return row;
        }, bomId);
    }

    void seedSharedPackage() {
        // 产品1、产品4 均引用同一个材料方案2；材料方案2 只审一次。
        bom(1L, "PRODUCT", 100L, ProductEnums.BomStatus.REVIEWING.getValue(), "P1-BOM");
        bom(4L, "PRODUCT", 101L, ProductEnums.BomStatus.REVIEWING.getValue(), "P4-BOM");
        bom(2L, "MATERIAL", 5L, ProductEnums.BomStatus.REVIEWING.getValue(), "M5-MFG");
        item(11L, 1L, 5L, 2L, "make", ProductEnums.BomItemReviewStatus.PENDING.getValue());
        item(41L, 4L, 5L, 2L, "make", ProductEnums.BomItemReviewStatus.PENDING.getValue());
        item(21L, 2L, 5L, null, "buy", ProductEnums.BomItemReviewStatus.PENDING.getValue());
    }

    BomReviewPackageRequest.ProductScope scope(long rootBomId) {
        BomReviewPackageVO plan = service.planReviewPackage(rootBomId, "APPROVE");
        assertTrue(plan.isExecutable(), plan.getReason());
        BomReviewPackageRequest.ProductScope scope = new BomReviewPackageRequest.ProductScope();
        scope.setRootBomId(rootBomId);
        scope.setExpectedVersion(plan.getRootBomVersion());
        List<BomReviewPackageRequest.NodeScope> nodes = new ArrayList<>();
        for (BomReviewPackageVO.Node node : plan.getNodes()) {
            BomReviewPackageRequest.NodeScope item = new BomReviewPackageRequest.NodeScope();
            item.setBomId(node.getBomId());
            item.setVersion(node.getBomVersion());
            item.setStatus(node.getStatus());
            item.setPackageRevision(node.getPackageRevision());
            nodes.add(item);
        }
        scope.setExpectedNodes(nodes);
        return scope;
    }

    int status(long bomId) {
        return jdbc.queryForObject("select status from review_bom where id=?", Integer.class, bomId);
    }

    int rowStatus(long itemId) {
        return jdbc.queryForObject("select review_status from review_item where item_id=?", Integer.class, itemId);
    }

    int logs(long bizId, String action) {
        return jdbc.queryForObject("select count(*) from review_log where biz_id=? and action=?",
                Integer.class, bizId, action);
    }

    @Test
    void oneNodeFailureRollsBackWholeProductTransaction() {
        seedSharedPackage();
        BomReviewPackageRequest request = new BomReviewPackageRequest();
        request.setAction("APPROVE");
        request.setProducts(List.of(scope(1L)));
        failRootApprove.set(true);

        BomReviewBatchResultVO result = service.executeReviewPackages(request);

        assertEquals(1, result.getFailures().size());
        assertTrue(result.getSuccesses().isEmpty());
        // 子方案已先批准并写过日志，根节点失败后必须全部回滚，不出现半套通过。
        assertEquals(ProductEnums.BomStatus.REVIEWING.getValue(), status(2L));
        assertEquals(ProductEnums.BomStatus.REVIEWING.getValue(), status(1L));
        assertEquals(ProductEnums.BomItemReviewStatus.PENDING.getValue(), rowStatus(11L));
        assertEquals(ProductEnums.BomItemReviewStatus.PENDING.getValue(), rowStatus(21L));
        assertEquals(0, jdbc.queryForObject("select count(*) from review_log", Integer.class));
    }

    @Test
    void oneProductFailureDoesNotRollBackOtherSuccessfulProduct() {
        seedSharedPackage();
        BomReviewPackageRequest request = new BomReviewPackageRequest();
        request.setAction("APPROVE");
        request.setProducts(List.of(scope(1L), scope(4L)));
        failRootApprove.set(true);

        BomReviewBatchResultVO result = service.executeReviewPackages(request);

        // 产品1根日志失败只回滚产品1；产品4独立事务成功，共享方案由其批准。
        assertEquals(1, result.getFailures().size());
        assertEquals(1, result.getSuccesses().size());
        assertEquals(4L, result.getSuccesses().get(0).getRootBomId());
        assertEquals(ProductEnums.BomStatus.REVIEWING.getValue(), status(1L));
        assertEquals(ProductEnums.BomStatus.APPROVED.getValue(), status(4L));
        assertEquals(ProductEnums.BomStatus.APPROVED.getValue(), status(2L));
        assertEquals(ProductEnums.BomItemReviewStatus.PENDING.getValue(), rowStatus(11L));
        assertEquals(ProductEnums.BomItemReviewStatus.APPROVED.getValue(), rowStatus(41L));
    }

    @Test
    void sharedNodeIsApprovedOnceAndSecondProductReusesIt() {
        seedSharedPackage();
        BomReviewPackageRequest request = new BomReviewPackageRequest();
        request.setAction("APPROVE");
        request.setProducts(List.of(scope(1L), scope(4L)));

        BomReviewBatchResultVO result = service.executeReviewPackages(request);

        assertTrue(result.getFailures().isEmpty(), String.valueOf(result.getFailures()));
        assertEquals(2, result.getSuccesses().size());
        assertEquals(ProductEnums.BomStatus.APPROVED.getValue(), status(2L));
        assertEquals(ProductEnums.BomStatus.APPROVED.getValue(), status(1L));
        assertEquals(ProductEnums.BomStatus.APPROVED.getValue(), status(4L));
        assertEquals(1, logs(2L, "APPROVE"));
        assertEquals(1, logs(1L, "APPROVE"));
        assertEquals(1, logs(4L, "APPROVE"));
        assertTrue(result.getSharedNodes().stream().anyMatch(node -> node.getBomId() == 2L
                && node.getProducts().size() == 2));
    }

    @Test
    void retryWithStalePreviewDoesNotDuplicateLogsOrOverwrite() {
        seedSharedPackage();
        BomReviewPackageRequest request = new BomReviewPackageRequest();
        request.setAction("APPROVE");
        request.setProducts(List.of(scope(1L)));
        service.executeReviewPackages(request);
        int logsBefore = jdbc.queryForObject("select count(*) from review_log", Integer.class);

        BomReviewBatchResultVO retry = service.executeReviewPackages(request);

        assertEquals(1, retry.getFailures().size());
        assertTrue(retry.getSuccesses().isEmpty());
        assertTrue(retry.getFailures().get(0).getReason().contains("状态已变化")
                || retry.getFailures().get(0).getReason().contains("已批准"));
        assertEquals(logsBefore, jdbc.queryForObject("select count(*) from review_log", Integer.class));
        assertEquals(ProductEnums.BomStatus.APPROVED.getValue(), status(1L));
    }

    @Test
    void submitRollsBackAllNodesWhenRootSubmitLogFails() {
        bom(1L, "PRODUCT", 100L, ProductEnums.BomStatus.DRAFT.getValue(), "P1-BOM");
        bom(2L, "MATERIAL", 5L, ProductEnums.BomStatus.DRAFT.getValue(), "M5-MFG");
        item(11L, 1L, 5L, 2L, "make", ProductEnums.BomItemReviewStatus.DRAFT.getValue());
        item(21L, 2L, 5L, null, "buy", ProductEnums.BomItemReviewStatus.DRAFT.getValue());
        BomReviewPackageVO plan = service.planReviewPackage(1L, "SUBMIT");
        BomReviewPackageRequest.ProductScope scope = new BomReviewPackageRequest.ProductScope();
        scope.setRootBomId(1L);
        scope.setExpectedVersion(plan.getRootBomVersion());
        List<BomReviewPackageRequest.NodeScope> nodes = new ArrayList<>();
        for (BomReviewPackageVO.Node node : plan.getNodes()) {
            BomReviewPackageRequest.NodeScope item = new BomReviewPackageRequest.NodeScope();
            item.setBomId(node.getBomId());
            item.setVersion(node.getBomVersion());
            item.setStatus(node.getStatus());
            item.setPackageRevision(node.getPackageRevision());
            nodes.add(item);
        }
        scope.setExpectedNodes(nodes);
        BomReviewPackageRequest request = new BomReviewPackageRequest();
        request.setAction("SUBMIT");
        request.setProducts(List.of(scope));
        doAnswer(inv -> {
            UpdateBomStatusDTO dto = inv.getArgument(0);
            int updated = jdbc.update("update review_bom set status=? where id=? and status=?",
                    dto.getTarget(), dto.getBomId(), dto.getCurrent());
            if (dto.getTarget() != null
                    && dto.getTarget().equals(ProductEnums.BomStatus.REVIEWING.getValue())
                    && Long.valueOf(1L).equals(dto.getBomId())) {
                throw new IllegalStateException("模拟根送审状态写入后失败");
            }
            return updated > 0;
        }).when(service).updateStatus(any(UpdateBomStatusDTO.class));

        BomReviewBatchResultVO result = service.executeReviewPackages(request);

        assertEquals(1, result.getFailures().size());
        assertEquals(ProductEnums.BomStatus.DRAFT.getValue(), status(2L));
        assertEquals(ProductEnums.BomStatus.DRAFT.getValue(), status(1L));
        assertEquals(ProductEnums.BomItemReviewStatus.DRAFT.getValue(), rowStatus(21L));
        assertEquals(0, jdbc.queryForObject("select count(*) from review_log", Integer.class));
    }
}
