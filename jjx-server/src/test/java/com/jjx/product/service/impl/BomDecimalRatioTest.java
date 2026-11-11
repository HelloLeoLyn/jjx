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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 模数/基数正小数、超精度拒绝、单位依据与派生值非权威（dev-20261111-004）。 */
class BomDecimalRatioTest {

    IEngineeringBomService service;

    @BeforeEach
    void setup() {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("decimal", new SpringManagedTransactionFactory(), source));
        configuration.addMapper(EngineeringBomMapper.class);
        configuration.addMapper(EngineeringBomItemMapper.class);
        configuration.addMapper(SysOperLogMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        var db = new JdbcTemplate(source);
        createTable(db, EngineeringBom.class);
        createTable(db, EngineeringBomItem.class);
        createTable(db, com.jjx.system.domain.entity.SysOperLog.class);

        var materials = mock(InventoryMaterialMapper.class);
        when(materials.selectById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            var material = new InventoryMaterial();
            material.setMaterialId(id);
            material.setMaterialCode("M" + id);
            material.setMaterialName("物料" + id);
            material.setUnit("M");
            material.setStatus(1);
            return material;
        });
        var materialMaster = mock(com.jjx.inventory.master.MaterialMasterService.class);
        when(materialMaster.specificationSource(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of("size", "系统规格", "attributes", java.util.Map.of()));
        var implementation = new EngineeringBomServiceImpl(
                session.getMapper(EngineeringBomMapper.class), session.getMapper(EngineeringBomItemMapper.class),
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

    private void createTable(JdbcTemplate db, Class<?> entity) {
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

    private EngineeringBomItemDTO row(String module, String base, String unit) {
        var row = new EngineeringBomItemDTO();
        row.setMaterialId(110L);
        row.setMaterialCode("RM110");
        row.setMaterialName("卷料");
        row.setUnit(unit);
        row.setSourceType("buy");
        row.setModuleQty(module == null ? null : new BigDecimal(module));
        row.setBaseQty(base == null ? null : new BigDecimal(base));
        row.setItemOrder(1);
        return row;
    }

    private Long create(EngineeringBomItemDTO row) {
        var dto = new EngineeringBomDTO();
        dto.setBomCode("DEC-" + UUID.randomUUID());
        dto.setBomName("精度测试");
        dto.setBomVersion("V1.0");
        dto.setProductId(1L);
        dto.setOutputType("PRODUCT");
        dto.setOutputId(1L);
        dto.setItems(List.of(row));
        return service.createBomReturnId(dto);
    }

    @Test
    void savesPositiveDecimalBelowOneAndReadsBackRawRatio() {
        long bomId = create(row("0.5", "1", "M"));
        var saved = service.getBomDetail(bomId).getItems().get(0);
        assertEquals(0, saved.getModuleQty().compareTo(new BigDecimal("0.5")));
        assertEquals(0, saved.getQuantity().compareTo(new BigDecimal("2")));
    }

    @Test
    void rejectsZeroNegativeAndOverPrecisionInsteadOfRoundingToZero() {
        var zero = assertThrows(BusinessException.class, () -> create(row("0", "1", "M")));
        assertTrue(zero.getMessage().contains("MODULE_QTY_INVALID"), zero.getMessage());
        var negative = assertThrows(BusinessException.class, () -> create(row("1", "-1", "M")));
        assertTrue(negative.getMessage().contains("BASE_QTY_INVALID"), negative.getMessage());
        var overScale = assertThrows(BusinessException.class, () -> create(row("0.00001", "1", "M")));
        assertTrue(overScale.getMessage().contains("MODULE_QTY_INVALID"), overScale.getMessage());
        assertTrue(overScale.getMessage().contains("超精度"), overScale.getMessage());
        var empty = assertThrows(BusinessException.class, () -> create(row(null, "1", "M")));
        assertTrue(empty.getMessage().contains("MODULE_QTY_INVALID"), empty.getMessage());
    }

    @Test
    void sixModuleOneBaseProducesExactFiftyWhileSnapshotStaysDisplayOnly() {
        long bomId = create(row("6", "1", "M"));
        var saved = service.getBomDetail(bomId).getItems().get(0);
        assertEquals(0, saved.getQuantity().compareTo(new BigDecimal("0.1667")));
        // 权威是原始比例；300×1÷6=50，不允许用已舍入快照 0.1667×300=50.01。
        BigDecimal exact = BigDecimal.valueOf(300).multiply(saved.getBaseQty())
                .divide(saved.getModuleQty(), 0, RoundingMode.HALF_UP);
        assertEquals(0, exact.compareTo(new BigDecimal("50")));
        assertNotEquals(0, saved.getQuantity().multiply(BigDecimal.valueOf(300)).compareTo(new BigDecimal("50")));
    }

    @Test
    void requiresUnitFactorAndBasisOnlyWhenUnitDiffers() {
        var missing = assertThrows(BusinessException.class, () -> create(row("1", "1", "PCS")));
        assertTrue(missing.getMessage().contains("UNIT_FACTOR_MISSING"), missing.getMessage());

        var withFactor = row("1", "1", "PCS");
        withFactor.setUnitFactor(new BigDecimal("0.3"));
        withFactor.setUnitFactorBasis("每张 0.3 平方米");
        withFactor.setUnitFactorSource("供应商资料");
        assertNotNull(create(withFactor));
    }
}
