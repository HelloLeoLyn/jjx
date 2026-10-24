package com.jjx.product.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.domain.converter.EngineeringBomConverterImpl;
import com.jjx.product.domain.dto.EngineeringBomDTO;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.service.impl.EngineeringBomServiceImpl;
import jakarta.validation.Validation;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EngineeringBomNameMappingTest {
    @Test
    void savedAndRenamedBomNamesSurviveActualMapperAndDetailConversion() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("bom-name", new JdbcTransactionFactory(), source));
        configuration.addMapper(EngineeringBomMapper.class);
        var table = TableInfoHelper.getTableInfo(EngineeringBom.class);
        var columns = new StringBuilder("bom_id BIGINT AUTO_INCREMENT PRIMARY KEY");
        for (var field : table.getFieldList()) {
            var type = field.getPropertyType();
            columns.append(',').append(field.getColumn()).append(' ').append(type == String.class ? "VARCHAR(500)"
                    : type == Boolean.class ? "BOOLEAN" : type == LocalDateTime.class || type == java.util.Date.class ? "TIMESTAMP" : "BIGINT");
        }
        new JdbcTemplate(source).execute("CREATE TABLE engineering_bom (" + columns + ")");
        var converter = new EngineeringBomConverterImpl();
        try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
            var mapper = session.getMapper(EngineeringBomMapper.class);
            var items = org.mockito.Mockito.mock(com.jjx.product.mapper.EngineeringBomItemMapper.class);
            org.mockito.Mockito.when(items.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
            var service = new EngineeringBomServiceImpl(mapper, items, null, converter, null, null, null, null, null, null);
            var dto = new EngineeringBomDTO();
            dto.setBomCode("P1-BOM");
            dto.setBomName("用户自定名称");
            var entity = converter.toEntity(dto);
            assertEquals(1, mapper.insert(entity));
            assertEquals("用户自定名称", service.getBomDetail(entity.getBomId()).getBomName());
            dto.setBomId(entity.getBomId());
            dto.setBomName("修改后的名称");
            assertEquals(1, mapper.updateById(converter.toEntity(dto)));
            var reopened = service.getBomDetail(entity.getBomId());
            assertEquals("修改后的名称", reopened.getBomName());
            assertEquals("修改后的名称", new ObjectMapper().valueToTree(reopened).path("bomName").asText());
        }
    }

    @Test
    void emptyNameStillFailsExistingDtoValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var dto = new EngineeringBomDTO();
            dto.setBomName("");
            assertTrue(factory.getValidator().validate(dto).stream().anyMatch(v -> v.getPropertyPath().toString().equals("bomName")));
        }
    }
}
