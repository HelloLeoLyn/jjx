package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.product.domain.converter.EngineeringBomConverterImpl;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.domain.dto.EngineeringBomItemDTO;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.system.mapper.SysOperLogMapper;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 显式下层引用：自指/跨版本环/产出不一致/买挂方案/自制无方案（dev-20261111-004）。 */
class ManufacturingBomCycleTest {

    IEngineeringBomService service;
    JdbcTemplate db;

    @BeforeEach
    void setup() {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("mfg-cycle", new SpringManagedTransactionFactory(), source));
        configuration.addMapper(EngineeringBomMapper.class);
        configuration.addMapper(EngineeringBomItemMapper.class);
        configuration.addMapper(SysOperLogMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        db = new JdbcTemplate(source);
        createTable(EngineeringBom.class);
        createTable(EngineeringBomItem.class);
        createTable(com.jjx.system.domain.entity.SysOperLog.class);

        var materials = mock(InventoryMaterialMapper.class);
        when(materials.selectById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            var material = new InventoryMaterial();
            material.setMaterialId(id);
            material.setMaterialCode("M" + id);
            material.setMaterialName("物料" + id);
            material.setUnit("PCS");
            material.setStatus(1);
            return material;
        });
        var materialMaster = mock(com.jjx.inventory.master.MaterialMasterService.class);
        when(materialMaster.specificationSource(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of("size", "系统规格", "attributes", java.util.Map.of()));
        var implementation = new EngineeringBomServiceImpl(
                session.getMapper(EngineeringBomMapper.class),
                session.getMapper(EngineeringBomItemMapper.class),
                mock(ProductMapper.class), new EngineeringBomConverterImpl(), null, materials,
                null, null, mock(OperLogChangeRecorder.class), null);
        org.springframework.test.util.ReflectionTestUtils.setField(implementation, "bomSpecificationService",
                new BomSpecificationService(materialMaster, materials, session.getMapper(SysOperLogMapper.class)));
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
            String sqlType = type == String.class ? "VARCHAR(4000)"
                    : type == LocalDateTime.class ? "TIMESTAMP"
                    : type == Boolean.class ? "BOOLEAN"
                    : type == BigDecimal.class ? "DECIMAL(18,8)" : "BIGINT";
            columns.append(',').append(field.getColumn()).append(' ').append(sqlType);
        }
        db.execute("CREATE TABLE " + table.getTableName() + " (" + columns + ")");
    }

    private EngineeringBomItemDTO row(long materialId, String sourceType, Long subBomId) {
        var row = new EngineeringBomItemDTO();
        row.setMaterialId(materialId);
        row.setMaterialCode("M" + materialId);
        row.setMaterialName("物料" + materialId);
        row.setUnit("PCS");
        row.setSourceType(sourceType);
        row.setSubBomId(subBomId);
        row.setModuleQty(BigDecimal.ONE);
        row.setBaseQty(BigDecimal.ONE);
        row.setItemOrder(1);
        return row;
    }

    private long createScheme(String code, long outputMaterialId, EngineeringBomItemDTO row) {
        var dto = new EngineeringBomDTO();
        dto.setBomCode(code);
        dto.setBomName(code);
        dto.setBomVersion("V1.0");
        dto.setOutputType("MATERIAL");
        dto.setOutputId(outputMaterialId);
        dto.setItems(List.of(row));
        return service.createBomReturnId(dto);
    }

    private EngineeringBomDTO updatePayload(long bomId, long outputMaterialId, EngineeringBomItemDTO row) {
        row.setItemId(db.queryForObject("SELECT item_id FROM engineering_bom_item WHERE bom_id=?", Long.class, bomId));
        var dto = new EngineeringBomDTO();
        dto.setBomId(bomId);
        dto.setBomCode("UPD-" + bomId);
        dto.setBomName("UPD");
        dto.setBomVersion("V1.0");
        dto.setOutputType("MATERIAL");
        dto.setOutputId(outputMaterialId);
        dto.setItems(List.of(row));
        return dto;
    }

    @Test
    void rejectsSelfReference() {
        long a = createScheme("A", 349, row(110, "buy", null));
        var ex = assertThrows(BusinessException.class,
                () -> service.updateBomWithDetail(updatePayload(a, 349, row(349, "make", a))));
        assertTrue(ex.getMessage().contains("BOM_CYCLE"), ex.getMessage());
    }

    @Test
    void rejectsDirectAndAncestorCycles() {
        long a = createScheme("A", 349, row(110, "buy", null));
        long b = createScheme("B", 349, row(349, "make", a));
        var ex2 = assertThrows(BusinessException.class,
                () -> service.updateBomWithDetail(updatePayload(a, 349, row(349, "make", b))));
        assertTrue(ex2.getMessage().contains("BOM_CYCLE"), ex2.getMessage());
    }

    @Test
    void rejectsOutputMismatchBuyWithSchemeAndMakeWithoutScheme() {
        long a = createScheme("A", 349, row(110, "buy", null));
        long c = createScheme("C", 350, row(111, "buy", null));

        var mismatch = assertThrows(BusinessException.class,
                () -> service.updateBomWithDetail(updatePayload(a, 349, row(351, "make", c))));
        assertTrue(mismatch.getMessage().contains("SUB_BOM_OUTPUT_MISMATCH"), mismatch.getMessage());

        var buy = assertThrows(BusinessException.class,
                () -> service.updateBomWithDetail(updatePayload(a, 349, row(349, "buy", c))));
        assertTrue(buy.getMessage().contains("BUY_WITH_SUB_BOM"), buy.getMessage());

        var make = assertThrows(BusinessException.class,
                () -> service.updateBomWithDetail(updatePayload(a, 349, row(349, "make", null))));
        assertTrue(make.getMessage().contains("MAKE_WITHOUT_SCHEME"), make.getMessage());
    }

    @Test
    void deepLegalChainBeyond32LevelsPasses() {
        long previous = -1L;
        long top = -1L;
        for (int i = 1; i <= 40; i++) {
            long output = 5000L + i;
            EngineeringBomItemDTO row = previous < 0
                    ? row(100L, "buy", null)
                    : row(5000L + i - 1, "make", previous);
            top = createScheme("CHAIN-" + i, output, row);
            previous = top;
        }
        assertTrue(top > 0, "40 层合法显式链不应被固定深度上限拒绝");
    }

    @Test
    void deepClosedLoopRejectedWithoutDepthLimit() {
        long previous = -1L;
        long first = -1L;
        long top = -1L;
        for (int i = 1; i <= 40; i++) {
            long output = 6000L + i;
            EngineeringBomItemDTO row = previous < 0
                    ? row(100L, "buy", null)
                    : row(6000L + i - 1, "make", previous);
            long created = createScheme("LOOP-" + i, output, row);
            if (first < 0) first = created;
            previous = created;
            top = created;
        }
        final long firstBom = first;
        final long topBom = top;
        var ex = assertThrows(BusinessException.class,
                () -> service.updateBomWithDetail(updatePayload(firstBom, 6001, row(6040, "make", topBom))));
        assertTrue(ex.getMessage().contains("BOM_CYCLE"), ex.getMessage());
    }

    @Test
    void sharedSubgraphIsNotReportedAsCycle() {
        long d = createScheme("SHARED-D", 7004, row(100, "buy", null));
        long b = createScheme("SHARED-B", 7002, row(7004, "make", d));
        long c = createScheme("SHARED-C", 7003, row(7004, "make", d));
        var a = new EngineeringBomDTO();
        a.setBomCode("SHARED-A");
        a.setBomName("SHARED-A");
        a.setBomVersion("V1.0");
        a.setOutputType("MATERIAL");
        a.setOutputId(7001L);
        a.setItems(List.of(row(7002, "make", b), row(7003, "make", c)));
        assertNotNull(service.createBomReturnId(a), "共享 DAG 不是环，不应被误判");
    }

    @Test
    void concurrentCrossReferenceIsSerializedAndOnlyOneCommitWins() throws Exception {
        long a = createScheme("CONC-A", 349, row(110, "buy", null));
        long b = createScheme("CONC-B", 350, row(111, "buy", null));
        var aToB = updatePayload(a, 349, row(350, "make", b));
        var bToA = updatePayload(b, 350, row(349, "make", a));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> first = () -> {
            start.await();
            try {
                service.updateBomWithDetail(aToB);
                return "OK";
            } catch (BusinessException ex) {
                return ex.getMessage();
            }
        };
        Callable<String> second = () -> {
            start.await();
            try {
                service.updateBomWithDetail(bToA);
                return "OK";
            } catch (BusinessException ex) {
                return ex.getMessage();
            }
        };
        Future<String> f1 = pool.submit(first);
        Future<String> f2 = pool.submit(second);
        start.countDown();
        String r1;
        String r2;
        try {
            r1 = f1.get(20, TimeUnit.SECONDS);
            r2 = f2.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        long ok = ("OK".equals(r1) ? 1 : 0) + ("OK".equals(r2) ? 1 : 0);
        assertEquals(1, ok, "并发互引只能有一个事务提交成功: " + r1 + " / " + r2);
        assertTrue((r1 + " / " + r2).contains("BOM_CYCLE"), "失败方必须因成环被拒绝: " + r1 + " / " + r2);
        assertEquals(1L, db.queryForObject(
                "SELECT COUNT(*) FROM engineering_bom_item WHERE sub_bom_id IS NOT NULL", Long.class),
                "最终图只能留下一条有向边，不能形成 A→B→A");
    }

    @Test
    void graphLockBlocksSecondWriterUntilFirstCommitsThenRechecksGraph() throws Exception {
        long a = createScheme("LOCK-A", 349, row(110, "buy", null));
        long b = createScheme("LOCK-B", 350, row(111, "buy", null));
        var bToA = updatePayload(b, 350, row(349, "make", a));

        CountDownLatch committed = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<String> writer = pool.submit(() -> {
            committed.await();
            try {
                service.updateBomWithDetail(bToA);
                return "OK";
            } catch (BusinessException ex) {
                return ex.getMessage();
            }
        });
        String result;
        try {
            try (var conn = db.getDataSource().getConnection()) {
                conn.setAutoCommit(false);
                try (var st = conn.createStatement()) {
                    st.executeQuery("SELECT bom_id FROM engineering_bom ORDER BY bom_id FOR UPDATE").close();
                }
                committed.countDown();
                Thread.sleep(800);
                assertFalse(writer.isDone(), "第二个写者必须在数据库图锁上等待，不能凭快照读直接提交");
                // 先持锁事务写入 A→B 后提交；等待者恢复后必须重读已提交图并发现 B→A 成环。
                try (var st = conn.createStatement()) {
                    st.executeUpdate("UPDATE engineering_bom_item SET source_type='make', sub_bom_id=" + b
                            + " WHERE bom_id=" + a);
                }
                conn.commit();
            }
            result = writer.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertTrue(result.contains("BOM_CYCLE"), "锁释放后必须重读已提交图并拒绝成环: " + result);
        assertEquals(1L, db.queryForObject(
                "SELECT COUNT(*) FROM engineering_bom_item WHERE sub_bom_id IS NOT NULL", Long.class));
    }

    @Test
    void unchangedHistoricalMakeRowCanStillBeSaved() {
        long a = createScheme("LEGACY", 349, row(110, "buy", null));
        db.update("UPDATE engineering_bom_item SET source_type='make', sub_bom_id=NULL WHERE bom_id=?", a);
        var dto = updatePayload(a, 349, row(110, "make", null));
        dto.setRemark("历史行只改备注，不强制补方案");
        assertNotNull(service.updateBomWithDetail(dto));
    }
}
