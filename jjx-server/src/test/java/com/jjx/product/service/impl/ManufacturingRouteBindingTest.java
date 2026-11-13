package com.jjx.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.product.domain.converter.EngineeringRoutingConverterImpl;
import com.jjx.product.domain.dto.EngineeringRoutingDTO;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import com.jjx.product.mapper.EngineeringRoutingMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IEngineeringRoutingService;
import com.jjx.system.service.OperLogChangeRecorder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 路线 PRODUCT/MATERIAL 产出身份、跨类型拒绝与当前版本隔离（dev-20261111-004）。 */
class ManufacturingRouteBindingTest {

    IEngineeringRoutingService service;
    EngineeringRoutingMapper routingMapper;

    @BeforeEach
    void setup() {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("routing-output", new SpringManagedTransactionFactory(), source));
        configuration.addMapper(EngineeringRoutingMapper.class);
        configuration.addMapper(EngineeringRoutingItemMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        var db = new JdbcTemplate(source);
        createTable(db, EngineeringRouting.class);
        createTable(db, EngineeringRoutingItem.class);
        db.execute("CREATE TABLE engineering_standard_process (process_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "process_code VARCHAR(100), process_name VARCHAR(200), process_type VARCHAR(50), "
                + "standard_labor_hours DECIMAL(12,4), standard_machine_hours DECIMAL(12,4), "
                + "equipment_type VARCHAR(100), skill_requirement VARCHAR(200), is_enabled INT, "
                + "display_order INT, icon VARCHAR(100), has_index INT, has_work_instruction INT)");
        routingMapper = session.getMapper(EngineeringRoutingMapper.class);

        var materials = mock(InventoryMaterialMapper.class);
        when(materials.selectById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            var material = new InventoryMaterial();
            material.setMaterialId(id);
            material.setMaterialCode("SFG" + id);
            material.setMaterialName("半成品" + id);
            material.setUnit("PCS");
            material.setStatus(1);
            return material;
        });
        var implementation = new EngineeringRoutingServiceImpl(routingMapper, mock(com.jjx.event.EventPublisher.class),
                session.getMapper(EngineeringRoutingItemMapper.class), new EngineeringRoutingConverterImpl(),
                mock(ProductMapper.class), mock(OperLogChangeRecorder.class));
        ReflectionTestUtils.setField(implementation, "baseMapper", routingMapper);
        ReflectionTestUtils.setField(implementation, "inventoryMaterialMapper", materials);
        service = implementation;
    }

    private void createTable(JdbcTemplate db, Class<?> entity) {
        var table = TableInfoHelper.getTableInfo(entity);
        var columns = new StringBuilder(table.getKeyColumn() + " BIGINT AUTO_INCREMENT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            Class<?> type = field.getPropertyType();
            String sqlType = type == String.class ? "VARCHAR(4000)"
                    : type == LocalDateTime.class ? "TIMESTAMP"
                    : type == Boolean.class ? "BOOLEAN"
                    : type == BigDecimal.class ? "DECIMAL(18,4)"
                    : type == Integer.class ? "INT" : "BIGINT";
            columns.append(',').append(field.getColumn()).append(' ').append(sqlType);
        }
        db.execute("CREATE TABLE " + table.getTableName() + " (" + columns + ")");
    }

    private EngineeringRoutingDTO dto(String code, String outputType, Long outputId) {
        var dto = new EngineeringRoutingDTO();
        dto.setRoutingCode(code);
        dto.setRoutingName(code);
        dto.setRoutingVersion("V1.0");
        dto.setOutputType(outputType);
        dto.setOutputId(outputId);
        dto.setProductId("PRODUCT".equals(outputType) ? outputId : null);
        dto.setProductCode("PRODUCT".equals(outputType) ? "P" + outputId : null);
        dto.setProductName("PRODUCT".equals(outputType) ? "产品" + outputId : null);
        return dto;
    }

    @Test
    void createsProductAndMaterialRoutesWithExplicitIdentity() {
        var product = service.createRouting(dto("RT-P1", "PRODUCT", 1L));
        assertEquals("PRODUCT", product.getOutputType());
        assertEquals(Long.valueOf(1), product.getOutputId());

        var material = service.createRouting(dto("RT-M349", "MATERIAL", 349L));
        assertEquals("MATERIAL", material.getOutputType());
        assertEquals(Long.valueOf(349), material.getOutputId());
        assertNull(material.getProductId());
        assertNull(service.getById(material.getRoutingId()).getProductId());
    }

    @Test
    void rejectsChangingOutputIdentityAndMissingMaterial() {
        var material = service.createRouting(dto("RT-M349", "MATERIAL", 349L));
        var change = dto("RT-M349", "PRODUCT", 1L);
        change.setRoutingId(material.getRoutingId());
        var ex = assertThrows(BusinessException.class, () -> service.updateRouting(change));
        assertTrue(ex.getMessage().contains("产出身份不可修改"), ex.getMessage());
    }

    @Test
    void currentVersionIsScopedByOutputNotProduct() {
        var first = service.createRouting(dto("RT-M349-A", "MATERIAL", 349L));
        var second = service.createRouting(dto("RT-M349-B", "MATERIAL", 349L));
        service.submitApprove(first.getRoutingId()); service.approve(first.getRoutingId(), null);
        service.submitApprove(second.getRoutingId()); service.approve(second.getRoutingId(), null);
        service.setCurrentVersion(first.getRoutingId());
        service.setCurrentVersion(second.getRoutingId());
        var current = routingMapper.selectCurrentByOutput("MATERIAL", 349L);
        assertNotNull(current);
        assertEquals(second.getRoutingId(), current.getRoutingId());
        assertNull(routingMapper.selectCurrentByOutput("PRODUCT", 349L));
    }
}
