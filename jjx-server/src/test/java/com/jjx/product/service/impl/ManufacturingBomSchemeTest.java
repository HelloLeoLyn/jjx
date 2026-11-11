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
import com.jjx.product.domain.dto.ManufacturingSchemeChoiceDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.product.service.ManufacturingBomService;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.service.OperLogChangeRecorder;
import com.jjx.system.service.ReviewFlowService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 制造方案保存回读、产品隔离、主方案同事务与草稿/审批边界（dev-20261111-004）。 */
class ManufacturingBomSchemeTest {

    IEngineeringBomService service;
    ManufacturingBomService manufacturing;
    JdbcTemplate db;
    ProductMapper productMapper;

    @BeforeEach
    void setup() {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("mfg-scheme", new SpringManagedTransactionFactory(), source));
        configuration.addMapper(EngineeringBomMapper.class);
        configuration.addMapper(EngineeringBomItemMapper.class);
        configuration.addMapper(ProductMapper.class);
        configuration.addMapper(SysOperLogMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        db = new JdbcTemplate(source);
        createTable(EngineeringBom.class);
        createTable(EngineeringBomItem.class);
        createTable(Product.class);
        createTable(com.jjx.system.domain.entity.SysOperLog.class);

        productMapper = session.getMapper(ProductMapper.class);
        var materials = mock(InventoryMaterialMapper.class);
        var material = new InventoryMaterial();
        material.setMaterialId(349L);
        material.setMaterialCode("SFG000349");
        material.setMaterialName("半成品349");
        material.setUnit("PCS");
        material.setStatus(1);
        when(materials.selectById(anyLong())).thenReturn(material);
        var materialMaster = mock(com.jjx.inventory.master.MaterialMasterService.class);
        when(materialMaster.specificationSource(material)).thenReturn(java.util.Map.of("size", "系统规格", "attributes", java.util.Map.of()));

        var reviewFlow = mock(ReviewFlowService.class);
        var implementation = new EngineeringBomServiceImpl(
                session.getMapper(EngineeringBomMapper.class),
                session.getMapper(EngineeringBomItemMapper.class),
                productMapper, new EngineeringBomConverterImpl(), null, materials,
                reviewFlow, null, mock(OperLogChangeRecorder.class), null);
        org.springframework.test.util.ReflectionTestUtils.setField(implementation, "bomSpecificationService",
                new BomSpecificationService(materialMaster, materials, session.getMapper(SysOperLogMapper.class)));
        var proxy = new ProxyFactory(implementation);
        proxy.setInterfaces(IEngineeringBomService.class);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        service = (IEngineeringBomService) proxy.getProxy();
        manufacturing = new ManufacturingBomServiceImpl(service, session.getMapper(EngineeringBomMapper.class), productMapper);
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

    private Product product(String code) {
        var product = new Product();
        product.setProductCode(code);
        product.setProductName(code);
        product.setProductStatus(1);
        productMapper.insert(product);
        return product;
    }

    private EngineeringBomItemDTO buyRow(long materialId) {
        var row = new EngineeringBomItemDTO();
        row.setMaterialId(materialId);
        row.setMaterialCode("RM" + materialId);
        row.setMaterialName("原料" + materialId);
        row.setUnit("PCS");
        row.setSourceType("buy");
        row.setModuleQty(new BigDecimal("6"));
        row.setBaseQty(BigDecimal.ONE);
        row.setItemOrder(1);
        return row;
    }

    private EngineeringBomItemDTO makeRow(long materialId, long subBomId) {
        var row = buyRow(materialId);
        row.setSourceType("make");
        row.setSubBomId(subBomId);
        row.setModuleQty(BigDecimal.ONE);
        row.setBaseQty(BigDecimal.ONE);
        return row;
    }

    private EngineeringBomDTO scheme(String code, String version, long materialId) {
        var dto = new EngineeringBomDTO();
        dto.setBomCode(code);
        dto.setBomName(code);
        dto.setBomVersion(version);
        dto.setOutputType("MATERIAL");
        dto.setOutputId(materialId);
        dto.setItems(List.of(buyRow(110)));
        return dto;
    }

    private EngineeringBomDTO parent(Long productId, long materialId, long childBomId, Integer revision) {
        var dto = new EngineeringBomDTO();
        dto.setBomCode("TOP-" + productId + "-" + childBomId);
        dto.setBomName("成品BOM");
        dto.setBomVersion("V1.0");
        dto.setOutputType("PRODUCT");
        dto.setOutputId(productId);
        dto.setProductId(productId);
        dto.setItems(List.of(makeRow(materialId, childBomId)));
        var choice = new ManufacturingSchemeChoiceDTO();
        choice.setMaterialId(materialId);
        choice.setBomId(childBomId);
        choice.setIsPrimary(true);
        dto.setSchemeChoices(List.of(choice));
        dto.setSchemeRevision(revision);
        return dto;
    }

    @Test
    void savesReadsSchemesAndKeepsProductChoicesIsolated() {
        var p1 = product("P1");
        var p2 = product("P2");
        long child1 = service.createBomReturnId(scheme("SFG349-A", "V1.0", 349));
        long child2 = service.createBomReturnId(scheme("SFG349-B", "V1.0", 349));

        long parent1 = service.createBomReturnId(parent(p1.getProductId(), 349, child1, 0));
        long parent2 = service.createBomReturnId(parent(p2.getProductId(), 349, child2, 0));

        var saved1 = productMapper.selectById(p1.getProductId());
        var saved2 = productMapper.selectById(p2.getProductId());
        assertEquals(Integer.valueOf(1), saved1.getSchemeRevision());
        assertEquals(Integer.valueOf(1), saved2.getSchemeRevision());
        assertTrue(saved1.getMaterialSchemeJson().contains("\"349\""));
        assertTrue(saved1.getMaterialSchemeJson().contains("\"bomId\":" + child1));
        assertTrue(saved2.getMaterialSchemeJson().contains("\"bomId\":" + child2));

        var detail = service.getBomDetail(parent1);
        assertEquals("PRODUCT", detail.getOutputType());
        assertEquals(Integer.valueOf(1), detail.getSchemeRevision());
        assertEquals(Long.valueOf(child1), detail.getSchemeChoices().get(0).getBomId());
        assertEquals(Boolean.TRUE, detail.getSchemeChoices().get(0).getIsPrimary());

        var childDetail = service.getBomDetail(child1);
        assertEquals("MATERIAL", childDetail.getOutputType());
        assertEquals(Long.valueOf(349), childDetail.getOutputId());
        assertEquals("6.00000000", childDetail.getItems().get(0).getModuleQty().toPlainString());

        var options = manufacturing.options("MATERIAL", 349L, p1.getProductId());
        assertEquals(2, options.size());
        assertTrue(options.stream().filter(o -> o.getBomId() == child1).findFirst().orElseThrow().getIsPrimary());
        assertFalse(options.stream().filter(o -> o.getBomId() == child2).findFirst().orElseThrow().getIsPrimary());
        assertEquals(1, manufacturing.getSchemeChoices(parent1).size());
        assertEquals(Long.valueOf(child1), manufacturing.getSchemeChoices(parent1).get(0).getBomId());
    }

    @Test
    void adoptDoesNotPersistAndDraftReferenceBlocksSubmitButNotSave() {
        var p1 = product("P1");
        long child = service.createBomReturnId(scheme("SFG349-A", "V1.0", 349));
        long parentId = service.createBomReturnId(parent(p1.getProductId(), 349, child, 0));
        var before = productMapper.selectById(p1.getProductId()).getSchemeRevision();

        var adopted = manufacturing.adopt(child);
        assertNotNull(adopted.getSelectionToken());
        assertEquals(before, productMapper.selectById(p1.getProductId()).getSchemeRevision());
        assertEquals(Integer.valueOf(1), service.getBomDetail(child).getApproveStatus());

        var ex = assertThrows(BusinessException.class, () -> service.submitApprove(parentId));
        assertTrue(ex.getMessage().contains("SUB_BOM_NOT_APPROVED"), ex.getMessage());
    }

    @Test
    void concurrentSchemeRevisionIsRejected() {
        var p1 = product("P1");
        long child = service.createBomReturnId(scheme("SFG349-A", "V1.0", 349));
        long parentId = service.createBomReturnId(parent(p1.getProductId(), 349, child, 0));
        var stale = parent(p1.getProductId(), 349, child, 0);
        stale.setBomId(parentId);
        var ex = assertThrows(BusinessException.class, () -> service.updateBomWithDetail(stale));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("CONCURRENT_MODIFICATION"));
    }

}
